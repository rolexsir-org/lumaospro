package com.lumaos.pro.launcher.controls

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import com.lumaos.pro.launcher.R
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
    private val streamWrites = mutableListOf<Triple<AudioStream, Float, Boolean>>()
    private var soundSettingsOpened = 0
    private var displaySettingsOpened = 0
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
        override fun onStreamVolumeChanged(stream: AudioStream, fraction: Float, commit: Boolean) {
            streamWrites += Triple(stream, fraction, commit)
        }
        override fun onToggleSoundMode() = Unit
        override fun onOpenSoundSettings() { soundSettingsOpened++ }
        override fun onOpenDisplaySettings() { displaySettingsOpened++ }
        override fun onMediaCommand(command: MusicCommand) = Unit
        override fun onMediaSeek(positionMs: Long, sessionId: Long?) = Unit
        override fun onNeedsWriteSettings() = Unit
        override fun onNeedsNotificationPolicy() = Unit
        override fun onNeedsNotificationAccess() = Unit
    }

    private fun findView(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findView(root.getChildAt(i), predicate)?.let { return it }
            }
        }
        return null
    }

    @Test
    fun expandedAudioControlsRespectCapabilitiesAndRouteUnavailableStream() {
        val view = QuickControlsView(context, host)
        view.rebuild(QuickControlsSnapshot(ringVolume = SliderState(3, 0, 5, Capability.DIRECT),
            notificationVolume = SliderState.UNAVAILABLE,
            alarmVolume = SliderState(2, 1, 7, Capability.DIRECT), ringerMode = RingerMode.VIBRATE), false)
        val expand = findView(view) { it is TextView && it.text == context.getString(R.string.qc_audio_more) }
        assertTrue(expand != null)
        expand!!.performClick()
        assertTrue(findView(view) { it is TextView && it.text == context.getString(R.string.qc_audio_less) } != null)
        assertTrue(findView(view) { it is TextView && it.text.toString().contains(context.getString(R.string.qc_sound_mode_vibrate)) } != null)
        val notificationSlider = findView(view) {
            it is GlassSlider && it.contentDescription == context.getString(R.string.qc_notification_volume)
        } as GlassSlider
        assertTrue(!notificationSlider.interactive)
        notificationSlider.performClick()
        assertEquals(1, soundSettingsOpened)
        val display = findView(view) { it is TextView && it.text == context.getString(R.string.qc_display_settings) }
        display!!.performClick()
        assertEquals(1, displaySettingsOpened)
    }

    @Test
    fun ringStreamSliderDispatchesCorrectStreamAndCommit() {
        val view = QuickControlsView(context, host)
        val row = view.AudioLevelRow(context, AudioStream.RING, R.string.qc_ring_volume)
        row.bind(SliderState(2, 0, 5, Capability.DIRECT))
        val slider = findView(row) { it is GlassSlider } as GlassSlider
        slider.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY))
        slider.layout(0, 0, 200, 80)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 150f, 40f, 0)
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 150f, 40f, 0)
        try {
            slider.onTouchEvent(down)
            slider.onTouchEvent(up)
            assertTrue(streamWrites.any { it.first == AudioStream.RING && it.third })
        } finally { down.recycle(); up.recycle() }
    }

    @Test
    fun seekControlOnlyAppearsForSeekableLiveSession() {
        fun expandedMedia(canSeek: Boolean): QuickControlsView {
            val view = QuickControlsView(context, host)
            val media = MediaSnapshot.EMPTY.copy(title = "Track", positionMs = 1000L,
                durationMs = 5000L, sessionId = 1L, canSeek = canSeek)
            view.rebuild(QuickControlsSnapshot(media = media), false)
            val expand = findView(view) {
                it is TextView && it.text == context.getString(R.string.qc_media_expand)
            }
            assertTrue(expand != null)
            expand!!.performClick()
            return view
        }
        assertTrue(findView(expandedMedia(false)) { it is SeekBar } == null)
        assertTrue(findView(expandedMedia(true)) { it is SeekBar } != null)
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

    @Test
    fun verticalSliderMapsBottomToMinimumAndTopToMaximum() {
        val slider = GlassSlider(context, vertical = true)
        slider.measure(View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.EXACTLY))
        slider.layout(0, 0, 80, 180)
        var committed = -1f
        slider.onCommit = { committed = it }
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 40f, 180f, 0)
        val move = MotionEvent.obtain(0, 5, MotionEvent.ACTION_MOVE, 40f, 0f, 0)
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 40f, 0f, 0)
        try {
            slider.onTouchEvent(down)
            assertEquals(0f, slider.value(), 0.0001f)
            slider.onTouchEvent(move)
            assertEquals(1f, slider.value(), 0.0001f)
            slider.onTouchEvent(up)
            assertEquals(1f, committed, 0.0001f)
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }
    }

    @Test
    fun restrictedVerticalSliderRoutesClickWithoutChangingValue() {
        val slider = GlassSlider(context, vertical = true)
        slider.interactive = false
        slider.setValueSilently(0.5f)
        var clicked = false
        slider.setOnClickListener { clicked = true }
        slider.measure(View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.EXACTLY))
        slider.layout(0, 0, 80, 180)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 40f, 10f, 0)
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 40f, 10f, 0)
        try {
            slider.onTouchEvent(down)
            slider.onTouchEvent(up)
            assertEquals(0.5f, slider.value(), 0.0001f)
            assertTrue(clicked)
        } finally {
            down.recycle()
            up.recycle()
        }
    }
}
