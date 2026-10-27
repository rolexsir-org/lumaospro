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
        assertEquals(InsertionPlan.NoSpace, WidgetPlan.planInsertion(page, widget(7, 1)))
    }

    @Test
    fun `page widget count budget is enforced`() {
        val page = (1..WidgetPlan.MAX_WIDGETS_PER_PAGE).map { widget(it, 1) }
        assertEquals(InsertionPlan.NoSpace, WidgetPlan.planInsertion(page, widget(99, 1)))
    }

    @Test
    fun `combined widget row budget is enforced`() {
        // Three 4-row widgets already use the full 12-row budget.
        val page = listOf(widget(1, 4), widget(2, 4), widget(3, 4))
        assertEquals(InsertionPlan.NoSpace, WidgetPlan.planInsertion(page, widget(4, 1)))
        assertTrue(WidgetPlan.canFit(page.drop(1), widget(4, 1)))
    }

    @Test
    fun `apps do not count against the widget row budget`() {
        val page = listOf(app(), app(), widget(1, 4), app())
        assertEquals(4, WidgetPlan.widgetRows(page))
        assertTrue(WidgetPlan.canFit(page, widget(2, 4)))
    }
}
