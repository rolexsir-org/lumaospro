package com.lumaos.pro.launcher.robolectric

import android.content.Context
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.launcher.view.AlphaIndexView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlphaIndexRuntimeTest {
    @Test
    fun `accessibility and keyboard actions navigate available buckets`() {
        val view = AlphaIndexView(ApplicationProvider.getApplicationContext<Context>())
        view.letters = listOf('A', 'C', 'Z')
        val visited = mutableListOf<Char>()
        view.onLetterTouched = { visited += it }

        assertTrue(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null))
        assertTrue(view.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
        assertTrue(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null))
        assertEquals(listOf('A', 'C', 'A'), visited)
        assertFalse(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null))
    }
}
