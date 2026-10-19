package com.rolexm.launcher.intelligence

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.Process
import com.rolexm.launcher.diagnostics.Reading

/**
 * ROLEXM's own process memory, read from
 * ActivityManager.getProcessMemoryInfo (proportional set size).
 *
 * PSS is the honest metric: it counts shared pages proportionally, so it
 * reflects real cost to the system rather than a virtual address total.
 */
data class ProcessMemory(
    val totalPssKb: Int,
    val dalvikPssKb: Int,
    val nativePssKb: Int,
    /** Free memory across the whole system, in megabytes. */
    val systemAvailableMb: Long,
    /** System-wide low-memory flag; the trigger for defensive trimming. */
    val systemLowMemory: Boolean
) {
    val totalPssMb: Double get() = totalPssKb / 1024.0
}

class MemorySampler(private val context: Context) {

    private val activityManager: ActivityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * Android throttles how often PSS can be read; on some devices a call made
     * too soon after a previous one returns stale numbers rather than failing.
     * Sampling is therefore paced by the profile's interval, not per frame.
     */
    fun sample(): Reading<ProcessMemory> {
        val pid = Process.myPid()
        val infos: Array<Debug.MemoryInfo>? = try {
            activityManager.getProcessMemoryInfo(intArrayOf(pid))
        } catch (e: RuntimeException) {
            return Reading.unavailable("Could not read process memory: ${e.message ?: "system service error"}")
        }

        val info = infos?.firstOrNull()
            ?: return Reading.unavailable("ActivityManager returned no memory information for this process.")

        val system = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(system)

        return Reading.measured(
            value = ProcessMemory(
                totalPssKb = info.totalPss,
                dalvikPssKb = info.dalvikPss,
                nativePssKb = info.nativePss,
                systemAvailableMb = system.availMem / (1024L * 1024L),
                systemLowMemory = system.lowMemory
            ),
            source = "ActivityManager.getProcessMemoryInfo + MemoryInfo",
            atMillis = System.currentTimeMillis()
        )
    }
}

/**
 * Battery state from the sticky ACTION_BATTERY_CHANGED broadcast. No
 * permission, no receiver registration that outlives the call.
 *
 * Level and charging state are universally supported. Current draw is not:
 * many devices omit it, which is reported as absent rather than as zero.
 */
data class BatterySnapshot(
    val percent: Int,
    val charging: Boolean,
    /** "AC", "USB", "Wireless", or "" when not charging. */
    val chargeSource: String,
    /** Instantaneous current in microamps, or null when the device omits it. */
    val currentMicroAmps: Int?
)

class BatterySampler(private val context: Context) {

    fun sample(): Reading<BatterySnapshot> {
        val intent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: RuntimeException) {
            return Reading.unavailable("Battery status broadcast unavailable: ${e.message ?: "unknown"}")
        }

        if (intent == null) {
            return Reading.unavailable("Battery status broadcast returned no data.")
        }

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            return Reading.unavailable("Battery level was not reported by the system.")
        }

        val percent = (level * 100f / scale).toInt().coerceIn(0, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val chargeSource = when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless"
            else -> ""
        }

        val current = readCurrentNow()

        return Reading.measured(
            value = BatterySnapshot(percent, charging, chargeSource, current),
            source = "Intent.ACTION_BATTERY_CHANGED (sticky)",
            atMillis = System.currentTimeMillis()
        )
    }

    /**
     * BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, in microamps. Returns
     * Int.MIN_VALUE when unsupported, which is reported as null rather than as
     * an absurd negative current.
     */
    private fun readCurrentNow(): Int? {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return null
        return try {
            val microAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (microAmps == Int.MIN_VALUE) null else microAmps
        } catch (e: RuntimeException) {
            null
        }
    }
}
