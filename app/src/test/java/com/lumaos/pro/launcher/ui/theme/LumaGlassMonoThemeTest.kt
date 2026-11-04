package com.lumaos.pro.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 2026-11 reference identities: Luma Glass (deep-navy liquid glass) and Luma Mono (peach duotone). */
class LumaGlassMonoThemeTest {

    private val env = ThemeEnvironment(systemDark = true, minuteOfDay = 600)

    @Test fun `storage keys round trip and existing keys are untouched`() {
        assertEquals(ThemeId.LUMA_GLASS, ThemeId.fromStorageKey("luma_glass"))
        assertEquals(ThemeId.LUMA_MONO, ThemeId.fromStorageKey("luma_mono"))
        assertEquals(ThemeId.SIGNATURE, ThemeId.fromStorageKey("signature"))
        assertEquals(ThemeId.PORCELAIN, ThemeId.fromStorageKey("porcelain"))
        // v3.2 trio keeps its exact membership/order; new themes are additive.
        assertEquals(listOf(ThemeId.SIGNATURE, ThemeId.ATMOSPHERE, ThemeId.MONOLITH), ThemePresets.SIGNATURE.map { it.id })
        assertEquals(ThemePresets.SIGNATURE + ThemePresets.SCREENSHOT, ThemePresets.FEATURED)
        assertEquals(ThemeId.entries.toSet(), ThemePresets.ALL.map { it.id }.toSet())
    }

    @Test fun `glass is deep navy glass with glossy natural icons`() {
        val t = ThemeEngine.resolve(ThemeSettings().pinned(ThemeId.LUMA_GLASS), env)
        assertTrue(t.isDark)
        assertTrue(t.usesBlur)
        assertEquals(SurfaceStyle.GLASS, t.surfaceStyle)
        assertEquals(IconStyle.NATURAL, t.iconStyle)
        assertEquals(IconShape.SQUIRCLE, t.iconShape)
        assertEquals(Backdrop.WALLPAPER, t.backdrop)
        assertEquals(72f, t.clockSp, 0f)
        // Deep navy, not black and not the Signature midnight.
        assertTrue(ColorMath.blue(t.colors.background) > ColorMath.red(t.colors.background))
    }

    @Test fun `mono is zero blur duotone with mono voice`() {
        val t = ThemeEngine.resolve(ThemeSettings().pinned(ThemeId.LUMA_MONO), env)
        assertTrue(t.isDark)
        assertFalse(t.usesBlur)
        assertEquals(0f, t.blurDp, 0f)
        assertEquals(SurfaceStyle.SOLID, t.surfaceStyle)
        assertEquals(IconStyle.TINTED, t.iconStyle)
        assertEquals(TypeStyle.MONOGRAPH, t.typography)
        assertTrue(t.typography.readoutCaps)
        assertEquals(FontFamilyId.SYSTEM_MONO, t.typography.label.family)
        // Warm: red channel dominates the peach accent.
        assertTrue(ColorMath.red(t.colors.accent) > ColorMath.blue(t.colors.accent))
    }

    @Test fun `both themes keep body text at AAA on their own surfaces`() {
        for (id in listOf(ThemeId.LUMA_GLASS, ThemeId.LUMA_MONO)) {
            val c = ThemeEngine.resolve(ThemeSettings().pinned(id), env).colors
            assertTrue("$id onSurface", ColorMath.contrast(ColorMath.opaque(c.onSurface), ColorMath.opaque(c.surface1)) >= 7.0)
            assertTrue("$id onBackground", ColorMath.contrast(ColorMath.opaque(c.onBackground), ColorMath.opaque(c.background)) >= 7.0)
            assertTrue("$id accent", ColorMath.contrast(ColorMath.opaque(c.accent), ColorMath.opaque(c.surface1)) >= 3.0)
        }
    }

    @Test fun `theme packages are real and distinct`() {
        val glass = ThemeLauncherPackage.forTheme(ThemeId.LUMA_GLASS)
        val mono = ThemeLauncherPackage.forTheme(ThemeId.LUMA_MONO)
        assertEquals(IconStyle.NATURAL, glass.iconStyle)
        assertTrue(glass.livingIcons)
        assertEquals(IconStyle.TINTED, mono.iconStyle)
        assertFalse(mono.livingIcons)
    }

    @Test fun `switching themes does not rewrite unrelated settings`() {
        val base = ThemeSettings(accentOverride = 0xFF112233.toInt(), iconShape = IconShape.CIRCLE)
        val switched = base.withPreset(ThemeId.LUMA_MONO)
        assertEquals(ThemeId.LUMA_MONO, switched.themeId)
        assertEquals(base.accentOverride, switched.accentOverride)
        assertEquals(IconShape.CIRCLE, switched.iconShape)
    }
}
