package com.lumaos.pro.launcher.ui.glass

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Build
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassColors
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.GlassSpec
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Unified 6-layer View-system Luma Glass Drawable:
 *  1. Window-aligned pre-blurred wallpaper underlay on API 29–30 (API 31+ uses
 *     hardware RenderEffect on the backdrop layer; API <= 28 boosts tint + grain).
 *  2. Tinted surface fill with depth-based ±10% variation and background
 *     luminance adaptation (+8% over bright >0.7, -5% over dark <0.2).
 *  3. Permanently cached 256x256 procedural noise grain at 0.02f opacity.
 *  4. Top-left -> bottom-right 6% white overhead specular sheen.
 *  5. 1dp directional inner border (18% top/left light catch -> 6% bottom/right).
 *  6. Hardware outline for crisp child content clipping & shadows.
 */
class GlassDrawable(
    val spec: GlassSpec,
    private val density: Float,
    private val depth: Float = defaultDepthForRole(spec.role)
) : Drawable() {

    private var colors: GlassColors = spec.states.rest
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val noisePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val blurBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPath = Path()
    private val edgePath = Path()
    private val rect = RectF()
    private val blurSrcRect = Rect()
    private var radii = FloatArray(8)
    private var drawableAlpha = 255

    private var adaptiveAlphaDelta: Float = 0f
    private var windowLeft: Int = 0
    private var windowTop: Int = 0
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    val radiusPx: Float = spec.radiusDp * density
    private val strokePx: Float = max(1f, LumaTokens.glassBorderWidth * density)
    private val isOpaqueFallback: Boolean
        get() = spec.blurDp <= 0f && ColorMath.alpha(spec.states.rest.fillBottom) >= 250

    init {
        rebuildRadii()
        edgePaint.strokeWidth = strokePx
    }

    fun setRegionAdaptation(
        alphaDelta: Float,
        winLeft: Int = 0,
        winTop: Int = 0,
        scrWidth: Int = 0,
        scrHeight: Int = 0
    ) {
        val clamped = alphaDelta.coerceIn(-0.08f, 0.14f)
        val moved = windowLeft != winLeft || windowTop != winTop || screenWidth != scrWidth || screenHeight != scrHeight
        if (adaptiveAlphaDelta != clamped || moved) {
            adaptiveAlphaDelta = clamped
            windowLeft = winLeft
            windowTop = winTop
            screenWidth = scrWidth
            screenHeight = scrHeight
            rebuildShaders(bounds)
            invalidateSelf()
        }
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val next = when {
            state.contains(android.R.attr.state_pressed) -> spec.states.pressed
            state.contains(android.R.attr.state_selected) -> spec.states.selected
            state.contains(android.R.attr.state_focused) -> spec.states.focused
            else -> spec.states.rest
        }
        if (next == colors) return false
        colors = next
        rebuildShaders(bounds)
        invalidateSelf()
        return true
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rebuildPaths(bounds)
        rebuildShaders(bounds)
    }

    private fun rebuildRadii() {
        val r = radiusPx
        radii = if (spec.topOnly) floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        else FloatArray(8) { r }
    }

    private fun rebuildPaths(b: Rect) {
        fillPath.reset()
        edgePath.reset()
        if (b.isEmpty) return
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        fillPath.addRoundRect(rect, radii, Path.Direction.CW)

        val inset = strokePx / 2f
        rect.inset(inset, inset)
        val innerR = (radiusPx - inset).coerceAtLeast(0f)
        val innerRadii = if (spec.topOnly) floatArrayOf(innerR, innerR, innerR, innerR, 0f, 0f, 0f, 0f)
        else FloatArray(8) { innerR }
        edgePath.addRoundRect(rect, innerRadii, Path.Direction.CW)
    }

    private fun rebuildShaders(b: Rect) {
        if (b.isEmpty) return
        val top = b.top.toFloat()
        val bottom = b.bottom.toFloat()
        val left = b.left.toFloat()
        val right = b.right.toFloat()

        val apiFallbackBoost = if (Build.VERSION.SDK_INT <= 28 && !isOpaqueFallback) 0.16f else 0f
        val depthBoost = ((depth - LumaTokens.depthHome) * 0.02f).coerceIn(-0.04f, 0.06f)
        val totalShift = if (isOpaqueFallback) 0f else (adaptiveAlphaDelta + depthBoost + apiFallbackBoost)

        val fillTopAdjusted = shiftAlpha(colors.fillTop, totalShift)
        val fillBottomAdjusted = shiftAlpha(colors.fillBottom, totalShift)

        fillPaint.shader = LinearGradient(
            0f, top, 0f, bottom,
            fillTopAdjusted, fillBottomAdjusted,
            Shader.TileMode.CLAMP
        )

        // Layer 4: Diagonal top-left -> bottom-right 6% white overhead specular sheen.
        val sheenTopLeft = ColorMath.withAlpha(0xFFFFFFFF.toInt(), LumaTokens.glassSheenAlpha)
        sheenPaint.shader = LinearGradient(
            left, top, right, bottom,
            intArrayOf(
                sheenTopLeft,
                ColorMath.withAlpha(0xFFFFFFFF.toInt(), LumaTokens.glassSheenAlpha * 0.35f),
                0x00FFFFFF
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )

        // Layer 5: 1dp directional inner border — 18% top/left light catch -> 6% bottom/right.
        val isBrightSurface = ColorMath.luminance(ColorMath.opaque(colors.fillBottom)) > 0.65
        val borderAnchor = if (isBrightSurface) {
            ColorMath.lerp(0xFFFFFFFF.toInt(), ColorMath.opaque(colors.edgeTop), 0.35f)
        } else {
            0xFFFFFFFF.toInt()
        }
        val topLeftBorder = if (state.contains(android.R.attr.state_focused)) {
            colors.edgeTop
        } else {
            ColorMath.withAlpha(ColorMath.opaque(borderAnchor), LumaTokens.glassBorderTopAlpha)
        }
        val bottomRightBorder = ColorMath.withAlpha(ColorMath.opaque(borderAnchor), LumaTokens.glassBorderBottomAlpha)
        edgePaint.shader = LinearGradient(
            left, top, right, bottom,
            topLeftBorder, bottomRightBorder,
            Shader.TileMode.CLAMP
        )
    }

    private fun shiftAlpha(color: Int, delta: Float): Int {
        if (delta == 0f) return color
        val baseA = ColorMath.alpha(color) / 255f
        if (baseA >= 0.99f) return color
        return ColorMath.withAlpha(color, (baseA + delta).coerceIn(0.12f, 0.94f))
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        // Layer 1 (API 29–30): Pre-rendered blurred wallpaper underlay aligned to window bounds.
        if (Build.VERSION.SDK_INT in 29..30 && !isOpaqueFallback && screenWidth > 0 && screenHeight > 0) {
            val blurred: Bitmap? = LumaWallpaperBlurCache.current()
            if (blurred != null && !blurred.isRecycled) {
                val save = canvas.save()
                canvas.clipPath(fillPath)
                val sx0 = ((windowLeft.toFloat() / screenWidth) * blurred.width).toInt().coerceIn(0, blurred.width - 1)
                val sy0 = ((windowTop.toFloat() / screenHeight) * blurred.height).toInt().coerceIn(0, blurred.height - 1)
                val sx1 = (((windowLeft + b.width()).toFloat() / screenWidth) * blurred.width).roundToInt()
                    .coerceIn(sx0 + 1, blurred.width)
                val sy1 = (((windowTop + b.height()).toFloat() / screenHeight) * blurred.height).roundToInt()
                    .coerceIn(sy0 + 1, blurred.height)
                blurSrcRect.set(sx0, sy0, sx1, sy1)
                blurBitmapPaint.alpha = drawableAlpha
                canvas.drawBitmap(blurred, blurSrcRect, b, blurBitmapPaint)
                canvas.restoreToCount(save)
            }
        }

        // Layer 2: Tinted glass fill.
        fillPaint.alpha = drawableAlpha
        canvas.drawPath(fillPath, fillPaint)

        // Layer 3: Permanently cached 256x256 procedural noise grain at 0.02f opacity.
        LumaGlassNoise.shader()?.let { shader ->
            noisePaint.shader = shader
            noisePaint.alpha = ((LumaTokens.glassNoiseOpacity * drawableAlpha).roundToInt()).coerceIn(1, 255)
            canvas.drawPath(fillPath, noisePaint)
        }

        // Layer 4: Diagonal top-left -> bottom-right 6% white specular sheen.
        sheenPaint.alpha = drawableAlpha
        canvas.drawPath(fillPath, sheenPaint)

        // Layer 5: 1dp directional inner border (18% top/left -> 6% bottom/right).
        edgePaint.alpha = drawableAlpha
        canvas.drawPath(edgePath, edgePaint)
    }

    override fun getOutline(outline: Outline) {
        val b = bounds
        if (b.isEmpty) return
        if (spec.topOnly) {
            outline.setRoundRect(b.left, b.top, b.right, b.bottom + radiusPx.toInt(), radiusPx)
        } else {
            outline.setRoundRect(b, radiusPx)
        }
        outline.alpha = if (isOpaqueFallback) 0.50f else LumaTokens.shadowCardAlpha.coerceAtLeast(0.22f)
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        sheenPaint.colorFilter = colorFilter
        edgePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        fun defaultDepthForRole(role: GlassRole): Float = when (role) {
            GlassRole.PRIMARY,
            GlassRole.DOCK,
            GlassRole.SEARCH,
            GlassRole.WIDGET,
            GlassRole.SECONDARY,
            GlassRole.SETTINGS_CONTROL -> LumaTokens.depthHome
            GlassRole.SHEET -> LumaTokens.depthDrawer
            GlassRole.FOLDER -> LumaTokens.depthFolder
            GlassRole.MENU,
            GlassRole.OVERLAY -> LumaTokens.depthModal
        }

        fun withRipple(
            spec: GlassSpec,
            density: Float,
            depth: Float = defaultDepthForRole(spec.role)
        ): Drawable {
            val content = GlassDrawable(spec, density, depth)
            val r = content.radiusPx
            val radii = if (spec.topOnly) floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) else FloatArray(8) { r }
            val mask = ShapeDrawable(RoundRectShape(radii, null, null))
            return RippleDrawable(ColorStateList.valueOf(spec.states.rest.ripple), content, mask)
        }
    }
}
