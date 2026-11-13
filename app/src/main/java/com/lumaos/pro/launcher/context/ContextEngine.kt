package com.lumaos.pro.launcher.context

import android.content.Context
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.core.prefs.LumaPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A manual context choice that stands until the next scheduled transition.
 *
 * [untilMillis] is the epoch millis of the upcoming schedule boundary, or
 * [Long.MAX_VALUE] when no schedule is enabled (the choice then stands until
 * a schedule exists or the user resumes automatic switching). Persisted in
 * DataStore, so it survives process death and reboot.
 */
data class ManualOverride(val profile: ContextProfile, val untilMillis: Long) {
    fun isActive(nowMillis: Long): Boolean = nowMillis < untilMillis

    /** Milliseconds until expiry, or null when the override is intentionally indefinite. */
    internal fun millisUntilExpiry(nowMillis: Long): Long? = when {
        untilMillis == Long.MAX_VALUE -> null
        nowMillis >= untilMillis -> 0L
        else -> untilMillis - nowMillis
    }
}

/**
 * The context switching engine behind the home screen's four-option selector.
 *
 * One switching path for every trigger (selector menu, swipe gesture, voice
 * command, Settings, scheduled alarm): manual triggers record a [ManualOverride]
 * so the schedule does not immediately yank the launcher back, then activate
 * the profile through the same preference every observer already follows.
 *
 * State needs no explicit save step at switch time: home layouts, dock, pages,
 * folders, widgets, visibility and focus sets are all written through to
 * DataStore on every mutation, and the last visible page is persisted on every
 * page change. Switching therefore only has to (1) record the override,
 * (2) activate the profile, and (3) re-arm the schedule alarm. Home layout,
 * drawer visibility, wallpaper/theme and focus restrictions all follow from
 * the active-profile flows without restarting anything.
 *
 * Focus Protection's layer 1 intercepts launches made through LumaOS Pro.
 * Optional Accessibility-assisted layer 2 may react to supported foreground
 * package changes, but neither layer covers other launchers, lock-screen
 * shortcuts, widgets, in-app content, or an already-visible app in an unfocused
 * split-screen pane. Accessibility is not required for layer 1.
 */
object ContextEngine {

    /** Profiles that support optional Focus Protection. */
    val FOCUS_PROFILES: Set<ContextProfile> = setOf(ContextProfile.WORK, ContextProfile.STUDY)

    /** True while [active] runs an enabled focus session. */
    fun focusActive(active: ContextProfile, focusEnabled: Boolean): Boolean =
        active in FOCUS_PROFILES && focusEnabled

    /** True when launching [key] must be intercepted by the restriction screen. */
    fun isRestricted(
        active: ContextProfile,
        focusEnabled: Boolean,
        restricted: Set<ComponentKey>,
        key: ComponentKey
    ): Boolean = focusActive(active, focusEnabled) && key in restricted

    /**
     * Shortcut variant: shortcuts carry only a package + id, so a shortcut is
     * restricted when any restricted component belongs to its package.
     */
    fun isPackageRestricted(
        active: ContextProfile,
        focusEnabled: Boolean,
        restricted: Set<ComponentKey>,
        packageName: String
    ): Boolean = focusActive(active, focusEnabled) && restricted.any { it.packageName == packageName }

    /**
     * The documented resolution contract for a manual override, covered by
     * `ContextEngineTest` but unused in production. The production
     * `ContextScheduleManager.refreshLocked` path also recomputes the next
     * schedule deadline and re-arms its boundary alarm; this pure helper does
     * neither.
     *
     * Returns the effective profile plus whether a stored override has expired
     * (and should be cleared).
     */
    fun resolveEffective(
        scheduled: ContextProfile,
        override: ManualOverride?,
        nowMillis: Long
    ): Pair<ContextProfile, Boolean> {
        if (override == null) return scheduled to false
        return if (override.isActive(nowMillis)) override.profile to false else scheduled to true
    }

    /**
     * Manual switch (selector, gesture, voice, Settings). Records an override
     * until the next scheduled transition and activates [target]. The whole
     * sequence runs under the schedule mutex, so two triggers racing (a tap
     * plus an alarm, two rapid voice commands) resolve in order instead of
     * interleaving override, profile and alarm writes.
     */
    suspend fun switchManual(context: Context, prefs: LumaPrefs, target: ContextProfile) =
        ContextScheduleManager.switchMutex.withLock {
            val now = LocalDateTime.now()
            val schedules = prefs.profileSchedules.first()
            val until = ContextScheduleManager.nextBoundary(now, schedules)
                ?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: Long.MAX_VALUE
            prefs.setManualOverride(target, until)
            prefs.setActiveContextProfile(target)
            ContextScheduleManager.refreshLocked(context.applicationContext, prefs)
        }

    /** Drops the manual override and re-applies the schedule immediately. */
    suspend fun resumeSchedule(context: Context, prefs: LumaPrefs) =
        ContextScheduleManager.switchMutex.withLock {
            prefs.clearManualOverride()
            ContextScheduleManager.refreshLocked(context.applicationContext, prefs)
        }
}
