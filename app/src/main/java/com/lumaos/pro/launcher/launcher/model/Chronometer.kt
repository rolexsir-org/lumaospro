package com.lumaos.pro.launcher.launcher.model

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Live values shown by the chronometer and readout line; every field comes from a platform API. */
data class ReadoutSnapshot(
    /** From the sticky ACTION_BATTERY_CHANGED broadcast; null if unavailable. */
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    /** AlarmManager.getNextAlarmClock().triggerTime; null when no alarm is set. */
    val nextAlarmAtMillis: Long? = null,
    /** NotificationManager interruption filter is not "all". */
    val focusOn: Boolean = false
)

/**
 * Pure maths and formatting for the chronometer dial and the readout line,
 * kept free of Android so it is unit tested.
 */
object Chronometer {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Fraction of the local day elapsed at [nowMillis], 0..1. */
    fun dayFraction(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Float {
        val c = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        val minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return minutes / (24f * 60f)
    }

    /**
     * Position of the next alarm on the 24-hour ring (0..1 from midnight), or
     * null when there is no alarm within the next 24 hours.
     */
    fun alarmFraction(nowMillis: Long, alarmAtMillis: Long?, zone: TimeZone = TimeZone.getDefault()): Float? {
        if (alarmAtMillis == null || alarmAtMillis < nowMillis || alarmAtMillis - nowMillis > DAY_MS) return null
        return dayFraction(alarmAtMillis, zone)
    }

    /** Milliseconds until the next minute boundary (for aligned dial updates). */
    fun millisToNextMinute(nowMillis: Long): Long = 60_000L - (nowMillis % 60_000L)

    fun formatTime(millis: Long, use24h: Boolean, zone: TimeZone = TimeZone.getDefault()): String {
        val c = Calendar.getInstance(zone).apply { timeInMillis = millis }
        val h = c.get(Calendar.HOUR_OF_DAY)
        val m = c.get(Calendar.MINUTE)
        return if (use24h) "%02d:%02d".format(h, m)
        else "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
    }

    /**
     * The readout line under the clock, e.g. "Personal · Alarm 7:30 AM · 82%".
     * Items without data are omitted rather than shown as placeholders.
     */
    fun readoutLine(
        snapshot: ReadoutSnapshot,
        contextTitle: String?,
        nowMillis: Long,
        use24h: Boolean,
        caps: Boolean,
        zone: TimeZone = TimeZone.getDefault()
    ): String {
        val parts = ArrayList<String>(4)
        contextTitle?.takeIf { it.isNotBlank() }?.let { parts += it }
        snapshot.nextAlarmAtMillis?.takeIf { it >= nowMillis && it - nowMillis <= DAY_MS }?.let {
            parts += "Alarm ${formatTime(it, use24h, zone)}"
        }
        snapshot.batteryPercent?.let { p -> parts += if (snapshot.charging) "$p% charging" else "$p%" }
        if (snapshot.focusOn) parts += "Focus"
        val line = parts.joinToString(SEPARATOR)
        return if (caps) line.uppercase(Locale.getDefault()) else line
    }

    /** Spoken summary for the dial (TalkBack). */
    fun describeDial(dayFraction: Float, snapshot: ReadoutSnapshot): String {
        val parts = ArrayList<String>(3)
        parts += "Day ${(dayFraction * 100).toInt()} percent complete"
        snapshot.batteryPercent?.let { parts += "battery $it percent" + if (snapshot.charging) ", charging" else "" }
        if (snapshot.nextAlarmAtMillis != null) parts += "alarm set"
        return parts.joinToString(", ")
    }

    const val SEPARATOR = "  ·  "
}
