package com.lumaos.pro.launcher.robolectric

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.core.apps.IconCache
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.launcher.model.HomeLayout
import com.lumaos.pro.launcher.launcher.model.HomeLayoutCodec
import com.lumaos.pro.launcher.launcher.model.HomeLayoutOps
import com.lumaos.pro.launcher.core.prefs.LumaPrefs
import com.lumaos.pro.launcher.diagnostics.DiagnosticsRepository
import com.lumaos.pro.launcher.diagnostics.DiagnosticsStore
import com.lumaos.pro.launcher.intelligence.ProfileId
import com.lumaos.pro.launcher.intelligence.Profiles
import com.lumaos.pro.launcher.optimizer.Optimizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.flow.first
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
 * Runs the optimizer's actions against a real filesystem and a real database.
 *
 * These actions delete files and mutate caches, so they are the part of LumaOS Pro
 * most likely to cause irreversible damage if wrong. Verifying them here means
 * verifying that they do what they claim (delete LumaOS Pro's own cache, report the
 * bytes freed) rather than merely compiling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = LumaApp::class)
class OptimizerRuntimeTest {

    private lateinit var context: Application
    private lateinit var iconCache: IconCache
    private lateinit var optimizer: Optimizer
    private lateinit var diagnostics: DiagnosticsRepository
    private lateinit var prefs: LumaPrefs
    private val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
    private lateinit var prefsFile: File
    private lateinit var prefsDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        iconCache = IconCache(64)

        val store = DiagnosticsStore(context)
        diagnostics = DiagnosticsRepository(store)

