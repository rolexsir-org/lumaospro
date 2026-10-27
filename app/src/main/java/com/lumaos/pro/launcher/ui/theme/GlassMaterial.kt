package com.lumaos.pro.launcher.ui.theme

/**
 * LumaOS Liquid Glass — the single material grammar shared by the View launcher
 * and the Compose settings surfaces.
 *
 * This file deliberately has no Android, Compose or effects imports. A screen
 * asks for a semantic [GlassRole] and receives a complete material recipe:
 * fill, directional edge, internal reflection, corner geometry, blur budget,
 * elevation, content colour and pressed/focused/selected states. The View layer
 * turns that recipe into one cached drawable; Compose turns the same values into
 * brushes, borders and shapes. Nothing here is a wallpaper effect baked into a
 * screen, and no component owns a private hex value.
 *
 * Colour roles come from [ResolvedTheme], so the existing contrast guarantees,
 * wallpaper brightness adaptation and saved light/dark preferences still apply.
 * Legacy theme IDs remain storage-compatible; they are accents within one
 * visual system rather than separate design systems.
 */
enum class GlassRole {
    /** Floating hero surface: previews and prominent panels. */
    PRIMARY,

    /** Secondary information panels and settings cards. */
    SECONDARY,

    /** Search fields, on the desktop and in the drawer. */
    SEARCH,

    /** The floating dock. */
    DOCK,

    /** In-window folder containers. */
    FOLDER,

    /** Context menus and compact popups. */
    MENU,

    /** Full-height drawer and bottom sheets; top corners only. */
    SHEET,

    /** Frames around hosted Android widgets. */
    WIDGET,

    /** Dense settings controls and interactive overlays. */
    SETTINGS_CONTROL,

    /** Low-emphasis state layers and hint surfaces. */
    OVERLAY
}

/** Runtime capabilities, supplied by [com.lumaos.pro.launcher.effects.EffectsDecision]. */
data class GlassCapabilities(
    val translucency: Boolean = true,
    val blur: Boolean = true,
    val depth: Boolean = true
) {
    companion object {
        /** Settings and previews use opaque, readable glass without real-time blur. */
        val SOLID = GlassCapabilities(translucency = false, blur = false, depth = false)
        val DEFAULT = GlassCapabilities()
    }
}

/** The colours needed to draw one glass surface and its content. All are packed ARGB. */
data class GlassColors(
    val fillTop: Int,
    val fillBottom: Int,
    val edgeTop: Int,
    val edgeBottom: Int,
    val specular: Int,
    val content: Int,
    val muted: Int,
    val ripple: Int,
    val focusRing: Int
)

/** State colours are resolved once, so Android can switch states without recomputing the palette. */
data class GlassStates(
    val rest: GlassColors,
    val pressed: GlassColors,
    val focused: GlassColors,
    val selected: GlassColors
)

data class GlassSpec(
    val role: GlassRole,
    val radiusDp: Float,
    val blurDp: Float,
    val elevationDp: Float,
    val borderDp: Float,
    val topOnly: Boolean,
    val states: GlassStates
) {
    val rest: GlassColors get() = states.rest
}

class GlassPalette(private val specs: Map<GlassRole, GlassSpec>) {
    operator fun get(role: GlassRole): GlassSpec =
        requireNotNull(specs[role]) { "Missing glass material for $role" }

    val roles: Set<GlassRole> get() = specs.keys
}

/** Geometry, spacing and motion tokens; one scale for both UI toolkits. */
object GlassGeometry {
    const val GUTTER_DP = 20f
    const val SECTION_GAP_DP = 24f
    const val CONTROL_DP = 48f
    const val SEARCH_HEIGHT_DP = 56f
    const val DOCK_HEIGHT_DP = 76f
    const val BORDER_DP = 1f
    const val SPECULAR_DP = 1f

