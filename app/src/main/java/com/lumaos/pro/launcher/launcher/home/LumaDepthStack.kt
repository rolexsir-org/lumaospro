package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.motion.LumaSpring
import com.lumaos.pro.launcher.motion.MotionPolicy
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Unified Z-Depth Spatial Stack & Continuity Engine (Phase 4).
 *
 * Coordinates all persistent launcher layers along a single continuous Z-axis:
 *  - Layer 0 ([LumaTokens.depthWallpaper]): Wallpaper & Ambient Backdrop (parallax + subtle zoom)
 *  - Layer 1 ([LumaTokens.depthHome]): Home Desktop (progressive blur, scale recession, parallax)
 *  - Layer 2 ([LumaTokens.depthFolder]): Persistent 3x3 Glass Folder Layer
 *  - Layer 3 ([LumaTokens.depthDrawer]): Persistent Glass App Drawer & Search Layer
 *  - Layer 4 ([LumaTokens.depthModal]): Persistent Modal / Widget Sheet Layer
 *  - Layer 5 ([LumaTokens.depthLaunchPortal]): App Icon <-> Window Launch & Return Morph Portal
 *
 * Every transform uses RenderThread-friendly properties (`translationX/Y`, `scaleX/Y`,
 * `alpha`, `RenderEffect`) driven exclusively by [LumaSpring] (`0.78f / 280f`).
 */
object LumaDepthStack {

    data class Inputs(
        val drawerProgress: Float = 0f,
        val folderProgress: Float = 0f,
        val editProgress: Float = 0f,
        val modalProgress: Float = 0f,
        val launchProgress: Float = 0f,
        val settleProgress: Float = 1f,
        /** Normalized horizontal gesture displacement in `[-1f, 1f]`. */
        val parallaxX: Float = 0f,
        val density: Float = 1f,
        val allowDepthTransforms: Boolean = true,
        val allowBlur: Boolean = true,
        /** True while a spring or finger gesture is actively in motion (scales blur cost 50%). */
        val inMotion: Boolean = false
    )

    data class Frame(
        val wallpaperScale: Float,
        val wallpaperTranslationX: Float,
        val wallpaperTranslationY: Float,
        val desktopScale: Float,
        val desktopAlpha: Float,
        val desktopTranslationX: Float,
        val desktopTranslationY: Float,
        val desktopBlurRadiusPx: Float,
        val drawerScale: Float,
        val drawerAlpha: Float
    )

