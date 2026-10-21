package com.rolexm.launcher.context

import java.time.DayOfWeek
import java.time.LocalTime

/** The launcher context is separate from ROLEXM's device-performance profile. */
enum class ContextProfile(val title: String) {
    WORK("Work"), STUDY("Study"), PERSONAL("Personal"), SLEEP("Sleep");

    companion object {
        fun parse(value: String?): ContextProfile? = entries.firstOrNull { it.name == value }
    }
}

/** A local-time window. End times earlier than start times cross midnight. */
data class ProfileSchedule(
    val enabled: Boolean,
    val start: LocalTime,
    val end: LocalTime,
    /** ISO-8601 days: Monday=1 through Sunday=7. */
    val days: Set<DayOfWeek>
) {
    fun contains(time: LocalTime, day: DayOfWeek): Boolean {
        if (!enabled || start == end) return false
        return if (end > start) day in days && time >= start && time < end
        else if (time >= start) day in days
        else time < end && day.minus(1) in days
    }

    companion object {
        fun defaults(profile: ContextProfile): ProfileSchedule = when (profile) {
            ContextProfile.WORK -> ProfileSchedule(true, LocalTime.of(9, 0), LocalTime.of(17, 0),
                setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
            ContextProfile.STUDY -> ProfileSchedule(true, LocalTime.of(17, 0), LocalTime.of(20, 0),
                setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
            ContextProfile.PERSONAL -> ProfileSchedule(false, LocalTime.of(8, 0), LocalTime.of(22, 0),
                DayOfWeek.values().toSet())
            ContextProfile.SLEEP -> ProfileSchedule(true, LocalTime.of(22, 0), LocalTime.of(7, 0),
                DayOfWeek.values().toSet())
        }

        fun encode(schedule: ProfileSchedule): String = listOf(
            if (schedule.enabled) "1" else "0",
            schedule.start.toString(),
            schedule.end.toString(),
            schedule.days.map { it.value }.sorted().joinToString("")
        ).joinToString("|")

        fun decode(raw: String?, fallback: ProfileSchedule): ProfileSchedule {
            if (raw == null) return fallback
            val parts = raw.split('|')
            if (parts.size != 4) return fallback
            val start = runCatching { LocalTime.parse(parts[1]) }.getOrNull() ?: return fallback
            val end = runCatching { LocalTime.parse(parts[2]) }.getOrNull() ?: return fallback
            val days = parts[3].mapNotNull { it.digitToIntOrNull()?.let { n -> DayOfWeek.values().firstOrNull { it.value == n } } }.toSet()
            return ProfileSchedule(parts[0] == "1", start, end, days)
        }
    }
}

/** Deterministic tie-break for overlapping windows: Sleep > Work > Study > Personal. */
object ContextProfileResolver {
    private val priority = listOf(ContextProfile.SLEEP, ContextProfile.WORK, ContextProfile.STUDY, ContextProfile.PERSONAL)

    fun resolve(schedules: Map<ContextProfile, ProfileSchedule>, time: LocalTime, day: DayOfWeek): ContextProfile =
        priority.firstOrNull { schedules[it]?.contains(time, day) == true } ?: ContextProfile.PERSONAL
}
