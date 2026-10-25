package com.lumaos.pro.launcher.diagnostics

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lumaos.pro.launcher.core.util.LumaLog

/**
 * Why one optimization action ran, what it achieved, and when.
 *
 * Stored so the dashboard can show real history instead of a claim, and so the
 * user can see that a run did nothing measurable rather than being told it
 * "freed 200 MB".
 */
data class OptimizationEvent(
    val id: Long = 0L,
    val timestampMillis: Long,
    val actionId: String,
    val actionTitle: String,
    /** What caused the run: "manual", or the id of the rule that fired. */
    val trigger: String,
    val completed: Boolean,
    val detail: String,
    val durationMs: Long,
    /** PSS before the action, in KB; null when it could not be read. */
    val beforePssKb: Int?,
    val afterPssKb: Int?,
    val bytesFreed: Long,
    /** True only when a post-action measurement was actually lower. */
    val measuredImprovement: Boolean
) {
    val pssDeltaKb: Int? get() {
        val before = beforePssKb ?: return null
        val after = afterPssKb ?: return null
        return after - before
    }
}

/**
 * Local SQLite store for diagnostics.
 *
 * SQLite is used directly rather than Room because the workload is two tables
 * with fixed, trivial queries; Room would add an annotation processor (KSP) to
 * the build and a runtime layer to the APK for no benefit here. Everything is
 * on-device - nothing in this table leaves the phone.
 */
