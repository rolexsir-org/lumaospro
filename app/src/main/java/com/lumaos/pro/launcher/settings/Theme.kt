package com.lumaos.pro.launcher.settings

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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.ui.LumaCorners
import com.lumaos.pro.launcher.ui.glass.LocalGlassPalette
import com.lumaos.pro.launcher.ui.theme.GlassCapabilities
import com.lumaos.pro.launcher.ui.theme.GlassMaterials
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.ThemeMode
import com.lumaos.pro.launcher.ui.theme.FontFace
import com.lumaos.pro.launcher.ui.theme.FontFamilyId
import com.lumaos.pro.launcher.ui.theme.FontWeightToken
import com.lumaos.pro.launcher.ui.theme.LumaComponents
import com.lumaos.pro.launcher.ui.theme.LumaMotionProfiles
import com.lumaos.pro.launcher.ui.theme.LumaTypeScale
import com.lumaos.pro.launcher.ui.theme.MotionProfile
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

/** Status, disabled, overlay and widget roles that Material 3 has no slot for. */
val LocalLumaSemantics = staticCompositionLocalOf {
    ThemeEngine.resolve(ThemeSettings(), ThemeEnvironment(systemDark = true, minuteOfDay = 0)).semantics
}

/** The active theme's motion character. */
val LocalLumaMotion = staticCompositionLocalOf<MotionProfile> {
    LumaMotionProfiles.AURORA
}

/** Shape, surface and size tokens for the active theme. */
val LocalLumaComponents = staticCompositionLocalOf {
    LumaComponents.of(ThemeEngine.resolve(ThemeSettings(), ThemeEnvironment(systemDark = true, minuteOfDay = 0)))
}

/**
 * Material 3 theme driven by LumaOS Pro's [ResolvedTheme], so the Compose screens
 * (Settings, Studio, onboarding, diagnostics) and the View-based home screen
 * draw from exactly the same palette, typography system, icon language and
 * corner geometry.
 *
 * [textScale] is applied through LocalDensity rather than by multiplying every
 * font size: sp units then scale everywhere, including inside Material
 * components, with no per-call-site arithmetic to forget. The system font-scale
 * setting is multiplied in, not replaced.
 */
@Composable
fun LumaOSTheme(
    theme: ResolvedTheme,
    textScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val semantics = theme.semantics
    val motion = theme.motionProfile
    val components = LumaComponents.of(theme)
    val glassPalette = remember(theme) { GlassMaterials.palette(theme, GlassCapabilities.SOLID) }
    CompositionLocalProvider(
        LocalLumaTheme provides theme,
        LocalLumaSemantics provides semantics,
        LocalLumaMotion provides motion,
        LocalLumaComponents provides components,
        LocalGlassPalette provides glassPalette,
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * textScale.coerceIn(0.75f, 1.6f)
        )
    ) {
        MaterialTheme(
            colorScheme = colorSchemeFor(theme),
            typography = typographyFor(theme.typography),
            shapes = Shapes(
                extraSmall = RoundedCornerShape((6f * theme.cornerScale).dp),
                small = RoundedCornerShape(LumaCorners.small * theme.cornerScale),
                medium = RoundedCornerShape(LumaCorners.medium * theme.cornerScale),
                large = RoundedCornerShape(LumaCorners.card * theme.cornerScale),
                extraLarge = RoundedCornerShape(LumaCorners.dialog * theme.cornerScale)
            ),
            content = content
        )
    }
}

/** 0.1-compatible entry point (light/dark/system), mapped onto the 0.2 presets. */
@Composable
fun LumaOSTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    textScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val dark = themeMode.resolveDark(isSystemInDarkTheme())
    val theme = ThemeEngine.resolve(
        ThemeSettings().pinned(if (dark) ThemeId.SIGNATURE else ThemeId.PORCELAIN),
        ThemeEnvironment(systemDark = dark, minuteOfDay = 0)
    )
    LumaOSTheme(theme = theme, textScale = textScale, content = content)
}

