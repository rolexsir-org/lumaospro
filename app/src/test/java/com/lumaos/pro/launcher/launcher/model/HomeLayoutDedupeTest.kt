package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repairing a layout that arrived with the same thing twice.
 *
 * The mutation paths refuse to create duplicates, but saved layouts outlive the
 * code that wrote them: an older build, a cloud restore or a hand-edited backup
 * can hold two cells for one app, or two cells bound to one widget id, and on
 * screen that is the "duplicated, overlapping icons" a restored layout produces.
 * These tests pin both the repair and its limits - nothing is reordered, no page
 * disappears, and a clean layout is returned untouched.
 */
class HomeLayoutDedupeTest {

    private fun key(packageName: String) = ComponentKey(packageName, "$packageName.Main")
    private fun app(packageName: String) = HomeItem.App(key(packageName))
    private fun widget(id: Int, rows: Int = 2) = HomeItem.Widget(id, "com.p/Provider$id", rows)
    private fun folder(id: String, vararg members: String) =
        HomeItem.Folder(id, "Folder $id", members.map { key(it) })
    private fun smart(id: String, kind: SmartKind = SmartKind.FREQUENT) =
        HomeItem.SmartFolder(id, kind, kind.name)

    @Test
    fun `a layout without duplicates comes back as the same instance`() {
        val clean = HomeLayout(
            dock = listOf(app("phone")),
            pages = listOf(listOf(app("mail"), widget(7)), listOf(folder("f1", "a", "b")))
        )
        assertSame(clean, HomeLayoutOps.dedupe(clean))
    }

    @Test
    fun `the dock wins over a page copy of the same app`() {
        val duplicated = HomeLayout(
            dock = listOf(app("mail")),
            pages = listOf(listOf(app("mail"), app("calendar")))
        )
        val repaired = HomeLayoutOps.dedupe(duplicated)
        assertEquals(listOf<HomeItem>(app("mail")), repaired.dock)
        assertEquals(listOf<HomeItem>(app("calendar")), repaired.page(0))
    }

    @Test
    fun `the first cell on a page wins and the rest keep their order`() {
        val duplicated = HomeLayout(
            pages = listOf(listOf(app("a"), app("b"), app("a"), app("c"), app("b")))
        )
        assertEquals(
            listOf<HomeItem>(app("a"), app("b"), app("c")),
            HomeLayoutOps.dedupe(duplicated).page(0)
        )
    }

    @Test
    fun `two cells bound to one widget id collapse to the first`() {
        val duplicated = HomeLayout(
            pages = listOf(listOf(widget(11, rows = 2), app("a"), widget(11, rows = 4)))
        )
        val repaired = HomeLayoutOps.dedupe(duplicated)
        assertEquals(listOf<HomeItem>(widget(11, rows = 2), app("a")), repaired.page(0))
        assertEquals(setOf(11), repaired.widgetIds())
    }

    @Test
    fun `a folder keeps every member and a loose copy of one is the duplicate`() {
        val duplicated = HomeLayout(
            pages = listOf(listOf(folder("f1", "x", "y"), app("y"), app("z")))
        )
        val repaired = HomeLayoutOps.dedupe(duplicated)
        // The folder is intact: dropping a member to fix the duplicate would lose
        // data the user arranged.
        assertEquals(listOf<HomeItem>(folder("f1", "x", "y"), app("z")), repaired.page(0))
    }

    @Test
    fun `a loose app placed before its folder still yields to the folder`() {
        val duplicated = HomeLayout(
            pages = listOf(listOf(app("y"), folder("f1", "x", "y")))
        )
        val repaired = HomeLayoutOps.dedupe(duplicated)
        assertEquals(listOf<HomeItem>(folder("f1", "x", "y")), repaired.page(0))
    }

    @Test
    fun `duplicate folder and smart folder ids collapse to the first`() {
        val duplicated = HomeLayout(
            pages = listOf(listOf(
                folder("f1", "x", "y"), folder("f1", "x", "y"),
                smart("s1"), smart("s1")
            ))
        )
        assertEquals(
            listOf<HomeItem>(folder("f1", "x", "y"), smart("s1")),
            HomeLayoutOps.dedupe(duplicated).page(0)
        )
    }

    @Test
    fun `page count, page order and empty pages survive the repair`() {
        val duplicated = HomeLayout(
            dock = listOf(app("phone"), app("phone")),
            pages = listOf(listOf(app("a")), emptyList(), listOf(app("a"), app("b")))
        )
        val repaired = HomeLayoutOps.dedupe(duplicated)
        assertEquals(3, repaired.pageCount)
        assertEquals(listOf<HomeItem>(app("phone")), repaired.dock)
        assertEquals(listOf<HomeItem>(app("a")), repaired.page(0))
        assertTrue(repaired.page(1).isEmpty())
        assertEquals(listOf<HomeItem>(app("b")), repaired.page(2))
    }

    @Test
    fun `a repaired layout is stable under a second repair`() {
        val duplicated = HomeLayout(
            dock = listOf(app("mail")),
            pages = listOf(listOf(app("mail"), widget(3), widget(3), folder("f", "a", "b"), app("a")))
        )
        val once = HomeLayoutOps.dedupe(duplicated)
        assertSame(once, HomeLayoutOps.dedupe(once))
    }

    @Test
    fun `a repaired layout round-trips through the codec unchanged`() {
        val duplicated = HomeLayout(
            dock = listOf(app("mail")),
            pages = listOf(listOf(app("mail"), widget(3), widget(3)))
        )
        val repaired = HomeLayoutOps.dedupe(duplicated)
        val encoded = HomeLayoutCodec.encode(repaired)
        assertEquals(repaired, HomeLayoutCodec.decode(encoded))
        // And the repaired file is now its own recovery proof again.
        assertTrue(HomeLayoutCodec.canSafelyRewrite(encoded))
    }

    @Test
    fun `decode stays byte-faithful so the recovery proof is about the file`() {
        // The repair lives where storage becomes UI state, not in the decoder:
        // canSafelyRewrite proves the parser skipped no record, and that proof
        // would be meaningless if decode also silently rewrote what it read.
        val duplicatedRaw = "v1\nD|A|0:com.mail/com.mail.Main\nP|A|0:com.mail/com.mail.Main,A|0:com.cal/com.cal.Main\n"
        val decoded = HomeLayoutCodec.decode(duplicatedRaw)
        assertEquals(2, decoded.page(0).size)
        assertTrue(HomeLayoutCodec.canSafelyRewrite(duplicatedRaw))
        assertEquals(1, HomeLayoutOps.dedupe(decoded).page(0).size)
        assertFalse(HomeLayoutOps.dedupe(decoded) == decoded)
    }
}