class DiagnosticsStore(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, SCHEMA_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                action_id TEXT NOT NULL,
                action_title TEXT NOT NULL,
                trigger TEXT NOT NULL,
                completed INTEGER NOT NULL,
                detail TEXT NOT NULL,
                duration_ms INTEGER NOT NULL,
                before_pss_kb INTEGER,
                after_pss_kb INTEGER,
                bytes_freed INTEGER NOT NULL,
                improved INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_ts ON events(ts DESC)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS startup_samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_startup_ts ON startup_samples(ts DESC)")
    }

    /**
     * Diagnostics are disposable measurements, not user data. A schema bump
     * therefore recreates the tables rather than carrying a migration for
     * numbers that can simply be re-measured.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS events")
        db.execSQL("DROP TABLE IF EXISTS startup_samples")
        onCreate(db)
    }

    fun insertEvent(event: OptimizationEvent): Long {
        val values = ContentValues().apply {
            put("ts", event.timestampMillis)
            put("action_id", event.actionId)
            put("action_title", event.actionTitle)
            put("trigger", event.trigger)
            put("completed", if (event.completed) 1 else 0)
            put("detail", event.detail)
            put("duration_ms", event.durationMs)
            put("before_pss_kb", event.beforePssKb)
            put("after_pss_kb", event.afterPssKb)
            put("bytes_freed", event.bytesFreed)
            put("improved", if (event.measuredImprovement) 1 else 0)
        }
        return writableDatabase.use { db ->
            val id = db.insert("events", null, values)
            // Enforce the row cap on write so the database cannot grow without
            // bound on a device that is already short of storage.
            db.execSQL(
                "DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY ts DESC LIMIT ?)",
                arrayOf(MAX_EVENTS.toString())
            )
            id
        }
    }

    fun recentEvents(limit: Int): List<OptimizationEvent> =
        readableDatabase.use { db ->
            db.query(
                "events", null, null, null, null, null,
                "ts DESC", limit.coerceIn(1, MAX_EVENTS).toString()
            ).use { cursor ->
                val out = ArrayList<OptimizationEvent>(cursor.count)
                val idIdx = cursor.getColumnIndexOrThrow("id")
                val tsIdx = cursor.getColumnIndexOrThrow("ts")
                val actionIdIdx = cursor.getColumnIndexOrThrow("action_id")
                val titleIdx = cursor.getColumnIndexOrThrow("action_title")
                val triggerIdx = cursor.getColumnIndexOrThrow("trigger")
                val completedIdx = cursor.getColumnIndexOrThrow("completed")
                val detailIdx = cursor.getColumnIndexOrThrow("detail")
                val durationIdx = cursor.getColumnIndexOrThrow("duration_ms")
                val beforeIdx = cursor.getColumnIndexOrThrow("before_pss_kb")
                val afterIdx = cursor.getColumnIndexOrThrow("after_pss_kb")
                val freedIdx = cursor.getColumnIndexOrThrow("bytes_freed")
                val improvedIdx = cursor.getColumnIndexOrThrow("improved")
                while (cursor.moveToNext()) {
                    val before = if (cursor.isNull(beforeIdx)) null else cursor.getInt(beforeIdx)
                    val after = if (cursor.isNull(afterIdx)) null else cursor.getInt(afterIdx)
                    out += OptimizationEvent(
                        id = cursor.getLong(idIdx),
                        timestampMillis = cursor.getLong(tsIdx),
                        actionId = cursor.getString(actionIdIdx),
                        actionTitle = cursor.getString(titleIdx),
                        trigger = cursor.getString(triggerIdx),
                        completed = cursor.getInt(completedIdx) == 1,
                        detail = cursor.getString(detailIdx),
                        durationMs = cursor.getLong(durationIdx),
                        beforePssKb = before,
                        afterPssKb = after,
                        bytesFreed = cursor.getLong(freedIdx),
                        measuredImprovement = cursor.getInt(improvedIdx) == 1
                    )
                }
                out
            }
        }

    fun recordStartup(durationMs: Long, atMillis: Long = System.currentTimeMillis()) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put("ts", atMillis)
                put("duration_ms", durationMs)
            }
            db.insert("startup_samples", null, values)
            db.execSQL(
                "DELETE FROM startup_samples WHERE id NOT IN " +
                    "(SELECT id FROM startup_samples ORDER BY ts DESC LIMIT ?)",
                arrayOf(MAX_STARTUP_SAMPLES.toString())
            )
        }
    }

    /**
     * Recent startup durations, oldest first, so the dashboard can show a
     * trend rather than a single number.
     */
    fun startupHistory(limit: Int): List<Pair<Long, Long>> =
        readableDatabase.use { db ->
            db.rawQuery(
                "SELECT ts, duration_ms FROM startup_samples ORDER BY ts DESC LIMIT ?",
                arrayOf(limit.coerceIn(1, MAX_STARTUP_SAMPLES).toString())
            ).use { cursor ->
                val out = ArrayList<Pair<Long, Long>>(cursor.count)
                while (cursor.moveToNext()) {
                    out += cursor.getLong(0) to cursor.getLong(1)
                }
                out.reversed()
            }
        }

    /** Drops everything. Used by "reset optimizations" only. */
    fun clearHistory() {
        writableDatabase.use { db ->
            db.delete("events", null, null)
            db.delete("startup_samples", null, null)
        }
    }

    /**
     * Reclaims free pages so the diagnostics database does not itself become a
     * source of bloat. Safe to run at any time; SQLite locks the file.
     */
    fun vacuum() {
        try {
            writableDatabase.use { db -> db.execSQL("VACUUM") }
        } catch (e: RuntimeException) {
            LumaLog.w("VACUUM failed: ${e.message ?: "unknown"}")
        }
    }

    /** Approximate on-disk size of the diagnostics database, in bytes. */
    fun databaseSizeBytes(): Long =
        runCatching { readableDatabase.path?.let { java.io.File(it).length() } ?: 0L }
            .getOrDefault(0L)

    companion object {
        private const val DATABASE_NAME = "lumaos_diagnostics.db"
        private const val SCHEMA_VERSION = 1

        /** Row caps. Bounded on purpose - this is a low-storage device target. */
        const val MAX_EVENTS = 200
        const val MAX_STARTUP_SAMPLES = 30
    }
}
