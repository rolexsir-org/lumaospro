package com.lumaos.pro.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin contract for the shared Luma Glass material grammar.
 *
 * These tests do not need Android or Robolectric: the palette is derived from
 * the same semantic theme engine as the app, so opacity, contrast, state
 * colours, role geometry and capability fallbacks are verifiable on the JVM.
 */
class GlassMaterialTest {

    private val envDark = ThemeEnvironment(systemDark = true, minuteOfDay = 0)
    private val envLight = ThemeEnvironment(systemDark = false, minuteOfDay = 12 * 60)

    @Test
    fun `every semantic role has a complete glass recipe`() {
        val theme = ThemeEngine.resolve(ThemeSettings(), envDark)
        val palette = GlassMaterials.palette(theme)

        assertEquals(GlassRole.entries.size, palette.roles.size)
        for (role in GlassRole.entries) {
            val spec = palette[role]
            assertEquals(role, spec.role)
            assertTrue("$role radius", spec.radiusDp > 0f)
            assertTrue("$role blur", spec.blurDp >= 0f)
            assertTrue("$role border", spec.borderDp > 0f)
            assertTrue("$role fill", ColorMath.alpha(spec.rest.fillTop) > 0)
            assertTrue("$role content", ColorMath.alpha(spec.rest.content) == 255)
            assertTrue("$role pressed fill", ColorMath.alpha(spec.states.pressed.fillTop) > 0)
            assertTrue("$role focus ring", ColorMath.alpha(spec.states.focused.edgeTop) > 0)
        }
    }

    @Test
    fun `roles use deliberately different materials and one sheet shape`() {
        val theme = ThemeEngine.resolve(ThemeSettings(), envDark)
        val palette = GlassMaterials.palette(theme)
        val dock = palette[GlassRole.DOCK]
        val search = palette[GlassRole.SEARCH]
        val control = palette[GlassRole.SETTINGS_CONTROL]
        val sheet = palette[GlassRole.SHEET]

        assertTrue("dock is the roundest", dock.radiusDp > search.radiusDp)
        assertTrue("search is softer than a control", search.radiusDp > control.radiusDp)
        assertTrue("dock has more blur than search", dock.blurDp > search.blurDp)
        assertTrue("dock floats higher", dock.elevationDp > control.elevationDp)
        assertTrue("sheet is top-rounded", sheet.topOnly)
        assertFalse("search is fully rounded", search.topOnly)
        assertTrue(
            "sheet is denser than search",
            ColorMath.alpha(sheet.rest.fillTop) > ColorMath.alpha(search.rest.fillTop)
        )
    }

    @Test
    fun `light and dark glass stay legible over their theme background`() {
        for (env in listOf(envDark, envLight)) {
            val theme = ThemeEngine.resolve(ThemeSettings(), env)
            val spec = GlassMaterials.palette(theme)[GlassRole.SEARCH]
            val behind = ColorMath.composite(spec.rest.fillBottom, theme.colors.background)
            val ratio = ColorMath.contrast(
                ColorMath.opaque(spec.rest.content),
                ColorMath.opaque(behind)
            )
            assertTrue(
                "Contrast ${theme.id} = $ratio must stay at WCAG AA for body text",
                ratio >= 4.5
            )
        }
    }

    @Test
    fun `missing blur produces opaque readable fallbacks in every state`() {
        val theme = ThemeEngine.resolve(ThemeSettings(), envDark)
        val noBlur = GlassMaterials.palette(theme, GlassCapabilities(blur = false))
        val solid = GlassMaterials.palette(theme, GlassCapabilities.SOLID)

        for (palette in listOf(noBlur, solid)) {
            for (role in GlassRole.entries) {
                val spec = palette[role]
                assertEquals("$role blur", 0f, spec.blurDp, 0f)
                for (state in listOf(spec.rest, spec.states.pressed, spec.states.focused, spec.states.selected)) {
                    assertEquals("$role fill", 255, ColorMath.alpha(state.fillTop))
                    assertEquals("$role bottom", 255, ColorMath.alpha(state.fillBottom))
                }
            }
        }
    }

    @Test
    fun `wallpaper scrim follows text polarity and is not uniform`() {
        val darkSettings = ThemeSettings().copy(backdrop = Backdrop.WALLPAPER)
        val lightWallpaper = WallpaperSeeds(
            primary = ColorMath.WHITE,
            secondary = ColorMath.WHITE,
            supportsDarkText = true
        )
        val dark = GlassBackdrop.scrim(ThemeEngine.resolve(darkSettings, envDark))
        val light = GlassBackdrop.scrim(
            ThemeEngine.resolve(darkSettings, envLight.copy(wallpaper = lightWallpaper))
        )

        assertEquals(ColorMath.BLACK, dark.tint)
        assertEquals(ColorMath.WHITE, light.tint)
        assertTrue(ColorMath.alpha(dark.bottom) > ColorMath.alpha(dark.center))
        assertTrue(ColorMath.alpha(light.bottom) > ColorMath.alpha(light.center))
        assertTrue(ColorMath.alpha(dark.top) > ColorMath.alpha(dark.center))
    }

    @Test
    fun `accent presets keep one appearance instead of a second theme system`() {
        val accent = LumaGlassIdentity.ACCENTS.first()
        val settings = accent.applyTo(ThemeSettings().pinned(ThemeId.VECTOR))

        assertEquals(accent.darkAnchor, settings.themeId)
        assertEquals(accent.darkAnchor, settings.darkThemeId)
        assertEquals(accent.lightAnchor, settings.lightThemeId)
        assertEquals(ThemeSwitching.SYSTEM, settings.switching)
        assertEquals(accent.color, settings.accentOverride)
        assertEquals(Backdrop.WALLPAPER, settings.backdrop)
        assertNull(settings.backgroundOverride)
        assertEquals(Backdrop.WALLPAPER, ThemeSettings().backdrop)
    }

    @Test
    fun `glass motion aliases the central token family`() {
        assertEquals(LumaDesignTokens.Motion.PRESS, GlassMotion.pressMs)
        assertEquals(LumaDesignTokens.Motion.DUR_2, GlassMotion.openMs)
        assertEquals(LumaDesignTokens.Motion.DUR_3, GlassMotion.themeChangeMs)
        assertTrue(GlassMotion.standard.contentEquals(LumaDesignTokens.Motion.STANDARD))
        assertTrue(GlassMotion.emphasis.contentEquals(LumaDesignTokens.Motion.EMPHASIS))
    }
}
