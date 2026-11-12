package com.lumaos.pro.launcher.context

import com.lumaos.pro.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextEngineTest {

    private val game = ComponentKey("com.example.game", "com.example.game.Play")
    private val mail = ComponentKey("com.example.mail", "com.example.mail.Main")

    @Test fun `focus runs only in Work and Study and only when enabled`() {
        assertTrue(ContextEngine.focusActive(ContextProfile.WORK, true))
        assertTrue(ContextEngine.focusActive(ContextProfile.STUDY, true))
        assertFalse(ContextEngine.focusActive(ContextProfile.WORK, false))
        assertFalse(ContextEngine.focusActive(ContextProfile.STUDY, false))
        // Even a stored enabled flag must not restrict Personal or Sleep.
        assertFalse(ContextEngine.focusActive(ContextProfile.PERSONAL, true))
        assertFalse(ContextEngine.focusActive(ContextProfile.SLEEP, true))
    }

    @Test fun `restriction needs focus plus membership`() {
        val restricted = setOf(game)
        assertTrue(ContextEngine.isRestricted(ContextProfile.WORK, true, restricted, game))
        assertFalse(ContextEngine.isRestricted(ContextProfile.WORK, true, restricted, mail))
        assertFalse(ContextEngine.isRestricted(ContextProfile.WORK, false, restricted, game))
        assertFalse(ContextEngine.isRestricted(ContextProfile.PERSONAL, true, restricted, game))
        assertFalse(ContextEngine.isRestricted(ContextProfile.SLEEP, true, restricted, game))
    }

    @Test fun `shortcuts are judged by package`() {
        val restricted = setOf(game)
        assertTrue(ContextEngine.isPackageRestricted(ContextProfile.STUDY, true, restricted, "com.example.game"))
        assertFalse(ContextEngine.isPackageRestricted(ContextProfile.STUDY, true, restricted, "com.example.mail"))
        assertFalse(ContextEngine.isPackageRestricted(ContextProfile.STUDY, false, restricted, "com.example.game"))
    }

    @Test fun `manual override stands until its deadline then expires`() {
        val now = 1_700_000_000_000L
        val active = ManualOverride(ContextProfile.SLEEP, now + 60_000L)
        assertEquals(ContextProfile.SLEEP to false, ContextEngine.resolveEffective(ContextProfile.WORK, active, now))

        val expired = ManualOverride(ContextProfile.SLEEP, now)
        assertEquals(ContextProfile.WORK to true, ContextEngine.resolveEffective(ContextProfile.WORK, expired, now))

        assertEquals(ContextProfile.WORK to false, ContextEngine.resolveEffective(ContextProfile.WORK, null, now))
    }

    @Test fun `override activity is a strict deadline`() {
        val override = ManualOverride(ContextProfile.WORK, 100L)
        assertTrue(override.isActive(99L))
        assertFalse(override.isActive(100L))
        assertFalse(override.isActive(101L))
    }
}
