package com.lumaos.pro.launcher.context

import com.lumaos.pro.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusEnforcementTest {

    private val own = "com.lumaos.pro.launcher"
    private val game = "com.example.game"
    private val now = 1_700_000_000_000L

    private fun snapshot(
        active: ContextProfile = ContextProfile.WORK,
        focusEnabled: Boolean = true,
        restricted: Set<String> = setOf(game),
        unlockProfile: ContextProfile? = null,
        unlockUntil: Long = 0L
    ) = FocusEnforcement.Snapshot(active, focusEnabled, restricted, unlockProfile, unlockUntil)

    @Test fun `restricted package is blocked during a Work focus session`() {
        assertTrue(FocusEnforcement.shouldBlock(snapshot(), game, own, now))
    }

    @Test fun `unrestricted packages pass through`() {
        assertFalse(FocusEnforcement.shouldBlock(snapshot(), "com.example.mail", own, now))
    }

    @Test fun `Personal and Sleep never enforce even with a restriction set`() {
        assertFalse(FocusEnforcement.shouldBlock(snapshot(active = ContextProfile.PERSONAL), game, own, now))
        assertFalse(FocusEnforcement.shouldBlock(snapshot(active = ContextProfile.SLEEP), game, own, now))
    }

    @Test fun `disabled toggle never enforces`() {
        assertFalse(FocusEnforcement.shouldBlock(snapshot(focusEnabled = false), game, own, now))
    }

    @Test fun `unexpired unlock for the active profile allows everything`() {
        val unlocked = snapshot(unlockProfile = ContextProfile.WORK, unlockUntil = now + 60_000L)
        assertTrue(FocusEnforcement.unlockActive(unlocked, now))
        assertFalse(FocusEnforcement.shouldBlock(unlocked, game, own, now))
    }

    @Test fun `expired unlock resumes enforcement at the exact deadline`() {
        val expired = snapshot(unlockProfile = ContextProfile.WORK, unlockUntil = now)
        assertFalse(FocusEnforcement.unlockActive(expired, now))
        assertTrue(FocusEnforcement.shouldBlock(expired, game, own, now))
    }

    @Test fun `an unlock never leaks into another context`() {
        val workUnlock = snapshot(
            active = ContextProfile.STUDY,
            unlockProfile = ContextProfile.WORK,
            unlockUntil = now + 60_000L
        )
        assertFalse(FocusEnforcement.unlockActive(workUnlock, now))
        assertTrue(FocusEnforcement.shouldBlock(workUnlock, game, own, now))
    }

    @Test fun `protected packages are never blocked`() {
        for (pkg in FocusSafety.PROTECTED_PACKAGES + own) {
            assertFalse("must never block $pkg", FocusEnforcement.shouldBlock(snapshot(restricted = setOf(pkg)), pkg, own, now))
        }
    }

    @Test fun `blank package names are ignored`() {
        assertFalse(FocusEnforcement.shouldBlock(snapshot(), "", own, now))
        assertFalse(FocusEnforcement.shouldBlock(snapshot(), "   ", own, now))
    }

    @Test fun `restriction maps components to packages`() {
        val keys = setOf(
            ComponentKey("com.example.game", "com.example.game.Play"),
            ComponentKey("com.example.game", "com.example.game.Share"),
            ComponentKey("com.example.mail", "com.example.mail.Main", 10L)
        )
        assertEquals(setOf("com.example.game", "com.example.mail"), FocusEnforcement.restrictedPackages(keys))
    }
}
