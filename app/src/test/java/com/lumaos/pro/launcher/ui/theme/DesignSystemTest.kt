package com.lumaos.pro.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants of the design system itself, independent of a rendered screen.
 *
 * These are the rules that, if broken, would silently undo the visual identity:
 * a storage key changing (and resetting a user's choice), a theme losing its
 * voice, a scale stopping being a hierarchy, an artwork losing its focal point, or
 * home text flipping to the wrong side of the artwork.
 *
 * Pure JVM: no Android framework, no robolectric, no device.
 */
class DesignSystemTest {

    private val envDark = ThemeEnvironment(systemDark = true, minuteOfDay = 600)
    private val envLight = ThemeEnvironment(systemDark = false, minuteOfDay = 600)

    // ------------------------------------------------------------------ type

    @Test fun `typography storage keys are stable and complete`() {
        assertEquals(
            listOf("precision", "ambient", "monograph", "serene", "grotesk", "serif", "mono"),
            TypeStyle.entries.map { it.storageKey }
        )
        // Every old key still resolves, so an upgrade never resets a choice.
        for (key in listOf("precision", "serene", "grotesk", "serif", "mono")) {
            assertNotNull(key, TypeStyle.fromStorageKey(key))
        }
        for (key in listOf("ambient", "monograph")) {
            assertNotNull(key, TypeStyle.fromStorageKey(key))
        }
        assertNull(TypeStyle.fromStorageKey("no-such-voice"))
    }

    @Test fun `the three v3_2 voices are the ones offered first`() {
        assertEquals(listOf(TypeStyle.PRECISION, TypeStyle.AMBIENT, TypeStyle.MONOGRAPH), TypeStyle.SIGNATURE)
        assertEquals(TypeStyle.SIGNATURE.size, TypeStyle.SIGNATURE.map { it.storageKey }.distinct().size)
        // Retained voices stay selectable and are not silently dropped.
        for (voice in TypeStyle.RETAINED) {
            assertNotNull("${voice.storageKey} must stay resolvable", TypeStyle.fromStorageKey(voice.storageKey))
        }
        assertEquals(TypeStyle.entries.toSet(), (TypeStyle.SIGNATURE + TypeStyle.RETAINED).toSet())
    }

    @Test fun `every voice defines a complete role set`() {
        for (voice in TypeStyle.entries) {
            // Monolith's authored display clock is 84 sp; everything else sits lower.
            assertTrue("${voice.storageKey} clock size", voice.clockSizeSp in 40f..90f)
            assertTrue("${voice.storageKey} clock tracking", voice.clockTracking in -0.06f..0f)
            // Monolith's mono labels are tracked at 0.10 em; 0.06 em is the ceiling
            // for sentence-case labels only.
            assertTrue("${voice.storageKey} label tracking", voice.labelTracking in 0f..0.12f)
            assertTrue("${voice.storageKey} readout tracking", voice.readoutTracking in 0f..0.3f)
            // Body copy stays Inter for legibility even in the Grotesk voice.
            if (voice == TypeStyle.GROTESK) {
                assertEquals(FontFamilyId.INTER, voice.body.family)
                assertEquals(FontFamilyId.SPACE_GROTESK, voice.title.family)
            }
        }
    }

    @Test fun `the type scale is a hierarchy`() {
        assertTrue(LumaTypeScale.displaySmallSp > LumaTypeScale.headlineLargeSp)
        assertTrue(LumaTypeScale.headlineSmallSp > LumaTypeScale.titleLargeSp)
        assertTrue(LumaTypeScale.titleSmallSp > LumaTypeScale.bodyLargeSp)
        assertTrue(LumaTypeScale.bodyLargeSp > LumaTypeScale.labelLargeSp)
        assertTrue(LumaTypeScale.labelLargeSp > LumaTypeScale.labelSmallSp)
        // Line heights never collapse below the type size.
        for ((size, multiplier) in listOf(
            LumaTypeScale.displayLargeSp to LumaTypeScale.displayLineHeight,
            LumaTypeScale.headlineLargeSp to LumaTypeScale.headingLineHeight,
            LumaTypeScale.bodyLargeSp to LumaTypeScale.bodyLineHeight
        )) {
            assertTrue(LumaTypeScale.safeLineHeight(size, multiplier) >= size)
        }
    }

    // ---------------------------------------------------------------- motion

    @Test fun `motion profiles obey the feedback bounds`() {
        for (character in MotionCharacter.entries) {
            val p = LumaMotionProfiles.of(character)
            assertEquals(character, p.character)
            assertTrue("${p.character} pressMs", p.pressMs in 40..200)
            assertTrue("${p.character} themeChangeMs", p.themeChangeMs in 120..800)
            assertTrue("${p.character} pressScale", p.pressScale in 0.94f..1f)
            assertTrue("${p.character} enterScale", p.enterScaleFrom >= 0.85f)
            assertTrue("${p.character} popupScale", p.popupScaleFrom >= 0.85f)
            assertEquals("${p.character} easing", 4, p.easing.size)
        }
    }

    @Test fun `subtle and quiet never overshoot and expressive does`() {
        assertFalse(LumaMotionProfiles.of(MotionCharacter.SUBTLE).allowOvershoot)
        assertFalse(LumaMotionProfiles.of(MotionCharacter.QUIET).allowOvershoot)
        assertTrue(LumaMotionProfiles.of(MotionCharacter.EXPRESSIVE).allowOvershoot)
    }

    // -------------------------------------------------------------- wallpaper

    @Test fun `only the retained palettes ship bundled artwork`() {
        // The v3.2 trio paints its own backdrop: Signature and Monolith use their
        // authored atmosphere, Atmosphere follows the user's wallpaper.
        for (theme in ThemePresets.SIGNATURE) {
            assertNull("${theme.displayName} uses its own backdrop", WallpaperArt.forTheme(theme.id))
            assertTrue(theme.defaultBackdrop != Backdrop.LUMA)
        }
        for (id in listOf(ThemeId.AURORA, ThemeId.PORCELAIN, ThemeId.VECTOR)) {
            assertNotNull("$id keeps its bundled artwork", WallpaperArt.forTheme(id))
        }
        // Specialist palettes deliberately keep the atmospheric backdrop instead.
        for (id in listOf(ThemeId.OBSIDIAN, ThemeId.GRAPHITE, ThemeId.CHROMA)) {
            assertNull("$id should not ship artwork", WallpaperArt.forTheme(id))
        }
    }

    @Test fun `artwork declares a usable crop and a light or dark surface`() {
        for (art in WallpaperArt.entries) {
            assertTrue("${art.storageKey} focalX", art.focalX in 0f..1f)
            assertTrue("${art.storageKey} focalY", art.focalY in 0f..1f)
            assertTrue("${art.storageKey} scrimTop", art.scrimTop in 0f..0.6f)
            assertTrue("${art.storageKey} scrimBottom", art.scrimBottom in 0f..0.6f)
            assertTrue(art.storageKey.isNotBlank())
        }
        // Zen is the light artwork the engine must use dark text on.
        assertTrue(WallpaperArt.ZEN_MORNING.supportsDarkText)
        assertFalse(WallpaperArt.AURORA_NIGHT.supportsDarkText)
        assertFalse(WallpaperArt.VOID_FLUX.supportsDarkText)
    }

    @Test fun `home text follows the artwork it sits on`() {
        val zen = ThemeEngine.resolve(
            ThemeSettings().pinned(ThemeId.PORCELAIN).copy(backdrop = Backdrop.LUMA), envLight
        )
        assertTrue("Zen artwork is light, so home text must be dark", ColorMath.isDark(zen.colors.homeForeground))

        for (id in listOf(ThemeId.AURORA, ThemeId.VECTOR)) {
            val dark = ThemeEngine.resolve(
                ThemeSettings().pinned(id).copy(backdrop = Backdrop.LUMA), envDark
            )
            assertFalse("$id artwork is dark, so home text must be light", ColorMath.isDark(dark.colors.homeForeground))
        }
    }

    @Test fun `a theme without artwork cannot resolve the luma backdrop`() {
        val t = ThemeEngine.resolve(
            ThemeSettings().pinned(ThemeId.OBSIDIAN).copy(backdrop = Backdrop.LUMA), envDark
        )
        assertEquals(Backdrop.THEME, t.backdrop)
        assertFalse(t.backdrop.usesArtwork)
    }

    // -------------------------------------------------------- semantic colour

    @Test fun `status roles meet AA on surfaces for every signature theme`() {
        for (id in listOf(ThemeId.AURORA, ThemeId.PORCELAIN, ThemeId.VECTOR)) {
            for (env in listOf(envDark, envLight)) {
                val t = ThemeEngine.resolve(ThemeSettings().pinned(id), env)
                val s = t.semantics
                fun ratio(a: Int, b: Int) = ColorMath.contrast(ColorMath.opaque(a), ColorMath.opaque(b))
                assertTrue("$id/$env success", ratio(s.onSuccessContainer, s.successContainer) >= 4.5)
                assertTrue("$id/$env warning", ratio(s.onWarningContainer, s.warningContainer) >= 4.5)
                assertTrue("$id/$env danger", ratio(s.onDangerContainer, s.dangerContainer) >= 4.5)
                assertTrue("$id/$env disabled", ratio(s.disabledContent, s.disabledSurface) >= 3.0)
                assertTrue("$id/$env search hint", ratio(s.searchHint, s.searchSurface) >= 4.5)
            }
        }
    }

    @Test fun `selection is not hue-only - containers carry their own content roles`() {
        val t = ThemeEngine.resolve(ThemeSettings().pinned(ThemeId.AURORA), envDark)
        val s = t.semantics
        val ratio = ColorMath.contrast(ColorMath.opaque(s.selectedContent), ColorMath.opaque(s.selectedSurface))
        assertTrue("selected pair $ratio", ratio >= 4.5)
        val unselected = ColorMath.contrast(ColorMath.opaque(s.unselectedContent), ColorMath.opaque(s.unselectedSurface))
        assertTrue("unselected pair $unselected", unselected >= 4.5)
    }

    // ----------------------------------------------------------- geometry

    @Test fun `component radii scale with the theme but keep their order`() {
        for (id in ThemeId.entries) {
            val t = ThemeEngine.resolve(ThemeSettings().pinned(id), envDark)
            val shapes = com.lumaos.pro.launcher.ui.theme.LumaComponents.of(t).shapes
            assertTrue("$id search", shapes.searchDp >= shapes.cellDp)
            assertTrue("$id sheet", shapes.sheetDp > shapes.menuDp)
            assertTrue("$id control", shapes.controlDp < shapes.cellDp)
            // Scaling is the only difference between themes.
            assertTrue("$id shape scale", kotlin.math.abs(t.cornerScale - shapes.scale) < 1e-4f)
        }
    }

    @Test fun `retained scales stay within their historical bounds`() {
        val void = ThemePresets.VOID.cornerScale
        val zen = ThemePresets.ZEN.cornerScale
        assertTrue("Void should be sharper than Zen", void < zen)
        for (theme in ThemePresets.ADDITIONAL) {
            assertTrue("${theme.displayName} corner scale", theme.cornerScale in 0.75f..1.2f)
        }
    }

    @Test fun `v3_2 corner scales follow their authored medium radius`() {
        // Medium radius over the pre-3.2 16 dp base: 16 / 20 / 2 -> 1.0 / 1.25 / 0.125.
        assertEquals(1.0f, ThemePresets.SIGNATURE_THEME.cornerScale, 0f)
        assertEquals(1.25f, ThemePresets.ATMOSPHERE_THEME.cornerScale, 0f)
        assertEquals(0.125f, ThemePresets.MONOLITH_THEME.cornerScale, 0f)
        for (theme in ThemePresets.SIGNATURE) {
            val tokens = requireNotNull(theme.tokens)
            assertEquals(
                "${theme.displayName} shape scale",
                tokens.shape.medium / 16f,
                theme.cornerScale,
                0.0001f
            )
        }
    }

    // ----------------------------------------------------------- identity

    @Test fun `each retained theme keeps its voice, material and motion`() {
        val aurora = ThemePresets.AURORA
        val zen = ThemePresets.ZEN
        val void = ThemePresets.VOID
        assertEquals(TypeStyle.PRECISION, aurora.typography)
        assertEquals(TypeStyle.SERENE, zen.typography)
        assertEquals(TypeStyle.GROTESK, void.typography)
        assertEquals(MotionCharacter.SUBTLE, aurora.motionCharacter)
        assertEquals(MotionCharacter.QUIET, zen.motionCharacter)
        assertEquals(MotionCharacter.EXPRESSIVE, void.motionCharacter)
        // Zen is paper (solid), Aurora and Void are layered light (glass).
        assertEquals(SurfaceStyle.SOLID, zen.surfaceStyle)
        assertEquals(SurfaceStyle.GLASS, aurora.surfaceStyle)
        assertEquals(SurfaceStyle.GLASS, void.surfaceStyle)
    }
}