    val ROLE_RADIUS_DP = mapOf(
        GlassRole.PRIMARY to 30f,
        GlassRole.SECONDARY to 24f,
        GlassRole.SEARCH to 26f,
        GlassRole.DOCK to 34f,
        GlassRole.FOLDER to 30f,
        GlassRole.MENU to 20f,
        GlassRole.SHEET to 36f,
        GlassRole.WIDGET to 24f,
        GlassRole.SETTINGS_CONTROL to 18f,
        GlassRole.OVERLAY to 16f
    )
}

object GlassMotion {
    val pressMs: Int = LumaDesignTokens.Motion.PRESS
    val openMs: Int = LumaDesignTokens.Motion.DUR_2
    val closeMs: Int = LumaDesignTokens.Motion.DUR_1
    val themeChangeMs: Int = LumaDesignTokens.Motion.DUR_3
    val standard: FloatArray = LumaDesignTokens.Motion.STANDARD
    val emphasis: FloatArray = LumaDesignTokens.Motion.EMPHASIS
    val exit: FloatArray = LumaDesignTokens.Motion.EXIT
}

data class LumaGlassAccent(
    val name: String,
    val color: Int,
    val darkAnchor: ThemeId = ThemeId.SIGNATURE,
    val lightAnchor: ThemeId = ThemeId.PORCELAIN
) {
    /**
     * Applies one unified appearance to stored [ThemeSettings]. Accents change
     * the semantic colour; they no longer introduce a second material system.
     */
    fun applyTo(settings: ThemeSettings): ThemeSettings = settings.copy(
        themeId = darkAnchor,
        darkThemeId = darkAnchor,
        lightThemeId = lightAnchor,
        switching = ThemeSwitching.SYSTEM,
        accentOverride = color,
        backgroundOverride = null,
        backdrop = Backdrop.WALLPAPER
    )
}

object LumaGlassIdentity {
    const val NAME = "Luma Glass"
    const val VERSION = "4.0"
    const val TAGLINE = "Restrained refraction, quiet depth and one coherent LumaOS surface."

    val ACCENTS = listOf(
        LumaGlassAccent("Iris", 0xFF8E96FF.toInt()),
        LumaGlassAccent("Aurora", 0xFF55D9BC.toInt(), darkAnchor = ThemeId.ATMOSPHERE),
        LumaGlassAccent("Ember", 0xFFFF8D72.toInt())
    )

    fun settings(): ThemeSettings = ThemeSettings(backdrop = Backdrop.WALLPAPER)
}

/**
 * A legibility scrim over photographic wallpapers. It is a quiet vertical tint,
 * not an abstract wallpaper or a decorative smoke layer. The tint follows the
 * text colour chosen by the theme engine: dark text gets a white veil, light
 * text gets a black veil.
 */
data class GlassScrim(
    val tint: Int,
    val top: Int,
    val center: Int,
    val bottom: Int
)

object GlassBackdrop {
    fun scrim(theme: ResolvedTheme): GlassScrim {
        val darkText = ColorMath.isDark(ColorMath.opaque(theme.colors.homeForeground))
        val tint = if (darkText) ColorMath.WHITE else ColorMath.BLACK
        return GlassScrim(
            tint = tint,
            // The atmosphere view already paints the wallpaper legibility
            // gradients. This is a very light material veil, not a second
            // opaque scrim.
            top = ColorMath.withAlpha(tint, 0.08f),
            center = ColorMath.withAlpha(tint, 0.01f),
            bottom = ColorMath.withAlpha(tint, 0.12f)
        )
    }
}

/**
 * Builds the palette. Every role gets intentionally different transparency,
 * blur budget, radius and elevation; shared colour roles keep the family
 * coherent. A missing runtime blur capability produces an opaque composited
 * fallback rather than a half-transparent surface with no depth.
 */
object GlassMaterials {
    private data class Recipe(
        val radiusDp: Float,
        val darkFill: Float,
        val lightFill: Float,
        val blurFactor: Float,
        val elevationDp: Float,
        val edgeAlpha: Float,
        val topOnly: Boolean = false
    )

