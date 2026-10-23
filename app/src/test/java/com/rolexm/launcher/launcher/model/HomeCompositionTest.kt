package com.rolexm.launcher.launcher.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCompositionTest {

    @Test fun `default columns follow window width classes`() {
        assertEquals(4, GridRules.defaultColumns(360))
        assertEquals(5, GridRules.defaultColumns(412))
        assertEquals(6, GridRules.defaultColumns(700))
        assertEquals(7, GridRules.defaultColumns(1000))
    }

    @Test fun `user columns are clamped to keep touch targets`() {
        assertEquals(5, GridRules.effectiveColumns(7, 320))
        assertEquals(7, GridRules.effectiveColumns(7, 800))
        assertEquals(3, GridRules.effectiveColumns(1, 800))
    }

    @Test fun `side by side only for wide landscape windows`() {
        assertTrue(GridRules.useSideBySide(800, 400))
        assertFalse(GridRules.useSideBySide(400, 800))
        assertFalse(GridRules.useSideBySide(500, 300))
    }

    @Test fun `presets parse with a safe fallback and sane rows`() {
        assertEquals(HomePreset.ZEN, HomePreset.fromStorageKey("zen"))
        assertEquals(HomePreset.DEFAULT, HomePreset.fromStorageKey("nope"))
        HomePreset.entries.forEach { assertTrue(it.defaultRows in 3..8) }
        assertFalse(HomePreset.ZEN.showGrid)
    }
}
