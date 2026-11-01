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
        backdrop = backdrop,
        dynamicWallpaperColors = false
    )

    companion object {
        /** New installations start in the flagship Liquid experience. */
        val DEFAULT = LIQUID

        fun fromStorageKey(raw: String?): ExperienceProfile? =
            entries.firstOrNull { it.storageKey == raw }
    }
}

/**
 * Complete launcher experience package for each [ThemeId] in Design System v3.2.
 *
 * Selecting a theme in LumaOS Pro transforms the entire launcher — home
 * composition, clock/date layout, dock geometry, glass & blur budget, icon
 * treatment, typography voice, backdrop atmosphere, and motion physics —
 * rather than merely swapping colour tokens.
 */
data class ThemeLauncherPackage(
    val themeId: ThemeId,
    val subtitle: String,
    val preset: HomePreset,
    val dock: DockStyle,
    val backdrop: Backdrop,
    val effects: EffectsPreference,
    val motion: MotionPreference,
    val iconShape: IconShape,
    val iconStyle: IconStyle?,
    val typography: TypeStyle?,
    val dynamicWallpaperColors: Boolean,
    val livingIcons: Boolean = iconStyle != IconStyle.TINTED,
    /** Concise summary of the active launcher effects & composition shown in Settings & Studio. */
    val effectsSummary: String
) {
    val dockStyle: DockStyle get() = dock

    /**
     * Produces the pure, un-overridden [ThemeSettings] for this theme's launcher package.
     */
    fun toThemeSettings(base: ThemeSettings = ThemeSettings()): ThemeSettings =
        base.withPreset(themeId).copy(
            themeId = themeId,
            switching = ThemeSwitching.FIXED,
            accentOverride = null,
            backgroundOverride = null,
            typography = typography,
            iconShape = iconShape,
            iconStyle = iconStyle,
            backdrop = backdrop,
            dynamicWallpaperColors = dynamicWallpaperColors
        )

    companion object {
        val SIGNATURE = ThemeLauncherPackage(
            themeId = ThemeId.SIGNATURE,
            subtitle = "Obsidian Glass",
            preset = HomePreset.INSTRUMENT,
            dock = DockStyle.FLOAT,
            backdrop = Backdrop.THEME,
            effects = EffectsPreference.FULL,
            motion = MotionPreference.FULL,
            iconShape = IconShape.SQUIRCLE,
            iconStyle = null,
            typography = null,
            dynamicWallpaperColors = false,
            livingIcons = true,
            effectsSummary = "26dp Glass · Floating Dock · Precision Clock"
        )

        val ATMOSPHERE = ThemeLauncherPackage(
            themeId = ThemeId.ATMOSPHERE,
            subtitle = "Luminous Adaptive",
            preset = HomePreset.HORIZON,
            dock = DockStyle.FLOAT,
            backdrop = Backdrop.WALLPAPER,
            effects = EffectsPreference.FULL,
            motion = MotionPreference.FULL,
            iconShape = IconShape.CIRCLE,
            iconStyle = null,
            typography = null,
            dynamicWallpaperColors = true,
            livingIcons = true,
            effectsSummary = "34dp Adaptive Glass · Aurora Drift · Horizon"
        )

        val MONOLITH = ThemeLauncherPackage(
            themeId = ThemeId.MONOLITH,
            subtitle = "Editorial Stone",
            preset = HomePreset.GRID,
            dock = DockStyle.BEZEL,
            backdrop = Backdrop.THEME,
            effects = EffectsPreference.MINIMAL,
            motion = MotionPreference.FULL,
            iconShape = IconShape.ROUNDED,
            iconStyle = IconStyle.TINTED,
            typography = null,
            dynamicWallpaperColors = false,
            livingIcons = false,
            effectsSummary = "Zero-Blur Stone · Edge Bezel · Monograph 84sp"
        )

        fun forTheme(id: ThemeId): ThemeLauncherPackage = when (id) {
            ThemeId.SIGNATURE -> SIGNATURE
            ThemeId.ATMOSPHERE -> ATMOSPHERE
            ThemeId.MONOLITH -> MONOLITH
            ThemeId.AURORA -> ThemeLauncherPackage(
                themeId = ThemeId.AURORA,
                subtitle = "Midnight Aurora",
                preset = HomePreset.INSTRUMENT,
                dock = DockStyle.FLOAT,
                backdrop = Backdrop.THEME,
                effects = EffectsPreference.FULL,
                motion = MotionPreference.FULL,
                iconShape = IconShape.SQUIRCLE,
                iconStyle = null,
                typography = null,
                dynamicWallpaperColors = false,
                livingIcons = true,
                effectsSummary = "26dp Glass · Floating Dock · Aurora Glow"
            )
            ThemeId.PORCELAIN -> ThemeLauncherPackage(
                themeId = ThemeId.PORCELAIN,
                subtitle = "Warm Paper",
                preset = HomePreset.ZEN,
                dock = DockStyle.MINIMAL,
                backdrop = Backdrop.THEME,
                effects = EffectsPreference.MINIMAL,
                motion = MotionPreference.REDUCED,
                iconShape = IconShape.CIRCLE,
                iconStyle = null,
                typography = null,
                dynamicWallpaperColors = false,
                livingIcons = true,
                effectsSummary = "Paper Surface · Minimal Dock · Serene"
            )
            ThemeId.VECTOR -> ThemeLauncherPackage(
                themeId = ThemeId.VECTOR,
                subtitle = "Technical Grid",
                preset = HomePreset.GRID,
                dock = DockStyle.BEZEL,
                backdrop = Backdrop.THEME,
                effects = EffectsPreference.BALANCED,
                motion = MotionPreference.FULL,
                iconShape = IconShape.OCTAGON,
                iconStyle = IconStyle.TINTED,
                typography = null,
                dynamicWallpaperColors = false,
                livingIcons = false,
                effectsSummary = "Chamfer Grid · Bezel Dock · Technical Mono"
            )
            ThemeId.OBSIDIAN -> ThemeLauncherPackage(
                themeId = ThemeId.OBSIDIAN,
                subtitle = "True-Black OLED",
                preset = HomePreset.ZEN,
                dock = DockStyle.MINIMAL,
                backdrop = Backdrop.THEME,
                effects = EffectsPreference.MINIMAL,
                motion = MotionPreference.REDUCED,
                iconShape = IconShape.SQUIRCLE,
                iconStyle = IconStyle.TINTED,
                typography = null,
                dynamicWallpaperColors = false,
                livingIcons = false,
                effectsSummary = "True-Black AMOLED · Zero Blur · Minimal"
            )
            ThemeId.GRAPHITE -> ThemeLauncherPackage(
                themeId = ThemeId.GRAPHITE,
                subtitle = "Neutral Slate",
                preset = HomePreset.GRID,
                dock = DockStyle.BEZEL,
                backdrop = Backdrop.THEME,
                effects = EffectsPreference.BALANCED,
                motion = MotionPreference.FULL,
                iconShape = IconShape.ROUNDED,
                iconStyle = IconStyle.PLATE,
                typography = null,
                dynamicWallpaperColors = false,
                livingIcons = true,
                effectsSummary = "Neutral Slate · Bezel Dock · Grotesk"
            )
            ThemeId.CHROMA -> ThemeLauncherPackage(
                themeId = ThemeId.CHROMA,
                subtitle = "Wallpaper Adaptive",
                preset = HomePreset.HORIZON,
                dock = DockStyle.FLOAT,
                backdrop = Backdrop.WALLPAPER,
                effects = EffectsPreference.FULL,
                motion = MotionPreference.FULL,
                iconShape = IconShape.TEARDROP,
                iconStyle = null,
                typography = null,
                dynamicWallpaperColors = true,
                livingIcons = true,
                effectsSummary = "Wallpaper Adaptive · Floating Dock · Horizon"
            )
        }
    }
}
