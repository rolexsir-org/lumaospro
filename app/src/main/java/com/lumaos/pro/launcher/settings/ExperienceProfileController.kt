package com.lumaos.pro.launcher.settings

import com.lumaos.pro.launcher.core.prefs.LumaPrefs
import com.lumaos.pro.launcher.ui.theme.ExperienceProfile

/**
 * Applies a coordinated appearance in one preference transaction. The caller
 * handles write failures; layouts, widgets and an explicitly chosen row count
 * are not changed by an experience switch.
 */
object ExperienceProfileController {
    suspend fun apply(prefs: LumaPrefs, profile: ExperienceProfile) {
        prefs.applyExperienceProfile(profile)
    }
}