    /**
     * Pure spatial solver computing continuous, monotonic Z-depth transforms
     * across all persistent layers without discontinuities on interruption.
     */
    fun solve(inputs: Inputs): Frame {
        val d = inputs.drawerProgress.coerceIn(0f, 1.05f)
        val f = inputs.folderProgress.coerceIn(0f, 1.05f)
        val e = inputs.editProgress.coerceIn(0f, 1f)
        val m = inputs.modalProgress.coerceIn(0f, 1.05f)
        val l = inputs.launchProgress.coerceIn(0f, 1f)
        val settle = inputs.settleProgress.coerceIn(0f, 1.1f)
        val px = inputs.parallaxX.coerceIn(-1f, 1f)
        val dp = inputs.density.coerceAtLeast(0.5f)

        val upperOverlay = maxOf(d.coerceIn(0f, 1f), f.coerceIn(0f, 1f), m.coerceIn(0f, 1f), l)

        val wallpaperScale = if (inputs.allowDepthTransforms) {
            1f + 0.04f * upperOverlay
        } else 1f
        val wallpaperTx = if (inputs.allowDepthTransforms) -px * 16f * dp else 0f
        val wallpaperTy = if (inputs.allowDepthTransforms) -(d * 18f + m * 10f) * dp else 0f

        val desktopScale = if (inputs.allowDepthTransforms) {
            (1f - 0.06f * d - 0.04f * f - 0.08f * e - 0.04f * m - 0.05f * l - 0.04f * (1f - settle))
                .coerceIn(0.84f, 1.02f)
        } else 1f

        val desktopAlpha = (
            (1f - 0.34f * d.coerceIn(0f, 1f)) *
                (1f - 0.24f * f.coerceIn(0f, 1f)) *
                (1f - 0.28f * m.coerceIn(0f, 1f)) *
                (1f - 0.35f * l) *
                (0.68f + 0.32f * settle.coerceIn(0f, 1f))
            ).coerceIn(0.18f, 1f)

        val desktopTx = if (inputs.allowDepthTransforms) px * 28f * dp else 0f
        val desktopTy = if (inputs.allowDepthTransforms) -(d * 28f + m * 16f) * dp else 0f

        val rawBlurDp = if (inputs.allowBlur) {
            max(
                max(LumaTokens.blurRadiusOverlay * d.coerceIn(0f, 1f), LumaTokens.blurRadiusSurface * f.coerceIn(0f, 1f)),
                max(LumaTokens.blurRadiusSurface * m.coerceIn(0f, 1f), LumaTokens.blurRadiusBackground * l)
            )
        } else 0f
        val qualityFactor = if (inputs.inMotion) LumaTokens.transitionBlurScale else 1f
        val steppedBlurPx = ((rawBlurDp * qualityFactor * dp) * 2f).roundToInt() / 2f

        val drawerScale = if (inputs.allowDepthTransforms) (1f - 0.04f * l).coerceIn(0.92f, 1f) else 1f
        val drawerAlpha = (1f - 0.28f * l).coerceIn(0.5f, 1f)

        return Frame(
            wallpaperScale = wallpaperScale,
            wallpaperTranslationX = wallpaperTx,
            wallpaperTranslationY = wallpaperTy,
            desktopScale = desktopScale,
            desktopAlpha = desktopAlpha,
            desktopTranslationX = desktopTx,
            desktopTranslationY = desktopTy,
            desktopBlurRadiusPx = steppedBlurPx,
            drawerScale = drawerScale,
            drawerAlpha = drawerAlpha
        )
    }

    /** Assigns canonical Z-order (`translationZ`) to persistent layers in [HomeActivity]. */
    fun bindLayerElevation(view: View, depthLevel: Float, density: Float) {
        view.translationZ = depthLevel * 4f * density
    }
}

/**
 * Persistent Layer-5 Morph Portal providing seamless icon <-> app window launch
 * and return continuity without recreating views or allocating per frame.
 */
class AppLaunchPortalView(context: Context) : View(context) {

    var onLaunchProgress: ((Float) -> Unit)? = null

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val startRect = RectF()
    private val currentRect = RectF()
    private val tempInt = IntArray(2)
    private val rootLoc = IntArray(2)

    private var iconDrawable: Drawable? = null
    private var lastKey: ComponentKey? = null
    private var surfaceColor: Int = 0xFF141820.toInt()
    private var progress: Float = 0f
    private var launchingOut: Boolean = true

    private val spring = SpringAnimator(0f, valueThreshold = 0.004f) { p ->
        progress = p.coerceIn(0f, 1f)
        onLaunchProgress?.invoke(progress)
        invalidate()
    }

    init {
        visibility = GONE
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        borderPaint.strokeWidth = resources.displayMetrics.density
    }

    val isRunning: Boolean get() = spring.isRunning
    val lastLaunchedKey: ComponentKey? get() = lastKey

    /**
     * Morphs from [sourceView] (`56dp` icon bounds + `14dp` icon corner radius)
     * outward toward full-screen bounds (`0dp` corner radius) on [LumaSpring].
     */
    fun playLaunch(
        root: FrameLayout,
        key: ComponentKey,
        sourceView: View?,
        icon: Drawable?,
        fillColor: Int,
        motion: MotionPolicy
    ) {
        lastKey = key
        iconDrawable = icon?.constantState?.newDrawable(resources)?.mutate() ?: icon
        surfaceColor = ColorMath.withAlpha(fillColor, 0.92f)
        captureSourceBounds(root, sourceView)

        if (!motion.enabled || motion.reduced || width <= 0 && root.width <= 0) {
            spring.snapTo(0f)
            visibility = GONE
            return
        }

        launchingOut = true
        visibility = VISIBLE
        spring.snapTo(0f)
        spring.animateTo(1f, MotionTokens.LUMA, motion) {
            // Once the external app window covers the launcher, reset portal invisibly
            // while preserving startRect & iconDrawable for the return morph!
            visibility = GONE
            progress = 0f
            onLaunchProgress?.invoke(0f)
        }
    }

