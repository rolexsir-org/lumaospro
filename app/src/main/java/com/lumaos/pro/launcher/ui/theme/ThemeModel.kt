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
/**
 * Selectable identities.
 *
 * The first three are Design System v3.2. The rest are the pre-3.2 palettes,
 * kept verbatim - same storage keys, same geometry, same voices - so that an
 * upgrade never resets a saved choice or rewrites an exported backup. No theme is
 * removed; ordering only decides what Studio shows first.
 */
enum class ThemeId(val storageKey: String) {
    /** v3.2 - refined, timeless, expensive. */
    SIGNATURE("signature"),
    /** v3.2 - immersive, cinematic, adaptive. */
    ATMOSPHERE("atmosphere"),
    /** v3.2 - architectural, bold, exact. Blur is 0 by design. */
    MONOLITH("monolith"),
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

/**
 * What sits behind the home screen.
 *
 * THEME paints LumaOS Pro's own atmosphere (no wallpaper compositing, the
 * cheapest and most power-efficient choice). WALLPAPER shows the user's system
 * wallpaper with legibility gradients. LUMA shows the art-directed wallpaper
 * bundled with the active signature theme, cropped rather than stretched.
 */
enum class Backdrop(val storageKey: String) {
    /** The system wallpaper, with legibility gradients only. */
    WALLPAPER("wallpaper"),
    /** LumaOS Pro's own atmospheric background; the wallpaper is not composited. */
    THEME("theme"),
    /** A bundled, art-directed LumaOS wallpaper for the active theme. */
    LUMA("luma");

    /** True when a photographic surface (system or bundled art) is behind the UI. */
    val usesArtwork: Boolean get() = this != THEME

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
    /** How this theme moves; see [MotionProfile]. */
    val motionCharacter: MotionCharacter,
    val iconShape: IconShape,
    val iconStyle: IconStyle,
    val atmosphere: Atmosphere,
    val surfaceStyle: SurfaceStyle,
    val defaultBackdrop: Backdrop,
    /** Multiplier for every corner radius (sharper < 1 < softer). */
    val cornerScale: Float,
    /**
     * Authored v3.2 tokens for this identity, or null for the retained pre-3.2
     * palettes (whose colours are derived from the seeds above).
     *
     * Declared last and defaulted so every existing preset stays source-compatible.
     */
    val tokens: ThemeTokens? = null
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
    val danger: Int,
    /**
     * Authored `ok` / `warn` roles from a v3.2 bundle, when the theme has them.
     * Null for the retained palettes, whose status colours stay hue-derived.
     */
    val successSeed: Int? = null,
    val warningSeed: Int? = null
) {
    /**
     * Shadow tint for elevated glass surfaces. Derived from the themed scrim
     * rather than stored as another constructor role, so existing persisted
     * palettes and every [lerp] stay source- and data-compatible while each
     * identity keeps its own shadow colour.
     */
    val shadowColor: Int get() = scrim

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
                danger = c(a.danger, b.danger),
                // A status seed is a role identity, not a gradient: it snaps at the
                // midpoint instead of being interpolated into an undefined hue.
                successSeed = if (t < 0.5f) a.successSeed else b.successSeed,
                warningSeed = if (t < 0.5f) a.warningSeed else b.warningSeed
            )
        }
    }
}

/**
 * Corner roles a component may ask for. A screen never passes a raw dp value:
 * it asks for the role and the active theme answers with its authored radius.
 */
enum class RadiusRole { EXTRA_LARGE, LARGE, MEDIUM, SMALL, EXTRA_SMALL, DOCK, TILE }

