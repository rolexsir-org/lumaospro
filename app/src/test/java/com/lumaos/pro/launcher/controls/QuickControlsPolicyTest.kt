package com.lumaos.pro.launcher.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Quick Controls decisions that can be made without a device: which tiles
 * exist, how many columns fit, how a slider maps onto a system level, and how the
 * Focus tile shares Android's interruption filter with the context-profile
 * automation.
 *
 * Plain JVM tests. Every platform constant is written as a literal here, so
 * nothing in this file needs `android.jar` at runtime.
 */
class QuickControlsPolicyTest {

    // android.app.NotificationManager constants, as literals.
    private val filterAll = 1
    private val filterPriority = 2

    private fun snapshot(
        castAvailable: Boolean = false,
        cameraAvailable: Boolean = false
    ) = QuickControlsSnapshot(castAvailable = castAvailable, cameraAvailable = cameraAvailable)

    // ── Columns ────────────────────────────────────────────────────────────

    @Test
    fun `columns widen with the panel instead of stretching tiles`() {
        assertEquals(2, QuickControlsPolicy.tileColumnsFor(0))
        assertEquals(2, QuickControlsPolicy.tileColumnsFor(-40))
        assertEquals(2, QuickControlsPolicy.tileColumnsFor(299))
        assertEquals(3, QuickControlsPolicy.tileColumnsFor(300))
        assertEquals(3, QuickControlsPolicy.tileColumnsFor(399))
        assertEquals(4, QuickControlsPolicy.tileColumnsFor(400))
        assertEquals(4, QuickControlsPolicy.tileColumnsFor(559))
        assertEquals(5, QuickControlsPolicy.tileColumnsFor(560))
        assertEquals(6, QuickControlsPolicy.tileColumnsFor(720))
        assertEquals(6, QuickControlsPolicy.tileColumnsFor(1600))
    }

    @Test
    fun `a phone-width panel keeps every tile at least 72dp wide`() {
        // The narrowest width that still gets four columns must leave each tile
        // wide enough for its label on one line.
        assertTrue(400 / QuickControlsPolicy.tileColumnsFor(400) >= 72)
    }

    // ── Tile set ───────────────────────────────────────────────────────────

    @Test
    fun `connectivity and device tiles are always offered, even when Android owns the write`() {
        val tiles = QuickControlsPolicy.tilesFor(snapshot(), torchCapable = false)
        assertTrue(tiles.containsAll(listOf(
            TileId.WIFI, TileId.MOBILE_DATA, TileId.BLUETOOTH, TileId.AIRPLANE,
            TileId.HOTSPOT, TileId.ROTATION_LOCK, TileId.FOCUS
        )))
        // No flash unit on this device, so no torch switch that could not move.
        assertFalse(tiles.contains(TileId.TORCH))
    }

    @Test
    fun `the torch tile exists only when the device really has a flash unit`() {
        assertTrue(QuickControlsPolicy.tilesFor(snapshot(), torchCapable = true).contains(TileId.TORCH))
        assertFalse(QuickControlsPolicy.tilesFor(snapshot(), torchCapable = false).contains(TileId.TORCH))
    }

    @Test
    fun `cast and camera are offered only when a real destination answers`() {
        val none = QuickControlsPolicy.tilesFor(snapshot(), torchCapable = true)
        assertFalse(none.contains(TileId.CAST))
        assertFalse(none.contains(TileId.CAMERA))

        val both = QuickControlsPolicy.tilesFor(
            snapshot(castAvailable = true, cameraAvailable = true), torchCapable = true
        )
        assertTrue(both.contains(TileId.CAST))
        assertTrue(both.contains(TileId.CAMERA))
    }

    @Test
    fun `tile order is stable so a re-read cannot shuffle the panel under a finger`() {
        val first = QuickControlsPolicy.tilesFor(snapshot(castAvailable = true), torchCapable = true)
        val second = QuickControlsPolicy.tilesFor(snapshot(castAvailable = true), torchCapable = true)
        assertEquals(first, second)
        assertEquals(TileId.WIFI, first.first())
    }

