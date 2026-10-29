package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.lumaos.pro.launcher.R

/**
 * The desktop column: chronometer header, icon/widget grid, search pill, dock.
 *
 * In portrait it is an ordinary vertical LinearLayout. On wide landscape
 * windows (tablets, unfolded foldables, phones in landscape) it switches to a
 * side-by-side composition - the chronometer in a left column, the grid,
 * search and dock on the right - so the clock never eats the short axis.
 */
class DesktopLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var sideBySide: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    /** Fraction of the width given to the header column in side-by-side mode. */
    var headerFraction: Float = 0.40f

    private val header: View? get() = findViewById(R.id.home_header)
    /**
     * The action slot, not the search pill: the pill and the edit capsule are
     * two states of the same row and must be laid out as one.
     */
    private val right: List<View> get() = listOfNotNull(
        findViewById(R.id.home_grid_frame), findViewById(R.id.home_page_indicator),
        findViewById(R.id.home_action_slot), findViewById(R.id.dock_surface)
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!sideBySide || header == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val innerW = w - paddingLeft - paddingRight
        val innerH = h - paddingTop - paddingBottom
        val leftW = (innerW * headerFraction).toInt()
        val rightW = innerW - leftW
        header!!.measure(
            MeasureSpec.makeMeasureSpec(leftW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(innerH, MeasureSpec.AT_MOST)
        )
        // Fixed-height children first (pill, dock), then the grid takes the rest.
        var used = 0
        for (child in right) {
            if (child.id == R.id.home_grid_frame || child.visibility == GONE) continue
            val lp = child.layoutParams as MarginLayoutParams
            child.measure(
                MeasureSpec.makeMeasureSpec(rightW - lp.leftMargin - lp.rightMargin, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(innerH, MeasureSpec.AT_MOST)
            )
            used += child.measuredHeight + lp.topMargin + lp.bottomMargin
        }
        findViewById<View>(R.id.home_grid_frame)?.let { grid ->
            val lp = grid.layoutParams as MarginLayoutParams
            grid.measure(
                MeasureSpec.makeMeasureSpec(rightW - lp.leftMargin - lp.rightMargin, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((innerH - used - lp.topMargin - lp.bottomMargin).coerceAtLeast(0), MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val hdr = header
        if (!sideBySide || hdr == null) {
            super.onLayout(changed, l, t, r, b)
            return
        }
        val innerW = (r - l) - paddingLeft - paddingRight
        val leftW = (innerW * headerFraction).toInt()
        val innerH = (b - t) - paddingTop - paddingBottom
        // Header vertically centred in its column.
        val hy = paddingTop + (innerH - hdr.measuredHeight) / 2
        hdr.layout(paddingLeft, hy, paddingLeft + hdr.measuredWidth, hy + hdr.measuredHeight)
        var y = paddingTop
        val x0 = paddingLeft + leftW
        for (child in right) {
            if (child.visibility == GONE) continue
            val lp = child.layoutParams as MarginLayoutParams
            y += lp.topMargin
            child.layout(x0 + lp.leftMargin, y, x0 + lp.leftMargin + child.measuredWidth, y + child.measuredHeight)
            y += child.measuredHeight + lp.bottomMargin
        }
    }
}
