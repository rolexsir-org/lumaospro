package com.rolexm.launcher.intelligence

import android.content.Context
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.coroutineScope
import com.rolexm.launcher.core.apps.IconCache
import com.rolexm.launcher.core.prefs.RolexPrefs
import com.rolexm.launcher.core.util.RolexLog
import com.rolexm.launcher.diagnostics.DiagnosticsRepository
import com.rolexm.launcher.diagnostics.OptimizationEvent
import com.rolexm.launcher.diagnostics.Reading
import com.rolexm.launcher.optimizer.Optimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.plus
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the engine currently knows, for the dashboard to render. */
data class IntelligenceState(
    val active: Boolean = false,
    val profile: Profile = Profiles.BALANCED,
    val profileReason: String = "",
    val profileChosenByUser: Boolean = false,
    val memory: Reading<ProcessMemory> = Reading.unavailable("No sample taken yet."),
    val battery: Reading<BatterySnapshot> = Reading.unavailable("No sample taken yet."),
    val frameStats: FrameStats = FrameStats.EMPTY,
    val underMemoryPressure: Boolean = false,
    val lastSampleAtMillis: Long? = null,
    val lastEvaluationAtMillis: Long? = null
)

/**
 * ROLEXM's decision engine.
 *
 * "Event-driven" here means concrete things:
 *  - it is bound to the process lifecycle, so it starts sampling when ROLEXM
 *    becomes visible and stops when it does not;
 *  - it samples on a schedule derived from the active profile rather than on a
 *    fixed timer;
 *  - every automatic action passes a rule and a cooldown.
 *
 * There is deliberately no persistent foreground service. ROLEXM does not keep
 * itself alive to look busy; when it is not visible it does no work at all.
 */
