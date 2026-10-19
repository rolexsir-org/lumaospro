package com.rolexm.launcher.optimizer

import android.content.Context
import com.rolexm.launcher.core.apps.IconCache
import com.rolexm.launcher.core.prefs.RolexPrefs
import com.rolexm.launcher.core.util.RolexLog
import com.rolexm.launcher.diagnostics.DiagnosticsRepository
import com.rolexm.launcher.diagnostics.OptimizationEvent
import com.rolexm.launcher.diagnostics.formatBytes
import com.rolexm.launcher.intelligence.MemorySampler
import com.rolexm.launcher.intelligence.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Something ROLEXM can do to reduce its own overhead.
 *
 * [scope] is shown in the UI next to every action. It exists because the
 * single most common lie in this product category is an implied scope: an
 * action listed under "cleaner" reads as system-wide, so each one states
 * plainly that it touches ROLEXM and nothing else.
 */
data class OptimizationAction(
    val id: String,
    val title: String,
    val description: String,
    val scope: String
)

/**
 * Outcome of a run, including whether it achieved anything measurable.
 *
 * [completed] and [measuredImprovement] are deliberately separate: an action
 * can complete perfectly and still show no improvement, and reporting that
 * honestly is the whole point of the diagnostics screen.
 */
data class OptimizationResult(
    val actionId: String,
    val actionTitle: String,
    val completed: Boolean,
    val detail: String,
    val durationMs: Long,
    val beforePssKb: Int?,
    val afterPssKb: Int?,
    val bytesFreed: Long,
    val measuredImprovement: Boolean
)

/**
 * Executes the maintenance actions ROLEXM supports.
 *
 * Boundary, stated once and enforced by every action below: ROLEXM only ever
 * touches its own process, caches and databases. It does not stop other apps,
 * clear other apps' caches, or change system settings. Those operations are
 * either forbidden to third-party apps on modern Android or require
 * permissions ROLEXM will not ask for, and a launcher that claims otherwise is
 * selling a number rather than doing work.
 */
