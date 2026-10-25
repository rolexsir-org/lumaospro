package com.lumaos.pro.launcher.intelligence

/**
 * Rate limiting for automatic maintenance.
 *
 * The point is battery: a "cleaner" that re-runs every few seconds burns more
 * energy than it can ever save. Every automatic action passes through here.
 */
object Cooldown {

    data class State(val canRun: Boolean, val remainingMillis: Long) {
        companion object {
            val READY = State(true, 0L)
        }
    }

    fun state(lastRunAtMillis: Long?, intervalMillis: Long, nowMillis: Long): State {
        if (intervalMillis <= 0L) return State.READY
        if (lastRunAtMillis == null) return State.READY
        val elapsed = nowMillis - lastRunAtMillis
        // Clock changes (NTP correction, user adjustment) must not lock an
        // action out forever, so a negative elapsed time is treated as ready.
        if (elapsed < 0L) return State.READY
        val remaining = intervalMillis - elapsed
        return if (remaining <= 0L) State.READY else State(false, remaining)
    }
}

/** Formats a cooldown remainder compactly, e.g. "12m 30s". */
fun formatCooldown(millis: Long): String {
    if (millis <= 0L) return "0s"
    val totalSeconds = (millis + 999L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
