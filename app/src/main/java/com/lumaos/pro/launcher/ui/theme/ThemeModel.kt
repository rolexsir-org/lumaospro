package com.lumaos.pro.launcher.ui.theme

/**
 * LumaOS Pro theme model.
 *
 * A theme is more than a palette. Each [ThemeSpec] fixes five things that are
 * applied together across the home screen, drawer, menus, folders, widgets
 * frame, search, settings, dialogs and system bars:
 *
 *  1. colour seeds (background, accent, secondary, neutral tint) from which the
 *     engine derives a full surface hierarchy and interaction states;
 *  2. a typography voice ([TypeStyle]);
 *  3. an icon treatment ([IconShape] + [IconStyle]);
 *  4. an atmosphere - the backdrop composition LumaOS Pro paints itself
 *     ([Atmosphere]);
 *  5. a surface material ([SurfaceStyle]) and corner geometry.
 *
 * Everything here is plain data with no Android imports, so the derived
 * colours and their contrast guarantees are unit tested on the JVM.
 */
enum class ThemeId(val storageKey: String) {
    OBSIDIAN("obsidian"),
    GRAPHITE("graphite"),
    VECTOR("vector"),
    AURORA("aurora"),
    PORCELAIN("porcelain"),
    CHROMA("chroma");

    companion object {
        fun fromStorageKey(raw: String?): ThemeId? = entries.firstOrNull { it.storageKey == raw }
    }
}

/** Typographic voice. Families are platform fonts available on every Android 8+ device. */
enum class TypeStyle(
    val storageKey: String,
    /** Typeface family for the large clock numerals. */
    val clockFamily: String,
    /** Letter spacing (em) for the clock. */
    val clockTracking: Float,
    /** Family for app labels and body text. */
    val labelFamily: String,
    val labelTracking: Float,
    /** Family for small instrument readouts (date, battery, alarm). */
    val readoutFamily: String,
    /** Readouts rendered in capitals with open tracking. */
    val readoutCaps: Boolean,
    val readoutTracking: Float,
    /** Family for titles (drawer title, folder names, section headers). */
    val titleFamily: String
) {
    PRECISION("precision", "sans-serif-thin", -0.03f, "sans-serif", 0f, "sans-serif-condensed", true, 0.14f, "sans-serif-light"),
    GROTESK("grotesk", "sans-serif-medium", -0.055f, "sans-serif-medium", -0.01f, "sans-serif-medium", false, 0.01f, "sans-serif-medium"),
    SERIF("serif", "serif", -0.03f, "sans-serif", 0f, "serif", false, 0.02f, "serif"),
    MONO("mono", "monospace", -0.06f, "sans-serif-condensed", 0.02f, "monospace", true, 0.12f, "monospace");

    companion object {
        fun fromStorageKey(raw: String?): TypeStyle? = entries.firstOrNull { it.storageKey == raw }
    }
}

/** Mask applied to every app icon. SYSTEM keeps the device's own adaptive-icon mask. */
enum class IconShape(val storageKey: String) {
    SYSTEM("system"),
    SQUIRCLE("squircle"),
    CIRCLE("circle"),
    ROUNDED("rounded"),
    TEARDROP("teardrop"),
    OCTAGON("octagon");

    companion object {
        fun fromStorageKey(raw: String?): IconShape? = entries.firstOrNull { it.storageKey == raw }
    }
}

/**
 * How icon artwork is presented.
 *  - NATURAL: the app's own colours inside the chosen mask.
 *  - PLATE: artwork seated on a theme-coloured plate with a fine rim - unifies
 *    mismatched legacy icons.
 *  - TINTED: the app's monochrome layer (Android 13+ adaptive icons) drawn in
 *    the theme accent; apps without one fall back to a desaturated plate.
 */
enum class IconStyle(val storageKey: String) {
    NATURAL("natural"),
    PLATE("plate"),
    TINTED("tinted");

    companion object {
        fun fromStorageKey(raw: String?): IconStyle? = entries.firstOrNull { it.storageKey == raw }
    }
}

/** Backdrop compositions painted by LumaOS Pro itself (see [AtmospherePlan]). */
enum class Atmosphere { NONE, HORIZON, AURORA, GRID, HALO, TINT }

