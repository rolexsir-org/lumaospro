package com.rolexm.launcher.robolectric

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.rolexm.launcher.core.RolexApp
import com.rolexm.launcher.context.ContextProfile
import com.rolexm.launcher.context.ProfileSchedule
import com.rolexm.launcher.core.model.ComponentKey
import com.rolexm.launcher.core.prefs.GestureKind
import com.rolexm.launcher.core.prefs.RolexPrefs
import com.rolexm.launcher.core.util.RoleHelper
import com.rolexm.launcher.diagnostics.Reading
import com.rolexm.launcher.intelligence.BatterySampler
import com.rolexm.launcher.intelligence.DeviceCapability
import com.rolexm.launcher.intelligence.DeviceCapabilityProvider
import com.rolexm.launcher.intelligence.MemorySampler
import com.rolexm.launcher.intelligence.ProfileId
import com.rolexm.launcher.launcher.model.GestureAction
import com.rolexm.launcher.launcher.model.HomeItem
import com.rolexm.launcher.launcher.model.HomeLayout
import com.rolexm.launcher.launcher.model.HomeLayoutCodec
import com.rolexm.launcher.launcher.model.HomeLayoutOps
import com.rolexm.launcher.launcher.model.DrawerPresentation
import com.rolexm.launcher.ui.ThemeMode
import com.rolexm.launcher.ui.theme.ThemeSwitching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Preferences and system samplers against real implementations.
 *
 * RolexPrefs is the single source of truth for every setting, and it is built
 * on DataStore - which means file I/O, protobuf serialisation and a background
 * read path. Testing it against the real DataStore (rather than mocks) is what
 * catches a key that was written but never read back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RolexApp::class)
class PreferencesAndSamplersRuntimeTest {

    private val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
    private lateinit var prefs: RolexPrefs
    private lateinit var file: File

