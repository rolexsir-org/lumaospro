package com.lumaos.pro.launcher.core.apps

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.ui.theme.LumaTokens

/**
 * First-run layout & dock resolver (Phase 6).
 *
 * Resolves the canonical 4-icon Dock (Phone, Messages, Browser, Camera) via
 * system intents so OEM package variations always resolve cleanly, and selects
 * a breathing initial Home Grid (leaving 1–2 empty rows so the wallpaper shines)
 * ranked by UsageStatsManager (when available) with smart category fallbacks.
 */
object DefaultDockResolver {

    private fun primaryDockSlotCandidates(): List<List<Intent>> = listOf(
        // Slot 1: Phone / Dialer
        listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_VIEW, Uri.parse("tel:"))
        ),
        // Slot 2: Messages / SMS
        listOf(
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
        ),
        // Slot 3: Browser
        listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            },
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        ),
        // Slot 4: Camera (fallback Settings if device has no camera activity)
        listOf(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Settings.ACTION_SETTINGS)
        )
    )

    /**
     * Resolves up to [LumaTokens.dockIconCount] (`4`) dock apps in canonical
     * Phone -> Messages -> Browser -> Camera order, de-duplicated.
     */
    fun resolve(context: Context, apps: List<AppEntry>): List<ComponentKey> {
        if (apps.isEmpty()) return emptyList()
        val packageManager = context.packageManager
        val selfPkg = context.packageName
        val byPackage = LinkedHashMap<String, AppEntry>()
        for (entry in apps) {
            if (entry.key.packageName != selfPkg) {
                byPackage.putIfAbsent(entry.key.packageName, entry)
            }
        }

        val out = ArrayList<ComponentKey>(LumaTokens.dockIconCount)
        for (candidates in primaryDockSlotCandidates()) {
            if (out.size >= LumaTokens.dockIconCount) break
            var picked: ComponentKey? = null
            for (intent in candidates) {
                val resolved = runCatching {
                    packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                }.getOrNull() ?: continue
                val pkg = resolved.activityInfo?.packageName ?: continue
                val entry = byPackage[pkg] ?: continue
                if (out.none { it.packageName == entry.key.packageName }) {
                    picked = entry.key
                    break
                }
            }
            if (picked != null) out += picked
        }

        // Heuristic package-keyword fallback when running under headless/minimal ROMs
        if (out.size < LumaTokens.dockIconCount) {
            val dockKeywords = listOf("dialer", "phone", "messaging", "mms", "sms", "chrome", "browser", "camera")
            for (kw in dockKeywords) {
                if (out.size >= LumaTokens.dockIconCount) break
                val match = apps.firstOrNull { e ->
                    e.key.packageName != selfPkg &&
                        out.none { it.packageName == e.key.packageName } &&
                        e.key.packageName.lowercase().contains(kw)
                }
                if (match != null) out += match.key
            }
        }

        return out.take(LumaTokens.dockIconCount)
    }

    /**
     * Computes how many apps should be placed on the initial Home Grid so at
     * least 1–2 full rows remain open for the wallpaper to breathe.
     */
    fun computeFirstRunHomeCapacity(columns: Int, rows: Int): Int {
        val safeCols = columns.coerceIn(3, 6)
        val safeRows = rows.coerceIn(3, 8)
        val emptyRowsToLeave = if (safeRows >= 4) 2 else 1
        val filledRows = (safeRows - emptyRowsToLeave).coerceAtLeast(1)
        return (safeCols * filledRows).coerceIn(safeCols, safeCols * 3)
    }

    /**
     * Selects up to [maxCount] initial Home Grid apps excluding [dockKeys] and
     * LumaOS Pro itself, preferring recent/frequent apps from [UsageStatsManager]
     * when available and falling back to curated everyday tools + alphabetical order.
     */
    fun resolveInitialHomeApps(
        context: Context,
        apps: List<AppEntry>,
        dockKeys: Collection<ComponentKey>,
        maxCount: Int
    ): List<ComponentKey> {
        if (apps.isEmpty() || maxCount <= 0) return emptyList()
        val usageScores = queryUsageScores(context)
        return selectInitialHomeApps(
            apps = apps,
            dockKeys = dockKeys,
            selfPackage = context.packageName,
            usageMillisByPackage = usageScores,
            maxCount = maxCount
        )
    }

    /** Pure-Kotlin selector (unit-tested on JVM). */
    fun selectInitialHomeApps(
        apps: List<AppEntry>,
        dockKeys: Collection<ComponentKey>,
        selfPackage: String,
        usageMillisByPackage: Map<String, Long>,
        maxCount: Int
    ): List<ComponentKey> {
        if (apps.isEmpty() || maxCount <= 0) return emptyList()
        val excludedPackages = dockKeys.mapTo(HashSet()) { it.packageName }.apply { add(selfPackage) }
        val candidates = apps.filter { it.key.packageName !in excludedPackages }
        if (candidates.isEmpty()) return emptyList()

        val sorted = candidates.sortedWith(
            compareByDescending<AppEntry> { usageMillisByPackage[it.key.packageName] ?: 0L }
                .thenByDescending { essentialPriorityScore(it.key.packageName, it.label) }
                .thenBy { it.sortLabel }
        )

        val picked = ArrayList<ComponentKey>(maxCount)
        val seenPkg = HashSet<String>()
        for (entry in sorted) {
            if (picked.size >= maxCount) break
            if (seenPkg.add(entry.key.packageName)) {
                picked += entry.key
            }
        }
        return picked
    }

    private fun queryUsageScores(context: Context): Map<String, Long> = runCatching {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val end = System.currentTimeMillis()
        val start = end - 30L * 24L * 60L * 60L * 1000L
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, start, end)
            ?: return emptyMap()
        val out = HashMap<String, Long>(stats.size)
        for (s in stats) {
            val fg = s.totalTimeInForeground
            if (fg > 0L && !s.packageName.isNullOrBlank()) {
                out[s.packageName] = maxOf(out[s.packageName] ?: 0L, fg)
            }
        }
        out
    }.getOrDefault(emptyMap())

    private fun essentialPriorityScore(packageName: String, label: String): Int {
        val token = "${packageName.lowercase()} ${label.lowercase()}"
        return when {
            token.contains("photos") || token.contains("gallery") -> 90
            token.contains("maps") -> 85
            token.contains("gmail") || token.contains("mail") -> 80
            token.contains("calendar") -> 75
            token.contains("clock") || token.contains("deskclock") -> 70
            token.contains("music") || token.contains("spotify") || token.contains("youtube") -> 65
            token.contains("files") || token.contains("documents") -> 60
            token.contains("calculator") -> 55
            token.contains("notes") || token.contains("keep") -> 50
            token.contains("vending") || token.contains("store") -> 45
            token.contains("settings") -> 40
            else -> 0
        }
    }
}
