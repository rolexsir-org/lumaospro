package com.rolexm.launcher.diagnostics

/**
 * A value shown on the diagnostics dashboard, tagged with how it was obtained.
 *
 * ROLEXM's core promise is that it does not invent numbers. Every reading is
 * therefore one of three things, and the UI must render the distinction:
 *
 *  - [Measured] - read from a real API, with the source named so it can be
 *    checked.
 *  - [Estimated] - derived or extrapolated, with the basis stated.
 *  - [Unavailable] - the platform or permission does not expose it, with the
 *    reason stated. This is a first-class result, not an error to be hidden.
 */
sealed class Reading<out T> {

    data class Measured<T>(
        val value: T,
        /** Human-readable API the value came from, e.g. "ActivityManager.getProcessMemoryInfo". */
        val source: String,
        val takenAtMillis: Long
    ) : Reading<T>()

    data class Estimated<T>(
        val value: T,
        /** How the estimate was derived, e.g. "interpolated from 3 samples". */
        val basis: String,
        val takenAtMillis: Long
    ) : Reading<T>()

    data class Unavailable(val reason: String) : Reading<Nothing>()

    /** The value if there is one, otherwise null. */
    fun valueOrNull(): T? = when (this) {
        is Measured -> value
        is Estimated -> value
        is Unavailable -> null
    }

    fun isMeasured(): Boolean = this is Measured

    companion object {
        fun <T> measured(value: T, source: String, atMillis: Long = System.currentTimeMillis()) =
            Measured(value, source, atMillis)

        fun <T> estimated(value: T, basis: String, atMillis: Long = System.currentTimeMillis()) =
            Estimated(value, basis, atMillis)

        fun unavailable(reason: String): Reading<Nothing> = Unavailable(reason)
    }
}

/** Convenience for "this exists but we could not read it right now". */
fun <T> readingOf(value: T?, source: String, unavailableReason: String, now: Long = System.currentTimeMillis()): Reading<T> =
    if (value == null) Reading.unavailable(unavailableReason) else Reading.measured(value, source, now)
