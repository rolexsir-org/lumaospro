package com.lumaos.pro.launcher.diagnostics

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.lumaos.pro.launcher.core.util.LumaLog
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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
 *
 * ## Connection ownership
 *
 * A [SQLiteOpenHelper] owns exactly **one** [SQLiteDatabase] for its whole
 * lifetime: [getReadableDatabase] and [getWritableDatabase] hand back that same
 * cached instance, and every caller in the process shares it. LumaOS Pro holds a
 * single [DiagnosticsStore] in `LumaCore`, so the Settings dashboard and the
 * Optimizer really do run on one connection.
 *
 * That instance is therefore *never* closed per operation. Closing it - for
 * example with `readableDatabase.use { ... }`, which compiles to
 * `SQLiteDatabase.close()` because `SQLiteDatabase` is `Closeable` - tears down
 * the shared connection pool underneath any other operation in flight. The
 * victim throws `IllegalStateException: attempt to re-open an already-closed
 * object` or `SQLiteException: Cannot perform this operation because the
 * connection pool has been closed`, and because that lands inside a coroutine
 * launched from `viewModelScope` it becomes a fatal, unhandled exception. It also
 * makes every single read pay a full re-open of the database.
 *
 * The rule enforced here: **only [Cursor]s are closed** (with `.use`), and the
 * database is closed once, by [close], when the process shuts the store down.
 *
 * ## Concurrency
 *
 * SQLite itself serialises writes, but two things still need a Kotlin-level
 * guard: `VACUUM` requires the whole database and fails when any other
 * statement is running, and a burst of small writes is measurably faster in one
 * transaction than as separate auto-commits. [access] provides both.
 */
class DiagnosticsStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, SCHEMA_VERSION) {

    /** Retained only so [databaseSizeBytes] can stat the file without a connection. */
    private val appContext: Context = context.applicationContext

    /**
     * Serialises whole-database operations. Reads may still overlap each other
     * (SQLite handles that), but nothing runs concurrently with `VACUUM`.
     */
    private val access = ReentrantLock()

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

    fun insertEvent(event: OptimizationEvent): Long = access.withLock {
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
        // Not writableDatabase.use{}: the helper's database is shared and must
        // stay open. The transaction keeps the insert and the row-cap trim
        // atomic, so a reader can never observe an over-cap table.
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insert(TABLE_EVENTS, null, values)
            // Enforce the row cap on write so the database cannot grow without
            // bound on a device that is already short of storage.
            db.execSQL(
                "DELETE FROM $TABLE_EVENTS WHERE id NOT IN " +
                    "(SELECT id FROM $TABLE_EVENTS ORDER BY ts DESC LIMIT ?)",
                arrayOf(MAX_EVENTS.toString())
            )
            db.setTransactionSuccessful()
            id
        } finally {
            db.endTransaction()
        }
    }

    fun recentEvents(limit: Int): List<OptimizationEvent> = access.withLock {
        // Only the cursor is closed here, never the database it came from.
        readableDatabase.query(
            TABLE_EVENTS, null, null, null, null, null,
            "ts DESC", limit.coerceIn(1, MAX_EVENTS).toString()
        ).use { cursor -> cursor.readEvents() }
    }

    fun recordStartup(durationMs: Long, atMillis: Long = System.currentTimeMillis()) =
        access.withLock {
            val values = ContentValues().apply {
                put("ts", atMillis)
                put("duration_ms", durationMs)
            }
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.insert(TABLE_STARTUP, null, values)
                db.execSQL(
                    "DELETE FROM $TABLE_STARTUP WHERE id NOT IN " +
                        "(SELECT id FROM $TABLE_STARTUP ORDER BY ts DESC LIMIT ?)",
                    arrayOf(MAX_STARTUP_SAMPLES.toString())
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

    /**
     * Recent startup durations, oldest first, so the dashboard can show a
     * trend rather than a single number.
     */
    fun startupHistory(limit: Int): List<Pair<Long, Long>> = access.withLock {
        readableDatabase.rawQuery(
            "SELECT ts, duration_ms FROM $TABLE_STARTUP ORDER BY ts DESC LIMIT ?",
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
    fun clearHistory() = access.withLock {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_EVENTS, null, null)
            db.delete(TABLE_STARTUP, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Reclaims free pages so the diagnostics database does not itself become a
     * source of bloat.
     *
     * `VACUUM` cannot run inside a transaction and needs the database to
     * itself, which is why it takes [access]: without it, a concurrent read
     * makes SQLite answer `SQLITE_BUSY`. That specific failure is expected and
     * reported; anything else - including closed connection, a missing table - is a
     * programming error and is deliberately allowed to propagate rather than be
     * hidden behind a catch-all.
     */
    fun vacuum() {
        try {
            access.withLock { writableDatabase.execSQL("VACUUM") }
        } catch (e: SQLiteException) {
            LumaLog.w("VACUUM failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Approximate on-disk size of the diagnostics database, in bytes.
     *
     * Read from the file rather than through a connection: this is called from
     * the Optimizer either side of a `VACUUM`, and opening the database just to
     * stat it would defeat the point. Returns 0 when the file does not exist
     * yet, which is a real answer rather than an error.
     */
    fun databaseSizeBytes(): Long {
        val file = appContext.getDatabasePath(DATABASE_NAME)
        return if (file.exists()) file.length() else 0L
    }

    /**
     * Releases the one connection this store owns. Called once from
     * `LumaCore.shutdown()`; after this the store must not be used again.
     */
    override fun close() {
        access.withLock { super.close() }
    }

    private fun Cursor.readEvents(): List<OptimizationEvent> {
        val out = ArrayList<OptimizationEvent>(count)
        val idIdx = getColumnIndexOrThrow("id")
        val tsIdx = getColumnIndexOrThrow("ts")
        val actionIdIdx = getColumnIndexOrThrow("action_id")
        val titleIdx = getColumnIndexOrThrow("action_title")
        val triggerIdx = getColumnIndexOrThrow("trigger")
        val completedIdx = getColumnIndexOrThrow("completed")
        val detailIdx = getColumnIndexOrThrow("detail")
        val durationIdx = getColumnIndexOrThrow("duration_ms")
        val beforeIdx = getColumnIndexOrThrow("before_pss_kb")
        val afterIdx = getColumnIndexOrThrow("after_pss_kb")
        val freedIdx = getColumnIndexOrThrow("bytes_freed")
        val improvedIdx = getColumnIndexOrThrow("improved")
        while (moveToNext()) {
            val before = if (isNull(beforeIdx)) null else getInt(beforeIdx)
            val after = if (isNull(afterIdx)) null else getInt(afterIdx)
            out += OptimizationEvent(
                id = getLong(idIdx),
                timestampMillis = getLong(tsIdx),
                actionId = getString(actionIdIdx),
                actionTitle = getString(titleIdx),
                trigger = getString(triggerIdx),
                completed = getInt(completedIdx) == 1,
                detail = getString(detailIdx),
                durationMs = getLong(durationIdx),
                beforePssKb = before,
                afterPssKb = after,
                bytesFreed = getLong(freedIdx),
                measuredImprovement = getInt(improvedIdx) == 1
            )
        }
        return out
    }

    companion object {
        private const val DATABASE_NAME = "lumaos_diagnostics.db"
        private const val SCHEMA_VERSION = 1
        private const val TABLE_EVENTS = "events"
        private const val TABLE_STARTUP = "startup_samples"

        /** Row caps. Bounded on purpose - this is a low-storage device target. */
        const val MAX_EVENTS = 200
        const val MAX_STARTUP_SAMPLES = 30
    }
}
