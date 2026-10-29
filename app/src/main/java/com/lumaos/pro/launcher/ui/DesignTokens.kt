package com.lumaos.pro.launcher.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumaos.pro.launcher.ui.theme.GlassMotion
import com.lumaos.pro.launcher.ui.theme.LumaTokens

/**
 * Compose aliases backed directly by [LumaTokens].
 *
 * Ensures Settings, Studio, Wallpaper Studio, Onboarding, and View helpers all
 * share the exact same 8dp spacing grid, 24dp universal screen margins, 56dp
 * app icons, 44dp search bar, unified corner radii, and 15ms stagger cadence.
 */
object LumaSpacing {
    val grid: Dp = LumaTokens.Compose.spaceXXS
    val xxs: Dp = LumaTokens.Compose.spaceXXS
    val xs: Dp = LumaTokens.Compose.spaceXS
    val compactGap: Dp = LumaTokens.Compose.spaceXS
    val elementGap: Dp = LumaTokens.Compose.spaceSM
    val sm: Dp = LumaTokens.Compose.spaceSM
    val md: Dp = LumaTokens.Compose.spaceMD
    val cardPadding: Dp = LumaTokens.Compose.cardPadding
    val cardGap: Dp = LumaTokens.Compose.cardGap
    val contentPadding: Dp = LumaTokens.Compose.cardPadding
    val lg: Dp = LumaTokens.Compose.spaceLG
    val sectionGap: Dp = LumaTokens.Compose.spaceXL
    /** Universal 24dp horizontal screen margin across all LumaOS surfaces. */
    val screenHorizontal: Dp = LumaTokens.Compose.screenMarginHorizontal
    val screenVertical: Dp = LumaTokens.Compose.spaceMD
    val xl: Dp = LumaTokens.Compose.spaceXL
    val screenEnd: Dp = LumaTokens.Compose.spaceXXL
    val xxl: Dp = LumaTokens.Compose.spaceXXL
    val dockGap: Dp = LumaTokens.Compose.spaceMD
    val xxxl: Dp = LumaTokens.Compose.spaceHero
    val display: Dp = LumaTokens.Compose.spaceHero
}

object LumaCorners {
    val xs: Dp = (LumaTokens.radiusSmall * 0.5f).dp
    val small: Dp = LumaTokens.Compose.radiusSmall
    val medium: Dp = LumaTokens.Compose.radiusMedium
    val card: Dp = LumaTokens.Compose.radiusLarge
    val large: Dp = LumaTokens.Compose.radiusLarge
    val dialog: Dp = LumaTokens.Compose.radiusFolder
    val sheet: Dp = LumaTokens.Compose.radiusXLarge
    val dock: Dp = LumaTokens.Compose.radiusXLarge
    val extraLarge: Dp = LumaTokens.Compose.radiusXLarge
    val full: Dp = LumaTokens.Compose.radiusFull
}

object LumaSizes {
    val minimumTouchTarget: Dp = LumaTokens.Compose.minTouchTarget
    val controlIcon: Dp = LumaTokens.Compose.iconAction
    val appIcon: Dp = LumaTokens.Compose.iconApp
    val smallAppIcon: Dp = LumaTokens.Compose.iconNotification
    val searchHeight: Dp = LumaTokens.Compose.searchBarHeight
    val settingsRowMinHeight: Dp = LumaTokens.Compose.minTouchTarget
    val studioThemeCardWidth: Dp = 160.dp
    val studioThemeCardHeight: Dp = 132.dp
    val studioPreviewMaxWidth: Dp = 560.dp
}

object LumaElevation {
    const val flat = 0f
    const val raised = LumaTokens.shadowCardElevation * 0.5f
    const val floating = LumaTokens.shadowCardElevation
    const val menu = LumaTokens.shadowCardElevation
    const val dialog = LumaTokens.shadowFloatingElevation
}

object LumaMotion {
    val themeChangeMillis: Long = GlassMotion.themeChangeMs.toLong()
    val pressMillis: Long = GlassMotion.pressMs.toLong()
    const val staggerMillis: Long = LumaTokens.staggerDelayMs
}
