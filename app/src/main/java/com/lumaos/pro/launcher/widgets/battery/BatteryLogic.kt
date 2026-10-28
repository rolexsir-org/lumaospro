package com.lumaos.pro.launcher.widgets.battery

enum class BatteryStatus { CHARGING, FULL, NOT_CHARGING, UNKNOWN }

/** Android-free validation for the sticky battery broadcast. */
object BatteryLogic {
    fun percent(level: Int, scale: Int): Int? =
        if (level < 0 || scale <= 0 || level > scale) null else (level.toLong() * 100 / scale).toInt()

    // Stable public BatteryManager.EXTRA_STATUS values (UNKNOWN=1, CHARGING=2,
    // DISCHARGING=3, NOT_CHARGING=4, FULL=5).
    fun status(value: Int): BatteryStatus = when (value) {
        2 -> BatteryStatus.CHARGING
        5 -> BatteryStatus.FULL
        3, 4 -> BatteryStatus.NOT_CHARGING
        else -> BatteryStatus.UNKNOWN
    }
}
