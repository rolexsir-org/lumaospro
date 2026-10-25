package com.lumaos.pro.launcher.diagnostics

import com.lumaos.pro.launcher.intelligence.Rules
import com.lumaos.pro.launcher.intelligence.Profile
import com.lumaos.pro.launcher.intelligence.ProfileId
import com.lumaos.pro.launcher.intelligence.Profiles
import com.lumaos.pro.launcher.optimizer.Optimizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingTest {

    @Test
    fun `measured keeps its value and source`() {
        val reading = Reading.measured(42, "SomeApi")
        assertEquals(42, reading.valueOrNull())
        assertTrue(reading.isMeasured())
        assertEquals("SomeApi", (reading as Reading.Measured).source)
    }

    @Test
    fun `estimated is explicitly not measured`() {
        val reading = Reading.estimated(42, "interpolated")
        assertEquals(42, reading.valueOrNull())
        assertFalse(reading.isMeasured())
    }

    @Test
    fun `unavailable carries a reason and no value`() {
        val reading: Reading<Int> = Reading.unavailable("no permission")
        assertNull(reading.valueOrNull())
        assertEquals("no permission", (reading as Reading.Unavailable).reason)
    }

    @Test
    fun `readingOf maps a missing value to unavailable`() {
        assertTrue(readingOf(null, "src", "reason") is Reading.Unavailable)
        assertTrue(readingOf(1, "src", "reason") is Reading.Measured)
    }
}

class StartupStatsTest {

    @Test
    fun `no samples yields nothing`() {
        assertNull(StartupStats.from(emptyList()))
    }

    @Test
    fun `a single sample is its own median and best`() {
        val stats = StartupStats.from(listOf(1000L to 400L))!!
        assertEquals(1, stats.sampleCount)
        assertEquals(400L, stats.medianMs)
        assertEquals(400L, stats.bestMs)
        assertEquals(400L, stats.latestMs)
    }

    @Test
    fun `median of an even count is the mean of the middle two`() {
        // Samples arrive newest-first from the store and are sorted internally.
        val stats = StartupStats.from(
            listOf(4L to 500L, 3L to 300L, 2L to 200L, 1L to 100L)
        )!!
        assertEquals(250L, stats.medianMs)
        assertEquals(100L, stats.bestMs)
        assertEquals(4, stats.sampleCount)
    }

    @Test
    fun `median of an odd count is the middle value`() {
        val stats = StartupStats.from(listOf(300L, 100L, 200L).mapIndexed { i, v -> i.toLong() to v })!!
        assertEquals(200L, stats.medianMs)
    }

    @Test
    fun `latest is the most recent sample, not the largest`() {
        // Newest sample is slower; latest must track recency, best the minimum.
        val stats = StartupStats.from(listOf(1L to 100L, 2L to 900L))!!
        assertEquals(900L, stats.latestMs)
        assertEquals(100L, stats.bestMs)
    }
}

class FormattingTest {

    @Test
    fun `byte formatting switches units`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.0 MB", formatBytes(1024 * 1024))
        assertEquals("2.00 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `a negative size is not rendered as a huge positive number`() {
        assertEquals("-", formatBytes(-1))
    }

    @Test
    fun `a missing pss renders as a dash`() {
        assertEquals("—", formatPss(null))
        assertEquals("1.0 MB", formatPss(1024))
    }
}

/**
 * The rules decide whether LumaOS Pro acts on its own. Getting them wrong in
 * either direction is bad: too eager wastes battery, too timid makes the
 * intelligence engine decorative.
 */
class RulesTest {

    private fun context(
        profile: Profile = Profiles.BALANCED,
        memory: com.lumaos.pro.launcher.intelligence.ProcessMemory? = null,
        frames: com.lumaos.pro.launcher.intelligence.FrameStats = com.lumaos.pro.launcher.intelligence.FrameStats.EMPTY,
        icons: Int = 0,
        cacheBytes: Long = 0L,
        autoProfile: Boolean = true
    ) = Rules.Context(profile, memory, frames, icons, cacheBytes, autoProfile)

