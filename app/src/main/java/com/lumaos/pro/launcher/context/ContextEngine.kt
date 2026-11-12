package com.lumaos.pro.launcher.context

import android.content.Context
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.core.prefs.LumaPrefs
import kotlinx.coroutines.flow.first
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
 * Focus enforcement is launcher-level only: LumaOS Pro intercepts launches
 * made through its own surfaces and shows the native restriction screen. It
 * does not claim to block content inside third-party apps, and it needs no
 * Accessibility Service, device admin, or usage-access permission.
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
     * The profile in force right now. Returns the effective profile plus
     * whether a stored override has expired (and should be cleared).
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
     * until the next scheduled transition and activates [target].
     */
    suspend fun switchManual(context: Context, prefs: LumaPrefs, target: ContextProfile) {
        val now = LocalDateTime.now()
        val schedules = prefs.profileSchedules.first()
        val until = ContextScheduleManager.nextBoundary(now, schedules)
            ?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            ?: Long.MAX_VALUE
        prefs.setManualOverride(target, until)
        prefs.setActiveContextProfile(target)
        ContextScheduleManager.refresh(context.applicationContext, prefs)
    }

    /** Drops the manual override and re-applies the schedule immediately. */
    suspend fun resumeSchedule(context: Context, prefs: LumaPrefs) {
        prefs.clearManualOverride()
        ContextScheduleManager.refresh(context.applicationContext, prefs)
    }
}
