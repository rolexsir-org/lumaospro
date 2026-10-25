package com.lumaos.pro.launcher.effects

import com.lumaos.pro.launcher.intelligence.DeviceTier
import com.lumaos.pro.launcher.intelligence.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsPolicyTest {

    private val high = EffectsSignals(DeviceTier.HIGH, ProfileId.BALANCED, sdkInt = 34, crossWindowBlurEnabled = true, batteryPercent = 80)

    @Test fun `auto picks the level from profile and tier`() {
        assertEquals(EffectsLevel.FULL, EffectsPolicy.decide(EffectsPreference.AUTO, high).level)
        assertEquals(EffectsLevel.BALANCED, EffectsPolicy.decide(EffectsPreference.AUTO, high.copy(tier = DeviceTier.MEDIUM)).level)
        assertEquals(EffectsLevel.MINIMAL, EffectsPolicy.decide(EffectsPreference.AUTO, high.copy(profile = ProfileId.LIGHTWEIGHT)).level)
    }

    @Test fun `full on capable hardware enables blur and ambient motion`() {
        val d = EffectsPolicy.decide(EffectsPreference.AUTO, high)
        assertTrue(d.blur)
        assertTrue(d.ambientMotion)
        assertTrue(d.translucency)
        assertTrue(d.transitions)
    }

    @Test fun `minimal still keeps transitions`() {
        val d = EffectsPolicy.decide(EffectsPreference.MINIMAL, high)
        assertTrue("springs are cheap and stay on", d.transitions)
        assertFalse(d.blur)
        assertFalse(d.ambientMotion)
        assertFalse(d.translucency)
    }

    @Test fun `battery saver pauses motion and blur even when full is chosen`() {
        val d = EffectsPolicy.decide(EffectsPreference.FULL, high.copy(powerSave = true))
        assertEquals(EffectsLevel.FULL, d.level)
        assertFalse(d.ambientMotion)
        assertFalse(d.blur)
    }

    @Test fun `auto steps down under heat, memory pressure and jank`() {
        assertEquals(EffectsLevel.MINIMAL, EffectsPolicy.decide(EffectsPreference.AUTO, high.copy(thermalStatus = 3)).level)
        assertEquals(EffectsLevel.BALANCED, EffectsPolicy.decide(EffectsPreference.AUTO, high.copy(thermalStatus = 2)).level)
        assertEquals(EffectsLevel.BALANCED, EffectsPolicy.decide(EffectsPreference.AUTO, high.copy(memoryPressure = true)).level)
        assertEquals(EffectsLevel.BALANCED, EffectsPolicy.decide(EffectsPreference.AUTO, high.copy(jankPercent = 25.0)).level)
    }

    @Test fun `explicit choice is not stepped down for jank`() {
        assertEquals(EffectsLevel.FULL, EffectsPolicy.decide(EffectsPreference.FULL, high.copy(jankPercent = 30.0)).level)
    }

    @Test fun `severe jank in auto makes transitions instant`() {
        assertFalse(EffectsPolicy.decide(EffectsPreference.AUTO, high.copy(jankPercent = 45.0)).transitions)
    }

    @Test fun `blur needs API 31 and system blur support`() {
        assertFalse(EffectsPolicy.decide(EffectsPreference.FULL, high.copy(sdkInt = 30)).blur)
        assertFalse(EffectsPolicy.decide(EffectsPreference.FULL, high.copy(crossWindowBlurEnabled = false)).blur)
    }

    @Test fun `low battery pauses ambient motion`() {
        assertFalse(EffectsPolicy.decide(EffectsPreference.FULL, high.copy(batteryPercent = 10, charging = false)).ambientMotion)
        assertTrue(EffectsPolicy.decide(EffectsPreference.FULL, high.copy(batteryPercent = 10, charging = true)).ambientMotion)
    }

    @Test fun `system animations off disables transitions and depth`() {
        val d = EffectsPolicy.decide(EffectsPreference.FULL, high.copy(systemAnimatorScale = 0f))
        assertFalse(d.transitions)
        assertFalse(d.depthTransforms)
        assertFalse(d.ambientMotion)
    }

    @Test fun `every decision explains itself`() {
        assertTrue(EffectsPolicy.decide(EffectsPreference.AUTO, high).reasons.isNotEmpty())
    }
}
