package com.lumaos.pro.launcher.intelligence

import com.lumaos.pro.launcher.optimizer.Optimizer

/**
 * The conditions under which LumaOS Pro acts on its own.
 *
 * Pure functions over a snapshot, so the decision logic is unit tested: each
 * rule returns either a human-readable reason to act, or null.
 *
 * Two design constraints shape these rules:
 *
 *  1. They only ever trigger work on LumaOS Pro itself. There is no rule that kills
 *     a process, clears another app's cache or changes a system setting,
 *     because LumaOS Pro cannot do those things legitimately.
 *  2. They are deliberately conservative. An optimizer that fires constantly
 *     costs more battery than it saves, so each rule requires a real signal -
 *     system low-memory pressure, or a cache that has actually grown.
 */
object Rules {

    const val RULE_ICON_CACHE = "rule_icon_cache"
    const val RULE_INTERNAL_CACHE = "rule_internal_cache"
    const val RULE_PROFILE_ADAPT = "rule_profile_adapt"

    /** Don't trust jank figures until enough frames have been observed. */
    const val MIN_FRAME_SAMPLES = 120L

    /** Below this many cached icons, trimming saves nothing worth the reload. */
    const val MIN_ICONS_TO_TRIM = 16

    data class Context(
        val profile: Profile,
        val memory: ProcessMemory?,
        val frameStats: FrameStats,
        val iconCacheEntries: Int,
        val internalCacheBytes: Long,
        /** False when the user picked a profile explicitly. */
        val autoProfile: Boolean
    )

    /**
     * Trim icons only when the system is genuinely short of memory. Trimming
     * otherwise would force a re-decode storm the next time the user scrolls,
     * which is a worse experience than the memory it holds.
     */
    fun iconCacheShouldTrim(ctx: Context): String? {
        if (ctx.iconCacheEntries < MIN_ICONS_TO_TRIM) return null
        if (ctx.memory?.systemLowMemory != true) return null
        return "System reported low memory with ${ctx.iconCacheEntries} icons cached."
    }

    /**
     * Clear LumaOS Pro's own cache once it has grown past the threshold. This is a
     * size test, not a timer: a small cache is not worth a disk walk.
     */
    fun internalCacheShouldClear(ctx: Context): String? {
        if (ctx.internalCacheBytes < Optimizer.INTERNAL_CACHE_TRIM_THRESHOLD_BYTES) return null
        return "LumaOS Pro's cache has grown to ${ctx.internalCacheBytes / (1024 * 1024)} MB."
    }

    /**
     * Step the profile down when sustained jank is observed.
     *
     * Requires: automatic profile selection, enough samples to be meaningful,
     * jank above the profile's own threshold, and headroom to step down. The
     * sample floor matters - a handful of frames right after a screen
     * transition is not evidence.
     */
    fun shouldAdaptProfile(ctx: Context): String? {
        if (!ctx.autoProfile) return null
        if (ctx.profile.id == ProfileId.LIGHTWEIGHT) return null
        if (ctx.frameStats.frameCount < MIN_FRAME_SAMPLES) return null
        if (ctx.frameStats.jankPercent <= ctx.profile.jankTriggerPercent) return null
        return "Sustained jank of %.0f%% over %d frames (threshold %.0f%%).".format(
            ctx.frameStats.jankPercent, ctx.frameStats.frameCount, ctx.profile.jankTriggerPercent
        )
    }
}
