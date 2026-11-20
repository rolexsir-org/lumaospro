package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.core.apps.AppMatcher
import com.lumaos.pro.launcher.core.apps.DrawerSort
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.core.usage.LaunchRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObsidianProjectionTest {
    private fun app(label: String, installedAt: Long = 0) =
        AppEntry(ComponentKey("test.${label.lowercase()}", "test.$label.Main"), label, installedAt = installedAt)

    private val alpha = app("Alpha", 1)
    private val beta = app("Beta", 2)
    private val gamma = app("Gamma", 3)
    private val delta = app("Delta", 4)
    private val apps = AppMatcher.sort(listOf(alpha, beta, gamma, delta))
    private fun keys(output: DrawerModel.Output) =
        ObsidianProjection.project(output).rows.filterIsInstance<DrawerRow.App>().map { it.entry.key }

    @Test fun `pins and genuine recents lead one deduplicated grid`() {
        val output = DrawerModel.build(DrawerModel.Input(
            apps = apps, pinned = listOf(beta.key, beta.key), hidden = setOf(gamma.key),
            history = listOf(LaunchRecord(alpha.key, 5, 100, 5.0, 100)), nowMillis = 100
        ))
        val grid = ObsidianProjection.project(output)
        assertEquals(listOf(beta.key, alpha.key, delta.key), keys(output))
        assertEquals(3, grid.appCount)
        assertTrue(grid.buckets.isEmpty())
    }

    @Test fun `disabled learning retains pins but not recent ordering`() {
        val input = DrawerModel.Input(apps = apps, pinned = listOf(beta.key), suggestionsEnabled = false,
            history = listOf(LaunchRecord(gamma.key, 3, 100, 3.0, 100)))
        assertEquals(listOf(beta.key, alpha.key, delta.key, gamma.key), keys(DrawerModel.build(input)))
    }

    @Test fun `remaining apps follow each persisted sort while hidden and removed pins stay absent`() {
        val removed = app("Removed")
        val input = DrawerModel.Input(apps = apps, hidden = setOf(beta.key), pinned = listOf(beta.key, removed.key),
            history = listOf(LaunchRecord(gamma.key, 4, 100, 8.0, 100)), suggestionsEnabled = false)
        assertEquals(listOf(alpha.key, delta.key, gamma.key), keys(DrawerModel.build(input.copy(sort = DrawerSort.ALPHABETICAL))))
        assertEquals(listOf(delta.key, gamma.key, alpha.key), keys(DrawerModel.build(input.copy(sort = DrawerSort.NEWEST))))
        // Learning disabled deliberately prevents usage sorting from reading history.
        assertEquals(listOf(alpha.key, delta.key, gamma.key), keys(DrawerModel.build(input.copy(sort = DrawerSort.MOST_USED))))
        assertEquals(listOf(gamma.key, alpha.key, delta.key), keys(DrawerModel.build(input.copy(
            sort = DrawerSort.MOST_USED, suggestionsEnabled = true, suggestionCount = 0, nowMillis = 100))))
    }

    @Test fun `distinct personal and work components are each shown once`() {
        val work = alpha.copy(key = alpha.key.copy(userSerial = 42))
        val input = DrawerModel.Input(apps = AppMatcher.sort(listOf(alpha, work, alpha)),
            pinned = listOf(work.key, alpha.key))
        assertEquals(listOf(work.key, alpha.key), keys(DrawerModel.build(input)))
    }

    @Test fun `matching shortcuts share the grid while external actions remain outside it`() {
        val output = DrawerModel.build(DrawerModel.Input(
            apps = apps, query = "alpha", webSearchAvailable = true, storeAvailable = true,
            shortcuts = listOf(ShortcutRef(alpha.key.packageName, "shortcut", "Alpha shortcut"))
        ))
        val grid = ObsidianProjection.project(output)
        assertEquals(listOf(alpha.key), keys(output))
        assertEquals(1, grid.rows.filterIsInstance<DrawerRow.Shortcut>().size)
        assertTrue(grid.rows.none { it is DrawerRow.Header || it is DrawerRow.Action })
        assertEquals("alpha", grid.query)
        assertTrue(ObsidianProjection.project(DrawerModel.build(DrawerModel.Input(
            apps = apps, query = "missing", webSearchAvailable = true
        ))).rows.isEmpty())
    }

    @Test fun `shortcut search does not expose hidden personal app through visible work copy`() {
        val work = alpha.copy(key = alpha.key.copy(userSerial = 42))
        val input = DrawerModel.Input(apps = listOf(alpha, work), query = "shortcut", hidden = setOf(alpha.key),
            shortcuts = listOf(ShortcutRef(alpha.key.packageName, "shortcut", "Shortcut")))
        assertTrue(ObsidianProjection.project(DrawerModel.build(input)).rows.isEmpty())
    }

    @Test fun `hidden search policy remains intact`() {
        val input = DrawerModel.Input(apps = apps, query = "beta", hidden = setOf(beta.key))
        assertTrue(keys(DrawerModel.build(input)).isEmpty())
        assertEquals(listOf(beta.key), keys(DrawerModel.build(input.copy(hiddenInSearch = true))))
    }

    @Test fun `empty installed snapshot stays empty`() {
        assertTrue(ObsidianProjection.project(DrawerModel.build(DrawerModel.Input(apps = emptyList()))).rows.isEmpty())
    }
}
