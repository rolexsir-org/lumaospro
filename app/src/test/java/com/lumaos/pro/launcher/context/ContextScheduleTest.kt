package com.lumaos.pro.launcher.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The alarm must be armed for exactly the next transition: the first future
 * schedule edge whose far side resolves to a different profile. Edges that
 * change nothing (a window shadowed by a higher-priority schedule) must be
 * skipped, and with nothing scheduled no alarm may be armed at all.
 */
class ContextScheduleTest {

    // 2026-11-16 is a Monday; the Saturday below is 2026-11-14.
    private val monday = LocalDateTime.of(2026, 11, 16, 8, 0)
    private val defaults = ContextProfile.entries.associateWith { ProfileSchedule.defaults(it) }

    @Test fun `default windows produce the day's transitions in order`() {
        assertEquals(
            LocalDateTime.of(2026, 11, 16, 9, 0),
            ContextScheduleManager.nextBoundary(monday, defaults)
        )
        assertEquals(
            LocalDateTime.of(2026, 11, 16, 17, 0),
            ContextScheduleManager.nextBoundary(monday.withHour(10), defaults)
        )
        assertEquals(
            LocalDateTime.of(2026, 11, 16, 20, 0),
            ContextScheduleManager.nextBoundary(monday.withHour(18), defaults)
        )
        assertEquals(
            LocalDateTime.of(2026, 11, 16, 22, 0),
            ContextScheduleManager.nextBoundary(monday.withHour(21), defaults)
        )
    }

    @Test fun `overnight sleep end is the next morning's boundary`() {
        assertEquals(
            LocalDateTime.of(2026, 11, 17, 7, 0),
            ContextScheduleManager.nextBoundary(LocalDateTime.of(2026, 11, 17, 6, 0), defaults)
        )
    }

    @Test fun `weekday windows are found from the weekend`() {
        val weekdaysOnly = defaults +
            (ContextProfile.SLEEP to ProfileSchedule.defaults(ContextProfile.SLEEP).copy(enabled = false))
        assertEquals(
            LocalDateTime.of(2026, 11, 16, 9, 0),
            ContextScheduleManager.nextBoundary(LocalDateTime.of(2026, 11, 14, 10, 0), weekdaysOnly)
        )
    }

    @Test fun `shadowed edges are skipped instead of armed`() {
        val schedules = mapOf(
            ContextProfile.WORK to ProfileSchedule(
                true, LocalTime.of(9, 0), LocalTime.of(17, 0),
                setOf(DayOfWeek.MONDAY)
            ),
            // Wholly inside Work's window, and lower priority: neither its
            // start nor its end may produce an alarm.
            ContextProfile.STUDY to ProfileSchedule(
                true, LocalTime.of(10, 0), LocalTime.of(12, 0),
                setOf(DayOfWeek.MONDAY)
            ),
            ContextProfile.PERSONAL to ProfileSchedule.defaults(ContextProfile.PERSONAL),
            ContextProfile.SLEEP to ProfileSchedule.defaults(ContextProfile.SLEEP).copy(enabled = false)
        )
        assertEquals(
            LocalDateTime.of(2026, 11, 16, 17, 0),
            ContextScheduleManager.nextBoundary(LocalDateTime.of(2026, 11, 16, 9, 30), schedules)
        )
    }

    @Test fun `nothing scheduled means no boundary`() {
        val disabled = ContextProfile.entries.associateWith {
            ProfileSchedule.defaults(it).copy(enabled = false)
        }
        assertNull(ContextScheduleManager.nextBoundary(monday, disabled))
    }

    @Test fun `degenerate windows are ignored`() {
        val schedules = mapOf(
            // start == end never matches, so it must not arm an alarm either.
            ContextProfile.WORK to ProfileSchedule(true, LocalTime.of(9, 0), LocalTime.of(9, 0), setOf(DayOfWeek.MONDAY)),
            ContextProfile.STUDY to ProfileSchedule.defaults(ContextProfile.STUDY).copy(enabled = false),
            ContextProfile.PERSONAL to ProfileSchedule.defaults(ContextProfile.PERSONAL),
            ContextProfile.SLEEP to ProfileSchedule.defaults(ContextProfile.SLEEP).copy(enabled = false)
        )
        assertNull(ContextScheduleManager.nextBoundary(monday, schedules))
    }
}
