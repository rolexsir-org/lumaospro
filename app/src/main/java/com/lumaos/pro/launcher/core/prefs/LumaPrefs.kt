package com.lumaos.pro.launcher.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.context.ProfileSchedule
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.intelligence.ProfileId
import com.lumaos.pro.launcher.launcher.model.GestureAction
import com.lumaos.pro.launcher.ui.ThemeMode
import com.lumaos.pro.launcher.ui.theme.Backdrop
import com.lumaos.pro.launcher.ui.theme.DarkWindow
import com.lumaos.pro.launcher.ui.theme.ExperienceProfile
import com.lumaos.pro.launcher.ui.theme.IconShape
import com.lumaos.pro.launcher.ui.theme.IconStyle
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import com.lumaos.pro.launcher.ui.theme.ThemeSwitching
import com.lumaos.pro.launcher.ui.theme.TypeStyle
import com.lumaos.pro.launcher.core.apps.DrawerSort
import com.lumaos.pro.launcher.effects.EffectsPreference
import com.lumaos.pro.launcher.icons.LivingIconOverride
import com.lumaos.pro.launcher.icons.LivingIconOverridesCodec
import com.lumaos.pro.launcher.launcher.model.DockStyle
import com.lumaos.pro.launcher.launcher.model.DrawerPresentation
import com.lumaos.pro.launcher.launcher.model.HomePreset
import com.lumaos.pro.launcher.motion.MotionPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.lumaosDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "lumaos_settings"
)

/**
 * All LumaOS Pro settings, persisted locally with DataStore Preferences.
 *
 * DataStore is used rather than SharedPreferences because every read is a Flow:
 * the home screen and settings screens observe changes and stay consistent
 * without manual listeners or stale caches.
 *
 * Every read is wrapped with [catch] so that a corrupted preferences file
 * degrades to defaults instead of crashing the launcher at startup - a
 * launcher that will not start is an unrecoverable failure for the user.
 */
