package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where an authenticated launch lands.
 *
 * This is small but it is the difference between "the menu entry worked" and "the
 * menu entry did nothing", and it is also the only place a caller-supplied value
 * decides *which* Vault screen opens - so an unknown value must be inert.
 */
class VaultDestinationTests {

    @Test
    fun theHiddenAppsDestinationIsRecognised() {
        assertTrue(VaultDestination.opensHiddenApps(VaultDestination.HIDDEN_APPS))
        assertEquals(VaultDestination.HIDDEN_APPS, VaultDestination.normalize(VaultDestination.HIDDEN_APPS))
    }

    @Test
    fun noDestinationFallsBackToTheVaultHome() {
        assertFalse(VaultDestination.opensHiddenApps(null))
        assertEquals(VaultDestination.HOME, VaultDestination.normalize(null))
    }

    @Test
    fun anUnknownDestinationIsInert() {
        // A stale extra from an older build, or an arbitrary string, must not be able
        // to steer the unlock into some other screen.
        listOf("", "files", "hidden_apps ", "HIDDEN_APPS", "../files", "null").forEach { raw ->
            assertFalse("$raw must not open Hidden Apps", VaultDestination.opensHiddenApps(raw))
            assertEquals(VaultDestination.HOME, VaultDestination.normalize(raw))
        }
    }

    @Test
    fun theDestinationNamesAreStable() {
        // The value is written into an intent extra; renaming it silently would break
        // callers in other packages that compile against the constant.
        assertEquals("hidden_apps", VaultDestination.HIDDEN_APPS)
        assertEquals("home", VaultDestination.HOME)
    }
}
