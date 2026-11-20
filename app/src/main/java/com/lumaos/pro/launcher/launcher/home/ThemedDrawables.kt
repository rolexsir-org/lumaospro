package com.lumaos.pro.launcher.launcher.home

import android.content.res.ColorStateList
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
