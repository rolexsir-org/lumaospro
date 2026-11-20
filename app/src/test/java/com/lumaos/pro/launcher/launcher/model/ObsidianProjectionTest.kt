package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.core.apps.AppMatcher
import com.lumaos.pro.launcher.core.apps.DrawerCategory
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.core.usage.LaunchRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObsidianProjectionTest {
    private fun app(label: String, category: Int = -1) =
        AppEntry(ComponentKey("test.${label.lowercase()}", "test.$label.Main"), label, category = category)

    private val alpha = app("Alpha", 0)
    private val beta = app("Beta", 7)
    private val gamma = app("Gamma")
    private val apps = AppMatcher.sort(listOf(alpha, beta, gamma))

    @Test fun `overview keeps real curated rows and library contains every visible app`() {
        val output = DrawerModel.build(DrawerModel.Input(
            apps = apps, pinned = listOf(beta.key), hidden = setOf(gamma.key),
            history = listOf(LaunchRecord(alpha.key, 1, 100, 1.0, 100)), nowMillis = 100
        ))
        val overview = ObsidianProjection.project(output, ObsidianPage.OVERVIEW)
        val library = ObsidianProjection.project(output, ObsidianPage.LIBRARY)
        assertEquals(listOf(beta.key, alpha.key), overview.rows.filterIsInstance<DrawerRow.App>().map { it.entry.key })
        assertEquals(listOf(alpha.key, beta.key), library.rows.filterIsInstance<DrawerRow.App>().map { it.entry.key })
        assertTrue(overview.buckets.isEmpty())
        assertEquals(2, library.appCount)
        library.buckets.forEach { (letter, index) ->
            assertEquals(letter, AppMatcher.bucketOf((library.rows[index] as DrawerRow.App).entry.sortLabel))
        }
    }

    @Test fun `empty overview does not hide the library`() {
        val output = DrawerModel.build(DrawerModel.Input(apps = apps))
        assertTrue(ObsidianProjection.project(output, ObsidianPage.OVERVIEW).rows.isEmpty())
        assertEquals(apps, ObsidianProjection.project(output, ObsidianPage.LIBRARY)
            .rows.filterIsInstance<DrawerRow.App>().map { it.entry })
    }

    @Test fun `uninstalled and hidden favourites and recents never appear`() {
        val removed = app("Removed")
        val output = DrawerModel.build(DrawerModel.Input(
            apps = apps, hidden = setOf(beta.key), pinned = listOf(beta.key, removed.key),
            history = listOf(LaunchRecord(beta.key, 1, 100, 1.0, 100), LaunchRecord(removed.key, 1, 100, 1.0, 100))
        ))
        assertTrue(ObsidianProjection.project(output, ObsidianPage.OVERVIEW).rows.isEmpty())
        assertEquals(listOf(alpha.key, gamma.key), ObsidianProjection.project(output, ObsidianPage.LIBRARY)
            .rows.filterIsInstance<DrawerRow.App>().map { it.entry.key })
    }

    @Test fun `search and categories remain complete regardless of selected page`() {
        val input = DrawerModel.Input(apps = apps, pinned = listOf(beta.key))
        val search = DrawerModel.build(input.copy(query = "alpha"))
        val category = DrawerModel.build(input.copy(filter = DrawerFilter.Category(DrawerCategory.GAMES)))
        for (page in ObsidianPage.entries) {
            assertEquals(search, ObsidianProjection.project(search, page))
            assertEquals(category, ObsidianProjection.project(category, page))
            assertEquals(listOf(alpha.key), ObsidianProjection.project(category, page).rows
                .filterIsInstance<DrawerRow.App>().map { it.entry.key })
        }
    }

    @Test fun `empty installed snapshot stays empty in both pages`() {
        val empty = DrawerModel.build(DrawerModel.Input(apps = emptyList()))
        for (page in ObsidianPage.entries) {
            assertTrue(ObsidianProjection.project(empty, page).rows.isEmpty())
        }
    }
}