/** Material of elevated surfaces (drawer sheet, menus, folders, dock). */
enum class SurfaceStyle { SOLID, TRANSLUCENT, GLASS }

/** What sits behind the home screen. */
enum class Backdrop(val storageKey: String) {
    /** The system wallpaper, with legibility gradients only. */
    WALLPAPER("wallpaper"),
    /** LumaOS Pro's own atmospheric background; the wallpaper is not composited. */
    THEME("theme");

    companion object {
        fun fromStorageKey(raw: String?): Backdrop? = entries.firstOrNull { it.storageKey == raw }
    }
}

data class ThemeSpec(
    val id: ThemeId,
    val displayName: String,
    val tagline: String,
    val dark: Boolean,
    /** True-black surfaces for OLED panels. */
    val amoled: Boolean,
    val background: Int,
    val accent: Int,
    val secondary: Int,
    /** Hue source for neutral surfaces. */
    val neutralTint: Int,
    /** OKLCH chroma for neutral surfaces (0 = pure grey). */
    val neutralChroma: Double,
    val typography: TypeStyle,
    val iconShape: IconShape,
    val iconStyle: IconStyle,
    val atmosphere: Atmosphere,
    val surfaceStyle: SurfaceStyle,
    val defaultBackdrop: Backdrop,
    /** Multiplier for every corner radius (sharper < 1 < softer). */
    val cornerScale: Float
)

/** Complete derived colour set. Every on-colour is contrast-checked against its surface. */
data class ThemeColors(
    val isDark: Boolean,
    /** Opaque base: settings background and the THEME backdrop. */
    val background: Int,
    val onBackground: Int,
    /** Surface hierarchy, lowest to highest elevation. */
    val surface0: Int,
    val surface1: Int,
    val surface2: Int,
    val surface3: Int,
    val onSurface: Int,
    val onSurfaceMuted: Int,
    val onSurfaceFaint: Int,
    val outline: Int,
    val outlineStrong: Int,
    val accent: Int,
    val onAccent: Int,
    val accentContainer: Int,
    val onAccentContainer: Int,
    val secondary: Int,
    /** Accent at low alpha for halos and focus glows. */
    val glow: Int,
    val scrim: Int,
    /** Text and glyphs drawn directly over the backdrop. */
    val homeForeground: Int,
    val homeMuted: Int,
    val homeShadow: Int,
    /** Interaction states. */
    val pressedOverlay: Int,
    val selectedFill: Int,
    val focusRing: Int,
    val ripple: Int,
    val danger: Int
) {
    companion object {
        /** Animated theme changes interpolate every role at once. */
        fun lerp(a: ThemeColors, b: ThemeColors, t: Float): ThemeColors {
            fun c(x: Int, y: Int) = ColorMath.lerp(x, y, t)
            return ThemeColors(
                isDark = if (t < 0.5f) a.isDark else b.isDark,
                background = c(a.background, b.background),
                onBackground = c(a.onBackground, b.onBackground),
                surface0 = c(a.surface0, b.surface0),
                surface1 = c(a.surface1, b.surface1),
                surface2 = c(a.surface2, b.surface2),
                surface3 = c(a.surface3, b.surface3),
                onSurface = c(a.onSurface, b.onSurface),
                onSurfaceMuted = c(a.onSurfaceMuted, b.onSurfaceMuted),
                onSurfaceFaint = c(a.onSurfaceFaint, b.onSurfaceFaint),
                outline = c(a.outline, b.outline),
                outlineStrong = c(a.outlineStrong, b.outlineStrong),
                accent = c(a.accent, b.accent),
                onAccent = c(a.onAccent, b.onAccent),
                accentContainer = c(a.accentContainer, b.accentContainer),
                onAccentContainer = c(a.onAccentContainer, b.onAccentContainer),
                secondary = c(a.secondary, b.secondary),
                glow = c(a.glow, b.glow),
                scrim = c(a.scrim, b.scrim),
                homeForeground = c(a.homeForeground, b.homeForeground),
                homeMuted = c(a.homeMuted, b.homeMuted),
                homeShadow = c(a.homeShadow, b.homeShadow),
                pressedOverlay = c(a.pressedOverlay, b.pressedOverlay),
                selectedFill = c(a.selectedFill, b.selectedFill),
                focusRing = c(a.focusRing, b.focusRing),
                ripple = c(a.ripple, b.ripple),
                danger = c(a.danger, b.danger)
            )
        }
    }
}

