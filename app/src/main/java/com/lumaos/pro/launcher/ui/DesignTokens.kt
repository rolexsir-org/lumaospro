package com.lumaos.pro.launcher.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One 4 dp spacing rhythm for every surface.
 *
 * Screens use the named steps rather than arbitrary values so the visual
 * rhythm survives new features. Dense controls (drawer rows, chips) may use the
 * smaller steps; page breathing room always uses the larger ones.
 */
object LumaSpacing {
    val grid: Dp = 4.dp
    val xxs: Dp = 4.dp
    val compactGap: Dp = 6.dp
    val elementGap: Dp = 8.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val sectionGap: Dp = 16.dp
    val contentPadding: Dp = 16.dp
    val lg: Dp = 16.dp
    val screenHorizontal: Dp = 20.dp
    val screenVertical: Dp = 20.dp
    val xl: Dp = 24.dp
    val screenEnd: Dp = 24.dp
    val xxl: Dp = 32.dp
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
