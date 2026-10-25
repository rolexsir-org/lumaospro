package com.lumaos.pro.launcher.core.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.LauncherActivityInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.core.util.LumaLog

/**
 * The authoritative source of installed, launchable apps.
 *
 * Uses [LauncherApps] - the official launcher API - rather than
 * PackageManager.queryIntentActivities. Two reasons:
 *
 *  1. It is the documented interface for launcher-style app enumeration and is
 *     not subject to the package-visibility restrictions introduced in
 *     Android 11, so LumaOS Pro does not need QUERY_ALL_PACKAGES.
 *  2. It gives per-profile results and the correctly themed icon for free.
 *
 * This class does no caching of its own; icons are cached by [IconCache].
 */
class AppRepository(private val context: Context) {

    private val launcherApps: LauncherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private val userHandle: UserHandle = Process.myUserHandle()

    @Volatile
    private var snapshot: Map<ComponentKey, LauncherActivityInfo> = emptyMap()

    /**
     * Reads the launchable activity list. Must be called off the main thread:
     * on a device with several hundred apps this is a binder-heavy call.
     */
    @Suppress("MissingPermission")
    fun refresh(): List<AppEntry> {
        val infos = try {
            launcherApps.getActivityList(null, userHandle)
        } catch (e: SecurityException) {
            LumaLog.e("Not permitted to enumerate apps", e)
            emptyList<LauncherActivityInfo>()
        } catch (e: RuntimeException) {
            // LauncherApps is a system service; a dead binder here must not take
            // down the home screen.
            LumaLog.e("LauncherApps query failed", e)
            emptyList<LauncherActivityInfo>()
        }

        val entries = ArrayList<AppEntry>(infos.size)
        val map = HashMap<ComponentKey, LauncherActivityInfo>(infos.size)
        for (info in infos) {
            val component = info.componentName ?: continue
            val key = ComponentKey(component.packageName, component.className)
            val label = info.label?.toString().orEmpty().ifBlank { component.packageName }
            entries += AppEntry(
                key = key,
                label = label,
                category = runCatching { info.applicationInfo.category }.getOrDefault(AppCategories.UNDEFINED),
                installedAt = runCatching { info.firstInstallTime }.getOrDefault(0L)
            )
            map[key] = info
        }
        snapshot = map
        return AppMatcher.sort(entries)
    }

    /** Decodes an icon at [density]. Returns null if the app is gone. */
    fun iconFor(key: ComponentKey, density: Int): Drawable? =
        snapshot[key]?.getIcon(density)

    /** Whether [key] is currently installed and launchable. */
    fun isInstalled(key: ComponentKey): Boolean = snapshot.containsKey(key)

    /**
     * Launches an app. Returns false if the app is no longer installed - the
     * caller shows a message; a launcher must never silently do nothing.
     */
    fun launch(key: ComponentKey, sourceBounds: android.graphics.Rect? = null, options: android.os.Bundle? = null): Boolean {
        val component = android.content.ComponentName(key.packageName, key.className)
        return try {
            // sourceBounds tells the system where the icon is; options carry
            // LumaOS Pro's clip-reveal opening animation from that icon.
            launcherApps.startMainActivity(component, userHandle, sourceBounds, options)
            true
        } catch (e: ActivityNotFoundException) {
            LumaLog.w("App no longer installed: ${key.packageName}")
            false
        } catch (e: SecurityException) {
            LumaLog.w("Not permitted to launch ${key.packageName}")
            false
        } catch (e: RuntimeException) {
            LumaLog.e("Launch failed for ${key.packageName}", e)
            false
        }
    }

    /** System app-details screen for an app. */
    fun appDetailsIntent(key: ComponentKey): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${key.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Standard uninstall flow. LumaOS Pro shows the system confirmation dialog; it
     * never removes an app on its own. Requires REQUEST_DELETE_PACKAGES, which
     * is a normal permission granted at install time.
     */
    fun uninstallIntent(key: ComponentKey): Intent =
        Intent(Intent.ACTION_DELETE)
            .setData(Uri.parse("package:${key.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Wallpaper picker, offered as a long-press gesture action. */
    fun wallpaperPickerIntent(): Intent =
        Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Registers for package add/remove/change events.
     *
     * A manifest-declared receiver is not used: the callback route is
     * lifecycle-bound, so LumaOS Pro can stop listening while it is not the
     * foreground launcher instead of being woken for every install.
     */
    fun registerCallback(callback: LauncherApps.Callback) {
        try {
            launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        } catch (e: RuntimeException) {
            LumaLog.e("Failed to register package callback", e)
        }
    }

    fun unregisterCallback(callback: LauncherApps.Callback) {
        try {
            launcherApps.unregisterCallback(callback)
        } catch (e: RuntimeException) {
            LumaLog.e("Failed to unregister package callback", e)
        }
    }
}
