package com.lumaos.pro.launcher.controls

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class QuickControlsLifecycleTest {
    @Test fun stoppingAndShuttingDownCancelsPendingRefreshAndAudioPoll() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controls = QuickControlsController(context)
        controls.start()
        controls.stop()
        controls.shutdown()
        // A pending callback must not submit work to the terminated executor.
        shadowOf(Looper.getMainLooper()).idleFor(3, TimeUnit.SECONDS)
        assertEquals(QuickControlsSnapshot.EMPTY, controls.state.value)
    }
}
