package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    private val camera = ComponentKey("com.camera", "com.camera.Main")
    private val clock = ComponentKey("com.clock", "com.clock.Main")
    private val phone = ComponentKey("com.phone", "com.phone.Main")
    private val music = ComponentKey("com.music", "com.music.Main")

    private fun layoutWith(vararg apps: ComponentKey) =
        HomeLayout(pages = listOf(apps.map { HomeItem.App(it) }))

    @Test
    fun `adds to home and refuses duplicates`() {
        var layout = HomeLayout.EMPTY
        layout = HomeLayoutOps.addToHome(layout, camera)
        layout = HomeLayoutOps.addToHome(layout, camera)
        assertEquals(1, layout.page(0).size)
    }

    @Test
    fun `adds to dock and refuses duplicates`() {
        var layout = HomeLayout.EMPTY
        layout = HomeLayoutOps.addToDock(layout, phone)
        layout = HomeLayoutOps.addToDock(layout, phone)
        assertEquals(1, layout.dock.size)
    }

    @Test
    fun `an app already inside a folder counts as present`() {
        val layout = HomeLayoutOps.addToHome(HomeLayout.EMPTY, camera)
        val foldered = HomeLayoutOps.setPage(layout, 0, listOf(HomeItem.Folder("f1", "F", listOf(camera))))
        assertTrue(HomeLayoutOps.containsKey(foldered, camera))
    }

    @Test
    fun `removing an out of range position is a no op`() {
        val layout = layoutWith(camera)
        assertEquals(layout, HomeLayoutOps.removeFromHome(layout, 0, 99))
        assertEquals(layout, HomeLayoutOps.removeFromHome(layout, 0, -1))
    }

    @Test
    fun `merging two apps creates a folder at the lower position`() {
        val layout = layoutWith(camera, clock, phone)
        val merged = HomeLayoutOps.createFolder(layout, 0, 0, 2, "f1")
        val folder = merged.page(0)[0] as HomeItem.Folder
        assertEquals(listOf(camera, phone), folder.children)
        // The untouched middle item stays in place.
        assertEquals(HomeItem.App(clock), merged.page(0)[1])
        assertEquals(2, merged.page(0).size)
    }

    @Test
    fun `merging refuses non apps and identical positions`() {
        val layout = layoutWith(camera, clock)
        val foldered = HomeLayoutOps.createFolder(layout, 0, 0, 1, "f1")
        val again = HomeLayoutOps.createFolder(foldered, 0, 0, 0, "f2")
        assertEquals(foldered, again)
        assertEquals(layout, HomeLayoutOps.createFolder(layout, 0, 0, 0, "f"))
        assertEquals(layout, HomeLayoutOps.createFolder(layout, 0, 0, 99, "f"))
    }

    @Test
    fun `adding to a folder ignores duplicates`() {
        var layout = HomeLayoutOps.createFolder(layoutWith(camera, clock), 0, 0, 1, "f1")
        layout = HomeLayoutOps.addToFolder(layout, 0, 0, camera)
        assertEquals(2, (layout.page(0)[0] as HomeItem.Folder).children.size)
    }

    @Test
    fun `renaming only affects folders`() {
        val layout = HomeLayoutOps.createFolder(layoutWith(camera, clock), 0, 0, 1, "f1")
        val renamed = HomeLayoutOps.renameFolder(layout, 0, 0, "Utilities")
        assertEquals("Utilities", (renamed.page(0)[0] as HomeItem.Folder).name)
        val unchanged = HomeLayoutOps.renameFolder(layoutWith(camera), 0, 0, "Nope")
        assertEquals(layoutWith(camera), unchanged)
    }

    @Test
    fun `pruning drops uninstalled apps`() {
        val layout = layoutWith(camera, clock, phone)
        val pruned = HomeLayoutOps.prune(layout, setOf(camera, phone))
        assertEquals(listOf(HomeItem.App(camera), HomeItem.App(phone)), pruned.page(0))
    }

    @Test
    fun `pruning removes an empty folder`() {
        val layout = HomeLayoutOps.createFolder(layoutWith(camera, clock), 0, 0, 1, "f1")
        val pruned = HomeLayoutOps.prune(layout, emptySet())
        assertTrue(pruned.page(0).isEmpty())
    }

    @Test
    fun `pruning collapses a single child folder back to an icon`() {
        val layout = HomeLayoutOps.createFolder(layoutWith(camera, clock), 0, 0, 1, "f1")
        val pruned = HomeLayoutOps.prune(layout, setOf(camera))
        assertEquals(listOf(HomeItem.App(camera)), pruned.page(0))
    }

    @Test
    fun `pruning leaves a healthy folder intact`() {
        var layout = HomeLayoutOps.createFolder(layoutWith(camera, clock), 0, 0, 1, "f1")
        layout = HomeLayoutOps.addToFolder(layout, 0, 0, music)
        val pruned = HomeLayoutOps.prune(layout, setOf(camera, clock, music))
        assertEquals(3, (pruned.page(0)[0] as HomeItem.Folder).children.size)
    }

    @Test
    fun `an empty app scan does not prune saved placements`() {
        val original = layoutWith(camera, clock)
        assertEquals(original, HomeLayoutOps.pruneAfterScan(original, emptySet()))
        assertEquals(layoutWith(camera), HomeLayoutOps.pruneAfterScan(original, setOf(camera)))
    }

    @Test
    fun `pruning also cleans the dock`() {
        val layout = HomeLayoutOps.addToDock(HomeLayout.EMPTY, phone)
        assertTrue(HomeLayoutOps.prune(layout, emptySet()).dock.isEmpty())
    }
}

