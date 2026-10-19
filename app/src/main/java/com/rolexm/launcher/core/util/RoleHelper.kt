package com.rolexm.launcher.core.util

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Managing the Home role - the one piece of launcher plumbing that is easy to
 * get wrong and impossible to fake.
 *
 * Three capabilities, all using documented APIs:
 *  - check whether ROLEXM currently holds the Home role;
 *  - request it, via RoleManager on Android 10+ with a fallback to the system
 *    Home settings screen;
 *  - hand it back, by opening the same system screen.
 *
 * There is no programmatic way for an app to *clear* another app's Home role,
 * and there should not be: the choice of default launcher must stay the user's.
 */
object RoleHelper {

    /** Package name of the current default launcher, or null if none. */
    fun defaultHomePackage(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolved?.activityInfo?.packageName
    }

    fun isDefaultLauncher(context: Context): Boolean =
        defaultHomePackage(context) == context.packageName

    /** Name of the current default launcher, for display in Settings. */
    fun defaultHomeLabel(context: Context): String? {
        val packageName = defaultHomePackage(context) ?: return null
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    /**
     * Builds the intent that asks the user to make ROLEXM the default launcher.
     *
     * Returns null whenever the role request cannot be used, and the caller then
     * falls back to [openHomeSettings]. That happens on Android 9 and below
     * (no RoleManager), on devices where the OEM has not implemented the role,
     * and in the inconsistent state where the role is held but the system still
     * resolves HOME to something else.
     *
     * Returning an intent rather than starting one keeps the Activity Result
     * contract with the caller, which is the only supported way to get a result
     * back from a role request.
     */
    fun homeRoleRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return null
        if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return null
        return runCatching { roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME) }
            .getOrNull()
    }

    /**
     * Opens the system default-launcher chooser.
     *
     * This is also the "switch away" path. It is intentionally the same screen
     * Android has always used, rather than anything custom: leaving a launcher
     * must be boring and obvious.
     */
    fun openHomeSettings(context: Context) {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            RolexLog.w("Home settings screen unavailable on this device")
        }
    }
}