class LumaPrefs(private val dataStore: DataStore<Preferences>) {

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
        val EXPERIENCE_PROFILE = stringPreferencesKey("experience_profile")

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
        val LIVING_ICONS_ENABLED = booleanPreferencesKey("living_icons_enabled")
        val LIVING_ICON_OVERRIDES = stringPreferencesKey("living_icon_overrides")
        val BACKDROP = stringPreferencesKey("backdrop")
        val DYNAMIC_WALLPAPER_COLORS = booleanPreferencesKey("dynamic_wallpaper_colors")
        val ACTIVE_WALLPAPER_ID = stringPreferencesKey("active_wallpaper_id")
        val ACTIVE_WALLPAPER_SYSTEM_ID = intPreferencesKey("active_wallpaper_system_id")
        val CACHED_WALLPAPER_SEEDS = stringPreferencesKey("cached_wallpaper_seeds")
        val CACHED_LUMA_PALETTE = stringPreferencesKey("cached_luma_palette")
        val NOTIFICATION_PROMPT_SHOWN = booleanPreferencesKey("notification_prompt_shown")
        val WALLPAPER_FAVORITES = stringSetPreferencesKey("wallpaper_favorites")
        val EFFECTS_PREF = stringPreferencesKey("effects_pref")
        val MOTION_PREF = stringPreferencesKey("motion_pref")
        val HOME_PRESET = stringPreferencesKey("home_preset")
        val DOCK_STYLE = stringPreferencesKey("dock_style")
        val DRAWER_SORT = stringPreferencesKey("drawer_sort")
        val DRAWER_PRESENTATION = stringPreferencesKey("drawer_presentation")
        val DRAWER_CATEGORIES = booleanPreferencesKey("drawer_categories")
        val SUGGESTIONS_ENABLED = booleanPreferencesKey("suggestions_enabled")
        val HIDDEN_SEARCH = booleanPreferencesKey("hidden_search")
        val HIDDEN_LOCK = booleanPreferencesKey("hidden_lock")
    }

    // --- Appearance -------------------------------------------------------

    val themeMode: Flow<ThemeMode> = read(Keys.THEME_MODE) { ThemeMode.SYSTEM.storageKey }
        .map { ThemeMode.fromStorageKey(it) }

    /**
     * Compatibility entry point used by the original settings screen. Keep the
     * 0.2 resolver in the same transaction: writing only theme_mode would leave
     * an explicit theme_id in force and make this visible control appear dead.
     */
    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { p ->
            p[Keys.THEME_MODE] = mode.storageKey
            when (mode) {
                ThemeMode.SYSTEM -> p[Keys.THEME_SWITCHING] = ThemeSwitching.SYSTEM.storageKey
                ThemeMode.LIGHT, ThemeMode.DARK -> {
                    val pairKey = if (mode == ThemeMode.LIGHT) Keys.THEME_LIGHT_ID else Keys.THEME_DARK_ID
                    val fallback = if (mode == ThemeMode.LIGHT) ThemeId.PORCELAIN else ThemeId.SIGNATURE
                    val id = ThemeId.fromStorageKey(p[pairKey]) ?: fallback
                    p[Keys.THEME_ID] = id.storageKey
                    p[Keys.THEME_SWITCHING] = ThemeSwitching.FIXED.storageKey
                }
            }
        }
    }

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
     * Users of 0.1 had only a light/dark/system switch. Explicit legacy light
     * and dark choices keep their familiar presets; new installs follow the
     * system and use the Signature (dark) / Zen (light) pair as the dark and
     * light anchors of the single Luma Glass identity. Preset storage keys stay
     * stable, so nothing is rewritten on upgrade.
     */
    val themeSettings: Flow<ThemeSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                LumaLog.e("Preferences read failed, falling back to defaults", e)
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
            ThemeMode.DARK -> ThemeId.SIGNATURE
            else -> ThemeId.SIGNATURE
        }
        val profileThemes = ContextProfile.entries.mapNotNull { profile ->
            ThemeId.fromStorageKey(p[profileThemeKey(profile)])?.let { profile to it }
        }.toMap()
        return ThemeSettings(
            themeId = themeId,
            switching = switching,
            lightThemeId = ThemeId.fromStorageKey(p[Keys.THEME_LIGHT_ID]) ?: ThemeId.PORCELAIN,
            darkThemeId = ThemeId.fromStorageKey(p[Keys.THEME_DARK_ID]) ?: ThemeId.SIGNATURE,
            darkWindow = DarkWindow.decode(p[Keys.THEME_DARK_WINDOW]),
            accentOverride = p[Keys.ACCENT_OVERRIDE],
            backgroundOverride = p[Keys.BACKGROUND_OVERRIDE],
            typography = TypeStyle.fromStorageKey(p[Keys.TYPOGRAPHY]),
            iconShape = IconShape.fromStorageKey(p[Keys.ICON_SHAPE]),
            iconStyle = IconStyle.fromStorageKey(p[Keys.ICON_STYLE]),
            // Missing legacy values keep the existing wallpaper default. An explicit
            // "theme default" needs its own stored value so it survives a reload.
            backdrop = when (val raw = p[Keys.BACKDROP]) {
                BACKDROP_THEME_DEFAULT -> null
                else -> Backdrop.fromStorageKey(raw) ?: Backdrop.WALLPAPER
            },
            profileThemes = profileThemes,
            dynamicWallpaperColors = p[Keys.DYNAMIC_WALLPAPER_COLORS] ?: false
        )
    }

    /**
     * Writes a complete [ThemeSettings] atomically (Studio "Apply"). The legacy
     * light/dark key is kept in step so 0.1-era readers stay coherent.
     */
    suspend fun applyThemeSettings(settings: ThemeSettings) {
        dataStore.edit { p -> putThemeSettings(p, settings) }
    }

    /** Wallpaper Studio changes only the explicitly selected appearance field. */
    suspend fun showSystemWallpaperOnHome() = write(Keys.BACKDROP, Backdrop.WALLPAPER.storageKey)

    suspend fun setWallpaperAccent(color: Int) = write(Keys.ACCENT_OVERRIDE, color)

    suspend fun setDynamicWallpaperColors(enabled: Boolean) {
        dataStore.edit { p -> putOrRemove(p, Keys.DYNAMIC_WALLPAPER_COLORS, true.takeIf { enabled }) }
    }

    /** Active Wallpaper Studio entry ID (`art:...` or `photo:...`), or null when following system/theme default. */
    val activeWallpaperId: Flow<String?> = readNullable(Keys.ACTIVE_WALLPAPER_ID)

    /** Platform `WallpaperManager.getWallpaperId(FLAG_SYSTEM)` recorded when Luma applied [activeWallpaperId]. */
    val activeWallpaperSystemId: Flow<Int?> = readNullable(Keys.ACTIVE_WALLPAPER_SYSTEM_ID)

    /** Cached optical [com.lumaos.pro.launcher.ui.theme.WallpaperSeeds] string for instant cold-start palette resolution. */
    val cachedWallpaperSeedsRaw: Flow<String?> = readNullable(Keys.CACHED_WALLPAPER_SEEDS)

    suspend fun setCachedWallpaperSeedsRaw(encoded: String?) {
        dataStore.edit { p -> putOrRemove(p, Keys.CACHED_WALLPAPER_SEEDS, encoded?.takeIf { it.isNotBlank() }) }
    }

    /** Cached [com.lumaos.pro.launcher.ui.theme.LumaWallpaperPalette] string for zero-flash cold starts. */
    val cachedLumaPaletteRaw: Flow<String?> = readNullable(Keys.CACHED_LUMA_PALETTE)

    suspend fun setCachedLumaPaletteRaw(encoded: String?) {
        dataStore.edit { p -> putOrRemove(p, Keys.CACHED_LUMA_PALETTE, encoded?.takeIf { it.isNotBlank() }) }
    }

    /** Tracks whether the single optional notification-access bottom sheet has been shown once. */
    val notificationPromptShown: Flow<Boolean> = read(Keys.NOTIFICATION_PROMPT_SHOWN) { false }

    suspend fun isNotificationPromptShownOnce(): Boolean = notificationPromptShown.first()

    suspend fun setNotificationPromptShown(shown: Boolean) = write(Keys.NOTIFICATION_PROMPT_SHOWN, shown)

    /**
     * Records an applied Wallpaper Studio selection and optionally switches the
     * Home backdrop and dynamic wallpaper theming in one atomic transaction.
     */
    suspend fun recordAppliedWallpaper(
        wallpaperId: String?,
        systemWallpaperId: Int? = null,
        backdrop: Backdrop? = null,
        enableDynamicColors: Boolean? = null
    ) {
        dataStore.edit { p ->
            putOrRemove(p, Keys.ACTIVE_WALLPAPER_ID, wallpaperId?.takeIf { it.isNotBlank() })
            putOrRemove(p, Keys.ACTIVE_WALLPAPER_SYSTEM_ID, systemWallpaperId)
            if (backdrop != null) {
                p[Keys.BACKDROP] = backdrop.storageKey
            }
            if (enableDynamicColors != null) {
                putOrRemove(p, Keys.DYNAMIC_WALLPAPER_COLORS, true.takeIf { enableDynamicColors })
                if (enableDynamicColors) {
                    p.remove(Keys.ACCENT_OVERRIDE)
                    p.remove(Keys.BACKGROUND_OVERRIDE)
                }
            }
        }
    }

    suspend fun clearActiveWallpaperIfMatches(wallpaperId: String) {
        dataStore.edit { p ->
            if (p[Keys.ACTIVE_WALLPAPER_ID] == wallpaperId) {
                p.remove(Keys.ACTIVE_WALLPAPER_ID)
                p.remove(Keys.ACTIVE_WALLPAPER_SYSTEM_ID)
            }
        }
    }

    val wallpaperFavorites: Flow<Set<String>> = read(Keys.WALLPAPER_FAVORITES) { emptySet() }
        .map { it.toSet() }

    /** Atomic set edit; concurrent favorite changes cannot overwrite each other. */
    suspend fun setWallpaperFavorite(id: String, favorite: Boolean) {
        require(id.length in 1..160)
        dataStore.edit { p ->
            val next = p[Keys.WALLPAPER_FAVORITES].orEmpty().toMutableSet()
            if (favorite) next.add(id) else next.remove(id)
            p[Keys.WALLPAPER_FAVORITES] = next
        }
    }

    private fun putThemeSettings(p: MutablePreferences, settings: ThemeSettings) {
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
        p[Keys.BACKDROP] = settings.backdrop?.storageKey ?: BACKDROP_THEME_DEFAULT
        putOrRemove(p, Keys.DYNAMIC_WALLPAPER_COLORS, true.takeIf { settings.dynamicWallpaperColors })
        for (profile in ContextProfile.entries) {
            putOrRemove(p, profileThemeKey(profile), settings.profileThemes[profile]?.storageKey)
        }
        p[Keys.THEME_MODE] = when (settings.switching) {
            ThemeSwitching.SYSTEM, ThemeSwitching.SCHEDULE -> ThemeMode.SYSTEM.storageKey
            ThemeSwitching.FIXED -> if (settings.themeId == ThemeId.PORCELAIN) ThemeMode.LIGHT.storageKey
            else ThemeMode.DARK.storageKey
        }
    }

    /**
     * Commits the whole Studio draft as one DataStore transaction. A colour-only
     * edit must not reset a manually chosen row count. Changing the layout preset
     * still adopts its documented default rows, just like setHomePreset().
     */
    suspend fun applyStudioSettings(
        theme: ThemeSettings,
        iconPack: String?,
        homePreset: HomePreset,
        dockStyle: DockStyle?,
        effects: EffectsPreference,
        motion: MotionPreference,
        livingIconsEnabled: Boolean? = null
    ) {
        dataStore.edit { p ->
            val previousPreset = HomePreset.fromStorageKey(p[Keys.HOME_PRESET])
            putThemeSettings(p, theme)
            putOrRemove(p, Keys.ICON_PACK, iconPack?.takeIf { it.isNotBlank() })
            if (livingIconsEnabled != null) {
                p[Keys.LIVING_ICONS_ENABLED] = livingIconsEnabled
            }
            if (previousPreset != homePreset) {
                p[Keys.HOME_PRESET] = homePreset.storageKey
                p[Keys.GRID_ROWS] = homePreset.defaultRows
            }
            putOrRemove(p, Keys.DOCK_STYLE, dockStyle?.storageKey)
            p[Keys.EFFECTS_PREF] = effects.storageKey
            p[Keys.MOTION_PREF] = motion.storageKey
        }
    }

    /** Apply a coordinated look without replacing saved rows, layouts or widgets. */
    suspend fun applyExperienceProfile(profile: ExperienceProfile) {
        dataStore.edit { p -> putExperienceProfile(p, profile) }
    }

    /**
     * Seed Liquid only on a genuinely unconfigured installation. Onboarding can
     * be recreated or shown after a restore, so it must not reapply defaults over
     * an existing appearance merely because it was opened again.
     */
    suspend fun initializeDefaultExperienceIfUnconfigured() {
        dataStore.edit { p ->
            val appearanceKeys = listOf(
                Keys.EXPERIENCE_PROFILE, Keys.THEME_MODE, Keys.THEME_ID, Keys.THEME_SWITCHING,
                Keys.THEME_LIGHT_ID, Keys.THEME_DARK_ID, Keys.THEME_DARK_WINDOW,
                Keys.ACCENT_OVERRIDE, Keys.BACKGROUND_OVERRIDE, Keys.TYPOGRAPHY,
                Keys.ICON_SHAPE, Keys.ICON_STYLE, Keys.ICON_PACK,
                Keys.LIVING_ICONS_ENABLED, Keys.LIVING_ICON_OVERRIDES, Keys.BACKDROP,
                Keys.DYNAMIC_WALLPAPER_COLORS, Keys.ACTIVE_WALLPAPER_ID,
                Keys.HOME_PRESET, Keys.DOCK_STYLE, Keys.EFFECTS_PREF, Keys.MOTION_PREF,
                Keys.GRID_COLUMNS, Keys.GRID_ROWS, Keys.DRAWER_COLUMNS, Keys.ICON_SIZE_DP,
                Keys.SHOW_LABELS, Keys.TEXT_SCALE, Keys.HOME_LAYOUT, Keys.LAYOUT_SEEDED
            )
            val configured = appearanceKeys.any { it in p.asMap() } ||
                ContextProfile.entries.any { p[profileThemeKey(it)] != null }
            if (!configured) putExperienceProfile(p, ExperienceProfile.DEFAULT)
        }
    }

    private fun putExperienceProfile(p: MutablePreferences, profile: ExperienceProfile) {
        putThemeSettings(p, profile.themeSettings(themeSettingsFrom(p)))
        p[Keys.HOME_PRESET] = profile.preset.storageKey
        // Only seed a row count when the user has never chosen one.
        if (p[Keys.GRID_ROWS] == null) p[Keys.GRID_ROWS] = profile.preset.defaultRows
        p[Keys.DOCK_STYLE] = profile.dock.storageKey
        p[Keys.EFFECTS_PREF] = profile.effects.storageKey
        p[Keys.MOTION_PREF] = profile.motion.storageKey
        p[Keys.EXPERIENCE_PROFILE] = profile.storageKey
    }

    suspend fun setProfileTheme(profile: ContextProfile, id: ThemeId?) {
        dataStore.edit { p -> putOrRemove(p, profileThemeKey(profile), id?.storageKey) }
    }

    /** Installed icon pack package, or null for the apps' own icons. */
    val iconPack: Flow<String?> = readNullable(Keys.ICON_PACK)
    suspend fun setIconPack(packageName: String?) {
        dataStore.edit { p -> putOrRemove(p, Keys.ICON_PACK, packageName?.takeIf { it.isNotBlank() }) }
    }

    /** Whether bundled LumaOS Pro Living Icons are enabled across launcher surfaces. */
    val livingIconsEnabled: Flow<Boolean> = read(Keys.LIVING_ICONS_ENABLED) { true }
    suspend fun setLivingIconsEnabled(enabled: Boolean) = write(Keys.LIVING_ICONS_ENABLED, enabled)

    /** Per-component user overrides (custom Living Icon assignment or forced system icon). */
    val livingIconOverrides: Flow<Map<ComponentKey, LivingIconOverride>> =
        readNullable(Keys.LIVING_ICON_OVERRIDES).map { LivingIconOverridesCodec.decode(it) }

    suspend fun setLivingIconOverride(key: ComponentKey, override: LivingIconOverride?) {
        dataStore.edit { p ->
            val updated = LivingIconOverridesCodec.decode(p[Keys.LIVING_ICON_OVERRIDES]).toMutableMap()
            if (override == null) updated.remove(key) else updated[key] = override
            val encoded = LivingIconOverridesCodec.encode(updated)
            putOrRemove(p, Keys.LIVING_ICON_OVERRIDES, encoded.takeIf { it.isNotEmpty() })
        }
    }

    suspend fun clearLivingIconOverrides() {
        dataStore.edit { p -> p.remove(Keys.LIVING_ICON_OVERRIDES) }
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

    /** List is the efficient default; the original grid remains fully supported. */
    val drawerPresentation: Flow<DrawerPresentation> = readNullable(Keys.DRAWER_PRESENTATION)
        .map { DrawerPresentation.fromStorageKey(it) ?: DrawerPresentation.LIST }
    suspend fun setDrawerPresentation(value: DrawerPresentation) =
        write(Keys.DRAWER_PRESENTATION, value.storageKey)

    val drawerCategoriesEnabled: Flow<Boolean> = read(Keys.DRAWER_CATEGORIES) { true }
    suspend fun setDrawerCategoriesEnabled(value: Boolean) = write(Keys.DRAWER_CATEGORIES, value)

    /**
     * Whether LumaOS Pro learns from launches made through it (frequent/recent
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
                if (!isRestorable(key.name) || key.name in values) continue
                // A private photo selection is device-local and intentionally excluded
                // from backups; keep it unless the backup explicitly restores a bundled art ID.
                if (key == Keys.ACTIVE_WALLPAPER_ID && (p[Keys.ACTIVE_WALLPAPER_ID]?.startsWith("photo:") == true)) {
                    continue
                }
                p.remove(key)
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

    private fun <T> putOrRemove(p: MutablePreferences, key: Preferences.Key<T>, value: T?) {
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

    /**
     * Raw, single-snapshot read for layout safety checks; IO errors propagate.
     * Order is the base layout, known profiles in enum order, then future keys.
     */
    suspend fun allHomeLayoutsRaw(): List<String?> {
        val stored = dataStore.data.first()
        val known = listOf(Keys.HOME_LAYOUT) + ContextProfile.entries.map(::profileLayoutKey)
        val names = known.mapTo(HashSet()) { it.name }
        // A future or damaged profile key must not be overlooked on downgrade.
        // A non-string value is represented as an uncertain (blank) layout.
        val extra = stored.asMap().filterKeys { it.name.startsWith("home_layout_") && it.name !in names }
            .values.map { it as? String ?: "" }
        return known.map { stored[it] } + extra
    }

    /**
     * Compare the effective layout inside the same transaction that writes it.
     * A null profile addresses the legacy/base layout; a profile compares its
     * own value or its base fallback. Used only for automatic maintenance.
     */
    suspend fun setHomeLayoutRawIfUnchanged(profile: ContextProfile?, expected: String?, value: String): Boolean {
        var written = false
        dataStore.edit { p ->
            val key = profile?.let(::profileLayoutKey) ?: Keys.HOME_LAYOUT
            val current = if (profile == null) p[key] else p[key] ?: p[Keys.HOME_LAYOUT]
            if (current == expected) {
                p[key] = value
                written = true
            }
        }
        return written
    }

    val activeContextProfile: Flow<ContextProfile> =
        read(Keys.ACTIVE_CONTEXT_PROFILE) { ContextProfile.PERSONAL.name }
            .map { ContextProfile.parse(it) ?: ContextProfile.PERSONAL }

    suspend fun setActiveContextProfile(profile: ContextProfile) =
        write(Keys.ACTIVE_CONTEXT_PROFILE, profile.name)

    /** Notifications here mean LumaOS Pro's opt-in context-change alerts, not control of other apps. */
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

    /** Explicit favourites for the vertical/grid drawer, independent per context. */
    fun pinnedApps(profile: ContextProfile): Flow<List<ComponentKey>> =
        readNullable(pinnedAppsKey(profile)).map { raw ->
            raw.orEmpty().split('|').mapNotNullTo(mutableListOf()) { ComponentKey.parse(it) }.distinct()
        }

    suspend fun setAppPinned(profile: ContextProfile, key: ComponentKey, pinned: Boolean) {
        dataStore.edit { prefs ->
            val prefKey = pinnedAppsKey(profile)
            val current = prefs[prefKey].orEmpty().split('|')
                .mapNotNullTo(mutableListOf()) { ComponentKey.parse(it) }.distinct().toMutableList()
            current.remove(key)
            if (pinned) current.add(key)
            putOrRemove(prefs, prefKey, current.takeIf { it.isNotEmpty() }?.joinToString("|") { it.serialize() })
        }
    }

    private fun profileLayoutKey(profile: ContextProfile) = stringPreferencesKey("home_layout_${profile.name.lowercase()}")
    private fun scheduleKey(profile: ContextProfile) = stringPreferencesKey("schedule_${profile.name.lowercase()}")
    private fun hiddenAppsKey(profile: ContextProfile) = stringPreferencesKey("hidden_apps_${profile.name.lowercase()}")
    private fun pinnedAppsKey(profile: ContextProfile) = stringPreferencesKey("pinned_apps_${profile.name.lowercase()}")

    /** Controls adding widgets; existing placed widgets are never removed by this setting. */
    val widgetsEnabled: Flow<Boolean> = read(Keys.WIDGETS_ENABLED) { true }
    suspend fun setWidgetsEnabled(value: Boolean) = write(Keys.WIDGETS_ENABLED, value)

    /** Selected first-class experience (PURE, LIQUID, MIDNIGHT). New installs default to LIQUID. */
    val experienceProfile: Flow<ExperienceProfile> = readNullable(Keys.EXPERIENCE_PROFILE)
        .map { ExperienceProfile.fromStorageKey(it) ?: ExperienceProfile.DEFAULT }
    suspend fun setExperienceProfile(profile: ExperienceProfile) =
        write(Keys.EXPERIENCE_PROFILE, profile.storageKey)

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
                    LumaLog.e("Preferences read failed, falling back to defaults", e)
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
                    LumaLog.e("Preferences read failed, falling back to defaults", e)
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
        private const val BACKDROP_THEME_DEFAULT = "theme_default"

        const val DEFAULT_GRID_COLUMNS = LumaTokens.gridColumnsNarrow
        const val DEFAULT_GRID_ROWS = 5
        const val DEFAULT_DRAWER_COLUMNS = LumaTokens.gridColumnsNarrow
        const val DEFAULT_ICON_SIZE_DP = LumaTokens.iconAppInt

        fun create(context: Context): LumaPrefs = LumaPrefs(context.lumaosDataStore)
    }
}

/** The gestures LumaOS Pro can bind. Defaults match conventional launcher behaviour. */
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
