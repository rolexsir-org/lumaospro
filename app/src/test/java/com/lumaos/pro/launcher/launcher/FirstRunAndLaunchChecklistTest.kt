package com.lumaos.pro.launcher.launcher

import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.core.apps.DefaultDockResolver
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.launcher.model.HomeItem
import com.lumaos.pro.launcher.launcher.model.HomeLayout
import com.lumaos.pro.launcher.launcher.model.HomeLayoutOps
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunAndLaunchChecklistTest {

    private fun entry(pkg: String, label: String): AppEntry = AppEntry(
        key = ComponentKey(pkg, "$pkg.MainActivity", 0L),
        label = label
    )

    @Test
    fun firstRunHomeCapacityLeavesOneToTwoEmptyRowsForWallpaper() {
        // 4x4 grid -> fills 2 rows (8 apps), leaving 2 full empty rows!
        assertEquals(8, DefaultDockResolver.computeFirstRunHomeCapacity(columns = 4, rows = 4))
        // 4x5 grid -> fills 3 rows (12 apps), leaving 2 full empty rows!
        assertEquals(12, DefaultDockResolver.computeFirstRunHomeCapacity(columns = 4, rows = 5))
        // 5x5 grid -> fills 3 rows (15 apps), leaving 2 full empty rows!
        assertEquals(15, DefaultDockResolver.computeFirstRunHomeCapacity(columns = 5, rows = 5))
    }

    @Test
    fun firstRunHomeSelectorHonoursUsageStatsExcludesDockAndSelf() {
        val phone = entry("com.android.dialer", "Phone")
        val sms = entry("com.google.android.apps.messaging", "Messages")
        val browser = entry("com.android.chrome", "Chrome")
        val camera = entry("com.android.camera2", "Camera")
        val self = entry("com.lumaos.pro.launcher", "LumaOS Pro")
        val photos = entry("com.google.android.apps.photos", "Photos")
        val maps = entry("com.google.android.apps.maps", "Maps")
        val slack = entry("com.Slack", "Slack")
        val calculator = entry("com.google.android.calculator", "Calculator")

        val dock = listOf(phone.key, sms.key, browser.key, camera.key)
        val all = listOf(phone, sms, browser, camera, self, photos, maps, slack, calculator)
        val usage = mapOf("com.Slack" to 120_000L)

        val picked = DefaultDockResolver.selectInitialHomeApps(
            apps = all,
            dockKeys = dock,
            selfPackage = "com.lumaos.pro.launcher",
            usageMillisByPackage = usage,
            maxCount = 4
        )

        assertEquals(4, picked.size)
        // Slack ranks #1 from UsageStats; Photos & Maps follow via essential priority; self & dock excluded!
        assertEquals("com.Slack", picked[0].packageName)
        assertFalse(picked.any { it.packageName == "com.lumaos.pro.launcher" })
        assertTrue(picked.intersect(dock.toSet()).isEmpty())
    }

    @Test
    fun seedFirstRunIfEmptyPopulatesFourIconDockAndPreservesExistingLayouts() {
        val dockKeys = (1..5).map { ComponentKey("dock.$it", "Main", 0L) }
        val homeKeys = (1..6).map { ComponentKey("home.$it", "Main", 0L) }

        val seeded = HomeLayoutOps.seedFirstRunIfEmpty(HomeLayout.EMPTY, dockKeys, homeKeys)
        assertEquals(LumaTokens.dockIconCount, seeded.dock.size)
        assertEquals(6, seeded.page(0).size)

        // Existing or restored user layout must NEVER be overwritten!
        val existing = HomeLayout(
            dock = listOf(HomeItem.App(ComponentKey("custom.app", "Main", 0L))),
            pages = listOf(emptyList())
        )
        assertSame(existing, HomeLayoutOps.seedFirstRunIfEmpty(existing, dockKeys, homeKeys))
    }
}
