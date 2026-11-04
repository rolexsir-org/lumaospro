package com.lumaos.pro.launcher.settings.studio

import com.lumaos.pro.launcher.settings.StudioConfiguration
import com.lumaos.pro.launcher.ui.theme.ThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioHistoryTest {

    @Test fun `edit undo redo round trip`() {
        val h = StudioHistory(0)
        assertFalse(h.canUndo)
        h.edit(1); h.edit(2)
        assertTrue(h.canUndo)
        assertEquals(1, h.undo())
        assertEquals(0, h.undo())
        assertFalse(h.canUndo)
        assertEquals(1, h.redo())
        assertEquals(2, h.redo())
        assertFalse(h.canRedo)
    }

    @Test fun `new edit clears the redo branch`() {
        val h = StudioHistory(0)
        h.edit(1); h.edit(2); h.undo()
        h.edit(9)
        assertFalse(h.canRedo)
        assertEquals(9, h.current)
        assertEquals(1, h.undo())
    }

    @Test fun `equal edits and reset behave`() {
        val h = StudioHistory(5)
        h.edit(5)
        assertFalse(h.canUndo)
        h.edit(6)
        h.reset(42)
        assertEquals(42, h.current)
        assertFalse(h.canUndo); assertFalse(h.canRedo)
    }

    @Test fun `previewing a theme package never mutates the applied configuration`() {
        val applied = StudioConfiguration()
        val h = StudioHistory(applied)
        h.edit(applied.withThemePackage(ThemeId.LUMA_MONO))
        assertEquals(ThemeId.SIGNATURE, applied.theme.themeId)
        assertEquals(ThemeId.LUMA_MONO, h.current.theme.themeId)
        // Discard path: undo returns exactly the applied value.
        assertEquals(applied, h.undo())
    }
}