class HomeLayoutCodecTest {

    private val camera = ComponentKey("com.camera", "com.camera.Main")
    private val clock = ComponentKey("com.clock", "com.clock.Main")
    private val maps = ComponentKey("com.maps", "com.maps.Main")

    @Test
    fun `round trips an empty layout`() {
        val decoded = HomeLayoutCodec.decode(HomeLayoutCodec.encode(HomeLayout.EMPTY))
        assertTrue(decoded.dock.isEmpty())
        assertTrue(decoded.page(0).isEmpty())
    }

    @Test
    fun `round trips apps, folders and the dock`() {
        var layout = HomeLayoutOps.addToDock(HomeLayout.EMPTY, camera)
        layout = HomeLayoutOps.addToHome(layout, clock)
        layout = HomeLayoutOps.addToHome(layout, maps)
        layout = HomeLayoutOps.createFolder(layout, 0, 0, 1, "folder-1")

        val decoded = HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout))
        assertEquals(layout.dock, decoded.dock)
        assertEquals(layout.page(0), decoded.page(0))
    }

    @Test
    fun `null or blank input decodes to an empty layout`() {
        assertTrue(HomeLayoutCodec.decode(null).dock.isEmpty())
        assertTrue(HomeLayoutCodec.decode("   ").dock.isEmpty())
    }

    @Test
    fun `automatic maintenance refuses unknown and truncated layouts`() {
        val canonical = HomeLayoutCodec.encode(HomeLayoutOps.addToHome(HomeLayout.EMPTY, camera))
        assertTrue(HomeLayoutCodec.canSafelyRewrite(null))
        assertTrue(HomeLayoutCodec.canSafelyRewrite(canonical))
        assertFalse(HomeLayoutCodec.canSafelyRewrite(""))
        assertFalse(HomeLayoutCodec.canSafelyRewrite("v99\nD|\nP|\n"))
        assertFalse(HomeLayoutCodec.canSafelyRewrite(canonical.removeSuffix("\n")))
        assertFalse(HomeLayoutCodec.canSafelyRewrite("v1\nD|\nP|A|broken\n"))
    }

    @Test
    fun `widget release check retains ids for unknown or malformed layouts`() {
        assertTrue(HomeLayoutCodec.mayReferenceWidget("v99\nP|new-format", 42))
        assertTrue(HomeLayoutCodec.mayReferenceWidget("v2", 42))
        assertTrue(HomeLayoutCodec.mayReferenceWidget("", 42))
        assertFalse(HomeLayoutCodec.mayReferenceWidget(null, 42))
        assertTrue(HomeLayoutCodec.mayReferenceWidget("v2\nP|W|42|broken", 42))
        assertTrue(HomeLayoutCodec.mayReferenceWidget("v2\nD|\nP|W|not-a-number|provider|2\n", 42))
        assertTrue(HomeLayoutCodec.mayReferenceWidget("v2\nD|\nP|W|43||2\n", 42))
        val otherWidget = HomeLayoutCodec.encode(HomeLayout(pages = listOf(listOf(HomeItem.Widget(43, "provider", 2)))))
        val referenced = HomeLayoutCodec.encode(HomeLayout(pages = listOf(listOf(HomeItem.Widget(42, "provider", 2)))))
        assertTrue(HomeLayoutCodec.mayReferenceWidget(referenced, 42))
        assertFalse(HomeLayoutCodec.mayReferenceWidget(otherWidget, 42))
        assertTrue(HomeLayoutCodec.mayReferenceWidget(otherWidget.removeSuffix("\n"), 42))
        assertTrue(HomeLayoutCodec.mayReferenceWidget("v1\nD|\nP|", 42))
        assertFalse(HomeLayoutCodec.mayReferenceWidget(HomeLayoutCodec.encode(HomeLayout.EMPTY), 42))
    }

    @Test
    fun `an unknown version decodes to an empty layout rather than crashing`() {
        assertTrue(HomeLayoutCodec.decode("v99\nD|A|0:a/b").dock.isEmpty())
    }

    @Test
    fun `malformed records are skipped, not fatal`() {
        val decoded = HomeLayoutCodec.decode("v1\nD|A|garbage,A|0:com.x/com.x.Main\nP|")
        assertEquals(1, decoded.dock.size)
        assertEquals(ComponentKey("com.x", "com.x.Main"), (decoded.dock[0] as HomeItem.App).key)
    }

    @Test
    fun `folder names round trip with structural characters escaped`() {
        val nasty = "a|b;c,d\ne\\f"
        val folder = HomeItem.Folder("id|1", nasty, listOf(camera))
        val layout = HomeLayout(pages = listOf(listOf(folder)))
        val decoded = HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout))
        assertEquals(nasty, (decoded.page(0)[0] as HomeItem.Folder).name)
        assertEquals("id|1", (decoded.page(0)[0] as HomeItem.Folder).id)
    }

    @Test
    fun `escaping is symmetric for tricky sequences`() {
        val cases = listOf("plain", "", "a|b", "a;b", "a,b", "line\nbreak", "back\\slash", "\\\\")
        cases.forEach { assertEquals(it, HomeLayoutCodec.unescape(HomeLayoutCodec.escape(it))) }
    }

    @Test
    fun `a single page is preserved as a single page`() {
        val layout = HomeLayoutOps.addToHome(HomeLayout.EMPTY, camera)
        assertEquals(1, HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout)).pageCount)
    }

    @Test
    fun `a layout with no pages still reports one empty page`() {
        val decoded = HomeLayoutCodec.decode("v1\nD|")
        assertEquals(1, decoded.pageCount)
        assertTrue(decoded.page(0).isEmpty())
    }
}
