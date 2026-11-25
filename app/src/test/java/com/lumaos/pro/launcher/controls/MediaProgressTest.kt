package com.lumaos.pro.launcher.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaProgressTest {
    private val playing = MediaSnapshot.EMPTY.copy(
        playing = true, positionMs = 10_000L, durationMs = 30_000L,
        positionUpdatedAtMs = 1_000L, playbackSpeed = 1f
    )

    @Test fun `position uses monotonic elapsed time and clamps to duration`() {
        assertEquals(12_000L, MediaProgress.position(playing, 3_000L))
        assertEquals(30_000L, MediaProgress.position(playing, 100_000L))
        assertEquals(10_000L, MediaProgress.position(playing, 500L))
    }

    @Test fun `paused and unknown sessions never invent progress`() {
        assertEquals(10_000L, MediaProgress.position(playing.copy(playing = false), 20_000L))
        assertNull(MediaProgress.position(playing.copy(positionMs = null), 20_000L))
        assertNull(MediaProgress.position(playing.copy(durationMs = 0), 20_000L))
    }

    @Test fun `new device shortcuts appear only with resolvable settings`() {
        val unavailable = QuickControlsPolicy.tilesFor(QuickControlsSnapshot.EMPTY, false)
        assertTrue(TileId.POWER_SAVE !in unavailable && TileId.LOCATION !in unavailable)
        val available = QuickControlsPolicy.tilesFor(
            QuickControlsSnapshot(powerSettingsAvailable = true, locationSettingsAvailable = true), false
        )
        assertTrue(TileId.POWER_SAVE in available && TileId.LOCATION in available)
        assertTrue(QuickControlsPolicy.opensSystemRoute(TileId.POWER_SAVE, QuickControlsSnapshot.EMPTY))
        assertTrue(QuickControlsPolicy.opensSystemRoute(TileId.LOCATION, QuickControlsSnapshot.EMPTY))
    }
}
