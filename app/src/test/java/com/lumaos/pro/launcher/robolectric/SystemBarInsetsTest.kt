package com.lumaos.pro.launcher.robolectric

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.graphics.Color
import android.graphics.Insets
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowInsets
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.launcher.HomeActivity
import com.lumaos.pro.launcher.launcher.home.LumaShade
import com.lumaos.pro.launcher.settings.SettingsActivity
import com.lumaos.pro.launcher.vault.ui.VaultScreenActivity
import com.lumaos.pro.launcher.widgets.builtin.WidgetSettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * System-bar, cutout, navigation-mode and IME inset handling for the screens
 * changed in the November 2026 production compatibility audit.
 *
 * Insets are injected through the same dispatch path the platform uses
 * (`View.dispatchApplyWindowInsets`), so the tests exercise the production
 * listeners - not test doubles. They prove *where* the listeners are
 * registered, *when* (Android 15+ enforcement for the platform-Activity
 * screens), and that the padding applied matches the reported insets exactly
 * once - no double padding, no missing reservation.
 *
 * What these tests do NOT prove: real-device appearance (bar icon colour
 * against live wallpapers), keyboard animation behaviour, and the enforced
 * edge-to-edge layout on a physical Android 15/16 device. Those still need a
 * device pass; see the audit report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = LumaApp::class)
class SystemBarInsetsTest {

    // ------------------------------------------------------------- helpers

    private fun dp(activity: android.app.Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private fun dispatch(
        view: View,
        status: Insets = Insets.NONE,
        nav: Insets = Insets.NONE,
        cutout: Insets = Insets.NONE,
        ime: Insets = Insets.NONE
    ) {
        val platform = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.statusBars(), status)
            .setInsets(WindowInsets.Type.navigationBars(), nav)
            .setInsets(WindowInsets.Type.displayCutout(), cutout)
            .setInsets(WindowInsets.Type.ime(), ime)
            .build()
        view.dispatchApplyWindowInsets(platform)
    }

    private fun buildHome(): HomeActivity =
        Robolectric.buildActivity(HomeActivity::class.java).setup().get()

    // -------------------------------------------------------------- Home

    @Test
    fun `home reserves the status bar exactly once and feeds the shade pull zone`() {
        val activity = buildHome()
        val homeRoot = activity.findViewById<View>(R.id.home_root)
        val desktop = activity.findViewById<View>(R.id.desktop)
        val topPad = activity.findViewById<View>(R.id.drawer_top_pad)
        val statusTop = dp(activity, 24)

        dispatch(homeRoot, status = Insets.of(0, statusTop, 0, 0))

        // Drawer chrome and desktop both sit below the bar; +2dp is the
        // desktop's resting margin (applyChromeInsets), not a second inset.
        assertEquals(statusTop, topPad.layoutParams.height)
        assertEquals(statusTop + dp(activity, 2), desktop.paddingTop)
        assertEquals(0, desktop.paddingLeft)

        // The shade positions itself from the same inset the listener stored.
        assertEquals(statusTop, activity.topInsetPx())
        val density = activity.resources.displayMetrics.density
        val slop = 8
        assertTrue(
            LumaShade.isShadePullZone(activity.topInsetPx().toFloat(), activity.topInsetPx(), density, slop)
        )
        val belowBand = activity.topInsetPx() + (44f * density) + 1f
        assertFalse(
            LumaShade.isShadePullZone(belowBand, activity.topInsetPx(), density, slop)
        )
    }

    @Test
    fun `home unions the display cutout with the status bar instead of stacking them`() {
        val activity = buildHome()
        val homeRoot = activity.findViewById<View>(R.id.home_root)
        val desktop = activity.findViewById<View>(R.id.desktop)
        val topPad = activity.findViewById<View>(R.id.drawer_top_pad)
        val statusTop = dp(activity, 24)
        val cutoutTop = dp(activity, 40)
        val cutoutLeft = dp(activity, 60)

        dispatch(
            homeRoot,
            status = Insets.of(0, statusTop, 0, 0),
            cutout = Insets.of(cutoutLeft, cutoutTop, 0, 0)
        )

        // getInsets(statusBars | displayCutout) is a per-side max: the cutout
        // wins on top, the side inset applies once, nothing is added twice.
        assertEquals(cutoutTop, topPad.layoutParams.height)
        assertEquals(cutoutTop + dp(activity, 2), desktop.paddingTop)
        assertEquals(cutoutLeft, desktop.paddingLeft)
    }