/** A theme after overrides and environment have been applied: what is actually drawn. */
data class ResolvedTheme(
    val id: ThemeId,
    val displayName: String,
    val colors: ThemeColors,
    val typography: TypeStyle,
    val iconShape: IconShape,
    val iconStyle: IconStyle,
    val atmosphere: Atmosphere,
    val surfaceStyle: SurfaceStyle,
    val backdrop: Backdrop,
    val cornerScale: Float
) {
    val isDark: Boolean get() = colors.isDark

    /** Corner radius in dp for a given base radius, scaled by the theme's geometry. */
    fun corner(baseDp: Float): Float = baseDp * cornerScale

    /** Alpha applied to elevated surfaces for the theme's material. */
    fun surfaceAlpha(effectsAllowTranslucency: Boolean): Float = when {
        !effectsAllowTranslucency -> 1f
        surfaceStyle == SurfaceStyle.SOLID -> 1f
        surfaceStyle == SurfaceStyle.TRANSLUCENT -> 0.92f
        else -> 0.84f
    }
}

/** Seed colours reported by WallpaperManager (API 27+). */
data class WallpaperSeeds(
    val primary: Int,
    val secondary: Int? = null,
    val tertiary: Int? = null,
    /** WallpaperColors.HINT_SUPPORTS_DARK_TEXT: the wallpaper is light. */
    val supportsDarkText: Boolean? = null
)

object ThemePresets {

    val OBSIDIAN = ThemeSpec(
        id = ThemeId.OBSIDIAN,
        displayName = "Obsidian",
        tagline = "True black for OLED. No glow, lowest power.",
        dark = true,
        amoled = true,
        background = 0xFF000000.toInt(),
        accent = 0xFFB5C5A9.toInt(),
        secondary = 0xFFE8EBE4.toInt(),
        neutralTint = 0xFFB5C5A9.toInt(),
        neutralChroma = 0.006,
        typography = TypeStyle.PRECISION,
        iconShape = IconShape.SYSTEM,
        iconStyle = IconStyle.NATURAL,
        atmosphere = Atmosphere.NONE,
        surfaceStyle = SurfaceStyle.SOLID,
        defaultBackdrop = Backdrop.THEME,
        cornerScale = 1.0f
    )

    val GRAPHITE = ThemeSpec(
        id = ThemeId.GRAPHITE,
        displayName = "Graphite",
        tagline = "Machined greys lit by an amber instrument glow.",
        dark = true,
        amoled = false,
        background = 0xFF101214.toInt(),
        accent = 0xFFE2B66B.toInt(),
        secondary = 0xFF9FB4C7.toInt(),
        neutralTint = 0xFF8FA3B5.toInt(),
        neutralChroma = 0.010,
        typography = TypeStyle.GROTESK,
        iconShape = IconShape.SQUIRCLE,
        iconStyle = IconStyle.NATURAL,
        atmosphere = Atmosphere.HORIZON,
        surfaceStyle = SurfaceStyle.TRANSLUCENT,
        defaultBackdrop = Backdrop.WALLPAPER,
        cornerScale = 1.0f
    )

    val VECTOR = ThemeSpec(
        id = ThemeId.VECTOR,
        displayName = "Vector",
        tagline = "Cyber instrumentation: fine lines, signal colour used sparingly.",
        dark = true,
        amoled = false,
        background = 0xFF05070D.toInt(),
        accent = 0xFF3DF2E0.toInt(),
        secondary = 0xFFFF4D9D.toInt(),
        neutralTint = 0xFF3D6BFF.toInt(),
        neutralChroma = 0.028,
        typography = TypeStyle.MONO,
        iconShape = IconShape.OCTAGON,
        iconStyle = IconStyle.PLATE,
        atmosphere = Atmosphere.GRID,
        surfaceStyle = SurfaceStyle.GLASS,
        defaultBackdrop = Backdrop.THEME,
        cornerScale = 0.55f
    )

