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
    private val apps = AppMatcher.sort(listOf(alpha, beta, gamma))

    @Test fun `one grid contains every visible app once despite curated sections`() {
        val output = DrawerModel.build(DrawerModel.Input(
            apps = apps, pinned = listOf(beta.key), hidden = setOf(gamma.key),
            history = listOf(LaunchRecord(alpha.key, 1, 100, 1.0, 100)), nowMillis = 100
        ))
        val grid = ObsidianProjection.project(output)
        assertEquals(listOf(alpha.key, beta.key), grid.rows.map { (it as DrawerRow.App).entry.key })
        assertEquals(2, grid.appCount)
        assertTrue(grid.buckets.isEmpty())
    }

    @Test fun `grid respects existing sort and excludes hidden and removed apps`() {
        val removed = app("Removed")
        val output = DrawerModel.build(DrawerModel.Input(
            apps = apps, sort = DrawerSort.NEWEST, hidden = setOf(beta.key), pinned = listOf(beta.key, removed.key)
        ))
        assertEquals(listOf(gamma.key, alpha.key), ObsidianProjection.project(output).rows.map { (it as DrawerRow.App).entry.key })
    }

    @Test fun `search contains only matching real apps and no external action rows`() {
        val output = DrawerModel.build(DrawerModel.Input(
            apps = apps, query = "alpha", webSearchAvailable = true, storeAvailable = true,
            shortcuts = listOf(ShortcutRef(alpha.key.packageName, "shortcut", "Alpha shortcut"))
        ))
        val grid = ObsidianProjection.project(output)
        assertEquals(listOf(alpha.key), grid.rows.map { (it as DrawerRow.App).entry.key })
        assertEquals("alpha", grid.query)
        assertTrue(ObsidianProjection.project(DrawerModel.build(DrawerModel.Input(
            apps = apps, query = "missing", webSearchAvailable = true
        ))).rows.isEmpty())
    }

    @Test fun `hidden search policy remains intact`() {
        val input = DrawerModel.Input(apps = apps, query = "beta", hidden = setOf(beta.key))
        assertTrue(ObsidianProjection.project(DrawerModel.build(input)).rows.isEmpty())
        assertEquals(listOf(beta.key), ObsidianProjection.project(DrawerModel.build(input.copy(hiddenInSearch = true)))
            .rows.map { (it as DrawerRow.App).entry.key })
    }

    @Test fun `empty installed snapshot stays empty`() {
        assertTrue(ObsidianProjection.project(DrawerModel.build(DrawerModel.Input(apps = emptyList()))).rows.isEmpty())
    }
}
