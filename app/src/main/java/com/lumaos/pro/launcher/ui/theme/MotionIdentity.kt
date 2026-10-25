package com.lumaos.pro.launcher.ui.theme

import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringSpec

/**
 * Motion character for each signature theme.
 *
 * All three share one spring vocabulary ([MotionTokens]) and one rule set:
 * every animation is interruptible, nothing blocks input, and Android's
 * animator-duration scale is honoured. The character only changes how the
 * springs are used - never whether feedback happens.
 *
 *  - Aurora: subtle fades and restrained scale on smooth, non-overshooting springs.
 *  - Zen: gentle, quiet movement with slightly longer settles and no urgency.
 *  - Void: expressive, responsive scale with a single controlled overshoot.
 */
enum class MotionCharacter(val storageKey: String) {
    SUBTLE("subtle"),
    QUIET("quiet"),
    EXPRESSIVE("expressive");

    companion object {
        fun fromStorageKey(raw: String?): MotionCharacter? = entries.firstOrNull { it.storageKey == raw }
    }
}

/** Reusable durations, scales, springs and easing for a motion character. */
data class MotionProfile(
    val character: MotionCharacter,
    /** Duration of the pressed-state feedback, in milliseconds. */
    val pressMs: Int,
    /** Nominal open/close durations for Compose transitions that cannot use a spring. */
    val openMs: Int,
    val closeMs: Int,
    /** Theme cross-fade length. Colour roles interpolate on the same clock. */
    val themeChangeMs: Int,
    /** Delay between sibling items entering (lists, grids). 0 disables staggering. */
    val staggerMs: Int,
    /** Press feedback scale. Never below 0.95: smaller reads as a glitch, not a press. */
    val pressScale: Float,
    /** Scale a surface enters from. */
    val enterScaleFrom: Float,
    /** Scale a popup/menu enters from. */
    val popupScaleFrom: Float,
    /** Whether a spring may overshoot. Zen and Aurora never do. */
    val allowOvershoot: Boolean,
    val openSpring: SpringSpec,
    val closeSpring: SpringSpec,
    val popupSpring: SpringSpec,
    val settleSpring: SpringSpec,
    /** Compose easing control points (x1, y1, x2, y2). */
    val easing: FloatArray
) {
    init {
        require(pressMs in 40..200) { "pressMs out of range" }
        require(openMs > 0 && closeMs > 0) { "durations must be positive" }
        require(themeChangeMs in 120..800) { "themeChangeMs out of range" }
        require(pressScale in 0.94f..1f) { "pressScale out of range" }
        require(enterScaleFrom in 0.85f..1f) { "enterScaleFrom out of range" }
        require(popupScaleFrom in 0.85f..1f) { "popupScaleFrom out of range" }
        require(easing.size == 4) { "easing needs four control points" }
    }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = character.hashCode()
}

object LumaMotionProfiles {

    val AURORA = MotionProfile(
        character = MotionCharacter.SUBTLE,
        pressMs = 90,
        openMs = 320,
        closeMs = 240,
        themeChangeMs = 420,
        staggerMs = 18,
        pressScale = 0.985f,
        enterScaleFrom = 0.98f,
        popupScaleFrom = 0.96f,
        allowOvershoot = false,
        openSpring = MotionTokens.GENTLE,
        closeSpring = MotionTokens.SNAPPY,
        popupSpring = MotionTokens.SNAPPY.withoutOvershoot(),
        settleSpring = MotionTokens.STANDARD,
        easing = floatArrayOf(0.2f, 0f, 0f, 1f)
    )

    val ZEN = MotionProfile(
        character = MotionCharacter.QUIET,
        pressMs = 110,
        openMs = 380,
        closeMs = 280,
        themeChangeMs = 480,
        staggerMs = 24,
        pressScale = 0.99f,
        enterScaleFrom = 0.99f,
        popupScaleFrom = 0.98f,
        allowOvershoot = false,
        openSpring = MotionTokens.GENTLE,
        closeSpring = MotionTokens.STANDARD,
        popupSpring = MotionTokens.STANDARD.withoutOvershoot(),
        settleSpring = MotionTokens.GENTLE,
        easing = floatArrayOf(0.4f, 0f, 0.2f, 1f)
    )

    val VOID = MotionProfile(
        character = MotionCharacter.EXPRESSIVE,
        pressMs = 70,
        openMs = 260,
        closeMs = 190,
        themeChangeMs = 320,
        staggerMs = 14,
        pressScale = 0.96f,
        enterScaleFrom = 0.92f,
        popupScaleFrom = 0.94f,
        allowOvershoot = true,
        openSpring = MotionTokens.LIVELY,
        closeSpring = MotionTokens.SNAPPY,
        popupSpring = MotionTokens.SNAPPY,
        settleSpring = MotionTokens.LIVELY,
        easing = floatArrayOf(0.05f, 0.7f, 0.1f, 1f)
    )

    fun of(character: MotionCharacter): MotionProfile = when (character) {
        MotionCharacter.SUBTLE -> AURORA
        MotionCharacter.QUIET -> ZEN
        MotionCharacter.EXPRESSIVE -> VOID
    }
}
