package com.lumaos.pro.launcher.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lumaos.pro.launcher.core.prefs.LumaPrefs
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.ui.theme.Backdrop
import com.lumaos.pro.launcher.ui.theme.ExperienceProfile
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import com.lumaos.pro.launcher.launcher.model.DockStyle
import com.lumaos.pro.launcher.launcher.model.HomePreset
import com.lumaos.pro.launcher.effects.EffectsPreference
import com.lumaos.pro.launcher.motion.MotionPreference
import com.lumaos.pro.launcher.ui.theme.ThemeSwitching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Regression coverage for the settings persistence path that the onboarding and
 * settings screens share. A profile switch must write the whole appearance
 * bundle through [LumaPrefs], while user content and first-run state stay put.
 */
class SettingsPersistenceRegressionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun dataStore(scope: CoroutineScope, file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `wallpaper favorites and targeted appearance fields persist without changing home or other theme data`() = runBlocking {
        val file = temporaryFolder.newFile("wallpaper.preferences_pb")
        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(firstJob + Dispatchers.Default)
        val originalTheme = ThemeSettings(themeId = ThemeId.MONOLITH, switching = ThemeSwitching.FIXED,
            backdrop = Backdrop.THEME, backgroundOverride = 0xFF102030.toInt())
        try {
            val prefs = LumaPrefs(dataStore(firstScope, file))
            prefs.applyThemeSettings(originalTheme)
            prefs.setHomeLayoutRaw("home-with-widget")
            prefs.setWallpaperFavorite("art:zen_morning", true)
            prefs.setWallpaperFavorite("photo:01234567-89ab-cdef-0123-456789abcdef", true)
            prefs.setWallpaperFavorite("art:aurora_night", true)
            prefs.setWallpaperFavorite("art:aurora_night", false)
            prefs.showSystemWallpaperOnHome()
            prefs.setWallpaperAccent(0xFFABCDEF.toInt())
            assertEquals(originalTheme.copy(backdrop = Backdrop.WALLPAPER, accentOverride = 0xFFABCDEF.toInt()), prefs.themeSettings.first())
            assertEquals("home-with-widget", prefs.homeLayoutRaw.first())
        } finally { firstJob.cancelAndJoin() }

        val secondJob = SupervisorJob()
        val secondScope = CoroutineScope(secondJob + Dispatchers.Default)
        try {
            val reopened = LumaPrefs(dataStore(secondScope, file))
            assertEquals(setOf("art:zen_morning", "photo:01234567-89ab-cdef-0123-456789abcdef"), reopened.wallpaperFavorites.first())
            assertEquals(originalTheme.copy(backdrop = Backdrop.WALLPAPER, accentOverride = 0xFFABCDEF.toInt()), reopened.themeSettings.first())
            assertEquals("home-with-widget", reopened.homeLayoutRaw.first())
        } finally { secondJob.cancelAndJoin() }
    }

    @Test
    fun `applied wallpaper selection dynamic toggle and cached seeds persist cleanly across restarts`() = runBlocking {
        val file = temporaryFolder.newFile("wallpaper-active.preferences_pb")
        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(firstJob + Dispatchers.Default)
        try {
            val prefs = LumaPrefs(dataStore(firstScope, file))
            prefs.recordAppliedWallpaper(
                wallpaperId = "art:05_emerald_northern_lights",
                systemWallpaperId = 77,
                backdrop = Backdrop.WALLPAPER,
                enableDynamicColors = true
            )
            prefs.setCachedWallpaperSeedsRaw("art:05_emerald_northern_lights|-16775417|-16636633||0|-14444971|0.0|0.11")
            assertEquals("art:05_emerald_northern_lights", prefs.activeWallpaperId.first())
            assertEquals(77, prefs.activeWallpaperSystemId.first())
            assertTrue(prefs.themeSettings.first().dynamicWallpaperColors)
        } finally { firstJob.cancelAndJoin() }

        val secondJob = SupervisorJob()
        val secondScope = CoroutineScope(secondJob + Dispatchers.Default)
        try {
            val reopened = LumaPrefs(dataStore(secondScope, file))
            assertEquals("art:05_emerald_northern_lights", reopened.activeWallpaperId.first())
            assertEquals(77, reopened.activeWallpaperSystemId.first())
            assertTrue(reopened.themeSettings.first().dynamicWallpaperColors)
            reopened.setDynamicWallpaperColors(false)
            assertFalse(reopened.themeSettings.first().dynamicWallpaperColors)
            reopened.clearActiveWallpaperIfMatches("art:wrong_id")
            assertEquals("art:05_emerald_northern_lights", reopened.activeWallpaperId.first())
            reopened.clearActiveWallpaperIfMatches("art:05_emerald_northern_lights")
            assertEquals(null, reopened.activeWallpaperId.first())
            assertEquals(null, reopened.activeWallpaperSystemId.first())
        } finally { secondJob.cancelAndJoin() }
    }

    @Test
    fun `concurrent favorite edits are atomic and legacy manual restore preserves them`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val prefs = LumaPrefs(dataStore(scope, temporaryFolder.newFile("wallpaper-concurrent.preferences_pb")))
            kotlinx.coroutines.coroutineScope {
                (1..20).map { index -> async { prefs.setWallpaperFavorite("art:item_$index", true) } }.forEach { it.await() }
            }
            assertEquals(20, prefs.wallpaperFavorites.first().size)
            val codec = com.lumaos.pro.launcher.backup.BackupCodec
            val decoded = codec.decode(codec.encode(prefs.snapshotForBackup(), "test", 1))
            // Manual backups deliberately do not promise to carry private photo files.
            assertFalse(decoded.values.containsKey("wallpaper_favorites"))
            prefs.restoreFromBackup(decoded.values) { codec.expectedType(it) != null }
            assertEquals(20, prefs.wallpaperFavorites.first().size)
        } finally { scope.cancel() }
    }

    @Test
    fun `applying an experience profile persists its appearance settings without touching user content`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val file = temporaryFolder.newFile("settings.preferences_pb")
                val prefs = LumaPrefs(dataStore(scope, file))

                prefs.setOnboardingDone(true)
                prefs.setHomeLayoutRaw("persisted-layout-v1")
                prefs.setWidgetsEnabled(false)

                ExperienceProfileController.apply(prefs, ExperienceProfile.MIDNIGHT)

                assertEquals(ExperienceProfile.MIDNIGHT, prefs.experienceProfile.first())
                val theme = prefs.themeSettings.first()
                assertEquals(ExperienceProfile.MIDNIGHT.themeId, theme.themeId)
                assertEquals(ThemeSwitching.FIXED, theme.switching)
                assertEquals(ExperienceProfile.MIDNIGHT.backdrop, theme.backdrop)
                assertEquals(ExperienceProfile.MIDNIGHT.preset, prefs.homePreset.first())
                assertEquals(ExperienceProfile.MIDNIGHT.dock, prefs.dockStyleOverride.first())
                assertEquals(ExperienceProfile.MIDNIGHT.effects, prefs.effectsPreference.first())
                assertEquals(ExperienceProfile.MIDNIGHT.motion, prefs.motionPreference.first())

                // The profile is appearance-only: onboarding, the home layout and
                // the widget switch are not reset or rearranged.
                assertTrue(prefs.onboardingDone.first())
                assertEquals("persisted-layout-v1", prefs.homeLayoutRaw.first())
                assertFalse(prefs.widgetsEnabled.first())
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `explicit theme default backdrop survives reopening while a missing value keeps the legacy default`() =
        runBlocking {
            val file = temporaryFolder.newFile("backdrop.preferences_pb")
            val firstJob = SupervisorJob()
            val firstScope = CoroutineScope(firstJob + Dispatchers.Default)
            try {
                val prefs = LumaPrefs(dataStore(firstScope, file))
                assertEquals(Backdrop.WALLPAPER, prefs.themeSettings.first().backdrop)
                prefs.applyThemeSettings(prefs.themeSettings.first().copy(backdrop = null))
                assertEquals(null, prefs.themeSettings.first().backdrop)
            } finally {
                firstJob.cancelAndJoin()
            }

            val secondJob = SupervisorJob()
            val secondScope = CoroutineScope(secondJob + Dispatchers.Default)
            try {
                val reopened = LumaPrefs(dataStore(secondScope, file))
                assertEquals(null, reopened.themeSettings.first().backdrop)
            } finally {
                secondJob.cancelAndJoin()
            }
        }

    @Test
    fun `studio colour edit preserves custom rows layout and unrelated preferences`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val prefs = LumaPrefs(dataStore(scope, temporaryFolder.newFile("studio.preferences_pb")))
            prefs.setGridRows(7)
            prefs.setGridColumns(6)
            prefs.setHomeLayoutRaw("saved-layout")
            prefs.setWidgetsEnabled(false)
            val draft = prefs.themeSettings.first().copy(
                themeId = ThemeId.ATMOSPHERE,
                switching = ThemeSwitching.FIXED,
                accentOverride = 0xFF55D9BC.toInt(),
                backdrop = null
            )
            prefs.applyStudioSettings(
                draft, null, prefs.homePreset.first(), DockStyle.FLOAT,
                EffectsPreference.BALANCED, MotionPreference.REDUCED
            )

            assertEquals(draft, prefs.themeSettings.first())
            assertEquals(7, prefs.gridRows.first())
            assertEquals(6, prefs.gridColumns.first())
            assertEquals("saved-layout", prefs.homeLayoutRaw.first())
            assertFalse(prefs.widgetsEnabled.first())
            assertEquals(DockStyle.FLOAT, prefs.dockStyleOverride.first())
            assertEquals(EffectsPreference.BALANCED, prefs.effectsPreference.first())
            assertEquals(MotionPreference.REDUCED, prefs.motionPreference.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `studio layout change adopts the selected preset rows`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val prefs = LumaPrefs(dataStore(scope, temporaryFolder.newFile("studio-layout.preferences_pb")))
            prefs.setGridRows(7)
            prefs.applyStudioSettings(
                prefs.themeSettings.first(), null, HomePreset.GRID, null,
                EffectsPreference.AUTO, MotionPreference.FULL
            )
            assertEquals(HomePreset.GRID, prefs.homePreset.first())
            assertEquals(HomePreset.GRID.defaultRows, prefs.gridRows.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `experience switching keeps an explicitly customised row count`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val prefs = LumaPrefs(dataStore(scope, temporaryFolder.newFile("experience-rows.preferences_pb")))
            prefs.setHomePreset(HomePreset.GRID)
            prefs.setGridRows(8)
            ExperienceProfileController.apply(prefs, ExperienceProfile.PURE)
            assertEquals(8, prefs.gridRows.first())
            assertEquals(ExperienceProfile.PURE.preset, prefs.homePreset.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `onboarding initialization leaves an existing appearance untouched`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val prefs = LumaPrefs(dataStore(scope, temporaryFolder.newFile("existing.preferences_pb")))
            prefs.applyThemeSettings(ThemeSettings(themeId = ThemeId.MONOLITH, switching = ThemeSwitching.FIXED))
            prefs.setGridRows(8)
            prefs.setHomeLayoutRaw("existing-layout")
            val before = prefs.snapshotForBackup()
            prefs.initializeDefaultExperienceIfUnconfigured()
            assertEquals(before, prefs.snapshotForBackup())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `onboarding initialization seeds liquid only on a fresh store`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val prefs = LumaPrefs(dataStore(scope, temporaryFolder.newFile("fresh.preferences_pb")))
            prefs.initializeDefaultExperienceIfUnconfigured()
            assertEquals(ExperienceProfile.LIQUID, prefs.experienceProfile.first())
            assertEquals(ExperienceProfile.LIQUID.themeId, prefs.themeSettings.first().themeId)
            assertEquals(ThemeSwitching.FIXED, prefs.themeSettings.first().switching)
            assertEquals(ExperienceProfile.LIQUID.preset.defaultRows, prefs.gridRows.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `widget release snapshot includes legacy and per-profile layouts`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = dataStore(scope, temporaryFolder.newFile("widget-snapshot.preferences_pb"))
            val prefs = LumaPrefs(store)
            prefs.setHomeLayoutRaw("legacy")
            prefs.setHomeLayoutRaw(ContextProfile.WORK, "work")
            store.edit {
                it[stringPreferencesKey("home_layout_future")] = "future"
                it[intPreferencesKey("home_layout_damaged")] = 42
            }
            val layouts = prefs.allHomeLayoutsRaw()
            assertEquals(3 + ContextProfile.entries.size, layouts.size)
            assertTrue("legacy" in layouts)
            assertTrue("work" in layouts)
            assertTrue("future" in layouts)
            assertTrue("" in layouts) // wrong type means uncertain, never empty proof
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `automatic layout writes compare profile and base state atomically`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val prefs = LumaPrefs(dataStore(scope, temporaryFolder.newFile("layout-cas.preferences_pb")))
            prefs.setHomeLayoutRaw("base")
            // An unset profile initially inherits the base layout.
            assertTrue(prefs.setHomeLayoutRawIfUnchanged(ContextProfile.WORK, "base", "work"))
            assertFalse(prefs.setHomeLayoutRawIfUnchanged(ContextProfile.WORK, "base", "stale"))
            assertEquals("work", prefs.homeLayoutRaw(ContextProfile.WORK).first())

            // A concurrent base edit invalidates a pending write to another
            // profile that was still inheriting that base.
            prefs.setHomeLayoutRaw("new-base")
            assertFalse(prefs.setHomeLayoutRawIfUnchanged(ContextProfile.PERSONAL, "base", "stale"))
            assertFalse(prefs.setHomeLayoutRawIfUnchanged(null, "base", "stale-base"))
            assertEquals("new-base", prefs.homeLayoutRaw.first())
            assertEquals("new-base", prefs.homeLayoutRaw(ContextProfile.PERSONAL).first())
            assertEquals("work", prefs.homeLayoutRaw(ContextProfile.WORK).first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `widget release snapshot propagates read errors`() = runBlocking {
        val failingStore = object : DataStore<Preferences> {
            override val data = flow<Preferences> { throw IOException("disk unavailable") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw IOException("disk unavailable")
        }
        try {
            LumaPrefs(failingStore).allHomeLayoutsRaw()
            org.junit.Assert.fail("Expected the read failure")
        } catch (expected: IOException) {
            assertEquals("disk unavailable", expected.message)
        }
    }

    @Test
    fun `a failed profile write is propagated instead of reporting success`() = runBlocking {
        val failingStore = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                throw IOException("disk unavailable")
            }
        }
        try {
            ExperienceProfileController.apply(LumaPrefs(failingStore), ExperienceProfile.MIDNIGHT)
            org.junit.Assert.fail("Expected the write failure")
        } catch (expected: IOException) {
            assertEquals("disk unavailable", expected.message)
        }
    }

}