class IntelligenceEngine(
    private val context: Context,
    private val prefs: RolexPrefs,
    private val iconCache: IconCache,
    private val diagnostics: DiagnosticsRepository,
    private val optimizer: Optimizer,
    val capability: DeviceCapability
) : DefaultLifecycleObserver {

    private val scope = ProcessLifecycleOwner.get().lifecycle.coroutineScope + SupervisorJob()

    private val frameMonitor = FrameMonitor()
    private val memorySampler = MemorySampler(context)
    private val batterySampler = BatterySampler(context)

    private val _memoryPressure = MutableStateFlow(false)

    private val _state = MutableStateFlow(IntelligenceState())
    val state: StateFlow<IntelligenceState> = _state

    private var samplingJob: Job? = null
    private var lastAppliedProfileId: ProfileId? = null

    /** Display refresh rate; "slow frame" is relative to the real panel. */
    private val refreshRateHz: Float by lazy {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val rate = display?.refreshRate ?: 0f
        if (rate > 0f) rate else 60f
    }

    /** Called once from Application.onCreate, on the main thread. */
    fun attach() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        scope.launch {
            combine(prefs.profileOverride, _memoryPressure) { override, pressure ->
                ProfileResolver.resolve(override, capability, pressure)
            }
                .distinctUntilChanged()
                .collect { resolution ->
                    // Record genuine profile changes so the history explains
                    // why behaviour shifted, but not the first resolution at
                    // startup (that is not an "action").
                    val previous = lastAppliedProfileId
                    if (previous != null && previous != resolution.profile.id) {
                        diagnostics.record(
                            OptimizationEvent(
                                timestampMillis = System.currentTimeMillis(),
                                actionId = Optimizer.ACTION_APPLY_PROFILE,
                                actionTitle = "Profile changed to ${resolution.profile.id.name}",
                                trigger = Rules.RULE_PROFILE_ADAPT,
                                completed = true,
                                detail = resolution.reason,
                                durationMs = 0L,
                                beforePssKb = null,
                                afterPssKb = null,
                                bytesFreed = 0L,
                                measuredImprovement = false
                            )
                        )
                    }
                    lastAppliedProfileId = resolution.profile.id

                    iconCache.resize(resolution.profile.maxCachedIcons)
                    _state.update {
                        it.copy(
                            profile = resolution.profile,
                            profileReason = resolution.reason,
                            profileChosenByUser = resolution.chosenByUser
                        )
                    }
                    restartFrameMonitorIfNeeded()
                }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        RolexLog.d("Intelligence: visible, resuming")
        _state.update { it.copy(active = true) }
        restartFrameMonitorIfNeeded()
        startSampling()
    }

    override fun onStop(owner: LifecycleOwner) {
        RolexLog.d("Intelligence: backgrounded, pausing")
        _state.update { it.copy(active = false) }
        frameMonitor.stop()
        samplingJob?.cancel()
        samplingJob = null
    }

    /**
     * Called when the home screen has drawn its first frame. Completes the
     * startup measurement and stores it.
     */
    fun onHomeFirstFrame() {
        val duration = StartupTracker.markFirstFrame() ?: return
        RolexLog.i("Startup: ${duration}ms to first frame")
        scope.launch {
            diagnostics.recordStartup(duration)
            frameMonitor.reset()
        }
    }

    /** Drops frame samples, e.g. after a configuration change. */
    fun resetFrameStats() = frameMonitor.reset()

    companion object {
        /** Bookkeeping id for the diagnostics vacuum, stored alongside actions. */
        const val MAINTENANCE_VACUUM = "maintenance_vacuum"

        /** Vacuum at most once a day. */
        const val VACUUM_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }

    private fun restartFrameMonitorIfNeeded() {
        val profile = _state.value.profile
        if (!_state.value.active) return
        if (!profile.frameMonitorEnabled) {
            if (frameMonitor.isRunning()) frameMonitor.stop()
            return
        }
        if (!frameMonitor.isRunning()) {
            try {
                frameMonitor.start(refreshRateHz)
            } catch (e: RuntimeException) {
                RolexLog.w("Frame monitor could not start: ${e.message ?: "unknown"}")
            }
        }
    }

    private fun startSampling() {
        samplingJob?.cancel()
        samplingJob = scope.launch {
            // Sample immediately so the dashboard is populated on arrival, then
            // pace according to the active profile.
            while (isActive) {
                sample()
                evaluate()
                delay(_state.value.profile.memorySampleIntervalMs)
            }
        }
    }

    private suspend fun sample() {
        val memory = withContext(Dispatchers.IO) { memorySampler.sample() }
        val battery = withContext(Dispatchers.IO) { batterySampler.sample() }
        val frames = frameMonitor.stats()
        val lowMemory = memory.valueOrNull()?.systemLowMemory == true

        _state.update {
            it.copy(
                memory = memory,
                battery = battery,
                frameStats = frames,
                underMemoryPressure = lowMemory,
                lastSampleAtMillis = System.currentTimeMillis()
            )
        }
        if (lowMemory != _memoryPressure.value) {
            _memoryPressure.value = lowMemory
        }
    }

    /**
     * Applies the rules and, for any that fire, runs the matching action if it
     * is off cooldown.
     *
     * @see maybeVacuumDiagnostics
     */
    private suspend fun evaluate() {
        if (!prefs.autoOptimizeState()) return
        val now = System.currentTimeMillis()
        val current = _state.value
        val lastRunAt = prefs.lastActionAtState()
        val profile = current.profile

        val ruleContext = Rules.Context(
            profile = profile,
            memory = current.memory.valueOrNull(),
            frameStats = current.frameStats,
            iconCacheEntries = iconCache.size,
            internalCacheBytes = optimizer.internalCacheBytes(),
            autoProfile = !current.profileChosenByUser
        )

        val pressure = Rules.shouldAdaptProfile(ruleContext) != null ||
            ruleContext.memory?.systemLowMemory == true
        if (pressure != _memoryPressure.value) {
            _memoryPressure.value = pressure
            // Stepping the profile down changes the sampling interval and cache
            // ceilings; let the profile observer settle before evaluating again.
        }

        Rules.iconCacheShouldTrim(ruleContext)?.let { reason ->
            runIfAllowed(Optimizer.ACTION_TRIM_ICON_CACHE, Rules.RULE_ICON_CACHE, reason, lastRunAt, now, profile)
        }
        Rules.internalCacheShouldClear(ruleContext)?.let { reason ->
            runIfAllowed(
                Optimizer.ACTION_CLEAR_INTERNAL_CACHE, Rules.RULE_INTERNAL_CACHE,
                reason, lastRunAt, now, profile
            )
        }

        maybeVacuumDiagnostics(now, lastRunAt)

        _state.update { it.copy(lastEvaluationAtMillis = now) }
    }

    /**
     * Periodic housekeeping of ROLEXM's own diagnostics database.
     *
     * This replaces a WorkManager periodic worker. A scheduled worker cost the
     * app four manifest permissions (FOREGROUND_SERVICE, RECEIVE_BOOT_COMPLETED,
     * WAKE_LOCK, ACCESS_NETWORK_STATE) as transitive additions from the
     * WorkManager runtime - a poor trade for a VACUUM, and one that would have
     * contradicted ROLEXM's "request only what is necessary" rule.
     *
     * Doing it here has a second benefit: the work only ever happens while
     * ROLEXM is visible, so it can never run in the background at all. It is
     * skipped under memory pressure, because a disk rebuild is the wrong thing
     * to start when the system is short of memory.
     */
    private suspend fun maybeVacuumDiagnostics(now: Long, lastRunAt: Map<String, Long>) {
        if (_state.value.underMemoryPressure) return
        val cooldown = Cooldown.state(lastRunAt[MAINTENANCE_VACUUM], VACUUM_INTERVAL_MS, now)
        if (!cooldown.canRun) return
        withContext(Dispatchers.IO) { diagnostics.vacuum() }
        prefs.markActionRan(MAINTENANCE_VACUUM, now)
        RolexLog.d("Diagnostics database compacted")
    }

    private suspend fun runIfAllowed(
        actionId: String,
        ruleId: String,
        reason: String,
        lastRunAt: Map<String, Long>,
        now: Long,
        profile: Profile
    ) {
        val cooldown = Cooldown.state(lastRunAt[actionId], profile.autoActionCooldownMs, now)
        if (!cooldown.canRun) {
            RolexLog.d("Action $actionId on cooldown (${cooldown.remainingMillis}ms left)")
            return
        }
        RolexLog.i("Rule $ruleId fired: $reason")
        val result = optimizer.run(actionId, trigger = ruleId, profile = profile)
        RolexLog.i("Action ${result.actionId}: ${result.detail}")
    }

    fun shutdown() {
        frameMonitor.stop()
        samplingJob?.cancel()
        samplingJob = null
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }
}

/**
 * One-shot preference reads.
 *
 * The engine evaluates on a schedule and only needs the value at that instant,
 * so these use first() rather than holding a subscription open for the whole
 * process lifetime.
 */
private suspend fun RolexPrefs.autoOptimizeState(): Boolean = autoOptimize.first()

private suspend fun RolexPrefs.lastActionAtState(): Map<String, Long> = lastActionAt.first()