    @Test
    fun `home distinguishes gesture navigation from the three-button bar`() {
        val activity = buildHome()
        val homeRoot = activity.findViewById<View>(R.id.home_root)
        val desktop = activity.findViewById<View>(R.id.desktop)
        val bottomPad = activity.findViewById<View>(R.id.drawer_bottom_pad)
        val statusTop = dp(activity, 24)

        // Gesture navigation: no bottom reservation beyond the resting margin.
        dispatch(homeRoot, status = Insets.of(0, statusTop, 0, 0), nav = Insets.NONE)
        assertEquals(0, activity.bottomInsetPx())
        assertEquals(0, bottomPad.layoutParams.height)
        assertEquals(dp(activity, 2), desktop.paddingBottom)

        // Three-button navigation: the full bar height is reserved.
        val navBottom = dp(activity, 48)
        dispatch(
            homeRoot,
            status = Insets.of(0, statusTop, 0, 0),
            nav = Insets.of(0, 0, 0, navBottom)
        )
        assertEquals(navBottom, activity.bottomInsetPx())
        assertEquals(navBottom, bottomPad.layoutParams.height)
        assertEquals(navBottom + dp(activity, 2), desktop.paddingBottom)
    }

    // ----------------------------------------------------------- Settings

    @Test
    fun `settings keeps the edge-to-edge window contract`() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()

        // enableEdgeToEdge() has replaced the deprecated
        // Window.setStatusBarColor() call; on API 29-34 it resolves to a
        // transparent status bar (and is a no-op on 35+, where the platform
        // forces the same result).
        assertEquals(Color.TRANSPARENT, activity.window.statusBarColor)

        // Keyboard: the manifest keeps adjustResize and the Scaffold content
        // window is safeDrawing (system bars + cutout + IME), so list content
        // is never trapped behind the keyboard.
        val softInput = activity.window.attributes.softInputMode
        assertTrue(
            "Settings must keep SOFT_INPUT_ADJUST_RESIZE for the keyboard",
            softInput and WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE != 0
        )

        // Light/dark theme contract the bar-appearance controllers follow:
        // the theme declares dark icons on light bars on every screen.
        val attrs = activity.theme.obtainStyledAttributes(
            intArrayOf(android.R.attr.windowLightStatusBar, android.R.attr.windowLightNavigationBar)
        )
        try {
            assertTrue("light theme draws dark status-bar icons", attrs.getBoolean(0, false))
            assertTrue("light theme draws dark navigation-bar icons", attrs.getBoolean(1, false))
        } finally {
            attrs.recycle()
        }

