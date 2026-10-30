package com.lumaos.pro.launcher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widget slot geometry, checked against the grid the home RecyclerView really
 * lays out: one row occupies `cellHeight + 2 * gutter` because the item
 * decoration adds a gutter on both sides.
 *
 * These are plain JVM tests - no device, no Robolectric - because the subject is
 * arithmetic. That is deliberate: the widget clipping and overlap defects came
 * from this arithmetic disagreeing with the decoration, and a disagreement here
 * is exactly what a unit test can catch.
 */
class WidgetSlotGeometryTest {

    private val cell = 100
    private val gutter = 12
    private val pitch = cell + 2 * gutter // 124

    @Test
    fun `one pitch is the cell plus both decoration gutters`() {
        assertEquals(pitch, WidgetSlotGeometry.pitchPx(cell, gutter))
    }

    @Test
    fun `a multi-row widget item plus its decoration equals exactly that many pitches`() {
        // The defect this replaces: rows * cellHeight, which was short by
        // 2 * gutter * (rows - 1) and pushed everything below it off the grid.
        for (rows in 1..4) {
            val item = WidgetSlotGeometry.itemHeightPx(rows, cell, gutter)
            assertEquals("rows=$rows", rows * pitch, item + 2 * gutter)
        }
    }

    @Test
    fun `a single-row widget keeps the height of a single app row`() {
        assertEquals(cell, WidgetSlotGeometry.itemHeightPx(1, cell, gutter))
    }

    @Test
    fun `the frame is the item minus the layout padding on both sides`() {
        val item = WidgetSlotGeometry.itemHeightPx(2, cell, gutter)
        val padding = 8
        assertEquals(item - 2 * padding, WidgetSlotGeometry.frameHeightPx(item, padding))
    }

    @Test
    fun `a frame never reports a negative height when padding exceeds the item`() {
        assertEquals(0, WidgetSlotGeometry.frameHeightPx(10, 24))
    }

    @Test
    fun `the reported size is the frame in dp rounded to one decimal`() {
        val density = 3f
        assertEquals(74.7f, WidgetSlotGeometry.reportedHeightDp(224, density), 0.0001f)
        assertEquals(0f, WidgetSlotGeometry.reportedHeightDp(224, 0f), 0.0001f)
    }

    @Test
    fun `rows for a height round up because a partial row cannot be shared with an icon`() {
        assertEquals(1, WidgetSlotGeometry.rowsForHeightPx(pitch, cell, gutter))
        assertEquals(2, WidgetSlotGeometry.rowsForHeightPx(pitch + 1, cell, gutter))
        assertEquals(3, WidgetSlotGeometry.rowsForHeightPx(2 * pitch + 1, cell, gutter))
    }

    @Test
    fun `rows for a height fall back to one row on degenerate input`() {
        assertEquals(1, WidgetSlotGeometry.rowsForHeightPx(0, cell, gutter))
        assertEquals(1, WidgetSlotGeometry.rowsForHeightPx(-40, cell, gutter))
        assertEquals(1, WidgetSlotGeometry.rowsForHeightPx(400, 0, 0))
    }

    @Test
    fun `degenerate geometry is clamped instead of producing negative sizes`() {
        assertEquals(0, WidgetSlotGeometry.pitchPx(-5, -5))
        assertEquals(0, WidgetSlotGeometry.itemHeightPx(3, 0, gutter))
        assertEquals(3 * cell, WidgetSlotGeometry.itemHeightPx(3, cell, -gutter))
        assertEquals(0f, WidgetSlotGeometry.toDp(120, -1f), 0.0001f)
    }

    @Test
    fun `only a positive size is reportable to the platform`() {
        assertTrue(WidgetSlotGeometry.isReportable(180f, 92.4f))
        assertFalse(WidgetSlotGeometry.isReportable(0f, 92.4f))
        assertFalse(WidgetSlotGeometry.isReportable(180f, 0f))
    }
}
