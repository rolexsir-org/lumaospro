package com.lumaos.pro.launcher.ui.theme

import com.lumaos.pro.launcher.context.ContextProfile
import kotlin.math.max
import kotlin.math.min

/** How the active theme is chosen over time. */
enum class ThemeSwitching(val storageKey: String) {
    /** Always [ThemeSettings.themeId]. */
    FIXED("fixed"),
    /** Light/dark pair following the system dark-theme setting. */
    SYSTEM("system"),
    /** Light/dark pair switched by a daily time window. */
    SCHEDULE("schedule");

    companion object {
        fun fromStorageKey(raw: String?): ThemeSwitching? = entries.firstOrNull { it.storageKey == raw }
    }
}

/** Daily window during which the dark theme applies, in minutes since midnight. Wraps midnight. */
data class DarkWindow(val startMinute: Int, val endMinute: Int) {

    fun contains(minuteOfDay: Int): Boolean {
        val m = Math.floorMod(minuteOfDay, MINUTES_PER_DAY)
        return when {
            startMinute == endMinute -> false
            startMinute < endMinute -> m in startMinute until endMinute
            else -> m >= startMinute || m < endMinute
        }
    }

    /** Minutes from [minuteOfDay] until the next boundary (1..1440). */
    fun minutesUntilNextChange(minuteOfDay: Int): Int {
        val m = Math.floorMod(minuteOfDay, MINUTES_PER_DAY)
        fun until(target: Int): Int {
            val d = Math.floorMod(target - m, MINUTES_PER_DAY)
            return if (d == 0) MINUTES_PER_DAY else d
        }
        return min(until(startMinute), until(endMinute))
    }

    fun encode(): String = "%02d:%02d-%02d:%02d".format(
        startMinute / 60, startMinute % 60, endMinute / 60, endMinute % 60
    )

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        val DEFAULT = DarkWindow(19 * 60, 7 * 60)

        fun decode(raw: String?): DarkWindow {
            val m = Regex("""^(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})$""").find(raw?.trim().orEmpty())
                ?: return DEFAULT
            val (sh, sm, eh, em) = m.destructured
            val start = sh.toInt() * 60 + sm.toInt()
            val end = eh.toInt() * 60 + em.toInt()
            if (sh.toInt() > 23 || eh.toInt() > 23 || sm.toInt() > 59 || em.toInt() > 59) return DEFAULT
            return DarkWindow(start, end)
        }
    }
}

/** Everything the user has chosen about appearance. Persisted by LumaPrefs. */
data class ThemeSettings(
    val themeId: ThemeId = ThemeId.AURORA,
    val switching: ThemeSwitching = ThemeSwitching.SYSTEM,
    val lightThemeId: ThemeId = ThemeId.PORCELAIN,
    val darkThemeId: ThemeId = ThemeId.AURORA,
    val darkWindow: DarkWindow = DarkWindow.DEFAULT,
    val accentOverride: Int? = null,
    val backgroundOverride: Int? = null,
    val typography: TypeStyle? = null,
    val iconShape: IconShape? = null,
    val iconStyle: IconStyle? = null,
    val backdrop: Backdrop? = null,
    /** Context profiles that force a theme while active (e.g. Sleep -> Obsidian). */
    val profileThemes: Map<ContextProfile, ThemeId> = emptyMap()
) {
    /** Which preset is in force right now. */
    fun effectiveThemeId(systemDark: Boolean, minuteOfDay: Int, profile: ContextProfile?): ThemeId {
        profile?.let { p -> profileThemes[p]?.let { return it } }
        return when (switching) {
            ThemeSwitching.FIXED -> themeId
            ThemeSwitching.SYSTEM -> if (systemDark) darkThemeId else lightThemeId
            ThemeSwitching.SCHEDULE -> if (darkWindow.contains(minuteOfDay)) darkThemeId else lightThemeId
        }
    }

    /** Same overrides, pinned to one preset (used for Studio previews). */
    fun pinned(id: ThemeId): ThemeSettings =
        copy(themeId = id, switching = ThemeSwitching.FIXED, profileThemes = emptyMap())

    /**
     * Select a global preset without discarding custom colors, typography,
     * icon treatment, backdrop, or per-profile choices.
     */
    fun withPreset(id: ThemeId): ThemeSettings = copy(themeId = id, switching = ThemeSwitching.FIXED)
}

