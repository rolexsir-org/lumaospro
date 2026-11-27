package com.lumaos.pro.launcher.controls

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceControlsAudioTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val controls = DeviceControls(context)

    @Test fun streamReadingsComeFromTheCorrespondingAudioManagerStream() {
        val streams = mapOf(
            AudioStream.MEDIA to AudioManager.STREAM_MUSIC,
            AudioStream.RING to AudioManager.STREAM_RING,
            AudioStream.NOTIFICATION to AudioManager.STREAM_NOTIFICATION,
            AudioStream.ALARM to AudioManager.STREAM_ALARM
        )
        streams.forEach { (stream, platformStream) ->
            val state = controls.volumeState(stream)
            assertEquals(audio.getStreamMaxVolume(platformStream), state.max)
            assertEquals(audio.getStreamVolume(platformStream), state.value)
            assertTrue(state.min <= state.value && state.value <= state.max)
        }
    }

    @Test fun alarmWritesUseReportedBoundsAndReadBack() {
        val before = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        try {
            val state = controls.volumeState(AudioStream.ALARM)
            assertTrue(state.max > state.min)
            assertTrue(controls.applyVolume(1f, AudioStream.ALARM))
            assertEquals(state.max, controls.volumeState(AudioStream.ALARM).value)
        } finally {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, before, 0)
        }
    }

    @Test fun ringerModeIsNotInferredFromAStreamLevel() {
        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        assertEquals(RingerMode.VIBRATE, controls.ringerMode())
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        assertEquals(RingerMode.NORMAL, controls.ringerMode())
    }
}
