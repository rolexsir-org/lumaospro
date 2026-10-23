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
import com.rolexm.launcher.context.ContextProfile
import com.rolexm.launcher.context.ProfileSchedule
import com.rolexm.launcher.core.model.ComponentKey
import com.rolexm.launcher.intelligence.ProfileId
import com.rolexm.launcher.launcher.model.GestureAction
import com.rolexm.launcher.ui.ThemeMode
import com.rolexm.launcher.ui.theme.Backdrop
import com.rolexm.launcher.ui.theme.DarkWindow
import com.rolexm.launcher.ui.theme.IconShape
import com.rolexm.launcher.ui.theme.IconStyle
import com.rolexm.launcher.ui.theme.ThemeId
import com.rolexm.launcher.ui.theme.ThemeSettings
import com.rolexm.launcher.ui.theme.ThemeSwitching
import com.rolexm.launcher.ui.theme.TypeStyle
import com.rolexm.launcher.core.apps.DrawerSort
import com.rolexm.launcher.effects.EffectsPreference
import com.rolexm.launcher.launcher.model.DockStyle
import com.rolexm.launcher.launcher.model.HomePreset
import com.rolexm.launcher.motion.MotionPreference
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
        val ACTIVE_CONTEXT_PROFILE = stringPreferencesKey("active_context_profile")
        val CONTEXT_NOTIFICATIONS_ENABLED = booleanPreferencesKey("context_notifications_enabled")
        val CONTEXT_NOTIFICATION_PROFILES = stringPreferencesKey("context_notification_profiles")
        val CONTEXT_DND_PROFILES = stringPreferencesKey("context_dnd_profiles")
        val DND_MANAGED = booleanPreferencesKey("context_dnd_managed")
        val DND_RESTORE_FILTER = intPreferencesKey("context_dnd_restore_filter")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val WIDGETS_ENABLED = booleanPreferencesKey("widgets_enabled")
        val LAYOUT_SEEDED = booleanPreferencesKey("layout_seeded")
        val LAST_ACTION_AT = stringPreferencesKey("last_action_at")

        // Theme engine (0.2)
        val THEME_ID = stringPreferencesKey("theme_id")
        val THEME_SWITCHING = stringPreferencesKey("theme_switching")
        val THEME_LIGHT_ID = stringPreferencesKey("theme_light_id")
        val THEME_DARK_ID = stringPreferencesKey("theme_dark_id")
        val THEME_DARK_WINDOW = stringPreferencesKey("theme_dark_window")
        val ACCENT_OVERRIDE = intPreferencesKey("accent_override")
        val BACKGROUND_OVERRIDE = intPreferencesKey("background_override")
        val TYPOGRAPHY = stringPreferencesKey("typography")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val ICON_STYLE = stringPreferencesKey("icon_style")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val BACKDROP = stringPreferencesKey("backdrop")
        val EFFECTS_PREF = stringPreferencesKey("effects_pref")
        val MOTION_PREF = stringPreferencesKey("motion_pref")
        val HOME_PRESET = stringPreferencesKey("home_preset")
        val DOCK_STYLE = stringPreferencesKey("dock_style")
        val DRAWER_SORT = stringPreferencesKey("drawer_sort")
        val DRAWER_CATEGORIES = booleanPreferencesKey("drawer_categories")
        val SUGGESTIONS_ENABLED = booleanPreferencesKey("suggestions_enabled")
        val HIDDEN_SEARCH = booleanPreferencesKey("hidden_search")
        val HIDDEN_LOCK = booleanPreferencesKey("hidden_lock")
    }

    // --- Appearance -------------------------------------------------------

    val themeMode: Flow<ThemeMode> = read(Keys.THEME_MODE) { ThemeMode.LIGHT.storageKey }
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

    // --- Theme engine -------------------------------------------------------

    /**
     * The full appearance configuration, read in one pass over the snapshot.
     *
     * Users of 0.1 had only a light/dark/system switch; when no 0.2 theme has
     * been chosen yet that choice is mapped onto the new presets (light ->
     * Porcelain, dark -> Graphite, system -> the pair following the system), so
     * nobody's home screen changes character on upgrade.
     */
    val themeSettings: Flow<ThemeSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                RolexLog.e("Preferences read failed, falling back to defaults", e)
                emit(emptyPreferences())
            } else throw e
        }
        .map { p -> themeSettingsFrom(p) }

    private fun themeSettingsFrom(p: Preferences): ThemeSettings {
        val legacy = p[Keys.THEME_MODE]?.let { ThemeMode.fromStorageKey(it) }
        val explicitId = ThemeId.fromStorageKey(p[Keys.THEME_ID])
        val switching = ThemeSwitching.fromStorageKey(p[Keys.THEME_SWITCHING]) ?: when {
            explicitId != null -> ThemeSwitching.FIXED
            legacy == ThemeMode.LIGHT || legacy == ThemeMode.DARK -> ThemeSwitching.FIXED
            else -> ThemeSwitching.SYSTEM
        }
        val themeId = explicitId ?: when (legacy) {
            ThemeMode.LIGHT -> ThemeId.PORCELAIN
            else -> ThemeId.GRAPHITE
        }
        val profileThemes = ContextProfile.entries.mapNotNull { profile ->
            ThemeId.fromStorageKey(p[profileThemeKey(profile)])?.let { profile to it }
        }.toMap()
        return ThemeSettings(
            themeId = themeId,
            switching = switching,
            lightThemeId = ThemeId.fromStorageKey(p[Keys.THEME_LIGHT_ID]) ?: ThemeId.PORCELAIN,
            darkThemeId = ThemeId.fromStorageKey(p[Keys.THEME_DARK_ID]) ?: ThemeId.GRAPHITE,
            darkWindow = DarkWindow.decode(p[Keys.THEME_DARK_WINDOW]),
            accentOverride = p[Keys.ACCENT_OVERRIDE],
            backgroundOverride = p[Keys.BACKGROUND_OVERRIDE],
            typography = TypeStyle.fromStorageKey(p[Keys.TYPOGRAPHY]),
            iconShape = IconShape.fromStorageKey(p[Keys.ICON_SHAPE]),
            iconStyle = IconStyle.fromStorageKey(p[Keys.ICON_STYLE]),
            backdrop = Backdrop.fromStorageKey(p[Keys.BACKDROP]),
            profileThemes = profileThemes
        )
    }

    /**
     * Writes a complete [ThemeSettings] atomically (Studio "Apply"). The legacy
     * light/dark key is kept in step so 0.1-era readers stay coherent.
     */
    suspend fun applyThemeSettings(settings: ThemeSettings) {
        dataStore.edit { p ->
            p[Keys.THEME_ID] = settings.themeId.storageKey
            p[Keys.THEME_SWITCHING] = settings.switching.storageKey
            p[Keys.THEME_LIGHT_ID] = settings.lightThemeId.storageKey
            p[Keys.THEME_DARK_ID] = settings.darkThemeId.storageKey
            p[Keys.THEME_DARK_WINDOW] = settings.darkWindow.encode()
            putOrRemove(p, Keys.ACCENT_OVERRIDE, settings.accentOverride)
            putOrRemove(p, Keys.BACKGROUND_OVERRIDE, settings.backgroundOverride)
            putOrRemove(p, Keys.TYPOGRAPHY, settings.typography?.storageKey)
            putOrRemove(p, Keys.ICON_SHAPE, settings.iconShape?.storageKey)
            putOrRemove(p, Keys.ICON_STYLE, settings.iconStyle?.storageKey)
            putOrRemove(p, Keys.BACKDROP, settings.backdrop?.storageKey)
            for (profile in ContextProfile.entries) {
                putOrRemove(p, profileThemeKey(profile), settings.profileThemes[profile]?.storageKey)
            }
            p[Keys.THEME_MODE] = when (settings.switching) {
                ThemeSwitching.SYSTEM, ThemeSwitching.SCHEDULE -> ThemeMode.SYSTEM.storageKey
                ThemeSwitching.FIXED -> if (settings.themeId == ThemeId.PORCELAIN) ThemeMode.LIGHT.storageKey
                else ThemeMode.DARK.storageKey
            }
        }
    }

    suspend fun setProfileTheme(profile: ContextProfile, id: ThemeId?) {
        dataStore.edit { p -> putOrRemove(p, profileThemeKey(profile), id?.storageKey) }
    }

    /** Installed icon pack package, or null for the apps' own icons. */
    val iconPack: Flow<String?> = readNullable(Keys.ICON_PACK)
    suspend fun setIconPack(packageName: String?) {
        dataStore.edit { p -> putOrRemove(p, Keys.ICON_PACK, packageName?.takeIf { it.isNotBlank() }) }
    }

    val effectsPreference: Flow<EffectsPreference> = readNullable(Keys.EFFECTS_PREF)
        .map { EffectsPreference.fromStorageKey(it) }
    suspend fun setEffectsPreference(value: EffectsPreference) = write(Keys.EFFECTS_PREF, value.storageKey)

    val motionPreference: Flow<MotionPreference> = readNullable(Keys.MOTION_PREF)
        .map { MotionPreference.fromStorageKey(it) }
    suspend fun setMotionPreference(value: MotionPreference) = write(Keys.MOTION_PREF, value.storageKey)

    // --- Home composition --------------------------------------------------

    val homePreset: Flow<HomePreset> = readNullable(Keys.HOME_PRESET).map { HomePreset.fromStorageKey(it) }

    /** Selecting a preset also adopts its row count; rows stay adjustable afterwards. */
    suspend fun setHomePreset(preset: HomePreset) {
        dataStore.edit { p ->
            p[Keys.HOME_PRESET] = preset.storageKey
            p[Keys.GRID_ROWS] = preset.defaultRows
            p.remove(Keys.DOCK_STYLE)
        }
    }

    /** Null means "use the preset's dock style". */
    val dockStyleOverride: Flow<DockStyle?> = readNullable(Keys.DOCK_STYLE).map { DockStyle.fromStorageKey(it) }
    suspend fun setDockStyle(style: DockStyle?) {
        dataStore.edit { p -> putOrRemove(p, Keys.DOCK_STYLE, style?.storageKey) }
    }

    // --- Drawer & privacy ----------------------------------------------------

    val drawerSort: Flow<DrawerSort> = readNullable(Keys.DRAWER_SORT).map { DrawerSort.fromStorageKey(it) }
    suspend fun setDrawerSort(value: DrawerSort) = write(Keys.DRAWER_SORT, value.storageKey)

    val drawerCategoriesEnabled: Flow<Boolean> = read(Keys.DRAWER_CATEGORIES) { true }
    suspend fun setDrawerCategoriesEnabled(value: Boolean) = write(Keys.DRAWER_CATEGORIES, value)

    /**
     * Whether ROLEXM learns from launches made through it (frequent/recent
     * rows, suggestions, smart folders). History never leaves the device.
     */
    val suggestionsEnabled: Flow<Boolean> = read(Keys.SUGGESTIONS_ENABLED) { true }
    suspend fun setSuggestionsEnabled(value: Boolean) = write(Keys.SUGGESTIONS_ENABLED, value)

    /** Hidden apps are excluded from search unless the user opts in. */
    val hiddenInSearch: Flow<Boolean> = read(Keys.HIDDEN_SEARCH) { false }
    suspend fun setHiddenInSearch(value: Boolean) = write(Keys.HIDDEN_SEARCH, value)

    /** Require the device screen lock before showing or changing the hidden-apps list. */
    val hiddenLock: Flow<Boolean> = read(Keys.HIDDEN_LOCK) { false }
    suspend fun setHiddenLock(value: Boolean) = write(Keys.HIDDEN_LOCK, value)

    // --- Backup -----------------------------------------------------------

    /** Every stored value keyed by preference name (the backup codec filters it). */
    suspend fun snapshotForBackup(): Map<String, Any> =
        dataStore.data.first().asMap().mapKeys { it.key.name }

    /**
     * Restores [values] (already validated by BackupCodec): known keys absent
     * from the backup are cleared so the result matches the backup exactly,
     * while device-local state (DND bookkeeping, optimizer timestamps) is kept.
     */
    suspend fun restoreFromBackup(values: Map<String, Any>, isRestorable: (String) -> Boolean) {
        dataStore.edit { p ->
            for (key in p.asMap().keys.toList()) {
                if (isRestorable(key.name) && key.name !in values) p.remove(key)
            }
            for ((name, value) in values) {
                when (value) {
                    is String -> p[stringPreferencesKey(name)] = value
                    is Int -> p[intPreferencesKey(name)] = value
                    is Long -> p[longPreferencesKey(name)] = value
                    is Boolean -> p[booleanPreferencesKey(name)] = value
                    is Float -> p[floatPreferencesKey(name)] = value
                }
            }
        }
    }

    private fun <T> putOrRemove(p: androidx.datastore.preferences.core.MutablePreferences, key: Preferences.Key<T>, value: T?) {
        if (value == null) p.remove(key) else p[key] = value
    }

    private fun profileThemeKey(profile: ContextProfile) =
        stringPreferencesKey("profile_theme_${profile.name.lowercase()}")

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

    /** Existing layout remains the fallback for profiles not customized yet. */
    fun homeLayoutRaw(profile: ContextProfile): Flow<String?> = readNullable(profileLayoutKey(profile))
        .map { saved -> saved ?: homeLayoutRaw.first() }

    suspend fun setHomeLayoutRaw(profile: ContextProfile, value: String) =
        write(profileLayoutKey(profile), value)

    val activeContextProfile: Flow<ContextProfile> =
        read(Keys.ACTIVE_CONTEXT_PROFILE) { ContextProfile.PERSONAL.name }
            .map { ContextProfile.parse(it) ?: ContextProfile.PERSONAL }

    suspend fun setActiveContextProfile(profile: ContextProfile) =
        write(Keys.ACTIVE_CONTEXT_PROFILE, profile.name)

    /** Notifications here mean ROLEXM's opt-in context-change alerts, not control of other apps. */
    val contextNotificationsEnabled: Flow<Boolean> = read(Keys.CONTEXT_NOTIFICATIONS_ENABLED) { false }
    suspend fun setContextNotificationsEnabled(enabled: Boolean) = write(Keys.CONTEXT_NOTIFICATIONS_ENABLED, enabled)

    val contextNotificationProfiles: Flow<Set<ContextProfile>> = profileSet(Keys.CONTEXT_NOTIFICATION_PROFILES)
    suspend fun setContextNotificationEnabled(profile: ContextProfile, enabled: Boolean) =
        editProfileSet(Keys.CONTEXT_NOTIFICATION_PROFILES, profile, enabled)

    val contextDndProfiles: Flow<Set<ContextProfile>> = profileSet(Keys.CONTEXT_DND_PROFILES)
    suspend fun setContextDndEnabled(profile: ContextProfile, enabled: Boolean) =
        editProfileSet(Keys.CONTEXT_DND_PROFILES, profile, enabled)

    val contextDndManaged: Flow<Boolean> = read(Keys.DND_MANAGED) { false }
    suspend fun setContextDndManaged(value: Boolean) = write(Keys.DND_MANAGED, value)
    val contextDndRestoreFilter: Flow<Int> = read(Keys.DND_RESTORE_FILTER) { android.app.NotificationManager.INTERRUPTION_FILTER_ALL }
    suspend fun setContextDndRestoreFilter(value: Int) = write(Keys.DND_RESTORE_FILTER, value)

    private fun profileSet(key: Preferences.Key<String>): Flow<Set<ContextProfile>> =
        read(key) { "" }.map { raw -> raw.split(',').mapNotNull { ContextProfile.parse(it) }.toSet() }

    private suspend fun editProfileSet(key: Preferences.Key<String>, profile: ContextProfile, enabled: Boolean) {
        dataStore.edit { values ->
            val profiles = values[key].orEmpty().split(',').mapNotNull { ContextProfile.parse(it) }.toMutableSet()
            if (enabled) profiles.add(profile) else profiles.remove(profile)
            values[key] = profiles.map { it.name }.sorted().joinToString(",")
        }
    }

    fun profileSchedule(profile: ContextProfile): Flow<ProfileSchedule> =
        readNullable(scheduleKey(profile)).map { ProfileSchedule.decode(it, ProfileSchedule.defaults(profile)) }

    val profileSchedules: Flow<Map<ContextProfile, ProfileSchedule>> = kotlinx.coroutines.flow.combine(
        ContextProfile.entries.map { profileSchedule(it) }
    ) { schedules ->
        ContextProfile.entries.mapIndexed { index, profile -> profile to schedules[index] }.toMap()
    }

    suspend fun setProfileSchedule(profile: ContextProfile, schedule: ProfileSchedule) =
        write(scheduleKey(profile), ProfileSchedule.encode(schedule))

    fun hiddenApps(profile: ContextProfile): Flow<Set<ComponentKey>> =
        read(hiddenAppsKey(profile)) { "" }.map { raw ->
            raw.split('|').mapNotNullTo(linkedSetOf()) { ComponentKey.parse(it) }
        }

    suspend fun setAppHidden(profile: ContextProfile, key: ComponentKey, hidden: Boolean) {
        dataStore.edit { prefs ->
            val prefKey = hiddenAppsKey(profile)
            val current = prefs[prefKey].orEmpty().split('|').mapNotNullTo(linkedSetOf()) { ComponentKey.parse(it) }
            if (hidden) current.add(key) else current.remove(key)
            prefs[prefKey] = current.joinToString("|") { it.serialize() }
        }
    }

    private fun profileLayoutKey(profile: ContextProfile) = stringPreferencesKey("home_layout_${profile.name.lowercase()}")
    private fun scheduleKey(profile: ContextProfile) = stringPreferencesKey("schedule_${profile.name.lowercase()}")
    private fun hiddenAppsKey(profile: ContextProfile) = stringPreferencesKey("hidden_apps_${profile.name.lowercase()}")

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
    /**
     * Long-press on empty space opens edit mode, as on most launchers; Settings
     * remains one tap away inside edit mode. (Before 0.2 this defaulted to
     * Settings; a value the user explicitly saved is still honoured.)
     */
    LONG_PRESS(
        androidx.datastore.preferences.core.stringPreferencesKey("gesture_long_press"),
        GestureAction.EnterEditMode
    ),
    SWIPE_LEFT(
        androidx.datastore.preferences.core.stringPreferencesKey("gesture_swipe_left"),
        GestureAction.None
    ),
    SWIPE_RIGHT(
        androidx.datastore.preferences.core.stringPreferencesKey("gesture_swipe_right"),
        GestureAction.None
    ),
    /** Two-finger pinch inwards on the desktop. */
    PINCH_IN(
        androidx.datastore.preferences.core.stringPreferencesKey("gesture_pinch_in"),
        GestureAction.EnterEditMode
    ),
}