    @Test
    fun `a control Android reserves is routed, not hidden and not faked`() {
        val systemWifi = snapshot().copy(wifi = SwitchState.systemRoute(on = null))
        assertTrue(QuickControlsPolicy.opensSystemRoute(TileId.WIFI, systemWifi))

        val directWifi = snapshot().copy(wifi = SwitchState.direct(on = true))
        assertFalse(QuickControlsPolicy.opensSystemRoute(TileId.WIFI, directWifi))

        // Destinations are always routed: they have no state of their own to write.
        assertTrue(QuickControlsPolicy.opensSystemRoute(TileId.CAST, snapshot()))
        assertTrue(QuickControlsPolicy.opensSystemRoute(TileId.CAMERA, snapshot()))
        assertTrue(QuickControlsPolicy.opensSystemRoute(TileId.HOTSPOT, snapshot()))
    }

    // ── Slider mapping ─────────────────────────────────────────────────────

    @Test
    fun `brightness never maps to a level that could blank the screen`() {
        assertEquals(1, QuickControlsPolicy.sliderToLevel(0f, 0, 255, allowZero = false))
        assertEquals(1, QuickControlsPolicy.sliderToLevel(0.001f, 0, 255, allowZero = false))
        assertEquals(255, QuickControlsPolicy.sliderToLevel(1f, 0, 255, allowZero = false))
        assertEquals(128, QuickControlsPolicy.sliderToLevel(0.5f, 1, 255, allowZero = false))
    }

    @Test
    fun `volume keeps zero because mute is a real intent`() {
        assertEquals(0, QuickControlsPolicy.sliderToLevel(0f, 0, 15, allowZero = true))
        assertEquals(15, QuickControlsPolicy.sliderToLevel(1f, 0, 15, allowZero = true))
        assertEquals(7, QuickControlsPolicy.sliderToLevel(0.5f, 0, 15, allowZero = true))
    }

    @Test
    fun `out-of-range fractions and degenerate ranges are clamped, never crash`() {
        assertEquals(255, QuickControlsPolicy.sliderToLevel(4f, 0, 255, allowZero = true))
        assertEquals(0, QuickControlsPolicy.sliderToLevel(-2f, 0, 255, allowZero = true))
        assertEquals(0, QuickControlsPolicy.sliderToLevel(0.5f, 0, 0, allowZero = true))
        // An inverted range reports its minimum instead of computing a negative span.
        assertEquals(9, QuickControlsPolicy.sliderToLevel(0.5f, 9, 3, allowZero = true))
    }

    @Test
    fun `a system level renders back as the same fraction`() {
        assertEquals(0.5f, QuickControlsPolicy.levelToFraction(128, 1, 255), 0.004f)
        assertEquals(0f, QuickControlsPolicy.levelToFraction(0, 0, 15), 0.0001f)
        assertEquals(1f, QuickControlsPolicy.levelToFraction(15, 0, 15), 0.0001f)
        // A value outside the reported range is clamped rather than drawn outside
        // the track.
        assertEquals(1f, QuickControlsPolicy.levelToFraction(99, 0, 15), 0.0001f)
        assertEquals(0f, QuickControlsPolicy.levelToFraction(5, 5, 5), 0.0001f)
    }

    @Test
    fun `slider state exposes a safe fraction for a zero span`() {
        assertEquals(0f, SliderState.UNAVAILABLE.fraction, 0.0001f)
        assertEquals(0, SliderState.UNAVAILABLE.span)
        assertEquals(0.5f, SliderState(8, 0, 16, Capability.DIRECT).fraction, 0.0001f)
    }

    // ── Battery ────────────────────────────────────────────────────────────

    @Test
    fun `battery percent comes from Android's level and scale pair`() {
        assertEquals(50, QuickControlsPolicy.batteryPercent(level = 50, scale = 100))
        assertEquals(75, QuickControlsPolicy.batteryPercent(level = 3, scale = 4) ?: -1)
        assertEquals(100, QuickControlsPolicy.batteryPercent(level = 5, scale = 4))
        assertEquals(0, QuickControlsPolicy.batteryPercent(level = 0, scale = 100))
    }