data class ThemeEnvironment(
    val systemDark: Boolean,
    val minuteOfDay: Int,
    val profile: ContextProfile? = null,
    val wallpaper: WallpaperSeeds? = null
)

/**
 * Resolves settings + environment into the concrete [ResolvedTheme] that every
 * surface draws with.
 *
 * Contrast guarantees (verified by ThemeEngineTest for every preset, every
 * curated swatch and 300 random user colours):
 *  - text roles never fall below 4.5:1 (WCAG AA) - always achievable;
 *  - the 7.0:1 targets below are met whenever the surface allows it; on a
 *    mid-tone custom colour (e.g. #FF4581, where black reaches only 6.8:1)
 *    the strongest possible colour is used instead.
 *  - onSurface      >= 7.0:1 on surface0 and surface1 (WCAG AAA body text)
 *  - onSurfaceMuted >= 4.5:1 on surface1 and surface2 (WCAG AA)
 *  - onSurfaceFaint >= 3.0:1 on surface1 (non-essential hints)
 *  - accent         >= 3.0:1 on surface1 (WCAG non-text UI components)
 *  - onAccent       >= 4.5:1 on accent
 *  - onBackground   >= 7.0:1 on background
 */
object ThemeEngine {

    fun resolve(settings: ThemeSettings, env: ThemeEnvironment): ResolvedTheme {
        val id = settings.effectiveThemeId(env.systemDark, env.minuteOfDay, env.profile)
        val base = if (id == ThemeId.CHROMA) chromaSpec(env.wallpaper, env.systemDark) else ThemePresets.of(id)
        val background = settings.backgroundOverride?.let { ColorMath.opaque(it) } ?: base.background
        val dark = if (settings.backgroundOverride != null) ColorMath.isDark(background) else base.dark
        val accent = settings.accentOverride?.let { ColorMath.opaque(it) } ?: base.accent
        val backdrop = settings.backdrop ?: base.defaultBackdrop
        val amoled = base.amoled && background == ColorMath.BLACK
        val spec = base.copy(
            dark = dark,
            amoled = amoled,
            background = background,
            accent = accent,
            // A custom background lends its own hue to the neutral surfaces so
            // the hierarchy reads as one material.
            neutralTint = if (settings.backgroundOverride != null) background else base.neutralTint,
            neutralChroma = if (settings.backgroundOverride != null) {
                min(Oklch.fromArgb(background).c, 0.03)
            } else base.neutralChroma
        )
        return ResolvedTheme(
            id = id,
            displayName = base.displayName,
            colors = deriveColors(spec, backdrop, env.wallpaper),
            typography = settings.typography ?: base.typography,
            iconShape = settings.iconShape ?: base.iconShape,
            iconStyle = settings.iconStyle ?: base.iconStyle,
            atmosphere = base.atmosphere,
            surfaceStyle = base.surfaceStyle,
            backdrop = backdrop,
            cornerScale = base.cornerScale
        )
    }

    /** Wallpaper-derived spec. Falls back to the static Chroma seeds when colours are unknown. */
    fun chromaSpec(seeds: WallpaperSeeds?, systemDark: Boolean): ThemeSpec {
        val base = ThemePresets.CHROMA
        if (seeds == null) {
            return if (systemDark) base else base.copy(dark = false, background = 0xFFF3F5F8.toInt())
        }
        val p = Oklch.fromArgb(seeds.primary)
        val dark = seeds.supportsDarkText?.let { !it } ?: systemDark
        val hue = p.h
        val chroma = p.c.coerceIn(0.04, 0.16)
        val secondaryHue = seeds.secondary
            ?.let { Oklch.fromArgb(it) }
            ?.takeIf { it.c > 0.02 && Oklch.hueDistance(it.h, hue) > 0.35 }
            ?.h ?: (hue + 1.0)
        return base.copy(
            dark = dark,
            background = if (dark) Oklch(0.17, min(chroma * 0.25, 0.03), hue).toArgb()
            else Oklch(0.965, min(chroma * 0.12, 0.015), hue).toArgb(),
            accent = if (dark) Oklch(0.82, min(chroma * 1.2, 0.14), hue).toArgb()
            else Oklch(0.46, min(chroma * 1.3, 0.15), hue).toArgb(),
            secondary = if (dark) Oklch(0.80, 0.09, secondaryHue).toArgb()
            else Oklch(0.50, 0.10, secondaryHue).toArgb(),
            neutralTint = seeds.primary,
            neutralChroma = min(chroma * 0.2, 0.03)
        )
    }

