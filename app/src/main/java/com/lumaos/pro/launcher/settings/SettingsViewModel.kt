package com.lumaos.pro.launcher.settings

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumaos.pro.launcher.BuildConfig
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.backup.BackupCodec
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.context.ContextAppSuggestions
import com.lumaos.pro.launcher.context.ContextEngine
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.context.FocusAccess
import com.lumaos.pro.launcher.context.ManualOverride
import com.lumaos.pro.launcher.context.ProfileSchedule
import com.lumaos.pro.launcher.core.util.RoleHelper
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.core.util.RefreshCoalescer
import com.lumaos.pro.launcher.diagnostics.DiagnosticsHistory
import com.lumaos.pro.launcher.diagnostics.OptimizationEvent
import com.lumaos.pro.launcher.diagnostics.StartupStats
import com.lumaos.pro.launcher.intelligence.DeviceCapability
import com.lumaos.pro.launcher.intelligence.IntelligenceState
import com.lumaos.pro.launcher.effects.EffectsPreference
import com.lumaos.pro.launcher.icons.IconPackInfo
import com.lumaos.pro.launcher.icons.IconRenderSpec
import com.lumaos.pro.launcher.icons.IconRenderer
import com.lumaos.pro.launcher.launcher.model.DockStyle
import com.lumaos.pro.launcher.launcher.model.HomeItem
import com.lumaos.pro.launcher.launcher.model.HomeLayoutCodec
import com.lumaos.pro.launcher.launcher.model.HomePreset
import com.lumaos.pro.launcher.motion.MotionPreference
import com.lumaos.pro.launcher.optimizer.OptimizationAction
import com.lumaos.pro.launcher.optimizer.OptimizationResult
import com.lumaos.pro.launcher.optimizer.Optimizer
import com.lumaos.pro.launcher.settings.studio.StudioDraftSession
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemeLauncherPackage
import com.lumaos.pro.launcher.ui.theme.ThemePresets
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import com.lumaos.pro.launcher.ui.theme.ThemeSwitching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Whether LumaOS Pro currently owns the Home screen, and what it was replaced by. */
data class RoleState(
    val isDefault: Boolean,
    val currentDefaultPackage: String?,
    val currentDefaultLabel: String?,
    val checkedAtMillis: Long
)

/** One Studio transaction. Layout positions and row customisation are not part of it. */
data class StudioConfiguration(
    val theme: ThemeSettings = ThemeSettings(),
    val iconPack: String? = null,
    val livingIconsEnabled: Boolean = true,
    val homePreset: HomePreset = HomePreset.DEFAULT,
    val dockStyle: DockStyle? = null,
    val effects: EffectsPreference = EffectsPreference.AUTO,
    val motion: MotionPreference = MotionPreference.FULL
) {
    /**
     * Switches to [id] and adopts its full v3.2 launcher effects package
     * (composition preset, dock style, backdrop, effects tier, motion, icon treatment).
     */
    fun withThemePackage(id: ThemeId): StudioConfiguration {
        val pkg = ThemeLauncherPackage.forTheme(id)
        return copy(
            theme = pkg.toThemeSettings(theme),
            livingIconsEnabled = pkg.livingIcons,
            homePreset = pkg.preset,
            dockStyle = pkg.dockStyle,
            effects = pkg.effects,
            motion = pkg.motion
        )
    }

    /**
     * Theme selection from Studio's theme cards.
     *
     * With a fixed appearance this is exactly [withThemePackage]. When the
     * appearance follows the system or a schedule, the chosen theme fills the
     * slot matching its own polarity (Ivory -> light, Obsidian -> dark) and the
     * automatic switching, its window and per-context themes are kept, so
     * picking a theme never silently turns automatic switching off.
     */
    fun withThemeSelection(id: ThemeId): StudioConfiguration {
        val previous = theme
        val packaged = withThemePackage(id)
        if (previous.switching == ThemeSwitching.FIXED) return packaged
        val dark = ThemePresets.of(id).dark
        return packaged.copy(
            theme = packaged.theme.copy(
                switching = previous.switching,
                darkWindow = previous.darkWindow,
                profileThemes = previous.profileThemes,
                lightThemeId = if (dark) previous.lightThemeId else id,
                darkThemeId = if (dark) id else previous.darkThemeId
            )
        )
    }

    /**
     * Changes how the theme is chosen over time. Entering an automatic mode
     * seeds the matching slot with the current theme, so the home screen does
     * not jump to an unrelated theme the moment the mode changes.
     */
    fun withAppearance(switching: ThemeSwitching): StudioConfiguration {
        val t = theme
        if (switching == ThemeSwitching.FIXED) return copy(theme = t.copy(switching = ThemeSwitching.FIXED))
        val currentDark = ThemePresets.of(t.themeId).dark
        return copy(
            theme = t.copy(
                switching = switching,
                lightThemeId = if (currentDark) t.lightThemeId else t.themeId,
                darkThemeId = if (currentDark) t.themeId else t.darkThemeId
            )
        )
    }
}

