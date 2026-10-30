package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.lumaos.pro.launcher.ui.theme.ColorMath

/**
 * Edit-mode placement guides drawn over the home grid.
 *
 * One dashed drop zone per *empty* slot — aligned to the real RecyclerView
 * cells rather than to a guessed cell size — plus a highlighted target while
 * dragging: accent stroke for "move here", accent fill for "drop to create or
 * extend a folder".
 *
 * Geometry contract with [EditModeController.updateGuideMetrics]:
 *  - [columns] is the grid's span count;
 *  - [contentWidth] is the grid's width *minus* its own horizontal padding, so
 *    `contentWidth / columns` is exactly one cell wide;
 *  - [cellHeight] is the row *pitch* the adapter actually lays out (cell plus
 *    decoration), so a zone sits on its cell at every row;
 *  - [scrollOffset] is the grid's vertical scroll in pixels, so the zones stay
 *    glued to the cells while the grid scrolls;
 *  - [occupied] lists the on-screen bounds of every placed item, in this view's
 *    coordinates, so only genuinely free slots are outlined.
 *
 * Drawing is allocation-free: three paints, one reusable rect.
 */
class PlacementGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    /** Grid span count. */
    var columns = 4

    /** Configured rows (used only as a sanity bound when the view is taller). */
    var rows = 5

    /**
     * Row pitch in px: one row as the adapter lays it out, i.e. the cell height
     * plus the item decoration above and below it. Zones advance by this, so they
     * stay on their cells however many rows down the grid they are.
     */
    var cellHeight = 0

    /** Grid content width in px (RecyclerView width minus horizontal padding). */
    var contentWidth = 0

    /** Vertical scroll of the grid in px. */
    var scrollOffset = 0

    /** Gap between two zones; each zone is inset by half of it on every side. */
    var gutterPx = EditModeDesign.gutterPx(density)

    /** Corner radius of a drop zone. */
    var cornerPx = EditModeDesign.slotRadiusPx(density)

    /** Dash colour: theme content at 12% (white until a theme is applied). */
    var guideColor = ColorMath.withAlpha(Color.WHITE, EditModeDesign.slotBorderAlpha)

    /** Zone fill: theme content at 2% (white until a theme is applied). */
    var fillColor = ColorMath.withAlpha(Color.WHITE, EditModeDesign.slotFillAlpha)

    /** Unset until the theme applies a semantic accent; 0 means transparent. */
    var accentColor = 0

    /** Bounds of placed items, in this view's coordinates. */
    var occupied: List<RectF> = emptyList()

    /** Highlight in this view's coordinates, or null. */
    var target: RectF? = null
        set(v) { field = v; invalidate() }

    /** True when the target would merge into a folder. */
    var mergeTarget = false
        set(v) { field = v; invalidate() }

    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = EditModeDesign.slotBorderDp * density
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
        dash.pathEffect = DashPathEffect(
            floatArrayOf(
                EditModeDesign.slotDashOnDp * density,
                EditModeDesign.slotDashOffDp * density
            ),
            0f
        )
    }

    /** Half gutter: the per-side inset that produces a [EditModeDesign.gridGutterDp] gutter. */
    private val inset: Float get() = gutterPx / 2f

    override fun onDraw(canvas: Canvas) {
        if (columns <= 0 || cellHeight <= 0) return
        val cellW = (if (contentWidth > 0) contentWidth else width) / columns.toFloat()
        if (cellW <= 0f) return

        dash.color = guideColor
        fill.color = fillColor

        // Walk the rows the content actually occupies, anchored to the scroll
        // offset so a zone moves with its cell instead of drifting.
        var top = -scrollOffset.toFloat()
        var guard = 0
        while (top < height && guard < MAX_ROWS) {
            for (c in 0 until columns) {
                rect.set(
                    c * cellW + inset,
                    top + inset,
                    (c + 1) * cellW - inset,
                    top + cellHeight - inset
                )
                if (rect.bottom < 0f || rect.top > height) continue
                if (isOccupied(rect)) continue
                canvas.drawRoundRect(rect, cornerPx, cornerPx, fill)
                canvas.drawRoundRect(rect, cornerPx, cornerPx, dash)
            }
            top += cellHeight
            guard++
        }

        target?.let { t ->
            if (mergeTarget) {
                // Filled accent plate while a merge is armed: the strongest,
                // clearest "let go here" signal in the screen.
                fill.color = (accentColor and 0x00FFFFFF) or 0x38000000
                canvas.drawRoundRect(t, cornerPx, cornerPx, fill)
            }
            accent.strokeWidth = 2f * density
            accent.color = accentColor
            canvas.drawRoundRect(t, cornerPx, cornerPx, accent)
        }
    }

    /** True when a placed item already covers (most of) this slot. */
    private fun isOccupied(cell: RectF): Boolean {
        for (i in occupied.indices) {
            val o = occupied[i]
            if (o.left < cell.right && o.right > cell.left && o.top < cell.bottom && o.bottom > cell.top) {
                return true
            }
        }
        return false
    }

    private companion object {
        /** Bounded loop: never draw more than a very tall phone's worth of rows. */
        const val MAX_ROWS = 24
    }
}
