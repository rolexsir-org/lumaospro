package com.rolexm.launcher.core.usage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.rolexm.launcher.core.model.ComponentKey
import com.rolexm.launcher.core.util.RolexLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * On-device store of launches made through ROLEXM.
 *
 * Powers the drawer's Suggested/Frequent rows, "Most used" sorting and smart
 * folders. Records are kept in memory (one small row per launched app) and
 * written to a private SQLite database off the main thread. Nothing is sent
 * anywhere; the database is excluded from cloud backup and from ROLEXM's own
 * export, and the user can clear it or switch recording off in Settings.
 */
class LaunchHistoryStore(context: Context, private val scope: CoroutineScope) {

    private val helper = Helper(context.applicationContext)

    private val _records = MutableStateFlow<Map<ComponentKey, LaunchRecord>>(emptyMap())
    val records: StateFlow<Map<ComponentKey, LaunchRecord>> = _records

    @Volatile
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        scope.launch(Dispatchers.IO) {
            val map = HashMap<ComponentKey, LaunchRecord>()
            runCatching {
                helper.readableDatabase.query(TABLE, null, null, null, null, null, null).use { c ->
                    val iKey = c.getColumnIndexOrThrow("app")
                    val iCount = c.getColumnIndexOrThrow("count")
                    val iLast = c.getColumnIndexOrThrow("last_at")
                    val iScore = c.getColumnIndexOrThrow("score")
                    val iScoreAt = c.getColumnIndexOrThrow("score_at")
                    val iHours = c.getColumnIndexOrThrow("hours")
                    while (c.moveToNext()) {
                        val key = ComponentKey.parse(c.getString(iKey)) ?: continue
                        map[key] = LaunchRecord(
                            key, c.getInt(iCount), c.getLong(iLast), c.getDouble(iScore),
                            c.getLong(iScoreAt), Frecency.decodeHours(c.getString(iHours))
                        )
                    }
                }
            }.onFailure { RolexLog.e("Launch history could not be read", it) }
            // Merge with anything recorded while loading.
            _records.value = map + _records.value
        }
    }

    /** Records one launch. Main-thread safe; persistence happens on IO. */
    fun recordLaunch(key: ComponentKey, now: Long = System.currentTimeMillis()) {
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val updated = Frecency.bump(_records.value[key], key, now, hour)
        _records.value = _records.value + (key to updated)
        scope.launch(Dispatchers.IO) {
            runCatching {
                helper.writableDatabase.insertWithOnConflict(TABLE, null, ContentValues().apply {
                    put("app", key.serialize())
                    put("count", updated.count)
                    put("last_at", updated.lastLaunchedAt)
                    put("score", updated.score)
                    put("score_at", updated.scoreAt)
                    put("hours", Frecency.encodeHours(updated.hours))
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }.onFailure { RolexLog.e("Launch history write failed", it) }
        }
    }

    /** Drops history for apps that are no longer installed. */
    fun retainOnly(installed: Set<ComponentKey>) {
        val gone = _records.value.keys.filterNot { it in installed }
        if (gone.isEmpty()) return
        _records.value = _records.value - gone.toSet()
        scope.launch(Dispatchers.IO) {
            runCatching {
                val db = helper.writableDatabase
                gone.forEach { db.delete(TABLE, "app = ?", arrayOf(it.serialize())) }
            }
        }
    }

    suspend fun clear() {
        _records.value = emptyMap()
        withContext(Dispatchers.IO) {
            runCatching { helper.writableDatabase.delete(TABLE, null, null) }
                .onFailure { RolexLog.e("Launch history clear failed", it) }
        }
    }

    val size: Int get() = _records.value.size

    private class Helper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE (app TEXT PRIMARY KEY NOT NULL, count INTEGER NOT NULL, " +
                    "last_at INTEGER NOT NULL, score REAL NOT NULL, score_at INTEGER NOT NULL, hours TEXT)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        const val DB_NAME = "rolexm_launches.db"
        private const val TABLE = "launches"
    }
}
