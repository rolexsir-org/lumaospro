package com.lumaos.pro.launcher.widgets.battery

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.RemoteViews
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.ui.theme.ColorMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date

/** Real sticky battery reading, with no extra permission or persistent service. */
class BatteryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        observeTheme(context.applicationContext)
        refresh(context.applicationContext)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        refresh(context.applicationContext)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        observeTheme(context.applicationContext)
        refresh(context.applicationContext)
    }

    override fun onDisabled(context: Context) { themeJob?.cancel(); themeJob = null }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var themeJob: Job? = null

        private fun observeTheme(context: Context) {
            if (themeJob != null) return
            val themes = (context as LumaApp).core.themes
            themeJob = scope.launch { themes.theme.collect { refresh(context) } }
        }

        /** Also called on real battery changes while Luma Home is started. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(app, BatteryWidgetProvider::class.java)) }
                .onFailure { LumaLog.w("Battery widget lookup failed") }.getOrDefault(intArrayOf())
            if (ids.isEmpty()) return
            val battery = runCatching { app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
                .onFailure { LumaLog.w("Battery reading unavailable") }.getOrNull()
            val level = BatteryLogic.percent(battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
                battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1)
            val status = BatteryLogic.status(battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1)
            val theme = (app as LumaApp).core.themes.theme.value
            val background = if (theme.isDark) 0xff202b39.toInt() else 0xffeef3f8.toInt()
            val primary = if (theme.isDark) 0xfff8faff.toInt() else 0xff142332.toInt()
            val muted = if (theme.isDark) 0xffc1cfdb.toInt() else 0xff40536a.toInt()
            val accent = ColorMath.ensureContrast(ColorMath.opaque(theme.colors.accent), background, 3.0)
            val views = RemoteViews(app.packageName, R.layout.widget_luma_battery)
            views.setInt(R.id.battery_root, "setBackgroundResource", if (theme.isDark) R.drawable.bg_music_glass_dark else R.drawable.bg_music_glass_light)
            views.setInt(R.id.battery_icon, "setColorFilter", accent)
            views.setTextColor(R.id.battery_level, primary)
            views.setTextColor(R.id.battery_status, muted)
            views.setTextColor(R.id.battery_updated, muted)
            views.setTextViewText(R.id.battery_level, level?.let { app.getString(R.string.battery_percent, it) } ?: app.getString(R.string.battery_unknown))
            val statusText = when (status) {
                BatteryStatus.CHARGING -> R.string.battery_status_charging
                BatteryStatus.FULL -> R.string.battery_status_full
                BatteryStatus.NOT_CHARGING -> R.string.battery_status_not_charging
                BatteryStatus.UNKNOWN -> R.string.battery_status_unknown
            }
            views.setTextViewText(R.id.battery_status, app.getString(statusText))
            val time = android.text.format.DateFormat.getTimeFormat(app).format(Date())
            views.setTextViewText(R.id.battery_updated, app.getString(R.string.battery_updated, time))
            ids.forEach { id ->
                runCatching { manager.updateAppWidget(id, views) }
                    .onFailure { LumaLog.w("Battery widget update failed") }
            }
        }
    }
}
