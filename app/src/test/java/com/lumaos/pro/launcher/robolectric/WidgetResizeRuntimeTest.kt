package com.lumaos.pro.launcher.robolectric

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.launcher.CellConfig
import com.lumaos.pro.launcher.launcher.IconLoader
import com.lumaos.pro.launcher.launcher.home.FolderStyle
import com.lumaos.pro.launcher.launcher.home.HomeGridAdapter
import com.lumaos.pro.launcher.launcher.model.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = LumaApp::class)
class WidgetResizeRuntimeTest {
    @Test
    fun `resize handle opens options and cancelled drags do not commit`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val core = (activity.application as LumaApp).core
        val icons = IconLoader(core.appRepository, core.iconCache, core.iconPacks, activity.resources)
        val widget = HomeItem.Widget(42, "example/.WidgetProvider", 2)
        var optionsOpened = 0
        val commits = mutableListOf<Boolean>()
        val adapter = HomeGridAdapter(
            config = { CellConfig(48, true, 1f, -1, true) },
            iconLoader = icons,
            density = activity.resources.displayMetrics.densityDpi,
            folderStyle = { FolderStyle(0, 0, null, 0) },
            labelFor = { "" },
            smartMembers = { emptyList() },
            widgetHost = object : HomeGridAdapter.WidgetViews {
                override fun viewFor(item: HomeItem.Widget): View? = null
                override fun rowHeightPx() = 100
                override fun editOutline() = ColorDrawable()
                override fun handle() = ColorDrawable()
            },
            listener = object : HomeGridAdapter.Listener {
                override fun onItemClick(item: HomeItem, position: Int, view: View) = Unit
                // Simulate edit mode reserving the ordinary long press for dragging.
                override fun onItemLongClick(item: HomeItem, position: Int, view: View) = false
                override fun onWidgetResizeOptions(item: HomeItem.Widget, position: Int, view: View) {
                    assertEquals(widget, item)
                    optionsOpened++
                }
                override fun onWidgetResize(item: HomeItem.Widget, rows: Int, commit: Boolean) {
                    commits += commit
                }
            }
        )
        val recycler = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            this.adapter = adapter
        }
        activity.setContentView(recycler)
        adapter.editMode = true
        adapter.submit(listOf(widget))
        recycler.measure(
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        )
        recycler.layout(0, 0, 600, 600)
        val holder = recycler.findViewHolderForAdapterPosition(0)
        assertNotNull(holder)
        val handle = holder!!.itemView.findViewById<View>(R.id.widget_resize)
        assertTrue(handle.isClickable)
        assertTrue(handle.performClick())
        assertEquals(1, optionsOpened)

        val originalHeight = holder.itemView.layoutParams.height
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(0, 1, action, 10f, y, 0)
            handle.dispatchTouchEvent(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 10f)
        send(MotionEvent.ACTION_MOVE, 80f)
        send(MotionEvent.ACTION_CANCEL, 80f)
        assertTrue(commits.isNotEmpty())
        assertFalse(commits.any { it })
        assertEquals(originalHeight, holder.itemView.layoutParams.height)
        icons.shutdown()
    }
}
