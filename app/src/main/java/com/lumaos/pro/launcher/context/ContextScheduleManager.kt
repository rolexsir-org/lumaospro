package com.lumaos.pro.launcher.context

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lumaos.pro.launcher.core.prefs.LumaPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/** Uses one inexact alarm for the next profile boundary; no polling or service. */
object ContextScheduleManager {
    const val ACTION_REEVALUATE = "com.lumaos.pro.launcher.action.REEVALUATE_CONTEXT"
    private const val REQUEST_CODE = 7614

    suspend fun refresh(context: Context, prefs: LumaPrefs) {
        val now = LocalDateTime.now()
        val schedules = prefs.profileSchedules.first()
        val nowMillis = System.currentTimeMillis()
        val override = prefs.manualOverride.first()
        if (override != null && override.isActive(nowMillis)) {
            // A manual choice stands until the next scheduled transition. Its
            // deadline stays live: enabling a schedule while overridden must
            // not strand the launcher on the manual choice forever.
            val previous = prefs.activeContextProfile.first()
            if (previous != override.profile) {
                prefs.setActiveContextProfile(override.profile)
            }
            val next = nextBoundary(now, schedules)
            val until = next?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: Long.MAX_VALUE
            if (until != override.untilMillis) {
                prefs.setManualOverride(override.profile, until)
            }
            scheduleNext(context, next)
            return
        }
        if (override != null) {
            prefs.clearManualOverride()
        }
        val active = ContextProfileResolver.resolve(schedules, now.toLocalTime(), now.dayOfWeek)
        val previous = prefs.activeContextProfile.first()
        if (previous != active) {
            prefs.setActiveContextProfile(active)
        }
        scheduleNext(context, nextBoundary(now, schedules))
    }

    private fun scheduleNext(context: Context, next: LocalDateTime?) {
        val intent = Intent(context, ContextScheduleReceiver::class.java).setAction(ACTION_REEVALUATE)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (next == null) {
            alarm.cancel(pending)
            pending.cancel()
            return
        }
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact-and-idle batches with other system work and avoids frequent wakeups.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
    }

    /**
     * The next wall-clock boundary at which the resolved profile changes, or
     * null when no schedule is enabled. Shared with [ContextEngine] so manual
     * overrides expire at exactly the transition the alarm is armed for.
     */
    internal fun nextBoundary(
        now: LocalDateTime,
        schedules: Map<ContextProfile, ProfileSchedule>
    ): LocalDateTime? {
        val current = ContextProfileResolver.resolve(schedules, now.toLocalTime(), now.dayOfWeek)
        val candidates = buildList {
            for (offset in 0..8) {
                val date = now.toLocalDate().plusDays(offset.toLong())
                schedules.values.filter { it.enabled && date.dayOfWeek in it.days && it.start != it.end }
                    .forEach { schedule ->
                        add(date.atTime(schedule.start))
                        // Overnight end belongs to the following calendar day.
                        val endDate = if (schedule.end < schedule.start) date.plusDays(1) else date
                        add(endDate.atTime(schedule.end))
                    }
            }
        }.filter { it.isAfter(now) }.sorted()
        return candidates.firstOrNull { boundary ->
            val after = boundary.plusSeconds(1)
            ContextProfileResolver.resolve(schedules, after.toLocalTime(), after.dayOfWeek) != current
        } ?: candidates.firstOrNull()
    }
}

/** Alarm and wall-clock receiver; it does no work beyond a quick DataStore read. */
class ContextScheduleReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = refreshAsync(context, goAsync())
}

/** Rebuilds the alarm after boot or a wall-clock / timezone adjustment. */
class ContextBootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = refreshAsync(context, goAsync())
}

private fun refreshAsync(context: Context, pending: android.content.BroadcastReceiver.PendingResult) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            ContextScheduleManager.refresh(context.applicationContext, LumaPrefs.create(context))
        } finally {
            pending.finish()
        }
    }
}
