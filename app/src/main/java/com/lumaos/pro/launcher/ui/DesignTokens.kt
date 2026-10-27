package com.lumaos.pro.launcher.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumaos.pro.launcher.ui.theme.GlassGeometry
import com.lumaos.pro.launcher.ui.theme.GlassMotion
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.LumaDesignTokens

/**
 * Compose aliases for the single Luma Glass token layer.
 *
 * Spacing and control sizes come from [GlassGeometry]; corner steps come from
 * the same role radius table used by [com.lumaos.pro.launcher.ui.glass.GlassDrawable].
 * The v3.2 spacing rhythm remains available through [LumaDesignTokens], so
 * existing screens and token-parity tests keep their source of truth.
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
    val sectionGap: Dp = GlassGeometry.SECTION_GAP_DP.dp
    val contentPadding: Dp = LumaDesignTokens.spacing(5).dp
    val lg: Dp = LumaDesignTokens.spacing(5).dp
    val screenHorizontal: Dp = GlassGeometry.GUTTER_DP.dp
    val screenVertical: Dp = GlassGeometry.GUTTER_DP.dp
    val xl: Dp = LumaDesignTokens.spacing(7).dp
    val screenEnd: Dp = LumaDesignTokens.spacing(7).dp
    val xxl: Dp = LumaDesignTokens.spacing(8).dp
    /** `sp-9`: the gap the specification keeps between the dock and the grid. */
    val dockGap: Dp = LumaDesignTokens.DOCK_GAP_DP.dp
    /** `sp-10` and `sp-11`: deliberate empty bands (Signature keeps one above the agenda). */
    val xxxl: Dp = LumaDesignTokens.spacing(10).dp
    val display: Dp = LumaDesignTokens.spacing(11).dp
}

/** Base corner radii, now mapped onto the shared glass role geometry. */
object LumaCorners {
    val xs: Dp = 6.dp
    val small: Dp = 10.dp
    val medium: Dp = 14.dp
    val card: Dp = GlassGeometry.ROLE_RADIUS_DP.getValue(GlassRole.SECONDARY).dp
    val large: Dp = GlassGeometry.ROLE_RADIUS_DP.getValue(GlassRole.SEARCH).dp
    val dialog: Dp = GlassGeometry.ROLE_RADIUS_DP.getValue(GlassRole.FOLDER).dp
    val extraLarge: Dp = GlassGeometry.ROLE_RADIUS_DP.getValue(GlassRole.DOCK).dp
    val full: Dp = 999.dp
}

/** Sizes with accessibility or layout significance, shared across UI surfaces. */
object LumaSizes {
    /** Minimum interactive target; visual artwork may be smaller inside it. */
    val minimumTouchTarget: Dp = GlassGeometry.CONTROL_DP.dp
    /** Standard interface glyph inside the 48 dp touch target. */
    val controlIcon: Dp = 24.dp
    /** Application icon on the home grid, drawer and dock. */
    val appIcon: Dp = 48.dp
    /** Application icon in menus and compact lists. */
    val smallAppIcon: Dp = 32.dp
    val searchHeight: Dp = GlassGeometry.SEARCH_HEIGHT_DP.dp
    val settingsRowMinHeight: Dp = GlassGeometry.CONTROL_DP.dp
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
 * Defaults now come from [GlassMotion], which itself aliases the v3.2 motion
 * token family. Prefer `ResolvedTheme.motionProfile` where a theme is available.
 */
object LumaMotion {
    val themeChangeMillis: Long = GlassMotion.themeChangeMs.toLong()
    val pressMillis: Long = GlassMotion.pressMs.toLong()
    const val staggerMillis: Long = 18L
}
