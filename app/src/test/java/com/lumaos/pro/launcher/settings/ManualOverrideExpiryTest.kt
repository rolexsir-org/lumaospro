package com.lumaos.pro.launcher.settings

import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.context.ManualOverride
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManualOverrideExpiryTest {

    @Test fun finiteOverrideWaitsForItsWallClockDeadline() = runTest {
        var wallClock = 1_000L
        val override = ManualOverride(ContextProfile.WORK, untilMillis = 1_010L)
        val waiter = launch { awaitManualOverrideExpiry(override) { wallClock } }

        runCurrent()
        advanceTimeBy(9L)
        runCurrent()
        assertFalse(waiter.isCompleted)

        wallClock = 1_010L
        advanceTimeBy(1L)
        runCurrent()
        assertTrue(waiter.isCompleted)
    }

    @Test fun waiterRechecksAfterDeadlineDelayIfWallClockHasNotReachedIt() = runTest {
        var wallClock = 2_000L
        val override = ManualOverride(ContextProfile.STUDY, untilMillis = 2_010L)
        val waiter = launch { awaitManualOverrideExpiry(override) { wallClock } }

        runCurrent()
        advanceTimeBy(10L)
        runCurrent()
        assertFalse(waiter.isCompleted)

        wallClock = 2_010L
        advanceTimeBy(10L)
        runCurrent()
        assertTrue(waiter.isCompleted)
    }

    @Test fun indefiniteOverrideDoesNotScheduleExpiryWait() = runTest {
        val waiter = launch {
            awaitManualOverrideExpiry(ManualOverride(ContextProfile.PERSONAL, Long.MAX_VALUE))
        }
        runCurrent()
        assertTrue(waiter.isCompleted)
    }
}
