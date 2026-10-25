package com.lumaos.pro.launcher.intelligence

import android.app.ActivityManager
import android.content.Context
import kotlin.math.max

/**
 * Gathers the raw inputs for [DeviceCapabilityClassifier] from public APIs.
 *
 * Everything read here is documented and needs no permission:
 *  - ActivityManager.MemoryInfo.totalMem
 *  - ActivityManager.isLowRamDevice()
 *  - Runtime.availableProcessors()
 *  - DisplayMetrics for screen size
 */
object DeviceCapabilityProvider {

    fun snapshot(context: Context): DeviceCapability {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalMemoryMb = memoryInfo.totalMem / (1024L * 1024L)

        // The platform's own verdict, which accounts for things a raw number
        // cannot (e.g. a device with 2 GB that reserves most of it).
        val isLowRam = activityManager.isLowRamDevice

        val metrics = context.resources.displayMetrics
        val longEdgePx = max(metrics.widthPixels, metrics.heightPixels)
        val longEdgeDp = (longEdgePx * 160f / metrics.densityDpi).toInt()

        return DeviceCapabilityClassifier.classify(
            totalMemoryMb = totalMemoryMb,
            isLowRamDevice = isLowRam,
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            screenLongEdgeDp = longEdgeDp
        )
    }
}
