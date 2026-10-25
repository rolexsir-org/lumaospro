package com.lumaos.pro.launcher.diagnostics

import com.lumaos.pro.launcher.intelligence.ProcessMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coroutine facade over [DiagnosticsStore].
 *
 * All SQLite work happens on Dispatchers.IO. The store is a single connection
 * and SQLite serialises access, so no additional locking is needed.
 */
class DiagnosticsRepository(val store: DiagnosticsStore) {

    suspend fun record(event: OptimizationEvent): Long = withContext(Dispatchers.IO) {
        store.insertEvent(event)
    }

    suspend fun recentEvents(limit: Int = 50): List<OptimizationEvent> =
        withContext(Dispatchers.IO) { store.recentEvents(limit) }

    suspend fun recordStartup(durationMs: Long, atMillis: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) { store.recordStartup(durationMs, atMillis) }

    suspend fun startupHistory(limit: Int = 20): List<Pair<Long, Long>> =
        withContext(Dispatchers.IO) { store.startupHistory(limit) }

    suspend fun clearHistory() = withContext(Dispatchers.IO) { store.clearHistory() }

    suspend fun vacuum() = withContext(Dispatchers.IO) { store.vacuum() }

    suspend fun databaseSizeBytes(): Long = withContext(Dispatchers.IO) { store.databaseSizeBytes() }

    // --- Blocking variants ------------------------------------------------
    // For callers already dispatched onto Dispatchers.IO (the Optimizer runs
    // its whole action on IO). Calling the suspend versions from there would
    // mean a pointless second dispatch per database operation.

    fun vacuumBlocking() = store.vacuum()

    fun databaseSizeBytesBlocking(): Long = store.databaseSizeBytes()
}

/**
 * Aggregated startup statistics.
 *
 * Pure so the maths is unit tested; the repository only supplies samples.
 */
data class StartupStats(
    val sampleCount: Int,
    val latestMs: Long,
    val bestMs: Long,
    val medianMs: Long
) {
    companion object {
        fun from(samples: List<Pair<Long, Long>>): StartupStats? {
            if (samples.isEmpty()) return null
            val durations = samples.map { it.second }.sorted()
            val median = if (durations.size % 2 == 1) {
                durations[durations.size / 2]
            } else {
                val hi = durations[durations.size / 2]
                val lo = durations[durations.size / 2 - 1]
                (hi + lo) / 2
            }
            return StartupStats(
                sampleCount = durations.size,
                latestMs = samples.last().second,
                bestMs = durations.first(),
                medianMs = median
            )
        }
    }
}

/** Formats a memory figure for display, switching units at 1 MB. */
fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "-"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

/** Formats a PSS reading given in KB. */
fun formatPss(kb: Int?): String = if (kb == null) "—" else "%.1f MB".format(kb / 1024.0)

/** Human one-liner for a process-memory reading. */
fun describeMemory(memory: ProcessMemory?): String = when {
    memory == null -> "—"
    else -> "%.1f MB (Dalvik %.1f MB, native %.1f MB)".format(
        memory.totalPssMb, memory.dalvikPssKb / 1024.0, memory.nativePssKb / 1024.0
    )
}