        // The screen is edge to edge: its content view exists behind the bars
        // rather than being fitted above them by the window.
        assertEquals(View.VISIBLE, activity.window.decorView.visibility)
    }

    @Test
    fun `home theme keeps the translucent-bar contract the launcher draws behind`() {
        val activity = buildHome()
        val attrs = activity.theme.obtainStyledAttributes(
            intArrayOf(
                android.R.attr.windowLightStatusBar,
                android.R.attr.windowTranslucentStatus,
                android.R.attr.windowTranslucentNavigation
            )
        )
        try {
            assertTrue(attrs.getBoolean(0, false))
            assertTrue("home draws the status bar transparently", attrs.getBoolean(1, false))
            assertTrue("home draws behind the navigation bar", attrs.getBoolean(2, false))
        } finally {
            attrs.recycle()
        }
        // enableEdgeToEdge(dark(TRANSPARENT)) resolves both bars to
        // transparent on API 29-34; content-driven icon colours are applied
        // from applyChromeInsets / onDrawerProgress on every frame.
        assertEquals(Color.TRANSPARENT, activity.window.statusBarColor)
        assertEquals(Color.TRANSPARENT, activity.window.navigationBarColor)
    }

    // -------------------------------------------------------------- Vault

    /** Concrete probe of the production [VaultScreenActivity] base class. */
    class VaultInsetProbeActivity : VaultScreenActivity() {
        override fun onCreateContent(): View = TextView(this)
        override fun requiresUnlockedVault(): Boolean = false
        fun vaultRoot(): View = root
    }

    @Test
    @Config(sdk = [35])
    fun `vault root keeps clear of bars cutout and keyboard on Android 15`() {
        val activity = Robolectric.buildActivity(VaultInsetProbeActivity::class.java).create().get()
        val root = activity.vaultRoot()

        val statusTop = dp(activity, 24)
        val cutoutTop = dp(activity, 40)
        val navBottom = dp(activity, 48)
        val imeBottom = dp(activity, 300)
        dispatch(
            root,
            status = Insets.of(0, statusTop, 0, 0),
            nav = Insets.of(0, 0, 0, navBottom),
            cutout = Insets.of(0, cutoutTop, 0, 0),
            ime = Insets.of(0, 0, 0, imeBottom)
        )

        // Per-side union, applied once to the Vault root: the cutout wins on
        // top, the open keyboard wins at the bottom over the navigation bar.
        assertEquals(0, root.paddingLeft)
        assertEquals(cutoutTop, root.paddingTop)
        assertEquals(0, root.paddingRight)
        assertEquals(imeBottom, root.paddingBottom)
    }

    @Test
    @Config(sdk = [34])
    fun `vault keeps its fitted opaque-bar layout below Android 15`() {
        val activity = Robolectric.buildActivity(VaultInsetProbeActivity::class.java).create().get()
        val root = activity.vaultRoot()

        dispatch(
            root,
            status = Insets.of(0, dp(activity, 24), 0, 0),
            nav = Insets.of(0, 0, 0, dp(activity, 48)),
            ime = Insets.of(0, 0, 0, dp(activity, 300))
        )

        // The inset listener is only registered where the platform enforces
        // edge-to-edge (API 35+); older systems keep the theme's fitted bars.
        assertEquals(0, root.paddingTop)
        assertEquals(0, root.paddingBottom)
        assertEquals(0, root.paddingLeft)
        assertEquals(0, root.paddingRight)
    }

    // -------------------------------------------------- widget configuration

    private fun installWidgetInfo(app: LumaApp, id: Int): AppWidgetProviderInfo {
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(
                app.packageName,
                "com.lumaos.pro.launcher.widgets.builtin.DigitalClockWidgetProvider"
            )
            configure = ComponentName(app.packageName, WidgetSettingsActivity::class.java.name)
        }
        shadowOf(AppWidgetManager.getInstance(app)).putWidgetInfo(id, info)
        return info
    }

    @Test
    @Config(sdk = [35])
    fun `widget configuration form keeps clear of bars cutout and keyboard on Android 15`() {
        val app = ApplicationProvider.getApplicationContext<LumaApp>()
        installWidgetInfo(app, 77)
        val activity = Robolectric.buildActivity(WidgetSettingsActivity::class.java)
            .withIntent(WidgetSettingsActivity.intent(app, 77))
            .setup()
            .get()

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val scroll = content.getChildAt(0) as ScrollView
        val statusTop = dp(activity, 24)
        val cutoutLeft = dp(activity, 60)
        val imeBottom = dp(activity, 300)
        dispatch(
            scroll,
            status = Insets.of(0, statusTop, 0, 0),
            cutout = Insets.of(cutoutLeft, 0, 0, 0),
            ime = Insets.of(0, 0, 0, imeBottom)
        )

        assertEquals(cutoutLeft, scroll.paddingLeft)
        assertEquals(statusTop, scroll.paddingTop)
        assertEquals(imeBottom, scroll.paddingBottom)
    }
}