    /**
     * Morphs the returning app window smoothly back into its live home/dock icon
     * slot ([resolvedTargetView] if visible, otherwise the saved launch slot).
     */
    fun playReturn(
        root: FrameLayout,
        resolvedTargetView: View?,
        fillColor: Int,
        motion: MotionPolicy
    ) {
        if (lastKey == null) return
        if (resolvedTargetView != null && resolvedTargetView.isShown && resolvedTargetView.width > 0) {
            captureSourceBounds(root, resolvedTargetView)
        }
        if (startRect.isEmpty || !motion.enabled || motion.reduced) {
            spring.snapTo(0f)
            visibility = GONE
            onLaunchProgress?.invoke(0f)
            return
        }

        surfaceColor = ColorMath.withAlpha(fillColor, 0.86f)
        launchingOut = false
        visibility = VISIBLE
        spring.snapTo(1f)
        spring.animateTo(0f, MotionTokens.LUMA, motion) {
            visibility = GONE
            onLaunchProgress?.invoke(0f)
        }
    }

    fun cancelImmediately() {
        spring.cancel()
        progress = 0f
        visibility = GONE
        onLaunchProgress?.invoke(0f)
    }

    private fun captureSourceBounds(root: FrameLayout, sourceView: View?) {
        val rw = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val rh = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        if (sourceView != null && sourceView.width > 0 && sourceView.height > 0) {
            root.getLocationInWindow(rootLoc)
            sourceView.getLocationInWindow(tempInt)
            val left = (tempInt[0] - rootLoc[0]).toFloat()
            val top = (tempInt[1] - rootLoc[1]).toFloat()
            startRect.set(left, top, left + sourceView.width, top + sourceView.height)
        } else {
            val size = LumaTokens.iconApp * resources.displayMetrics.density
            val cx = rw / 2f
            val cy = rh * 0.55f
            startRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || startRect.isEmpty) return

        val p = progress.coerceIn(0f, 1f)
        currentRect.set(
            lerp(startRect.left, 0f, p),
            lerp(startRect.top, 0f, p),
            lerp(startRect.right, w, p),
            lerp(startRect.bottom, h, p)
        )

        val iconCornerPx = 14f * resources.displayMetrics.density
        val cornerPx = lerp(iconCornerPx, 0f, p)

        // Envelope fades cleanly near both endpoints so neither the icon nor the OS window double-draws.
        val envelopeAlpha = if (launchingOut) {
            ((1f - p) * 0.90f + 0.10f) * (p * 3.2f).coerceIn(0f, 1f)
        } else {
            (p * 0.85f) * ((1f - p) * 4f + 0.25f).coerceIn(0f, 1f)
        }

        cardPaint.color = ColorMath.withAlpha(surfaceColor, envelopeAlpha.coerceIn(0f, 0.88f))
        canvas.drawRoundRect(currentRect, cornerPx, cornerPx, cardPaint)

        borderPaint.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), (0.18f * (1f - p)).coerceIn(0f, 0.18f))
        if (cornerPx > 0.5f) {
            canvas.drawRoundRect(currentRect, cornerPx, cornerPx, borderPaint)
        }

        val d = iconDrawable
        if (d != null) {
            val baseSize = max(startRect.width(), startRect.height()).coerceAtLeast(1f)
            val iconSize = lerp(baseSize, baseSize * 1.45f, p)
            val cx = currentRect.centerX()
            val cy = currentRect.centerY()
            val half = (iconSize / 2f).roundToInt()
            val iconAlpha = ((1f - p * 0.85f) * 255f).roundToInt().coerceIn(0, 255)
            d.setBounds((cx - half).roundToInt(), (cy - half).roundToInt(), (cx + half).roundToInt(), (cy + half).roundToInt())
            d.alpha = iconAlpha
            d.draw(canvas)
            d.alpha = 255
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
