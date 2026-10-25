package com.lumaos.pro.launcher.intelligence

/**
 * A bundle of tuning parameters LumaOS Pro applies to *itself*.
 *
 * Scope matters: nothing here reaches outside LumaOS Pro's own process. LumaOS Pro
 * cannot change another app's behaviour, the CPU governor, or system
 * animation scale, and a profile must not imply otherwise.
 */
data class Profile(
    val id: ProfileId,

    /** Multiplier for LumaOS Pro's own transition durations. 0 means "no animation". */
    val animationScale: Float,

    /** Whether home/drawer screen transitions animate at all. */
    val transitionsEnabled: Boolean,

    /** Upper bound for the in-memory app-icon cache, in entries. */
    val maxCachedIcons: Int,

    /** Icons decoded eagerly on load (smoother scrolling, more memory) vs on demand. */
    val preloadIcons: Boolean,

    /** How often the intelligence engine samples process memory while visible. */
    val memorySampleIntervalMs: Long,

    /** Choreographer frame instrumentation has a real cost; disable on weak devices. */
    val frameMonitorEnabled: Boolean,

    /** Whether home-screen widgets receive updates. */
    val widgetUpdatesEnabled: Boolean,

    /** Shadows, scrims and blur are the most expensive pixels to draw. */
    val richVisuals: Boolean,

    /** Minimum gap between automatic maintenance runs. */
    val autoActionCooldownMs: Long,

    /** Jank percentage above which automatic maintenance is considered. */
    val jankTriggerPercent: Double
)

enum class ProfileId { LIGHTWEIGHT, BALANCED, VISUAL_QUALITY }

object Profiles {

    /**
     * Fewest moving parts. Targets the 1-2 GB phones LumaOS Pro exists for:
     * no frame instrumentation, small icon cache, no rich visuals.
     */
    val LIGHTWEIGHT = Profile(
        id = ProfileId.LIGHTWEIGHT,
        animationScale = 0f,
        transitionsEnabled = false,
        maxCachedIcons = 48,
        preloadIcons = false,
        memorySampleIntervalMs = 60_000L,
        frameMonitorEnabled = false,
        widgetUpdatesEnabled = false,
        richVisuals = false,
        autoActionCooldownMs = 30 * 60 * 1000L,
        jankTriggerPercent = 25.0
    )

    /** Default. Mild transitions, moderate cache, frame instrumentation on. */
    val BALANCED = Profile(
        id = ProfileId.BALANCED,
        animationScale = 0.6f,
        transitionsEnabled = true,
        maxCachedIcons = 128,
        preloadIcons = true,
        memorySampleIntervalMs = 30_000L,
        frameMonitorEnabled = true,
        widgetUpdatesEnabled = true,
        richVisuals = true,
        autoActionCooldownMs = 15 * 60 * 1000L,
        jankTriggerPercent = 15.0
    )

    /** Smoothest visuals. Only offered as an explicit choice on capable hardware. */
    val VISUAL_QUALITY = Profile(
        id = ProfileId.VISUAL_QUALITY,
        animationScale = 1f,
        transitionsEnabled = true,
        maxCachedIcons = 256,
        preloadIcons = true,
        memorySampleIntervalMs = 15_000L,
        frameMonitorEnabled = true,
        widgetUpdatesEnabled = true,
        richVisuals = true,
        autoActionCooldownMs = 10 * 60 * 1000L,
        jankTriggerPercent = 10.0
    )

    fun of(id: ProfileId): Profile = when (id) {
        ProfileId.LIGHTWEIGHT -> LIGHTWEIGHT
        ProfileId.BALANCED -> BALANCED
        ProfileId.VISUAL_QUALITY -> VISUAL_QUALITY
    }

    val ALL = listOf(LIGHTWEIGHT, BALANCED, VISUAL_QUALITY)

    /** Order used when the engine steps a profile up or down. */
    private val LADDER = listOf(ProfileId.LIGHTWEIGHT, ProfileId.BALANCED, ProfileId.VISUAL_QUALITY)

    fun step(id: ProfileId, delta: Int): ProfileId {
        val index = LADDER.indexOf(id)
        if (index < 0) return id
        return LADDER[(index + delta).coerceIn(0, LADDER.lastIndex)]
    }
}
