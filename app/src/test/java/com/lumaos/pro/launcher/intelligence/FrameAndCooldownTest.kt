package com.lumaos.pro.launcher.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameStatsAccumulatorTest {

    private val sixtyHzPeriodNs = 16_666_667L

    @Test
    fun `ignores a non positive first interval`() {
        val accumulator = FrameStatsAccumulator(sixtyHzPeriodNs)
        accumulator.onFrame(0L)
        accumulator.onFrame(-5L)
        assertEquals(0L, accumulator.snapshot().frameCount)
    }

    @Test
    fun `a smooth sequence registers no slow frames`() {
        val accumulator = FrameStatsAccumulator(sixtyHzPeriodNs)
        repeat(60) { accumulator.onFrame(sixtyHzPeriodNs) }
        val stats = accumulator.snapshot()
        assertEquals(60L, stats.frameCount)
        assertEquals(0L, stats.slowFrames)
        assertEquals(0L, stats.frozenFrames)
        assertEquals(0.0, stats.jankPercent, 0.0001)
        assertTrue(stats.hasSamples)
    }

    @Test
    fun `a frame longer than 1_5 refresh periods counts as slow`() {
        val accumulator = FrameStatsAccumulator(sixtyHzPeriodNs)
        accumulator.onFrame(sixtyHzPeriodNs)
        accumulator.onFrame(sixtyHzPeriodNs * 2)
        val stats = accumulator.snapshot()
        assertEquals(2L, stats.frameCount)
        assertEquals(1L, stats.slowFrames)
        assertEquals(0L, stats.frozenFrames)
    }

    @Test
    fun `a frame over 700ms counts as both slow and frozen`() {
        val accumulator = FrameStatsAccumulator(sixtyHzPeriodNs)
        accumulator.onFrame(750L * 1_000_000L)
        val stats = accumulator.snapshot()
        assertEquals(1L, stats.frozenFrames)
        assertEquals(1L, stats.slowFrames)
    }

    @Test
    fun `jank percentage and mean are computed over all frames`() {
        val accumulator = FrameStatsAccumulator(sixtyHzPeriodNs)
        repeat(9) { accumulator.onFrame(sixtyHzPeriodNs) }
        accumulator.onFrame(sixtyHzPeriodNs * 3)
        val stats = accumulator.snapshot()
        assertEquals(10L, stats.frameCount)
        assertEquals(10.0, stats.jankPercent, 0.0001)
        // (9 * 16.67 + 50) / 10 = 20 ms
        assertEquals(20.0, stats.meanFrameMs, 0.5)
        assertEquals(50.0, stats.longestFrameMs, 0.5)
    }

    @Test
    fun `reset clears counters but keeps the thresholds`() {
        val accumulator = FrameStatsAccumulator(sixtyHzPeriodNs)
        accumulator.onFrame(sixtyHzPeriodNs * 4)
        accumulator.reset()
        assertFalse(accumulator.snapshot().hasSamples)
        accumulator.onFrame(sixtyHzPeriodNs * 4)
        assertEquals(1L, accumulator.snapshot().slowFrames)
    }

    @Test
    fun `a zero refresh rate falls back to a 60Hz period`() {
        val accumulator = FrameStatsAccumulator(0f)
        accumulator.onFrame(16_666_667L)
        assertEquals(0L, accumulator.snapshot().slowFrames)
        assertEquals(16.67, accumulator.snapshot().refreshPeriodMs, 0.01)
    }

    @Test
    fun `thresholds track a high refresh display`() {
        // On a 120Hz panel a 20ms frame is slow; on 60Hz it would be fine.
        val accumulator = FrameStatsAccumulator(120f)
        accumulator.onFrame(20L * 1_000_000L)
        assertEquals(1L, accumulator.snapshot().slowFrames)
    }
}

class CooldownTest {

    @Test
    fun `never having run means ready`() {
        assertTrue(Cooldown.state(null, 1000L, 5000L).canRun)
    }

    @Test
    fun `an elapsed interval means ready`() {
        assertTrue(Cooldown.state(1000L, 500L, 2000L).canRun)
    }

    @Test
    fun `an unexpired interval reports the remainder`() {
        val state = Cooldown.state(1000L, 5000L, 3000L)
        assertFalse(state.canRun)
        assertEquals(3000L, state.remainingMillis)
    }

    @Test
    fun `a zero or negative interval disables the cooldown`() {
        assertTrue(Cooldown.state(0L, 0L, 10L).canRun)
        assertTrue(Cooldown.state(0L, -1L, 10L).canRun)
    }

    @Test
    fun `a clock moving backwards does not lock an action out`() {
        // NTP corrections and user-adjusted clocks must not disable maintenance
        // forever.
        assertTrue(Cooldown.state(10_000L, 60_000L, 5_000L).canRun)
    }

    @Test
    fun `the boundary is inclusive of the exact interval`() {
        assertFalse(Cooldown.state(0L, 1000L, 999L).canRun)
        assertTrue(Cooldown.state(0L, 1000L, 1000L).canRun)
    }

    @Test
    fun `remaining time formats legibly`() {
        assertEquals("0s", formatCooldown(0L))
        assertEquals("5s", formatCooldown(4_500L))
        assertEquals("2m 29s", formatCooldown(149_000L))
        assertEquals("1h 5m", formatCooldown(3_900_000L))
    }
}
