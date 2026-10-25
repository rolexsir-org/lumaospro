package com.lumaos.pro.launcher.launcher.home

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import com.lumaos.pro.launcher.ui.theme.ColorMath

/** Small factory for theme-coloured backgrounds used across the home screen. */
object ThemedDrawables {

    fun surface(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(color)
            if (strokePx > 0 && strokeColor != 0) setStroke(strokePx, strokeColor)
        }

    fun topRounded(color: Int, radiusPx: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadii = floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f, 0f, 0f)
        setColor(color)
    }

    /** Ripple bounded by [content] (or by a rounded mask when content is null). */
    fun ripple(rippleColor: Int, content: Drawable?, radiusPx: Float): RippleDrawable {
        val mask = surface(ColorMath.WHITE, radiusPx)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }
}

/**
 * Dock and folder plate: a rounded body with a fine specular rim along the top
 * edge and a darker line along the bottom - the "machined bezel" of the
 * LumaOS Pro instrument language.
 */
class BezelDrawable(
    private val fill: Int,
    private val rimTop: Int,
    private val rimBottom: Int,
    private val radiusPx: Float,
    private val strokePx: Float
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokePx
        paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, rimTop, rimBottom, Shader.TileMode.CLAMP)
        val half = strokePx / 2
        rect.inset(half, half)
        canvas.drawRoundRect(rect, radiusPx - half, radiusPx - half, paint)
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
