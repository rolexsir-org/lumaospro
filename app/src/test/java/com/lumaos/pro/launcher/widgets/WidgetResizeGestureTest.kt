package com.lumaos.pro.launcher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetResizeGestureTest {
    private fun gesture() = WidgetResizeGesture(
        startY = 20f, originalHeight = 200, rowHeight = 100,
        minRows = 1, maxRows = 4, touchSlop = 8
    )

    @Test
    fun `a tap opens the accessible height menu instead of committing a resize`() {
        val gesture = gesture()
        assertEquals(200, gesture.move(25f))
        assertFalse(gesture.dragged)
        assertEquals(WidgetResizeResult.OpenMenu, gesture.finish(cancelled = false))
    }

    @Test
    fun `a drag commits the previewed height clamped to provider limits`() {
        val gesture = gesture()
        assertEquals(400, gesture.move(1000f))
        assertTrue(gesture.dragged)
        assertEquals(WidgetResizeResult.Commit(4), gesture.finish(cancelled = false))
    }

    @Test
    fun `half a row rounds up on commit`() {
        val gesture = gesture()
        assertEquals(250, gesture.move(70f))
        assertEquals(WidgetResizeResult.Commit(3), gesture.finish(cancelled = false))
    }

    @Test
    fun `cancelling a drag restores the original height without committing`() {
        val gesture = gesture()
        assertEquals(320, gesture.move(140f))
        assertEquals(WidgetResizeResult.Cancel(200), gesture.finish(cancelled = true))
    }
}
