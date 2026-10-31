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
import android.os.UserManager
import android.provider.Settings
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.core.util.LumaLog
import java.util.concurrent.ConcurrentHashMap

/** Result of an installed-app enumeration; false means [apps] may be partial or stale. */
data class AppRefreshResult(val apps: List<AppEntry>, val successful: Boolean)

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
 * The last usable app snapshot is retained across primary system-service
 * failures; rendered icons are cached separately by [IconCache].
 */
class AppRepository(
    private val context: Context,
    /** Injectable query for deterministic tests; production uses LauncherApps below. */
    private val activityListQuery: (() -> List<LauncherActivityInfo>)? = null
) {

    private val launcherApps: LauncherApps by lazy {
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    }

    private val userManager: UserManager? by lazy {
        runCatching { context.getSystemService(Context.USER_SERVICE) as? UserManager }.getOrNull()
    }

    private val userHandle: UserHandle = Process.myUserHandle()
    private val profileHandles = ConcurrentHashMap<Long, UserHandle>().apply {
        put(0L, userHandle)
    }

    @Volatile
    private var snapshot: Map<ComponentKey, LauncherActivityInfo> = emptyMap()

    @Volatile
    private var hasCompletedInitialScan: Boolean = false

    /** Last usable list, retained if the primary system query temporarily fails. */
    @Volatile
    private var lastEntries: List<AppEntry> = emptyList()

    /**
     * Reads the launchable activity list across personal and managed work
     * profiles. Must be called off the main thread: on a device with several
     * hundred apps this is a binder-heavy call.
     *
     * A failed binder/security query is not the same as a successful empty
     * result. Callers that remove stale layout entries must only do so after a
     * complete enumeration; otherwise a transient system-service failure could
     * erase the user's home layout and launch history.
     */
    @Suppress("MissingPermission")
    @Synchronized
    fun refreshSnapshot(): AppRefreshResult {
        val taggedInfos = ArrayList<Pair<Long, LauncherActivityInfo>>()
        var complete = true
        if (activityListQuery != null) {
            val infos = try {
                activityListQuery.invoke()
            } catch (e: SecurityException) {
                LumaLog.e("Not permitted to enumerate apps", e)
                return AppRefreshResult(lastEntries, successful = false)
            } catch (e: RuntimeException) {
                LumaLog.e("LauncherApps query failed", e)
                return AppRefreshResult(lastEntries, successful = false)
            }
            infos.forEach { taggedInfos += 0L to it }
        } else {
            val profiles = try {
                launcherApps.profiles
            } catch (e: RuntimeException) {
                LumaLog.e("Could not enumerate launcher profiles", e)
                return AppRefreshResult(lastEntries, successful = false)
            }
            if (profiles.isEmpty() || userHandle !in profiles) {
                LumaLog.w("Incomplete launcher profile list; retaining previous snapshot")
                return AppRefreshResult(lastEntries, successful = false)
            }
            for (profile in profiles) {
                val isPrimary = profile == userHandle
                if (!isPrimary) {
                    val quiet = try {
                        userManager?.isQuietModeEnabled(profile) ?: true
                    } catch (e: RuntimeException) {
                        LumaLog.e("Could not check launcher profile availability", e)
                        true
                    }
                    if (quiet) {
                        LumaLog.w("Launcher profile paused; skipping destructive reconciliation")
                        complete = false
                        continue
                    }
                }
                val serial = if (isPrimary) 0L else {
                    val id = try {
                        userManager?.getSerialNumberForUser(profile) ?: -1L
                    } catch (e: RuntimeException) {
                        LumaLog.e("Could not identify launcher profile", e)
                        -1L
                    }
                    if (id <= 0L) {
                        LumaLog.w("Incomplete launcher profile identity; retaining previous snapshot")
                        return AppRefreshResult(lastEntries, successful = false)
                    }
                    id
                }
                profileHandles[serial] = profile
                try {
                    val list = launcherApps.getActivityList(null, profile)
                    if (list == null) {
                        LumaLog.w("Launcher profile query returned no result")
                        if (isPrimary) return AppRefreshResult(lastEntries, successful = false)
                        complete = false
                        continue
                    }
                    for (info in list) taggedInfos += serial to info
                } catch (e: SecurityException) {
                    LumaLog.e("Not permitted to enumerate launcher profile", e)
                    if (isPrimary) return AppRefreshResult(lastEntries, successful = false)
                    complete = false
                } catch (e: RuntimeException) {
                    LumaLog.e("Launcher profile unavailable", e)
                    if (isPrimary) return AppRefreshResult(lastEntries, successful = false)
                    complete = false
                }
            }
        }

        val entries = ArrayList<AppEntry>(taggedInfos.size)
        val map = HashMap<ComponentKey, LauncherActivityInfo>(taggedInfos.size)
        for ((serial, info) in taggedInfos) {
            val component = info.componentName ?: continue
            val key = ComponentKey(component.packageName, component.className, serial)
            val label = info.label?.toString().orEmpty().ifBlank { component.packageName }
            entries += AppEntry(
                key = key,
                label = label,
                category = runCatching { info.applicationInfo.category }.getOrDefault(AppCategories.UNDEFINED),
                installedAt = runCatching { info.firstInstallTime }.getOrDefault(0L)
            )
            map[key] = info
        }
        val sorted = AppMatcher.sort(entries).toList()
        snapshot = map
        hasCompletedInitialScan = true
        lastEntries = sorted
        return AppRefreshResult(sorted, successful = complete)
    }

    /** Compatibility helper for consumers that only need the latest known list. */
    fun refresh(): List<AppEntry> = refreshSnapshot().apps

    /** Decodes an icon at [density] (badged automatically for work profiles). Returns null if gone. */
    fun iconFor(key: ComponentKey, density: Int): Drawable? {
        val info = snapshot[key] ?: if (!hasCompletedInitialScan) resolveSingleInfo(key) else null
        info ?: return null
        return runCatching {
            if (key.userSerial != 0L) info.getBadgedIcon(density) else info.getIcon(density)
        }.getOrNull()
    }

    /**
     * The launcher's own label for a package, taken from the LauncherApps
     * enumeration.
     *
     * LauncherApps is exempt from Android 11+ package-visibility filtering, so
     * this resolves packages that `PackageManager.getApplicationLabel` would
     * refuse to see. That matters for notification cards: the app that posted a
     * notification has no reason to be visible to this launcher, yet its real name
     * is the honest thing to show. Null means "no launchable activity here", and
     * the caller falls back to its own wording rather than guessing.
     */
    fun labelForPackage(packageName: String): String? =
        lastEntries.firstOrNull { it.key.packageName == packageName }?.label

    /** Icon for a package at [density], or null when it has no launchable activity here. */
    fun iconForPackage(packageName: String, density: Int): Drawable? {
        val key = lastEntries.firstOrNull { it.key.packageName == packageName }?.key ?: return null
        return iconFor(key, density)
    }

    /** Whether [key] is currently installed and launchable (resolves directly during cold start). */
    fun isInstalled(key: ComponentKey): Boolean {
        if (snapshot.containsKey(key)) return true
        if (!hasCompletedInitialScan) return resolveSingleInfo(key) != null
        return false
    }

    /** Applies the OS managed-profile badge when [userSerial] belongs to a work profile. */
    fun badgeIfManagedProfile(drawable: Drawable, userSerial: Long): Drawable {
        if (userSerial == 0L) return drawable
        val profile = profileForSerial(userSerial) ?: return drawable
        if (profile == userHandle) return drawable
        return runCatching { context.packageManager.getUserBadgedIcon(drawable, profile) }
            .getOrDefault(drawable)
    }

    private fun resolveSingleInfo(key: ComponentKey): LauncherActivityInfo? {
        if (activityListQuery != null) return null
        val profile = profileForSerial(key.userSerial) ?: return null
        return runCatching {
            launcherApps.getActivityList(key.packageName, profile)
                ?.firstOrNull { it.componentName?.className == key.className }
        }.getOrNull()
    }

    private fun profileForSerial(serial: Long): UserHandle? {
        if (serial == 0L) return userHandle
        profileHandles[serial]?.let { return it }
        val resolved = runCatching { userManager?.getUserForSerialNumber(serial) }.getOrNull()
        if (resolved != null) profileHandles[serial] = resolved
        return resolved
    }

    /**
     * Launches an app. Returns false if the app is no longer installed - the
     * caller shows a message; a launcher must never silently do nothing.
     */
    fun launch(key: ComponentKey, sourceBounds: android.graphics.Rect? = null, options: android.os.Bundle? = null): Boolean {
        val component = android.content.ComponentName(key.packageName, key.className)
        val targetProfile = profileForSerial(key.userSerial) ?: return false
        return try {
            // sourceBounds tells the system where the icon is; options carry
            // LumaOS Pro's clip-reveal opening animation from that icon.
            launcherApps.startMainActivity(component, targetProfile, sourceBounds, options)
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
