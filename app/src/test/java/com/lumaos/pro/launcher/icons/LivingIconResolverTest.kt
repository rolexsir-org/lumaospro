package com.lumaos.pro.launcher.icons

import com.lumaos.pro.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests verified package-to-asset resolution, component disambiguation,
 * non-fabrication fallback guarantees, user overrides, work-profile inheritance,
 * and codec resilience.
 */
class LivingIconResolverTest {

    @Test
    fun `resolves all 10 verified popular apps accurately`() {
        val cases = mapOf(
            "com.whatsapp" to LivingIconId.WHATSAPP,
            "com.instagram.android" to LivingIconId.INSTAGRAM,
            "com.google.android.youtube" to LivingIconId.YOUTUBE,
            "com.spotify.music" to LivingIconId.SPOTIFY,
            "org.telegram.messenger" to LivingIconId.TELEGRAM,
            "com.discord" to LivingIconId.DISCORD,
            "com.google.android.gm" to LivingIconId.GMAIL,
            "com.google.android.apps.docs" to LivingIconId.GOOGLE_DRIVE,
            "com.android.chrome" to LivingIconId.GOOGLE_CHROME,
            "com.google.android.apps.photos" to LivingIconId.GOOGLE_PHOTOS
        )
        for ((pkg, expected) in cases) {
            val key = ComponentKey(pkg, "$pkg.MainActivity")
            assertEquals("Mismatch for $pkg", expected, LivingIconResolver.resolve(key, livingIconsEnabled = true))
        }
    }

    @Test
    fun `resolves all 20 core system categories across AOSP Pixel Samsung Xiaomi OnePlus`() {
        val cases = listOf(
            ComponentKey("com.google.android.dialer", "com.google.android.dialer.extensions.GoogleDialtactsActivity") to LivingIconId.PHONE,
            ComponentKey("com.samsung.android.dialer", "com.samsung.android.dialer.DialtactsActivity") to LivingIconId.PHONE,
            ComponentKey("com.google.android.apps.messaging", "com.google.android.apps.messaging.ui.ConversationListActivity") to LivingIconId.MESSAGES,
            ComponentKey("com.google.android.GoogleCamera", "com.android.camera.CameraLauncher") to LivingIconId.CAMERA,
            ComponentKey("com.sec.android.app.camera", "com.sec.android.app.camera.Camera") to LivingIconId.CAMERA,
            ComponentKey("com.sec.android.gallery3d", "com.samsung.android.gallery.app.activity.GalleryActivity") to LivingIconId.PHOTOS,
            ComponentKey("com.miui.gallery", "com.miui.gallery.activity.HomePageActivity") to LivingIconId.PHOTOS,
            ComponentKey("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.SBrowserMainActivity") to LivingIconId.BROWSER,
            ComponentKey("org.lineageos.jelly", "org.lineageos.jelly.MainActivity") to LivingIconId.BROWSER,
            ComponentKey("com.android.settings", "com.android.settings.Settings") to LivingIconId.SETTINGS,
            ComponentKey("com.sec.android.app.music", "com.sec.android.app.music.common.activity.MusicMainActivity") to LivingIconId.MUSIC,
            ComponentKey("com.google.android.calendar", "com.android.calendar.AllInOneActivity") to LivingIconId.CALENDAR,
            ComponentKey("com.google.android.deskclock", "com.android.deskclock.DeskClock") to LivingIconId.CLOCK,
            ComponentKey("com.google.android.apps.nbu.files", "com.google.android.apps.nbu.files.home.HomeActivity") to LivingIconId.FILES,
            ComponentKey("com.google.android.calculator", "com.android.calculator2.Calculator") to LivingIconId.CALCULATOR,
            ComponentKey("com.google.android.contacts", "com.android.contacts.activities.PeopleActivity") to LivingIconId.CONTACTS,
            ComponentKey("com.google.android.apps.maps", "com.google.android.maps.MapsActivity") to LivingIconId.MAPS,
            ComponentKey("com.miui.weather2", "com.miui.weather2.ActivityWeatherMain") to LivingIconId.WEATHER,
            ComponentKey("com.google.android.apps.recorder", "com.google.android.apps.recorder.ui.MainActivity") to LivingIconId.RECORDER,
            ComponentKey("com.google.android.keep", "com.google.android.keep.activities.BrowseActivity") to LivingIconId.NOTES,
            ComponentKey("com.samsung.android.email.provider", "com.samsung.android.email.ui.activity.MessageListXL") to LivingIconId.MAIL,
            ComponentKey("com.android.providers.downloads.ui", "com.android.providers.downloads.ui.DownloadList") to LivingIconId.DOWNLOADS,
            ComponentKey("com.android.vending", "com.android.vending.AssetBrowserActivity") to LivingIconId.APP_STORE,
            ComponentKey("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity") to LivingIconId.BATTERY
        )
        val coveredIds = HashSet<LivingIconId>()
        for ((key, expected) in cases) {
            val actual = LivingIconResolver.resolve(key, livingIconsEnabled = true)
            assertEquals("Mismatch for $key", expected, actual)
            coveredIds += actual!!
        }
        assertEquals(20, coveredIds.size)
    }

