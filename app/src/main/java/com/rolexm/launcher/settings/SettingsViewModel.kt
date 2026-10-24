package com.rolexm.launcher.settings

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rolexm.launcher.BuildConfig
import com.rolexm.launcher.R
import com.rolexm.launcher.backup.BackupCodec
import com.rolexm.launcher.core.RolexApp
import com.rolexm.launcher.core.apps.AppEntry
import com.rolexm.launcher.core.model.ComponentKey
import com.rolexm.launcher.context.ContextProfile
import com.rolexm.launcher.context.ProfileSchedule
import com.rolexm.launcher.core.util.RoleHelper
import com.rolexm.launcher.diagnostics.OptimizationEvent
import com.rolexm.launcher.diagnostics.StartupStats
import com.rolexm.launcher.intelligence.DeviceCapability
import com.rolexm.launcher.intelligence.IntelligenceState
import com.rolexm.launcher.effects.EffectsPreference
import com.rolexm.launcher.icons.IconPackInfo
import com.rolexm.launcher.icons.IconRenderSpec
import com.rolexm.launcher.icons.IconRenderer
import com.rolexm.launcher.launcher.model.DockStyle
import com.rolexm.launcher.launcher.model.HomeItem
import com.rolexm.launcher.launcher.model.HomeLayoutCodec
import com.rolexm.launcher.launcher.model.HomePreset
import com.rolexm.launcher.motion.MotionPreference
import com.rolexm.launcher.optimizer.OptimizationAction
import com.rolexm.launcher.optimizer.OptimizationResult
import com.rolexm.launcher.optimizer.Optimizer
import com.rolexm.launcher.ui.theme.ResolvedTheme
import com.rolexm.launcher.ui.theme.ThemeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Whether ROLEXM currently owns the Home screen, and what it was replaced by. */
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
    val homePreset: HomePreset = HomePreset.DEFAULT,
    val dockStyle: DockStyle? = null,
    val effects: EffectsPreference = EffectsPreference.AUTO,
    val motion: MotionPreference = MotionPreference.FULL
)

data class StudioPreviewIcons(val home: List<Bitmap>, val dock: List<Bitmap>)

data class BackupUiState(val busy: Boolean = false, val message: String? = null)

private data class StudioVisuals(
    val theme: ThemeSettings,
    val iconPack: String?,
    val homePreset: HomePreset,
    val dockStyle: DockStyle?
)

