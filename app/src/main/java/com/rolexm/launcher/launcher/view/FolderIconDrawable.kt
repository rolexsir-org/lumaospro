package com.rolexm.launcher.launcher.view

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * A folder icon: up to four member icons arranged in a 2x2 grid inside a
 * rounded plate.
 *
 * Drawn manually because a folder icon is not a composition Android's drawable
 * XML can express (child count varies, and members are live Drawables rather
 * than resources). It is also cheap: four draw calls and a round rect.
 */
class FolderIconDrawable(
    private val members: List<Drawable>,
    private val plateColor: Int = 0x33FFFFFF.toInt()
) : Drawable() {

    override fun draw(canvas: Canvas) {
        val bounds: Rect = bounds
        if (bounds.isEmpty) return

        canvas.drawRoundRect(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            bounds.width() * 0.22f,
            bounds.height() * 0.22f,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = plateColor }
        )

        val shown = members.take(MAX_MEMBERS)
        if (shown.isEmpty()) return

        val inset = bounds.width() * PADDING_FRACTION
        val cellW = (bounds.width() - inset * 2) / 2f
        val cellH = (bounds.height() - inset * 2) / 2f
        val iconSize = min(cellW, cellH) * ICON_FRACTION
        val halfGap = (min(cellW, cellH) - iconSize) / 2f

        shown.forEachIndexed { index, drawable ->
            val col = index % 2
            val row = index / 2
            val cx = bounds.left + inset + col * cellW + cellW / 2f
            val cy = bounds.top + inset + row * cellH + cellH / 2f
            val half = iconSize / 2f
            drawable.setBounds(
                (cx - half).toInt(),
                (cy - half - halfGap / 2).toInt(),
                (cx + half).toInt(),
                (cy + half - halfGap / 2).toInt()
            )
            drawable.draw(canvas)
        }
    }

    override fun setAlpha(alpha: Int) {
        members.forEach { it.alpha = alpha }
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        members.forEach { it.colorFilter = colorFilter }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        const val MAX_MEMBERS = 4
        private const val PADDING_FRACTION = 0.12f
        private const val ICON_FRACTION = 0.82f
    }
}
