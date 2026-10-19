package com.rolexm.launcher.intelligence

/**
 * How capable the current device is, as measured from public Android APIs.
 *
 * ROLEXM uses this to pick sensible defaults on first run. It is deliberately
 * coarse: the tier only decides *starting* parameters, and the user can always
 * override them.
 */
data class DeviceCapability(
    val tier: DeviceTier,
    /** ActivityManager.MemoryInfo.totalMem, in megabytes. */
    val totalMemoryMb: Long,
    /** ActivityManager.isLowRamDevice() - the platform's own low-memory verdict. */
    val isLowRamDevice: Boolean,
    val cpuCoreCount: Int,
    val smallScreen: Boolean,
    /** Plain-language explanation shown on the diagnostics screen. */
    val reasons: List<String>
)

enum class DeviceTier { LOW, MEDIUM, HIGH }

/**
 * Classifies a device from raw numbers.
 *
 * Kept free of Android imports so the thresholds are unit tested on the JVM;
 * the Android-side collector ([DeviceCapabilityProvider]) only gathers inputs.
 */
object DeviceCapabilityClassifier {

    /** A 4-core / 2 GB phone scores 1 -> LOW, which is the target device. */
    fun classify(
        totalMemoryMb: Long,
        isLowRamDevice: Boolean,
        cpuCoreCount: Int,
        screenLongEdgeDp: Int
    ): DeviceCapability {
        val reasons = ArrayList<String>(6)

        var score = when {
            totalMemoryMb <= 1_024L -> {
                reasons += "1 GB RAM or less ($totalMemoryMb MB)"; 0
            }
            totalMemoryMb <= 2_048L -> {
                reasons += "about 2 GB RAM ($totalMemoryMb MB)"; 1
            }
            totalMemoryMb <= 4_096L -> {
                reasons += "3-4 GB RAM ($totalMemoryMb MB)"; 2
            }
            totalMemoryMb <= 6_144L -> {
                reasons += "4-6 GB RAM ($totalMemoryMb MB)"; 3
            }
            else -> {
                reasons += "more than 6 GB RAM ($totalMemoryMb MB)"; 4
            }
        }

        when {
            cpuCoreCount <= 2 -> {
                reasons += "$cpuCoreCount CPU cores"
                score -= 1
            }
            cpuCoreCount >= 8 -> {
                reasons += "$cpuCoreCount CPU cores"
                score += 1
            }
            else -> reasons += "$cpuCoreCount CPU cores"
        }

        if (isLowRamDevice) {
            reasons += "reported as a low-RAM device by the system"
            // The platform's own verdict caps the tier; we never claim a
            // low-RAM device is high-end just because core counts are healthy.
            score = minOf(score, 3)
        }

        val smallScreen = screenLongEdgeDp in 1..599
        if (smallScreen) reasons += "small screen (${screenLongEdgeDp}dp long edge)"

        val tier = when {
            score <= 1 -> DeviceTier.LOW
            score <= 3 -> DeviceTier.MEDIUM
            else -> DeviceTier.HIGH
        }
        reasons += "classified as ${tier.name}"

        return DeviceCapability(
            tier = tier,
            totalMemoryMb = totalMemoryMb,
            isLowRamDevice = isLowRamDevice,
            cpuCoreCount = cpuCoreCount,
            smallScreen = smallScreen,
            reasons = reasons
        )
    }
}