data class StudioDraftState(val current: StudioConfiguration, val canUndo: Boolean, val canRedo: Boolean)

data class StudioPreviewIcons(val home: List<Bitmap>, val dock: List<Bitmap>)

enum class StudioApplyState { IDLE, APPLYING, FAILED }

data class BackupUiState(val busy: Boolean = false, val message: String? = null)

private data class StudioVisuals(
    val theme: ThemeSettings,
    val iconPack: String?,
    val livingIconsEnabled: Boolean,
    val homePreset: HomePreset,
    val dockStyle: DockStyle?
)

/**
 * State for the settings and diagnostics screens.
 *
 * Holds no duplicated state: preferences, engine state and diagnostics are all
 * read straight from LumaOS Pro Core, so a change made here is reflected on the
 * home screen immediately and vice versa.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val core = (application as LumaApp).core

    val prefs = core.prefs
    val intelligenceState: StateFlow<IntelligenceState> = core.intelligence.state
    val capability: DeviceCapability = core.capability
    val actions: List<OptimizationAction> = core.optimizer.actions()
    val themes = core.themes
    val effectsState = core.effects.decision

    // Null until every backing preference has emitted, so Studio cannot apply
    // its placeholder defaults over a previously saved appearance at startup.
    val studioConfiguration: StateFlow<StudioConfiguration?> = combine(
        combine(
            prefs.themeSettings,
            prefs.iconPack,
            prefs.livingIconsEnabled,
            prefs.homePreset,
            prefs.dockStyleOverride
        ) { theme, pack, living, preset, dock -> StudioVisuals(theme, pack, living, preset, dock) },
        prefs.effectsPreference,
        prefs.motionPreference
    ) { visuals, effects, motion ->
        StudioConfiguration(
            theme = visuals.theme,
            iconPack = visuals.iconPack,
            livingIconsEnabled = visuals.livingIconsEnabled,
            homePreset = visuals.homePreset,
            dockStyle = visuals.dockStyle,
            effects = effects,
            motion = motion
        )
    }.stateIn<StudioConfiguration?>(viewModelScope, SharingStarted.Eagerly, null)

    // The Studio tab leaves composition when another tab is selected. Keep its
    // unapplied transaction and undo stack here instead of recreating them from
    // preferences each time the user returns. This is session state, not a save.
    private var studioSession: StudioDraftSession<StudioConfiguration>? = null
    private val _studioDraft = MutableStateFlow<StudioDraftState?>(null)
    val studioDraft: StateFlow<StudioDraftState?> = _studioDraft.asStateFlow()

    private fun publishStudioDraft() {
        val session = studioSession ?: return
        _studioDraft.value = StudioDraftState(session.current, session.canUndo, session.canRedo)
    }

    fun syncStudioDraft(saved: StudioConfiguration) {
        val session = studioSession ?: StudioDraftSession(saved).also { studioSession = it }
        session.syncSaved(saved)
        publishStudioDraft()
    }

    fun editStudioDraft(next: StudioConfiguration) {
        studioSession?.edit(next)
        publishStudioDraft()
    }

    fun undoStudioDraft() {
        studioSession?.undo()
        publishStudioDraft()
    }

    fun redoStudioDraft() {
        studioSession?.redo()
        publishStudioDraft()
    }

    fun resetStudioDraft(saved: StudioConfiguration) {
        studioSession?.reset(saved)
        publishStudioDraft()
    }

    /**
     * Session-only Studio preset: a candidate configuration the user can save,
     * leave, and restore while Studio is alive. Deliberately NOT persisted —
     * keeping a configuration across restarts goes through Apply (existing
     * prefs) or the backup export, so no new stored schema is introduced.
     */
    private val _studioPreset = MutableStateFlow<StudioConfiguration?>(null)
    val studioPreset: StateFlow<StudioConfiguration?> = _studioPreset.asStateFlow()

    fun saveStudioPreset(configuration: StudioConfiguration) {
        _studioPreset.value = configuration
    }

    fun clearStudioPreset() {
        _studioPreset.value = null
    }

    private val _studioApplyState = MutableStateFlow(StudioApplyState.IDLE)
    val studioApplyState: StateFlow<StudioApplyState> = _studioApplyState.asStateFlow()

    private val _iconPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())
    val iconPacks: StateFlow<List<IconPackInfo>> = _iconPacks.asStateFlow()

    private val _roleState = MutableStateFlow(
        RoleState(false, null, null, 0L)
    )
    val roleState: StateFlow<RoleState> = _roleState.asStateFlow()

    private val _events = MutableStateFlow<List<OptimizationEvent>>(emptyList())
    val events: StateFlow<List<OptimizationEvent>> = _events.asStateFlow()

    private val _startupStats = MutableStateFlow<StartupStats?>(null)
    val startupStats: StateFlow<StartupStats?> = _startupStats.asStateFlow()

    /** Result of the most recent manual run per action id, for inline feedback. */
    private val _lastResults = MutableStateFlow<Map<String, OptimizationResult>>(emptyMap())
    val lastResults: StateFlow<Map<String, OptimizationResult>> = _lastResults.asStateFlow()

    private val _isRunning = MutableStateFlow<Set<String>>(emptySet())
    val isRunning: StateFlow<Set<String>> = _isRunning.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    private val _historyLoading = MutableStateFlow(true)
    val historyLoading: StateFlow<Boolean> = _historyLoading.asStateFlow()

    /**
     * Non-null when the diagnostics database could not be read.
     *
     * The dashboard shows this instead of "no actions recorded", because an
     * unreadable database and an empty one are different facts and only one of
     * them is true. Never a crash: this screen is reached by tapping a menu
     * item on the home screen, so anything thrown out of [init] or [onResume]
     * would take the launcher process down with it.
     */
    private val _historyError = MutableStateFlow<String?>(null)
    val historyError: StateFlow<String?> = _historyError.asStateFlow()

    private val _hasDndAccess = MutableStateFlow(false)
    val hasDndAccess: StateFlow<Boolean> = _hasDndAccess.asStateFlow()

    /**
     * `onCreate` and every `onResume` ask for the app list and the diagnostics
     * history. Without coalescing, returning to this screen repeatedly stacks a
     * fresh IO job per visit, each one opening a cursor on the same shared
     * SQLite connection. One pass at a time, with a single trailing pass for
     * anything requested in between, is both correct and cheaper.
     */
    private val historyCoalescer = RefreshCoalescer(
        scope = viewModelScope,
        dispatcher = Dispatchers.IO,
        onLoadingChanged = { _historyLoading.value = it },
        operation = ::loadHistoryOnce,
        onFailure = {
            // A history read must degrade, never escape: an exception thrown
            // here would propagate out of viewModelScope and kill the process.
            LumaLog.e("Diagnostics history could not be loaded", it)
            _historyError.value = it.javaClass.simpleName
        }
    )

    /** Manual override state; null while the schedule is in charge. */
    val manualOverride: StateFlow<ManualOverride?> = prefs.manualOverride
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Installed apps for the context visibility / focus pickers. Loaded once:
     * enumeration is binder-heavy and the pickers only need labels.
     */
    private val _installedApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val installedApps: StateFlow<List<AppEntry>> = _installedApps.asStateFlow()

    init {
        refreshRoleStatus()
        refreshHistory()
        refreshIconPacks()
        refreshInstalledApps()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _installedApps.value = core.appRepository.refreshSnapshot().apps
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LumaLog.e("Installed apps could not be listed for context settings", e)
            }
        }
    }

    fun setSchedule(profile: ContextProfile, schedule: ProfileSchedule) {
        viewModelScope.launch { prefs.setProfileSchedule(profile, schedule) }
    }

    fun selectContextProfile(profile: ContextProfile) {
        viewModelScope.launch { ContextEngine.switchManual(getApplication(), prefs, profile) }
    }

    fun resumeSchedule() {
        viewModelScope.launch { ContextEngine.resumeSchedule(getApplication(), prefs) }
    }

    fun setAppVisible(profile: ContextProfile, key: ComponentKey, visible: Boolean) {
        viewModelScope.launch { prefs.setAppVisible(profile, key, visible) }
    }

    fun showAllApps(profile: ContextProfile) {
        viewModelScope.launch { prefs.setDrawerHidden(profile, emptySet()) }
    }

    /** One-tap sensible starting point; the user stays in control app by app. */
    fun applyVisibilitySuggestions(profile: ContextProfile) {
        viewModelScope.launch {
            val apps = _installedApps.value
            if (apps.isEmpty()) return@launch
            val visible = ContextAppSuggestions.suggestedVisible(profile, apps)
            prefs.setDrawerHidden(profile, apps.mapTo(HashSet()) { it.key } - visible)
        }
    }

    fun setFocusEnabled(profile: ContextProfile, enabled: Boolean) {
        viewModelScope.launch { prefs.setFocusEnabled(profile, enabled) }
    }

    fun setAppRestricted(profile: ContextProfile, key: ComponentKey, restricted: Boolean) {
        viewModelScope.launch { prefs.setAppRestricted(profile, key, restricted) }
    }

    fun clearFocusRestrictions(profile: ContextProfile) {
        viewModelScope.launch { prefs.setFocusRestricted(profile, emptySet()) }
    }

    fun applyFocusSuggestions(profile: ContextProfile) {
        viewModelScope.launch {
            val apps = _installedApps.value
            if (apps.isEmpty()) return@launch
            prefs.setFocusRestricted(profile, ContextAppSuggestions.suggestedRestricted(profile, apps))
        }
    }

    fun applyStudio(configuration: StudioConfiguration) {
        if (_studioApplyState.value == StudioApplyState.APPLYING) return
        _studioApplyState.value = StudioApplyState.APPLYING
        viewModelScope.launch {
            try {
                prefs.applyStudioSettings(
                    theme = configuration.theme,
                    iconPack = configuration.iconPack,
                    homePreset = configuration.homePreset,
                    dockStyle = configuration.dockStyle,
                    effects = configuration.effects,
                    motion = configuration.motion,
                    livingIconsEnabled = configuration.livingIconsEnabled
                )
                _studioApplyState.value = StudioApplyState.IDLE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LumaLog.e("Could not apply Studio appearance", e)
                _studioApplyState.value = StudioApplyState.FAILED
            }
        }
    }

    fun refreshIconPacks() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _iconPacks.value = core.iconPacks.installedPacks()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A third-party icon pack can be mid-update or malformed. Keep
                // whatever was already listed rather than blanking a working
                // picker, and keep rendering with the current treatment.
                LumaLog.e("Installed icon packs could not be listed", e)
            }
        }
    }

    suspend fun clearLaunchHistory() = core.launchHistory.clear()

    /** Exports only after the user chooses a document through Android's SAF. */
    fun exportBackup(uri: Uri) {
        if (_backupState.value.busy) return
        _backupState.value = BackupUiState(busy = true)
        viewModelScope.launch {
            val message = try {
                val values = prefs.snapshotForBackup()
                withContext(Dispatchers.IO) {
                    val encoded = BackupCodec.encode(values, BuildConfig.VERSION_NAME, System.currentTimeMillis())
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "w")
                        ?: throw java.io.IOException("Document provider did not open an output stream")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(encoded) }
                }
                getApplication<Application>().getString(R.string.backup_exported)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The user sees that the export failed; the log carries why.
                // Silently reporting "failed" with no cause is not a diagnostic.
                LumaLog.e("Backup export failed", e)
                getApplication<Application>().getString(R.string.backup_export_failed)
            }
            _backupState.value = BackupUiState(message = message)
        }
    }

    /** Imports a bounded, type-checked document; unknown/device-local keys are not written. */
    fun importBackup(uri: Uri) {
        if (_backupState.value.busy) return
        _backupState.value = BackupUiState(busy = true)
        viewModelScope.launch {
            val message = try {
                val decoded = withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw java.io.IOException("Document provider did not open an input stream")
                    val text = stream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val output = StringBuilder()
                        val buffer = CharArray(8_192)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            if (output.length + count > MAX_BACKUP_CHARS) throw BackupTooLargeException()
                            output.append(buffer, 0, count)
                        }
                        output.toString()
                    }
                    BackupCodec.decode(text)
                }
                prefs.restoreFromBackup(decoded.values) { BackupCodec.expectedType(it) != null }
                val app = getApplication<Application>()
                if (decoded.skippedKeys.isEmpty()) {
                    app.getString(R.string.backup_restored, decoded.values.size)
                } else {
                    app.getString(R.string.backup_restored_skipped, decoded.values.size, decoded.skippedKeys.size)
                }
            } catch (_: BackupTooLargeException) {
                getApplication<Application>().getString(R.string.backup_too_large)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LumaLog.e("Backup import failed", e)
                getApplication<Application>().getString(
                    R.string.backup_restore_failed, e.message ?: e.javaClass.simpleName
                )
            }
            _backupState.value = BackupUiState(message = message)
        }
    }

    fun consumeBackupMessage() {
        if (!_backupState.value.busy) _backupState.value = BackupUiState()
    }

    /** Renders the user's actual home/dock apps with the draft treatment. */
    suspend fun loadPreviewIcons(
        theme: ResolvedTheme,
        iconPack: String?,
        depth: Boolean,
        livingIconsEnabled: Boolean = true,
        explicitTint: Boolean = false,
        iconSizeDp: Int = 52
    ): StudioPreviewIcons = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val profile = prefs.activeContextProfile.first()
        val overrides = prefs.livingIconOverrides.first()
        val layout = HomeLayoutCodec.decode(prefs.homeLayoutRaw(profile).first())
        val homeKeys = layout.page(0).filterIsInstance<HomeItem.App>().take(12).map { it.key }
        val dockKeys = layout.dock.filterIsInstance<HomeItem.App>().take(4).map { it.key }
        val metrics = context.resources.displayMetrics
        // Render at the user's actual icon-size preference so the preview is
        // honest about scale; clamped to the same sane band as before.
        val size = (iconSizeDp * metrics.density).toInt().coerceIn(48, 156)
        val spec = IconRenderSpec.from(
            theme = theme,
            sizePx = size,
            depth = depth,
            iconPack = iconPack,
            livingIcons = livingIconsEnabled,
            livingOverridesHash = overrides.hashCode(),
            explicitTint = explicitTint
        )
        val renderer = IconRenderer()

        fun render(key: ComponentKey): Bitmap? {
            val component = ComponentName(key.packageName, key.className)
            return try {
                if (!core.appRepository.isInstalled(key)) return null
                val exactOverride = overrides[key]
                    ?: if (key.userSerial != 0L) overrides[key.copy(userSerial = 0L)] else null
                val overrideLiving = (exactOverride as? com.lumaos.pro.launcher.icons.LivingIconOverride.UseLiving)
                    ?.let { core.livingIcons.drawableForId(it.icon, size) }
                val forceSystem = exactOverride == com.lumaos.pro.launcher.icons.LivingIconOverride.UseSystem
                val packed = if (!forceSystem && overrideLiving == null && !iconPack.isNullOrBlank()) {
                    core.iconPacks.iconFor(iconPack, component, metrics.densityDpi)
                } else null
                val defaultLiving = if (!forceSystem && overrideLiving == null && packed == null) {
                    com.lumaos.pro.launcher.icons.LivingIconResolver
                        .resolve(key, livingIconsEnabled = livingIconsEnabled, overrides = overrides)
                        ?.let { core.livingIcons.drawableForId(it, size) }
                } else null
                val source = overrideLiving
                    ?: packed
                    ?: defaultLiving
                    ?: core.appRepository.iconFor(key, metrics.densityDpi)
                    ?: return null
                renderer.render(source, spec, fromIconPack = packed != null)
            } catch (_: RuntimeException) {
                // A provider can disappear between enumeration and decode. The
                // preview omits that icon rather than substituting a fake one.
                null
            }
        }
        StudioPreviewIcons(homeKeys.mapNotNull(::render), dockKeys.mapNotNull(::render))
    }

    /**
     * Re-reads the Home role.
     *
     * Cannot be a Flow: the system does not emit anything when the default
     * launcher changes, so the only correct approach is to re-resolve on resume.
     */
    /**
     * Whether Android currently reports the Focus accessibility grant.
     * Refreshed every time Settings appears (the system emits nothing when
     * the user toggles the switch), following the role-status pattern.
     */
    private val _focusServiceOn = MutableStateFlow(false)
    val focusServiceOn: StateFlow<Boolean> = _focusServiceOn.asStateFlow()

    fun refreshFocusService() {
        _focusServiceOn.value = FocusAccess.isGranted(getApplication())
    }

    fun setFocusUnlockMinutes(minutes: Int) {
        viewModelScope.launch { prefs.setFocusUnlockMinutes(minutes) }
    }

    fun refreshRoleStatus() {
        val context = getApplication<Application>()
        val current = RoleHelper.defaultHomePackage(context)
        _roleState.value = RoleState(
            isDefault = RoleHelper.isDefaultLauncher(context),
            currentDefaultPackage = current,
            currentDefaultLabel = RoleHelper.defaultHomeLabel(context),
            checkedAtMillis = System.currentTimeMillis()
        )
    }

    fun refreshHistory() {
        historyCoalescer.request()
    }

    /**
     * One history pass. Read failures arrive here as [DiagnosticsHistory.Unavailable]
     * rather than as an exception, so the dashboard can distinguish "empty" from
     * "unreadable" without either case reaching the coroutine exception handler.
     */
    private suspend fun loadHistoryOnce() {
        when (val history = core.diagnostics.loadHistory(eventsLimit = 30, startupLimit = 20)) {
            is DiagnosticsHistory.Loaded -> {
                _events.value = history.events
                _startupStats.value = StartupStats.from(history.startup)
                _historyError.value = null
            }
            is DiagnosticsHistory.Unavailable -> _historyError.value = history.cause
        }
    }

    fun runAction(actionId: String) {
        // Ignore a second tap while the same action is running: two concurrent
        // runs of one action would both trim the same caches and record two
        // events for what the user did once.
        if (actionId in _isRunning.value) return
        viewModelScope.launch {
            _isRunning.value = _isRunning.value + actionId
            try {
                val result = core.optimizer.run(
                    actionId = actionId,
                    trigger = "manual",
                    profile = core.intelligence.state.value.profile
                )
                _lastResults.value = _lastResults.value + (actionId to result)
                _statusMessage.value = result.detail
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The Optimizer already reports its own failures as a result;
                // this only covers something escaping it. It must not reach the
                // coroutine exception handler, which would end the process.
                LumaLog.e("Optimization '$actionId' failed", e)
                _statusMessage.value = "Failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _isRunning.value = _isRunning.value - actionId
            }
            refreshHistory()
        }
    }

    fun emergencyReset() {
        viewModelScope.launch {
            _isRunning.value = _isRunning.value + Optimizer.ACTION_APPLY_PROFILE
            try {
                val result = core.optimizer.emergencyReset()
                _statusMessage.value = result.detail
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LumaLog.e("Emergency reset failed", e)
                _statusMessage.value = "Failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _isRunning.value = emptySet()
            }
            refreshHistory()
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    fun refreshContextDnd() {
        _hasDndAccess.value = getApplication<Application>()
            .getSystemService(android.app.NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true
        core.refreshContextDnd()
    }
}

private class BackupTooLargeException : Exception()
private const val MAX_BACKUP_CHARS = 4_000_000