    @Test
    fun `keeps generic Photos Browser Mail strictly distinct from Google Photos Chrome Gmail`() {
        assertEquals(
            LivingIconId.PHOTOS,
            LivingIconResolver.resolveDefault("com.android.gallery3d", "com.android.gallery3d.app.GalleryActivity")
        )
        assertEquals(
            LivingIconId.GOOGLE_PHOTOS,
            LivingIconResolver.resolveDefault("com.google.android.apps.photos", "com.google.android.apps.photos.home.HomeActivity")
        )
        assertEquals(
            LivingIconId.BROWSER,
            LivingIconResolver.resolveDefault("com.android.browser", "com.android.browser.BrowserActivity")
        )
        assertEquals(
            LivingIconId.GOOGLE_CHROME,
            LivingIconResolver.resolveDefault("com.android.chrome", "com.google.android.apps.chrome.Main")
        )
        assertEquals(
            LivingIconId.MAIL,
            LivingIconResolver.resolveDefault("com.android.email", "com.android.email.activity.Welcome")
        )
        assertEquals(
            LivingIconId.GMAIL,
            LivingIconResolver.resolveDefault("com.google.android.gm", "com.google.android.gm.ConversationListActivityGmail")
        )
    }

    @Test
    fun `disambiguates multi-launcher system packages by activity class`() {
        // Combined Contacts + Dialer package
        assertEquals(
            LivingIconId.PHONE,
            LivingIconResolver.resolveDefault("com.android.contacts", "com.android.contacts.activities.DialtactsActivity")
        )
        assertEquals(
            LivingIconId.CONTACTS,
            LivingIconResolver.resolveDefault("com.android.contacts", "com.android.contacts.activities.PeopleActivity")
        )

        // DocumentsUI Files vs Downloads launcher alias
        assertEquals(
            LivingIconId.FILES,
            LivingIconResolver.resolveDefault("com.google.android.documentsui", "com.android.documentsui.LauncherActivity")
        )
        assertEquals(
            LivingIconId.DOWNLOADS,
            LivingIconResolver.resolveDefault("com.google.android.documentsui", "com.android.documentsui.DownloadsActivity")
        )

        // Settings vs Battery Usage shortcut activity
        assertEquals(
            LivingIconId.SETTINGS,
            LivingIconResolver.resolveDefault("com.android.settings", "com.android.settings.Settings")
        )
        assertEquals(
            LivingIconId.BATTERY,
            LivingIconResolver.resolveDefault("com.android.settings", "com.android.settings.Settings\$PowerUsageSummaryActivity")
        )
    }

