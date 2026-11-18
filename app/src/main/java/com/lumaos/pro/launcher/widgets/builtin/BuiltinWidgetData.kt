package com.lumaos.pro.launcher.widgets.builtin

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.CalendarContract.Instances
import android.text.format.DateUtils
import android.text.format.Formatter
import com.lumaos.pro.launcher.R
import java.time.LocalDate
import java.time.ZoneId

internal data class WidgetReading(val value: String, val detail: String = "", val used: Float? = null, val days: List<Long?> = emptyList(), val weatherCode: Int? = null)

internal object BuiltinWidgetData {
    fun calendarAllowed(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun usageAllowed(context: Context): Boolean = context.getSystemService(AppOpsManager::class.java)
        ?.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    fun calendar(context: Context): WidgetReading {
        if (!calendarAllowed(context)) return WidgetReading(context.getString(R.string.widget_calendar_permission))
        val now = System.currentTimeMillis()
        val range = Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, now)
            ContentUris.appendId(this, now + 7 * DateUtils.DAY_IN_MILLIS)
        }.build()
        context.contentResolver.query(range,
            arrayOf(Instances.TITLE, Instances.BEGIN, Instances.ALL_DAY),
            "${Instances.VISIBLE} = 1 AND ${Instances.STATUS} != ?",
            arrayOf(android.provider.CalendarContract.Events.STATUS_CANCELED.toString()),
            "${Instances.BEGIN} ASC"
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return WidgetReading(context.getString(R.string.widget_no_events))
            val allDay = cursor.getInt(2) != 0
            val start = cursor.getLong(1)
            val flags = if (allDay) DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_UTC
                else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
            return WidgetReading(cursor.getString(0)?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.widget_untitled_event), DateUtils.formatDateTime(context, start, flags))
        }
        return WidgetReading(context.getString(R.string.widget_data_unavailable))
    }

    fun storage(context: Context): WidgetReading {
        // App-private data and user media normally share this internal volume.
        // StatFs needs no broad file/media access permission.
        val volume = StatFs(Environment.getDataDirectory().absolutePath)
        return WidgetReading(context.getString(R.string.widget_storage_free, Formatter.formatFileSize(context, volume.availableBytes)),
            context.getString(R.string.widget_storage_total, Formatter.formatFileSize(context, volume.totalBytes)),
            used = 1f - volume.availableBytes.toFloat() / volume.totalBytes.coerceAtLeast(1L))
    }

    /** Interactive (screen-on) time since local midnight, not launcher-open time. */
    fun usage(context: Context): WidgetReading {
        if (!usageAllowed(context)) return WidgetReading(context.getString(R.string.widget_usage_permission))
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        if (android.os.Build.VERSION.SDK_INT < 28) {
            val stats = context.getSystemService(UsageStatsManager::class.java)
                ?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
                ?: return WidgetReading(context.getString(R.string.widget_data_unavailable))
            if (stats.isEmpty()) return WidgetReading(context.getString(R.string.widget_data_unavailable))
            return WidgetReading(duration(context, stats.sumOf { it.totalTimeInForeground }),
                context.getString(R.string.widget_usage_app_today))
        }
        val boundaries = (6 downTo 0).map { today.minusDays(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() }
        val events = context.getSystemService(UsageStatsManager::class.java)
            ?.queryEvents(boundaries.first() - DateUtils.DAY_IN_MILLIS, now)
            ?: return WidgetReading(context.getString(R.string.widget_data_unavailable))
        val event = UsageEvents.Event()
        val intervals = boundaries.mapIndexed { i, midnight -> ScreenTime(midnight, boundaries.getOrElse(i + 1) { now }) }
        while (events.hasNextEvent() && events.getNextEvent(event)) {
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> intervals.forEach { it.record(event.timeStamp, true) }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> intervals.forEach { it.record(event.timeStamp, false) }
            }
        }
        val days = intervals.map { it.total() }
        val milliseconds = days.last() ?: return WidgetReading(context.getString(R.string.widget_data_unavailable))
        return WidgetReading(duration(context, milliseconds), context.getString(R.string.widget_usage_today), days = days)
    }

    private fun duration(context: Context, milliseconds: Long): String {
        val minutes = milliseconds / DateUtils.MINUTE_IN_MILLIS
        return context.getString(R.string.widget_usage_duration, minutes / 60, minutes % 60)
    }
}

/** Clips sessions crossing midnight; missing history is never assumed to be screen time. */
internal class ScreenTime(private val start: Long, private val now: Long) {
    private var known = false
    private var opening: Long? = null
    private var elapsed = 0L

    fun record(timestamp: Long, interactive: Boolean) {
        if (timestamp > now) return
        val at = timestamp.coerceAtLeast(start)
        if (interactive) {
            if (opening == null) opening = at
        } else {
            opening?.let { elapsed += (at - it).coerceAtLeast(0L) }
            opening = null
        }
        known = true
    }

    fun total(): Long? = if (!known) null else elapsed + (opening?.let { now - it } ?: 0L)
}
