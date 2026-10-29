package com.lumaos.pro.launcher.widgets

import com.lumaos.pro.launcher.launcher.model.HomeItem
import com.lumaos.pro.launcher.launcher.model.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPlanTest {

    private fun widget(id: Int, rows: Int) = HomeItem.Widget(id, "com.x/com.x.Provider", rows)
    private fun app() = HomeItem.App(com.lumaos.pro.launcher.core.model.ComponentKey("com.a", "com.a.Main"))

    private fun facts(
        resizeMode: Int = WidgetResizeFlags.NONE,
        minHeightPx: Int = 200,
        minResizeHeightPx: Int = 0,
        maxResizeHeightPx: Int = 0,
        targetCellHeight: Int = 0,
        minCellHeight: Int = 0,
        hasConfigure: Boolean = false,
        widgetFeatures: Int = 0,
        rowHeightPx: Int = 100
    ) = ProviderFacts(
        resizeMode, minHeightPx, minResizeHeightPx, maxResizeHeightPx,
        targetCellHeight, minCellHeight, hasConfigure, widgetFeatures, rowHeightPx
    )

    @Test
    fun `music provider uses normal widget placement and local layout restoration`() {
        val caps = WidgetPlan.capabilities(facts(resizeMode = WidgetResizeFlags.BOTH, minHeightPx = 180,
            minResizeHeightPx = 160, targetCellHeight = 3, rowHeightPx = 80))
        assertEquals(3, caps.initialRows)
        val music = HomeItem.Widget(37, "com.lumaos.pro.launcher/com.lumaos.pro.launcher.widgets.music.MusicWidgetProvider", caps.initialRows)
        val original = HomeLayout(pages = listOf(listOf(music, app())))
        assertEquals(original, com.lumaos.pro.launcher.launcher.model.HomeLayoutCodec.decode(
            com.lumaos.pro.launcher.launcher.model.HomeLayoutCodec.encode(original)))
        assertEquals(InsertionPlan.AlreadyPresent, WidgetPlan.planInsertion(original.page(0), music))
    }

    @Test
    fun `fixed widget spans exactly its minimum rows and cannot resize`() {
        val caps = WidgetPlan.capabilities(facts(resizeMode = WidgetResizeFlags.NONE, minHeightPx = 250))
        assertFalse(caps.resizable)
        assertFalse(caps.resizableVertical)
        assertEquals(3, caps.minRows)
        assertEquals(3, caps.maxRows)
        assertEquals(3, caps.initialRows)
    }

    @Test
    fun `vertically resizable widget honours min and max provider heights`() {
        val caps = WidgetPlan.capabilities(
            facts(
                resizeMode = WidgetResizeFlags.VERTICAL,
                minHeightPx = 200,
                minResizeHeightPx = 100,
                maxResizeHeightPx = 400
            )
        )
        assertTrue(caps.resizableVertical)
        assertEquals(1, caps.minRows)
        assertEquals(4, caps.maxRows)
    }

    @Test
    fun `resize mode both offers vertical and horizontal capability`() {
        val caps = WidgetPlan.capabilities(facts(resizeMode = WidgetResizeFlags.BOTH))
        assertTrue(caps.resizableVertical)
        assertTrue(caps.resizableHorizontal)
    }

    @Test
    fun `row clamp stays inside the provider range and global bounds`() {
        val caps = WidgetPlan.capabilities(
            facts(resizeMode = WidgetResizeFlags.VERTICAL, minHeightPx = 200, maxResizeHeightPx = 300)
        )
        assertEquals(2, WidgetPlan.clampRows(1, caps))
        assertEquals(3, WidgetPlan.clampRows(9, caps))
        assertEquals(2, WidgetPlan.clampRows(2, caps))
    }

    @Test
    fun `target cell height drives the initial placement when the provider supplies it`() {
        val caps = WidgetPlan.capabilities(
            facts(resizeMode = WidgetResizeFlags.VERTICAL, targetCellHeight = 3, minCellHeight = 2, minHeightPx = 100)
        )
        assertEquals(3, caps.initialRows)
        assertEquals(2, caps.minRows)
    }

    @Test
    fun `initial rows never exceed the global four-row ceiling`() {
        val caps = WidgetPlan.capabilities(
            facts(resizeMode = WidgetResizeFlags.VERTICAL, minHeightPx = 100, targetCellHeight = 9)
        )
        assertEquals(HomeLayout.MAX_WIDGET_ROWS, caps.initialRows)
    }

    @Test
    fun `configuration and reconfigurable flags are interpreted separately`() {
        val plain = WidgetPlan.capabilities(facts(hasConfigure = true))
        assertTrue(plain.configurable)
        assertFalse(plain.reconfigurable)

        val reconfigurable = WidgetPlan.capabilities(
            facts(hasConfigure = true, widgetFeatures = WidgetResizeFlags.FEATURE_RECONFIGURABLE)
        )
        assertTrue(reconfigurable.configurable)
        assertTrue(reconfigurable.reconfigurable)
    }

    @Test
    fun `unknown row height degrades to the global minimum instead of crashing`() {
        val caps = WidgetPlan.capabilities(facts(rowHeightPx = 0, minHeightPx = 9999))
        assertEquals(HomeLayout.MIN_WIDGET_ROWS, caps.initialRows)
        assertTrue(caps.minRows >= HomeLayout.MIN_WIDGET_ROWS)
    }

    @Test
    fun `insertion leads the page and never overwrites existing items`() {
        val page = listOf(app(), app())
        val plan = WidgetPlan.planInsertion(page, widget(7, 2))
        assertEquals(InsertionPlan.AtIndex(0), plan)
    }

    @Test
    fun `duplicate widget id is refused rather than inserted twice`() {
        val page = listOf(widget(7, 2), app())
        assertEquals(InsertionPlan.AlreadyPresent, WidgetPlan.planInsertion(page, widget(7, 1)))
    }

    @Test
    fun `configured page widget count budget is enforced`() {
        val grid = com.lumaos.pro.launcher.launcher.model.HomeGridSpec(
            rows = 3,
            maxWidgetsPerPage = 2,
            maxWidgetsTotal = 6
        )
        val page = (1..grid.maxWidgetsPerPage).map { widget(it, 1) }
        assertEquals(InsertionPlan.NoSpace, WidgetPlan.planInsertion(page, widget(99, 1), grid))
    }

    @Test
    fun `combined widget row budget is enforced`() {
        val grid = com.lumaos.pro.launcher.launcher.model.HomeGridSpec(columns = 4, rows = 8)
        val page = listOf(widget(1, 4), widget(2, 4))
        assertEquals(InsertionPlan.NoSpace, WidgetPlan.planInsertion(page, widget(3, 1), grid))
        assertTrue(WidgetPlan.canFit(page.drop(1), widget(4, 1), grid))
    }

    @Test
    fun `widget and app cells share the page grid with full width widgets row aligned`() {
        val grid = com.lumaos.pro.launcher.launcher.model.HomeGridSpec(columns = 4, rows = 7)
        val page = listOf(app(), app(), widget(1, 2), app())
        assertEquals(2, WidgetPlan.widgetRows(page))
        assertEquals(25, grid.cellsUsed(listOf(widget(2, 2)) + page))
        assertEquals(InsertionPlan.AtIndex(0), WidgetPlan.planInsertion(page, widget(2, 2), grid))
    }

    @Test
    fun `full layout plan detects cross page duplicates and total widget limits`() {
        val grid = com.lumaos.pro.launcher.launcher.model.HomeGridSpec(
            columns = 2,
            rows = 2,
            maxWidgetsPerPage = 2,
            maxWidgetsTotal = 1
        )
        val layout = HomeLayout(pages = listOf(listOf(widget(7, 1)), emptyList()))

        assertEquals(InsertionPlan.AlreadyPresent, WidgetPlan.planInsertion(layout, 1, widget(7, 1), grid))
        assertEquals(InsertionPlan.NoSpace, WidgetPlan.planInsertion(layout, 1, widget(8, 1), grid))
    }

    @Test
    fun `full layout plan validates page index and provider row boundaries`() {
        val grid = com.lumaos.pro.launcher.launcher.model.HomeGridSpec(columns = 2, rows = 2)
        val layout = HomeLayout(pages = listOf(emptyList()))

        assertEquals(InsertionPlan.NoSpace, WidgetPlan.planInsertion(layout, 3, widget(8, 1), grid))
        assertEquals(InsertionPlan.NoSpace, WidgetPlan.planInsertion(layout, 0, widget(9, 5), grid))
        assertEquals(InsertionPlan.AtIndex(0), WidgetPlan.planInsertion(layout, 0, widget(10, 2), grid))
    }
}
