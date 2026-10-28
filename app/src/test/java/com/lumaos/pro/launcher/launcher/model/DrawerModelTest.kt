package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.core.apps.AppMatcher
import com.lumaos.pro.launcher.core.apps.DrawerCategory
import com.lumaos.pro.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerModelTest {

    private fun app(label: String, pkg: String): AppEntry =
        AppEntry(ComponentKey(pkg, "$pkg.Main"), label)

    private val camera = app("Camera", "com.camera")
    private val chrome = app("Chrome", "com.chrome")
    private val clock = app("Clock", "com.clock")
    private val apps = AppMatcher.sort(listOf(camera, chrome, clock))

    @Test
    fun `pinned apps keep their saved order and do not break alphabet offsets`() {
        val output = DrawerModel.build(
            DrawerModel.Input(apps = apps, pinned = listOf(chrome.key, camera.key))
        )

        assertEquals(DrawerRow.Header(DrawerModel.SECTION_PINNED, "Pinned"), output.rows.first())
        assertEquals(listOf("Chrome", "Camera"), output.rows.filterIsInstance<DrawerRow.App>().take(2).map { it.entry.label })
        assertTrue(output.rows.any { it == DrawerRow.Header(DrawerModel.SECTION_ALL, "All apps") })

        output.buckets.forEach { (letter, position) ->
            val row = output.rows[position] as DrawerRow.App
            assertEquals(letter, AppMatcher.bucketOf(row.entry.sortLabel))
        }
        // The complete installed collection remains in All apps as well.
        assertEquals(apps.size, output.appCount)
        assertEquals(apps.size + 2, output.rows.filterIsInstance<DrawerRow.App>().size)
    }

    @Test
    fun `hidden pinned apps are omitted but unpinned visible apps remain`() {
        val output = DrawerModel.build(
            DrawerModel.Input(apps = apps, pinned = listOf(chrome.key), hidden = setOf(chrome.key))
        )

        assertTrue(output.rows.filterIsInstance<DrawerRow.App>().none { it.entry.key == chrome.key })
        assertTrue(output.rows.none { it is DrawerRow.Header && it.id == DrawerModel.SECTION_PINNED })
        assertEquals(2, output.appCount)
    }

    @Test
    fun `explicit search returns one result row without pinned suggestions`() {
        val output = DrawerModel.build(
            DrawerModel.Input(apps = apps, pinned = listOf(chrome.key), query = "  chr   ome ")
        )

        // Whitespace inside a word is not ignored; only repeated word spacing
        // is normalised. This query therefore has a useful empty state.
        assertTrue(output.rows.isEmpty())
        val relaxed = DrawerModel.build(
            DrawerModel.Input(apps = apps, pinned = listOf(chrome.key), query = "  CHROME ")
        )
        assertEquals(listOf("Chrome"), relaxed.rows.filterIsInstance<DrawerRow.App>().map { it.entry.label })
        assertTrue(relaxed.buckets.isEmpty())
    }

    @Test
    fun `disabling category chips keeps available all and frequent filters usable`() {
        assertEquals(
            DrawerFilter.Frequent,
            DrawerModel.availableFilter(
                DrawerFilter.Frequent, emptyList(), categoriesEnabled = false, frequentEnabled = true
            )
        )
        assertEquals(
            DrawerFilter.All,
            DrawerModel.availableFilter(
                DrawerFilter.Category(DrawerCategory.GAMES), emptyList(),
                categoriesEnabled = false, frequentEnabled = true
            )
        )
    }

    @Test
    fun `a category removed from the available app set cannot strand the drawer`() {
        assertEquals(
            DrawerFilter.All,
            DrawerModel.availableFilter(
                DrawerFilter.Category(DrawerCategory.GAMES), listOf(DrawerCategory.PRODUCTIVITY),
                categoriesEnabled = true, frequentEnabled = true
            )
        )
        assertEquals(
            DrawerFilter.All,
            DrawerModel.availableFilter(
                DrawerFilter.Frequent, emptyList(), categoriesEnabled = true, frequentEnabled = false
            )
        )
    }

    @Test
    fun `presentation parser rejects corrupt values safely`() {
        assertEquals(DrawerPresentation.LIST, DrawerPresentation.fromStorageKey("list"))
        assertEquals(DrawerPresentation.GRID, DrawerPresentation.fromStorageKey("grid"))
        assertEquals(null, DrawerPresentation.fromStorageKey("unexpected"))
    }

    @Test
    fun `no local results are explained even when external search is available`() {
        val output = DrawerModel.build(
            DrawerModel.Input(apps = apps, query = "  missing  ", webSearchAvailable = true, storeAvailable = true)
        )
        assertEquals("missing", output.query)
        assertEquals(DrawerRow.Empty("missing"), output.rows.first())
        assertEquals(2, output.rows.filterIsInstance<DrawerRow.Action>().size)
        assertEquals(0, output.appCount)
        assertTrue(output.buckets.isEmpty())
    }

    @Test
    fun `a matching shortcut does not show the empty explanation`() {
        val output = DrawerModel.build(
            DrawerModel.Input(
                apps = apps, query = "timer", webSearchAvailable = true,
                shortcuts = listOf(ShortcutRef(clock.key.packageName, "timer", "Timer"))
            )
        )
        assertTrue(output.rows.any { it is DrawerRow.Shortcut })
        assertTrue(output.rows.none { it is DrawerRow.Empty })
    }

    @Test
    fun `clearing search restores the selected category and alphabetical buckets`() {
        val categorised = apps.map { it.copy(category = if (it.key == clock.key) 7 else 0) }
        val filter = DrawerFilter.Category(DrawerCategory.GAMES)
        val searched = DrawerModel.build(DrawerModel.Input(apps = categorised, query = "clock", filter = filter))
        val cleared = DrawerModel.build(DrawerModel.Input(apps = categorised, query = " ", filter = filter))
        assertEquals("clock", searched.query)
        assertEquals(listOf("Clock"), searched.rows.filterIsInstance<DrawerRow.App>().map { it.entry.label })
        assertEquals("", cleared.query)
        assertEquals(listOf("Camera", "Chrome"), cleared.rows.filterIsInstance<DrawerRow.App>().map { it.entry.label })
        assertEquals(2, cleared.appCount)
        assertTrue(cleared.buckets.isNotEmpty())
    }

}
