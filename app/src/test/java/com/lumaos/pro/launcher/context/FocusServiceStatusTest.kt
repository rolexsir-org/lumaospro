package com.lumaos.pro.launcher.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusServiceStatusTest {

    @Test fun accessibilityDisabledMeansForegroundLayerUnavailable() {
        assertEquals(
            FocusServiceStatus.ACCESSIBILITY_OFF,
            FocusAccess.serviceStatus(accessibilityEnabled = false, serviceConnected = false)
        )
        // A stale connection signal must not overrule the current system grant.
        assertEquals(
            FocusServiceStatus.ACCESSIBILITY_OFF,
            FocusAccess.serviceStatus(accessibilityEnabled = false, serviceConnected = true)
        )
    }

    @Test fun enabledGrantDoesNotImplyConnectedService() {
        assertEquals(
            FocusServiceStatus.SERVICE_NOT_CONNECTED,
            FocusAccess.serviceStatus(accessibilityEnabled = true, serviceConnected = false)
        )
    }

    @Test fun enabledGrantAndConnectionReportConnected() {
        assertEquals(
            FocusServiceStatus.CONNECTED,
            FocusAccess.serviceStatus(accessibilityEnabled = true, serviceConnected = true)
        )
    }

    @Test fun serviceConnectionSignalTracksConnectAndDisconnect() {
        FocusAccess.updateServiceConnection(false)
        assertFalse(FocusAccess.serviceConnected.value)

        FocusAccess.updateServiceConnection(true)
        assertTrue(FocusAccess.serviceConnected.value)

        FocusAccess.updateServiceConnection(false)
        assertFalse(FocusAccess.serviceConnected.value)
    }
}
