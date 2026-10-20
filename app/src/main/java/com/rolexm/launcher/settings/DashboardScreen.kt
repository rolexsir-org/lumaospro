package com.rolexm.launcher.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rolexm.launcher.R
import com.rolexm.launcher.ui.RolexSpacing
import com.rolexm.launcher.diagnostics.OptimizationEvent
import com.rolexm.launcher.diagnostics.Reading
import com.rolexm.launcher.diagnostics.StartupStats
import com.rolexm.launcher.diagnostics.describeMemory
import com.rolexm.launcher.diagnostics.formatBytes
import com.rolexm.launcher.intelligence.BatterySnapshot
import com.rolexm.launcher.intelligence.FrameStats
import com.rolexm.launcher.intelligence.IntelligenceState
import com.rolexm.launcher.intelligence.StartupTracker
import com.rolexm.launcher.optimizer.OptimizationResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * How a number was obtained, shown next to it so that a reading is never
 * presented without saying where it came from.
 */
private enum class Badge { MEASURED, ESTIMATE, UNAVAILABLE, SETTING }

@Composable
fun DashboardRoot(viewModel: SettingsViewModel) {
    val state by viewModel.intelligenceState.collectAsState()
    val events by viewModel.events.collectAsState()
    val startupStats by viewModel.startupStats.collectAsState()
    val results by viewModel.lastResults.collectAsState()
    val running by viewModel.isRunning.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = RolexSpacing.screenHorizontal,
                vertical = RolexSpacing.screenVertical
            ),
        verticalArrangement = Arrangement.spacedBy(RolexSpacing.sectionGap)
    ) {
        if (statusMessage != null) {
            StatusCard(statusMessage!!) { viewModel.consumeStatusMessage() }
        }
        MeasurementsCard(state, startupStats)
        ProfileCard(state, viewModel)
        ActionsCard(viewModel, running, results)
        HistoryCard(events)
        Spacer(Modifier.height(RolexSpacing.screenEnd))
    }
}

@Composable
private fun MeasurementsCard(state: IntelligenceState, startupStats: StartupStats?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(RolexSpacing.contentPadding)) {
            CardTitle(stringResource(R.string.dash_measurements))

            when (val memory = state.memory) {
                is Reading.Measured -> MetricRow(
                    label = stringResource(R.string.dash_process_memory),
                    value = describeMemory(memory.value),
                    badge = Badge.MEASURED,
                    source = memory.source
                )
                is Reading.Estimated -> MetricRow(
                    stringResource(R.string.dash_process_memory),
                    describeMemory(memory.value), Badge.ESTIMATE, memory.basis
                )
                is Reading.Unavailable -> MetricRow(
                    stringResource(R.string.dash_process_memory), "—", Badge.UNAVAILABLE, memory.reason
                )
            }

            MetricRow(
                label = stringResource(R.string.dash_startup),
                value = startupStats?.let {
                    stringResource(
                        R.string.dash_startup_value,
                        it.latestMs, it.medianMs, it.bestMs, it.sampleCount
                    )
                } ?: stringResource(R.string.dash_startup_none),
                badge = if (startupStats != null) Badge.MEASURED else Badge.UNAVAILABLE,
                source = if (startupStats != null) {
                    StartupTracker.SOURCE
                } else {
                    stringResource(R.string.dash_startup_hint)
                }
            )

            val frames = state.frameStats
            MetricRow(
                label = stringResource(R.string.dash_frames),
                value = if (frames.hasSamples) {
                    stringResource(
                        R.string.dash_frames_value,
                        frames.frameCount, frames.jankPercent, frames.frozenFrames,
                        frames.meanFrameMs, frames.longestFrameMs
                    )
                } else {
                    stringResource(R.string.dash_no_frames)
                },
                badge = if (frames.hasSamples) Badge.MEASURED else Badge.UNAVAILABLE,
                source = frameSourceText(frames, state.profile.frameMonitorEnabled, state.profile.id.name)
            )

            when (val battery = state.battery) {
                is Reading.Measured -> MetricRow(
                    stringResource(R.string.dash_battery),
                    describeBattery(battery.value), Badge.MEASURED, battery.source
                )
                is Reading.Estimated -> MetricRow(
                    stringResource(R.string.dash_battery),
                    describeBattery(battery.value), Badge.ESTIMATE, battery.basis
                )
                is Reading.Unavailable -> MetricRow(
                    stringResource(R.string.dash_battery), "—", Badge.UNAVAILABLE, battery.reason
                )
            }

            MetricRow(
                label = stringResource(R.string.dash_engine),
                value = if (state.active) {
                    stringResource(
                        R.string.dash_engine_active,
                        state.profile.memorySampleIntervalMs / 1000
                    )
                } else {
                    stringResource(R.string.dash_engine_paused)
                },
                badge = Badge.SETTING,
                source = stringResource(R.string.dash_engine_note)
            )
        }
    }
}

@Composable
private fun frameSourceText(frames: FrameStats, instrumentationEnabled: Boolean, profileName: String): String =
    when {
        frames.hasSamples -> stringResource(R.string.dash_frames_source, frames.refreshPeriodMs)
        instrumentationEnabled -> stringResource(R.string.dash_frames_hint_on)
        else -> stringResource(R.string.dash_frames_hint_off, profileName)
    }

