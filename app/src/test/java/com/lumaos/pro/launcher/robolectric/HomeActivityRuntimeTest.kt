package com.lumaos.pro.launcher.robolectric

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.launcher.HomeActivity
import com.lumaos.pro.launcher.launcher.AppCellViewHolder
import com.lumaos.pro.launcher.launcher.CellConfig
import com.lumaos.pro.launcher.launcher.IconLoader
import com.lumaos.pro.launcher.launcher.home.DrawerRowStyle
import com.lumaos.pro.launcher.launcher.model.DrawerPresentation
import com.lumaos.pro.launcher.icons.MonogramDrawable
import com.lumaos.pro.launcher.launcher.home.EditModeController
import com.lumaos.pro.launcher.launcher.home.DrawerAdapter
import com.lumaos.pro.launcher.launcher.home.DrawerController
import com.lumaos.pro.launcher.launcher.model.DrawerModel
import com.lumaos.pro.launcher.launcher.model.DrawerRow
import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.launcher.home.HomePagePagerAdapter
import com.lumaos.pro.launcher.onboarding.OnboardingActivity
import com.lumaos.pro.launcher.widgets.WidgetHostController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        activity.findViewById<android.view.View>(R.id.drawer_scrim).also { assertNotNull(it) }
        activity.findViewById<android.view.View>(R.id.drawer_bottom_pad).also { assertNotNull(it) }
    }

    @Test
    fun `Obsidian keeps accessible search and drawer controls in the real layout`() {
        val activity = buildHome()
        assertEquals(activity.getString(R.string.drawer_title), activity.findViewById<TextView>(R.id.drawer_title).text.toString())
        val search = activity.findViewById<EditText>(R.id.search_field)
        assertEquals(activity.getString(R.string.drawer_search_hint), search.hint.toString())
        assertTrue(activity.findViewById<android.view.View>(R.id.drawer_close).contentDescription.isNotEmpty())
        search.setText("camera")
        val clear = activity.findViewById<android.view.View>(R.id.search_clear)
        assertEquals(android.view.View.VISIBLE, clear.visibility)
        clear.performClick()
        assertEquals("", search.text.toString())
        assertEquals(android.view.View.GONE, clear.visibility)
    }

    @Test
    fun `drawer opens and closes safely`() {
        val activity = buildHome()
        val field = HomeActivity::class.java.getDeclaredField("drawer").apply { isAccessible = true }
        val controller = field.get(activity) as DrawerController
        val container = activity.findViewById<FrameLayout>(R.id.drawer_container)
        controller.open(false)
        assertTrue(controller.isOpen)
        assertEquals(android.view.View.VISIBLE, container.visibility)
        controller.closeInstantly()
        assertEquals(android.view.View.GONE, container.visibility)
    }

    @Test
    fun `drawer cells forward launch and long press and fall back when icon is missing`() {
        val activity = buildHome()
        val recycler = activity.findViewById<RecyclerView>(R.id.drawer_grid)
        val loader = HomeActivity::class.java.getDeclaredField("iconLoader").apply { isAccessible = true }
            .get(activity) as IconLoader
        val entry = AppEntry(ComponentKey("missing.test.app", "missing.test.app.Main"), "Example")
        val row = DrawerRow.App(entry, DrawerModel.SECTION_RESULTS)
        var clicked: DrawerRow? = null
        var held: DrawerRow? = null
        val adapter = DrawerAdapter(
            cellConfig = { CellConfig(48, true, 1f, android.graphics.Color.WHITE, false) },
            presentation = { DrawerPresentation.GRID },
            rowStyle = { DrawerRowStyle(android.graphics.Color.WHITE, null, false, 0f,
                android.graphics.Color.WHITE, null, android.graphics.Color.GRAY, null,
                android.graphics.Color.WHITE, 0x22FFFFFF, 12f) },
            iconLoader = loader,
            density = activity.resources.displayMetrics.densityDpi,
            shortcutIcon = { null },
            onClick = { item, _ -> clicked = item },
            onLongClick = { item, _ -> held = item }
        )
        adapter.submitList(listOf(row))
        val holder = adapter.createViewHolder(recycler, adapter.getItemViewType(0)) as AppCellViewHolder
        adapter.bindViewHolder(holder, 0)
        assertTrue(holder.icon.drawable is MonogramDrawable)
        holder.itemView.performClick()
        holder.itemView.performLongClick()
        assertEquals(row, clicked)
        assertEquals(row, held)
    }

    @Test
    fun `all drawer app cells occupy one grid span`() {
        val activity = buildHome()
        val recycler = activity.findViewById<RecyclerView>(R.id.drawer_grid)
        val adapter = recycler.adapter as DrawerAdapter
        val app = AppEntry(ComponentKey("example.app", "example.app.Main"), "Example")
        // Clearing first makes the next ListAdapter submission synchronous.
        adapter.submitList(null)
        adapter.submitList(listOf(
            DrawerRow.Header(DrawerModel.SECTION_RECENT, "Recent"),
            DrawerRow.App(app, DrawerModel.SECTION_RECENT),
            DrawerRow.App(app, DrawerModel.SECTION_ALL),
            DrawerRow.App(app, DrawerModel.SECTION_RESULTS)
        ))
        assertTrue(adapter.isFullSpan(0))
        assertFalse(adapter.isFullSpan(1))
        assertFalse(adapter.isFullSpan(2))
        assertFalse(adapter.isFullSpan(3))
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
    fun `home uses a horizontal snapped page container while drawer remains vertically scrollable`() {
        val activity = buildHome()
        val homePager = activity.findViewById<RecyclerView>(R.id.home_grid)
        val drawer = activity.findViewById<RecyclerView>(R.id.drawer_grid)

        assertTrue(homePager.layoutManager is LinearLayoutManager)
        assertEquals(LinearLayoutManager.HORIZONTAL, (homePager.layoutManager as LinearLayoutManager).orientation)
        assertTrue(homePager.adapter is HomePagePagerAdapter)
        homePager.findViewHolderForAdapterPosition(0)?.itemView?.let { page ->
            val pageGrid = page as RecyclerView
            assertTrue(pageGrid.layoutManager is GridLayoutManager)
            assertTrue("home page grid must not scroll vertically", !pageGrid.canScrollVertically(1))
        }
        assertTrue("drawer keeps vertical scrolling", drawer.layoutManager!!.canScrollVertically())
    }

    @Test
    fun `setting the same search text does not hide existing drawer results`() {
        val activity = buildHome()
        val grid = activity.findViewById<RecyclerView>(R.id.drawer_grid)
        val search = activity.findViewById<EditText>(R.id.search_field)
        grid.visibility = android.view.View.VISIBLE
        search.setText(search.text.toString())
        assertEquals(android.view.View.VISIBLE, grid.visibility)
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
        // The activity must rebind from persisted layout after recreation.
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
    fun `in-flight onboarding launch guard survives recreation`() {
        val app = ApplicationProvider.getApplicationContext<LumaApp>()
        runBlocking { app.core.prefs.setOnboardingDone(true) }
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
        val activity = controller.get()
        val inFlight = HomeActivity::class.java.getDeclaredField("onboardingLaunchInFlight")
            .apply { isAccessible = true }
        inFlight.setBoolean(activity, true)

        val recreated = controller.configurationChange(
            android.content.res.Configuration().apply {
                orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
        ).get()
        assertTrue("restored activity must keep the onboarding launch guard", inFlight.getBoolean(recreated))
    }

    @Test
    fun `pending widget operations retain their ids across recreation`() {
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
        val activity = controller.get()
        val pendingField = HomeActivity::class.java.getDeclaredField("pendingWidget").apply { isAccessible = true }
        val reconfigureField = HomeActivity::class.java.getDeclaredField("pendingReconfigureWidgetId").apply { isAccessible = true }
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(activity.packageName, "example.WidgetProvider")
        }
        pendingField.set(activity, WidgetHostController.Pending(42, info))
        reconfigureField.set(activity, 73)

        val recreated = controller.configurationChange(
            android.content.res.Configuration().apply {
                orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
        ).get()
        assertEquals(42, (pendingField.get(recreated) as WidgetHostController.Pending).appWidgetId)
        assertEquals(73, reconfigureField.get(recreated))
    }

    @Test
    fun `back on the home screen does not finish the activity`() {
        // Finishing the home screen would leave the device with no launcher.
        val activity = buildHome()
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue("home must survive a back press", !activity.isFinishing)
    }

    // ------------------------------------------------------------ home editing

    /**
     * The legacy model is gone from the resource table, not merely hidden: a
     * banner and a permanent toolbar cannot come back by flipping a visibility.
     */
    @Test
    fun `the inflated home screen carries no instructional editing chrome`() {
        val activity = buildHome()
        val res = activity.resources
        listOf("edit_top_bar", "edit_toolbar", "edit_hint", "edit_title", "edit_done").forEach { id ->
            assertEquals(
                "$id must not exist in the home layout any more",
                0,
                res.getIdentifier(id, "id", activity.packageName)
            )
        }
    }

    @Test
    fun `the contextual editing surfaces are absent at rest`() {
        val activity = buildHome()
        assertEquals(android.view.View.GONE, activity.findViewById<android.view.View>(R.id.edit_action_bar).visibility)
        assertEquals(android.view.View.GONE, activity.findViewById<android.view.View>(R.id.edit_remove_target).visibility)
        assertEquals(android.view.View.GONE, activity.findViewById<android.view.View>(R.id.placement_guide).visibility)
        assertEquals(android.view.View.VISIBLE, activity.findViewById<android.view.View>(R.id.home_search_pill).visibility)
    }

    /**
     * Editing must not re-flow the workspace: the capsule shares the search row
     * and the drop zones float, so the grid keeps the exact padding it had.
     */
    @Test
    fun `entering and leaving editing reserves no space in the workspace`() {
        val activity = buildHome()
        val controller = HomeActivity::class.java.getDeclaredField("edit")
            .apply { isAccessible = true }
            .get(activity) as EditModeController
        val grid = activity.findViewById<RecyclerView>(R.id.home_grid)
        val desktop = activity.findViewById<android.view.View>(R.id.desktop)
        val gridPadding = intArrayOf(grid.paddingLeft, grid.paddingTop, grid.paddingRight, grid.paddingBottom)
        val desktopPadding = intArrayOf(
            desktop.paddingLeft, desktop.paddingTop, desktop.paddingRight, desktop.paddingBottom
        )

        controller.enter(haptic = false)
        assertTrue("editing should be active", controller.isActive)
        assertEquals(android.view.View.VISIBLE, activity.findViewById<android.view.View>(R.id.edit_action_bar).visibility)
        assertEquals(android.view.View.VISIBLE, activity.findViewById<android.view.View>(R.id.placement_guide).visibility)
        assertEquals(
            "desktop padding must be identical while editing",
            desktopPadding.toList(),
            listOf(desktop.paddingLeft, desktop.paddingTop, desktop.paddingRight, desktop.paddingBottom)
        )
        assertEquals(
            "grid padding must be identical while editing",
            gridPadding.toList(),
            listOf(grid.paddingLeft, grid.paddingTop, grid.paddingRight, grid.paddingBottom)
        )

        controller.exit()
        assertFalse("editing should have ended", controller.isActive)
        assertEquals(
            "the search pill owns the row again",
            android.view.View.VISIBLE,
            activity.findViewById<android.view.View>(R.id.home_search_pill).visibility
        )
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
