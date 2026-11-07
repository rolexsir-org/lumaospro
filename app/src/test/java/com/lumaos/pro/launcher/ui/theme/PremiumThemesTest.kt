package com.lumaos.pro.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The premium collection: Obsidian (dark), Ivory (light) and Aurora (expressive
 * gradient). Each must be a complete authored identity - its own palette,
 * surface ladder, radii, elevation, material, type voice and icon treatment -
 * and must hold the engine's contrast contract with or without a user accent.
 */
class PremiumThemesTest {

    private val envDark = ThemeEnvironment(systemDark = true, minuteOfDay = 600)
    private val envLight = ThemeEnvironment(systemDark = false, minuteOfDay = 600)

    private fun resolve(id: ThemeId, settings: ThemeSettings = ThemeSettings(), env: ThemeEnvironment = envDark) =
        ThemeEngine.resolve(settings.copy(backdrop = null).pinned(id), env)

    private fun ratio(a: Int, b: Int) = ColorMath.contrast(ColorMath.opaque(a), ColorMath.opaque(b))

    @Test fun `the premium trio is offered first, in order, under stable keys`() {
        assertEquals(listOf(ThemeId.OBSIDIAN, ThemeId.IVORY, ThemeId.AURORA), ThemePresets.PREMIUM.map { it.id })
        assertEquals(ThemePresets.PREMIUM, ThemePresets.ALL.take(3))
        assertEquals(ThemePresets.PREMIUM, ThemePresets.FEATURED.take(3))
        // Obsidian and Aurora keep their historic keys; Ivory is new and round-trips.
        assertEquals(ThemeId.OBSIDIAN, ThemeId.fromStorageKey("obsidian"))
        assertEquals(ThemeId.AURORA, ThemeId.fromStorageKey("aurora"))
        assertEquals(ThemeId.IVORY, ThemeId.fromStorageKey("ivory"))
        assertEquals(ThemeId.entries.size, ThemePresets.ALL.map { it.id }.distinct().size)
    }

    @Test fun `every premium theme carries a complete authored token set`() {
        for (spec in ThemePresets.PREMIUM) {
            val tokens = assertNotNullAndGet(spec.tokens, "${spec.displayName} tokens")
            assertEquals(spec.id.storageKey, tokens.key)
            assertNotNull("${spec.displayName} elevation", tokens.elevation)
            // Radii are ordered and the medium radius drives the legacy scale.
            with(tokens.shape) {
                assertTrue(extraLarge >= large && large >= medium && medium >= small && small >= extraSmall)
            }
            assertEquals(tokens.shape.medium / 16f, spec.cornerScale, 1e-4f)
            assertTrue(tokens.material.blurDp > 0f)
        }
        assertEquals(LumaDesignTokens.PREMIUM, ThemePresets.PREMIUM.map { it.tokens })
        // The v3.2 parity set is untouched by the premium additions.
        assertEquals(listOf("signature", "atmosphere", "monolith"), LumaDesignTokens.ALL.map { it.key })
    }

    @Test fun `the three are genuinely different identities`() {
        val (obsidian, ivory, aurora) = ThemePresets.PREMIUM
        assertTrue(obsidian.dark && aurora.dark)
        assertFalse(ivory.dark)
        assertTrue("Obsidian is the OLED theme", obsidian.amoled)
        assertEquals(Atmosphere.NONE, obsidian.atmosphere)
        assertEquals(Atmosphere.HALO, ivory.atmosphere)
        assertEquals(Atmosphere.AURORA, aurora.atmosphere)
        assertEquals(SurfaceStyle.GLASS, aurora.surfaceStyle)
        assertEquals(MotionCharacter.CINEMATIC, aurora.motionCharacter)
        assertEquals(3, ThemePresets.PREMIUM.map { it.accent }.toSet().size)
        // Light themes need softer shadows than dark ones to read as the same depth.
        assertTrue(ivory.tokens!!.elevation!!.shadowAlpha < obsidian.tokens!!.elevation!!.shadowAlpha)
    }

    @Test fun `authored roles are drawn exactly, including the light theme`() {
        for (spec in ThemePresets.PREMIUM) for (env in listOf(envDark, envLight)) {
            val t = resolve(spec.id, env = env)
            val tokens = spec.tokens!!.colors
            assertEquals(spec.dark, t.isDark)
            assertEquals(tokens.background0, t.colors.background)
            assertEquals(ColorMath.opaque(tokens.background1), t.colors.surface0)
            assertEquals(tokens.text1, t.colors.onSurface)
            assertEquals(tokens.accent, t.colors.accent)
        }
    }

    @Test fun `obsidian is true black for OLED and its field carries no glow`() {
        val t = resolve(ThemeId.OBSIDIAN)
        assertEquals(ColorMath.BLACK, t.colors.background)
        assertEquals(ColorMath.BLACK, t.colors.surface0)
        assertEquals(listOf(AtmosphereLayer.Solid(ColorMath.BLACK)), AtmospherePlan.layers(t, 0.4f, true))
    }