    private val RECIPES = mapOf(
        GlassRole.PRIMARY to Recipe(30f, 0.24f, 0.40f, 0.72f, 9f, 0.34f),
        GlassRole.SECONDARY to Recipe(24f, 0.18f, 0.32f, 0.54f, 5f, 0.25f),
        GlassRole.SEARCH to Recipe(26f, 0.15f, 0.28f, 0.42f, 3f, 0.30f),
        GlassRole.DOCK to Recipe(34f, 0.21f, 0.36f, 0.78f, 10f, 0.38f),
        GlassRole.FOLDER to Recipe(30f, 0.28f, 0.46f, 0.86f, 12f, 0.34f),
        GlassRole.MENU to Recipe(20f, 0.34f, 0.54f, 0.70f, 11f, 0.38f),
        GlassRole.SHEET to Recipe(36f, 0.42f, 0.66f, 0.56f, 12f, 0.26f, topOnly = true),
        GlassRole.WIDGET to Recipe(24f, 0.17f, 0.30f, 0.48f, 4f, 0.22f),
        GlassRole.SETTINGS_CONTROL to Recipe(18f, 0.11f, 0.20f, 0.24f, 1f, 0.18f),
        GlassRole.OVERLAY to Recipe(16f, 0.08f, 0.13f, 0.16f, 0f, 0.14f)
    )

    fun palette(theme: ResolvedTheme, capabilities: GlassCapabilities = GlassCapabilities.DEFAULT): GlassPalette {
        // Keep legacy shape characters as a light modulation, never as nine
        // unrelated geometries. Monolith's authored sharpness cannot collapse
        // the glass radius below a usable, touch-friendly shape.
        val shapeScale = (theme.radius(RadiusRole.MEDIUM) / 16f).coerceIn(0.78f, 1.15f)
        val blurAvailable = capabilities.translucency && capabilities.blur && theme.usesBlur
        val themeBlur = theme.blurDp.coerceIn(0f, 34f)
        val darkText = ColorMath.isDark(ColorMath.opaque(theme.colors.homeForeground))
        val fillTint = if (darkText) ColorMath.WHITE else ColorMath.BLACK
        val edgeTint = if (darkText) ColorMath.BLACK else ColorMath.WHITE
        val identityTint = ColorMath.lerp(
            ColorMath.opaque(fillTint),
            ColorMath.opaque(theme.colors.accent),
            0.025f
        )
        val accent = ColorMath.opaque(theme.colors.accent)

        val specs = GlassRole.entries.associateWith { role ->
            val recipe = RECIPES.getValue(role)
            val baseAlpha = if (theme.isDark) recipe.darkFill else recipe.lightFill
            val resolvedAlpha = if (!capabilities.translucency) 1f
            else if (!blurAvailable) (baseAlpha + 0.22f).coerceAtMost(0.94f)
            else baseAlpha

            // Glass is a surface, so its text uses the contrast-checked
            // onSurface role. homeForeground is reserved for clock text drawn
            // directly on the wallpaper, where a wallpaper hint may be unknown.
            val states = states(
                theme = theme,
                fillTint = identityTint,
                edgeTint = edgeTint,
                accent = accent,
                alpha = resolvedAlpha,
                edgeAlpha = recipe.edgeAlpha,
                content = ColorMath.opaque(theme.colors.onSurface),
                opaqueFallback = !capabilities.translucency || !blurAvailable
            )
            GlassSpec(
                role = role,
                radiusDp = recipe.radiusDp * shapeScale,
                blurDp = if (blurAvailable) (themeBlur * recipe.blurFactor).coerceIn(0f, 26f) else 0f,
                elevationDp = if (capabilities.depth) recipe.elevationDp else 0f,
                borderDp = GlassGeometry.BORDER_DP,
                topOnly = recipe.topOnly,
                states = states
            )
        }
        return GlassPalette(specs)
    }