class Optimizer(
    private val context: Context,
    private val prefs: RolexPrefs,
    private val iconCache: IconCache,
    private val diagnostics: DiagnosticsRepository
) {

    private val memorySampler = MemorySampler(context)

    companion object {
        const val ACTION_TRIM_ICON_CACHE = "trim_icon_cache"
        const val ACTION_CLEAR_INTERNAL_CACHE = "clear_internal_cache"
        const val ACTION_COMPACT_DIAGNOSTICS = "compact_diagnostics"
        const val ACTION_APPLY_PROFILE = "apply_profile"

        /** Internal cache size above which trimming is worthwhile. */
        const val INTERNAL_CACHE_TRIM_THRESHOLD_BYTES = 8L * 1024 * 1024

        /** Icons always retained when trimming, so the visible grid stays warm. */
        const val ICON_CACHE_KEEP = 16
    }

    fun actions(): List<OptimizationAction> = listOf(
        OptimizationAction(
            id = ACTION_TRIM_ICON_CACHE,
            title = "Trim icon cache",
            description = "Releases decoded app icons that are not currently on screen. " +
                "Icons reload the next time they are shown.",
            scope = "ROLEXM's own memory"
        ),
        OptimizationAction(
            id = ACTION_CLEAR_INTERNAL_CACHE,
            title = "Clear ROLEXM cache",
            description = "Deletes temporary files ROLEXM wrote to its own cache directory. " +
                "No other app's cache is touched.",
            scope = "ROLEXM's own files"
        ),
        OptimizationAction(
            id = ACTION_COMPACT_DIAGNOSTICS,
            title = "Compact diagnostics database",
            description = "Rebuilds ROLEXM's local measurements database to reclaim free space.",
            scope = "ROLEXM's own database"
        ),
        OptimizationAction(
            id = ACTION_APPLY_PROFILE,
            title = "Re-apply performance profile",
            description = "Re-sizes caches and re-applies animation and sampling settings for the " +
                "active profile.",
            scope = "ROLEXM's own settings"
        )
    )

    /**
     * Runs one action, measuring before and after.
     *
     * The measurement is taken with PSS, and a result is only flagged as an
     * improvement when the after-reading is genuinely lower. Android does not
     * return freed pages instantly, so a correct-but-unhelpful run is reported
     * as "no measurable change" rather than as a win.
     */
    suspend fun run(
        actionId: String,
        trigger: String,
        profile: Profile
    ): OptimizationResult = withContext(Dispatchers.IO) {
        val action = actions().firstOrNull { it.id == actionId }
        if (action == null) {
            return@withContext OptimizationResult(
                actionId = actionId,
                actionTitle = actionId,
                completed = false,
                detail = "Unknown action.",
                durationMs = 0L,
                beforePssKb = null,
                afterPssKb = null,
                bytesFreed = 0L,
                measuredImprovement = false
            )
        }

        val startedAt = System.currentTimeMillis()
        val before = memorySampler.sample().valueOrNull()

        // Each action returns its own outcome text and bytes freed, so nothing
        // is shared between concurrent runs.
        val (detail, bytesFreed) = try {
            when (actionId) {
                ACTION_TRIM_ICON_CACHE -> trimIconCache()
                ACTION_CLEAR_INTERNAL_CACHE -> clearInternalCache()
                ACTION_COMPACT_DIAGNOSTICS -> compactDiagnostics()
                ACTION_APPLY_PROFILE -> applyProfile(profile)
                else -> "Nothing to do." to 0L
            }
        } catch (e: Exception) {
            RolexLog.e("Optimization $actionId failed", e)
            "Failed: ${e.message ?: "unknown error"}" to 0L
        }

        // PSS right after a free is frequently unchanged because the pages are
        // not yet reclaimed; that is reported as "no measurable change" rather
        // than spun as a win.
        val after = memorySampler.sample().valueOrNull()
        val duration = System.currentTimeMillis() - startedAt

        val beforeKb = before?.totalPssKb
        val afterKb = after?.totalPssKb
        val improved = beforeKb != null && afterKb != null && afterKb < beforeKb

        val optimizationResult = OptimizationResult(
            actionId = actionId,
            actionTitle = action.title,
            completed = !detail.startsWith("Failed"),
            detail = detail,
            durationMs = duration,
            beforePssKb = beforeKb,
            afterPssKb = afterKb,
            bytesFreed = bytesFreed,
            measuredImprovement = improved
        )

        diagnostics.record(
            OptimizationEvent(
                timestampMillis = startedAt,
                actionId = optimizationResult.actionId,
                actionTitle = optimizationResult.actionTitle,
                trigger = trigger,
                completed = optimizationResult.completed,
                detail = optimizationResult.detail,
                durationMs = optimizationResult.durationMs,
                beforePssKb = optimizationResult.beforePssKb,
                afterPssKb = optimizationResult.afterPssKb,
                bytesFreed = optimizationResult.bytesFreed,
                measuredImprovement = optimizationResult.measuredImprovement
            )
        )
        prefs.markActionRan(actionId, startedAt)
        optimizationResult
    }

    private fun trimIconCache(): Pair<String, Long> {
        val before = iconCache.size
        if (before == 0) return "Icon cache was already empty." to 0L
        iconCache.trim(fraction = 0.5f, keep = ICON_CACHE_KEEP)
        val after = iconCache.size
        return if (after < before) {
            "Released ${before - after} cached icons (~${(before - after) * 40 / 1024} MB of decoded bitmaps)."
        } else {
            "No icons were released; only ${before.coerceAtMost(ICON_CACHE_KEEP)} are retained on screen."
        } to 0L
    }

    private fun clearInternalCache(): Pair<String, Long> {
        val dir = context.cacheDir ?: return "No cache directory available." to 0L
        val before = dir.sizeRecursive()
        if (before == 0L) return "ROLEXM's cache was already empty." to 0L
        val freed = deleteContents(dir)
        return "Deleted ${formatBytes(freed)} from ROLEXM's own cache directory." to freed
    }

    private fun compactDiagnostics(): Pair<String, Long> {
        val before = runCatching { diagnostics.databaseSizeBytesBlocking() }.getOrDefault(0L)
        diagnostics.vacuumBlocking()
        val after = runCatching { diagnostics.databaseSizeBytesBlocking() }.getOrDefault(0L)
        return if (before > after) {
            "Compacted the diagnostics database (${formatBytes(before)} -> ${formatBytes(after)})."
        } else {
            "Diagnostics database compacted (${formatBytes(after)}); no space was reclaimed."
        } to 0L
    }

    private fun applyProfile(profile: Profile): Pair<String, Long> {
        iconCache.resize(profile.maxCachedIcons)
        return "Applied the ${profile.id.name.lowercase().replace('_', ' ')} profile: " +
            "icon cache ceiling ${profile.maxCachedIcons} entries, " +
            "animations ${if (profile.transitionsEnabled) "on" else "off"}, " +
            "frame instrumentation ${if (profile.frameMonitorEnabled) "on" else "off"}." to 0L
    }

    /**
     * Restores every ROLEXM-applied optimization to its default state.
     *
     * Explicitly does not touch the home layout, dock, folders or appearance
     * settings: a reset that rearranged the user's home screen would be a
     * worse outcome than the problem it solves.
     */
    suspend fun emergencyReset(): OptimizationResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        iconCache.clear()
        deleteContents(context.cacheDir)
        diagnostics.clearHistory()
        prefs.clearActionHistory()
        prefs.setProfileOverride(null)
        val duration = System.currentTimeMillis() - startedAt
        OptimizationResult(
            actionId = "emergency_reset",
            actionTitle = "Reset ROLEXM optimizations",
            completed = true,
            detail = "Restored the default profile and cleared ROLEXM's caches and " +
                "optimization history. Your home layout, dock and settings were kept.",
            durationMs = duration,
            beforePssKb = null,
            afterPssKb = null,
            bytesFreed = 0L,
            measuredImprovement = false
        ).also { diagnostics.record(it.toEvent(trigger = "manual", atMillis = startedAt)) }
    }

    private fun OptimizationResult.toEvent(trigger: String, atMillis: Long) = OptimizationEvent(
        timestampMillis = atMillis,
        actionId = actionId,
        actionTitle = actionTitle,
        trigger = trigger,
        completed = completed,
        detail = detail,
        durationMs = durationMs,
        beforePssKb = beforePssKb,
        afterPssKb = afterPssKb,
        bytesFreed = bytesFreed,
        measuredImprovement = measuredImprovement
    )

    /** Size of ROLEXM's internal cache, in bytes. Read off the main thread. */
    suspend fun internalCacheBytes(): Long = withContext(Dispatchers.IO) {
        context.cacheDir?.sizeRecursive() ?: 0L
    }

    private fun File.sizeRecursive(): Long {
        if (!exists()) return 0L
        if (isFile) return length()
        var total = 0L
        listFiles()?.forEach { total += it.sizeRecursive() }
        return total
    }

    /**
     * Deletes the contents of [dir] but keeps the directory itself, because
     * some paths (cacheDir) are expected to exist.
     */
    private fun deleteContents(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var freed = 0L
        dir.listFiles()?.forEach { child ->
            freed += when {
                child.isDirectory -> {
                    val size = child.sizeRecursive()
                    if (child.deleteRecursively()) size else 0L
                }
                else -> {
                    val size = child.length()
                    if (child.delete()) size else 0L
                }
            }
        }
        return freed
    }
}
