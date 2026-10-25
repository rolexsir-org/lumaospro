package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Edit-mode placement guides drawn over the home grid: a faint outline for
 * every cell (occupied or free), and a highlighted target while dragging -
 * accent for "move here", a filled pulse for "drop to create/extend a folder".
 */
class PlacementGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var columns = 4
    var rows = 5
    var cellHeight = 0
    var scrollOffset = 0
    var guideColor = 0x40FFFFFF
    /** Unset until the theme applies a semantic accent; 0 means transparent. */
    var accentColor = 0
    var cornerPx = 16f

    /** Highlight in this view's coordinates, or null. */
    var target: RectF? = null
        set(v) { field = v; invalidate() }
    var mergeTarget = false
        set(v) { field = v; invalidate() }

    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        dash.pathEffect = DashPathEffect(floatArrayOf(6f * resources.displayMetrics.density, 5f * resources.displayMetrics.density), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        if (columns <= 0 || cellHeight <= 0) return
        val cellW = width / columns.toFloat()
        val inset = resources.displayMetrics.density * 5
        dash.color = guideColor
        var r = 0
        while (r < rows + 1) {
            val top = r * cellHeight - (scrollOffset % cellHeight).toFloat()
            if (top > height) break
            for (c in 0 until columns) {
                rect.set(c * cellW + inset, top + inset, (c + 1) * cellW - inset, top + cellHeight - inset)
                canvas.drawRoundRect(rect, cornerPx, cornerPx, dash)
            }
            r++
        }
        target?.let { t ->
            if (mergeTarget) {
                fill.style = Paint.Style.FILL
                fill.color = (accentColor and 0x00FFFFFF) or 0x38000000
                canvas.drawRoundRect(t, cornerPx, cornerPx, fill)
            }
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = resources.displayMetrics.density * 2
            fill.color = accentColor
            canvas.drawRoundRect(t, cornerPx, cornerPx, fill)
        }
    }
}