    private fun deriveColors(spec: ThemeSpec, backdrop: Backdrop, wallpaper: WallpaperSeeds?): ThemeColors {
        val tint = Oklch.fromArgb(spec.neutralTint)
        fun neutral(l: Double, chromaScale: Double = 1.0): Int =
            Oklch(l.coerceIn(0.0, 1.0), spec.neutralChroma * chromaScale, tint.h).toArgb()

        val bg = spec.background
        val bgL = Oklch.fromArgb(bg).l
        // Surfaces are derived from a base kept clear of the light/dark
        // crossover (~18% luminance). Without this, a mid-tone custom
        // background (e.g. #841E99) yields surfaces on both sides of it, and
        // no single text colour can reach 4.5:1 on all of them. Presets sit
        // far from the crossover and are unaffected.
        val baseL = if (spec.dark) min(bgL, DARK_SURFACE_BASE_MAX) else max(bgL, LIGHT_SURFACE_BASE_MIN)
        val accentLch = Oklch.fromArgb(spec.accent)

        val surface0: Int
        val surface1: Int
        val surface2: Int
        val surface3: Int
        var onSurface: Int
        var muted: Int
        var faint: Int
        val outline: Int
        val outlineStrong: Int
        val scrim: Int
        val pressed: Int
        if (spec.dark) {
            if (spec.amoled) {
                surface0 = ColorMath.BLACK
                surface1 = neutral(0.13)
                surface2 = neutral(0.18)
                surface3 = neutral(0.23)
            } else {
                surface0 = neutral(baseL + 0.035)
                surface1 = neutral(baseL + 0.075)
                surface2 = neutral(baseL + 0.12)
                surface3 = neutral(baseL + 0.17)
            }
            onSurface = neutral(0.955, 0.5)
            muted = neutral(0.77, 0.8)
            faint = neutral(0.60)
            outline = neutral(min(baseL + 0.20, 0.42))
            outlineStrong = neutral(min(baseL + 0.32, 0.55))
            scrim = ColorMath.withAlpha(ColorMath.BLACK, 0.56f)
            pressed = ColorMath.withAlpha(ColorMath.WHITE, 0.10f)
        } else {
            surface0 = neutral(min(baseL + 0.025, 0.995), 0.4)
            surface1 = neutral(min(baseL + 0.03, 1.0), 0.3)
            surface2 = neutral(baseL - 0.035)
            surface3 = neutral(baseL - 0.075)
            onSurface = neutral(0.22, 1.2)
            muted = neutral(0.44)
            faint = neutral(0.60)
            outline = neutral(baseL - 0.11)
            outlineStrong = neutral(baseL - 0.22)
            scrim = ColorMath.withAlpha(neutral(0.18), 0.38f)
            pressed = ColorMath.withAlpha(ColorMath.BLACK, 0.08f)
        }

        onSurface = ensureOnAll(onSurface, 7.0, surface0, surface1)
        muted = ensureOnAll(muted, 4.5, surface1, surface2)
        faint = ensureOnAll(faint, 3.0, surface1)
        val onBackground = ColorMath.ensureContrast(onSurface, bg, 7.0)

        val accent = ensureOnAll(spec.accent, 3.0, surface1)
        val onAccentCandidate = if (ColorMath.isDark(accent)) neutral(0.97, 0.3)
        else Oklch(0.18, accentLch.c * 0.4, accentLch.h).toArgb()
        val onAccent = ColorMath.ensureContrast(onAccentCandidate, accent, 4.5)
        val accentContainer = if (spec.dark) Oklch(0.32, accentLch.c * 0.55, accentLch.h).toArgb()
        else Oklch(0.90, accentLch.c * 0.35, accentLch.h).toArgb()
        val onAccentContainer = ColorMath.ensureContrast(
            if (spec.dark) Oklch(0.92, accentLch.c * 0.5, accentLch.h).toArgb()
            else Oklch(0.24, accentLch.c * 0.7, accentLch.h).toArgb(),
            accentContainer, 4.5
        )
        val secondary = ensureOnAll(spec.secondary, 3.0, surface1)
        val danger = ColorMath.ensureContrast(
            if (spec.dark) 0xFFFF8A80.toInt() else 0xFFB3261E.toInt(), surface1, 4.5
        )

        val homeForeground: Int
        val homeMuted: Int
        val homeShadow: Int
        if (backdrop == Backdrop.THEME) {
            homeForeground = onBackground
            homeMuted = ColorMath.ensureContrast(muted, bg, 4.5)
            homeShadow = ColorMath.withAlpha(if (spec.dark) ColorMath.BLACK else ColorMath.WHITE, 0.30f)
        } else {
            // Over an arbitrary wallpaper: pick the text colour from the
            // wallpaper's own light/dark hint and back it with a shadow.
            val lightWallpaper = wallpaper?.supportsDarkText ?: false
            homeForeground = if (lightWallpaper) 0xFF15171A.toInt() else ColorMath.WHITE
            homeMuted = ColorMath.withAlpha(homeForeground, 0.80f)
            homeShadow = if (lightWallpaper) ColorMath.withAlpha(ColorMath.WHITE, 0.60f)
            else ColorMath.withAlpha(ColorMath.BLACK, 0.62f)
        }

        return ThemeColors(
            isDark = spec.dark,
            background = bg,
            onBackground = onBackground,
            surface0 = surface0,
            surface1 = surface1,
            surface2 = surface2,
            surface3 = surface3,
            onSurface = onSurface,
            onSurfaceMuted = muted,
            onSurfaceFaint = faint,
            outline = outline,
            outlineStrong = outlineStrong,
            accent = accent,
            onAccent = onAccent,
            accentContainer = accentContainer,
            onAccentContainer = onAccentContainer,
            secondary = secondary,
            glow = ColorMath.withAlpha(accent, 0.22f),
            scrim = scrim,
            homeForeground = homeForeground,
            homeMuted = homeMuted,
            homeShadow = homeShadow,
            pressedOverlay = pressed,
            selectedFill = ColorMath.withAlpha(accent, if (spec.dark) 0.18f else 0.14f),
            focusRing = accent,
            ripple = ColorMath.withAlpha(accent, 0.24f),
            danger = danger
        )
    }

    /** Adjusts [fg] until it meets [ratio] against every surface in [backgrounds]. */
    private fun ensureOnAll(fg: Int, ratio: Double, vararg backgrounds: Int): Int {
        var c = fg
        // A few passes converge: each pass only moves lightness away from the
        // worst background, never back towards one already satisfied.
        repeat(3) {
            val worst = backgrounds.minByOrNull { ColorMath.contrast(ColorMath.opaque(c), ColorMath.opaque(it)) }
                ?: return c
            c = ColorMath.ensureContrast(c, worst, ratio)
        }
        return c
    }

    /** Maximum of two WCAG ratios, exposed for tests and the Studio's contrast badge. */
    fun bestTextOn(background: Int): Int =
        if (ColorMath.contrast(ColorMath.WHITE, background) >= ColorMath.contrast(ColorMath.BLACK, background))
            ColorMath.WHITE else ColorMath.BLACK

    /** Highest OKLCH lightness a dark theme's surface base may have (white text stays >= 7:1 on surface1). */
    const val DARK_SURFACE_BASE_MAX = 0.38

    /** Lowest OKLCH lightness a light theme's surface base may have (black text stays >= 7:1 on surface0/1). */
    const val LIGHT_SURFACE_BASE_MIN = 0.66
}
