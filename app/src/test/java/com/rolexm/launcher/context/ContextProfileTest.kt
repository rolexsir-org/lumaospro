package com.rolexm.launcher.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ContextProfileTest {
    @Test fun defaultScheduleWindowsResolveAtBoundaries() {
        val schedules = ContextProfile.entries.associateWith { ProfileSchedule.defaults(it) }
        val monday = DayOfWeek.MONDAY
        assertEquals(ContextProfile.PERSONAL, ContextProfileResolver.resolve(schedules, LocalTime.of(8, 59), monday))
        assertEquals(ContextProfile.WORK, ContextProfileResolver.resolve(schedules, LocalTime.of(9, 0), monday))
        assertEquals(ContextProfile.STUDY, ContextProfileResolver.resolve(schedules, LocalTime.of(17, 0), monday))
        assertEquals(ContextProfile.PERSONAL, ContextProfileResolver.resolve(schedules, LocalTime.of(20, 0), monday))
        assertEquals(ContextProfile.SLEEP, ContextProfileResolver.resolve(schedules, LocalTime.of(23, 0), monday))
    }

    @Test fun overnightScheduleUsesPreviousDayForAfterMidnightSegment() {
        val schedule = ProfileSchedule(
            enabled = true,
            start = LocalTime.of(22, 0),
            end = LocalTime.of(7, 0),
            days = setOf(DayOfWeek.MONDAY)
        )
        assertTrue(schedule.contains(LocalTime.of(23, 30), DayOfWeek.MONDAY))
        assertTrue(schedule.contains(LocalTime.of(6, 59), DayOfWeek.TUESDAY))
        assertFalse(schedule.contains(LocalTime.of(7, 0), DayOfWeek.TUESDAY))
        assertFalse(schedule.contains(LocalTime.NOON, DayOfWeek.TUESDAY))
    }

    @Test fun sleepWinsWhenWindowsOverlap() {
        val schedules = mapOf(
            ContextProfile.WORK to ProfileSchedule(true, LocalTime.of(21, 0), LocalTime.of(23, 0), setOf(DayOfWeek.FRIDAY)),
            ContextProfile.SLEEP to ProfileSchedule(true, LocalTime.of(22, 0), LocalTime.of(8, 0), setOf(DayOfWeek.FRIDAY)),
            ContextProfile.PERSONAL to ProfileSchedule.defaults(ContextProfile.PERSONAL),
            ContextProfile.STUDY to ProfileSchedule.defaults(ContextProfile.STUDY)
        )
        assertEquals(ContextProfile.SLEEP, ContextProfileResolver.resolve(schedules, LocalTime.of(22, 30), DayOfWeek.FRIDAY))
    }

    @Test fun scheduleEncodingRoundTripsAndCorruptionFallsBack() {
        val schedule = ProfileSchedule.defaults(ContextProfile.SLEEP).copy(enabled = false)
        assertEquals(schedule, ProfileSchedule.decode(ProfileSchedule.encode(schedule), ProfileSchedule.defaults(ContextProfile.WORK)))
        assertEquals(ProfileSchedule.defaults(ContextProfile.WORK), ProfileSchedule.decode("broken", ProfileSchedule.defaults(ContextProfile.WORK)))
    }
}
