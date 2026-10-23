package com.rolexm.launcher.launcher.view

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * A folder icon: up to four member icons arranged in a 2x2 grid inside a plate
 * that takes the theme's icon shape.
 *
 * Drawn manually because a folder icon is not a composition Android's drawable
 * XML can express (child count varies, and members are live Drawables rather
 * than resources). It is also cheap: four draw calls and one path.
 *
 * Smart folders (live contents) carry a small accent marker so they read as
 * "computed" rather than hand-made.
 */
class FolderIconDrawable(
    private val members: List<Drawable>,
    private val plateColor: Int = 0x33FFFFFF.toInt(),
    private val rimColor: Int = 0,
    /** Normalised outline (see IconShapes); null draws a rounded square. */
    private val outline: FloatArray? = null,
    private val markerColor: Int = 0
) : Drawable() {

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b: Rect = bounds
        if (b.isEmpty) return
        buildPath(b)
        platePaint.style = Paint.Style.FILL
        platePaint.color = plateColor
        canvas.drawPath(path, platePaint)
        if (rimColor != 0) {
            platePaint.style = Paint.Style.STROKE
            platePaint.strokeWidth = (b.width() * 0.014f).coerceAtLeast(1f)
            platePaint.color = rimColor
            canvas.drawPath(path, platePaint)
        }

        val shown = members.take(MAX_MEMBERS)
        if (shown.isNotEmpty()) {
            val inset = b.width() * PADDING_FRACTION
            val cellW = (b.width() - inset * 2) / 2f
            val cellH = (b.height() - inset * 2) / 2f
            val iconSize = min(cellW, cellH) * ICON_FRACTION
            shown.forEachIndexed { index, drawable ->
                val col = index % 2
                val row = index / 2
                val cx = b.left + inset + col * cellW + cellW / 2f
                val cy = b.top + inset + row * cellH + cellH / 2f
                val half = iconSize / 2f
                drawable.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
                drawable.draw(canvas)
            }
        }
        if (markerColor != 0) {
            platePaint.style = Paint.Style.FILL
            platePaint.color = markerColor
            val r = b.width() * 0.07f
            canvas.drawCircle(b.right - r * 1.6f, b.top + r * 1.6f, r, platePaint)
        }
    }

    private fun buildPath(b: Rect) {
        path.reset()
        val pts = outline
        if (pts == null) {
            path.addRoundRect(
                b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                b.width() * 0.24f, b.height() * 0.24f, Path.Direction.CW
            )
            return
        }
        for (i in pts.indices step 2) {
            val x = b.left + pts[i] * b.width()
            val y = b.top + pts[i + 1] * b.height()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    override fun setAlpha(alpha: Int) {
        members.forEach { it.alpha = alpha }
        platePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        members.forEach { it.colorFilter = colorFilter }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        const val MAX_MEMBERS = 4
        private const val PADDING_FRACTION = 0.14f
        private const val ICON_FRACTION = 0.80f
    }
}
