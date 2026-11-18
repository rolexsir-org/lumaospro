package com.lumaos.pro.launcher.widgets.builtin

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.widgets.WidgetGlassPalette
import com.lumaos.pro.launcher.widgets.WidgetGlassRenderer
import com.lumaos.pro.launcher.widgets.WidgetLabArt
import com.lumaos.pro.launcher.widgets.battery.BatteryWidgetProvider
import com.lumaos.pro.launcher.widgets.music.MusicBridge
import com.lumaos.pro.launcher.widgets.music.MusicWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

internal enum class BuiltinWidgetKind(val provider: Class<out AppWidgetProvider>, val title: Int, val layout: Int) {
    CLOCK(DigitalClockWidgetProvider::class.java, R.string.widget_digital_clock, R.layout.widget_luma_digital),
    ANALOG(AnalogClockWidgetProvider::class.java, R.string.widget_analog_clock, R.layout.widget_luma_analog),
    CALENDAR(CalendarWidgetProvider::class.java, R.string.widget_calendar, R.layout.widget_luma_reading),
    WEATHER(WeatherWidgetProvider::class.java, R.string.widget_weather, R.layout.widget_luma_reading),
    STORAGE(StorageWidgetProvider::class.java, R.string.widget_storage, R.layout.widget_luma_reading),
    USAGE(ScreenTimeWidgetProvider::class.java, R.string.widget_screen_time, R.layout.widget_luma_reading);

    fun component(context: Context) = ComponentName(context, provider)
}

abstract class BuiltinWidgetProvider : AppWidgetProvider() {
    private val kind get() = BuiltinWidgetKind.entries.first { it.provider == javaClass }

    // Hold the process through the short local read. Forecast networking is scheduled separately.
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val result = goAsync()
        BuiltinWidgets.scope.launch {
            try {
                when (intent.action) {
                    AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                        val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: intArrayOf()
                        BuiltinWidgets.refreshNow(app, kind, ids)
                    }
                    AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                        BuiltinWidgets.refreshNow(app, kind, intArrayOf(id))
                    }
                    AppWidgetManager.ACTION_APPWIDGET_RESTORED -> {
                        val old = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS) ?: intArrayOf()
                        val new = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: intArrayOf()
                        if (old.size == new.size) {
                            BuiltinWidgetSettings.restore(app, old, new)
                            val manager = AppWidgetManager.getInstance(app)
                            for (id in new) manager.updateAppWidgetOptions(id, Bundle().apply {
                                putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true)
                            })
                            BuiltinWidgets.refreshNow(app, kind, new)
                        }
                    }
                    AppWidgetManager.ACTION_APPWIDGET_DELETED -> {
                        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                        BuiltinWidgetSettings.delete(app, intArrayOf(id))
                    }
                    AppWidgetManager.ACTION_APPWIDGET_DISABLED -> if (kind == BuiltinWidgetKind.WEATHER) {
                        WeatherRefreshJob.cancel(app)
                        WeatherData.clear(app)
                    }
                }
            } catch (e: RuntimeException) {
                LumaLog.e("Bundled widget update failed", e)
            } finally {
                result?.finish()
            }
        }
    }
}

class DigitalClockWidgetProvider : BuiltinWidgetProvider()
class AnalogClockWidgetProvider : BuiltinWidgetProvider()
class CalendarWidgetProvider : BuiltinWidgetProvider()
class WeatherWidgetProvider : BuiltinWidgetProvider()
class StorageWidgetProvider : BuiltinWidgetProvider()
class ScreenTimeWidgetProvider : BuiltinWidgetProvider()

