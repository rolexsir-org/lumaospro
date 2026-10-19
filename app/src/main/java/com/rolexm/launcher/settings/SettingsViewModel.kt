package com.rolexm.launcher.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rolexm.launcher.core.RolexApp
import com.rolexm.launcher.core.util.RoleHelper
import com.rolexm.launcher.diagnostics.OptimizationEvent
import com.rolexm.launcher.diagnostics.StartupStats
import com.rolexm.launcher.intelligence.DeviceCapability
import com.rolexm.launcher.intelligence.IntelligenceState
import com.rolexm.launcher.optimizer.OptimizationAction
import com.rolexm.launcher.optimizer.OptimizationResult
import com.rolexm.launcher.optimizer.Optimizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Whether ROLEXM currently owns the Home screen, and what it was replaced by. */
data class RoleState(
    val isDefault: Boolean,
    val currentDefaultPackage: String?,
    val currentDefaultLabel: String?,
    val checkedAtMillis: Long
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

    init {
        refreshRoleStatus()
        refreshHistory()
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
        viewModelScope.launch {
            _events.value = core.diagnostics.recentEvents(limit = 30)
            _startupStats.value = StartupStats.from(core.diagnostics.startupHistory(limit = 20))
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
}