    @Test
    fun `never fabricates mappings for unverified or lookalike packages`() {
        val unmappedPackages = listOf(
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.netflix.mediaclient",
            "com.slack",
            "com.zhiliaoapp.musically",
            "com.fake.whatsapp.clone",
            "com.example.myweatherapp",
            "com.bank.secure",
            ""
        )
        for (pkg in unmappedPackages) {
            assertNull(
                "Expected null fallback for unverified package '$pkg'",
                LivingIconResolver.resolveDefault(pkg, "$pkg.MainActivity")
            )
            assertNull(
                "Expected null package lookup for '$pkg'",
                LivingIconResolver.resolvePackage(pkg, livingIconsEnabled = true)
            )
        }
    }

    @Test
    fun `respects global toggle per-app overrides and work-profile inheritance`() {
        val spotifyPersonal = ComponentKey("com.spotify.music", "com.spotify.music.MainActivity", 0L)
        val spotifyWork = ComponentKey("com.spotify.music", "com.spotify.music.MainActivity", 10L)
        val customNotesApp = ComponentKey("md.obsidian", "md.obsidian.MainActivity", 0L)

        // Global toggle off disables automatic mappings
        assertNull(LivingIconResolver.resolve(spotifyPersonal, livingIconsEnabled = false))

        // Explicit UseLiving works even when global automatic mapping is off, and covers unmapped apps
        val overrides = mapOf(
            customNotesApp to LivingIconOverride.UseLiving(LivingIconId.NOTES),
            spotifyPersonal to LivingIconOverride.UseSystem
        )
        assertEquals(
            LivingIconId.NOTES,
            LivingIconResolver.resolve(customNotesApp, livingIconsEnabled = false, overrides = overrides)
        )

        // UseSystem forces fallback to app's original Android icon
        assertNull(LivingIconResolver.resolve(spotifyPersonal, livingIconsEnabled = true, overrides = overrides))
        // Work profile inherits user-0 override unless overridden explicitly
        assertNull(LivingIconResolver.resolve(spotifyWork, livingIconsEnabled = true, overrides = overrides))

        val workSpecificOverrides = overrides + (spotifyWork to LivingIconOverride.UseLiving(LivingIconId.MUSIC))
        assertEquals(
            LivingIconId.MUSIC,
            LivingIconResolver.resolve(spotifyWork, livingIconsEnabled = true, overrides = workSpecificOverrides)
        )
    }

    @Test
    fun `LivingIconOverridesCodec encodes deterministically and skips corrupt entries`() {
        val k1 = ComponentKey("com.spotify.music", "com.spotify.music.MainActivity", 0L)
        val k2 = ComponentKey("md.obsidian", "md.obsidian.MainActivity", 10L)
        val original = mapOf(
            k2 to LivingIconOverride.UseLiving(LivingIconId.NOTES),
            k1 to LivingIconOverride.UseSystem
        )
        val encoded = LivingIconOverridesCodec.encode(original)
        assertEquals("0:com.spotify.music/com.spotify.music.MainActivity=system|10:md.obsidian/md.obsidian.MainActivity=notes", encoded)
        assertEquals(original, LivingIconOverridesCodec.decode(encoded))

        // Corrupt tokens, missing separators, and unknown future icon IDs degrade gracefully
        val noisy = "$encoded|corrupt_entry|0:bad=unknown_future_icon|=phone|0:com.a/com.a.B="
        assertEquals(original, LivingIconOverridesCodec.decode(noisy))
        assertTrue(LivingIconOverridesCodec.decode(null).isEmpty())
        assertTrue(LivingIconOverridesCodec.decode("   ").isEmpty())
    }

    @Test
    fun `LivingIconManager sample size scales down cleanly for launcher icon sizes`() {
        assertEquals(2, LivingIconManager.computeSampleSize(96))
        assertEquals(2, LivingIconManager.computeSampleSize(192))
        assertEquals(2, LivingIconManager.computeSampleSize(384))
        assertEquals(1, LivingIconManager.computeSampleSize(512))
    }
}
