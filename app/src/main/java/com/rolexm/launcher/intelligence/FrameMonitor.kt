package com.rolexm.launcher.intelligence

import android.view.Choreographer
import com.rolexm.launcher.core.util.RolexLog

/**
 * Frame-timing instrumentation built on Choreographer.
 *
 * This is real measurement, not a heuristic: every vsync callback yields the
 * interval since the previous one, from which slow and frozen frames are
 * counted. It is also the reason instrumentation is switchable - installing a
 * per-frame callback has a cost, so the Lightweight profile leaves it off
 * rather than taxing the very devices it exists to help.
 *
 * Callers must run on the main thread (Choreographer requirement).
 */
class FrameMonitor {

    private val choreographer: Choreographer? = try {
        Choreographer.getInstance()
    } catch (e: RuntimeException) {
        RolexLog.w("Choreographer unavailable; frame metrics disabled")
        null
    }

    private var accumulator: FrameStatsAccumulator? = null
    private var lastFrameTimeNs = 0L

    @Volatile
    private var running = false

    private val callback = Choreographer.FrameCallback { frameTimeNs ->
        val previous = lastFrameTimeNs
        lastFrameTimeNs = frameTimeNs
        // The first callback has no predecessor; skipping it avoids recording a
        // bogus multi-second first frame.
        if (previous > 0L) {
            accumulator?.onFrame(frameTimeNs - previous)
        }
        if (running) {
            postNext()
        }
    }

    fun isRunning(): Boolean = running

    /**
     * Begins instrumentation, discarding any previous samples.
     *
     * @param refreshRateHz display refresh rate; used to derive the expected
     *                      frame period so "slow" is relative to the actual
     *                      panel rather than an assumed 60 Hz.
     */
    fun start(refreshRateHz: Float) {
        if (choreographer == null) return
        if (running) return
        accumulator = FrameStatsAccumulator(refreshRateHz)
        lastFrameTimeNs = 0L
        running = true
        postNext()
    }

    /** Stops instrumentation. Keeps the accumulated stats for reading. */
    fun stop() {
        running = false
        if (choreographer != null) {
            try {
                choreographer.removeFrameCallback(callback)
            } catch (e: RuntimeException) {
                RolexLog.w("Failed to remove frame callback")
            }
        }
        lastFrameTimeNs = 0L
    }

    /** Drops all samples without stopping. */
    fun reset() {
        lastFrameTimeNs = 0L
        accumulator?.reset()
    }

    fun stats(): FrameStats = accumulator?.snapshot() ?: FrameStats.EMPTY

    private fun postNext() {
        try {
            choreographer?.postFrameCallback(callback)
        } catch (e: RuntimeException) {
            running = false
            RolexLog.w("Failed to post frame callback; frame metrics disabled")
        }
    }
}