    private fun memory(lowMemory: Boolean) = com.lumaos.pro.launcher.intelligence.ProcessMemory(
        totalPssKb = 40_000, dalvikPssKb = 30_000, nativePssKb = 8_000,
        systemAvailableMb = 200, systemLowMemory = lowMemory
    )

    @Test
    fun `icons are not trimmed unless the system is short of memory`() {
        assertNull(Rules.iconCacheShouldTrim(context(icons = 100, memory = memory(lowMemory = false))))
        assertNotNull(Rules.iconCacheShouldTrim(context(icons = 100, memory = memory(lowMemory = true))))
    }

    @Test
    fun `a small icon cache is left alone even under pressure`() {
        assertNull(
            Rules.iconCacheShouldTrim(
                context(icons = Rules.MIN_ICONS_TO_TRIM - 1, memory = memory(lowMemory = true))
            )
        )
    }

    @Test
    fun `the cache is only cleared once it has actually grown`() {
        assertNull(Rules.internalCacheShouldClear(context(cacheBytes = 1024)))
        assertNotNull(
            Rules.internalCacheShouldClear(
                context(cacheBytes = Optimizer.INTERNAL_CACHE_TRIM_THRESHOLD_BYTES + 1)
            )
        )
    }

    @Test
    fun `profile adaptation respects the sample floor`() {
        val janky = com.lumaos.pro.launcher.intelligence.FrameStats(
            frameCount = Rules.MIN_FRAME_SAMPLES - 1, slowFrames = 100, frozenFrames = 0,
            meanFrameMs = 40.0, longestFrameMs = 100.0, jankPercent = 80.0, refreshPeriodMs = 16.67
        )
        assertNull(Rules.shouldAdaptProfile(context(frames = janky)))
    }

    @Test
    fun `sustained jank above the threshold triggers adaptation`() {
        val janky = com.lumaos.pro.launcher.intelligence.FrameStats(
            frameCount = 500, slowFrames = 200, frozenFrames = 0,
            meanFrameMs = 40.0, longestFrameMs = 100.0, jankPercent = 40.0, refreshPeriodMs = 16.67
        )
        assertNotNull(Rules.shouldAdaptProfile(context(frames = janky, profile = Profiles.BALANCED)))
    }

    @Test
    fun `smooth frames never trigger adaptation`() {
        val smooth = com.lumaos.pro.launcher.intelligence.FrameStats(
            frameCount = 500, slowFrames = 2, frozenFrames = 0,
            meanFrameMs = 16.7, longestFrameMs = 20.0, jankPercent = 0.4, refreshPeriodMs = 16.67
        )
        assertNull(Rules.shouldAdaptProfile(context(frames = smooth, profile = Profiles.VISUAL_QUALITY)))
    }

    @Test
    fun `an explicitly chosen profile is never overridden automatically`() {
        val janky = com.lumaos.pro.launcher.intelligence.FrameStats(
            frameCount = 500, slowFrames = 200, frozenFrames = 0,
            meanFrameMs = 40.0, longestFrameMs = 100.0, jankPercent = 40.0, refreshPeriodMs = 16.67
        )
        assertNull(Rules.shouldAdaptProfile(context(frames = janky, autoProfile = false)))
    }

    @Test
    fun `there is nothing to step down to below lightweight`() {
        val janky = com.lumaos.pro.launcher.intelligence.FrameStats(
            frameCount = 500, slowFrames = 200, frozenFrames = 0,
            meanFrameMs = 40.0, longestFrameMs = 100.0, jankPercent = 40.0, refreshPeriodMs = 16.67
        )
        assertNull(Rules.shouldAdaptProfile(context(frames = janky, profile = Profile(ProfileId.LIGHTWEIGHT, 0f, false, 48, false, 60_000, false, false, false, 1000, 25.0))))
    }
}