    val AURORA = ThemeSpec(
        id = ThemeId.AURORA,
        displayName = "Aurora",
        tagline = "Polar night with slow, drifting light.",
        dark = true,
        amoled = false,
        background = 0xFF060A14.toInt(),
        accent = 0xFF6EE7B7.toInt(),
        secondary = 0xFFA78BFA.toInt(),
        neutralTint = 0xFF5B7BB5.toInt(),
        neutralChroma = 0.024,
        typography = TypeStyle.PRECISION,
        iconShape = IconShape.SQUIRCLE,
        iconStyle = IconStyle.NATURAL,
        atmosphere = Atmosphere.AURORA,
        surfaceStyle = SurfaceStyle.GLASS,
        defaultBackdrop = Backdrop.THEME,
        cornerScale = 1.25f
    )

    val PORCELAIN = ThemeSpec(
        id = ThemeId.PORCELAIN,
        displayName = "Porcelain",
        tagline = "Warm minimal light. Serif time, ink-on-paper contrast.",
        dark = false,
        amoled = false,
        background = 0xFFF4F2EC.toInt(),
        accent = 0xFF3F5B45.toInt(),
        secondary = 0xFFA3654A.toInt(),
        neutralTint = 0xFF8C8A7E.toInt(),
        neutralChroma = 0.010,
        typography = TypeStyle.SERIF,
        iconShape = IconShape.ROUNDED,
        iconStyle = IconStyle.PLATE,
        atmosphere = Atmosphere.HALO,
        surfaceStyle = SurfaceStyle.SOLID,
        defaultBackdrop = Backdrop.THEME,
        cornerScale = 1.1f
    )

    /** Seeds are replaced by wallpaper colours at runtime; these apply when none are available. */
    val CHROMA = ThemeSpec(
        id = ThemeId.CHROMA,
        displayName = "Chroma",
        tagline = "Built live from your wallpaper's colours.",
        dark = true,
        amoled = false,
        background = 0xFF11151B.toInt(),
        accent = 0xFF9CC2E8.toInt(),
        secondary = 0xFFE0B8D8.toInt(),
        neutralTint = 0xFF7C9CBF.toInt(),
        neutralChroma = 0.018,
        typography = TypeStyle.GROTESK,
        iconShape = IconShape.SYSTEM,
        iconStyle = IconStyle.NATURAL,
        atmosphere = Atmosphere.TINT,
        surfaceStyle = SurfaceStyle.TRANSLUCENT,
        defaultBackdrop = Backdrop.WALLPAPER,
        cornerScale = 1.0f
    )

    val ALL: List<ThemeSpec> = listOf(OBSIDIAN, GRAPHITE, VECTOR, AURORA, PORCELAIN, CHROMA)

    fun of(id: ThemeId): ThemeSpec = ALL.first { it.id == id }

    /** Curated accent swatches offered in the Studio (plus the theme's own). */
    val ACCENT_SWATCHES: List<Int> = listOf(
        0xFFB5C5A9.toInt(), // LumaOS Pro sage
        0xFFE2B66B.toInt(), // amber
        0xFF3DF2E0.toInt(), // signal cyan
        0xFF6EE7B7.toInt(), // aurora green
        0xFF8AB4F8.toInt(), // sky
        0xFFA78BFA.toInt(), // violet
        0xFFFF8FA3.toInt(), // coral
        0xFFF5F5F0.toInt(), // chalk
        0xFF3F5B45.toInt(), // deep sage
        0xFF1F4E79.toInt(), // ink blue
        0xFF8C3B2F.toInt()  // oxide
    )

    /** Curated background swatches for the THEME backdrop. */
    val BACKGROUND_SWATCHES: List<Int> = listOf(
        0xFF000000.toInt(),
        0xFF101214.toInt(),
        0xFF0B1220.toInt(),
        0xFF14110F.toInt(),
        0xFF0F1A14.toInt(),
        0xFF1A1020.toInt(),
        0xFFF4F2EC.toInt(),
        0xFFEEF1F5.toInt(),
        0xFFF3EDE4.toInt()
    )
}
