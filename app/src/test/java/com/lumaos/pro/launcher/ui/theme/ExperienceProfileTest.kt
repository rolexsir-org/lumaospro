package com.lumaos.pro.launcher.ui.theme

import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.effects.EffectsPreference
import com.lumaos.pro.launcher.launcher.model.DockStyle
import com.lumaos.pro.launcher.motion.MotionPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperienceProfileTest {

    @Test
    fun `storage keys are unique and stable`() {
        val keys = ExperienceProfile.entries.map { it.storageKey }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(listOf("pure", "liquid", "midnight"), keys)
    }

    @Test
    fun `new installs default to liquid`() {
        assertSame(ExperienceProfile.LIQUID, ExperienceProfile.DEFAULT)
        assertSame(ExperienceProfile.MIDNIGHT, ExperienceProfile.fromStorageKey("midnight"))
        assertSame(ExperienceProfile.PURE, ExperienceProfile.fromStorageKey("pure"))
        // Unknown or absent stored values resolve through the DEFAULT at call sites.
        assertSame(null, ExperienceProfile.fromStorageKey(null))
        assertSame(null, ExperienceProfile.fromStorageKey("nonsense"))
        assertSame(
            ExperienceProfile.LIQUID,
            ExperienceProfile.fromStorageKey(null) ?: ExperienceProfile.DEFAULT
        )
    }

    @Test
    fun `profiles are genuinely distinct identities`() {
        val ids = ExperienceProfile.entries.map { it.themeId }.toSet()
        assertEquals(3, ids.size)
        assertEquals(
            setOf(ThemeId.PORCELAIN, ThemeId.SIGNATURE, ThemeId.ATMOSPHERE),
            ids
        )
    }

    @Test
    fun `pure is the calm solid light identity without blur`() {
        val pure = ExperienceProfile.PURE
        assertFalse(pure.dark)
        assertEquals(ThemeId.PORCELAIN, pure.themeId)
        assertEquals(EffectsPreference.MINIMAL, pure.effects)
        assertEquals(DockStyle.MINIMAL, pure.dock)
    }

    @Test
    fun `liquid is the glass flagship and midnight is wallpaper led`() {
        assertEquals(EffectsPreference.AUTO, ExperienceProfile.LIQUID.effects)
        assertEquals(Backdrop.THEME, ExperienceProfile.LIQUID.backdrop)
        assertEquals(DockStyle.FLOAT, ExperienceProfile.LIQUID.dock)

        assertEquals(EffectsPreference.AUTO, ExperienceProfile.MIDNIGHT.effects)
        assertEquals(Backdrop.WALLPAPER, ExperienceProfile.MIDNIGHT.backdrop)
        assertTrue(ExperienceProfile.MIDNIGHT.dark)
    }

    @Test
    fun `all profiles keep the shared spring motion policy`() {
        ExperienceProfile.entries.forEach {
            assertEquals(MotionPreference.FULL, it.motion)
        }
    }

    @Test
    fun `applying a profile fixes its identity and clears bespoke overrides`() {
        val base = ThemeSettings(
            themeId = ThemeId.OBSIDIAN,
            switching = ThemeSwitching.SYSTEM,
            accentOverride = 0xFFFF0000.toInt(),
            backgroundOverride = 0xFF00FF00.toInt(),
            typography = TypeStyle.GROTESK,
            backdrop = Backdrop.WALLPAPER
        )
        val applied = ExperienceProfile.MIDNIGHT.themeSettings(base)
        assertEquals(ThemeId.ATMOSPHERE, applied.themeId)
        assertEquals(ThemeSwitching.FIXED, applied.switching)
        assertEquals(ThemeId.ATMOSPHERE, applied.darkThemeId)
        assertNull(applied.accentOverride)
        assertNull(applied.backgroundOverride)
        assertNull(applied.typography)
        assertEquals(Backdrop.WALLPAPER, applied.backdrop)
    }

    @Test
    fun `advanced per-context theme overrides survive a profile switch`() {
        val base = ThemeSettings(
            profileThemes = mapOf(ContextProfile.SLEEP to ThemeId.OBSIDIAN)
        )
        val applied = ExperienceProfile.LIQUID.themeSettings(base)
        assertEquals(ThemeId.OBSIDIAN, applied.profileThemes[ContextProfile.SLEEP])
    }
}
