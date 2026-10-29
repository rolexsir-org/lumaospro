package com.lumaos.pro.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.diagnostics.OptimizationEvent
import com.lumaos.pro.launcher.diagnostics.Reading
import com.lumaos.pro.launcher.diagnostics.StartupStats
import com.lumaos.pro.launcher.diagnostics.describeMemory
import com.lumaos.pro.launcher.diagnostics.formatBytes
import com.lumaos.pro.launcher.intelligence.BatterySnapshot
import com.lumaos.pro.launcher.intelligence.FrameStats
import com.lumaos.pro.launcher.intelligence.IntelligenceState
import com.lumaos.pro.launcher.intelligence.StartupTracker
import com.lumaos.pro.launcher.optimizer.OptimizationResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Badge { MEASURED, ESTIMATE, UNAVAILABLE, SETTING }

@Composable
fun DashboardRoot(viewModel: SettingsViewModel) {
    val state by viewModel.intelligenceState.collectAsState()
    val events by viewModel.events.collectAsState()
    val historyLoading by viewModel.historyLoading.collectAsState()
    val startupStats by viewModel.startupStats.collectAsState()
    val results by viewModel.lastResults.collectAsState()
    val running by viewModel.isRunning.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        if (statusMessage != null) {
            CleanCard {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(statusMessage!!, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                    TextButton(onClick = { viewModel.consumeStatusMessage() }) { Text("Dismiss") }
                }
            }
        }

        // Health overview — 2x2 compact grid
        SectionLabel("Health")
        CleanCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HealthTile(
                        title = "Memory",
                        value = when (val m = state.memory) { is Reading.Measured -> describeMemory(m.value); is Reading.Estimated -> describeMemory(m.value); is Reading.Unavailable -> "—" },
                        badge = when (state.memory) { is Reading.Measured -> Badge.MEASURED; is Reading.Estimated -> Badge.ESTIMATE; else -> Badge.UNAVAILABLE },
                        modifier = Modifier.weight(1f)
                    )
                    HealthTile(
                        title = "Startup",
                        value = startupStats?.let { "${it.medianMs} ms" } ?: "—",
                        badge = if (startupStats != null) Badge.MEASURED else Badge.UNAVAILABLE,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HealthTile(
                        title = "Frames",
                        value = if (state.frameStats.hasSamples) "${state.frameStats.frameCount} frames · ${String.format(Locale.US, "%.1f%% jank", state.frameStats.jankPercent)}" else "No samples",
                        badge = if (state.frameStats.hasSamples) Badge.MEASURED else Badge.UNAVAILABLE,
                        modifier = Modifier.weight(1f)
                    )
                    HealthTile(
                        title = "Battery",
                        value = when (val b = state.battery) { is Reading.Measured -> "${b.value.percent}%"; is Reading.Estimated -> "${b.value.percent}%"; is Reading.Unavailable -> "—" },
                        badge = when (state.battery) { is Reading.Measured -> Badge.MEASURED; is Reading.Estimated -> Badge.ESTIMATE; else -> Badge.UNAVAILABLE },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Detailed measurements — expandable
        SectionLabel("Measurements")
        CleanCard {
            var expanded by remember { mutableStateOf(false) }
            Column {
                CompactMetricRow(
                    label = stringResource(R.string.dash_process_memory),
                    value = when (val m = state.memory) { is Reading.Measured -> describeMemory(m.value); is Reading.Estimated -> describeMemory(m.value); is Reading.Unavailable -> "—" },
                    detail = when (val m = state.memory) { is Reading.Measured -> m.source; is Reading.Estimated -> m.basis; is Reading.Unavailable -> m.reason }
                )
                ThinDivider()
                CompactMetricRow(
                    label = stringResource(R.string.dash_startup),
                    value = startupStats?.let { stringResource(R.string.dash_startup_value, it.latestMs, it.medianMs, it.bestMs, it.sampleCount) } ?: stringResource(R.string.dash_startup_none),
                    detail = if (startupStats != null) StartupTracker.SOURCE else stringResource(R.string.dash_startup_hint)
                )
                ThinDivider()
                CompactMetricRow(
                    label = stringResource(R.string.dash_frames),
                    value = if (state.frameStats.hasSamples) stringResource(R.string.dash_frames_value, state.frameStats.frameCount, state.frameStats.jankPercent, state.frameStats.frozenFrames, state.frameStats.meanFrameMs, state.frameStats.longestFrameMs) else stringResource(R.string.dash_no_frames),
                    detail = frameSourceText(state.frameStats, state.profile.frameMonitorEnabled, state.profile.id.name)
                )
                ThinDivider()
                CompactMetricRow(
                    label = stringResource(R.string.dash_battery),
                    value = when (val b = state.battery) { is Reading.Measured -> describeBattery(b.value); is Reading.Estimated -> describeBattery(b.value); is Reading.Unavailable -> "—" },
                    detail = when (val b = state.battery) { is Reading.Measured -> b.source; is Reading.Estimated -> b.basis; is Reading.Unavailable -> b.reason }
                )
                if (expanded) {
                    ThinDivider()
                    CompactMetricRow(label = "Engine", value = if (state.active) "Active · ${state.profile.memorySampleIntervalMs / 1000}s interval" else "Paused", detail = stringResource(R.string.dash_engine_note))
                    ThinDivider()
                    CompactMetricRow(label = stringResource(R.string.dash_profile), value = state.profile.id.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, detail = state.profileReason)
                    ThinDivider()
                    CompactMetricRow(label = "Device", value = stringResource(R.string.dash_device_summary, viewModel.capability.tier.name, viewModel.capability.totalMemoryMb, viewModel.capability.cpuCoreCount), detail = viewModel.capability.reasons.firstOrNull { it.startsWith("classified as") })
                }
                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp), horizontalArrangement = Arrangement.Center) {
                    Text(if (expanded) "Show less" else "Show more", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Maintenance — concise list with trailing action
        SectionLabel("Maintenance")
        CleanCard {
            Column {
                viewModel.actions.forEachIndexed { idx, action ->
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(action.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(action.description, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            results[action.id]?.let {
                                Text(stringResource(R.string.dash_took, it.durationMs) + if (it.bytesFreed > 0) stringResource(R.string.dash_freed, formatBytes(it.bytesFreed)) else "", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable(enabled = action.id !in running) { viewModel.runAction(action.id) }) {
                            Text(if (action.id in running) "Running…" else "Run", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                        }
                    }
                    if (idx != viewModel.actions.lastIndex) ThinDivider()
                }
            }
        }

        // Recent history — grouped 3 distinct + repeat count + Show All
        SectionLabel("Recent activity")
        CleanCard {
            if (events.isEmpty() && historyLoading) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dash_history_loading), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (events.isEmpty()) {
                Box(Modifier.padding(16.dp)) { Text(stringResource(R.string.dash_no_actions), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val grouped = groupEvents(events)
                var showAll by remember { mutableStateOf(false) }
                val toShow = if (showAll) grouped else grouped.take(3)
                Column {
                    toShow.forEachIndexed { idx, g ->
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(g.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    if (g.count > 1) {
                                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                                            Text("×${g.count}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(formatTime(g.latest), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(g.detail, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (idx != toShow.lastIndex) ThinDivider()
                    }
                    if (grouped.size > 3 && !showAll) {
                        ThinDivider()
                        Row(Modifier.fillMaxWidth().clickable { showAll = true }.padding(12.dp), horizontalArrangement = Arrangement.Center) {
                            Text("Show all (${grouped.size})", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (showAll) {
                        ThinDivider()
                        Row(Modifier.fillMaxWidth().clickable { showAll = false }.padding(12.dp), horizontalArrangement = Arrangement.Center) {
                            Text("Show less", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Reset — restrained at bottom
        Spacer(Modifier.height(8.dp))
        CleanCard {
            var confirmReset by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reset to defaults", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    Text(stringResource(R.string.opt_emergency_reset_desc), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.clickable { confirmReset = true }) {
                    Text("Reset", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            if (confirmReset) {
                Dialog(onDismissRequest = { confirmReset = false }) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.dash_reset_title), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.opt_emergency_reset_desc), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
                                Spacer(Modifier.width(6.dp))
                                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable {
                                    confirmReset = false; viewModel.emergencyReset()
                                }) { Text("Reset", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp))
}
@Composable private fun CleanCard(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) { content() }
}
@Composable private fun ThinDivider() { HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 12.dp)) }
@Composable private fun HealthTile(title: String, value: String, badge: Badge, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(when(badge){ Badge.MEASURED->"Measured"; Badge.ESTIMATE->"Estimate"; Badge.UNAVAILABLE->"Unavailable"; Badge.SETTING->"Setting" }, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun CompactMetricRow(label: String, value: String, detail: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class GroupedEvent(val title: String, val detail: String, val trigger: String, val count: Int, val latest: Long)
private fun groupEvents(events: List<OptimizationEvent>): List<GroupedEvent> {
    val map = LinkedHashMap<String, MutableList<OptimizationEvent>>()
    events.forEach { e ->
        val key = "${e.actionTitle}|${e.trigger}"
        map.getOrPut(key) { mutableListOf() }.add(e)
    }
    return map.values.map { list ->
        val first = list.first()
        GroupedEvent(title = first.actionTitle, detail = first.detail, trigger = first.trigger, count = list.size, latest = list.maxOf { it.timestampMillis })
    }.sortedByDescending { it.latest }
}

@Composable private fun describeBattery(snapshot: BatterySnapshot): String {
    val charging = when { !snapshot.charging -> stringResource(R.string.dash_battery_on_battery); snapshot.chargeSource.isNotEmpty() -> stringResource(R.string.dash_battery_charging_source, snapshot.chargeSource); else -> stringResource(R.string.dash_battery_charging) }
    val parts = mutableListOf(stringResource(R.string.dash_battery_percent, snapshot.percent), charging)
    snapshot.currentMicroAmps?.let { parts += stringResource(R.string.dash_battery_current, it / 1000) }
    return parts.joinToString(" · ")
}
@Composable private fun frameSourceText(frames: FrameStats, instrumentationEnabled: Boolean, profileName: String): String = when { frames.hasSamples -> stringResource(R.string.dash_frames_source, frames.refreshPeriodMs); instrumentationEnabled -> stringResource(R.string.dash_frames_hint_on); else -> stringResource(R.string.dash_frames_hint_off, profileName) }
private fun formatTime(millis: Long): String = SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(millis))
