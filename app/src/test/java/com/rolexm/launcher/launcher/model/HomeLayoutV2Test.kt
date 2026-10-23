package com.rolexm.launcher.launcher.model

import com.rolexm.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutV2Test {

    private fun key(p: String) = ComponentKey(p, "$p.Main")
    private fun app(p: String) = HomeItem.App(key(p))

    @Test fun `widgets and smart folders round trip through v2`() {
        val layout = HomeLayout(
            dock = listOf(app("phone")),
            pages = listOf(listOf(
                HomeItem.Widget(42, "com.weather/com.weather.Provider", 2),
                app("mail"),
                HomeItem.SmartFolder("s1", SmartKind.FREQUENT, "Frequent | used")
            ))
        )
        val encoded = HomeLayoutCodec.encode(layout)
        assertTrue(encoded.startsWith("v2"))
        assertEquals(layout, HomeLayoutCodec.decode(encoded))
    }

    @Test fun `layouts without new item types are still written as v1`() {
        val encoded = HomeLayoutCodec.encode(HomeLayout(pages = listOf(listOf(app("mail")))))
        assertTrue(encoded.startsWith("v1"))
    }

    @Test fun `a v1 file cannot smuggle in v2 records`() {
        val decoded = HomeLayoutCodec.decode("v1\nD|\nP|W|7|x/y|2,A|0:com.a/com.a.Main")
        assertEquals(listOf<HomeItem>(app("com.a")), decoded.page(0))
    }

    @Test fun `malformed widget records are skipped`() {
        val decoded = HomeLayoutCodec.decode("v2\nD|\nP|W|abc|x/y|2,W|-3|x/y|1,W|9|x/y|99")
        assertEquals(listOf<HomeItem>(HomeItem.Widget(9, "x/y", HomeLayout.MAX_WIDGET_ROWS)), decoded.page(0))
    }

    @Test fun `widgets never load into the dock`() {
        val decoded = HomeLayoutCodec.decode("v2\nD|W|5|x/y|1,A|0:com.a/com.a.Main\nP|")
        assertEquals(listOf<HomeItem>(app("com.a")), decoded.dock)
    }

    @Test fun `move reorders within a page and ignores bad indices`() {
        val l = HomeLayout(pages = listOf(listOf(app("a"), app("b"), app("c"))))
        assertEquals(listOf(app("c"), app("a"), app("b")), HomeLayoutOps.move(l, 0, 2, 0).page(0))
        assertSame(l, HomeLayoutOps.move(l, 0, 0, 9))
    }

    @Test fun `dropping an app on a folder adds it and frees the cell`() {
        val folder = HomeItem.Folder("f", "Tools", listOf(key("x"), key("y")))
        val l = HomeLayout(pages = listOf(listOf(folder, app("a"), app("b"))))
        val dropped = HomeLayoutOps.dropIntoFolder(l, 0, 2, 0)
        assertEquals(2, dropped.page(0).size)
        assertEquals(listOf(key("x"), key("y"), key("b")), (dropped.page(0)[0] as HomeItem.Folder).children)
    }

    @Test fun `dock accepts apps up to its limit but never widgets`() {
        val widget = HomeItem.Widget(3, "p/c", 2)
        val l = HomeLayout(pages = listOf(listOf(widget, app("a"))))
        assertSame(l, HomeLayoutOps.moveHomeToDock(l, 0, 0))
        val moved = HomeLayoutOps.moveHomeToDock(l, 0, 1)
        assertEquals(listOf<HomeItem>(app("a")), moved.dock)
        val full = HomeLayout(dock = (1..HomeLayout.MAX_DOCK).map { app("d$it") }, pages = listOf(listOf(app("z"))))
        assertSame(full, HomeLayoutOps.moveHomeToDock(full, 0, 0))
        assertSame(full, HomeLayoutOps.addToDock(full, key("new")))
    }

    @Test fun `dock and home exchange items`() {
        val l = HomeLayout(dock = listOf(app("phone"), app("web")), pages = listOf(emptyList()))
        val back = HomeLayoutOps.moveDockToHome(l, 1)
        assertEquals(listOf<HomeItem>(app("phone")), back.dock)
        assertEquals(listOf<HomeItem>(app("web")), back.page(0))
        assertEquals(listOf(app("web"), app("phone")), HomeLayoutOps.moveInDock(l, 0, 1).dock)
    }

    @Test fun `widget add, resize and remove`() {
        var l = HomeLayout(pages = listOf(listOf(app("a"))))
        l = HomeLayoutOps.addWidget(l, 0, HomeItem.Widget(11, "p/c", 9), position = 0)
        assertEquals(HomeItem.Widget(11, "p/c", HomeLayout.MAX_WIDGET_ROWS), l.page(0)[0])
        assertSame(l, HomeLayoutOps.addWidget(l, 0, HomeItem.Widget(11, "p/c", 1)))
        l = HomeLayoutOps.resizeWidget(l, 11, 0)
        assertEquals(1, (l.page(0)[0] as HomeItem.Widget).rows)
        assertEquals(setOf(11), l.widgetIds())
        l = HomeLayoutOps.removeWidget(l, 11)
        assertEquals(listOf<HomeItem>(app("a")), l.page(0))
    }

    @Test fun `prune keeps widgets and smart folders`() {
        val l = HomeLayout(pages = listOf(listOf(
            HomeItem.Widget(1, "p/c", 2), HomeItem.SmartFolder("s", SmartKind.RECENT, "Recent"), app("gone")
        )))
        val pruned = HomeLayoutOps.prune(l, emptySet())
        assertEquals(2, pruned.page(0).size)
    }

    @Test fun `strip widgets removes only widgets`() {
        val l = HomeLayout(pages = listOf(listOf(HomeItem.Widget(1, "p/c", 2), app("a"))))
        assertEquals(listOf<HomeItem>(app("a")), HomeLayoutOps.stripWidgets(l).page(0))
    }

    @Test fun `only one smart folder of each kind`() {
        var l = HomeLayout.EMPTY
        l = HomeLayoutOps.addSmartFolder(l, 0, HomeItem.SmartFolder("1", SmartKind.FREQUENT, "Frequent"))
        l = HomeLayoutOps.addSmartFolder(l, 0, HomeItem.SmartFolder("2", SmartKind.FREQUENT, "Again"))
        assertEquals(1, l.page(0).size)
    }

    @Test fun `smart folders can be renamed`() {
        val l = HomeLayout(pages = listOf(listOf(HomeItem.SmartFolder("s", SmartKind.NEW_APPS, "New"))))
        assertEquals("Fresh", (HomeLayoutOps.renameFolder(l, 0, 0, "Fresh").page(0)[0] as HomeItem.SmartFolder).name)
    }

    @Test fun `folder creation accepts a caller supplied name`() {
        val l = HomeLayout(pages = listOf(listOf(app("a"), app("b"))))
        val f = HomeLayoutOps.createFolder(l, 0, 0, 1, "id", "Games").page(0)[0] as HomeItem.Folder
        assertEquals("Games", f.name)
    }
}