fun colorSchemeFor(theme: ResolvedTheme): ColorScheme {
    val c = theme.colors
    val s = theme.semantics
    val glass = GlassMaterials.palette(theme, GlassCapabilities.SOLID)
    val secondary = glass[GlassRole.SECONDARY].rest
    val control = glass[GlassRole.SETTINGS_CONTROL].rest
    val folder = glass[GlassRole.FOLDER].rest
    fun col(v: Int) = Color(v)
    return if (c.isDark) {
        darkColorScheme(
            primary = col(c.accent), onPrimary = col(c.onAccent),
            primaryContainer = col(c.accentContainer), onPrimaryContainer = col(c.onAccentContainer),
            secondary = col(c.secondary), onSecondary = col(c.onAccent),
            secondaryContainer = col(c.surface3), onSecondaryContainer = col(c.onSurface),
            tertiary = col(c.secondary), onTertiary = col(c.onAccent),
            background = col(c.background), onBackground = col(c.onBackground),
            surface = col(secondary.fillBottom), onSurface = col(secondary.content),
            surfaceVariant = col(control.fillBottom), onSurfaceVariant = col(secondary.muted),
            surfaceTint = col(c.accent),
            outline = col(secondary.edgeTop), outlineVariant = col(secondary.edgeBottom),
            error = col(s.danger), onError = col(s.onDanger),
            errorContainer = col(s.dangerContainer), onErrorContainer = col(s.onDangerContainer),
            scrim = col(c.scrim),
            inverseSurface = col(s.inverseSurface), inverseOnSurface = col(s.inverseOnSurface),
            surfaceContainerLowest = col(c.background), surfaceContainerLow = col(control.fillBottom),
            surfaceContainer = col(secondary.fillBottom), surfaceContainerHigh = col(folder.fillBottom),
            surfaceContainerHighest = col(folder.fillTop), surfaceBright = col(folder.fillTop), surfaceDim = col(c.background)
        )
    } else {
        lightColorScheme(
            primary = col(c.accent), onPrimary = col(c.onAccent),
            primaryContainer = col(c.accentContainer), onPrimaryContainer = col(c.onAccentContainer),
            secondary = col(c.secondary), onSecondary = col(c.onAccent),
            secondaryContainer = col(c.surface3), onSecondaryContainer = col(c.onSurface),
            tertiary = col(c.secondary), onTertiary = col(c.onAccent),
            background = col(c.background), onBackground = col(c.onBackground),
            surface = col(secondary.fillBottom), onSurface = col(secondary.content),
            surfaceVariant = col(control.fillBottom), onSurfaceVariant = col(secondary.muted),
            surfaceTint = col(c.accent),
            outline = col(secondary.edgeTop), outlineVariant = col(secondary.edgeBottom),
            error = col(s.danger), onError = col(s.onDanger),
            errorContainer = col(s.dangerContainer), onErrorContainer = col(s.onDangerContainer),
            scrim = col(c.scrim),
            inverseSurface = col(s.inverseSurface), inverseOnSurface = col(s.inverseOnSurface),
            surfaceContainerLowest = col(c.background), surfaceContainerLow = col(control.fillBottom),
            surfaceContainer = col(secondary.fillBottom), surfaceContainerHigh = col(folder.fillBottom),
            surfaceContainerHighest = col(folder.fillTop), surfaceBright = col(folder.fillTop), surfaceDim = col(c.background)
        )
    }
}

/** Font family for a family id, with every bundled weight registered once. */
val InterFamily: FontFamily = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

val SpaceGroteskFamily: FontFamily = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold)
)

fun FontWeightToken.toFontWeight(): FontWeight = when (this) {
    FontWeightToken.LIGHT -> FontWeight.Light
    FontWeightToken.REGULAR -> FontWeight.Normal
    FontWeightToken.MEDIUM -> FontWeight.Medium
    FontWeightToken.SEMIBOLD -> FontWeight.SemiBold
}

fun fontFamilyFor(face: FontFace): FontFamily = fontFamilyFor(face.family)

fun fontFamilyFor(family: FontFamilyId): FontFamily = when (family) {
    FontFamilyId.INTER -> InterFamily
    FontFamilyId.SPACE_GROTESK -> SpaceGroteskFamily
    FontFamilyId.SYSTEM_SANS -> FontFamily.SansSerif
    FontFamilyId.SYSTEM_SERIF -> FontFamily.Serif
    FontFamilyId.SYSTEM_MONO -> FontFamily.Monospace
}

/**
 * The full LumaOS type scale, not Material's defaults with a swapped family.
 *
 * Sizes, line heights, weights and tracking all come from the shared token
 * layer; switching theme changes the voice of each role while keeping the
 * hierarchy identical across the app.
 */