    @Test
    fun `an unusable battery reading is null rather than a invented number`() {
        assertNull(QuickControlsPolicy.batteryPercent(level = -1, scale = 100))
        assertNull(QuickControlsPolicy.batteryPercent(level = 50, scale = 0))
        assertNull(QuickControlsPolicy.batteryPercent(level = 50, scale = -1))
        assertFalse(QuickControlsPolicy.showBatteryPercent(BatteryReading.UNKNOWN))
        assertFalse(QuickControlsPolicy.showBatteryPercent(null))
        assertTrue(QuickControlsPolicy.showBatteryPercent(BatteryReading(percent = 42, charging = true)))
    }

    // ── Focus and the existing DND automation ──────────────────────────────

    @Test
    fun `switching focus off by hand ends the automation's ownership`() {
        val decision = DndCoordination.afterUserChange(
            managed = true, previousFilter = filterPriority, newFilter = filterAll,
            filterAll = filterAll, policyAccessGranted = true
        )
        assertFalse(decision.managed)
        // Nothing to restore: the user just chose "no do not disturb".
        assertNull(decision.restoreFilter)
    }

    @Test
    fun `switching focus on from a clean state records what to give back`() {
        val decision = DndCoordination.afterUserChange(
            managed = false, previousFilter = filterAll, newFilter = filterPriority,
            filterAll = filterAll, policyAccessGranted = true
        )
        assertTrue(decision.managed)
        assertEquals(filterAll, decision.restoreFilter)
    }

    @Test
    fun `without notification policy access nothing is claimed or written`() {
        val decision = DndCoordination.afterUserChange(
            managed = true, previousFilter = filterAll, newFilter = filterPriority,
            filterAll = filterAll, policyAccessGranted = false
        )
        assertTrue(decision.managed)
        assertNull(decision.restoreFilter)
    }

    @Test
    fun `a change while the automation already owns the filter leaves its bookkeeping alone`() {
        val decision = DndCoordination.afterUserChange(
            managed = true, previousFilter = filterPriority, newFilter = filterPriority,
            filterAll = filterAll, policyAccessGranted = true
        )
        assertTrue(decision.managed)
        assertNull(decision.restoreFilter)
    }

    // ── Torch ──────────────────────────────────────────────────────────────

    @Test
    fun `the torch is offered only when it can actually be driven`() {
        assertEquals(
            TorchPolicy.Verdict.AVAILABLE,
            TorchPolicy.verdict(cameraCount = 2, flashAvailable = true, cameraInUse = false)
        )
        assertTrue(TorchPolicy.canToggle(TorchPolicy.Verdict.AVAILABLE))

        assertEquals(
            TorchPolicy.Verdict.NO_CAMERA,
            TorchPolicy.verdict(cameraCount = 0, flashAvailable = true, cameraInUse = false)
        )
        assertEquals(
            TorchPolicy.Verdict.NO_FLASH_UNIT,
            TorchPolicy.verdict(cameraCount = 1, flashAvailable = false, cameraInUse = false)
        )
        assertEquals(
            TorchPolicy.Verdict.IN_USE_BY_CAMERA_APP,
            TorchPolicy.verdict(cameraCount = 1, flashAvailable = true, cameraInUse = true)
        )
        listOf(
            TorchPolicy.Verdict.NO_CAMERA,
            TorchPolicy.Verdict.NO_FLASH_UNIT,
            TorchPolicy.Verdict.IN_USE_BY_CAMERA_APP
        ).forEach { assertFalse(TorchPolicy.canToggle(it)) }
    }

    @Test
    fun `a switch state that Android will not report is unknown, not off`() {
        assertNull(SwitchState.UNKNOWN.on)
        assertEquals(Capability.UNAVAILABLE, SwitchState.UNKNOWN.capability)
        assertNull(SwitchState.systemRoute(on = null).on)
    }
}
