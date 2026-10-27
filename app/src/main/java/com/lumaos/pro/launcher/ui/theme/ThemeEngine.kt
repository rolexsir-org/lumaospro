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
    val themeId: ThemeId = ThemeId.SIGNATURE,
    val switching: ThemeSwitching = ThemeSwitching.SYSTEM,
    /**
     * Light mode keeps the retained Zen palette as the light adaptation of the
     * single Luma Glass system. The palette IDs are compatibility anchors, not
     * separate design systems; the glass material layer supplies one geometry,
     * one surface grammar and one motion vocabulary in both modes.
     */
    val lightThemeId: ThemeId = ThemeId.PORCELAIN,
    val darkThemeId: ThemeId = ThemeId.SIGNATURE,
    val darkWindow: DarkWindow = DarkWindow.DEFAULT,
    val accentOverride: Int? = null,
    val backgroundOverride: Int? = null,
    val typography: TypeStyle? = null,
    val iconShape: IconShape? = null,
    val iconStyle: IconStyle? = null,
    /** Luma Glass defaults to the real system wallpaper, with a legibility scrim. */
    val backdrop: Backdrop? = Backdrop.WALLPAPER,
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
        // LUMA is only meaningful for themes with bundled artwork. A backup or
        // an older preference that asks for it elsewhere falls back to the
        // theme's own atmosphere instead of a blank backdrop.
        val requestedBackdrop = settings.backdrop ?: base.defaultBackdrop
        val backdrop = if (requestedBackdrop == Backdrop.LUMA && WallpaperArt.forTheme(id) == null) {
            Backdrop.THEME
        } else {
            requestedBackdrop
        }
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
        // Authored v3.2 roles are used whenever the user has not replaced the
        // theme's own accent or background; a custom colour falls back to the
        // seed-derived path, which can guarantee contrast for any input.
        val tokens = base.tokens
        val curated = settings.accentOverride == null && settings.backgroundOverride == null
        val colors = if (tokens != null && curated) {
            authoredColors(spec, tokens, backdrop, env.wallpaper)
        } else {
            deriveColors(spec, backdrop, env.wallpaper)
        }
        return ResolvedTheme(
            id = id,
            displayName = base.displayName,
            colors = colors,
            typography = settings.typography ?: base.typography,
            motionCharacter = base.motionCharacter,
            iconShape = settings.iconShape ?: base.iconShape,
            iconStyle = settings.iconStyle ?: base.iconStyle,
            atmosphere = base.atmosphere,
            surfaceStyle = base.surfaceStyle,
            backdrop = backdrop,
            cornerScale = base.cornerScale,
            tokens = tokens
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

    /** Raw text roles before the contrast guarantees are applied. */
    private data class RawText(val onSurface: Int, val muted: Int, val faint: Int)

    /** Hue source + chroma shared by both colour paths, so neutrals behave alike. */
    private fun neutralFn(spec: ThemeSpec): (Double, Double) -> Int {
        val tint = Oklch.fromArgb(spec.neutralTint)
        return { l, chromaScale ->
            Oklch(l.coerceIn(0.0, 1.0), spec.neutralChroma * chromaScale, tint.h).toArgb()
        }
    }

    /**
     * Authored v3.2 colours.
     *
     * The token bundle states the exact roles, so these are *not* regenerated from
     * a seed: `surface-1` is the authored translucent white over the authored
     * `bg-1`, `text-2` is the authored grey, and so on. The contrast guarantees
     * below still run, because the specification promises them (text-1 >= 15:1,
     * text-2 >= 7:1, text-3 >= 4.6:1) and a hand-edited token file must not be
     * able to ship illegible text.
     */
    private fun authoredColors(
        spec: ThemeSpec,
        tokens: ThemeTokens,
        backdrop: Backdrop,
        wallpaper: WallpaperSeeds?
    ): ThemeColors {
        val t = tokens.colors
        val surface0 = ColorMath.opaque(t.background1)
        // Translucent authored roles are composited over the panel they sit on:
        // surface-1/2/3 are rows, controls and active states, all on surface0.
        val surface1 = ColorMath.composite(t.surface1, surface0)
        val surface2 = ColorMath.composite(t.surface2, surface0)
        val surface3 = ColorMath.composite(t.surface3, surface0)
        return assemble(
            spec = spec,
            backdrop = backdrop,
            wallpaper = wallpaper,
            neutral = neutralFn(spec),
            surfaces = intArrayOf(surface0, surface1, surface2, surface3),
            text = RawText(ColorMath.opaque(t.text1), ColorMath.opaque(t.text2), ColorMath.opaque(t.text3)),
            accentSeed = ColorMath.opaque(t.accent),
            secondarySeed = ColorMath.opaque(t.accent2),
            dangerSeed = ColorMath.opaque(t.danger),
            outline = ColorMath.composite(t.stroke1, surface0),
            outlineStrong = ColorMath.composite(t.stroke2, surface0),
            scrim = ColorMath.withAlpha(ColorMath.BLACK, 0.42f),
            pressed = ColorMath.withAlpha(ColorMath.WHITE, 0.10f),
            // Authored selection: the accent-soft role, made opaque for a fill.
            selectedFill = ColorMath.composite(t.accentSoft, surface0),
            successSeed = ColorMath.opaque(t.ok),
            warningSeed = ColorMath.opaque(t.warn)
        )
    }

    private fun deriveColors(spec: ThemeSpec, backdrop: Backdrop, wallpaper: WallpaperSeeds?): ThemeColors {
        val neutral = neutralFn(spec)
        val bg = spec.background
        val bgL = Oklch.fromArgb(bg).l
        // Surfaces are derived from a base kept clear of the light/dark
        // crossover (~18% luminance). Without this, a mid-tone custom
        // background (e.g. #841E99) yields surfaces on both sides of it, and
        // no single text colour can reach 4.5:1 on all of them. Presets sit
        // far from the crossover and are unaffected.
        val baseL = if (spec.dark) min(bgL, DARK_SURFACE_BASE_MAX) else max(bgL, LIGHT_SURFACE_BASE_MIN)

        val surface0: Int
        val surface1: Int
        val surface2: Int
        val surface3: Int
        val onSurface: Int
        val muted: Int
        val faint: Int
        val outline: Int
        val outlineStrong: Int
        val scrim: Int
        val pressed: Int
        if (spec.dark) {
            if (spec.amoled) {
                surface0 = ColorMath.BLACK
                surface1 = neutral(0.13, 1.0)
                surface2 = neutral(0.18, 1.0)
                surface3 = neutral(0.23, 1.0)
            } else {
                surface0 = neutral(baseL + 0.035, 1.0)
                surface1 = neutral(baseL + 0.075, 1.0)
                surface2 = neutral(baseL + 0.12, 1.0)
                surface3 = neutral(baseL + 0.17, 1.0)
            }
            onSurface = neutral(0.955, 0.5)
            muted = neutral(0.77, 0.8)
            faint = neutral(0.60, 1.0)
            outline = neutral(min(baseL + 0.20, 0.42), 1.0)
            outlineStrong = neutral(min(baseL + 0.32, 0.55), 1.0)
            scrim = ColorMath.withAlpha(ColorMath.BLACK, 0.56f)
            pressed = ColorMath.withAlpha(ColorMath.WHITE, 0.10f)
        } else {
            surface0 = neutral(min(baseL + 0.025, 0.995), 0.4)
            surface1 = neutral(min(baseL + 0.03, 1.0), 0.3)
            surface2 = neutral(baseL - 0.035, 1.0)
            surface3 = neutral(baseL - 0.075, 1.0)
            onSurface = neutral(0.22, 1.2)
            muted = neutral(0.44, 1.0)
            faint = neutral(0.60, 1.0)
            outline = neutral(baseL - 0.11, 1.0)
            outlineStrong = neutral(baseL - 0.22, 1.0)
            scrim = ColorMath.withAlpha(neutral(0.18, 1.0), 0.38f)
            pressed = ColorMath.withAlpha(ColorMath.BLACK, 0.08f)
        }
        return assemble(
            spec = spec,
            backdrop = backdrop,
            wallpaper = wallpaper,
            neutral = neutral,
            surfaces = intArrayOf(surface0, surface1, surface2, surface3),
            text = RawText(onSurface, muted, faint),
            accentSeed = spec.accent,
            secondarySeed = spec.secondary,
            dangerSeed = if (spec.dark) 0xFFFF8A80.toInt() else 0xFFB3261E.toInt(),
            outline = outline,
            outlineStrong = outlineStrong,
            scrim = scrim,
            pressed = pressed,
            selectedFill = ColorMath.withAlpha(spec.accent, if (spec.dark) 0.18f else 0.14f)
        )
    }

    /**
     * Shared tail of both colour paths: applies the published contrast
     * guarantees, derives the accent/on-accent pair from the seed, decides how
     * text sits over artwork, and assembles [ThemeColors].
     */
    private fun assemble(
        spec: ThemeSpec,
        backdrop: Backdrop,
        wallpaper: WallpaperSeeds?,
        neutral: (Double, Double) -> Int,
        surfaces: IntArray,
        text: RawText,
        accentSeed: Int,
        secondarySeed: Int,
        dangerSeed: Int,
        outline: Int,
        outlineStrong: Int,
        scrim: Int,
        pressed: Int,
        selectedFill: Int,
        successSeed: Int? = null,
        warningSeed: Int? = null
    ): ThemeColors {
        val surface0 = surfaces[0]
        val surface1 = surfaces[1]
        val surface2 = surfaces[2]
        val surface3 = surfaces[3]
        val bg = spec.background

        val onSurface = ensureOnAll(text.onSurface, 7.0, surface0, surface1)
        val muted = ensureOnAll(text.muted, 4.5, surface1, surface2)
        val faint = ensureOnAll(text.faint, 3.0, surface1)
        val onBackground = ColorMath.ensureContrast(onSurface, bg, 7.0)

        val accentLch = Oklch.fromArgb(accentSeed)
        val accent = ensureOnAll(accentSeed, 3.0, surface1)
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
        val secondary = ensureOnAll(secondarySeed, 3.0, surface1)
        val danger = ColorMath.ensureContrast(dangerSeed, surface1, 4.5)

        val homeForeground: Int
        val homeMuted: Int
        val homeShadow: Int
        if (backdrop == Backdrop.THEME) {
            homeForeground = onBackground
            homeMuted = ColorMath.ensureContrast(muted, bg, 4.5)
            homeShadow = ColorMath.withAlpha(if (spec.dark) ColorMath.BLACK else ColorMath.WHITE, 0.30f)
        } else {
            // Over a wallpaper: pick the text colour from the artwork's own
            // light/dark property (bundled art knows itself; system wallpaper
            // provides a platform hint when available) and back it with a shadow.
            val lightWallpaper = if (backdrop == Backdrop.LUMA) {
                WallpaperArt.forTheme(spec.id)?.supportsDarkText ?: false
            } else {
                wallpaper?.supportsDarkText ?: false
            }
            homeForeground = if (lightWallpaper) 0xFF15171A.toInt() else ColorMath.WHITE
            homeMuted = ColorMath.withAlpha(homeForeground, 0.80f)
            homeShadow = if (lightWallpaper) ColorMath.withAlpha(ColorMath.WHITE, 0.62f)
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
            selectedFill = selectedFill,
            focusRing = accent,
            ripple = ColorMath.withAlpha(accent, 0.24f),
            danger = danger,
            successSeed = successSeed,
            warningSeed = warningSeed
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