internal object BuiltinWidgets {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun refreshAll(context: Context) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        for (kind in BuiltinWidgetKind.entries) refreshNow(context, kind)
    }

    internal fun configure(context: Context, provider: ComponentName, id: Int) {
        val kind = BuiltinWidgetKind.entries.firstOrNull { it.component(context) == provider }
        when {
            kind != null -> scope.launch {
                refreshNow(context, kind, intArrayOf(id), reconfigure = true)
            }
            provider == ComponentName(context, BatteryWidgetProvider::class.java) -> BatteryWidgetProvider.refresh(context)
            provider == ComponentName(context, MusicWidgetProvider::class.java) -> MusicBridge.refresh(context)
        }
    }

    internal fun refreshNow(context: Context, kind: BuiltinWidgetKind, requested: IntArray? = null, reconfigure: Boolean = false) {
        runCatching {
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(kind.component(context))
            val targets = if (requested == null) ids else ids.filter { it in requested }.toIntArray()
            if (targets.isEmpty()) return
            render(context, kind, targets)
            if (kind == BuiltinWidgetKind.WEATHER) WeatherRefreshJob.enqueue(context, ids, reconfigure)
        }.onFailure { LumaLog.e("Bundled widget refresh failed", it) }
    }

    internal fun render(context: Context, kind: BuiltinWidgetKind, ids: IntArray) {
        val app = context.applicationContext as? LumaApp ?: return
        val manager = AppWidgetManager.getInstance(app)
        val theme = app.core.themes.theme.value
        val palette = WidgetGlassPalette.fromTheme(theme)
        val reading = when (kind) {
            BuiltinWidgetKind.CALENDAR -> readingOrUnavailable(app) { BuiltinWidgetData.calendar(app) }
            BuiltinWidgetKind.STORAGE -> readingOrUnavailable(app) { BuiltinWidgetData.storage(app) }
            BuiltinWidgetKind.USAGE -> readingOrUnavailable(app) { BuiltinWidgetData.usage(app) }
            else -> null
        }
        for (id in ids) {
            if (manager.getAppWidgetInfo(id)?.provider != kind.component(app)) continue
            val options = manager.getAppWidgetOptions(id)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val compact = width in 1..145 || height in 1..78
            val views = RemoteViews(app.packageName, kind.layout)
            views.setImageViewBitmap(R.id.builtin_widget_plate,
                WidgetGlassRenderer.plateBitmap(app, theme, BuiltinWidgetSettings.radius(app, id)))
            val click = PendingIntent.getActivity(app, id, WidgetSettingsActivity.intent(app, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.builtin_widget_root, click)
            if (kind == BuiltinWidgetKind.CLOCK || kind == BuiltinWidgetKind.ANALOG) {
                views.setInt(R.id.builtin_widget_accent, "setColorFilter", palette.accent)
            }
            when (kind) {
                BuiltinWidgetKind.CLOCK, BuiltinWidgetKind.ANALOG -> {
                    val date = DateFormat.getBestDateTimePattern(Locale.getDefault(), if (compact) "MMMd" else "EEE MMMd")
                    views.setCharSequence(R.id.builtin_widget_date, "setFormat12Hour", date)
                    views.setCharSequence(R.id.builtin_widget_date, "setFormat24Hour", date)
                    views.setTextColor(R.id.builtin_widget_date, palette.mutedText)
                    if (kind == BuiltinWidgetKind.CLOCK) {
                        views.setTextColor(R.id.builtin_widget_clock, palette.primaryText)
                    }
                }
                else -> {
                    val value = reading ?: WeatherData.reading(app, BuiltinWidgetSettings.place(app, id))
                    views.setTextViewText(R.id.builtin_widget_title, app.getString(kind.title).uppercase(Locale.getDefault()))
                    views.setTextViewText(R.id.builtin_widget_value, value.value)
                    views.setTextViewText(R.id.builtin_widget_detail, value.detail)
                    views.setViewVisibility(R.id.builtin_widget_detail, if (value.detail.isBlank()) View.GONE else View.VISIBLE)
                    views.setTextColor(R.id.builtin_widget_title, palette.mutedText)
                    views.setTextColor(R.id.builtin_widget_value, palette.primaryText)
                    views.setTextColor(R.id.builtin_widget_detail, palette.mutedText)
                    views.setTextViewTextSize(R.id.builtin_widget_value, TypedValue.COMPLEX_UNIT_SP,
                        if (compact) 16f else 27f)
                    val showsVisual = width == 0 || width >= 148
                    when {
                        kind == BuiltinWidgetKind.CALENDAR && showsVisual -> {
                            views.setViewVisibility(R.id.builtin_widget_visual, View.VISIBLE)
                            views.setViewVisibility(R.id.builtin_widget_calendar_tile, View.VISIBLE)
                            views.setTextColor(R.id.builtin_widget_day, palette.accent)
                            views.setTextColor(R.id.builtin_widget_weekday, palette.mutedText)
                        }
                        value.weatherCode != null && showsVisual -> {
                            val icon = when (value.weatherCode) {
                                0 -> R.drawable.widget_weather_clear
                                in 1..3 -> R.drawable.widget_weather_cloud
                                45, 48 -> R.drawable.widget_weather_fog
                                in 51..67, in 80..82 -> R.drawable.widget_weather_rain
                                in 71..77, 85, 86 -> R.drawable.widget_weather_snow
                                in 95..99 -> R.drawable.widget_weather_storm
                                else -> null
                            }
                            if (icon != null) {
                                views.setViewVisibility(R.id.builtin_widget_visual, View.VISIBLE)
                                views.setImageViewResource(R.id.builtin_widget_art, icon)
                                views.setInt(R.id.builtin_widget_art, "setColorFilter", palette.accent)
                            }
                        }
                        value.days.count { it != null } >= 2 && showsVisual -> {
                            views.setViewVisibility(R.id.builtin_widget_visual, View.VISIBLE)
                            views.setImageViewBitmap(R.id.builtin_widget_art, WidgetLabArt.history(value.days, palette))
                        }
                        value.used != null -> {
                            views.setViewVisibility(R.id.builtin_widget_meter, View.VISIBLE)
                            views.setImageViewBitmap(R.id.builtin_widget_meter, WidgetLabArt.partitions(value.used, palette))
                        }
                    }
                }
            }
            runCatching { manager.updateAppWidget(id, views) }
                .onFailure { LumaLog.w("Bundled widget could not be displayed") }
        }
    }

    private inline fun readingOrUnavailable(context: Context, block: () -> WidgetReading): WidgetReading =
        runCatching(block).getOrElse { WidgetReading(context.getString(R.string.widget_data_unavailable)) }
}
