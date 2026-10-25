package com.lumaos.pro.launcher.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilityTest {

    @Test
    fun `a 2GB four core phone is the low tier target device`() {
        val capability = DeviceCapabilityClassifier.classify(
            totalMemoryMb = 2048, isLowRamDevice = false, cpuCoreCount = 4, screenLongEdgeDp = 640
        )
        assertEquals(DeviceTier.LOW, capability.tier)
    }

    @Test
    fun `1GB or less is low tier regardless of core count`() {
        val capability = DeviceCapabilityClassifier.classify(
            totalMemoryMb = 1024, isLowRamDevice = false, cpuCoreCount = 8, screenLongEdgeDp = 640
        )
        assertEquals(DeviceTier.LOW, capability.tier)
    }

    @Test
    fun `a dual core penalty pushes a mid range device down`() {
        val fast = DeviceCapabilityClassifier.classify(4096, false, 4, 640)
        val slow = DeviceCapabilityClassifier.classify(4096, false, 2, 640)
        assertEquals(DeviceTier.MEDIUM, fast.tier)
        assertEquals(DeviceTier.LOW, slow.tier)
    }

    @Test
    fun `a high memory device with many cores reaches the top tier`() {
        val capability = DeviceCapabilityClassifier.classify(8192, false, 8, 900)
        assertEquals(DeviceTier.HIGH, capability.tier)
    }

    @Test
    fun `the platform low ram verdict caps the tier`() {
        // A device with generous specs that the system still flags as low-RAM
        // must not be treated as high-end on core count alone.
        val capability = DeviceCapabilityClassifier.classify(8192, isLowRamDevice = true, 8, 900)
        assertTrue(capability.tier != DeviceTier.HIGH)
    }

    @Test
    fun `small screens are recorded`() {
        val small = DeviceCapabilityClassifier.classify(2048, false, 4, 480)
        val large = DeviceCapabilityClassifier.classify(2048, false, 4, 800)
        assertTrue(small.smallScreen)
        assertFalse(large.smallScreen)
    }

    @Test
    fun `classification always explains itself`() {
        val capability = DeviceCapabilityClassifier.classify(2048, false, 4, 640)
        assertTrue(capability.reasons.isNotEmpty())
        assertTrue(capability.reasons.any { it.contains("2 GB") || it.contains("2048") })
        assertTrue(capability.reasons.any { it.contains("cores") })
    }

    @Test
    fun `tier never goes out of range at the extremes`() {
        val tiny = DeviceCapabilityClassifier.classify(0, true, 1, 0)
        val huge = DeviceCapabilityClassifier.classify(64_000, false, 32, 4000)
        assertEquals(DeviceTier.LOW, tiny.tier)
        assertEquals(DeviceTier.HIGH, huge.tier)
    }
}

class ProfileResolverTest {

    private val low = DeviceCapability(DeviceTier.LOW, 2048, true, 4, true, emptyList())
    private val mid = DeviceCapability(DeviceTier.MEDIUM, 4096, false, 4, false, emptyList())
    private val high = DeviceCapability(DeviceTier.HIGH, 8192, false, 8, false, emptyList())

    @Test
    fun `automatic selection follows device tier`() {
        assertEquals(ProfileId.LIGHTWEIGHT, ProfileResolver.resolve(null, low).profile.id)
        assertEquals(ProfileId.BALANCED, ProfileResolver.resolve(null, mid).profile.id)
        assertEquals(ProfileId.VISUAL_QUALITY, ProfileResolver.resolve(null, high).profile.id)
    }

    @Test
    fun `an explicit user choice always wins`() {
        val result = ProfileResolver.resolve(ProfileId.VISUAL_QUALITY, low)
        assertEquals(ProfileId.VISUAL_QUALITY, result.profile.id)
        assertTrue(result.chosenByUser)
        assertTrue(result.reason.contains("Chosen by you"))
    }

    @Test
    fun `memory pressure steps down only in automatic mode`() {
        val auto = ProfileResolver.resolve(null, mid, underMemoryPressure = true)
        assertEquals(ProfileId.LIGHTWEIGHT, auto.profile.id)

        val manual = ProfileResolver.resolve(ProfileId.VISUAL_QUALITY, mid, underMemoryPressure = true)
        // A transient pressure spike must never silently override the user.
        assertEquals(ProfileId.VISUAL_QUALITY, manual.profile.id)
    }

    @Test
    fun `pressure cannot step below the floor`() {
        val result = ProfileResolver.resolve(null, low, underMemoryPressure = true)
        assertEquals(ProfileId.LIGHTWEIGHT, result.profile.id)
    }

    @Test
    fun `resolution always carries a reason`() {
        assertTrue(ProfileResolver.resolve(null, mid).reason.isNotBlank())
    }
}

class ProfilesTest {

    @Test
    fun `the ladder steps up and down and clamps at both ends`() {
        assertEquals(ProfileId.BALANCED, Profiles.step(ProfileId.LIGHTWEIGHT, 1))
        assertEquals(ProfileId.LIGHTWEIGHT, Profiles.step(ProfileId.LIGHTWEIGHT, -1))
        assertEquals(ProfileId.LIGHTWEIGHT, Profiles.step(ProfileId.VISUAL_QUALITY, -5))
        assertEquals(ProfileId.VISUAL_QUALITY, Profiles.step(ProfileId.VISUAL_QUALITY, 5))
    }

    @Test
    fun `every profile is retrievable by id`() {
        ProfileId.entries.forEach { id -> assertEquals(id, Profiles.of(id).id) }
    }

    @Test
    fun `profiles are ordered from leanest to richest`() {
        assertTrue(Profiles.LIGHTWEIGHT.maxCachedIcons < Profiles.BALANCED.maxCachedIcons)
        assertTrue(Profiles.BALANCED.maxCachedIcons < Profiles.VISUAL_QUALITY.maxCachedIcons)
        assertFalse(Profiles.LIGHTWEIGHT.frameMonitorEnabled)
        assertTrue(Profiles.BALANCED.frameMonitorEnabled)
    }
}
