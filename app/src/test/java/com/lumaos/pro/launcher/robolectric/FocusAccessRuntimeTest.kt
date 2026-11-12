package com.lumaos.pro.launcher.robolectric

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.context.FocusAccess
import com.lumaos.pro.launcher.core.LumaApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The accessibility grant behind Focus layer 2, through the real
 * `Settings.Secure` read path: off by default, on only while the framework
 * is enabled *and* this service is listed, and instantly off again on
 * revocation — the state layer 1 carries on alone in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = LumaApp::class)
class FocusAccessRuntimeTest {

    private val context get() = ApplicationProvider.getApplicationContext<LumaApp>()

    @Test fun `grant is off by default`() {
        assertFalse(FocusAccess.isGranted(context))
    }

    @Test fun `grant is detected only with framework on and service listed`() {
        val resolver = context.contentResolver
        val flat = FocusAccess.component(context).flattenToString()
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, flat)
        // Listed but the framework master switch is off: not granted.
        assertFalse(FocusAccess.isGranted(context))
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        assertTrue(FocusAccess.isGranted(context))
    }

    @Test fun `revocation reads as not granted immediately`() {
        val resolver = context.contentResolver
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Settings.Secure.putString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            FocusAccess.component(context).flattenToString()
        )
        assertTrue(FocusAccess.isGranted(context))
        // The user toggles the service off; other services stay listed.
        Settings.Secure.putString(
            resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "com.other/.Service"
        )
        assertFalse(FocusAccess.isGranted(context))
    }

    @Test fun `settings route resolves to a real screen or nothing`() {
        // Must never crash and never promise an unresolvable screen; under
        // Robolectric the system screens don't exist, so null is the honest
        // answer and the UI falls back to a toast.
        val intent = FocusAccess.settingsIntent(context)
        if (intent != null) {
            assertTrue(intent.resolveActivity(context.packageManager) != null)
        }
    }
}
