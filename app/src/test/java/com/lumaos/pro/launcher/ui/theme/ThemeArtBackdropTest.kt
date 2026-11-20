package com.lumaos.pro.launcher.ui.theme

import com.lumaos.pro.launcher.wallpaper.WallpaperCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for Phase 2 theme art used as the default backdrop.
 * They check what the user sees while the art is decoding (or when it can't be
 * decoded), and that the art can be found by the palette/blur pipeline.
 */
class ThemeArtBackdropTest {

    private val phase2 = listOf(
        ThemeId.LUMA_MONO, ThemeId.LUMA_GLASS, ThemeId.MONOLITH, ThemeId.ATMOSPHERE,
        ThemeId.AURORA, ThemeId.IVORY, ThemeId.OBSIDIAN
    )

    private fun resolve(id: ThemeId, systemDark: Boolean, backdrop: Backdrop? = null, selected: Boolean = false) =
        ThemeEngine.resolve(
            ThemeSettings(backdrop = backdrop).pinned(id),
            ThemeEnvironment(systemDark = systemDark, minuteOfDay = 12 * 60, hasSelectedArtwork = selected)
        )

    @Test fun `home text stays legible on the backdrop fallback for every theme art`() {
        // Regression: the fallback used to be colors.background. With a light art
        // on a dark theme (Monolith Basalt, Mono Paper & Ink, Atmosphere First
        // Light) that put dark home text on a near-black fill.
        for (id in phase2) for (dark in listOf(true, false)) {
            val t = resolve(id, dark)
            assertEquals("$id dark=$dark", Backdrop.LUMA, t.backdrop)
            val art = assertNotNullArt(t)
            val fallback = t.backdropFallbackColor
            val ratio = ColorMath.contrast(ColorMath.opaque(t.colors.homeForeground), fallback)
            assertTrue("$id/${art.name} home text on fallback = $ratio", ratio >= 4.5)
            assertEquals("$id fallback follows the art", ColorMath.opaque(art.seeds.primary), fallback)
        }
    }

    @Test fun `theme backdrop keeps the theme background as its fallback`() {
        for (id in ThemeId.entries) for (dark in listOf(true, false)) {
            val t = resolve(id, dark, Backdrop.THEME)
            assertEquals("$id", t.colors.background, t.backdropFallbackColor)
            assertNull("$id has no art id on THEME", t.lumaArtId)
        }
    }

    @Test fun `theme art id resolves to a bundled catalog entry for the current mode`() {
        // LumaThemeManager decodes this id to sample the palette and glass blur.
        for (id in phase2) for (dark in listOf(true, false)) {
            val t = resolve(id, dark)
            val art = assertNotNullArt(t)
            assertEquals(WallpaperArt.forTheme(id, dark), art)
            val entry = WallpaperCatalog.findBundled(t.lumaArtId)
            assertNotNull("$id dark=$dark id=${t.lumaArtId}", entry)
            assertEquals(art, entry!!.art)
        }
    }

    @Test fun `day and night pairs swap art with the system mode`() {
        assertEquals(WallpaperArt.MONO_DARKROOM, resolve(ThemeId.LUMA_MONO, true).lumaArt)
        assertEquals(WallpaperArt.MONO_PAPER, resolve(ThemeId.LUMA_MONO, false).lumaArt)
        assertEquals(WallpaperArt.ATMOSPHERE_CANOPY, resolve(ThemeId.ATMOSPHERE, true).lumaArt)
        assertEquals(WallpaperArt.ATMOSPHERE_FIRST_LIGHT, resolve(ThemeId.ATMOSPHERE, false).lumaArt)
        assertEquals(WallpaperArt.OBSIDIAN_ONYX, resolve(ThemeId.OBSIDIAN, false).lumaArt)
    }

    @Test fun `light art always gets dark home text and dark art light text`() {
        for (id in phase2) for (dark in listOf(true, false)) {
            val t = resolve(id, dark)
            val art = assertNotNullArt(t)
            assertEquals("$id/${art.name}", art.supportsDarkText, ColorMath.isDark(ColorMath.opaque(t.colors.homeForeground)))
        }
    }

    @Test fun `system wallpaper backdrop exposes no theme art id`() {
        for (id in phase2) {
            assertNull("$id", resolve(id, true, Backdrop.WALLPAPER).lumaArtId)
        }
    }

    private fun assertNotNullArt(t: ResolvedTheme): WallpaperArt {
        assertNotNull("${t.id} has theme art", t.lumaArt)
        return t.lumaArt!!
    }
}
