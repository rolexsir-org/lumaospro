package com.lumaos.pro.launcher.icons

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import com.lumaos.pro.launcher.ui.theme.IconShape
import com.lumaos.pro.launcher.ui.theme.IconShapes

/**
 * A legible fallback for an application whose icon could not be resolved.
 *
 * It keeps the app's own initial and the active icon shape rather than drawing
 * a generic grey square, so a missing icon still reads as a distinct app and
 * aligns with the rest of the grid. The original icon is never recoloured - this
 * is only used when there is no original artwork to preserve.
 */
class MonogramDrawable(
    label: String,
    private val fill: Int,
    private val textColor: Int,
    private val typeface: Typeface?,
    private val shape: IconShape,
    private val cornerRadiusPx: Float
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val rect = RectF()
    private val initial: String = label.trim().firstOrNull { !it.isWhitespace() }
        ?.uppercaseChar()?.toString() ?: "•"

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        val outline = IconShapes.outline(shape)
        if (outline != null) {
            path.reset()
            for (i in outline.indices step 2) {
                val x = rect.left + outline[i] * rect.width()
                val y = rect.top + outline[i + 1] * rect.height()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            paint.color = fill
            canvas.drawPath(path, paint)
        } else {
            paint.color = fill
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
        }
        textPaint.color = textColor
        textPaint.textSize = rect.height() * 0.42f
        val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(initial, rect.centerX(), baseline, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
