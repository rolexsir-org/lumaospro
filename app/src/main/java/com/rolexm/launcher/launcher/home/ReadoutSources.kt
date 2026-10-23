package com.rolexm.launcher.launcher.home

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.rolexm.launcher.launcher.model.ReadoutSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live data for the chronometer and readout line, all from public broadcasts
 * and getters that need no permission:
 *  - battery: sticky ACTION_BATTERY_CHANGED;
 *  - next alarm: AlarmManager.getNextAlarmClock + ACTION_NEXT_ALARM_CLOCK_CHANGED;
 *  - focus: NotificationManager.getCurrentInterruptionFilter + its change broadcast.
 * Receivers are registered only while the home screen is started.
 */
class ReadoutSources(private val context: Context) {

    private val _state = MutableStateFlow(ReadoutSnapshot())
    val state: StateFlow<ReadoutSnapshot> = _state
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> updateBattery(intent)
                else -> refresh()
            }
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        val sticky = runCatching {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(receiver, filter)
        }.getOrNull()
        registered = true
        sticky?.let { updateBattery(it) }
        refresh()
    }

    fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    fun refresh() {
        val alarm = runCatching {
            (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.nextAlarmClock?.triggerTime
        }.getOrNull()
        val focus = runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val f = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
            f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }.getOrDefault(false)
        _state.value = _state.value.copy(nextAlarmAtMillis = alarm, focusOn = focus)
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else null
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val current = _state.value
        if (current.batteryPercent != percent || current.charging != charging) {
            _state.value = current.copy(batteryPercent = percent, charging = charging)
        }
    }
}