/**
 * State for the settings and diagnostics screens.
 *
 * Holds no duplicated state: preferences, engine state and diagnostics are all
 * read straight from ROLEXM Core, so a change made here is reflected on the
 * home screen immediately and vice versa.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val core = (application as RolexApp).core

    val prefs = core.prefs
    val intelligenceState: StateFlow<IntelligenceState> = core.intelligence.state
    val capability: DeviceCapability = core.capability
    val actions: List<OptimizationAction> = core.optimizer.actions()
    val themes = core.themes
    val effectsState = core.effects.decision

    val studioConfiguration: StateFlow<StudioConfiguration> = combine(
        combine(
            prefs.themeSettings, prefs.iconPack, prefs.homePreset, prefs.dockStyleOverride
        ) { theme, pack, preset, dock -> StudioVisuals(theme, pack, preset, dock) },
        prefs.effectsPreference,
        prefs.motionPreference
    ) { visuals, effects, motion ->
        StudioConfiguration(
            visuals.theme, visuals.iconPack, visuals.homePreset, visuals.dockStyle, effects, motion
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StudioConfiguration())

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

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()
    private val _appsLoading = MutableStateFlow(true)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    private val _historyLoading = MutableStateFlow(true)
    val historyLoading: StateFlow<Boolean> = _historyLoading.asStateFlow()

    private val _hasDndAccess = MutableStateFlow(false)
    val hasDndAccess: StateFlow<Boolean> = _hasDndAccess.asStateFlow()

    init {
        refreshApps()
        refreshRoleStatus()
        refreshHistory()
        refreshIconPacks()
    }

    fun refreshApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _appsLoading.value = true
            try {
                _apps.value = core.appRepository.refresh()
            } finally {
                _appsLoading.value = false
            }
        }
    }

    fun setAppHidden(profile: ContextProfile, key: ComponentKey, hidden: Boolean) {
        viewModelScope.launch { prefs.setAppHidden(profile, key, hidden) }
    }

    fun setSchedule(profile: ContextProfile, schedule: ProfileSchedule) {
        viewModelScope.launch { prefs.setProfileSchedule(profile, schedule) }
    }

    fun selectContextProfile(profile: ContextProfile) {
        viewModelScope.launch { prefs.setActiveContextProfile(profile) }
    }

    fun applyStudio(configuration: StudioConfiguration) {
        viewModelScope.launch {
            val previous = studioConfiguration.value
            prefs.applyThemeSettings(configuration.theme)
            prefs.setIconPack(configuration.iconPack)
            // setHomePreset intentionally adopts its default row count. Do not
            // call it for an unrelated colour edit: existing row customisation
            // and, most importantly, the saved arrangement must remain intact.
            if (previous.homePreset != configuration.homePreset) {
                prefs.setHomePreset(configuration.homePreset)
            }
            prefs.setDockStyle(configuration.dockStyle)
            prefs.setEffectsPreference(configuration.effects)
            prefs.setMotionPreference(configuration.motion)
        }
    }

    fun refreshIconPacks() {
        viewModelScope.launch(Dispatchers.IO) {
            _iconPacks.value = core.iconPacks.installedPacks()
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
            } catch (_: Exception) {
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
            } catch (e: Exception) {
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
        depth: Boolean
    ): StudioPreviewIcons = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val profile = prefs.activeContextProfile.first()
        val layout = HomeLayoutCodec.decode(prefs.homeLayoutRaw(profile).first())
        val homeKeys = layout.page(0).filterIsInstance<HomeItem.App>().take(12).map { it.key }
        val dockKeys = layout.dock.filterIsInstance<HomeItem.App>().take(4).map { it.key }
        val metrics = context.resources.displayMetrics
        val size = (52 * metrics.density).toInt().coerceIn(48, 156)
        val spec = IconRenderSpec.from(theme, size, depth, iconPack)
        val renderer = IconRenderer()

        fun render(key: ComponentKey): Bitmap? {
            val component = ComponentName(key.packageName, key.className)
            return try {
                val packed = iconPack?.let { core.iconPacks.iconFor(it, component, metrics.densityDpi) }
                val source = packed ?: core.appRepository.iconFor(key, metrics.densityDpi) ?: return null
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
    fun refreshRoleStatus() {
        val context = getApplication<Application>()
        val current = RoleHelper.defaultHomePackage(context)
        _roleState.value = RoleState(
            isDefault = current == context.packageName,
            currentDefaultPackage = current,
            currentDefaultLabel = RoleHelper.defaultHomeLabel(context),
            checkedAtMillis = System.currentTimeMillis()
        )
    }

    fun refreshHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _historyLoading.value = true
            try {
                _events.value = core.diagnostics.recentEvents(limit = 30)
                _startupStats.value = StartupStats.from(core.diagnostics.startupHistory(limit = 20))
            } finally {
                _historyLoading.value = false
            }
        }
    }

    fun runAction(actionId: String) {
        viewModelScope.launch {
            _isRunning.value = _isRunning.value + actionId
            val result = core.optimizer.run(
                actionId = actionId,
                trigger = "manual",
                profile = core.intelligence.state.value.profile
            )
            _lastResults.value = _lastResults.value + (actionId to result)
            _isRunning.value = _isRunning.value - actionId
            _statusMessage.value = result.detail
            refreshHistory()
        }
    }

    fun emergencyReset() {
        viewModelScope.launch {
            _isRunning.value = _isRunning.value + Optimizer.ACTION_APPLY_PROFILE
            val result = core.optimizer.emergencyReset()
            _statusMessage.value = result.detail
            _isRunning.value = emptySet()
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
