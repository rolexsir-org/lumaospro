package com.lumaos.pro.launcher.robolectric

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lumaos.pro.launcher.effects.EffectsPolicy
import com.lumaos.pro.launcher.effects.EffectsPreference
import com.lumaos.pro.launcher.effects.EffectsSignals
import com.lumaos.pro.launcher.intelligence.DeviceTier
import com.lumaos.pro.launcher.intelligence.ProfileId
import com.lumaos.pro.launcher.launcher.home.LumaMenu
import com.lumaos.pro.launcher.launcher.home.MenuEntry
import com.lumaos.pro.launcher.motion.MotionPolicy
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeEnvironment
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w240dp-h320dp")
class LumaMenuRuntimeTest {
    @Test
    fun `long menus fit the visible frame without discarding actions`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val anchor = View(activity)
        activity.setContentView(anchor)
        val theme = ThemeEngine.resolve(ThemeSettings(), ThemeEnvironment(true, 0))
        val effects = EffectsPolicy.decide(EffectsPreference.AUTO, EffectsSignals(DeviceTier.MEDIUM, ProfileId.BALANCED, 34))
        val entries = (0 until 20).map { MenuEntry(it, "Action $it with a long label") }
        val popup = LumaMenu(activity, theme, effects, MotionPolicy.DEFAULT).show(anchor, null, entries) {}
        val frame = Rect().also(anchor::getWindowVisibleDisplayFrame)
        val maxHeight = if (frame.isEmpty) activity.resources.displayMetrics.heightPixels else frame.height()
        assertTrue(popup.isShowing)
        assertTrue(popup.height in 1..maxHeight)
        assertTrue(popup.contentView is ScrollView)
        val content = (popup.contentView as ScrollView).getChildAt(0) as LinearLayout
        assertEquals(entries.size, content.childCount)
        popup.dismiss()
    }
}
