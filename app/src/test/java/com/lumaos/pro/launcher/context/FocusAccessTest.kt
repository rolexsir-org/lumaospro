package com.lumaos.pro.launcher.context

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusAccessTest {

    private val flat = "com.lumaos.pro.launcher/com.lumaos.pro.launcher.context.FocusAccessibilityService"
    private val short = "com.lumaos.pro.launcher/.context.FocusAccessibilityService"

    @Test fun `finds the service among others`() {
        val list = "com.other/.TalkBack:$flat:com.third/.Service"
        assertTrue(FocusAccess.isServiceListed(list, flat))
    }

    @Test fun `finds the short flattened form`() {
        assertTrue(FocusAccess.isServiceListed(short, short))
    }

    @Test fun `partial package names never match`() {
        // A hostile or coincidental package sharing our prefix must not read as granted.
        assertFalse(FocusAccess.isServiceListed("com.lumaos.pro.launcher.evil/.Service", flat))
        assertFalse(FocusAccess.isServiceListed(flat, "com.lumaos.pro.launcher"))
    }

    @Test fun `blank inputs never match`() {
        assertFalse(FocusAccess.isServiceListed("", flat))
        assertFalse(FocusAccess.isServiceListed(flat, ""))
        assertFalse(FocusAccess.isServiceListed("", ""))
    }
}
