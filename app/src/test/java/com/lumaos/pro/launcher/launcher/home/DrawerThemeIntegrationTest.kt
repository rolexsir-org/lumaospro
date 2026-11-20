package com.lumaos.pro.launcher.launcher.home

import com.lumaos.pro.launcher.ui.glass.GlassCapabilities
import com.lumaos.pro.launcher.ui.glass.GlassMaterials
import com.lumaos.pro.launcher.ui.glass.GlassRole
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeEnvironment
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The drawer must consume the existing LumaOS theme system rather than a
 * hardcoded dark palette. These tests pin that contract: every theme - light,
 * dark and custom - yields a SHEET and SEARCH material whose content colour is
 * legible against its own fill, and the drawer's resolved surface actually
 * changes between themes (so a theme switch visibly recolours the sheet).
 */
@RunWith(Parameterized::class)
class DrawerThemeIntegrationTest(private val themeId: ThemeId) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun themes(): List<ThemeId> = listOf(
            ThemeId.OBSIDIAN,
            ThemeId.IVORY,
            ThemeId.SIGNATURE,
            ThemeId.ATMOSPHERE,
            ThemeId.MONOLITH,
            ThemeId.LUMA_GLASS,
            ThemeId.LUMA_MONO
        )
    }

    private fun resolved(systemDark: Boolean): ResolvedTheme =
        ThemeEngine.resolve(
            ThemeSettings(themeId = themeId),
            ThemeEnvironment(systemDark = systemDark, minuteOfDay = 720)
        )

    @Test
    fun `drawer sheet content is legible against its own theme-derived fill`() {
        val palette = GlassMaterials.palette(resolved(systemDark = true), GlassCapabilities.DEFAULT)
        val sheet = palette[GlassRole.SHEET].rest
        val fill = ColorMath.opaque(sheet.fillTop)
        assertTrue(
            "SHEET content $sheet must contrast >= 4.5 against fill $fill for $themeId",
            ColorMath.contrast(sheet.content, fill) >= 4.5
        )
    }

    @Test
    fun `drawer search content is legible against its own theme-derived fill`() {
        val palette = GlassMaterials.palette(resolved(systemDark = true), GlassCapabilities.DEFAULT)
        val search = palette[GlassRole.SEARCH].rest
        val fill = ColorMath.opaque(search.fillTop)
        assertTrue(
            "SEARCH content must contrast >= 4.5 against fill for $themeId",
            ColorMath.contrast(search.content, fill) >= 4.5
        )
    }

    @Test
    fun `drawer sheet surface follows the active theme and is not a fixed dark palette`() {
        // The same theme in light vs dark system mode must produce a different
        // sheet fill, proving the drawer reads the theme system live.
        val light = ColorMath.opaque(
            GlassMaterials.palette(resolved(systemDark = false), GlassCapabilities.DEFAULT)[GlassRole.SHEET].rest.fillTop
        )
        val dark = ColorMath.opaque(
            GlassMaterials.palette(resolved(systemDark = true), GlassCapabilities.DEFAULT)[GlassRole.SHEET].rest.fillTop
        )
        assertTrue(
            "Drawer sheet fill must change between light and dark mode for $themeId",
            light != dark
        )
    }

    @Test
    fun `light and dark themes resolve to distinct drawer surfaces`() {
        // Cross-theme check: a light theme (Ivory) and a dark theme (Obsidian)
        // cannot share the same drawer sheet fill - the drawer is themed, not fixed.
        val ivoryFill = ColorMath.opaque(
            GlassMaterials.palette(
                ThemeEngine.resolve(ThemeSettings(themeId = ThemeId.IVORY), ThemeEnvironment(true, 720)),
                GlassCapabilities.DEFAULT
            )[GlassRole.SHEET].rest.fillTop
        )
        val obsidianFill = ColorMath.opaque(
            GlassMaterials.palette(
                ThemeEngine.resolve(ThemeSettings(themeId = ThemeId.OBSIDIAN), ThemeEnvironment(true, 720)),
                GlassCapabilities.DEFAULT
            )[GlassRole.SHEET].rest.fillTop
        )
        assertTrue(
            "Ivory and Obsidian drawer sheets must differ ($ivoryFill vs $obsidianFill)",
            ivoryFill != obsidianFill
        )
    }
}