@Composable
private fun describeBattery(snapshot: BatterySnapshot): String {
    val charging = when {
        !snapshot.charging -> stringResource(R.string.dash_battery_on_battery)
        snapshot.chargeSource.isNotEmpty() ->
            stringResource(R.string.dash_battery_charging_source, snapshot.chargeSource)
        else -> stringResource(R.string.dash_battery_charging)
    }
    val parts = mutableListOf(
        stringResource(R.string.dash_battery_percent, snapshot.percent),
        charging
    )
    snapshot.currentMicroAmps?.let {
        parts += stringResource(R.string.dash_battery_current, it / 1000)
    }
    return parts.joinToString(" · ")
}

@Composable
private fun ProfileCard(state: IntelligenceState, viewModel: SettingsViewModel) {
    val capability = viewModel.capability
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(RolexSpacing.contentPadding)) {
            CardTitle(stringResource(R.string.dash_profile_card))
            MetricRow(
                label = stringResource(R.string.dash_profile),
                value = state.profile.id.name.lowercase().replace('_', ' '),
                badge = Badge.SETTING,
                source = state.profileReason
            )
            MetricRow(
                label = stringResource(R.string.dash_device),
                value = buildString {
                    append(capability.tier.name).append(" tier · ")
                    append(capability.totalMemoryMb).append(" MB RAM · ")
                    append(capability.cpuCoreCount).append(" cores")
                    if (capability.isLowRamDevice) append(" · low-RAM device")
                },
                badge = Badge.MEASURED,
                source = capability.reasons.joinToString("; ")
            )
        }
    }
}

@Composable
private fun ActionsCard(
    viewModel: SettingsViewModel,
    running: Set<String>,
    results: Map<String, OptimizationResult>
) {
    var confirmReset by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(RolexSpacing.contentPadding)) {
            CardTitle(stringResource(R.string.dash_actions_title))
            Text(
                stringResource(R.string.dash_actions_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            viewModel.actions.forEach { action ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(action.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        action.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.dash_scope, action.scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { viewModel.runAction(action.id) },
                        enabled = action.id !in running
                    ) {
                        Text(
                            if (action.id in running) stringResource(R.string.opt_running)
                            else stringResource(R.string.dash_run_now)
                        )
                    }
                    results[action.id]?.let { result ->
                        Text(
                            result.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.dash_took, result.durationMs) +
                                (if (result.bytesFreed > 0) {
                                    stringResource(R.string.dash_freed, formatBytes(result.bytesFreed))
                                } else "") +
                                (if (result.measuredImprovement) {
                                    stringResource(R.string.dash_pss_improved)
                                } else {
                                    stringResource(R.string.dash_pss_neutral)
                                }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = { confirmReset = true }) {
                Text(stringResource(R.string.opt_emergency_reset))
            }
        }
    }

    if (confirmReset) {
        Dialog(onDismissRequest = { confirmReset = false }) {
            Card {
                Column(Modifier.padding(RolexSpacing.contentPadding)) {
                    Text(
                        stringResource(R.string.dash_reset_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.opt_emergency_reset_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { confirmReset = false }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            confirmReset = false
                            viewModel.emergencyReset()
                        }) { Text(stringResource(R.string.dash_reset_action)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(events: List<OptimizationEvent>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(RolexSpacing.contentPadding)) {
            CardTitle(stringResource(R.string.dash_recent_actions))
            if (events.isEmpty()) {
                Text(
                    stringResource(R.string.dash_no_actions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                events.forEach { event ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                event.actionTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatTime(event.timestampMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            buildString {
                                append(
                                    if (event.completed) stringResource(R.string.dash_completed)
                                    else stringResource(R.string.dash_not_completed)
                                )
                                append(stringResource(R.string.dash_triggered_by, event.trigger))
                                append(stringResource(R.string.dash_duration, event.durationMs))
                                if (event.bytesFreed > 0) {
                                    append(stringResource(R.string.dash_freed, formatBytes(event.bytesFreed)))
                                }
                                event.pssDeltaKb?.let { delta ->
                                    if (delta < 0) {
                                        append(stringResource(R.string.dash_pss_lower, -delta / 1024.0))
                                    } else {
                                        append(stringResource(R.string.dash_pss_higher, delta / 1024.0))
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            event.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(message: String, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Column {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MetricRow(label: String, value: String, badge: Badge, source: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            BadgeChip(badge)
        }
        Text(value, style = MaterialTheme.typography.bodySmall)
        if (!source.isNullOrBlank()) {
            Text(
                source,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BadgeChip(badge: Badge) {
    val (text, color) = when (badge) {
        Badge.MEASURED -> stringResource(R.string.dash_measured) to MaterialTheme.colorScheme.primary
        Badge.ESTIMATE -> stringResource(R.string.dash_estimated) to MaterialTheme.colorScheme.tertiary
        Badge.UNAVAILABLE -> stringResource(R.string.dash_unavailable) to MaterialTheme.colorScheme.error
        Badge.SETTING -> stringResource(R.string.dash_setting) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

/**
 * Built per call rather than cached in a static field: a cached formatter keeps
 * the locale it was created with, so timestamps would stay in the old language
 * after the user changes it. The cost is a few allocations for at most 30 rows
 * on a settings screen, which is negligible.
 */
private fun formatTime(millis: Long): String =
    SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(millis))
