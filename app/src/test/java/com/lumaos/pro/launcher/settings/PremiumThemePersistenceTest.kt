package com.lumaos.pro.launcher.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lumaos.pro.launcher.core.prefs.LumaPrefs
import com.lumaos.pro.launcher.ui.theme.DarkWindow
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemeLauncherPackage
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import com.lumaos.pro.launcher.ui.theme.ThemeSwitching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Theme choices made in Studio must survive a launcher restart: the premium
 * theme, its accent, and the light/dark appearance pairing are all persisted
 * through the one DataStore transaction and read back identically.
 */
class PremiumThemePersistenceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun dataStore(scope: CoroutineScope, file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun <T> withPrefs(file: File, block: suspend (LumaPrefs) -> T): T = runBlocking {
        val job = SupervisorJob()
        try {
            block(LumaPrefs(dataStore(CoroutineScope(job + Dispatchers.Default), file)))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `a premium theme with a custom accent survives a restart`() {
        val file = temporaryFolder.newFile("premium.preferences_pb")
        val chosen = ThemeLauncherPackage.forTheme(ThemeId.AURORA).toThemeSettings()
            .copy(accentOverride = 0xFF4FE3C1.toInt())
        withPrefs(file) { it.applyThemeSettings(chosen) }
        assertEquals(chosen, withPrefs(file) { it.themeSettings.first() })
    }

    @Test
    fun `the light and dark pairing survives a restart`() {
        val file = temporaryFolder.newFile("pairing.preferences_pb")
        val paired = ThemeSettings(
            themeId = ThemeId.OBSIDIAN,
            switching = ThemeSwitching.SCHEDULE,
            lightThemeId = ThemeId.IVORY,
            darkThemeId = ThemeId.OBSIDIAN,
            darkWindow = DarkWindow(20 * 60, 6 * 60 + 30)
        )
        withPrefs(file) { it.applyThemeSettings(paired) }
        assertEquals(paired, withPrefs(file) { it.themeSettings.first() })
    }

    @Test
    fun `ivory is written as the legacy light mode`() {
        val file = temporaryFolder.newFile("legacy.preferences_pb")
        withPrefs(file) { it.applyThemeSettings(ThemeSettings().withPreset(ThemeId.IVORY)) }
        val raw = withPrefs(file) { prefs ->
            prefs.themeMode.first()
        }
        assertEquals(com.lumaos.pro.launcher.ui.ThemeMode.LIGHT, raw)
    }

    @Test
    fun `an explicit stored Zen light choice is never rewritten to Ivory`() {
        val file = temporaryFolder.newFile("zen.preferences_pb")
        runBlocking {
            val job = SupervisorJob()
            try {
                dataStore(CoroutineScope(job + Dispatchers.Default), file).edit {
                    it[stringPreferencesKey("theme_light_id")] = "porcelain"
                }
            } finally { job.cancelAndJoin() }
        }
        assertEquals(ThemeId.PORCELAIN, withPrefs(file) { it.themeSettings.first() }.lightThemeId)
    }

    @Test
    fun `a fresh install follows the system with the premium light theme`() {
        val file = temporaryFolder.newFile("fresh.preferences_pb")
        val fresh = withPrefs(file) { it.themeSettings.first() }
        assertEquals(ThemeSwitching.SYSTEM, fresh.switching)
        assertEquals(ThemeId.IVORY, fresh.lightThemeId)
    }
}
