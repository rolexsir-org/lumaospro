package com.lumaos.pro.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Design System v3.2 behaviour: the three authored identities, their material
 * values, their geometry and the way the engine applies them.
 *
 * These run on the JVM with no Android framework, so they hold for the View home
 * screen and the Compose settings alike - both read the same [ResolvedTheme].
 */
class DesignSystemV32Test {

    private val envDark = ThemeEnvironment(systemDark = true, minuteOfDay = 600)
    private val envLight = ThemeEnvironment(systemDark = false, minuteOfDay = 600)

    private fun resolve(id: ThemeId, env: ThemeEnvironment = envDark, settings: ThemeSettings = ThemeSettings()): ResolvedTheme =
        ThemeEngine.resolve(settings.pinned(id), env)

    // -------------------------------------------------------------- identity

    @Test fun `the three v3_2 themes are the ones offered first`() {
        assertEquals(
            listOf("Signature", "Atmosphere", "Monolith"),
            ThemePresets.SIGNATURE.map { it.displayName }
        )
        assertEquals(
            listOf(ThemeId.SIGNATURE, ThemeId.ATMOSPHERE, ThemeId.MONOLITH),
            ThemePresets.SIGNATURE.map { it.id }
        )
        // Every identity is selectable exactly once, and the retained palettes are
        // still reachable.
        assertEquals(ThemeId.entries.toSet(), ThemePresets.ALL.map { it.id }.toSet())
        assertEquals(ThemePresets.ALL.size, ThemePresets.ALL.map { it.id }.distinct().size)
        assertTrue(ThemePresets.ADDITIONAL.map { it.id }.containsAll(
            listOf(ThemeId.AURORA, ThemeId.PORCELAIN, ThemeId.VECTOR, ThemeId.OBSIDIAN, ThemeId.GRAPHITE, ThemeId.CHROMA)
        ))
    }

    @Test fun `pre-3_2 storage keys still resolve`() {
        for ((key, id) in listOf(
            "aurora" to ThemeId.AURORA,
            "porcelain" to ThemeId.PORCELAIN,
            "vector" to ThemeId.VECTOR,
            "obsidian" to ThemeId.OBSIDIAN,
            "graphite" to ThemeId.GRAPHITE,
            "chroma" to ThemeId.CHROMA,
            "signature" to ThemeId.SIGNATURE,
            "atmosphere" to ThemeId.ATMOSPHERE,
            "monolith" to ThemeId.MONOLITH
        )) {
            assertEquals(key, id, ThemeId.fromStorageKey(key))
        }
    }

    @Test fun `each theme keeps its own voice, material and motion`() {
        val signature = ThemePresets.SIGNATURE_THEME
        val atmosphere = ThemePresets.ATMOSPHERE_THEME
        val monolith = ThemePresets.MONOLITH_THEME

        assertEquals(TypeStyle.PRECISION, signature.typography)
        assertEquals(TypeStyle.AMBIENT, atmosphere.typography)
        assertEquals(TypeStyle.MONOGRAPH, monolith.typography)

        assertEquals(MotionCharacter.SUBTLE, signature.motionCharacter)
        assertEquals(MotionCharacter.CINEMATIC, atmosphere.motionCharacter)
        assertEquals(MotionCharacter.EXACT, monolith.motionCharacter)

        assertEquals(SurfaceStyle.TRANSLUCENT, signature.surfaceStyle)
        assertEquals(SurfaceStyle.GLASS, atmosphere.surfaceStyle)
        assertEquals(SurfaceStyle.SOLID, monolith.surfaceStyle)

        // The three differ in composition inputs, not only in hue.
        assertNotEquals(signature.defaultBackdrop, atmosphere.defaultBackdrop)
        assertNotEquals(signature.iconShape, monolith.iconShape)
        assertEquals(Backdrop.WALLPAPER, atmosphere.defaultBackdrop)
    }

    @Test fun `retained palettes keep their exact pre-3_2 seeds`() {
        // Regression guards: these numbers are what shipped, and an upgrade must
        // not move a user's existing home screen.
        assertEquals(0xFF080D1A.toInt(), ThemePresets.AURORA.background)
        assertEquals(0xFF9AD3FF.toInt(), ThemePresets.AURORA.accent)
        assertEquals(0xFFF2F4F1.toInt(), ThemePresets.ZEN.background)
        assertEquals(0xFF080815.toInt(), ThemePresets.VOID.background)
        assertEquals(0.82f, ThemePresets.VOID.cornerScale, 0f)
        assertEquals("porcelain", ThemePresets.ZEN.id.storageKey)
        assertEquals("vector", ThemePresets.VOID.id.storageKey)
        // Retained palettes have no authored token set; v3.2 themes all do.
        for (spec in ThemePresets.ADDITIONAL) assertNull("${spec.displayName} should not carry v3.2 tokens", spec.tokens)
        for (spec in ThemePresets.SIGNATURE) assertTrue("${spec.displayName} needs v3.2 tokens", spec.tokens != null)
    }

    // ------------------------------------------------------------- authored colour

