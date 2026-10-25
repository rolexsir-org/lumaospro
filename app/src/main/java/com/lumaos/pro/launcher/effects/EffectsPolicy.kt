package com.lumaos.pro.launcher.effects

import com.lumaos.pro.launcher.intelligence.DeviceTier
import com.lumaos.pro.launcher.intelligence.ProfileId

/** What the user asked for. AUTO lets LumaOS Pro decide from live signals. */
enum class EffectsPreference(val storageKey: String) {
    AUTO("auto"),
    MINIMAL("minimal"),
    BALANCED("balanced"),
    FULL("full");

    companion object {
        fun fromStorageKey(raw: String?): EffectsPreference = entries.firstOrNull { it.storageKey == raw } ?: AUTO
    }
}

/**
 * Effect tiers. Every tier keeps the full LumaOS Pro design - palette, typography,
 * icon treatment, layout, static atmosphere and spring transitions. Tiers only
 * add or remove the expensive pixels on top.
 */
enum class EffectsLevel { MINIMAL, BALANCED, FULL }

/**
 * Live signals, all read from public APIs at runtime. Device "tier" is only
 * the starting point; measured behaviour (jank, thermal, memory, battery
 * saver) overrides it.
 */
data class EffectsSignals(
    val tier: DeviceTier,
    val profile: ProfileId,
    val sdkInt: Int,
    val powerSave: Boolean = false,
    /** PowerManager.THERMAL_STATUS_*; -1 when unknown (API < 29). */
    val thermalStatus: Int = -1,
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    /** Share of janky frames in the recent window, 0..100; null when not measured. */
    val jankPercent: Double? = null,
    val memoryPressure: Boolean = false,
    /** WindowManager.isCrossWindowBlurEnabled(); false below API 31. */
    val crossWindowBlurEnabled: Boolean = false,
    /** Settings.Global.ANIMATOR_DURATION_SCALE. */
    val systemAnimatorScale: Float = 1f
)

data class EffectsDecision(
    val level: EffectsLevel,
    /** Slow drift of the atmosphere layers. */
    val ambientMotion: Boolean,
    /** Frame rate cap for ambient drift. */
    val ambientFps: Int,
    /** Real-time blur of content behind the drawer and folders (API 31+ RenderEffect). */
    val blur: Boolean,
    /** Translucent / glass surface materials instead of solid ones. */
    val translucency: Boolean,
    /** Rim light and soft shadow on icons. */
    val iconDepth: Boolean,
    /** Desktop recedes (scale/fade) behind drawer, folders and edit mode. */
    val depthTransforms: Boolean,
    /** Spring transitions at all; false only when measured jank is severe or animations are off. */
    val transitions: Boolean,
    /** Fine atmosphere detail (grid hairlines). */
    val atmosphereDetail: Boolean,
    /** Plain-language reasons, shown in Studio and diagnostics. */
    val reasons: List<String>
)

/**
 * Chooses effects from the user's preference and runtime signals.
 *
 * An explicit preference is honoured, with two exceptions that protect the
 * device rather than the look: battery saver and thermal throttling always
 * stop ambient motion and blur. AUTO additionally steps the level down for
 * measured jank, memory pressure and heat.
 */
object EffectsPolicy {

    const val THERMAL_MODERATE = 2
    const val THERMAL_SEVERE = 3

    fun decide(preference: EffectsPreference, signals: EffectsSignals): EffectsDecision {
        val reasons = ArrayList<String>()
        var level = when (preference) {
            EffectsPreference.MINIMAL -> EffectsLevel.MINIMAL.also { reasons += "Minimal effects chosen in settings" }
            EffectsPreference.BALANCED -> EffectsLevel.BALANCED.also { reasons += "Balanced effects chosen in settings" }
            EffectsPreference.FULL -> EffectsLevel.FULL.also { reasons += "Full effects chosen in settings" }
            EffectsPreference.AUTO -> autoLevel(signals, reasons)
        }
        val auto = preference == EffectsPreference.AUTO

        if (auto) {
            if (signals.powerSave && level > EffectsLevel.BALANCED) {
                level = EffectsLevel.BALANCED; reasons += "Battery saver is on"
            }
            if (signals.thermalStatus >= THERMAL_SEVERE && level > EffectsLevel.MINIMAL) {
                level = EffectsLevel.MINIMAL; reasons += "Device is thermally throttling"
            } else if (signals.thermalStatus == THERMAL_MODERATE && level > EffectsLevel.BALANCED) {
                level = EffectsLevel.BALANCED; reasons += "Device is warm"
            }
            if (signals.memoryPressure && level > EffectsLevel.BALANCED) {
                level = EffectsLevel.BALANCED; reasons += "System reported memory pressure"
            }
            val jank = signals.jankPercent
            if (jank != null && jank >= JANK_STEP_DOWN && level > EffectsLevel.MINIMAL) {
                level = if (level == EffectsLevel.FULL) EffectsLevel.BALANCED else EffectsLevel.MINIMAL
                reasons += "Measured %.0f%% janky frames".format(jank)
            }
        }

        val protectDevice = signals.powerSave || signals.thermalStatus >= THERMAL_MODERATE
        val lowBattery = signals.batteryPercent != null && signals.batteryPercent <= 15 && !signals.charging
        if (signals.powerSave && !auto) reasons += "Battery saver: ambient motion and blur paused"
        if (signals.thermalStatus >= THERMAL_MODERATE && !auto) reasons += "Device warm: ambient motion and blur paused"
        if (lowBattery) reasons += "Battery below 15%: ambient motion paused"

        val animationsOff = signals.systemAnimatorScale <= 0f
        if (animationsOff) reasons += "System animations are turned off"
        val severeJank = auto && (signals.jankPercent ?: 0.0) >= JANK_INSTANT

        val ambient = level == EffectsLevel.FULL && !protectDevice && !lowBattery && !animationsOff
        val blur = level == EffectsLevel.FULL && signals.sdkInt >= 31 && !protectDevice &&
            !signals.memoryPressure && signals.crossWindowBlurEnabled
        if (level == EffectsLevel.FULL && signals.sdkInt >= 31 && !signals.crossWindowBlurEnabled) {
            reasons += "System blur is disabled on this device"
        }
        return EffectsDecision(
            level = level,
            ambientMotion = ambient,
            ambientFps = if (ambient) 24 else 0,
            blur = blur,
            translucency = level >= EffectsLevel.BALANCED,
            iconDepth = level >= EffectsLevel.BALANCED,
            depthTransforms = level >= EffectsLevel.BALANCED && !animationsOff,
            transitions = !animationsOff && !severeJank,
            atmosphereDetail = level >= EffectsLevel.BALANCED,
            reasons = reasons
        )
    }

    private fun autoLevel(signals: EffectsSignals, reasons: MutableList<String>): EffectsLevel {
        val level = when (signals.profile) {
            ProfileId.LIGHTWEIGHT -> EffectsLevel.MINIMAL
            ProfileId.BALANCED -> if (signals.tier == DeviceTier.HIGH) EffectsLevel.FULL else EffectsLevel.BALANCED
            ProfileId.VISUAL_QUALITY -> EffectsLevel.FULL
        }
        reasons += "Automatic: ${signals.profile.name.lowercase().replace('_', ' ')} profile on a ${signals.tier.name.lowercase()}-tier device"
        return level
    }

    const val JANK_STEP_DOWN = 20.0
    const val JANK_INSTANT = 40.0
}
