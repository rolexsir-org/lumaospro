package com.lumaos.pro.launcher.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumaos.pro.launcher.ui.theme.LumaDesignTokens

/**
 * The Design System v3.2 spacing scale for every surface: **2 · 4 · 8 · 12 · 16 ·
 * 20 · 24 · 32 · 40 · 56 · 72**.
 *
 * Screens use the named steps rather than arbitrary values so the visual rhythm
 * survives new features, and the specification's rule is explicit: a gap that is
 * not on this scale (6, 10, 14 …) is a defect. The two gaps that are semantic
 * rather than decorative — row gap 12 and section gap 24 — are their own tokens, as
 * are the screen gutter (20, compact 16, large 22) and the dock-to-grid gap (40).
 *
 * Values come from [LumaDesignTokens.SPACING_DP]; this object only gives them
 * Compose types and readable names.
 */
object LumaSpacing {
    /** `sp-2`: the smallest visible separation. */
    val grid: Dp = 4.dp
    val xxs: Dp = LumaDesignTokens.spacing(2).dp
    /** Dense control internals, e.g. a chip's padding or a control-to-note gap. */
    val compactGap: Dp = LumaDesignTokens.spacing(3).dp
    val elementGap: Dp = LumaDesignTokens.spacing(3).dp
    val sm: Dp = LumaDesignTokens.spacing(3).dp
    /** `sp-4`: row gap between grid rows and list items. */
    val md: Dp = LumaDesignTokens.spacing(4).dp
    /** `sp-7`: gap between sections. */
    val sectionGap: Dp = LumaDesignTokens.SECTION_GAP_DP.dp
    val contentPadding: Dp = LumaDesignTokens.spacing(5).dp
    val lg: Dp = LumaDesignTokens.spacing(5).dp
    val screenHorizontal: Dp = LumaDesignTokens.GUTTER_DP.dp
    val screenVertical: Dp = LumaDesignTokens.GUTTER_DP.dp
    val xl: Dp = LumaDesignTokens.spacing(7).dp
    val screenEnd: Dp = LumaDesignTokens.spacing(7).dp
    val xxl: Dp = LumaDesignTokens.spacing(8).dp
    /** `sp-9`: the gap the specification keeps between the dock and the grid. */
    val dockGap: Dp = LumaDesignTokens.DOCK_GAP_DP.dp
    /** `sp-10` and `sp-11`: deliberate empty bands (Signature keeps one above the agenda). */
    val xxxl: Dp = LumaDesignTokens.spacing(10).dp
    val display: Dp = LumaDesignTokens.spacing(11).dp
}

/**
 * Base corner radii. Themes scale these through `ResolvedTheme.cornerScale`, and
 * component-specific tokens (search bar, dock, menu, dialog) live in
 * `ui.theme.LumaComponents` so a single control can never drift.
 *
 * The scale deliberately stops short of full pills for large surfaces: only
 * compact controls (chips, icon targets) may be fully rounded.
 */
object LumaCorners {
    val xs: Dp = 6.dp
    val small: Dp = 10.dp
    val medium: Dp = 14.dp
    val card: Dp = 16.dp
    val large: Dp = 22.dp
    val dialog: Dp = 24.dp
    val extraLarge: Dp = 28.dp
    val full: Dp = 999.dp
}

/** Sizes with accessibility or layout significance, shared across UI surfaces. */
object LumaSizes {
    /** Minimum interactive target; visual artwork may be smaller inside it. */
    val minimumTouchTarget: Dp = 48.dp
    /** Standard interface glyph inside the 48 dp touch target. */
    val controlIcon: Dp = 24.dp
    /** Application icon on the home grid, drawer and dock. */
    val appIcon: Dp = 48.dp
    /** Application icon in menus and compact lists. */
    val smallAppIcon: Dp = 32.dp
    val searchHeight: Dp = 52.dp
    val settingsRowMinHeight: Dp = 48.dp
    val studioThemeCardWidth: Dp = 160.dp
    val studioThemeCardHeight: Dp = 132.dp
    val studioPreviewMaxWidth: Dp = 560.dp
}

/** Elevation levels in dp. Theme surface tokens choose which level a surface uses. */
object LumaElevation {
    const val flat = 0f
    const val raised = 2f
    const val floating = 6f
    const val menu = 10f
    const val dialog = 16f
}

/**
 * Shared timing for the View-based home composition.
 *
 * Prefer `ResolvedTheme.motionProfile` where a theme is available (Aurora, Zen
 * and Void animate at different tempos). These constants remain for code paths
 * that only need a safe default.
 */
object LumaMotion {
    const val themeChangeMillis: Long = 420L
    const val pressMillis: Long = 90L
    const val staggerMillis: Long = 18L
}
