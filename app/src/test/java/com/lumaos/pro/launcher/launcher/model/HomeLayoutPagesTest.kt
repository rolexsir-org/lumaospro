package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutPagesTest {
    private fun app(name: String) = HomeItem.App(ComponentKey("com.$name", "com.$name.Main"))
    private fun widget(id: Int, rows: Int = 1) = HomeItem.Widget(id, "com.widgets/com.widgets.Provider", rows)

    @Test
    fun `pages add remove and round trip without changing serialized format`() {
        val withTwoPages = HomeLayoutOps.addPage(HomeLayout.EMPTY)
        assertEquals(2, withTwoPages.pageCount)
        val populated = HomeLayoutOps.setPage(withTwoPages, 1, listOf(app("mail")))
        val restored = HomeLayoutCodec.decode(HomeLayoutCodec.encode(populated))
        assertEquals(populated, restored)

        assertSame(populated, HomeLayoutOps.removeEmptyPage(populated, 1))
        val emptyPageRemoved = HomeLayoutOps.removeEmptyPage(populated, 0)
        assertEquals(listOf(listOf(app("mail"))), emptyPageRemoved.pages)
        assertSame(emptyPageRemoved, HomeLayoutOps.removeEmptyPage(emptyPageRemoved, 0))
    }

    @Test
    fun `page count is capped and invalid page requests do not mutate layout`() {
        var layout = HomeLayout.EMPTY
        repeat(HomeLayout.MAX_PAGES - 1) { layout = HomeLayoutOps.addPage(layout) }
        assertEquals(HomeLayout.MAX_PAGES, layout.pageCount)
        assertSame(layout, HomeLayoutOps.addPage(layout))
        assertSame(layout, HomeLayoutOps.removeEmptyPage(layout, HomeLayout.MAX_PAGES))
    }

    @Test
    fun `cross page moves persist item identity and automatically remove emptied source page`() {
        val sourceWidget = widget(44, 2)
        val original = HomeLayout(pages = listOf(listOf(sourceWidget), listOf(app("mail"))))

        val moved = HomeLayoutOps.moveToPage(original, 0, 0, 1)

        assertEquals(1, moved.pageCount)
        assertEquals(listOf(app("mail"), sourceWidget), moved.page(0))
        assertEquals(moved, HomeLayoutCodec.decode(HomeLayoutCodec.encode(moved)))
        assertSame(original, HomeLayoutOps.moveToPage(original, 0, 5, 1))
        assertSame(original, HomeLayoutOps.moveToPage(original, 0, 0, 8))
    }

    @Test
    fun `widget moves respect destination grid capacity without dropping source`() {
        val constrained = HomeGridSpec(columns = 2, rows = 2)
        val original = HomeLayout(pages = listOf(listOf(widget(1, 2)), listOf(widget(2, 2))))

        val refused = HomeLayoutOps.moveToPage(original, 0, 0, 1, spec = constrained)

        assertSame(original, refused)
        assertEquals(setOf(1, 2), refused.widgetIds())
    }

    @Test
    fun `removing a widget leaves other pages intact across persisted reload`() {
        val original = HomeLayout(pages = listOf(
            listOf(widget(21), app("mail")),
            listOf(widget(22, 2), app("calendar"))
        ))
        val loaded = HomeLayoutCodec.decode(HomeLayoutCodec.encode(original))
        val removed = HomeLayoutOps.removeWidget(loaded, 21)
        val restarted = HomeLayoutCodec.decode(HomeLayoutCodec.encode(removed))

        assertEquals(listOf(app("mail")), restarted.page(0))
        assertEquals(listOf(widget(22, 2), app("calendar")), restarted.page(1))
        assertEquals(setOf(22), restarted.widgetIds())
    }

    @Test
    fun `widget placement validates ids dimensions grid bounds and per page and total limits`() {
        val grid = HomeGridSpec(columns = 2, rows = 2, maxWidgetsPerPage = 1, maxWidgetsTotal = 2)
        val firstPage = HomeLayout(pages = listOf(emptyList(), emptyList()))
        val first = HomeLayoutOps.addWidget(firstPage, 0, widget(1, 2), spec = grid)
        assertEquals(listOf(widget(1, 2)), first.page(0))

        assertSame(first, HomeLayoutOps.addWidget(first, 0, widget(2, 1), spec = grid))
        assertSame(first, HomeLayoutOps.addWidget(first, 1, widget(1, 1), spec = grid))
        assertSame(first, HomeLayoutOps.addWidget(first, 1, widget(3, 3), spec = grid))

        val second = HomeLayoutOps.addWidget(first, 1, widget(2, 2), spec = grid)
        assertEquals(setOf(1, 2), second.widgetIds())
        assertSame(second, HomeLayoutOps.addWidget(second, 1, widget(3, 1), spec = grid))
    }

    @Test
    fun `widget resize enforces provider minimum and maximum in the state layer`() {
        val original = HomeLayout(pages = listOf(listOf(widget(8, 2))))

        assertSame(original, HomeLayoutOps.resizeWidget(original, 8, 1, minRows = 2, maxRows = 3))
        assertSame(original, HomeLayoutOps.resizeWidget(original, 8, 4, minRows = 2, maxRows = 3))
        assertEquals(3, (HomeLayoutOps.resizeWidget(original, 8, 3, minRows = 2, maxRows = 3)
            .page(0).single() as HomeItem.Widget).rows)
    }

    @Test
    fun `reordering cannot create grid overflow by fragmenting full width widget rows`() {
        val grid = HomeGridSpec(columns = 4, rows = 3)
        val first = widget(31)
        val second = widget(32)
        val original = HomeLayout(pages = listOf(listOf(first, app("a"), app("b"), app("c"), second)))
        val invalidOrder = listOf(app("a"), first, app("b"), app("c"), second)

        assertSame(original, HomeLayoutOps.reorderPage(original, 0, invalidOrder, grid))
    }

    @Test
    fun `full width widgets begin at a row boundary when validating remaining cells`() {
        val grid = HomeGridSpec(columns = 4, rows = 3)
        val page = listOf(app("mail"), widget(7, 2))

        assertEquals(12, grid.cellsUsed(page))
        assertTrue(!HomeLayoutOps.canFitItem(page, app("calendar"), grid))
    }

    @Test
    fun `widget row budgets prevent occupied cell overlap and resize overflow`() {
        val grid = HomeGridSpec(columns = 3, rows = 3)
        val oneTallWidget = HomeLayout(pages = listOf(listOf(widget(1, 2), app("mail"))))

        assertSame(oneTallWidget, HomeLayoutOps.addWidget(oneTallWidget, 0, widget(2, 2), spec = grid))
        assertSame(oneTallWidget, HomeLayoutOps.resizeWidget(oneTallWidget, 1, 3, grid))
        assertTrue(HomeLayoutOps.canPlaceWidget(
            HomeLayout.EMPTY,
            0,
            widget(9, 3),
            grid
        ))
    }
}
