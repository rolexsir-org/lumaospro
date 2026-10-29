package com.lumaos.pro.launcher.core.util

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
 *  - check whether LumaOS Pro currently holds the Home role;
 *  - request it, via RoleManager on Android 10+ with a fallback to the system
 *    Home settings screen;
 *  - hand it back, by opening the same system screen.
 *
 * There is no programmatic way for an app to *clear* another app's Home role,
 * and there should not be: the choice of default launcher must stay the user's.
 */
object RoleHelper {

    /** Package name of the current default launcher, or null if none. */
    fun defaultHomePackage(context: Context): String? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addCategory(Intent.CATEGORY_DEFAULT)
        context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
    }.onFailure {
        LumaLog.w("Could not resolve the current HOME activity")
    }.getOrNull()

    fun isDefaultLauncher(context: Context): Boolean {
        // Resolve what Android will actually launch for HOME and, where the
        // platform exposes it, cross-check the explicit HOME role. A saved
        // onboarding result or a successful result code is never evidence of
        // current role ownership.
        val resolvedHomeIsLuma = defaultHomePackage(context) == context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = runCatching { context.getSystemService(RoleManager::class.java) }.getOrNull()
            if (roleManager != null && runCatching {
                    roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
                }.getOrDefault(false)) {
                val roleHeld = runCatching {
                    roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                }.getOrNull()
                if (roleHeld != null) return roleHeld && resolvedHomeIsLuma
            }
        }
        // Android versions/OEM builds without an available Home role retain
        // the resolved HOME activity as the compatibility source of truth.
        return resolvedHomeIsLuma
    }

    /** Name of the current default launcher, for display in Settings. */
    fun defaultHomeLabel(context: Context): String? {
        val packageName = defaultHomePackage(context) ?: return null
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    /**
     * Builds the intent that asks the user to make LumaOS Pro the default launcher.
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
        return runCatching {
            val roleManager = context.getSystemService(RoleManager::class.java)
                ?: return@runCatching null
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return@runCatching null
            if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return@runCatching null
            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }.onFailure {
            LumaLog.w("Home role request unavailable on this device")
        }.getOrNull()
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
            LumaLog.w("Home settings screen unavailable on this device")
        }
    }
}
