package com.lumaos.pro.launcher.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpringTest {

    private fun run(spec: SpringSpec, from: Float, to: Float, seconds: Float, dt: Float = 1f / 60f): List<SpringState> {
        val out = ArrayList<SpringState>()
        var s = SpringState(from, 0f)
        var t = 0f
        while (t < seconds) {
            s = SpringPhysics.step(s, to, spec, dt)
            out += s
            t += dt
        }
        return out
    }

    @Test fun `every token settles on its target`() {
        for (spec in listOf(MotionTokens.SNAPPY, MotionTokens.STANDARD, MotionTokens.GENTLE, MotionTokens.LIVELY)) {
            val last = run(spec, 0f, 1000f, 3f).last()
            assertEquals(1000f, last.value, 1f)
            assertTrue(abs(last.velocity) < 5f)
        }
    }

    @Test fun `critically damped springs never overshoot`() {
        val states = run(MotionTokens.GENTLE, 0f, 100f, 3f)
        assertTrue(states.all { it.value <= 100.0001f })
    }

    @Test fun `underdamped springs overshoot a little`() {
        val states = run(MotionTokens.LIVELY, 0f, 100f, 2f)
        val peak = states.maxOf { it.value }
        assertTrue(peak > 100f && peak < 125f)
    }

    @Test fun `overdamped springs are handled`() {
        val last = run(SpringSpec(400f, 1.6f), 0f, 50f, 4f).last()
        assertEquals(50f, last.value, 0.5f)
    }

    @Test fun `retargeting mid-flight keeps position and velocity continuous`() {
        var s = SpringState(0f, 0f)
        repeat(10) { s = SpringPhysics.step(s, 100f, MotionTokens.SNAPPY, 1f / 60f) }
        val before = s
        // Reverse direction: the very next state starts from the same value
        // and velocity, it does not jump.
        val after = SpringPhysics.step(before, 0f, MotionTokens.SNAPPY, 1e-4f)
        assertEquals(before.value, after.value, 0.5f)
        assertEquals(before.velocity, after.velocity, 50f)
    }

    @Test fun `a long frame does not destabilise the spring`() {
        val s = SpringPhysics.step(SpringState(0f, 5000f), 100f, MotionTokens.LIVELY, 1.0f)
        assertTrue(s.value.isFinite() && s.velocity.isFinite())
        assertTrue(abs(s.value - 100f) < 20f)
    }

    @Test fun `release velocity carries into the settle`() {
        val flung = SpringPhysics.step(SpringState(0f, 4000f), 0f, MotionTokens.SNAPPY, 1f / 60f)
        assertTrue("moves in the fling direction first", flung.value > 0f)
    }

    @Test fun `settle time is bounded`() {
        val t = SpringPhysics.settleTime(MotionTokens.SNAPPY, 1000f, 0.5f)
        assertTrue("snappy settles quickly ($t s)", t < 0.6f)
        assertTrue(SpringPhysics.settleTime(MotionTokens.GENTLE, 1000f, 0.5f) > t)
    }

    @Test fun `system animations off means instant motion`() {
        assertFalse(MotionPolicy.resolve(MotionPreference.FULL, 0f, true, true).enabled)
        assertFalse(MotionPolicy.resolve(MotionPreference.OFF, 1f, true, true).enabled)
        assertFalse(MotionPolicy.resolve(MotionPreference.FULL, 1f, transitionsAllowed = false, overshootAllowed = true).enabled)
    }

    @Test fun `reduced motion removes overshoot and shortens time`() {
        val p = MotionPolicy.resolve(MotionPreference.REDUCED, 1f, true, true)
        assertTrue(p.enabled)
        assertFalse(p.allowOvershoot)
        assertTrue(p.timeScale < 1f)
        assertTrue(p.reduced)
        assertFalse(MotionPolicy.DEFAULT.reduced)
        assertTrue(p.adapt(MotionTokens.LIVELY).dampingRatio >= 1f)
    }

    @Test fun `system animator scale is followed`() {
        assertEquals(2f, MotionPolicy.resolve(MotionPreference.FULL, 2f, true, true).timeScale, 0f)
    }
}