        // A unique file per test, and crucially NOT inside cacheDir.
        // emergencyReset() deletes the contents of cacheDir, so a preferences
        // file parked there would be deleted mid-test. The real app is not
        // exposed to this: LumaPrefs goes through the preferencesDataStore
        // delegate (LumaPrefs.kt:24), which stores under filesDir/datastore,
        // a location emergencyReset never touches. The test has to mirror that
        // or it exercises a situation the product cannot be in.
        prefsDir = File(context.filesDir, "optimizer_test_datastore")
        prefsDir.mkdirs()
        prefsFile = File(prefsDir, "optimizer_test_${System.nanoTime()}.preferences_pb")
        prefsFile.delete()
        val dataStore: androidx.datastore.core.DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope) { prefsFile }
        prefs = LumaPrefs(dataStore)

        optimizer = Optimizer(context, prefs, iconCache, diagnostics)
    }

    @After
    fun tearDown() {
        // Only remove what this test created. Wiping the whole cacheDir would
        // delete files belonging to other test classes sharing this JVM.
        prefsFile.delete()
        prefsDir.deleteRecursively()
        File(context.cacheDir, "junk.bin").delete()
        File(context.cacheDir, "nested").deleteRecursively()
    }

    @Test
    fun `clearing the internal cache deletes real files and reports the bytes`() = runTest {
        val cache = context.cacheDir!!
        cache.mkdirs()
        File(cache, "junk.bin").apply { writeBytes(ByteArray(4_096)) }
        File(cache, "nested").apply {
            mkdirs()
            File(this, "more.bin").writeBytes(ByteArray(2_048))
        }
        val before = File(cache, "junk.bin").length() + File(cache, "nested/more.bin").length()

        val result = optimizer.run(
            Optimizer.ACTION_CLEAR_INTERNAL_CACHE,
            trigger = "manual",
            profile = Profiles.BALANCED
        )

        assertTrue("action should complete", result.completed)
        assertEquals(before, result.bytesFreed)
        assertTrue("cache directory should be emptied", !File(cache, "junk.bin").exists())
        assertTrue("nested files should be removed too", !File(cache, "nested").exists())
        // The directory itself must survive: Android expects cacheDir to exist.
        assertTrue("cacheDir itself must be kept", cache.exists())
    }

    @Test
    fun `clearing an already empty cache reports zero and still completes`() = runTest {
        context.cacheDir?.deleteRecursively()
        context.cacheDir?.mkdirs()

        val result = optimizer.run(
            Optimizer.ACTION_CLEAR_INTERNAL_CACHE, "manual", Profiles.BALANCED
        )
        assertTrue(result.completed)
        assertEquals(0L, result.bytesFreed)
        assertTrue(result.detail.contains("already empty"))
    }

    @Test
    fun `trimming the icon cache releases entries but keeps the visible ones`() = runTest {
        val drawable = android.graphics.drawable.ColorDrawable(0xFF000000.toInt())
        repeat(40) { iconCache[ComponentKey("com.app$it", "com.app$it.Main")] = drawable }
        val before = iconCache.size
        assertTrue("cache should be populated", before >= 40)

        val result = optimizer.run(
            Optimizer.ACTION_TRIM_ICON_CACHE, "manual", Profiles.BALANCED
        )

        assertTrue(result.completed)
        assertTrue("entries should have been released", iconCache.size < before)
        assertTrue(
            "the on-screen floor must be retained",
            iconCache.size >= Optimizer.ICON_CACHE_KEEP.coerceAtMost(before)
        )
    }

    @Test
    fun `applying a profile resizes the icon cache to the profile ceiling`() = runTest {
        val result = optimizer.run(
            Optimizer.ACTION_APPLY_PROFILE, "manual", Profiles.LIGHTWEIGHT
        )
        assertTrue(result.completed)
        assertEquals(Profiles.LIGHTWEIGHT.maxCachedIcons, iconCache.maxEntries)
        assertTrue(result.detail.contains("lightweight", ignoreCase = true))
    }

    @Test
    fun `compact diagnostics completes even on a brand new database`() = runTest {
        val result = optimizer.run(
            Optimizer.ACTION_COMPACT_DIAGNOSTICS, "manual", Profiles.BALANCED
        )
        assertTrue(result.completed)
    }

    @Test
    fun `every action records its outcome in the diagnostics history`() = runTest {
        optimizer.run(Optimizer.ACTION_APPLY_PROFILE, "manual", Profiles.BALANCED)
        optimizer.run(Optimizer.ACTION_TRIM_ICON_CACHE, "rule_icon_cache", Profiles.BALANCED)

        val events = diagnostics.recentEvents(10)
        assertEquals(2, events.size)
        // Newest first: the trim was recorded last.
        assertEquals(Optimizer.ACTION_TRIM_ICON_CACHE, events.first().actionId)
        assertEquals("rule_icon_cache", events.first().trigger)
        assertTrue(events.first().detail.isNotBlank())
    }

    @Test
    fun `running an unknown action reports failure instead of throwing`() = runTest {
        val result = optimizer.run("does_not_exist", "manual", Profiles.BALANCED)
        assertEquals(false, result.completed)
        assertTrue(result.detail.contains("Unknown action"))
    }

    @Test
    fun `emergency reset keeps the home layout it promises to keep`() = runTest {
        // emergencyReset() wipes context.cacheDir, so its own text promises
        // "your home layout, dock and settings were kept". Two things guard
        // that promise: the preferences file must live outside cacheDir (it
        // does, via the preferencesDataStore delegate at LumaPrefs.kt:24),
        // and reset must not clear keys it did not mean to clear.
        //
        // The sentinel must NOT be the profile override: resetting that to
        // automatic is the intended behaviour of an emergency reset, and the
        // test below asserts it. Home layout is the right sentinel.
        context.cacheDir?.mkdirs()
        File(context.cacheDir, "junk.bin").writeBytes(ByteArray(1_024))

        val key = ComponentKey("com.camera", "com.camera.Main")
        val layout = HomeLayoutOps.addToDock(HomeLayout.EMPTY, key)
        prefs.setHomeLayoutRaw(HomeLayoutCodec.encode(layout))
        prefs.setProfileOverride(ProfileId.VISUAL_QUALITY)
        assertTrue("precondition: layout stored", !prefs.homeLayoutRaw.first().isNullOrBlank())

        optimizer.emergencyReset()

        // The cache really is wiped.
        assertTrue("cache file should be gone", !File(context.cacheDir, "junk.bin").exists())
        // ...but the preferences file, and the layout inside it, survive.
        assertTrue("prefs file must survive outside cacheDir", prefsFile.exists())
        assertEquals(
            "home layout must survive an emergency reset",
            layout, HomeLayoutCodec.decode(prefs.homeLayoutRaw.first())
        )
        // And the profile override really is reset, as designed.
        assertEquals(null, prefs.profileOverride.first())
    }

    @Test
    fun `emergency reset clears caches and history but reports its scope honestly`() = runTest {
        context.cacheDir?.mkdirs()
        File(context.cacheDir, "junk.bin").writeBytes(ByteArray(1_024))
        iconCache[ComponentKey("com.app", "com.app.Main")] =
            android.graphics.drawable.ColorDrawable(0)
        prefs.setProfileOverride(com.lumaos.pro.launcher.intelligence.ProfileId.VISUAL_QUALITY)
        optimizer.run(Optimizer.ACTION_APPLY_PROFILE, "manual", Profiles.BALANCED)

        val result = optimizer.emergencyReset()

        assertTrue(result.completed)
        assertTrue("cache should be emptied", !File(context.cacheDir, "junk.bin").exists())
        assertEquals("icon cache should be cleared", 0, iconCache.size)
        assertEquals(
            "profile override should be reset to automatic",
            null,
            prefs.profileOverride.first()
        )
        assertTrue(
            "the reset message must say what was kept",
            result.detail.contains("kept", ignoreCase = true)
        )
        // The reset itself is recorded, so history shows it happened.
        assertTrue(diagnostics.recentEvents(10).any { it.actionId == "emergency_reset" })
    }
}
