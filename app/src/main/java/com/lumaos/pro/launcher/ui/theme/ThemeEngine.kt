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
    val profileThemes: Map<ContextProfile, ThemeId> = emptyMap(),
    /**
     * When true, derives surface, background, border and accent seeds from the
     * active wallpaper while preserving the chosen [ThemeId]'s geometry, blur,
     * typography and motion character. Turning this off returns cleanly to the
     * preset and any manual [accentOverride] / [backgroundOverride].
     */
    val dynamicWallpaperColors: Boolean = false
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

    /** Toggle automatic wallpaper-derived theming while preserving preset identity. */
    fun withDynamicWallpaperColors(enabled: Boolean): ThemeSettings =
        copy(dynamicWallpaperColors = enabled)
}

data class ThemeEnvironment(
    val systemDark: Boolean,
    val minuteOfDay: Int,
    val profile: ContextProfile? = null,
    val wallpaper: WallpaperSeeds? = null,
    /** True when an explicit Wallpaper Studio artwork or photo is active for Luma Home. */
    val hasSelectedArtwork: Boolean = false
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
        val rawBase = ThemePresets.of(id)
        val dynamicActive = id == ThemeId.CHROMA || (settings.dynamicWallpaperColors && env.wallpaper != null)
        val base = if (dynamicActive) {
            wallpaperSpec(rawBase, env.wallpaper, env.systemDark)
        } else {
            rawBase
        }
        val background = settings.backgroundOverride?.let { ColorMath.opaque(it) } ?: base.background
        val dark = if (settings.backgroundOverride != null) ColorMath.isDark(background) else base.dark
        val accent = settings.accentOverride?.let { ColorMath.opaque(it) } ?: base.accent
        // LUMA is meaningful for themes with bundled artwork or when the user has
        // chosen an active Wallpaper Studio item. An older preference without
        // either falls back to the theme's own atmosphere instead of a blank frame.
        val requestedBackdrop = settings.backdrop ?: base.defaultBackdrop
        val hasLumaArt = WallpaperArt.forTheme(id) != null || env.hasSelectedArtwork
        val backdrop = if (requestedBackdrop == Backdrop.LUMA && !hasLumaArt) {
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
        // Authored v3.2 roles are used whenever dynamic wallpaper derivation is off
        // and the user has not replaced the theme's own accent or background;
        // dynamic or custom colours use the seed-derived path, which guarantees
        // contrast for arbitrary inputs while keeping the theme's geometry/blur tokens.
        val tokens = base.tokens
        val curated = !dynamicActive && settings.accentOverride == null && settings.backgroundOverride == null
        val colors = if (tokens != null && curated) {
            authoredColors(spec, tokens, backdrop, env.wallpaper, env.hasSelectedArtwork)
        } else {
            deriveColors(spec, backdrop, env.wallpaper, env.hasSelectedArtwork)
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
            tokens = tokens,
            wallpaperSeeds = env.wallpaper
        )
    }

    /** Wallpaper-derived spec for [ThemePresets.CHROMA]. */
    fun chromaSpec(seeds: WallpaperSeeds?, systemDark: Boolean): ThemeSpec =
        wallpaperSpec(ThemePresets.CHROMA, seeds, systemDark)

    /**
     * Derives coordinated dark/light polarity, unmuddied neutral surfaces, background
     * and vivid-yet-controlled accent seeds from [seeds] while preserving [base]'s
     * typography, motion character, geometry, and blur material.
     */
    fun wallpaperSpec(base: ThemeSpec, seeds: WallpaperSeeds?, systemDark: Boolean): ThemeSpec {
        if (seeds == null) {
            return if (base.id == ThemeId.CHROMA && !systemDark) {
                base.copy(dark = false, background = 0xFFF3F5F8.toInt())
            } else {
                base
            }
        }
        val dom = Oklch.fromArgb(ColorMath.opaque(seeds.primary))
        val dark = seeds.supportsDarkText?.let { !it } ?: systemDark
        val accentSource = ColorMath.opaque(seeds.accent ?: seeds.primary)
        val accLch = Oklch.fromArgb(accentSource)
        val isMonochrome = accLch.c < 0.022 && dom.c < 0.022
        val accentHue = if (accLch.c >= 0.015) accLch.h else dom.h
        val domHue = if (dom.c >= 0.012) dom.h else accentHue

        // Warm yellow/ochre hues (~55..125 deg -> 0.96..2.18 rad) turn muddy olive
        // if darkened at moderate chroma, so tighten neutral chroma in that band.
        val normDomHue = ((domHue % (Math.PI * 2.0)) + Math.PI * 2.0) % (Math.PI * 2.0)
        val warmOchreZone = normDomHue in 0.96..2.18
        val maxNeutralChroma = when {
            isMonochrome -> 0.005
            dark && warmOchreZone -> 0.013
            dark -> 0.024
            warmOchreZone -> 0.011
            else -> 0.016
        }
        val neutralChroma = if (isMonochrome) {
            min(dom.c * 0.4, 0.005)
        } else {
            (max(dom.c, accLch.c * 0.45) * if (dark) 0.22 else 0.16).coerceIn(0.006, maxNeutralChroma)
        }

        val keepAmoled = base.amoled && dark && dom.l < 0.12
        val background = when {
            keepAmoled -> ColorMath.BLACK
            dark -> {
                val bgL = (0.115 + dom.l.coerceIn(0.0, 0.35) * 0.14).coerceIn(0.115, 0.165)
                Oklch(bgL, min(neutralChroma * 1.15, 0.026), domHue).toArgb()
            }
            else -> Oklch(0.965, min(neutralChroma, 0.014), domHue).toArgb()
        }

        val accent = if (isMonochrome) {
            val monoC = max(accLch.c, dom.c).coerceIn(0.006, 0.024)
            if (dark) Oklch(0.84, monoC, accentHue).toArgb()
            else Oklch(0.38, monoC, accentHue).toArgb()
        } else {
            val rawC = max(accLch.c, 0.055)
            if (dark) Oklch(0.81, (rawC * 1.22).coerceIn(0.075, 0.150), accentHue).toArgb()
            else Oklch(0.46, (rawC * 1.28).coerceIn(0.080, 0.155), accentHue).toArgb()
        }

        val secondaryCandidate = listOfNotNull(seeds.secondary, seeds.tertiary)
            .map { Oklch.fromArgb(ColorMath.opaque(it)) }
            .firstOrNull { it.c > 0.022 && Oklch.hueDistance(it.h, accentHue) > 0.35 }
        val secondaryHue = secondaryCandidate?.h ?: (accentHue + 0.85)
        val secondaryChroma = if (isMonochrome) 0.016 else (secondaryCandidate?.c?.coerceIn(0.06, 0.12) ?: 0.09)
        val secondary = if (dark) Oklch(0.80, secondaryChroma, secondaryHue).toArgb()
        else Oklch(0.50, min(secondaryChroma + 0.01, 0.12), secondaryHue).toArgb()

        val neutralTint = Oklch(if (dark) 0.50 else 0.65, neutralChroma.coerceAtLeast(0.005), domHue).toArgb()
        return base.copy(
            dark = dark,
            amoled = keepAmoled,
            background = background,
            accent = accent,
            secondary = secondary,
            neutralTint = neutralTint,
            neutralChroma = neutralChroma
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
        wallpaper: WallpaperSeeds?,
        hasSelectedArtwork: Boolean = false
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
            hasSelectedArtwork = hasSelectedArtwork,
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

    private fun deriveColors(
        spec: ThemeSpec,
        backdrop: Backdrop,
        wallpaper: WallpaperSeeds?,
        hasSelectedArtwork: Boolean = false
    ): ThemeColors {
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
            hasSelectedArtwork = hasSelectedArtwork,
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
        hasSelectedArtwork: Boolean = false,
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
                if (hasSelectedArtwork) {
                    wallpaper?.supportsDarkText ?: WallpaperArt.forTheme(spec.id)?.supportsDarkText ?: false
                } else {
                    WallpaperArt.forTheme(spec.id)?.supportsDarkText ?: false
                }
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
