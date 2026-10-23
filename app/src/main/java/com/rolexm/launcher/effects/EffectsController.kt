package com.rolexm.launcher.effects

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import com.rolexm.launcher.core.prefs.RolexPrefs
import com.rolexm.launcher.core.util.RolexLog
import com.rolexm.launcher.intelligence.DeviceCapability
import com.rolexm.launcher.intelligence.IntelligenceState
import com.rolexm.launcher.motion.MotionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import java.util.function.Consumer

/**
 * Feeds live device signals into [EffectsPolicy] and publishes the decision.
 *
 * Every signal comes from a public API and is cheap to read:
 *  - battery saver (PowerManager, broadcast while ROLEXM runs);
 *  - thermal status (PowerManager listener, API 29+);
 *  - jank, memory pressure and battery from the existing IntelligenceEngine;
 *  - system blur support (WindowManager listener, API 31+);
 *  - system animator scale (re-read whenever the home screen resumes).
 */
class EffectsController(
    private val app: Application,
    private val capability: DeviceCapability,
    prefs: RolexPrefs,
    intelligence: StateFlow<IntelligenceState>,
    scope: CoroutineScope
) {
    private val powerManager = app.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val powerSave = MutableStateFlow(powerManager?.isPowerSaveMode == true)
    private val thermal = MutableStateFlow(readThermal())
    private val crossWindowBlur = MutableStateFlow(readCrossWindowBlur())
    private val animatorScale = MutableStateFlow(readAnimatorScale())

    private val signals: StateFlow<EffectsSignals> = combine(
        combine(powerSave, thermal, crossWindowBlur, animatorScale) { ps, th, blur, anim ->
            Quad(ps, th, blur, anim)
        },
        intelligence
    ) { q, state ->
        val battery = state.battery.valueOrNull()
        EffectsSignals(
            tier = capability.tier,
            profile = state.profile.id,
            sdkInt = Build.VERSION.SDK_INT,
            powerSave = q.powerSave,
            thermalStatus = q.thermal,
            batteryPercent = battery?.percent,
            charging = battery?.charging ?: false,
            jankPercent = state.frameStats.takeIf { it.hasSamples && it.frameCount >= MIN_FRAMES }?.jankPercent,
            memoryPressure = state.underMemoryPressure,
            crossWindowBlurEnabled = q.blur,
            systemAnimatorScale = q.animator
        )
    }.stateIn(scope, SharingStarted.Eagerly, EffectsSignals(capability.tier, intelligence.value.profile.id, Build.VERSION.SDK_INT))

    val decision: StateFlow<EffectsDecision> = combine(prefs.effectsPreference, signals) { pref, s ->
        EffectsPolicy.decide(pref, s)
    }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, EffectsPolicy.decide(EffectsPreference.AUTO, signals.value))

    val motion: StateFlow<MotionPolicy> = combine(prefs.motionPreference, decision, animatorScale) { pref, d, scale ->
        MotionPolicy.resolve(pref, scale, d.transitions, overshootAllowed = d.level != EffectsLevel.MINIMAL)
    }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, MotionPolicy.DEFAULT)

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            powerSave.value = powerManager?.isPowerSaveMode == true
        }
    }

    fun start() {
        runCatching {
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else app.registerReceiver(powerReceiver, filter)
        }.onFailure { RolexLog.w("Battery saver updates unavailable") }

        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                powerManager?.addThermalStatusListener(app.mainExecutor) { status -> thermal.value = status }
            }.onFailure { RolexLog.w("Thermal status updates unavailable") }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching {
                val wm = app.getSystemService(WindowManager::class.java)
                wm?.addCrossWindowBlurEnabledListener(app.mainExecutor, Consumer { enabled ->
                    crossWindowBlur.value = enabled
                })
            }.onFailure { RolexLog.w("Blur support updates unavailable") }
        }
    }

    /** Re-reads values that have no change callback; called on home resume. */
    fun refresh() {
        animatorScale.value = readAnimatorScale()
        powerSave.value = powerManager?.isPowerSaveMode == true
        thermal.value = readThermal()
    }

    private fun readThermal(): Int =
        if (Build.VERSION.SDK_INT >= 29) runCatching { powerManager?.currentThermalStatus ?: -1 }.getOrDefault(-1) else -1

    private fun readCrossWindowBlur(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching { app.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true }
                .getOrDefault(false)
        } else false

    private fun readAnimatorScale(): Float = runCatching {
        Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)

    private data class Quad(val powerSave: Boolean, val thermal: Int, val blur: Boolean, val animator: Float)

    companion object {
        /** Ignore jank figures until enough frames were seen to be meaningful. */
        private const val MIN_FRAMES = 240L
    }
}