/** A theme after overrides and environment have been applied: what is actually drawn. */
data class ResolvedTheme(
    val id: ThemeId,
    val displayName: String,
    val colors: ThemeColors,
    val typography: TypeStyle,
    val motionCharacter: MotionCharacter,
    val iconShape: IconShape,
    val iconStyle: IconStyle,
    val atmosphere: Atmosphere,
    val surfaceStyle: SurfaceStyle,
    val backdrop: Backdrop,
    val cornerScale: Float,
    /** Authored v3.2 tokens; null for a retained pre-3.2 palette. */
    val tokens: ThemeTokens? = null
) {
    val isDark: Boolean get() = colors.isDark

    /** Semantic roles, including status/disabled/overlay colours, derived from [colors]. */
    val semantics: SemanticColors by lazy(LazyThreadSafetyMode.NONE) { SemanticPalette.of(colors) }

    /** Motion tokens for this theme's character. */
    val motionProfile: MotionProfile get() = LumaMotionProfiles.of(motionCharacter)

    /** Corner radius in dp for a given base radius, scaled by the theme's geometry. */
    fun corner(baseDp: Float): Float = baseDp * cornerScale

    /**
     * Corner radius in dp for a design-system role.
     *
     * v3.2 themes answer with their authored token (34/26/16/10/6 for Signature,
     * 38/30/20/12/8 for Atmosphere, 8/4/2/2/2 for Monolith). Retained palettes
     * answer with the pre-3.2 base radius scaled by their geometry, so old themes
     * keep exactly the shape they had before.
     */
    fun radius(role: RadiusRole): Float {
        tokens?.shape?.let { shape ->
            return when (role) {
                RadiusRole.EXTRA_LARGE -> shape.extraLarge
                RadiusRole.LARGE -> shape.large
                RadiusRole.MEDIUM -> shape.medium
                RadiusRole.SMALL -> shape.small
                RadiusRole.EXTRA_SMALL -> shape.extraSmall
                RadiusRole.DOCK -> shape.dockRadius
                RadiusRole.TILE -> shape.tileRadius
            }
        }
        val base = when (role) {
            RadiusRole.EXTRA_LARGE -> 24f
            RadiusRole.LARGE -> 22f
            RadiusRole.MEDIUM -> 16f
            RadiusRole.SMALL -> 10f
            RadiusRole.EXTRA_SMALL -> 6f
            RadiusRole.DOCK -> 24f
            RadiusRole.TILE -> 16f
        }
        return base * cornerScale
    }

    /**
     * Blur radius this theme's material calls for, in dp.
     *
     * Blur is a material value, not a global preference: Signature asks for 26,
     * Atmosphere 34 and Monolith 0. Retained palettes keep the launcher's
     * historical 26 dp ceiling.
     */
    val blurDp: Float get() = tokens?.material?.blurDp ?: LEGACY_BLUR_DP

    /** Multiplier for real-time blur radii; 0 disables blur entirely for this theme. */
    val blurFactor: Float get() = (blurDp / LEGACY_BLUR_DP).coerceIn(0f, 2f)

    /** False when the theme's material excludes blur (Monolith). */
    val usesBlur: Boolean get() = blurDp > 0f

    /** Saturation multiplier applied to blurred backdrop content. 1 = untouched. */
    val saturation: Float get() = tokens?.material?.saturation ?: 1f

    /** App-label size for this theme, in sp. */
    val appLabelSp: Float get() = tokens?.appLabelSp ?: typeScale.appLabel

    /**
     * Home clock size for this theme, in sp.
     *
     * v3.2 authors 64 / 58 / 84 for Signature / Atmosphere / Monolith, which is
     * intentionally not the shared 66 sp scale entry: the clock is the one role a
     * theme may resize. Retained palettes answer from their voice.
     */
    val clockSp: Float get() = tokens?.clockSp ?: typography.clockSizeSp

    /** The ten-role type ladder in force (v3.2 set, or the shared fallback). */
    val typeScale: TokenTypeScale get() = tokens?.type ?: LumaDesignTokens.TYPE_SCALE

    companion object {
        /** Historical blur ceiling; also the baseline the token ratio is measured against. */
        const val LEGACY_BLUR_DP = 26f
    }

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

    // The pre-3.2 presets are kept exactly as they shipped. They are the
    // compatibility half of this file: storage keys, seeds, corner scales and
    // voices here must not change, because a database row or an exported backup
    // may still name them.

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
        motionCharacter = MotionCharacter.SUBTLE,
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
        motionCharacter = MotionCharacter.SUBTLE,
        iconShape = IconShape.SQUIRCLE,
        iconStyle = IconStyle.NATURAL,
        atmosphere = Atmosphere.HORIZON,
        surfaceStyle = SurfaceStyle.TRANSLUCENT,
        defaultBackdrop = Backdrop.WALLPAPER,
        cornerScale = 1.0f
    )

    /**
     * Void keeps the historic `vector` storage key so existing preferences and
     * exported backups retain their selected theme across this visual refresh.
     */
    val VOID = ThemeSpec(
        id = ThemeId.VECTOR,
        displayName = "Void",
        tagline = "Indigo depth, cyan light, and a controlled magenta signal.",
        dark = true,
        amoled = false,
        background = 0xFF080815.toInt(),
        accent = 0xFF69E2F2.toInt(),
        secondary = 0xFFE28BCB.toInt(),
        neutralTint = 0xFF5857B8.toInt(),
        neutralChroma = 0.032,
        typography = TypeStyle.GROTESK,
        motionCharacter = MotionCharacter.EXPRESSIVE,
        iconShape = IconShape.OCTAGON,
        iconStyle = IconStyle.PLATE,
        atmosphere = Atmosphere.GRID,
        surfaceStyle = SurfaceStyle.GLASS,
        defaultBackdrop = Backdrop.LUMA,
        cornerScale = 0.82f
    )

    /** Backwards-compatible source name; the persisted ID is still `vector`. */
    val VECTOR: ThemeSpec get() = VOID

    val AURORA = ThemeSpec(
        id = ThemeId.AURORA,
        displayName = "Aurora",
        tagline = "Midnight glass, brushed with a cool polar-blue glow.",
        dark = true,
        amoled = false,
        background = 0xFF080D1A.toInt(),
        accent = 0xFF9AD3FF.toInt(),
        secondary = 0xFFC5DDF4.toInt(),
        neutralTint = 0xFF5B7592.toInt(),
        neutralChroma = 0.016,
        typography = TypeStyle.PRECISION,
        motionCharacter = MotionCharacter.SUBTLE,
        iconShape = IconShape.SQUIRCLE,
        iconStyle = IconStyle.NATURAL,
        atmosphere = Atmosphere.AURORA,
        surfaceStyle = SurfaceStyle.GLASS,
        defaultBackdrop = Backdrop.LUMA,
        cornerScale = 1.0f
    )

    /**
     * Zen keeps the historic `porcelain` storage key for preference/backup
     * compatibility while presenting the new signature identity.
     */
    val ZEN = ThemeSpec(
        id = ThemeId.PORCELAIN,
        displayName = "Zen",
        tagline = "Quiet paper neutrals, soft sage, and blue-grey clarity.",
        dark = false,
        amoled = false,
        background = 0xFFF2F4F1.toInt(),
        accent = 0xFF526B60.toInt(),
        secondary = 0xFF637E87.toInt(),
        neutralTint = 0xFF83918A.toInt(),
        neutralChroma = 0.008,
        typography = TypeStyle.SERENE,
        motionCharacter = MotionCharacter.QUIET,
        iconShape = IconShape.ROUNDED,
        iconStyle = IconStyle.PLATE,
        atmosphere = Atmosphere.HALO,
        surfaceStyle = SurfaceStyle.SOLID,
        defaultBackdrop = Backdrop.LUMA,
        cornerScale = 1.08f
    )

    /** Backwards-compatible source name; the persisted ID is still `porcelain`. */
    val PORCELAIN: ThemeSpec get() = ZEN

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
        typography = TypeStyle.PRECISION,
        motionCharacter = MotionCharacter.SUBTLE,
        iconShape = IconShape.SYSTEM,
        iconStyle = IconStyle.NATURAL,
        atmosphere = Atmosphere.TINT,
        surfaceStyle = SurfaceStyle.TRANSLUCENT,
        defaultBackdrop = Backdrop.WALLPAPER,
        cornerScale = 1.0f
    )

    // ------------------------------------------------------------ v3.2 themes
    //
    // Seeds below are the authored v3.2 token values (docs/design/v3.2). The
    // engine draws the exact authored roles when no override is in force; the
    // seeds here are what the derived path and Studio previews fall back to when
    // the user picks a custom accent or background.

    /** Signature: refined, timeless, expensive. Centred clock, floating dock. */
    val SIGNATURE_THEME = ThemeSpec(
        id = ThemeId.SIGNATURE,
        displayName = "Signature",
        tagline = "Refined and timeless: midnight ramp, indigo-violet light, floating dock.",
        dark = true,
        amoled = false,
        background = 0xFF06070B.toInt(),
        accent = 0xFF7C86FF.toInt(),
        secondary = 0xFFA78BFA.toInt(),
        neutralTint = 0xFF7C86FF.toInt(),
        neutralChroma = 0.012,
        typography = TypeStyle.PRECISION,
        motionCharacter = MotionCharacter.SUBTLE,
        iconShape = IconShape.SQUIRCLE,
        iconStyle = IconStyle.PLATE,
        atmosphere = Atmosphere.HALO,
        surfaceStyle = SurfaceStyle.TRANSLUCENT,
        defaultBackdrop = Backdrop.THEME,
        cornerScale = 1.0f,
        tokens = LumaDesignTokens.SIGNATURE
    )

    /** Atmosphere: immersive, cinematic, adaptive. Wallpaper-led, aurora mint. */
    val ATMOSPHERE_THEME = ThemeSpec(
        id = ThemeId.ATMOSPHERE,
        displayName = "Atmosphere",
        tagline = "Cinematic and adaptive: the wall leads, aurora mint and sky follow.",
        dark = true,
        amoled = false,
        background = 0xFF04131A.toInt(),
        accent = 0xFF46E0B0.toInt(),
        secondary = 0xFF7FB6FF.toInt(),
        neutralTint = 0xFF46E0B0.toInt(),
        neutralChroma = 0.018,
        typography = TypeStyle.AMBIENT,
        motionCharacter = MotionCharacter.CINEMATIC,
        iconShape = IconShape.CIRCLE,
        iconStyle = IconStyle.PLATE,
        atmosphere = Atmosphere.TINT,
        surfaceStyle = SurfaceStyle.GLASS,
        defaultBackdrop = Backdrop.WALLPAPER,
        // Medium radius 20 dp over the pre-3.2 16 dp base, so legacy component
        // tokens scale to the authored geometry instead of contradicting it.
        cornerScale = 1.25f,
        tokens = LumaDesignTokens.ATMOSPHERE
    )

    /** Monolith: architectural, bold, exact. Stone neutrals, vermilion, no blur. */
    val MONOLITH_THEME = ThemeSpec(
        id = ThemeId.MONOLITH,
        displayName = "Monolith",
        tagline = "Architectural and exact: stone neutrals, ruled surfaces, vermilion signal.",
        dark = true,
        amoled = false,
        background = 0xFF0B0B0A.toInt(),
        accent = 0xFFFF5A2B.toInt(),
        secondary = 0xFFFF5A2B.toInt(),
        neutralTint = 0xFFF5F3EE.toInt(),
        neutralChroma = 0.004,
        typography = TypeStyle.MONOGRAPH,
        motionCharacter = MotionCharacter.EXACT,
        iconShape = IconShape.ROUNDED,
        iconStyle = IconStyle.PLATE,
        atmosphere = Atmosphere.NONE,
        surfaceStyle = SurfaceStyle.SOLID,
        defaultBackdrop = Backdrop.THEME,
        // Medium radius 2 dp / 16 dp base: the near-square Monolith geometry.
        cornerScale = 0.125f,
        tokens = LumaDesignTokens.MONOLITH
    )

    /** The v3.2 identities, in the order Studio shows them. */
    val SIGNATURE: List<ThemeSpec> = listOf(SIGNATURE_THEME, ATMOSPHERE_THEME, MONOLITH_THEME)

    /**
     * Palettes retained from before v3.2. Same IDs, seeds and geometry as the day
     * they shipped: an upgrade must not move a user's home screen.
     */
    val ADDITIONAL: List<ThemeSpec> = listOf(AURORA, ZEN, VOID, OBSIDIAN, GRAPHITE, CHROMA)

    /** All selectable presets, including the retained specialist palettes. */
    val ALL: List<ThemeSpec> = SIGNATURE + ADDITIONAL

    fun of(id: ThemeId): ThemeSpec = ALL.first { it.id == id }

    /** Curated accent swatches offered in the Studio (plus the theme's own). */
    val ACCENT_SWATCHES: List<Int> = listOf(
        0xFF7C86FF.toInt(), // Signature indigo-violet (v3.2)
        0xFFA78BFA.toInt(), // Signature secondary violet (v3.2)
        0xFF46E0B0.toInt(), // Atmosphere aurora mint (v3.2)
        0xFF7FB6FF.toInt(), // Atmosphere sky (v3.2)
        0xFFFF5A2B.toInt(), // Monolith vermilion (v3.2)
        0xFFB5C5A9.toInt(), // LumaOS Pro sage
        0xFFE2B66B.toInt(), // amber
        0xFF9AD3FF.toInt(), // Aurora ice
        0xFF69E2F2.toInt(), // Void cyan
        0xFFE28BCB.toInt(), // Void magenta
        0xFF526B60.toInt(), // Zen sage
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
        0xFF06070B.toInt(), // Signature midnight (v3.2)
        0xFF04131A.toInt(), // Atmosphere deep teal (v3.2)
        0xFF0B0B0A.toInt(), // Monolith stone (v3.2)
        0xFF000000.toInt(),
        0xFF080D1A.toInt(), // Aurora midnight
        0xFF080815.toInt(), // Void indigo
        0xFF101214.toInt(),
        0xFF0B1220.toInt(),
        0xFF14110F.toInt(),
        0xFF0F1A14.toInt(),
        0xFF1A1020.toInt(),
        0xFFF2F4F1.toInt(), // Zen paper
        0xFFF4F2EC.toInt(),
        0xFFEEF1F5.toInt(),
        0xFFF3EDE4.toInt()
    )
}