    @Test fun `ivory uses ink, not white, for interaction layers`() {
        val c = resolve(ThemeId.IVORY, env = envLight).colors
        assertFalse(c.isDark)
        assertTrue("pressed veil must darken paper", ColorMath.isDark(ColorMath.opaque(c.pressedOverlay)))
        assertTrue("home text on paper is dark", ColorMath.isDark(ColorMath.opaque(c.homeForeground)))
        assertTrue(ratio(c.onSurface, c.surface1) >= 12.0)
    }

    @Test fun `aurora paints a real multi-colour gradient that drifts`() {
        val t = resolve(ThemeId.AURORA)
        val a = AtmospherePlan.layers(t, 0f, true)
        val b = AtmospherePlan.layers(t, 0.25f, true)
        val glows = a.filterIsInstance<AtmosphereLayer.Glow>()
        assertTrue(glows.size >= 3)
        assertTrue(glows.map { ColorMath.opaque(it.color) }.toSet().size >= 3)
        assertTrue(a.filterIsInstance<AtmosphereLayer.VerticalFade>().size >= 2)
        assertTrue(a != b)
    }

    @Test fun `all premium themes hold the contrast contract in both modes`() {
        for (spec in ThemePresets.PREMIUM) for (env in listOf(envDark, envLight)) {
            val c = resolve(spec.id, env = env).colors
            val n = spec.displayName
            assertTrue("$n body", ratio(c.onSurface, c.surface1) >= 7.0)
            assertTrue("$n background", ratio(c.onBackground, c.background) >= 7.0)
            assertTrue("$n muted", ratio(c.onSurfaceMuted, c.surface2) >= 4.5)
            assertTrue("$n faint", ratio(c.onSurfaceFaint, c.surface1) >= 3.0)
            assertTrue("$n accent", ratio(c.accent, c.surface1) >= 3.0)
            assertTrue("$n on-accent", ratio(c.onAccent, c.accent) >= 4.5)
        }
    }

    @Test fun `curated accents keep the authored surfaces and stay legible`() {
        for (spec in ThemePresets.PREMIUM) {
            val accents = ThemePresets.curatedAccents(spec.id)
            assertEquals("theme accent comes first", ColorMath.opaque(spec.accent), accents.first())
            assertTrue(accents.size >= 4)
            for (accent in accents) {
                val t = resolve(spec.id, ThemeSettings(accentOverride = accent))
                val tokens = spec.tokens!!.colors
                assertEquals(tokens.text1, t.colors.onSurface)
                assertEquals(ColorMath.opaque(tokens.background1), t.colors.surface0)
                assertTrue("${spec.displayName} ${ColorMath.toHex(accent)}", ratio(t.colors.accent, t.colors.surface1) >= 3.0)
                assertTrue(ratio(t.colors.onAccent, t.colors.accent) >= 4.5)
            }
        }
    }

    @Test fun `a custom background still falls back to the contrast-safe derived path`() {
        val t = resolve(ThemeId.IVORY, ThemeSettings(backgroundOverride = 0xFF223344.toInt()))
        assertTrue(t.isDark)
        assertTrue(ratio(t.colors.onSurface, t.colors.surface1) >= 7.0)
    }

    @Test fun `launcher packages apply the premium identity without dead settings`() {
        for (spec in ThemePresets.PREMIUM) {
            val pkg = ThemeLauncherPackage.forTheme(spec.id)
            assertEquals(spec.id, pkg.themeId)
            val applied = pkg.toThemeSettings(ThemeSettings(accentOverride = 0xFF112233.toInt()))
            assertEquals(spec.id, applied.themeId)
            assertEquals(ThemeSwitching.FIXED, applied.switching)
            assertEquals(null, applied.accentOverride)
            assertEquals(spec.id, ThemeEngine.resolve(applied, envDark).id)
        }
    }

    @Test fun `ivory is the light half of the default pair`() {
        assertEquals(ThemeId.IVORY, ThemePresets.DEFAULT_LIGHT)
        assertEquals(ThemeId.IVORY, ThemeEngine.resolve(ThemeSettings(), envLight).id)
        assertEquals(ThemeId.IVORY, LumaGlassAccent("Test", 0xFF123456.toInt()).lightAnchor)
    }

    @Test fun `premium themes paint glass for every role`() {
        for (spec in ThemePresets.PREMIUM) {
            val palette = GlassMaterials.palette(resolve(spec.id))
            assertEquals(GlassRole.entries.toSet(), palette.roles)
            // Floating roles lift more than cards, from the authored elevation.
            val e = spec.tokens!!.elevation!!
            assertEquals(e.floatingDp, palette[GlassRole.DOCK].elevationDp, 0f)
            assertEquals(e.cardDp, palette[GlassRole.SECONDARY].elevationDp, 0f)
        }
    }

    private fun <T> assertNotNullAndGet(value: T?, message: String): T {
        assertNotNull(message, value)
        return value!!
    }
}
