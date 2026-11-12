package com.lumaos.pro.launcher.context

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusInterventionGateTest {

    @Test fun `first sighting intervenes, repeats inside the cooldown do not`() {
        var now = 1_000L
        val gate = FocusInterventionGate(cooldownMillis = 2_000L, clock = { now })
        assertTrue(gate.shouldIntervene("com.example.game"))
        now += 500L
        assertFalse(gate.shouldIntervene("com.example.game"))
        now += 500L
        assertFalse(gate.shouldIntervene("com.example.game"))
    }

    @Test fun `returning after the cooldown re-blocks`() {
        var now = 1_000L
        val gate = FocusInterventionGate(cooldownMillis = 2_000L, clock = { now })
        assertTrue(gate.shouldIntervene("com.example.game"))
        now += 2_000L
        assertTrue(gate.shouldIntervene("com.example.game"))
    }

    @Test fun `a different restricted package intervenes immediately`() {
        var now = 1_000L
        val gate = FocusInterventionGate(cooldownMillis = 60_000L, clock = { now })
        assertTrue(gate.shouldIntervene("com.example.game"))
        now += 100L
        assertTrue(gate.shouldIntervene("com.example.social"))
    }

    @Test fun `reset clears the memory`() {
        var now = 1_000L
        val gate = FocusInterventionGate(cooldownMillis = 60_000L, clock = { now })
        assertTrue(gate.shouldIntervene("com.example.game"))
        gate.reset()
        assertTrue(gate.shouldIntervene("com.example.game"))
    }
}
