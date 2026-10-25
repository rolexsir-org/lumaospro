package com.lumaos.pro.launcher.ui.theme

/**
 * Component-level shape, surface and sizing tokens.
 *
 * A component asks for a *role* ("search bar", "dialog", "dock") and receives
 * values appropriate to the active theme. This is why the same search bar is a
 * softly rounded glass surface in Aurora, a quiet bordered card in Zen and a
 * sharper layered panel in Void, without three implementations.
 *
 * All dp values are floats so the View system (which uses pixels) and Compose
 * (which multiplies by density) read the same numbers.
 */
data class ShapeTokens(
    /** Theme corner multiplier. */
    val scale: Float,
    /** Compact control - icon buttons, chips, menu rows. */
    val controlDp: Float = 12f * scale,
    val chipDp: Float = 12f * scale,
    val cellDp: Float = 16f * scale,
    val searchDp: Float = 18f * scale,
    val menuDp: Float = 16f * scale,
    val dialogDp: Float = 24f * scale,
    val dockDp: Float = 24f * scale,
    val folderDp: Float = 24f * scale,
    val widgetDp: Float = 18f * scale,
    val sheetDp: Float = 28f * scale
)

/** Material and elevation rules for elevated surfaces. */
data class SurfaceTokens(
    /** Alpha of the surface fill before effects policy is applied. */
    val alpha: Float,
    /** Hairline border alpha; 0 disables the border. */
    val borderAlpha: Float,
    val elevationDp: Float,
    /** Soft shadow used behind home surfaces over artwork. */
    val shadowAlpha: Float,
    /** Accent halo, used sparingly in Void. */
    val glowAlpha: Float
)

/** Sizes that must stay consistent across adjacent surfaces. */
data class SizeTokens(
    val controlIconDp: Float = 24f,
    val appIconDp: Float = 48f,
    val dockIconDp: Float = 48f,
    val drawerIconDp: Float = 40f,
    val folderIconDp: Float = 44f,
    val smallAppIconDp: Float = 32f,
    val searchHeightDp: Float = 52f,
    val rowMinHeightDp: Float = 48f,
    val dividerDp: Float = 1f,
    val borderDp: Float = 1f,
    val widgetGapDp: Float = 6f,
    val hairlineDp: Float = 1f
)

data class ComponentTokens(
    val shapes: ShapeTokens,
    val surfaces: SurfaceTokens,
    val sizes: SizeTokens,
    val appLabelSp: Float = LumaTypeScale.appLabelSp,
    val pressScale: Float,
    val pressMs: Int
)

object LumaComponents {

    fun of(theme: ResolvedTheme): ComponentTokens {
        val surface = when (theme.id) {
            ThemeId.AURORA -> SurfaceTokens(alpha = 0.86f, borderAlpha = 0.18f, elevationDp = 6f, shadowAlpha = 0.26f, glowAlpha = 0.10f)
            ThemeId.PORCELAIN -> SurfaceTokens(alpha = 1f, borderAlpha = 0.12f, elevationDp = 0f, shadowAlpha = 0.06f, glowAlpha = 0f)
            ThemeId.VECTOR -> SurfaceTokens(alpha = 0.80f, borderAlpha = 0.26f, elevationDp = 10f, shadowAlpha = 0.32f, glowAlpha = 0.22f)
            ThemeId.GRAPHITE -> SurfaceTokens(alpha = 0.92f, borderAlpha = 0.14f, elevationDp = 3f, shadowAlpha = 0.20f, glowAlpha = 0f)
            ThemeId.CHROMA -> SurfaceTokens(alpha = 0.90f, borderAlpha = 0.16f, elevationDp = 4f, shadowAlpha = 0.22f, glowAlpha = 0.08f)
            ThemeId.OBSIDIAN -> SurfaceTokens(alpha = 1f, borderAlpha = 0.16f, elevationDp = 0f, shadowAlpha = 0.20f, glowAlpha = 0f)
        }
        val motion = theme.motionProfile
        return ComponentTokens(
            shapes = ShapeTokens(theme.cornerScale),
            surfaces = surface,
            sizes = SizeTokens(),
            pressScale = motion.pressScale,
            pressMs = motion.pressMs
        )
    }

    /** Effective fill alpha, respecting the device effects policy. */
    fun surfaceAlpha(theme: ResolvedTheme, effectsAllowTranslucency: Boolean): Float {
        if (!effectsAllowTranslucency) return 1f
        return of(theme).surfaces.alpha.coerceIn(0f, 1f)
    }
}
