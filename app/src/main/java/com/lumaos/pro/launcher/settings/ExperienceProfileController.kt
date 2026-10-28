package com.lumaos.pro.launcher.settings

import com.lumaos.pro.launcher.core.prefs.LumaPrefs
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.ui.theme.ExperienceProfile
import kotlinx.coroutines.flow.first

/**
 * Applies an [ExperienceProfile] through the existing preference layer.
 *
 * The profile is a bundle of appearance settings only. This writer never
 * touches the home layout, widgets, folders, the dock contents or context
 * profiles, so switching identities cannot rearrange or erase user content.
 * Re-applying the same profile is also the supported "restore this profile's
 * defaults" action.
 */
object ExperienceProfileController {

    suspend fun apply(prefs: LumaPrefs, profile: ExperienceProfile) {
        runCatching {
            // Preset first: setHomePreset also adopts the preset's row count and
            // clears a dock-style override, so the profile's dock treatment must
            // be written afterwards to win deterministically.
            prefs.setHomePreset(profile.preset)
            prefs.setDockStyle(profile.dock)
            prefs.setEffectsPreference(profile.effects)
            prefs.setMotionPreference(profile.motion)
            prefs.applyThemeSettings(profile.themeSettings(prefs.themeSettings.first()))
            prefs.setExperienceProfile(profile)
        }.onFailure { LumaLog.e("Could not apply experience profile $profile", it) }
    }
}
