package com.lumaos.pro.launcher.ui.theme

import com.lumaos.pro.launcher.wallpaper.WallpaperCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive accessibility, polarity, glass-legibility, and fallback verification
 * across all 30 bundled LumaOS Pro wallpapers and all 9 ThemeIds.
 */
class DynamicWallpaperThemeMatrixTest {

    @Test
    fun `all 30 bundled wallpapers across all 9 themes meet WCAG contrast in dynamic and manual modes`() {
        val bundled = WallpaperCatalog.bundled()
        assertEquals(30, bundled.size)

        for (entry in bundled) {
            val seeds = requireNotNull(entry.precomputedSeeds) { "Missing seeds on ${entry.id}" }
            for (sysDark in listOf(true, false)) {
                val env = ThemeEnvironment(
                    systemDark = sysDark,
                    minuteOfDay = if (sysDark) 23 * 60 else 11 * 60,
                    wallpaper = seeds,
                    hasSelectedArtwork = true
                )
                for (themeId in ThemeId.entries) {
                    for (dynamic in listOf(true, false)) {
                        val settings = ThemeSettings(
                            themeId = themeId,
                            switching = ThemeSwitching.FIXED,
                            backdrop = Backdrop.WALLPAPER,
                            dynamicWallpaperColors = dynamic
                        )
                        val resolved = ThemeEngine.resolve(settings, env)
                        val c = resolved.colors
                        val tag = "${entry.id}/$themeId/dyn=$dynamic/sysDark=$sysDark"

                        assertTrue(
                            "$tag onBackground/background",
                            ColorMath.contrast(c.onBackground, c.background) >= 7.0
                        )
                        assertTrue(
                            "$tag onSurface/surface1",
                            ColorMath.contrast(c.onSurface, c.surface1) >= 7.0
                        )
                        assertTrue(
                            "$tag onSurfaceMuted/surface1",
                            ColorMath.contrast(c.onSurfaceMuted, c.surface1) >= 4.5
                        )
                        assertTrue(
                            "$tag accent/surface1",
                            ColorMath.contrast(c.accent, c.surface1) >= 3.0
                        )
                        assertTrue(
                            "$tag onAccent/accent",
                            ColorMath.contrast(c.onAccent, c.accent) >= 4.5
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `warm ivory and lavender minimal wallpapers derive calm daylight themes without muddying`() {
        val ivory = WallpaperCatalog.findBundled("art:14_warm_ivory_curves")!!
        val env = ThemeEnvironment(
            systemDark = true,
            minuteOfDay = 12 * 60,
            wallpaper = ivory.precomputedSeeds,
            hasSelectedArtwork = true
        )
        val settings = ThemeSettings(
            themeId = ThemeId.SIGNATURE,
            switching = ThemeSwitching.FIXED,
            backdrop = Backdrop.WALLPAPER,
            dynamicWallpaperColors = true
        )
        val resolved = ThemeEngine.resolve(settings, env)
        assertFalse("Daylight ivory wallpaper switches dynamic theme to light surfaces", resolved.isDark)
        assertTrue("Background remains clean high-luminance daylight", ColorMath.luminance(resolved.colors.background) > 0.80)
        // Neutral surface channels stay balanced (never neon yellow).
        val bg = resolved.colors.background
        val channelSpread = maxOf(ColorMath.red(bg), ColorMath.green(bg), ColorMath.blue(bg)) -
            minOf(ColorMath.red(bg), ColorMath.green(bg), ColorMath.blue(bg))
        assertTrue("Warm ivory surface tint stays restrained (spread=$channelSpread)", channelSpread <= 28)
    }

    @Test
    fun `amoled obsidian retains pure black background when dynamic wallpaper colors are enabled`() {
        val emerald = WallpaperCatalog.findBundled("art:05_emerald_northern_lights")!!
        val env = ThemeEnvironment(
            systemDark = true,
            minuteOfDay = 0,
            wallpaper = emerald.precomputedSeeds,
            hasSelectedArtwork = true
        )
        val resolved = ThemeEngine.resolve(
            ThemeSettings(
                themeId = ThemeId.OBSIDIAN,
                switching = ThemeSwitching.FIXED,
                dynamicWallpaperColors = true
            ),
            env
        )
        assertEquals(0xFF000000.toInt(), resolved.colors.background)
        // Accent adapts toward emerald green rather than Obsidian's default blue.
        assertTrue(ColorMath.green(resolved.colors.accent) > ColorMath.red(resolved.colors.accent))
    }

    @Test
    fun `disabling dynamic wallpaper colors restores exact manual preset palette`() {
        val sunrise = WallpaperCatalog.findBundled("art:17_snow_peaks_at_sunrise")!!
        val env = ThemeEnvironment(
            systemDark = true,
            minuteOfDay = 22 * 60,
            wallpaper = sunrise.precomputedSeeds,
            hasSelectedArtwork = true
        )
        val manual = ThemeSettings(
            themeId = ThemeId.SIGNATURE,
            switching = ThemeSwitching.FIXED,
            backdrop = Backdrop.THEME,
            dynamicWallpaperColors = false
        )
        val toggledOn = manual.withDynamicWallpaperColors(true)
        val toggledBackOff = toggledOn.withDynamicWallpaperColors(false)
        assertEquals(ThemeEngine.resolve(manual, env).colors, ThemeEngine.resolve(toggledBackOff, env).colors)
    }

    @Test
    fun `split horizon sunrise wallpaper reinforces upper clock scrim while keeping center clear`() {
        val sunrise = WallpaperCatalog.findBundled("art:17_snow_peaks_at_sunrise")!!
        val obsidian = WallpaperCatalog.findBundled("art:09_obsidian_glass")!!
        val settings = ThemeSettings(backdrop = Backdrop.WALLPAPER, dynamicWallpaperColors = true)

        val sunriseTheme = ThemeEngine.resolve(
            settings,
            ThemeEnvironment(systemDark = true, minuteOfDay = 0, wallpaper = sunrise.precomputedSeeds)
        )
        val obsidianTheme = ThemeEngine.resolve(
            settings,
            ThemeEnvironment(systemDark = true, minuteOfDay = 0, wallpaper = obsidian.precomputedSeeds)
        )
        val sunriseScrim = GlassBackdrop.scrim(sunriseTheme)
        val obsidianScrim = GlassBackdrop.scrim(obsidianTheme)

        assertTrue(
            "Bright top sky reinforces top veil (${ColorMath.alpha(sunriseScrim.top)} > ${ColorMath.alpha(obsidianScrim.top)})",
            ColorMath.alpha(sunriseScrim.top) > ColorMath.alpha(obsidianScrim.top)
        )
        assertTrue(ColorMath.alpha(sunriseScrim.center) <= 4)
    }

    @Test
    fun `cross polarity manual theme over opposing wallpaper keeps Luma Glass legible`() {
        // Manual dark Signature pinned over bright Warm Ivory Curves
        val ivory = WallpaperCatalog.findBundled("art:14_warm_ivory_curves")!!
        val darkOverLight = ThemeEngine.resolve(
            ThemeSettings(
                themeId = ThemeId.SIGNATURE,
                switching = ThemeSwitching.FIXED,
                backdrop = Backdrop.WALLPAPER,
                dynamicWallpaperColors = false
            ),
            ThemeEnvironment(systemDark = false, minuteOfDay = 12 * 60, wallpaper = ivory.precomputedSeeds)
        )
        val darkGlass = GlassMaterials.palette(darkOverLight)[GlassRole.SEARCH]
        val compositedOverWhite = ColorMath.composite(darkGlass.rest.fillBottom, ColorMath.WHITE)
        assertTrue(
            "Dark glass over pure white wallpaper stays legible",
            ColorMath.contrast(darkGlass.rest.content, compositedOverWhite) >= 4.5
        )

        // Manual light Porcelain pinned over dark Obsidian Glass
        val obsidian = WallpaperCatalog.findBundled("art:09_obsidian_glass")!!
        val lightOverDark = ThemeEngine.resolve(
            ThemeSettings(
                themeId = ThemeId.PORCELAIN,
                switching = ThemeSwitching.FIXED,
                backdrop = Backdrop.WALLPAPER,
                dynamicWallpaperColors = false
            ),
            ThemeEnvironment(systemDark = true, minuteOfDay = 0, wallpaper = obsidian.precomputedSeeds)
        )
        val lightGlass = GlassMaterials.palette(lightOverDark)[GlassRole.SEARCH]
        val compositedOverBlack = ColorMath.composite(lightGlass.rest.fillBottom, ColorMath.BLACK)
        assertTrue(
            "Light glass over pure black wallpaper stays legible",
            ColorMath.contrast(lightGlass.rest.content, compositedOverBlack) >= 4.5
        )
    }

    @Test
    fun `selected studio wallpaper enables Backdrop LUMA on any theme`() {
        val settings = ThemeSettings(
            themeId = ThemeId.OBSIDIAN,
            switching = ThemeSwitching.FIXED,
            backdrop = Backdrop.LUMA
        )
        val withoutSelection = ThemeEngine.resolve(settings, ThemeEnvironment(systemDark = true, minuteOfDay = 0))
        val withSelection = ThemeEngine.resolve(
            settings,
            ThemeEnvironment(systemDark = true, minuteOfDay = 0, hasSelectedArtwork = true)
        )
        assertEquals(Backdrop.THEME, withoutSelection.backdrop)
        assertEquals(Backdrop.LUMA, withSelection.backdrop)
    }
}
