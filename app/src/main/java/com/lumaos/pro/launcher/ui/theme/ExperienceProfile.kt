package com.lumaos.pro.launcher.ui.theme

import com.lumaos.pro.launcher.effects.EffectsPreference
import com.lumaos.pro.launcher.launcher.model.DockStyle
import com.lumaos.pro.launcher.launcher.model.HomePreset
import com.lumaos.pro.launcher.motion.MotionPreference

/**
 * The three first-class LumaOS Pro experiences.
 *
 * A profile is a *coordinated bundle* of existing settings - a theme identity,
 * a backdrop treatment, an effects tier, a motion policy, a dock treatment and
 * a home composition preset - not a parallel theme system. Applying one writes
 * only those appearance preferences; the saved layout, widgets, folders, dock
 * contents and context profiles are never touched. Users can then fine-tune
 * any individual control in the Studio without leaving the profile.
 *
 * Pure data with no Android imports so the bundles are unit tested on the JVM.
 */
enum class ExperienceProfile(
    val storageKey: String,
    val themeId: ThemeId,
    val backdrop: Backdrop,
    val effects: EffectsPreference,
    val motion: MotionPreference,
    val dock: DockStyle,
    val preset: HomePreset,
    val dark: Boolean
) {
    /**
     * Quiet sophistication: the light Zen identity, solid legible surfaces,
     * no blur, restrained motion and an undecorated dock.
     */
    PURE(
        storageKey = "pure",
        themeId = ThemeId.PORCELAIN,
        backdrop = Backdrop.LUMA,
        effects = EffectsPreference.MINIMAL,
        motion = MotionPreference.FULL,
        dock = DockStyle.MINIMAL,
        preset = HomePreset.HORIZON,
        dark = false
    ),

    /**
     * The signature LumaOS Pro experience: layered translucent Luma Glass over
     * the atmospheric Signature identity, with the floating glass dock.
     */
    LIQUID(
        storageKey = "liquid",
        themeId = ThemeId.SIGNATURE,
        backdrop = Backdrop.THEME,
        effects = EffectsPreference.AUTO,
        motion = MotionPreference.FULL,
        dock = DockStyle.FLOAT,
        preset = HomePreset.HORIZON,
        dark = true
    ),

    /**
     * Cinematic darkness: the wallpaper-led Atmosphere identity, the deepest
     * blur budget and restrained highlights for an immersive night surface.
     */
    MIDNIGHT(
        storageKey = "midnight",
        themeId = ThemeId.ATMOSPHERE,
        backdrop = Backdrop.WALLPAPER,
        effects = EffectsPreference.AUTO,
        motion = MotionPreference.FULL,
        dock = DockStyle.FLOAT,
        preset = HomePreset.HORIZON,
        dark = true
    );

    /**
     * Builds the [ThemeSettings] for this profile on top of the [base] the user
     * currently has. Only identity-level fields are reset so the profile reads
     * coherently; per-context theme overrides are left intact for the advanced
     * users who set them.
     */
    fun themeSettings(base: ThemeSettings): ThemeSettings = base.copy(
        themeId = themeId,
        switching = ThemeSwitching.FIXED,
        darkThemeId = themeId,
        lightThemeId = themeId,
        accentOverride = null,
        backgroundOverride = null,
        typography = null,
        iconShape = null,
        iconStyle = null,
        backdrop = backdrop
    )

    companion object {
        /** New installations start in the flagship Liquid experience. */
        val DEFAULT = LIQUID

        fun fromStorageKey(raw: String?): ExperienceProfile? =
            entries.firstOrNull { it.storageKey == raw }
    }
}
