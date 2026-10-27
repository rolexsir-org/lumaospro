package com.lumaos.pro.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AtmosphereTest {

    private fun theme(id: ThemeId, backdrop: Backdrop? = null) = ThemeEngine.resolve(
        ThemeSettings(backdrop = backdrop).pinned(id), ThemeEnvironment(systemDark = true, minuteOfDay = 0)
    )

    @Test fun `wallpaper backdrop never paints an opaque base`() {
        for (id in ThemeId.entries) {
            val layers = AtmospherePlan.layers(theme(id, Backdrop.WALLPAPER), 0f, true)
            assertTrue(id.name, layers.none { it is AtmosphereLayer.Solid })
            assertTrue("legibility gradients for $id", layers.count { it is AtmosphereLayer.VerticalFade } >= 2)
        }
    }

    @Test fun `theme backdrop starts with the theme background`() {
        for (id in ThemeId.entries) {
            val t = theme(id, Backdrop.THEME)
            val first = AtmospherePlan.layers(t, 0f, true).first()
            assertEquals(AtmosphereLayer.Solid(t.colors.background), first)
        }
    }

    @Test fun `obsidian is pure black with nothing on top`() {
        val layers = AtmospherePlan.layers(theme(ThemeId.OBSIDIAN), 0.3f, true)
        assertEquals(listOf(AtmosphereLayer.Solid(ColorMath.BLACK)), layers)
    }

    @Test fun `grid hairlines are dropped when detail is off`() {
        // A bundled LUMA backdrop intentionally replaces the theme atmosphere
        // with artwork; the grid belongs to the THEME backdrop path.
        val withDetail = AtmospherePlan.layers(theme(ThemeId.VECTOR, Backdrop.THEME), 0f, true)
        val without = AtmospherePlan.layers(theme(ThemeId.VECTOR, Backdrop.THEME), 0f, false)
        assertTrue(withDetail.any { it is AtmosphereLayer.Lines })
        assertTrue(without.none { it is AtmosphereLayer.Lines })
    }

    @Test fun `aurora drifts with phase`() {
        val a = AtmospherePlan.layers(theme(ThemeId.AURORA, Backdrop.THEME), 0f, true)
        val b = AtmospherePlan.layers(theme(ThemeId.AURORA, Backdrop.THEME), 0.25f, true)
        assertNotEquals(a, b)
    }

    @Test fun `signature theme highlights remain restrained`() {
        fun strongestGlow(id: ThemeId): Float = AtmospherePlan
            .layers(theme(id), 0.18f, true)
            .filterIsInstance<AtmosphereLayer.Glow>()
            .maxOf { ColorMath.alpha(it.color) / 255f }

        assertTrue("Aurora glow", strongestGlow(ThemeId.AURORA) <= 0.15f)
        assertTrue("Zen glow", strongestGlow(ThemeId.PORCELAIN) <= 0.11f)
        assertTrue("Void glow", strongestGlow(ThemeId.VECTOR) <= 0.11f)
    }

    @Test fun `grid segments stay inside the floor area`() {
        val segs = AtmospherePlan.gridSegments(0.74f, 0.5f)
        assertEquals(0, segs.size % 4)
        for (i in segs.indices step 2) {
            val y = segs[i + 1]
            assertTrue(y >= 0.74f - 1e-4f && y <= 1.03f)
        }
    }

    @Test fun `icon outlines are normalised and system has none`() {
        assertNull(IconShapes.outline(IconShape.SYSTEM))
        for (shape in IconShape.entries.filter { it != IconShape.SYSTEM }) {
            val pts = IconShapes.outline(shape)!!
            assertTrue(shape.name, pts.size >= 16 && pts.size % 2 == 0)
            assertTrue(shape.name, pts.all { it in -0.001f..1.001f })
        }
    }
}
