package com.lumaos.pro.launcher.core.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lumaos.pro.launcher.backup.BackupCodec
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.effects.EffectsPreference
import com.lumaos.pro.launcher.icons.LivingIconId
import com.lumaos.pro.launcher.icons.LivingIconOverride
import com.lumaos.pro.launcher.icons.LivingIconOverridesCodec
import com.lumaos.pro.launcher.launcher.model.DockStyle
import com.lumaos.pro.launcher.launcher.model.HomeItem
import com.lumaos.pro.launcher.launcher.model.HomeLayout
import com.lumaos.pro.launcher.launcher.model.HomeLayoutCodec
import com.lumaos.pro.launcher.launcher.model.HomePreset
import com.lumaos.pro.launcher.motion.MotionPreference
import com.lumaos.pro.launcher.ui.theme.ExperienceProfile
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies durable DataStore persistence, upgrade safety over existing v1.0
 * user configurations, Studio atomic transactions, BackupCodec round-trips, and
 * AndroidManifest HOME launcher role declarations.
 */
class LivingIconsPersistenceAndUpgradeTest {

    @Test
    fun `fresh install enables Living Icons by default and persists overrides across reloads`() = runBlocking {
        val dir = Files.createTempDirectory("luma-prefs-fresh").toFile()
        val storeFile = File(dir, "test.preferences_pb")
        try {
            val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val prefs1 = LumaPrefs(PreferenceDataStoreFactory.create(scope = scope1) { storeFile })

            assertTrue(prefs1.livingIconsEnabled.first())
            assertTrue(prefs1.livingIconOverrides.first().isEmpty())

            prefs1.initializeDefaultExperienceIfUnconfigured()
            assertEquals(ExperienceProfile.DEFAULT, prefs1.experienceProfile.first())

            val obsidian = ComponentKey("md.obsidian", "md.obsidian.MainActivity")
            val spotify = ComponentKey("com.spotify.music", "com.spotify.music.MainActivity")
            prefs1.setLivingIconOverride(obsidian, LivingIconOverride.UseLiving(LivingIconId.NOTES))
            prefs1.setLivingIconOverride(spotify, LivingIconOverride.UseSystem)
            prefs1.setLivingIconsEnabled(false)
            scope1.cancel()

            // Simulate process death & cold restart reading from the same persisted DataStore file
            val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val prefs2 = LumaPrefs(PreferenceDataStoreFactory.create(scope = scope2) { storeFile })
            assertFalse(prefs2.livingIconsEnabled.first())
            assertEquals(
                mapOf(
                    obsidian to LivingIconOverride.UseLiving(LivingIconId.NOTES),
                    spotify to LivingIconOverride.UseSystem
                ),
                prefs2.livingIconOverrides.first()
            )

            // Removing a single override leaves others intact; clearing removes key cleanly
            prefs2.setLivingIconOverride(spotify, null)
            assertEquals(
                mapOf(obsidian to LivingIconOverride.UseLiving(LivingIconId.NOTES)),
                prefs2.livingIconOverrides.first()
            )
            prefs2.clearLivingIconOverrides()
            assertTrue(prefs2.livingIconOverrides.first().isEmpty())
            scope2.cancel()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `upgrade over pre-existing v1 layout and wallpaper preserves all user data without resetting`() = runBlocking {
        val dir = Files.createTempDirectory("luma-prefs-upgrade").toFile()
        val storeFile = File(dir, "upgrade.preferences_pb")
        try {
            val existingLayout = HomeLayout(
                dock = listOf(
                    HomeItem.App(ComponentKey("com.google.android.dialer", "com.google.android.dialer.DialtactsActivity")),
                    HomeItem.App(ComponentKey("com.whatsapp", "com.whatsapp.Main"))
                ),
                pages = listOf(
                    listOf(
                        HomeItem.Widget(appWidgetId = 77, provider = "com.lumaos.pro.launcher/.BatteryWidget", rows = 2),
                        HomeItem.Folder(
                            id = "f-social",
                            name = "Social",
                            children = listOf(
                                ComponentKey("com.instagram.android", "com.instagram.android.Main"),
                                ComponentKey("com.discord", "com.discord.MainActivity")
                            )
                        )
                    ),
                    listOf(
                        HomeItem.App(ComponentKey("com.spotify.music", "com.spotify.music.MainActivity"))
                    )
                )
            )
            val encodedLayout = HomeLayoutCodec.encode(existingLayout)

            // Seed a legacy pre-Living-Icons DataStore containing only layout & layout_seeded
            val legacyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val legacyDs = PreferenceDataStoreFactory.create(scope = legacyScope) { storeFile }
            legacyDs.edit { p ->
                p[stringPreferencesKey("home_layout_personal")] = encodedLayout
                p[stringPreferencesKey("home_layout")] = encodedLayout
                p[booleanPreferencesKey("layout_seeded")] = true
                p[intPreferencesKey("grid_columns")] = 5
            }
            legacyScope.cancel()

            // Open upgraded LumaPrefs and run onboarding/startup idempotent guard
            val upgradedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val upgradedPrefs = LumaPrefs(PreferenceDataStoreFactory.create(scope = upgradedScope) { storeFile })
            upgradedPrefs.initializeDefaultExperienceIfUnconfigured()

            // Existing layout (pages, folders, widgets, dock) and grid columns remain 100% intact
            assertEquals(5, upgradedPrefs.gridColumns.first())
            val decodedAfterUpgrade = HomeLayoutCodec.decode(
                upgradedPrefs.homeLayoutRaw(ContextProfile.PERSONAL).first()
            )
            assertEquals(existingLayout, decodedAfterUpgrade)
            // Living Icons smoothly enable by default on upgrade without requiring reset
            assertTrue(upgradedPrefs.livingIconsEnabled.first())
            upgradedScope.cancel()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `Studio apply commits Living Icons preference atomically without altering Home layout`() = runBlocking {
        val dir = Files.createTempDirectory("luma-prefs-studio").toFile()
        val storeFile = File(dir, "studio.preferences_pb")
        try {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val prefs = LumaPrefs(PreferenceDataStoreFactory.create(scope = scope) { storeFile })

            val savedLayout = HomeLayout(
                dock = listOf(HomeItem.App(ComponentKey("com.android.chrome", "com.google.android.apps.chrome.Main"))),
                pages = listOf(listOf(HomeItem.App(ComponentKey("com.android.settings", "com.android.settings.Settings"))))
            )
            prefs.setHomeLayoutRaw(ContextProfile.PERSONAL, HomeLayoutCodec.encode(savedLayout))

            prefs.applyStudioSettings(
                theme = ThemeSettings(themeId = ThemeId.OBSIDIAN),
                iconPack = null,
                homePreset = HomePreset.INSTRUMENT,
                dockStyle = DockStyle.FLOAT,
                effects = EffectsPreference.FULL,
                motion = MotionPreference.FULL,
                livingIconsEnabled = false
            )

            assertFalse(prefs.livingIconsEnabled.first())
            assertEquals(ThemeId.OBSIDIAN, prefs.themeSettings.first().themeId)
            assertEquals(
                savedLayout,
                HomeLayoutCodec.decode(prefs.homeLayoutRaw(ContextProfile.PERSONAL).first())
            )
            scope.cancel()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `BackupCodec exports and restores Living Icons settings and sanitizes malformed overrides`() {
        val k = ComponentKey("com.spotify.music", "com.spotify.music.MainActivity")
        val rawValues = mapOf(
            "living_icons_enabled" to false,
            "living_icon_overrides" to "${k.serialize()}=music|bad_token|0:x/y=nonexistent_icon",
            "experience_profile" to "liquid"
        )
        val json = BackupCodec.encode(rawValues, "1.0.0", 1700000000000L)
        val decoded = BackupCodec.decode(json)

        assertEquals(false, decoded.values["living_icons_enabled"])
        assertEquals("liquid", decoded.values["experience_profile"])
        assertEquals(
            "${k.serialize()}=music",
            decoded.values["living_icon_overrides"]
        )
        assertEquals(
            mapOf(k to LivingIconOverride.UseLiving(LivingIconId.MUSIC)),
            LivingIconOverridesCodec.decode(decoded.values["living_icon_overrides"] as String)
        )
    }

    @Test
    fun `AndroidManifest declares persistent singleTask HOME activity without deceptive receivers or services`() {
        val manifestFile = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).first { it.isFile }
        val xml = manifestFile.readText(Charsets.UTF_8)

        assertTrue(xml.contains("android:name=\".launcher.HomeActivity\""))
        assertTrue(xml.contains("android:launchMode=\"singleTask\""))
        assertTrue(xml.contains("android:clearTaskOnLaunch=\"true\""))
        assertTrue(xml.contains("android:excludeFromRecents=\"true\""))
        assertTrue(xml.contains("android:resumeWhilePausing=\"true\""))
        // Saved instance state must NOT be disabled by stateNotNeeded so pending widget binds and active page survive recreation
        assertFalse(xml.contains("android:stateNotNeeded=\"true\""))
        assertTrue(xml.contains("android.intent.category.HOME"))
        assertTrue(xml.contains("android.intent.category.DEFAULT"))
        // Onboarding must never intercept HOME intents
        val onboardingSection = xml.substringAfter("OnboardingActivity").substringBefore("</activity>", "")
        assertFalse(onboardingSection.contains("android.intent.category.HOME"))
        // No deceptive DeviceAdminReceiver or AccessibilityService declarations
        assertFalse(xml.contains("android.app.action.DEVICE_ADMIN_ENABLED"))
        assertFalse(xml.contains("android.accessibilityservice.AccessibilityService"))
    }
}
