package com.lumaos.pro.launcher.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared layout rhythm for the launcher settings, Studio, and diagnostics. */
object LumaSpacing {
    val screenHorizontal: Dp = 20.dp
    val screenVertical: Dp = 20.dp
    val sectionGap: Dp = 12.dp
    val contentPadding: Dp = 16.dp
    val elementGap: Dp = 8.dp
    val compactGap: Dp = 6.dp
    val screenEnd: Dp = 24.dp
}

/** Corner radii shared by Material controls, cards, and dialog surfaces. */
object LumaCorners {
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val card: Dp = 14.dp
    val large: Dp = 20.dp
}

/** Sizes with accessibility or layout significance, shared across UI surfaces. */
object LumaSizes {
    /** Minimum interactive target; visual artwork may be smaller inside it. */
    val minimumTouchTarget: Dp = 48.dp
    val studioThemeCardWidth: Dp = 152.dp
    val studioThemeCardHeight: Dp = 108.dp
    val studioPreviewMaxWidth: Dp = 560.dp
}

/** Shared timing for theme changes in the View-based home composition. */
object LumaMotion {
    const val themeChangeMillis: Long = 420L
}
