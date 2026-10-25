package com.lumaos.pro.launcher.settings

import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.lumaos.pro.launcher.ui.LumaCorners
import com.lumaos.pro.launcher.ui.ThemeMode
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeEnvironment
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import com.lumaos.pro.launcher.ui.theme.TypeStyle

/** The resolved LumaOS Pro theme, for composables that draw with theme roles directly. */
val LocalLumaTheme = staticCompositionLocalOf {
    ThemeEngine.resolve(ThemeSettings(), ThemeEnvironment(systemDark = true, minuteOfDay = 0))
}

/**
 * Material 3 theme driven by LumaOS Pro's [ResolvedTheme], so the Compose screens
 * (Settings, Studio, onboarding, diagnostics) and the View-based home screen
 * draw from exactly the same palette, typography voice and corner geometry.
 *
 * [textScale] is applied through LocalDensity rather than by multiplying every
 * font size: sp units then scale everywhere, including inside Material
 * components, with no per-call-site arithmetic to forget.
 */
@Composable
fun LumaOSTheme(
    theme: ResolvedTheme,
    textScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalLumaTheme provides theme,
        LocalDensity provides Density(density = density.density, fontScale = density.fontScale * textScale)
    ) {
        MaterialTheme(
            colorScheme = colorSchemeFor(theme),
            typography = typographyFor(theme.typography),
            shapes = Shapes(
                extraSmall = RoundedCornerShape((6f * theme.cornerScale).dp),
                small = RoundedCornerShape(LumaCorners.small * theme.cornerScale),
                medium = RoundedCornerShape(LumaCorners.card * theme.cornerScale),
                large = RoundedCornerShape(LumaCorners.large * theme.cornerScale),
                extraLarge = RoundedCornerShape((28f * theme.cornerScale).dp)
            ),
            content = content
        )
    }
}

/** 0.1-compatible entry point (light/dark/system), mapped onto the 0.2 presets. */
@Composable
fun LumaOSTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    textScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val dark = themeMode.resolveDark(isSystemInDarkTheme())
    val theme = ThemeEngine.resolve(
        ThemeSettings().pinned(if (dark) ThemeId.GRAPHITE else ThemeId.PORCELAIN),
        ThemeEnvironment(systemDark = dark, minuteOfDay = 0)
    )
    LumaOSTheme(theme = theme, textScale = textScale, content = content)
}

fun colorSchemeFor(theme: ResolvedTheme): ColorScheme {
    val c = theme.colors
    fun col(v: Int) = Color(v)
    return if (c.isDark) {
        darkColorScheme(
            primary = col(c.accent), onPrimary = col(c.onAccent),
            primaryContainer = col(c.accentContainer), onPrimaryContainer = col(c.onAccentContainer),
            secondary = col(c.secondary), onSecondary = col(c.onAccent),
            secondaryContainer = col(c.surface3), onSecondaryContainer = col(c.onSurface),
            tertiary = col(c.secondary),
            background = col(c.background), onBackground = col(c.onBackground),
            surface = col(c.surface0), onSurface = col(c.onSurface),
            surfaceVariant = col(c.surface2), onSurfaceVariant = col(c.onSurfaceMuted),
            surfaceTint = col(c.accent),
            outline = col(c.outlineStrong), outlineVariant = col(c.outline),
            error = col(c.danger), scrim = col(c.scrim),
            surfaceContainerLowest = col(c.surface0), surfaceContainerLow = col(c.surface1),
            surfaceContainer = col(c.surface1), surfaceContainerHigh = col(c.surface2),
            surfaceContainerHighest = col(c.surface3), surfaceBright = col(c.surface3), surfaceDim = col(c.background)
        )
    } else {
        lightColorScheme(
            primary = col(c.accent), onPrimary = col(c.onAccent),
            primaryContainer = col(c.accentContainer), onPrimaryContainer = col(c.onAccentContainer),
            secondary = col(c.secondary), onSecondary = col(c.onAccent),
            secondaryContainer = col(c.surface3), onSecondaryContainer = col(c.onSurface),
            tertiary = col(c.secondary),
            background = col(c.background), onBackground = col(c.onBackground),
            surface = col(c.surface0), onSurface = col(c.onSurface),
            surfaceVariant = col(c.surface2), onSurfaceVariant = col(c.onSurfaceMuted),
            surfaceTint = col(c.accent),
            outline = col(c.outlineStrong), outlineVariant = col(c.outline),
            error = col(c.danger), scrim = col(c.scrim),
            surfaceContainerLowest = col(c.surface1), surfaceContainerLow = col(c.surface0),
            surfaceContainer = col(c.surface0), surfaceContainerHigh = col(c.surface2),
            surfaceContainerHighest = col(c.surface3), surfaceBright = col(c.surface1), surfaceDim = col(c.surface3)
        )
    }
}

/** Compose font family for a platform family name. */
fun fontFamilyOf(name: String): Pair<FontFamily, FontWeight> = when (name) {
    "sans-serif-thin" -> FontFamily.SansSerif to FontWeight.Thin
    "sans-serif-light" -> FontFamily.SansSerif to FontWeight.Light
    "sans-serif-medium" -> FontFamily.SansSerif to FontWeight.Medium
    "serif" -> FontFamily.Serif to FontWeight.Normal
    "monospace" -> FontFamily.Monospace to FontWeight.Normal
    "sans-serif-condensed" -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL)) to FontWeight.Normal
    else -> FontFamily.SansSerif to FontWeight.Normal
}

fun typographyFor(style: TypeStyle): Typography {
    val base = Typography()
    val (titleFamily, titleWeight) = fontFamilyOf(style.titleFamily)
    val (bodyFamily, _) = fontFamilyOf(style.labelFamily)
    val (readoutFamily, readoutWeight) = fontFamilyOf(style.readoutFamily)
    val (clockFamily, clockWeight) = fontFamilyOf(style.clockFamily)
    fun TextStyle.title() = copy(fontFamily = titleFamily, fontWeight = if (titleWeight == FontWeight.Normal) FontWeight.Medium else titleWeight)
    fun TextStyle.body() = copy(fontFamily = bodyFamily)
    fun TextStyle.readout() = copy(fontFamily = readoutFamily, fontWeight = readoutWeight, letterSpacing = style.readoutTracking.em)
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = clockFamily, fontWeight = clockWeight, letterSpacing = style.clockTracking.em),
        displayMedium = base.displayMedium.copy(fontFamily = clockFamily, fontWeight = clockWeight, letterSpacing = style.clockTracking.em),
        displaySmall = base.displaySmall.copy(fontFamily = clockFamily, fontWeight = clockWeight, letterSpacing = style.clockTracking.em),
        headlineLarge = base.headlineLarge.title(),
        headlineMedium = base.headlineMedium.title(),
        headlineSmall = base.headlineSmall.title(),
        titleLarge = base.titleLarge.title(),
        titleMedium = base.titleMedium.title(),
        titleSmall = base.titleSmall.title(),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.body(),
        labelMedium = base.labelMedium.readout(),
        labelSmall = base.labelSmall.readout()
    )
}
