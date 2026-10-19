package com.rolexm.launcher.intelligence

import android.os.SystemClock
import com.rolexm.launcher.diagnostics.Reading

/**
 * Measures how long ROLEXM takes to show its home screen.
 *
 * Honest scope: this measures "ROLEXM's own initialisation to first drawn
 * frame", timed with SystemClock.uptimeMillis(). It is *not* the full Android
 * cold-start time - that begins when the zygote forks the process, before any
 * application code can run, and there is no public API to read that instant on
 * supported API levels. The dashboard labels it accordingly rather than
 * presenting it as a cold-start figure.
 */
object StartupTracker {

    /**
     * Stated on the dashboard next to the number. This measures ROLEXM's own
     * initialisation, not the process cold start, which begins before any
     * application code can run and is not readable through a public API.
     */
    const val SOURCE =
        "SystemClock.uptimeMillis(): Application.onCreate -> first populated home frame"

    @Volatile
    private var initUptimeMillis: Long = -1L

    @Volatile
    private var lastDurationMillis: Long? = null

    /** Called as the first statement of Application.onCreate. */
    fun markProcessInit(uptimeMillis: Long = SystemClock.uptimeMillis()) {
        if (initUptimeMillis < 0L) initUptimeMillis = uptimeMillis
    }

    /**
     * Called once the home screen's first frame has been drawn. Returns the
     * measured duration, or null if [markProcessInit] was never called (for
     * example the activity was restored into an existing process).
     */
    fun markFirstFrame(uptimeMillis: Long = SystemClock.uptimeMillis()): Long? {
        val start = initUptimeMillis
        if (start < 0L) return null
        val duration = uptimeMillis - start
        if (duration < 0L) return null
        lastDurationMillis = duration
        return duration
    }

    fun lastDurationMillis(): Long? = lastDurationMillis

    fun reading(): Reading<Long> {
        val value = lastDurationMillis
            ?: return Reading.unavailable(
                "No completed start since the process was created. " +
                    "Open the home screen once to record a measurement."
            )
        return Reading.measured(value, SOURCE)
    }
}
