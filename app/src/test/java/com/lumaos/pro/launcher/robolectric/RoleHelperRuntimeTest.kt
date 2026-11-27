package com.lumaos.pro.launcher.robolectric

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.core.util.RoleHelper
import com.lumaos.pro.launcher.launcher.HomeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Default-launcher status drives the layer-2 fallback note: the full
 * restriction presentation needs LumaOS Pro to own HOME. Status must track
 * the currently resolved HOME activity in both directions — gaining and
 * losing the role — never a saved onboarding result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = LumaApp::class)
class RoleHelperRuntimeTest {

    private val context get() = ApplicationProvider.getApplicationContext<LumaApp>()

    private fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .addCategory(Intent.CATEGORY_DEFAULT)

    private fun setHomeActivityEnabled(enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, HomeActivity::class.java),
            state,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun setHomeRoleHeld(held: Boolean) {
        val rm = context.getSystemService(RoleManager::class.java) ?: return
        val s = shadowOf(rm)
        if (held) {
            s.addHeldRole(RoleManager.ROLE_HOME)
        } else if (rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            s.removeHeldRole(RoleManager.ROLE_HOME)
        }
    }

    private fun resolveHomeTo(packageName: String) {
        val isSelf = packageName == context.packageName
        setHomeActivityEnabled(isSelf)
        setHomeRoleHeld(isSelf)
        if (!isSelf) {
            val info = ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    this.packageName = packageName
                    name = "$packageName.Home"
                }
            }
            shadowOf(context.packageManager).addResolveInfoForIntent(homeIntent(), info)
        }
    }

    @Test fun `no home resolution reads as not default`() {
        setHomeActivityEnabled(false)
        setHomeRoleHeld(false)
        assertNull(RoleHelper.defaultHomePackage(context))
        assertFalse(RoleHelper.isDefaultLauncher(context))
    }

    @Test
    fun `gaining and losing home ownership is tracked`() {
        resolveHomeTo(context.packageName)
        assertEquals(context.packageName, RoleHelper.defaultHomePackage(context))
        assertTrue(RoleHelper.isDefaultLauncher(context))

        resolveHomeTo("com.other.launcher")
        assertEquals("com.other.launcher", RoleHelper.defaultHomePackage(context))
        assertFalse(RoleHelper.isDefaultLauncher(context))
    }

    @Test fun `match flags do not affect the resolution`() {
        resolveHomeTo(context.packageName)
        val resolved = context.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        assertEquals(context.packageName, resolved?.activityInfo?.packageName)
    }
}
