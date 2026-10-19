package com.rolexm.launcher.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rolexm.launcher.core.util.RolexLog
import com.rolexm.launcher.intelligence.ProfileId
import com.rolexm.launcher.launcher.model.GestureAction
import com.rolexm.launcher.ui.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.rolexmDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "rolexm_settings"
)

/**
 * All ROLEXM settings, persisted locally with DataStore Preferences.
 *
 * DataStore is used rather than SharedPreferences because every read is a Flow:
 * the home screen and settings screens observe changes and stay consistent
 * without manual listeners or stale caches.
 *
 * Every read is wrapped with [catch] so that a corrupted preferences file
 * degrades to defaults instead of crashing the launcher at startup - a
 * launcher that will not start is an unrecoverable failure for the user.
 */
class RolexPrefs(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val GRID_ROWS = intPreferencesKey("grid_rows")
        val DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
        val ICON_SIZE_DP = intPreferencesKey("icon_size_dp")
        val SHOW_LABELS = booleanPreferencesKey("show_labels")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val PROFILE_OVERRIDE = stringPreferencesKey("profile_override")
        val AUTO_OPTIMIZE = booleanPreferencesKey("auto_optimize")
        val HOME_LAYOUT = stringPreferencesKey("home_layout")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val WIDGETS_ENABLED = booleanPreferencesKey("widgets_enabled")
        val LAYOUT_SEEDED = booleanPreferencesKey("layout_seeded")
        val LAST_ACTION_AT = stringPreferencesKey("last_action_at")
    }

    // --- Appearance -------------------------------------------------------

    val themeMode: Flow<ThemeMode> = read(Keys.THEME_MODE) { ThemeMode.SYSTEM.storageKey }
        .map { ThemeMode.fromStorageKey(it) }

    suspend fun setThemeMode(mode: ThemeMode) = write(Keys.THEME_MODE, mode.storageKey)

    val gridColumns: Flow<Int> = read(Keys.GRID_COLUMNS) { DEFAULT_GRID_COLUMNS }
    suspend fun setGridColumns(value: Int) = write(Keys.GRID_COLUMNS, value.coerceIn(3, 7))

    val gridRows: Flow<Int> = read(Keys.GRID_ROWS) { DEFAULT_GRID_ROWS }
    suspend fun setGridRows(value: Int) = write(Keys.GRID_ROWS, value.coerceIn(3, 8))

    val drawerColumns: Flow<Int> = read(Keys.DRAWER_COLUMNS) { DEFAULT_DRAWER_COLUMNS }
    suspend fun setDrawerColumns(value: Int) = write(Keys.DRAWER_COLUMNS, value.coerceIn(3, 7))

    val iconSizeDp: Flow<Int> = read(Keys.ICON_SIZE_DP) { DEFAULT_ICON_SIZE_DP }
    suspend fun setIconSizeDp(value: Int) = write(Keys.ICON_SIZE_DP, value.coerceIn(36, 72))

    val showLabels: Flow<Boolean> = read(Keys.SHOW_LABELS) { true }
    suspend fun setShowLabels(value: Boolean) = write(Keys.SHOW_LABELS, value)

    /**
     * Accessibility text scale, 1.0 = normal. Applied to launcher labels only;
     * it cannot change system font size (that is a Settings secure value).
     */
    val textScale: Flow<Float> = read(Keys.TEXT_SCALE) { 1.0f }
    suspend fun setTextScale(value: Float) = write(Keys.TEXT_SCALE, value.coerceIn(0.85f, 1.6f))

    // --- Performance ------------------------------------------------------

    /** Null means "choose automatically from device capability". */
    val profileOverride: Flow<ProfileId?> = readNullable(Keys.PROFILE_OVERRIDE)
        .map { raw -> raw?.let { runCatching { ProfileId.valueOf(it) }.getOrNull() } }

    suspend fun setProfileOverride(id: ProfileId?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.PROFILE_OVERRIDE)
            else prefs[Keys.PROFILE_OVERRIDE] = id.name
        }
    }

    val autoOptimize: Flow<Boolean> = read(Keys.AUTO_OPTIMIZE) { true }
    suspend fun setAutoOptimize(value: Boolean) = write(Keys.AUTO_OPTIMIZE, value)

    // --- Layout & gestures ------------------------------------------------

    val homeLayoutRaw: Flow<String?> = readNullable(Keys.HOME_LAYOUT)
    suspend fun setHomeLayoutRaw(value: String) = write(Keys.HOME_LAYOUT, value)

    val widgetsEnabled: Flow<Boolean> = read(Keys.WIDGETS_ENABLED) { false }
    suspend fun setWidgetsEnabled(value: Boolean) = write(Keys.WIDGETS_ENABLED, value)

    val onboardingDone: Flow<Boolean> = read(Keys.ONBOARDING_DONE) { false }
    suspend fun setOnboardingDone(value: Boolean) = write(Keys.ONBOARDING_DONE, value)

    /** True once the first-run dock has been populated. */
    val layoutSeeded: Flow<Boolean> = read(Keys.LAYOUT_SEEDED) { false }
    suspend fun setLayoutSeeded(value: Boolean) = write(Keys.LAYOUT_SEEDED, value)

    fun gesture(kind: GestureKind): Flow<GestureAction> =
        read(kind.key) { kind.default.storageKey }.map { GestureAction.parse(it) }

    suspend fun setGesture(kind: GestureKind, action: GestureAction) =
        write(kind.key, action.storageKey)

    // --- Optimizer bookkeeping -------------------------------------------

    /**
     * Per-action last-run timestamps, stored as one "id=millis" map in a single
     * preference so that adding an action never needs a migration.
     */
    val lastActionAt: Flow<Map<String, Long>> = read(Keys.LAST_ACTION_AT) { "" }
        .map { raw -> parseTimestampMap(raw) }

    suspend fun markActionRan(actionId: String, atMillis: Long) {
        dataStore.edit { prefs ->
            val current = parseTimestampMap(prefs[Keys.LAST_ACTION_AT]).toMutableMap()
            current[actionId] = atMillis
            prefs[Keys.LAST_ACTION_AT] = current.entries.joinToString(",") { "${it.key}=${it.value}" }
        }
    }

    /** Clears optimizer bookkeeping only. Never touches layout or settings. */
    suspend fun clearActionHistory() {
        dataStore.edit { prefs -> prefs.remove(Keys.LAST_ACTION_AT) }
    }

    // --- Internals --------------------------------------------------------

    private fun <T> read(key: Preferences.Key<T>, default: () -> T): Flow<T> =
        dataStore.data
            .catch { e ->
                if (e is IOException) {
                    RolexLog.e("Preferences read failed, falling back to defaults", e)
                    emit(emptyPreferences())
                } else throw e
            }
            .map { prefs -> prefs[key] ?: default() }

    private suspend fun <T> write(key: Preferences.Key<T>, value: T) {
        dataStore.edit { prefs -> prefs[key] = value }
    }

    /**
     * Variant of [read] for preferences that are legitimately absent.
     *
     * A separate function rather than a nullable default on [read]: with
     * generics, "no value" and "value of null" collapse into the same call, and
     * the distinction is worth keeping explicit at the call site.
     */
    private fun <T> readNullable(key: Preferences.Key<T>): Flow<T?> =
        dataStore.data
            .catch { e ->
                if (e is IOException) {
                    RolexLog.e("Preferences read failed, falling back to defaults", e)
                    emit(emptyPreferences())
                } else throw e
            }
            .map { prefs -> prefs[key] }

    internal fun parseTimestampMap(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(",").mapNotNull { entry ->
            val eq = entry.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val millis = entry.substring(eq + 1).toLongOrNull() ?: return@mapNotNull null
            entry.substring(0, eq) to millis
        }.toMap()
    }

    /** One-shot read used during startup where a Flow is inconvenient. */
    suspend fun isOnboardingDoneOnce(): Boolean = onboardingDone.first()

    companion object {
        const val DEFAULT_GRID_COLUMNS = 4
        const val DEFAULT_GRID_ROWS = 5
        const val DEFAULT_DRAWER_COLUMNS = 4
        const val DEFAULT_ICON_SIZE_DP = 48

        fun create(context: Context): RolexPrefs = RolexPrefs(context.rolexmDataStore)
    }
}

/** The gestures ROLEXM can bind. Defaults match conventional launcher behaviour. */
enum class GestureKind(
    val key: Preferences.Key<String>,
    val default: GestureAction
) {
    SWIPE_UP(
        androidx.datastore.preferences.core.stringPreferencesKey("gesture_swipe_up"),
        GestureAction.OpenDrawer
    ),
    SWIPE_DOWN(
        androidx.datastore.preferences.core.stringPreferencesKey("gesture_swipe_down"),
        GestureAction.OpenSearch
    ),
    DOUBLE_TAP(
        androidx.datastore.preferences.core.stringPreferencesKey("gesture_double_tap"),
        GestureAction.None
    ),
    LONG_PRESS(
        androidx.datastore.preferences.core.stringPreferencesKey("gesture_long_press"),
        GestureAction.OpenSettings
    ),
}