fun typographyFor(style: TypeStyle): Typography {
    val body = fontFamilyFor(style.body)
    val title = fontFamilyFor(style.title)
    val label = fontFamilyFor(style.label)
    val readout = fontFamilyFor(style.readout)
    val clock = fontFamilyFor(style.clock)

    fun line(size: Float, multiplier: Float) = LumaTypeScale.safeLineHeight(size, multiplier).sp

    fun TextStyle.title(weight: FontWeightToken = style.title.weight) = copy(
        fontFamily = title,
        fontWeight = weight.toFontWeight(),
        letterSpacing = style.titleTracking.em
    )

    return Typography(
        displayLarge = TextStyle(
            fontFamily = clock,
            fontWeight = style.clock.weight.toFontWeight(),
            fontSize = style.clockSizeSp.sp,
            lineHeight = line(style.clockSizeSp, LumaTypeScale.displayLineHeight),
            letterSpacing = style.clockTracking.em
        ),
        displayMedium = TextStyle(
            fontFamily = clock,
            fontWeight = style.clock.weight.toFontWeight(),
            fontSize = LumaTypeScale.displaySmallSp.sp,
            lineHeight = line(LumaTypeScale.displaySmallSp, LumaTypeScale.displayLineHeight),
            letterSpacing = style.clockTracking.em
        ),
        displaySmall = TextStyle(
            fontFamily = clock,
            fontWeight = style.clock.weight.toFontWeight(),
            fontSize = 44.sp,
            lineHeight = line(44f, LumaTypeScale.displayLineHeight),
            letterSpacing = style.clockTracking.em
        ),
        headlineLarge = TextStyle(
            fontFamily = title,
            fontWeight = style.title.weight.toFontWeight(),
            fontSize = LumaTypeScale.headlineLargeSp.sp,
            lineHeight = line(LumaTypeScale.headlineLargeSp, LumaTypeScale.headingLineHeight),
            letterSpacing = style.titleTracking.em
        ),
        headlineMedium = TextStyle(
            fontFamily = title,
            fontWeight = style.title.weight.toFontWeight(),
            fontSize = LumaTypeScale.headlineMediumSp.sp,
            lineHeight = line(LumaTypeScale.headlineMediumSp, LumaTypeScale.headingLineHeight),
            letterSpacing = style.titleTracking.em
        ),
        headlineSmall = TextStyle(
            fontFamily = title,
            fontWeight = style.title.weight.toFontWeight(),
            fontSize = LumaTypeScale.headlineSmallSp.sp,
            lineHeight = line(LumaTypeScale.headlineSmallSp, LumaTypeScale.headingLineHeight),
            letterSpacing = style.titleTracking.em
        ),
        titleLarge = TextStyle(
            fontFamily = title,
            fontWeight = style.title.weight.toFontWeight(),
            fontSize = LumaTypeScale.titleLargeSp.sp,
            lineHeight = line(LumaTypeScale.titleLargeSp, LumaTypeScale.titleLineHeight),
            letterSpacing = style.titleTracking.em
        ),
        titleMedium = TextStyle(
            fontFamily = title,
            fontWeight = style.title.weight.toFontWeight(),
            fontSize = LumaTypeScale.titleMediumSp.sp,
            lineHeight = line(LumaTypeScale.titleMediumSp, LumaTypeScale.titleLineHeight),
            letterSpacing = style.titleTracking.em
        ),
        titleSmall = TextStyle(
            fontFamily = title,
            fontWeight = style.title.weight.toFontWeight(),
            fontSize = LumaTypeScale.titleSmallSp.sp,
            lineHeight = line(LumaTypeScale.titleSmallSp, LumaTypeScale.titleLineHeight),
            letterSpacing = style.titleTracking.em
        ),
        bodyLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = LumaTypeScale.bodyLargeSp.sp,
            lineHeight = line(LumaTypeScale.bodyLargeSp, LumaTypeScale.bodyLineHeight),
            letterSpacing = 0.em
        ),
        bodyMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = LumaTypeScale.bodyMediumSp.sp,
            lineHeight = line(LumaTypeScale.bodyMediumSp, LumaTypeScale.bodyLineHeight),
            letterSpacing = 0.em
        ),
        bodySmall = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = LumaTypeScale.bodySmallSp.sp,
            lineHeight = line(LumaTypeScale.bodySmallSp, LumaTypeScale.compactBodyLineHeight),
            letterSpacing = 0.em
        ),
        labelLarge = TextStyle(
            fontFamily = label,
            fontWeight = style.label.weight.toFontWeight(),
            fontSize = LumaTypeScale.labelLargeSp.sp,
            lineHeight = line(LumaTypeScale.labelLargeSp, LumaTypeScale.labelLineHeight),
            letterSpacing = style.labelTracking.em
        ),
        labelMedium = TextStyle(
            fontFamily = readout,
            fontWeight = style.readout.weight.toFontWeight(),
            fontSize = LumaTypeScale.labelMediumSp.sp,
            lineHeight = line(LumaTypeScale.labelMediumSp, LumaTypeScale.labelLineHeight),
            letterSpacing = style.readoutTracking.em
        ),
        labelSmall = TextStyle(
            fontFamily = readout,
            fontWeight = style.readout.weight.toFontWeight(),
            fontSize = LumaTypeScale.labelSmallSp.sp,
            lineHeight = line(LumaTypeScale.labelSmallSp, LumaTypeScale.labelLineHeight),
            letterSpacing = style.readoutTracking.em
        )
    )
}
