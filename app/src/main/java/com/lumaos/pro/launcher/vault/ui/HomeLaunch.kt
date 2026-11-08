package com.lumaos.pro.launcher.vault.ui

import android.app.Activity
import android.content.Intent

/**
 * Returns from Vault to the launcher's home screen.
 *
 * Vault must not depend on the launcher's classes - the dependency runs the other
 * way (the launcher reads its hidden-app list from the Vault) - so the return trip
 * is expressed as the launcher's *HOME intent* rather than a class reference. That
 * is also more correct than naming an activity: the user's default launcher is
 * whatever the system resolves for `ACTION_MAIN` / `CATEGORY_HOME`, which is
 * LumaOS Pro when it is set as home and something sane otherwise.
 */
internal object HomeLaunch {

    /**
     * Brings the launcher's home screen to the front.
     *
     * `CLEAR_TOP | SINGLE_TOP` reuses the existing task instead of stacking a new
     * one, so leaving Vault lands on the home screen the user left, not a fresh
     * copy of it. `FLAG_ACTIVITY_NEW_TASK` is required only when the caller is not
     * an activity; every Vault caller is one, so it is not added - adding it would
     * risk a second launcher task.
     */
    fun start(activity: Activity) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val resolved = activity.packageManager.resolveActivity(intent, 0)
        if (resolved != null) {
            activity.startActivity(intent)
            return
        }
        // No home activity resolved at all (a device without a launcher is not a
        // real configuration, but a bare AOSP test image can be): try our own.
        runCatching {
            activity.startActivity(
                Intent().setClassName(activity, "com.lumaos.pro.launcher.launcher.HomeActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }
}
