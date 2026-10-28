package com.lumaos.pro.launcher.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.lumaos.pro.launcher.core.prefs.LumaPrefs
import com.lumaos.pro.launcher.ui.theme.ExperienceProfile
import com.lumaos.pro.launcher.ui.theme.ThemeSwitching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
}
