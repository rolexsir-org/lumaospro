package com.lumaos.pro.launcher.robolectric

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.context.FocusAccess
import com.lumaos.pro.launcher.context.FocusAccessConsent
import com.lumaos.pro.launcher.context.FocusAccessConsentState
import com.lumaos.pro.launcher.context.FocusAccessUiState
import com.lumaos.pro.launcher.context.FocusEnforcement
import com.lumaos.pro.launcher.core.LumaApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Consent + grant through Android's real `Settings.Secure` read path and the
 * app's real DataStore.
 *
 * The two facts are deliberately independent and this test proves they cannot
 * be conflated:
 *
 *  - the grant can be on while consent is not given (user enabled the service
 *    in system settings) — status must be NEEDS_DISCLOSURE, enforcement off;
 *  - consent can be recorded while the grant is off — status must be
 *    AWAITING_PERMISSION, enforcement off, and no re-prompt;
 *  - revoking the grant or disabling the framework keeps the consent record
 *    but turns enforcement off immediately.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = LumaApp::class)
class FocusAccessConsentRuntimeTest {

    private val now = 1_700_000_000_000L
    private val context get() = ApplicationProvider.getApplicationContext<LumaApp>()
    private val prefs get() = context.core.prefs

    @org.junit.Before fun setUp() = runBlocking {
        val resolver = context.contentResolver
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        Settings.Secure.putString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ""
        )
        prefs.setFocusAccessConsent(com.lumaos.pro.launcher.context.FocusAccessConsentRecord())
    }

    private fun listService() {
        val resolver = context.contentResolver
        val flat = FocusAccess.component(context).flattenToString()
        Settings.Secure.putString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            flat
        )
    }

    private fun grantThroughAndroid() {
        val resolver = context.contentResolver
        listService()
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
    }

    @Test fun `grant without the in-app disclosure is not consent and never enforces`() {
        assertFalse(FocusAccess.isGranted(context))
        grantThroughAndroid()
        assertTrue(FocusAccess.isGranted(context))

        runBlocking {
            val record = prefs.focusAccessConsent.first()
            assertEquals(FocusAccessConsentState.NOT_ASKED, record.state)
            assertEquals(
                FocusAccessUiState.NEEDS_DISCLOSURE,
                FocusAccessConsent.uiState(record, FocusAccess.isGranted(context))
            )
            assertFalse(FocusAccessConsent.mayEnforce(record, FocusAccess.isGranted(context)))
        }
    }

    @Test fun `consent then grant is active and revoking the grant returns to awaiting`() {
        runBlocking { prefs.setFocusAccessConsent(FocusAccessConsent.agree(now)) }
        assertFalse(FocusAccess.isGranted(context))
        runBlocking {
            assertEquals(
                FocusAccessUiState.AWAITING_PERMISSION,
                FocusAccessConsent.uiState(prefs.focusAccessConsent.first(), FocusAccess.isGranted(context))
            )
        }

        grantThroughAndroid()
        assertTrue(FocusAccess.isGranted(context))
        runBlocking {
            assertEquals(
                FocusAccessUiState.ACTIVE,
                FocusAccessConsent.uiState(prefs.focusAccessConsent.first(), FocusAccess.isGranted(context))
            )
            assertTrue(FocusAccessConsent.mayEnforce(prefs.focusAccessConsent.first(), FocusAccess.isGranted(context)))
        }

        // The user turns the service off in Android settings; another service stays on.
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.example.other/.TalkBack"
        )
        assertFalse(FocusAccess.isGranted(context))
        runBlocking {
            assertEquals(
                FocusAccessUiState.AWAITING_PERMISSION,
                FocusAccessConsent.uiState(prefs.focusAccessConsent.first(), FocusAccess.isGranted(context))
            )
            assertFalse(FocusAccessConsent.mayEnforce(prefs.focusAccessConsent.first(), FocusAccess.isGranted(context)))
            // Consent is not discarded by a revocation, so re-enabling needs no second disclosure.
            assertFalse(FocusAccessConsent.requiresDisclosure(prefs.focusAccessConsent.first()))
        }
    }

    @Test fun `disabling the accessibility framework disables enforcement`() {
        runBlocking { prefs.setFocusAccessConsent(FocusAccessConsent.agree(now)) }
        listService()
        // Framework master switch off: the service is listed but not running.
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)

        assertFalse(FocusAccess.isGranted(context))
        runBlocking {
            assertEquals(
                FocusAccessUiState.AWAITING_PERMISSION,
                FocusAccessConsent.uiState(prefs.focusAccessConsent.first(), FocusAccess.isGranted(context))
            )
            assertFalse(FocusAccessConsent.mayEnforce(prefs.focusAccessConsent.first(), permissionGranted = false))
        }
    }

    @Test fun `declining keeps layer one working`() {
        runBlocking {
            prefs.setFocusAccessConsent(FocusAccessConsent.decline(now))
            prefs.setFocusEnabled(ContextProfile.WORK, true)
        }

        // Nothing about layer 1 consults consent or the accessibility grant.
        runBlocking {
            val snapshot = FocusEnforcement.Snapshot(
                active = ContextProfile.WORK,
                focusEnabled = prefs.focusEnabled(ContextProfile.WORK).first(),
                restrictedPackages = setOf("com.example.game"),
                unlockProfile = null,
                unlockUntilMillis = 0L
            )
            assertTrue(
                FocusEnforcement.shouldBlock(
                    snapshot,
                    "com.example.game",
                    context.packageName,
                    System.currentTimeMillis()
                )
            )
            assertFalse(FocusAccessConsent.mayEnforce(prefs.focusAccessConsent.first(), permissionGranted = true))
        }
    }
}
