package com.rolexm.launcher.intelligence

/**
 * Frame-timing accumulation.
 *
 * Definitions follow the Android vitals convention so the numbers mean what a
 * reader expects:
 *  - slow frame: took longer than 1.5 refresh periods
 *  - frozen frame: took longer than 700 ms (perceptibly stalled)
 *
 * The accumulator is pure arithmetic with no Android dependency; [FrameMonitor]
 * is the thin Choreographer wrapper that feeds it.
 */
class FrameStatsAccumulator(private val refreshPeriodNs: Long) {

    constructor(refreshRateHz: Float) : this(
        if (refreshRateHz > 0f) (1_000_000_000L / refreshRateHz).toLong() else 16_666_667L
    )

    private val slowThresholdNs = (refreshPeriodNs * 1.5).toLong()
    private val frozenThresholdNs = 700L * 1_000_000L

    var frameCount: Long = 0L
        private set
    var slowFrames: Long = 0L
        private set
    var frozenFrames: Long = 0L
        private set
    var longestFrameNs: Long = 0L
        private set
    private var totalNs: Long = 0L

    fun onFrame(intervalNs: Long) {
        // The first callback has no predecessor; ignore it rather than
        // recording a bogus multi-second "frame".
        if (intervalNs <= 0L) return
        frameCount++
        totalNs += intervalNs
        if (intervalNs > longestFrameNs) longestFrameNs = intervalNs
        if (intervalNs >= frozenThresholdNs) frozenFrames++
        if (intervalNs >= slowThresholdNs) slowFrames++
    }

    fun snapshot(): FrameStats {
        val meanNs = if (frameCount == 0L) 0L else totalNs / frameCount
        val jankPercent = if (frameCount == 0L) 0.0 else slowFrames * 100.0 / frameCount
        return FrameStats(
            frameCount = frameCount,
            slowFrames = slowFrames,
            frozenFrames = frozenFrames,
            meanFrameMs = meanNs / 1_000_000.0,
            longestFrameMs = longestFrameNs / 1_000_000.0,
            jankPercent = jankPercent,
            refreshPeriodMs = refreshPeriodNs / 1_000_000.0
        )
    }

    fun reset() {
        frameCount = 0L
        slowFrames = 0L
        frozenFrames = 0L
        longestFrameNs = 0L
        totalNs = 0L
    }
}

data class FrameStats(
    val frameCount: Long,
    val slowFrames: Long,
    val frozenFrames: Long,
    val meanFrameMs: Double,
    val longestFrameMs: Double,
    val jankPercent: Double,
    val refreshPeriodMs: Double
) {
    val hasSamples: Boolean get() = frameCount > 0

    companion object {
        val EMPTY = FrameStats(0, 0, 0, 0.0, 0.0, 0.0, 16.67)
    }
}
