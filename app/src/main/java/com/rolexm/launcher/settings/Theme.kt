package com.rolexm.launcher.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.rolexm.launcher.ui.Palette
import com.rolexm.launcher.ui.Palettes
import com.rolexm.launcher.ui.ThemeMode

/**
 * Material3 theme driven by ROLEXM's own [Palette], so the Compose settings
 * screens and the View-based home screen use exactly the same colours.
 *
 * [textScale] is applied through LocalDensity rather than by multiplying every
 * font size: sp units then scale everywhere, including inside Material
 * components, with no per-call-site arithmetic to forget.
 */
@Composable
fun RolexmTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    textScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val palette = Palettes.forMode(themeMode, systemDark)

    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = Color(palette.accent),
            onPrimary = Color(palette.onAccent),
            background = Color(0xFF0E1418),
            surface = Color(palette.surface),
            surfaceVariant = Color(palette.surfaceVariant),
            onSurface = Color(palette.onSurface),
            onSurfaceVariant = Color(palette.onSurfaceVariant),
            outline = Color(palette.outline)
        )
    } else {
        lightColorScheme(
            primary = Color(palette.accent),
            onPrimary = Color(palette.onAccent),
            background = Color(0xFFF5F6F7),
            surface = Color(palette.surface),
            surfaceVariant = Color(palette.surfaceVariant),
            onSurface = Color(palette.onSurface),
            onSurfaceVariant = Color(palette.onSurfaceVariant),
            outline = Color(palette.outline)
        )
    }

    val density = LocalDensity.current

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * textScale
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}
