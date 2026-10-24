package com.rolexm.launcher.motion

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ROLEXM's motion primitive: a damped spring integrated in closed form.
 *
 * Why springs rather than fixed-duration tweens:
 *  - interruption safety: a spring's state is (value, velocity). Retargeting
 *    mid-flight keeps the current velocity, so reversing a half-open drawer
 *    never jumps or restarts;
 *  - gesture hand-off: the finger's release velocity becomes the spring's
 *    initial velocity, so motion continues the gesture instead of replacing it;
 *  - one vocabulary: every surface uses the same few [SpringSpec] tokens, so the
 *    launcher moves consistently.
 *
 * The step uses the analytic solution of x'' + 2ζω₀x' + ω₀²x = 0 for the
 * displacement from the target, which is exact for any frame time; a slow
 * frame never makes the spring unstable the way explicit Euler integration can.
 * Pure Kotlin, unit tested on the JVM.
 */
data class SpringSpec(val stiffness: Float, val dampingRatio: Float) {
    init {
        require(stiffness > 0f) { "stiffness must be positive" }
        require(dampingRatio > 0f) { "dampingRatio must be positive" }
    }

    /** Same speed, never overshoots. Used when overshoot is not allowed. */
    fun withoutOvershoot(): SpringSpec = if (dampingRatio >= 1f) this else copy(dampingRatio = 1f)
}

/** The shared motion vocabulary. */
object MotionTokens {
    /** Drawer settle, menus, chips: quick and firm, barely any overshoot. */
    val SNAPPY = SpringSpec(stiffness = 1100f, dampingRatio = 0.86f)
    /** General UI repositioning. */
    val STANDARD = SpringSpec(stiffness = 600f, dampingRatio = 0.9f)
    /** Large surfaces and colour/theme changes: no overshoot, unhurried. */
    val GENTLE = SpringSpec(stiffness = 220f, dampingRatio = 1.0f)
    /** Small objects landing (icon drop, folder pop): a single soft overshoot. */
    val LIVELY = SpringSpec(stiffness = 700f, dampingRatio = 0.68f)
}

data class SpringState(val value: Float, val velocity: Float)

object SpringPhysics {

    /**
     * Advances [state] towards [target] by [dtSeconds].
     */
    fun step(state: SpringState, target: Float, spec: SpringSpec, dtSeconds: Float): SpringState {
        if (dtSeconds <= 0f) return state
        val w0 = sqrt(spec.stiffness.toDouble())
        val zeta = spec.dampingRatio.toDouble()
        val x0 = (state.value - target).toDouble()
        val v0 = state.velocity.toDouble()
        val t = dtSeconds.toDouble()
        val x: Double
        val v: Double
        when {
            zeta < 1.0 -> {
                val wd = w0 * sqrt(1 - zeta * zeta)
                val a = x0
                val b = (v0 + zeta * w0 * x0) / wd
                val decay = exp(-zeta * w0 * t)
                val c = cos(wd * t)
                val s = sin(wd * t)
                x = decay * (a * c + b * s)
                v = decay * ((b * wd - zeta * w0 * a) * c - (a * wd + zeta * w0 * b) * s)
            }
            zeta == 1.0 -> {
                val a = x0
                val b = v0 + w0 * x0
                val decay = exp(-w0 * t)
                x = (a + b * t) * decay
                v = (b - w0 * (a + b * t)) * decay
            }
            else -> {
                val root = sqrt(zeta * zeta - 1)
                val r1 = -w0 * (zeta - root)
                val r2 = -w0 * (zeta + root)
                val c2 = (v0 - r1 * x0) / (r2 - r1)
                val c1 = x0 - c2
                x = c1 * exp(r1 * t) + c2 * exp(r2 * t)
                v = c1 * r1 * exp(r1 * t) + c2 * r2 * exp(r2 * t)
            }
        }
        return SpringState((target + x).toFloat(), v.toFloat())
    }

    /** At rest when both displacement and speed are below perceptual thresholds. */
    fun isAtRest(state: SpringState, target: Float, valueThreshold: Float, velocityThreshold: Float): Boolean =
        abs(state.value - target) <= valueThreshold && abs(state.velocity) <= velocityThreshold

    /**
     * Upper bound on settle time (seconds) from rest at displacement [distance],
     * used to cap animations and in tests.
     */
    fun settleTime(spec: SpringSpec, distance: Float, valueThreshold: Float): Float {
        var s = SpringState(distance, 0f)
        var t = 0f
        val dt = 1f / 120f
        while (t < 10f) {
            s = step(s, 0f, spec, dt)
            t += dt
            if (isAtRest(s, 0f, valueThreshold, max(valueThreshold * 10f, 0.01f))) return t
        }
        return t
    }
}

/** User preference for motion, independent of the system animator scale. */
enum class MotionPreference(val storageKey: String) {
    FULL("full"),
    REDUCED("reduced"),
    OFF("off");

    companion object {
        fun fromStorageKey(raw: String?): MotionPreference = entries.firstOrNull { it.storageKey == raw } ?: FULL
    }
}

/**
 * What motion is allowed right now.
 *
 * [timeScale] follows Android's convention for animator duration scale: 1 is
 * normal, larger is slower. A spring advances by dt / timeScale.
 */
data class MotionPolicy(
    val enabled: Boolean,
    val timeScale: Float,
    val allowOvershoot: Boolean,
    /** Large travel and depth transforms are replaced by immediate state changes. */
    val reduced: Boolean = false
) {

    fun adapt(spec: SpringSpec): SpringSpec = if (allowOvershoot) spec else spec.withoutOvershoot()

    companion object {
        val INSTANT = MotionPolicy(enabled = false, timeScale = 0f, allowOvershoot = false, reduced = true)
        val DEFAULT = MotionPolicy(enabled = true, timeScale = 1f, allowOvershoot = true)

        /**
         * @param systemAnimatorScale Settings.Global.ANIMATOR_DURATION_SCALE; 0
         *        means the user removed animations system-wide, which ROLEXM
         *        always honours.
         * @param transitionsAllowed  from the effects policy (false under heavy
         *        measured jank).
         */
        fun resolve(
            preference: MotionPreference,
            systemAnimatorScale: Float,
            transitionsAllowed: Boolean,
            overshootAllowed: Boolean
        ): MotionPolicy {
            if (preference == MotionPreference.OFF || systemAnimatorScale <= 0f || !transitionsAllowed) return INSTANT
            val base = systemAnimatorScale.coerceIn(0.25f, 3f)
            return when (preference) {
                MotionPreference.REDUCED -> MotionPolicy(
                    enabled = true,
                    timeScale = base * 0.8f,
                    allowOvershoot = false,
                    reduced = true
                )
                else -> MotionPolicy(true, base, allowOvershoot = overshootAllowed)
            }
        }
    }
}