    @Test fun `the engine draws the authored roles, not a regenerated palette`() {
        val signature = resolve(ThemeId.SIGNATURE)
        val tokens = LumaDesignTokens.SIGNATURE.colors
        assertEquals(tokens.background0, signature.colors.background)
        assertEquals(ColorMath.opaque(tokens.background1), signature.colors.surface0)
        assertEquals(tokens.text1, signature.colors.onSurface)
        assertEquals(tokens.text2, signature.colors.onSurfaceMuted)
        assertEquals(tokens.accent, signature.colors.accent)
        assertEquals(tokens.accent2, signature.colors.secondary)
        // Translucent roles are composited over the panel they sit on.
        assertEquals(
            ColorMath.composite(tokens.surface1, ColorMath.opaque(tokens.background1)),
            signature.colors.surface1
        )
        assertEquals(ColorMath.composite(tokens.stroke1, ColorMath.opaque(tokens.background1)), signature.colors.outline)
    }

    @Test fun `a custom accent falls back to the contrast-safe derived path`() {
        val custom = 0xFFD9822B.toInt()
        val themed = resolve(ThemeId.SIGNATURE, settings = ThemeSettings(accentOverride = custom))
        val tokens = LumaDesignTokens.SIGNATURE.colors
        assertNotEquals(tokens.accent, themed.colors.accent)
        assertNotEquals(tokens.text1, themed.colors.onSurface)
        // Overrides must never lower the guarantee.
        assertTrue(ColorMath.contrast(themed.colors.onSurface, themed.colors.surface1) >= 7.0)
    }

    @Test fun `the three v3_2 palettes all clear 7 to 1 body text`() {
        for (id in listOf(ThemeId.SIGNATURE, ThemeId.ATMOSPHERE, ThemeId.MONOLITH)) {
            for (env in listOf(envDark, envLight)) {
                val c = resolve(id, env).colors
                assertTrue("$id body", ColorMath.contrast(c.onSurface, c.surface1) >= 7.0)
                assertTrue("$id background", ColorMath.contrast(c.onBackground, c.background) >= 7.0)
                assertTrue("$id muted", ColorMath.contrast(c.onSurfaceMuted, c.surface2) >= 4.5)
                assertTrue("$id accent", ColorMath.contrast(c.accent, c.surface1) >= 3.0)
            }
        }
    }

    // ------------------------------------------------------------------ geometry

    @Test fun `radius roles answer with the authored geometry`() {
        val expected = mapOf(
            ThemeId.SIGNATURE to floatArrayOf(34f, 26f, 16f, 10f, 6f, 34f, 24f),
            ThemeId.ATMOSPHERE to floatArrayOf(38f, 30f, 20f, 12f, 8f, 36f, 26f),
            ThemeId.MONOLITH to floatArrayOf(8f, 4f, 2f, 2f, 2f, 4f, 10f)
        )
        for ((id, values) in expected) {
            val theme = resolve(id)
            val roles = listOf(
                RadiusRole.EXTRA_LARGE, RadiusRole.LARGE, RadiusRole.MEDIUM,
                RadiusRole.SMALL, RadiusRole.EXTRA_SMALL, RadiusRole.DOCK, RadiusRole.TILE
            )
            roles.forEachIndexed { index, role ->
                assertEquals("$id $role", values[index], theme.radius(role), 0f)
            }
            // The ladder never inverts: sheets are never sharper than rows.
            assertTrue(theme.radius(RadiusRole.EXTRA_LARGE) >= theme.radius(RadiusRole.LARGE))
            assertTrue(theme.radius(RadiusRole.LARGE) >= theme.radius(RadiusRole.MEDIUM))
            assertTrue(theme.radius(RadiusRole.MEDIUM) >= theme.radius(RadiusRole.SMALL))
            assertTrue(theme.radius(RadiusRole.SMALL) >= theme.radius(RadiusRole.EXTRA_SMALL))
        }
    }

    @Test fun `retained palettes answer radius roles from their own scale`() {
        val void = resolve(ThemeId.VECTOR)
        assertNull(void.tokens)
        assertEquals(24f * void.cornerScale, void.radius(RadiusRole.EXTRA_LARGE), 0.001f)
        assertEquals(16f * void.cornerScale, void.radius(RadiusRole.MEDIUM), 0.001f)
    }

    @Test fun `blur is a theme material and monolith has none`() {
        assertEquals(26f, resolve(ThemeId.SIGNATURE).blurDp, 0f)
        assertEquals(34f, resolve(ThemeId.ATMOSPHERE).blurDp, 0f)
        assertEquals(0f, resolve(ThemeId.MONOLITH).blurDp, 0f)
        assertTrue(resolve(ThemeId.SIGNATURE).usesBlur)
        assertTrue(resolve(ThemeId.ATMOSPHERE).usesBlur)
        assertFalse("Monolith's material excludes blur", resolve(ThemeId.MONOLITH).usesBlur)
        assertEquals("A theme without blur cannot scale a blur radius", 0f, resolve(ThemeId.MONOLITH).blurFactor, 0f)
        // Atmosphere is allowed to ask for more than the historical ceiling.
        assertTrue(resolve(ThemeId.ATMOSPHERE).blurFactor > 1f)
        // Retained palettes keep the historical material.
        assertEquals(ResolvedTheme.LEGACY_BLUR_DP, resolve(ThemeId.AURORA).blurDp, 0f)
    }

