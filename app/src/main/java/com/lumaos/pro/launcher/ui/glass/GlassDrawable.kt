package com.lumaos.pro.launcher.ui.glass

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassColors
import com.lumaos.pro.launcher.ui.theme.GlassSpec
import kotlin.math.max

/**
 * One cached, stateful View drawable for Luma Glass.
 *
 * The material is drawn in three passes - a vertical fill with a top specular
 * band, a directional edge and one thin internal reflection. Shaders are
 * rebuilt only when bounds or state change, never on a frame. Pressed, focused
 * and selected colours come from the shared [GlassSpec], so the same recipe
 * used by Compose also drives Android Views.
 */
class GlassDrawable(
    private var spec: GlassSpec,
    private val density: Float
) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillRect = RectF()
    private val edgeRect = RectF()
    private val fillPath = Path()
    private val edgePath = Path()
    private val fillRadii = FloatArray(8)
    private val edgeRadii = FloatArray(8)

    private var colors: GlassColors = spec.rest
    private var fillShader: LinearGradient? = null
    private var edgeShader: LinearGradient? = null
    private var specularShader: LinearGradient? = null
    private var alphaOverride = 255

    private val strokePx: Float get() = max(1f, spec.borderDp * density)
    private val radiusPx: Float get() = spec.radiusDp * density

    init {
        applyPaints()
    }

    fun setSpec(next: GlassSpec) {
        if (next == spec) return
        spec = next
        colors = colorsForState(state)
        rebuild()
        invalidateSelf()
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val next = colorsForState(state)
        if (next == colors) return false
        colors = next
        rebuildShaders()
        invalidateSelf()
        return true
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        rebuild()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty || fillShader == null) return
        fillPaint.shader = fillShader
        edgePaint.shader = edgeShader
        specularPaint.shader = specularShader

        canvas.drawPath(fillPath, fillPaint)

        // One restrained internal reflection, inset from the rounded edge.
        val inset = radiusPx * 0.55f
        val y = fillRect.top + strokePx * 2f
        if (fillRect.width() > inset * 2f) {
            canvas.drawLine(fillRect.left + inset, y, fillRect.right - inset, y, specularPaint)
        }
        canvas.drawPath(edgePath, edgePaint)
    }

    override fun getOutline(outline: Outline) {
        val b = bounds
        if (b.isEmpty) return
        // A rectangular outline with the same radius is cheaper than a path and
        // gives the compositor a usable clip for elevation on API 26+.
        outline.setRoundRect(0, 0, b.width(), b.height(), radiusPx)
    }

    override fun setAlpha(alpha: Int) {
        alphaOverride = alpha
        applyPaints()
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        edgePaint.colorFilter = colorFilter
        specularPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int {
        val translucent = ColorMath.alpha(colors.fillTop) < 255 || ColorMath.alpha(colors.fillBottom) < 255
        return if (translucent || alphaOverride < 255) PixelFormat.TRANSLUCENT else PixelFormat.OPAQUE
    }

    private fun colorsForState(state: IntArray): GlassColors {
        var pressed = false
        var focused = false
        var selected = false
        for (attr in state) {
            when (attr) {
                android.R.attr.state_pressed -> pressed = true
                android.R.attr.state_focused -> focused = true
                android.R.attr.state_selected,
                android.R.attr.state_checked,
                android.R.attr.state_activated -> selected = true
            }
        }
        return when {
            pressed -> spec.states.pressed
            selected -> spec.states.selected
            focused -> spec.states.focused
            else -> spec.states.rest
        }
    }

    private fun applyPaints() {
        fillPaint.alpha = alphaOverride
        edgePaint.alpha = alphaOverride
        specularPaint.alpha = alphaOverride
        edgePaint.strokeWidth = strokePx
        specularPaint.strokeWidth = max(1f, density)
    }

    private fun rebuild() {
        val b = bounds
        if (b.isEmpty) return
        val half = strokePx / 2f
        fillRect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        edgeRect.set(fillRect)
        edgeRect.inset(half, half)

        val r = radiusPx
        val top = if (spec.topOnly) floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        else FloatArray(8) { r }
        top.copyInto(fillRadii)
        for (i in top.indices) edgeRadii[i] = (top[i] - half).coerceAtLeast(0f)

        fillPath.reset()
        fillPath.addRoundRect(fillRect, fillRadii, Path.Direction.CW)
        edgePath.reset()
        edgePath.addRoundRect(edgeRect, edgeRadii, Path.Direction.CW)
        rebuildShaders()
    }

    private fun rebuildShaders() {
        if (fillRect.isEmpty) return
        val topFill = ColorMath.lerp(colors.fillTop, colors.specular, 0.38f)
        fillShader = LinearGradient(
            0f, fillRect.top, 0f, fillRect.bottom,
            intArrayOf(topFill, colors.fillTop, colors.fillBottom),
            floatArrayOf(0f, 0.045f, 1f),
            Shader.TileMode.CLAMP
        )
        edgeShader = LinearGradient(
            0f, edgeRect.top, 0f, edgeRect.bottom,
            colors.edgeTop, colors.edgeBottom, Shader.TileMode.CLAMP
        )
        val clearSpecular = ColorMath.withAlpha(colors.specular, 0f)
        specularShader = LinearGradient(
            fillRect.left, 0f, fillRect.right, 0f,
            intArrayOf(clearSpecular, colors.specular, clearSpecular),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }
}