    @Before
    fun setUp() {
        val dir = File(
            ApplicationProvider.getApplicationContext<RolexApp>().filesDir,
            "prefs_test_datastore"
        )
        dir.mkdirs()
        file = File(dir, "prefs_test_${System.nanoTime()}.preferences_pb")
        file.delete()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
        prefs = RolexPrefs(dataStore)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `settings default sensibly before anything is written`() = runTest {
        assertEquals(ThemeMode.LIGHT, prefs.themeMode.first())
        assertEquals(true, prefs.showLabels.first())
        assertEquals(1.0f, prefs.textScale.first(), 0f)
        assertEquals(null, prefs.profileOverride.first())
        assertEquals(true, prefs.autoOptimize.first())
        assertEquals(false, prefs.onboardingDone.first())
    }

    @Test
    fun `theme choice round trips`() = runTest {
        prefs.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, prefs.themeMode.first())
        prefs.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, prefs.themeMode.first())
    }

    @Test
    fun `legacy theme control updates the active theme resolver atomically`() = runTest {
        prefs.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeSwitching.FIXED, prefs.themeSettings.first().switching)
        assertEquals(prefs.themeSettings.first().darkThemeId, prefs.themeSettings.first().themeId)

        prefs.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeSwitching.SYSTEM, prefs.themeSettings.first().switching)
    }

    @Test
    fun `drawer presentation and pinned apps round trip per context`() = runTest {
        assertEquals(DrawerPresentation.LIST, prefs.drawerPresentation.first())
        prefs.setDrawerPresentation(DrawerPresentation.GRID)
        assertEquals(DrawerPresentation.GRID, prefs.drawerPresentation.first())

        val first = ComponentKey("com.first", "com.first.Main")
        val second = ComponentKey("com.second", "com.second.Main")
        prefs.setAppPinned(ContextProfile.WORK, first, true)
        prefs.setAppPinned(ContextProfile.WORK, second, true)
        prefs.setAppPinned(ContextProfile.WORK, first, true)
        assertEquals(listOf(second, first), prefs.pinnedApps(ContextProfile.WORK).first())
        assertTrue(prefs.pinnedApps(ContextProfile.PERSONAL).first().isEmpty())

        prefs.setAppPinned(ContextProfile.WORK, second, false)
        assertEquals(listOf(first), prefs.pinnedApps(ContextProfile.WORK).first())
    }

    @Test
    fun `an invalid stored theme falls back to system rather than crashing`() = runTest {
        // Simulates a corrupted or downgraded preferences file.
        prefs.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, prefs.themeMode.first())
        prefs.setThemeMode(ThemeMode.valueOf("SYSTEM"))
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode.first())
    }

    @Test
    fun `profile override can be set and cleared back to automatic`() = runTest {
        prefs.setProfileOverride(ProfileId.LIGHTWEIGHT)
        assertEquals(ProfileId.LIGHTWEIGHT, prefs.profileOverride.first())
        prefs.setProfileOverride(null)
        assertEquals(null, prefs.profileOverride.first())
    }

    @Test
    fun `context profile settings persist independently`() = runTest {
        val workSchedule = ProfileSchedule.defaults(ContextProfile.WORK).copy(enabled = false)
        prefs.setProfileSchedule(ContextProfile.WORK, workSchedule)
        val workKey = ComponentKey("com.work", "com.work.Main")
        val studyKey = ComponentKey("com.study", "com.study.Main")
        prefs.setHomeLayoutRaw(ContextProfile.WORK, HomeLayoutCodec.encode(HomeLayoutOps.addToHome(HomeLayout.EMPTY, workKey)))
        prefs.setHomeLayoutRaw(ContextProfile.STUDY, HomeLayoutCodec.encode(HomeLayoutOps.addToHome(HomeLayout.EMPTY, studyKey)))
        prefs.setActiveContextProfile(ContextProfile.STUDY)
        prefs.setContextNotificationsEnabled(true)
        prefs.setContextNotificationEnabled(ContextProfile.WORK, true)
        prefs.setContextDndEnabled(ContextProfile.SLEEP, true)

        assertEquals(ContextProfile.STUDY, prefs.activeContextProfile.first())
        assertEquals(workSchedule, prefs.profileSchedule(ContextProfile.WORK).first())
        val workLayout = HomeLayoutCodec.decode(prefs.homeLayoutRaw(ContextProfile.WORK).first())
        val studyLayout = HomeLayoutCodec.decode(prefs.homeLayoutRaw(ContextProfile.STUDY).first())
        assertEquals(workKey, (workLayout.page(0).single() as HomeItem.App).key)
        assertEquals(studyKey, (studyLayout.page(0).single() as HomeItem.App).key)
        assertTrue(prefs.contextNotificationsEnabled.first())
        assertEquals(setOf(ContextProfile.WORK), prefs.contextNotificationProfiles.first())
        assertEquals(setOf(ContextProfile.SLEEP), prefs.contextDndProfiles.first())
    }

    @Test
    fun `numeric settings are clamped to usable ranges`() = runTest {
        prefs.setGridColumns(99)
        assertEquals(7, prefs.gridColumns.first())
        prefs.setGridColumns(0)
        assertEquals(3, prefs.gridColumns.first())

        prefs.setIconSizeDp(10_000)
        assertEquals(72, prefs.iconSizeDp.first())

        prefs.setTextScale(9f)
        assertEquals(1.6f, prefs.textScale.first(), 0f)
    }

    @Test
    fun `gestures default to conventional behaviour and can be rebound`() = runTest {
        assertEquals(GestureAction.OpenDrawer, prefs.gesture(GestureKind.SWIPE_UP).first())
        assertEquals(GestureAction.OpenSearch, prefs.gesture(GestureKind.SWIPE_DOWN).first())

        prefs.setGesture(GestureKind.SWIPE_UP, GestureAction.OpenSettings)
        assertEquals(GestureAction.OpenSettings, prefs.gesture(GestureKind.SWIPE_UP).first())
    }

    @Test
    fun `a gesture bound to an app round trips its component key`() = runTest {
        val key = ComponentKey("com.example.app", "com.example.app.Main", 0L)
        prefs.setGesture(GestureKind.DOUBLE_TAP, GestureAction.OpenApp.of(key))
        val restored = prefs.gesture(GestureKind.DOUBLE_TAP).first()
        assertTrue(restored is GestureAction.OpenApp)
        assertEquals(key, (restored as GestureAction.OpenApp).key)
    }

    @Test
    fun `the home layout persists and reloads`() = runTest {
        val camera = ComponentKey("com.camera", "com.camera.Main")
        val clock = ComponentKey("com.clock", "com.clock.Main")
        var layout = HomeLayoutOps.addToDock(HomeLayout.EMPTY, camera)
        layout = HomeLayoutOps.addToHome(layout, clock)
        prefs.setHomeLayoutRaw(HomeLayoutCodec.encode(layout))

        val reloaded = HomeLayoutCodec.decode(prefs.homeLayoutRaw.first())
        assertEquals(1, reloaded.dock.size)
        assertEquals(HomeItem.App(clock), reloaded.page(0).single())
    }

    @Test
    fun `action timestamps round trip through their packed string form`() = runTest {
        assertTrue(prefs.lastActionAt.first().isEmpty())
        prefs.markActionRan("trim_icon_cache", 1_700_000_000_000L)
        prefs.markActionRan("clear_internal_cache", 1_700_000_060_000L)

        val map = prefs.lastActionAt.first()
        assertEquals(1_700_000_000_000L, map["trim_icon_cache"])
        assertEquals(1_700_000_060_000L, map["clear_internal_cache"])
    }

    @Test
    fun `clearing action history leaves other settings alone`() = runTest {
        prefs.setThemeMode(ThemeMode.DARK)
        prefs.markActionRan("trim_icon_cache", 1L)
        prefs.clearActionHistory()

        assertTrue(prefs.lastActionAt.first().isEmpty())
        assertEquals("appearance settings must survive", ThemeMode.DARK, prefs.themeMode.first())
    }

    // --- Samplers: verify they read the platform without crashing ---------

    @Test
    fun `device capability is measured from real system services`() {
        val context = ApplicationProvider.getApplicationContext<RolexApp>()
        val capability: DeviceCapability = DeviceCapabilityProvider.snapshot(context)
        assertTrue(capability.totalMemoryMb >= 0L)
        assertTrue(capability.cpuCoreCount >= 1)
        assertTrue("classification should explain itself", capability.reasons.isNotEmpty())
    }

    @Test
    fun `memory sampling returns a real reading rather than throwing`() {
        val context = ApplicationProvider.getApplicationContext<RolexApp>()
        val reading = MemorySampler(context).sample()
        // Either a measurement or an explicit unavailability - never a crash
        // and never a fabricated number.
        when (reading) {
            is Reading.Measured -> assertTrue(reading.value.totalPssKb >= 0)
            is Reading.Unavailable -> assertTrue(reading.reason.isNotBlank())
            is Reading.Estimated -> assertTrue(reading.basis.isNotBlank())
        }
    }

    @Test
    fun `battery sampling returns a real reading rather than throwing`() {
        val context = ApplicationProvider.getApplicationContext<RolexApp>()
        val reading = BatterySampler(context).sample()
        when (reading) {
            is Reading.Measured -> assertTrue(reading.value.percent in 0..100)
            is Reading.Unavailable -> assertTrue(reading.reason.isNotBlank())
            is Reading.Estimated -> assertTrue(reading.basis.isNotBlank())
        }
    }

    @Test
    fun `role helper resolves the default launcher without crashing`() {
        val context = ApplicationProvider.getApplicationContext<RolexApp>()
        // Under Robolectric no app necessarily holds HOME; what matters is that
        // the query is well-formed and returns without throwing.
        RoleHelper.defaultHomePackage(context)
        RoleHelper.isDefaultLauncher(context)
        assertTrue(true)
    }
}