    @Test fun `saturation and label sizes are authored, not hard-coded`() {
        assertEquals(1.35f, resolve(ThemeId.SIGNATURE).saturation, 1e-6f)
        assertEquals(1.65f, resolve(ThemeId.ATMOSPHERE).saturation, 1e-6f)
        assertEquals(1f, resolve(ThemeId.MONOLITH).saturation, 1e-6f)

        assertEquals(10.5f, resolve(ThemeId.SIGNATURE).appLabelSp, 0f)
        assertEquals(11f, resolve(ThemeId.ATMOSPHERE).appLabelSp, 0f)
        assertEquals("Monolith engraves smaller labels", 9f, resolve(ThemeId.MONOLITH).appLabelSp, 0f)
    }

    @Test fun `the type ladder is shared even where the voices differ`() {
        for (id in ThemeId.entries) {
            val theme = resolve(id)
            val scale = theme.typeScale
            // One ladder for every identity: the clock is the only role above 32 sp.
            assertEquals(66f, scale.clock, 0f)
            assertTrue(scale.clock > 32f)
            assertTrue(scale.screenTitle < scale.clock)
            assertTrue(scale.sectionHeading > scale.primary)
            assertTrue(scale.primary > scale.supporting)
            assertTrue(scale.appLabel >= scale.numeric)
            assertTrue(scale.kicker in 9f..10f)
        }
    }

    // -------------------------------------------------------------------- motion

    @Test fun `v3_2 motion characters obey the family rules`() {
        val subtle = LumaMotionProfiles.of(MotionCharacter.SUBTLE)
        val cinematic = LumaMotionProfiles.of(MotionCharacter.CINEMATIC)
        val exact = LumaMotionProfiles.of(MotionCharacter.EXACT)

        assertFalse(subtle.allowOvershoot)
        assertTrue("Atmosphere is the one v3.2 character allowed to overshoot", cinematic.allowOvershoot)
        assertFalse(exact.allowOvershoot)
        assertEquals("Monolith never staggers", 0, exact.staggerMs)
        assertEquals("Monolith cross-fades fastest", 280, exact.themeChangeMs)
        assertTrue("Exact is the shortest open", exact.openMs <= 200)
        // Durations stay inside the published 120/200/320/480 family.
        for (profile in listOf(subtle, cinematic, exact)) {
            assertTrue(profile.openMs in 120..480)
            assertTrue(profile.closeMs in 120..480)
            // Press feedback stays inside the profile invariant (40–200 ms); the
            // authored family default is 90 ms, which Monolith tightens to 70.
            assertTrue(profile.pressMs in 40..200)
        }
    }

    // ------------------------------------------------------------------ backdrop

    @Test fun `v3_2 themes have no bundled artwork and cannot resolve LUMA`() {
        for (id in listOf(ThemeId.SIGNATURE, ThemeId.ATMOSPHERE, ThemeId.MONOLITH)) {
            assertNull("$id should not ship artwork", WallpaperArt.forTheme(id))
            val themed = ThemeEngine.resolve(ThemeSettings().pinned(id).copy(backdrop = Backdrop.LUMA), envDark)
            assertEquals(Backdrop.THEME, themed.backdrop)
        }
        // The retained themes keep theirs.
        for (id in listOf(ThemeId.AURORA, ThemeId.PORCELAIN, ThemeId.VECTOR)) {
            assertTrue("$id keeps its bundled artwork", WallpaperArt.forTheme(id) != null)
        }
    }

    @Test fun `fresh installs land on Signature in dark and Zen in light`() {
        assertEquals(ThemeId.SIGNATURE, ThemeEngine.resolve(ThemeSettings(), envDark).id)
        assertEquals(ThemeId.PORCELAIN, ThemeEngine.resolve(ThemeSettings(), envLight).id)
        assertEquals(ThemeId.SIGNATURE, ThemeSettings().themeId)
        assertEquals(ThemeId.SIGNATURE, ThemeSettings().darkThemeId)
        assertEquals(ThemeId.PORCELAIN, ThemeSettings().lightThemeId)
    }

    @Test fun `a context profile can pin a v3_2 theme`() {
        val settings = ThemeSettings(
            switching = ThemeSwitching.SYSTEM,
            lightThemeId = ThemeId.PORCELAIN,
            darkThemeId = ThemeId.SIGNATURE,
            profileThemes = mapOf(com.lumaos.pro.launcher.context.ContextProfile.SLEEP to ThemeId.MONOLITH)
        )
        assertEquals(
            ThemeId.MONOLITH,
            settings.effectiveThemeId(systemDark = false, minuteOfDay = 0, profile = com.lumaos.pro.launcher.context.ContextProfile.SLEEP)
        )
        assertEquals(
            ThemeId.PORCELAIN,
            settings.effectiveThemeId(systemDark = false, minuteOfDay = 0, profile = com.lumaos.pro.launcher.context.ContextProfile.WORK)
        )
    }
}
