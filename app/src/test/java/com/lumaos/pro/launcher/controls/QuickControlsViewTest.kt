package com.lumaos.pro.launcher.controls

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.motion.MotionPolicy
import com.lumaos.pro.launcher.ui.theme.GlassCapabilities
import com.lumaos.pro.launcher.ui.theme.GlassMaterials
import com.lumaos.pro.launcher.ui.theme.GlassPalette
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeEnvironment
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import com.lumaos.pro.launcher.widgets.music.MusicCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuickControlsViewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val theme = ThemeEngine.resolve(ThemeSettings(), ThemeEnvironment(true, 720))
    private val host = object : QuickControlsHost {
        override fun theme(): ResolvedTheme = theme
        override fun glassPalette(): GlassPalette = GlassMaterials.palette(theme, GlassCapabilities.DEFAULT)
        override fun density(): Float = context.resources.displayMetrics.density
        override fun motion(): MotionPolicy = MotionPolicy.DEFAULT
        override fun availableWidthDp() = 320
        override fun mediaArtwork(): Drawable? = null
        override fun mediaAccessGranted() = false
        override fun onTileTap(id: TileId) = Unit
        override fun onTileLongPress(id: TileId) = Unit
        override fun onBrightnessChanged(fraction: Float, commit: Boolean) = Unit
        override fun onVolumeChanged(fraction: Float, commit: Boolean) = Unit
        override fun onMediaCommand(command: MusicCommand) = Unit
        override fun onNeedsWriteSettings() = Unit
        override fun onNeedsNotificationPolicy() = Unit
        override fun onNeedsNotificationAccess() = Unit
    }

    @Test
    fun tileDecorationDoesNotConsumeAvailableScreenHeight() {
        val tile = QuickControlsView(context, host).TileView(context, host, TileId.WIFI)
        val density = host.density()
        tile.measure(
            View.MeasureSpec.makeMeasureSpec((100 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((700 * density).toInt(), View.MeasureSpec.AT_MOST)
        )
        tile.layout(0, 0, tile.measuredWidth, tile.measuredHeight)
        assertTrue("tile should wrap its content, not the available screen", tile.measuredHeight < 140 * density)
        assertEquals(tile.width, tile.getChildAt(0).width)
        assertEquals(tile.height, tile.getChildAt(0).height)
    }

    @Test
    fun restrictedSliderStillDispatchesPermissionClick() {
        val slider = GlassSlider(context)
        slider.interactive = false
        var clicked = false
        slider.setOnClickListener { clicked = true }
        slider.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY))
        slider.layout(0, 0, 200, 80)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 40f, 0)
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 50f, 40f, 0)
        try {
            slider.onTouchEvent(down)
            slider.onTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
        assertTrue(clicked)
    }

    @Test
    fun platformRefreshDoesNotMoveSliderUnderFinger() {
        val slider = GlassSlider(context)
        slider.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY))
        slider.layout(0, 0, 200, 80)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 150f, 40f, 0)
        try {
            slider.onTouchEvent(down)
            val dragged = slider.value()
            slider.setValueSilently(0f)
            assertEquals(dragged, slider.value(), 0.0001f)
        } finally {
            down.recycle()
        }
    }
}
