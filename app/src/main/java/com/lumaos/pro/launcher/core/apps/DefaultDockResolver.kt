package com.lumaos.pro.launcher.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.lumaos.pro.launcher.core.model.ComponentKey

/**
 * Works out a sensible starting dock on first run.
 *
 * LumaOS Pro does not ship a hard-coded list of package names: OEMs rename system
 * apps constantly, and a hard-coded dock full of missing icons is worse than an
 * empty one. Instead, each slot is resolved by *intent* - "whatever handles
 * dialling", "whatever handles HTTPS" - which picks up the real app on any
 * device. Anything that does not resolve is simply omitted.
 */
object DefaultDockResolver {

    /** Slot order: the four things almost everyone reaches for. */
    private fun slotIntents(): List<Intent> = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        },
        Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        Intent(Settings.ACTION_SETTINGS)
    )

    /**
     * Resolves each slot against the installed apps.
     *
     * @return one key per resolved slot, de-duplicated, in slot order.
     */
    fun resolve(context: Context, apps: List<AppEntry>): List<ComponentKey> {
        val packageManager = context.packageManager
        val byPackage = HashMap<String, AppEntry>()
        for (entry in apps) {
            // First launcher activity wins for a package with several icons.
            byPackage.putIfAbsent(entry.key.packageName, entry)
        }

        val out = ArrayList<ComponentKey>(slotIntents().size)
        for (intent in slotIntents()) {
            val resolved = runCatching {
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }.getOrNull() ?: continue

            val packageName = resolved.activityInfo?.packageName ?: continue
            val entry = byPackage[packageName] ?: continue
            if (out.none { it.packageName == entry.key.packageName }) {
                out += entry.key
            }
        }
        return out
    }
}
