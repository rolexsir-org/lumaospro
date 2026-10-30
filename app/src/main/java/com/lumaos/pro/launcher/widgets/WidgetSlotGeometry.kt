package com.lumaos.pro.launcher.widgets

import kotlin.math.roundToInt

/**
 * Widget slot geometry, derived from the real grid pitch.
 *
 * **Why this exists.** The home grid gives every item a decoration gutter, so one
 * app row occupies `cellHeight + 2 * gutter` pixels. A widget that spans several
 * rows was sized as `rows * cellHeight` plus a single gutter, which is short by
 * `2 * gutter * (rows - 1)`: a four-row widget lost 24dp against four app rows.
 * Everything downstream drifted with it - the drop-zone guide (which is computed
 * from the real cells) disagreed with the widget's box, the capacity maths in
 * `HomeGridSpec.cellsUsed` counted rows the widget did not actually fill, and the
 * size reported to the provider was larger than the frame it was drawn in, so
 * providers that laid out for the reported height had their content clipped.
 *
 * These are the only three numbers that matter, and they are now computed once:
 *
 *  1. [itemHeightPx] - the RecyclerView child height, so item + decoration equals
 *     exactly `rows` grid pitches;
 *  2. [contentBoxPx] - the glass frame inside that child and then the box the
 *     provider is drawn in, after the layout's own padding (applied twice, because
 *     `item_widget.xml` pads the root and the frame);
 *  3. [reportedHeightDp] / [reportedWidthDp] - the size handed to
 *     `AppWidgetHostView.updateAppWidgetSize`, which must be the frame, not the
 *     theoretical cell, because that is the box the provider's RemoteViews get.
 *
 * Pure arithmetic on pixel values, so it is unit-tested without a device.
 */
object WidgetSlotGeometry {

    /**
     * The home grid's item-decoration gutter, one side, in dp.
     *
     * This is the single definition of the grid's gutter: the RecyclerView
     * decoration, the widget slot height, the size reported to a provider and the
     * drop-zone pitch all read it, which is what stops them from disagreeing.
     * Changing it changes the whole grid rhythm at once - and nothing else.
     */
    const val GUTTER_DP: Float = 4f

    /** One row of the home grid as the RecyclerView actually lays it out. */
    fun pitchPx(cellHeightPx: Int, gutterPx: Int): Int =
        cellHeightPx.coerceAtLeast(0) + 2 * gutterPx.coerceAtLeast(0)

    /**
     * Height for the widget's item view. Adding the decoration gutter to this
     * value yields exactly [rows] grid pitches, so widgets and app rows share one
     * rhythm and nothing after the widget is pushed off the grid.
     */
    fun itemHeightPx(rows: Int, cellHeightPx: Int, gutterPx: Int): Int {
        val safeRows = rows.coerceAtLeast(1)
        val cell = cellHeightPx.coerceAtLeast(0)
        val gutter = gutterPx.coerceAtLeast(0)
        if (cell <= 0) return 0
        return safeRows * cell + 2 * gutter * (safeRows - 1)
    }

    /**
     * A box after [paddingOneSidePx] of padding on both sides; never negative.
     *
     * `item_widget.xml` pads twice - once on the item root and once on the glass
     * frame - so this is applied twice, on both axes: item to frame, frame to the
     * box a provider's RemoteViews are actually drawn in. That box, not the item
     * and not the theoretical cell, is what `updateAppWidgetSize` must be told
     * about, or providers lay out for height they never get and clip.
     */
    fun contentBoxPx(boxPx: Int, paddingOneSidePx: Int): Int =
        (boxPx - 2 * paddingOneSidePx.coerceAtLeast(0)).coerceAtLeast(0)

    /** Pixels to dp, rounded the way `updateAppWidgetSize` consumers expect. */
    fun toDp(px: Int, density: Float): Float {
        if (density <= 0f) return 0f
        return (px / density * 10f).roundToInt() / 10f
    }

    /** Height in dp to report to the provider: the box it is really drawn in. */
    fun reportedHeightDp(boxHeightPx: Int, density: Float): Float = toDp(boxHeightPx, density)

    /** Width in dp to report to the provider: the box it is really drawn in. */
    fun reportedWidthDp(boxWidthPx: Int, density: Float): Float = toDp(boxWidthPx, density)

    /**
     * Rows that fit a measured height, for the resize gesture and for reconciling
     * a restored layout whose rows no longer match the current grid.
     */
    fun rowsForHeightPx(heightPx: Int, cellHeightPx: Int, gutterPx: Int): Int {
        val pitch = pitchPx(cellHeightPx, gutterPx)
        if (pitch <= 0 || heightPx <= 0) return 1
        // A partial row still costs a row: the grid cannot share one with an icon.
        return ((heightPx + pitch - 1) / pitch).coerceAtLeast(1)
    }

    /** True when a reported size is safe to hand to the platform. */
    fun isReportable(widthDp: Float, heightDp: Float): Boolean = widthDp > 0f && heightDp > 0f
}
