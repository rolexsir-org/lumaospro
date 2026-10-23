package com.rolexm.launcher.ui.theme

import com.rolexm.launcher.context.ContextProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeEngineTest {

    private fun ratio(a: Int, b: Int) = ColorMath.contrast(ColorMath.opaque(a), ColorMath.opaque(b))

    /** 7:1 where the background allows it (see ColorMath.maxContrast), and never below AA. */
    private fun aaa(fg: Int, bg: Int): Boolean {
        val r = ratio(fg, bg)
        return r >= 4.5 && r >= minOf(7.0, ColorMath.maxContrast(bg) - 1e-6)
    }

    private fun assertGuarantees(t: ResolvedTheme, label: String) {
        val c = t.colors
        assertTrue("$label onSurface/surface0 ${ratio(c.onSurface, c.surface0)}", aaa(c.onSurface, c.surface0))
        assertTrue("$label onSurface/surface1", aaa(c.onSurface, c.surface1))
        assertTrue("$label muted/surface1 ${ratio(c.onSurfaceMuted, c.surface1)}", ratio(c.onSurfaceMuted, c.surface1) >= 4.5)
        assertTrue("$label muted/surface2 ${ratio(c.onSurfaceMuted, c.surface2)}", ratio(c.onSurfaceMuted, c.surface2) >= 4.5)
        assertTrue("$label faint/surface1", ratio(c.onSurfaceFaint, c.surface1) >= 3.0)
        assertTrue("$label accent/surface1 ${ratio(c.accent, c.surface1)}", ratio(c.accent, c.surface1) >= 3.0)
        assertTrue("$label onAccent/accent ${ratio(c.onAccent, c.accent)}", ratio(c.onAccent, c.accent) >= 4.5)
        assertTrue("$label onAccentContainer", ratio(c.onAccentContainer, c.accentContainer) >= 4.5)
        assertTrue("$label onBackground ${ratio(c.onBackground, c.background)}", aaa(c.onBackground, c.background))
        assertTrue("$label danger", ratio(c.danger, c.surface1) >= 4.5)
    }

    private val envDark = ThemeEnvironment(systemDark = true, minuteOfDay = 600)
    private val envLight = ThemeEnvironment(systemDark = false, minuteOfDay = 600)

    @Test fun `every preset meets the contrast guarantees`() {
        for (id in ThemeId.entries) for (env in listOf(envDark, envLight)) for (backdrop in Backdrop.entries) {
            val t = ThemeEngine.resolve(ThemeSettings().pinned(id).copy(backdrop = backdrop), env)
            assertGuarantees(t, "$id/${env.systemDark}/$backdrop")
        }
    }

    @Test fun `every preset meets the guarantees with any curated accent or background`() {
        for (id in ThemeId.entries) {
            for (accent in ThemePresets.ACCENT_SWATCHES) {
                assertGuarantees(ThemeEngine.resolve(ThemeSettings(accentOverride = accent).pinned(id), envDark), "$id accent ${ColorMath.toHex(accent)}")
            }
            for (bg in ThemePresets.BACKGROUND_SWATCHES) {
                assertGuarantees(ThemeEngine.resolve(ThemeSettings(backgroundOverride = bg).pinned(id), envDark), "$id bg ${ColorMath.toHex(bg)}")
            }
        }
    }

    @Test fun `guarantees hold for arbitrary user colours`() {
        val rnd = java.util.Random(42)
        repeat(300) {
            val accent = 0xFF000000.toInt() or rnd.nextInt(0xFFFFFF)
            val bg = 0xFF000000.toInt() or rnd.nextInt(0xFFFFFF)
            val id = ThemeId.entries[rnd.nextInt(ThemeId.entries.size)]
            val t = ThemeEngine.resolve(ThemeSettings(accentOverride = accent, backgroundOverride = bg).pinned(id), envDark)
            assertGuarantees(t, "$id accent ${ColorMath.toHex(accent)} bg ${ColorMath.toHex(bg)}")
        }
    }

    @Test fun `all curated presets reach 7 to 1 body text without compromise`() {
        for (id in ThemeId.entries) for (env in listOf(envDark, envLight)) {
            val c = ThemeEngine.resolve(ThemeSettings().pinned(id), env).colors
            assertTrue("$id", ratio(c.onSurface, c.surface1) >= 7.0 && ratio(c.onBackground, c.background) >= 7.0)
        }
    }

    @Test fun `mid-tone custom backgrounds get the strongest possible text`() {
        val bg = 0xFFFF4581.toInt()
        val t = ThemeEngine.resolve(ThemeSettings(backgroundOverride = bg).pinned(ThemeId.OBSIDIAN), envDark)
        assertEquals(ColorMath.maxContrast(bg), ratio(t.colors.onBackground, bg), 0.01)
        assertTrue(ratio(t.colors.onBackground, bg) >= 4.5)
    }

    @Test fun `surfaces from a mid-tone background stay on one side of the crossover`() {
        // Regression: before clamping, #841E99 produced surface1 needing white
        // and surface2 needing black, so muted text fell to 3.7:1.
        val t = ThemeEngine.resolve(ThemeSettings(backgroundOverride = 0xFF841E99.toInt()).pinned(ThemeId.GRAPHITE), envDark)
        assertTrue(ratio(t.colors.onSurfaceMuted, t.colors.surface1) >= 4.5)
        assertTrue(ratio(t.colors.onSurfaceMuted, t.colors.surface2) >= 4.5)
    }

    @Test fun `a light custom background produces a light theme`() {
        val t = ThemeEngine.resolve(ThemeSettings(backgroundOverride = 0xFFF3EDE4.toInt()).pinned(ThemeId.VECTOR), envDark)
        assertFalse(t.isDark)
        assertTrue(ColorMath.isDark(t.colors.onSurface))
    }

    @Test fun `obsidian keeps true black surfaces for OLED`() {
        val t = ThemeEngine.resolve(ThemeSettings().pinned(ThemeId.OBSIDIAN), envDark)
        assertEquals(ColorMath.BLACK, t.colors.background)
        assertEquals(ColorMath.BLACK, t.colors.surface0)
    }

    @Test fun `obsidian with a custom non-black background is no longer amoled`() {
        val t = ThemeEngine.resolve(ThemeSettings(backgroundOverride = 0xFF101214.toInt()).pinned(ThemeId.OBSIDIAN), envDark)
        assertNotEquals(ColorMath.BLACK, t.colors.surface0)
    }

    @Test fun `overrides apply on top of the effective preset`() {
        val t = ThemeEngine.resolve(
            ThemeSettings(typography = TypeStyle.MONO, iconShape = IconShape.CIRCLE, iconStyle = IconStyle.TINTED).pinned(ThemeId.PORCELAIN),
            envLight
        )
        assertEquals(TypeStyle.MONO, t.typography)
        assertEquals(IconShape.CIRCLE, t.iconShape)
        assertEquals(IconStyle.TINTED, t.iconStyle)
        assertEquals(ThemeId.PORCELAIN, t.id)
    }

    @Test fun `system switching follows the system dark setting`() {
        val s = ThemeSettings(switching = ThemeSwitching.SYSTEM, lightThemeId = ThemeId.PORCELAIN, darkThemeId = ThemeId.AURORA)
        assertEquals(ThemeId.AURORA, s.effectiveThemeId(true, 0, null))
        assertEquals(ThemeId.PORCELAIN, s.effectiveThemeId(false, 0, null))
    }

    @Test fun `scheduled switching uses the dark window across midnight`() {
        val s = ThemeSettings(
            switching = ThemeSwitching.SCHEDULE, lightThemeId = ThemeId.PORCELAIN,
            darkThemeId = ThemeId.OBSIDIAN, darkWindow = DarkWindow(22 * 60, 6 * 60)
        )
        assertEquals(ThemeId.OBSIDIAN, s.effectiveThemeId(false, 23 * 60, null))
        assertEquals(ThemeId.OBSIDIAN, s.effectiveThemeId(false, 2 * 60, null))
        assertEquals(ThemeId.PORCELAIN, s.effectiveThemeId(true, 12 * 60, null))
    }

    @Test fun `a context profile theme wins over switching`() {
        val s = ThemeSettings(switching = ThemeSwitching.FIXED, themeId = ThemeId.PORCELAIN,
            profileThemes = mapOf(ContextProfile.SLEEP to ThemeId.OBSIDIAN))
        assertEquals(ThemeId.OBSIDIAN, s.effectiveThemeId(false, 0, ContextProfile.SLEEP))
        assertEquals(ThemeId.PORCELAIN, s.effectiveThemeId(false, 0, ContextProfile.WORK))
    }

    @Test fun `dark window handles boundaries and countdown`() {
        val w = DarkWindow(19 * 60, 7 * 60)
        assertTrue(w.contains(19 * 60))
        assertFalse(w.contains(7 * 60))
        assertEquals(60, w.minutesUntilNextChange(18 * 60))
        assertEquals(12 * 60, w.minutesUntilNextChange(19 * 60))
        assertFalse(DarkWindow(300, 300).contains(300))
    }

    @Test fun `dark window encodes and decodes, rejecting garbage`() {
        val w = DarkWindow(21 * 60 + 30, 6 * 60 + 15)
        assertEquals("21:30-06:15", w.encode())
        assertEquals(w, DarkWindow.decode("21:30-06:15"))
        assertEquals(DarkWindow.DEFAULT, DarkWindow.decode("25:00-06:00"))
        assertEquals(DarkWindow.DEFAULT, DarkWindow.decode(null))
    }

    @Test fun `chroma follows the wallpaper's light or dark hint and hue`() {
        val warm = WallpaperSeeds(primary = 0xFFD9822B.toInt(), supportsDarkText = true)
        val light = ThemeEngine.resolve(ThemeSettings().pinned(ThemeId.CHROMA), envDark.copy(wallpaper = warm))
        assertFalse("light wallpaper -> light theme", light.isDark)
        val dark = ThemeEngine.resolve(ThemeSettings().pinned(ThemeId.CHROMA),
            envLight.copy(wallpaper = warm.copy(supportsDarkText = false)))
        assertTrue(dark.isDark)
        val seedHue = Oklch.fromArgb(warm.primary).h
        assertTrue(Oklch.hueDistance(Oklch.fromArgb(dark.colors.accent).h, seedHue) < 0.35)
    }

    @Test fun `home text over a light wallpaper is dark with a light halo`() {
        val t = ThemeEngine.resolve(ThemeSettings(backdrop = Backdrop.WALLPAPER).pinned(ThemeId.GRAPHITE),
            envDark.copy(wallpaper = WallpaperSeeds(0xFFEEEEEE.toInt(), supportsDarkText = true)))
        assertTrue(ColorMath.isDark(t.colors.homeForeground))
        val t2 = ThemeEngine.resolve(ThemeSettings(backdrop = Backdrop.WALLPAPER).pinned(ThemeId.GRAPHITE),
            envDark.copy(wallpaper = WallpaperSeeds(0xFF111111.toInt(), supportsDarkText = false)))
        assertEquals(ColorMath.WHITE, t2.colors.homeForeground)
    }

    @Test fun `theme colours interpolate end to end`() {
        val a = ThemeEngine.resolve(ThemeSettings().pinned(ThemeId.PORCELAIN), envLight).colors
        val b = ThemeEngine.resolve(ThemeSettings().pinned(ThemeId.VECTOR), envDark).colors
        assertEquals(a, ThemeColors.lerp(a, b, 0f))
        assertEquals(b, ThemeColors.lerp(a, b, 1f))
    }
}