    private fun states(
        theme: ResolvedTheme,
        fillTint: Int,
        edgeTint: Int,
        accent: Int,
        alpha: Float,
        edgeAlpha: Float,
        content: Int,
        opaqueFallback: Boolean
    ): GlassStates {
        val focusRing = ColorMath.opaque(theme.colors.focusRing)
        val ripple = ColorMath.withAlpha(accent, if (theme.isDark) 0.20f else 0.14f)
        val muted = ColorMath.opaque(theme.colors.onSurfaceMuted)

        fun colors(
            fill: Int,
            edgeTop: Int,
            edgeBottom: Int,
            specular: Int,
            text: Int = content
        ) = GlassColors(
            fillTop = fill,
            fillBottom = ColorMath.withAlpha(fill, ColorMath.alpha(fill) / 255f * 0.82f),
            edgeTop = edgeTop,
            edgeBottom = edgeBottom,
            specular = specular,
            content = text,
            muted = ColorMath.withAlpha(text, 0.72f),
            ripple = ripple,
            focusRing = focusRing
        )

        val restFill = ColorMath.withAlpha(fillTint, alpha * 1.08f)
        val pressedFill = ColorMath.withAlpha(
            ColorMath.lerp(fillTint, accent, 0.035f),
            (alpha + 0.06f).coerceAtMost(1f) * 1.08f
        )
        val selectedFill = ColorMath.withAlpha(
            ColorMath.lerp(fillTint, accent, if (theme.isDark) 0.16f else 0.10f),
            (alpha + 0.04f).coerceAtMost(1f) * 1.08f
        )

        var rest = colors(
            restFill,
            ColorMath.withAlpha(edgeTint, edgeAlpha),
            ColorMath.withAlpha(edgeTint, edgeAlpha * 0.28f),
            ColorMath.withAlpha(edgeTint, edgeAlpha * 0.50f)
        )
        var pressed = colors(
            pressedFill,
            ColorMath.withAlpha(edgeTint, edgeAlpha * 1.22f),
            ColorMath.withAlpha(edgeTint, edgeAlpha * 0.36f),
            ColorMath.withAlpha(edgeTint, edgeAlpha * 0.66f)
        )
        var focused = colors(
            restFill,
            ColorMath.withAlpha(focusRing, 0.88f),
            ColorMath.withAlpha(focusRing, 0.42f),
            ColorMath.withAlpha(focusRing, 0.18f)
        )
        var selected = colors(
            selectedFill,
            ColorMath.withAlpha(focusRing, 0.78f),
            ColorMath.withAlpha(focusRing, 0.34f),
            ColorMath.withAlpha(focusRing, 0.20f)
        )

        // A device without blur or translucency gets an opaque surface derived
        // from the theme's own contrast-checked layers, not a disabled-looking
        // translucent plate over an unknown wallpaper.
        if (opaqueFallback) {
            val base = theme.colors.surface1
            val raised = theme.colors.surface2
            fun solidify(c: GlassColors, top: Int, bottom: Int): GlassColors = c.copy(
                fillTop = ColorMath.opaque(top),
                fillBottom = ColorMath.opaque(bottom)
            )
            rest = solidify(rest, raised, base)
            pressed = solidify(pressed, ColorMath.lerp(raised, theme.colors.accent, 0.05f), ColorMath.lerp(base, theme.colors.accent, 0.03f))
            focused = solidify(focused, raised, base)
            selected = solidify(selected, ColorMath.lerp(raised, theme.colors.accent, 0.13f), ColorMath.lerp(base, theme.colors.accent, 0.09f))
        }

        // Muted content is derived from the selected text, not from the surface.
        return GlassStates(
            rest = rest.copy(muted = muted),
            pressed = pressed.copy(muted = muted),
            focused = focused.copy(muted = muted),
            selected = selected.copy(muted = muted)
        )
    }
}
