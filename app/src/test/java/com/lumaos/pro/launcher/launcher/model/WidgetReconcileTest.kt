package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetReconcileTest {

    private fun app(p: String) = HomeItem.App(ComponentKey(p, "$p.Main"))
    private fun widget(id: Int, rows: Int = 2) = HomeItem.Widget(id, "com.x/com.x.Provider", rows)

    @Test
    fun `reconcile keeps widgets whose host ids are still valid`() {
        val layout = HomeLayout(pages = listOf(listOf(widget(1), app("a"), widget(2))))
        assertSame(layout, HomeLayoutOps.reconcileWidgets(layout, setOf(1, 2)))
        assertTrue(HomeLayoutOps.invalidWidgetIds(layout, setOf(1, 2)).isEmpty())
    }

    @Test
    fun `reconcile removes only widgets whose provider disappeared`() {
        val layout = HomeLayout(
            dock = listOf(app("dock")),
            pages = listOf(listOf(widget(1), app("a"), widget(2), HomeItem.SmartFolder("s", SmartKind.RECENT, "Recent")))
        )
        val reconciled = HomeLayoutOps.reconcileWidgets(layout, setOf(2))

        assertEquals(listOf(app("dock")), reconciled.dock)
        assertEquals(
            listOf<HomeItem>(app("a"), widget(2), HomeItem.SmartFolder("s", SmartKind.RECENT, "Recent")),
            reconciled.page(0)
        )
        assertEquals(setOf(1), HomeLayoutOps.invalidWidgetIds(layout, setOf(2)))
    }

    @Test
    fun `reconcile runs across every page and never touches other items`() {
        val layout = HomeLayout(
            pages = listOf(
                listOf(widget(1), app("a")),
                listOf(widget(2), app("b"), widget(3))
            )
        )
        val reconciled = HomeLayoutOps.reconcileWidgets(layout, setOf(3))
        assertEquals(listOf<HomeItem>(app("a")), reconciled.page(0))
        assertEquals(listOf<HomeItem>(app("b"), widget(3)), reconciled.page(1))
        assertEquals(setOf(1, 2), HomeLayoutOps.invalidWidgetIds(layout, setOf(3)))
    }

    @Test
    fun `an empty valid set removes every widget but keeps the rest of the layout`() {
        val layout = HomeLayout(pages = listOf(listOf(widget(1), app("a"), widget(2))))
        val reconciled = HomeLayoutOps.reconcileWidgets(layout, emptySet())
        assertEquals(listOf<HomeItem>(app("a")), reconciled.page(0))
        assertEquals(setOf(1, 2), HomeLayoutOps.invalidWidgetIds(layout, emptySet()))
    }

    @Test
    fun `codex round trip survives reconciliation results`() {
        val layout = HomeLayout(pages = listOf(listOf(widget(1), app("a"), widget(2))))
        val reconciled = HomeLayoutOps.reconcileWidgets(layout, setOf(2))
        val encoded = HomeLayoutCodec.encode(reconciled)
        assertEquals(reconciled, HomeLayoutCodec.decode(encoded))
    }
}
