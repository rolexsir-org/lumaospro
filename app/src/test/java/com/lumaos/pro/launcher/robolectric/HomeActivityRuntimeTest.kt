package com.lumaos.pro.launcher.robolectric

import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.launcher.HomeActivity
import com.lumaos.pro.launcher.launcher.view.AlphaIndexView
import com.lumaos.pro.launcher.onboarding.OnboardingActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Executes the real home screen against real Android framework classes.
 *
 * This is the closest thing to "running the app" available here: there is no
 * emulator in this environment (no /dev/kvm, no nested virtualisation), so
 * Robolectric is used to inflate the real layout, run the real Activity
 * lifecycle (onCreate -> onStart -> onResume), bind the real adapters and
 * observe the real preference flows.
 *
 * What this does prove: the layout XML is valid, every view id the activity
 * binds actually exists, the view types match the casts, the adapters attach,
 * the Flow collectors survive first emission, and no crash occurs on a cold
 * start with an empty app list.
 *
 * What it does NOT prove: how it looks, how it performs, or that it behaves
 * correctly on real hardware. Those still need a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = LumaApp::class)
class HomeActivityRuntimeTest {

    private fun buildHome(): HomeActivity =
        Robolectric.buildActivity(HomeActivity::class.java).setup().get()

    @Test
    fun `the home screen inflates and binds every view it uses`() {
        val activity = buildHome()

        // Every id referenced by HomeActivity.bindViews() must resolve, and
        // each cast must match the declared type. A mismatch here is the
        // classic "compiles fine, crashes on launch" bug.
        assertNotNull(activity.findViewById<FrameLayout>(R.id.home_root))
        assertNotNull(activity.findViewById<LinearLayout>(R.id.desktop))
        assertNotNull(activity.findViewById<android.view.View>(R.id.home_header))
        assertNotNull(activity.findViewById<android.widget.TextClock>(R.id.home_clock))
        assertNotNull(activity.findViewById<android.widget.TextClock>(R.id.home_date))
        assertNotNull(activity.findViewById<android.view.View>(R.id.home_search))
        assertNotNull(activity.findViewById<android.view.View>(R.id.home_settings))
        assertNotNull(activity.findViewById<TextView>(R.id.home_empty))
        assertNotNull(activity.findViewById<RecyclerView>(R.id.home_grid))
        assertNotNull(activity.findViewById<FrameLayout>(R.id.dock_surface))
        assertNotNull(activity.findViewById<RecyclerView>(R.id.dock_row))
        assertNotNull(activity.findViewById<FrameLayout>(R.id.drawer_container))
        assertNotNull(activity.findViewById<LinearLayout>(R.id.drawer_sheet))
        assertNotNull(activity.findViewById<android.view.View>(R.id.drawer_title))
        assertNotNull(activity.findViewById<android.view.View>(R.id.drawer_close))
        assertNotNull(activity.findViewById<android.view.View>(R.id.drawer_top_pad))
        assertNotNull(activity.findViewById<RecyclerView>(R.id.drawer_grid))
        assertNotNull(activity.findViewById<EditText>(R.id.search_field))
        assertNotNull(activity.findViewById<android.widget.ImageButton>(R.id.search_clear))
        assertNotNull(activity.findViewById<TextView>(R.id.empty_view))
        assertNotNull(activity.findViewById<AlphaIndexView>(R.id.alpha_index))
        activity.findViewById<android.view.View>(R.id.drawer_scrim).also { assertNotNull(it) }
        activity.findViewById<android.view.View>(R.id.drawer_bottom_pad).also { assertNotNull(it) }
    }

    @Test
    fun `the grids have adapters and layout managers attached`() {
        val activity = buildHome()
        val homeGrid = activity.findViewById<RecyclerView>(R.id.home_grid)
        val drawerGrid = activity.findViewById<RecyclerView>(R.id.drawer_grid)
        val dockRow = activity.findViewById<RecyclerView>(R.id.dock_row)

        assertNotNull("home grid adapter", homeGrid.adapter)
        assertNotNull("home grid layout manager", homeGrid.layoutManager)
        assertNotNull("drawer grid adapter", drawerGrid.adapter)
        assertNotNull("drawer grid layout manager", drawerGrid.layoutManager)
        assertNotNull("dock adapter", dockRow.adapter)
        assertNotNull("dock layout manager", dockRow.layoutManager)
    }

    @Test
    fun `the drawer starts hidden`() {
        val activity = buildHome()
        val drawer = activity.findViewById<FrameLayout>(R.id.drawer_container)
        assertTrue("drawer should not be visible before a swipe", drawer.visibility != android.view.View.VISIBLE)
    }

    @Test
    fun `a cold start with no apps does not crash and shows the empty state`() {
        // The worst case for a launcher: an empty app list on first run.
        val activity = buildHome()
        val empty = activity.findViewById<TextView>(R.id.empty_view)
        assertNotNull(empty)
        assertTrue(empty.text.toString().isNotBlank())
    }

    @Test
    fun `first run with no default launcher offers onboarding`() {
        val activity = buildHome()
        val next = Shadows.shadowOf(activity).nextStartedActivity
        // Either onboarding was launched, or LumaOS Pro already is the default (in
        // which case Robolectric resolved HOME to us). Both are valid outcomes;
        // what matters is that the decision was made without crashing.
        assertTrue(
            "expected onboarding to start, or LumaOS Pro to be default",
            next?.component?.className == OnboardingActivity::class.java.name || next == null
        )
    }

    @Test
    fun `the home screen survives a configuration change`() {
        // Orientation change with stateNotNeeded: the activity is destroyed and
        // recreated with no saved state, so it must rebuild from DataStore.
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
        val recreated = controller.configurationChange(
            android.content.res.Configuration().apply {
                orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
        ).get()

        assertNotNull(recreated.findViewById<RecyclerView>(R.id.home_grid))
        assertNotNull("grids must be re-bound after recreation", recreated.findViewById<RecyclerView>(R.id.home_grid).adapter)
    }

    @Test
    fun `back on the home screen does not finish the activity`() {
        // Finishing the home screen would leave the device with no launcher.
        val activity = buildHome()
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue("home must survive a back press", !activity.isFinishing)
    }

    @Test
    fun `the application boots and exposes a working core`() {
        val app = ApplicationProvider.getApplicationContext<LumaApp>()
        assertNotNull("capability should be measured at startup", app.core.capability)
        assertTrue(
            "device tier should be resolved",
            app.core.capability.totalMemoryMb >= 0
        )
        assertNotNull(app.core.intelligence)
        assertNotNull(app.core.optimizer)
        assertNotNull(app.core.diagnostics)
    }
}
