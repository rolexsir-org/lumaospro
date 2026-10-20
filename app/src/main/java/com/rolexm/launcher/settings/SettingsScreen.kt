package com.rolexm.launcher.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.rolexm.launcher.ui.RolexCorners
import com.rolexm.launcher.ui.RolexSpacing
import com.rolexm.launcher.core.prefs.GestureKind
import com.rolexm.launcher.intelligence.ProfileId
import com.rolexm.launcher.launcher.model.GestureAction
import com.rolexm.launcher.ui.ThemeMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * ROLEXM settings.
 *
 * Every control writes straight to DataStore; the home screen observes the same
 * flows, so changes take effect without a restart and without a manual
 * "apply" step.
 */
@Composable
fun SettingsRoot(
    viewModel: SettingsViewModel,
    onRequestHomeRole: () -> Unit,
    onOpenHomeSettings: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
            HomeRoleSection(viewModel, onRequestHomeRole, onOpenHomeSettings)
            AppearanceSection(viewModel)
            GestureSection(viewModel)
            PerformanceSection(viewModel)
            PrivacySection()
            AboutSection()
            Spacer(Modifier.height(RolexSpacing.screenEnd))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(RolexCorners.card)
    ) {
        Column(Modifier.padding(RolexSpacing.cardPadding)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(RolexSpacing.elementGap))
            content()
        }
    }
}

// ---------------------------------------------------------------- home role

@Composable
private fun HomeRoleSection(
    viewModel: SettingsViewModel,
    onRequestHomeRole: () -> Unit,
    onOpenHomeSettings: () -> Unit
) {
    val role by viewModel.roleState.collectAsState()

    SectionCard(stringResource(R.string.section_home_role)) {
        Text(
            text = stringResource(
                if (role.isDefault) R.string.role_status_default else R.string.role_status_not_default
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!role.isDefault && role.currentDefaultLabel != null) {
            Text(
                text = stringResource(R.string.role_current_default, role.currentDefaultLabel ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        if (!role.isDefault) {
            Button(onClick = onRequestHomeRole) {
                Text(stringResource(R.string.role_set_default))
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenHomeSettings) {
            Text(stringResource(R.string.role_switch_away))
        }
        Text(
            text = stringResource(R.string.role_switch_away_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --------------------------------------------------------------- appearance

@Composable
private fun AppearanceSection(viewModel: SettingsViewModel) {
    val themeMode by viewModel.prefs.themeMode.collectAsState(ThemeMode.SYSTEM)
    val gridColumns by viewModel.prefs.gridColumns.collectAsState(4)
    val gridRows by viewModel.prefs.gridRows.collectAsState(5)
    val drawerColumns by viewModel.prefs.drawerColumns.collectAsState(4)
    val iconSizeDp by viewModel.prefs.iconSizeDp.collectAsState(48)
    val showLabels by viewModel.prefs.showLabels.collectAsState(true)
    val textScale by viewModel.prefs.textScale.collectAsState(1.0f)
    val scope = rememberCoroutineScope()

    SectionCard(stringResource(R.string.section_appearance)) {
        ChoiceRow(
            label = stringResource(R.string.pref_theme),
            selected = when (themeMode) {
                ThemeMode.SYSTEM -> stringResource(R.string.pref_theme_system)
                ThemeMode.LIGHT -> stringResource(R.string.pref_theme_light)
                ThemeMode.DARK -> stringResource(R.string.pref_theme_dark)
            },
            options = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.pref_theme_system),
                ThemeMode.LIGHT to stringResource(R.string.pref_theme_light),
                ThemeMode.DARK to stringResource(R.string.pref_theme_dark)
            )
        ) { mode -> scope.launch { viewModel.prefs.setThemeMode(mode) } }

        SliderRow(stringResource(R.string.pref_grid_columns), gridColumns, 3f..7f) {
            scope.launch { viewModel.prefs.setGridColumns(it.roundToInt()) }
        }
        SliderRow(stringResource(R.string.pref_grid_rows), gridRows, 3f..8f) {
            scope.launch { viewModel.prefs.setGridRows(it.roundToInt()) }
        }
        SliderRow(stringResource(R.string.pref_drawer_columns), drawerColumns, 3f..7f) {
            scope.launch { viewModel.prefs.setDrawerColumns(it.roundToInt()) }
        }
        SliderRow(stringResource(R.string.pref_icon_size), iconSizeDp, 36f..72f, suffix = " dp") {
            scope.launch { viewModel.prefs.setIconSizeDp(it.roundToInt()) }
        }
        SliderRow(stringResource(R.string.pref_text_scale), textScale, 0.85f..1.6f, isFloat = true) {
            scope.launch { viewModel.prefs.setTextScale(it) }
        }
        SwitchRow(stringResource(R.string.pref_show_labels), showLabels) {
            scope.launch { viewModel.prefs.setShowLabels(it) }
        }
    }
}

// ----------------------------------------------------------------- gestures

@Composable
private fun GestureSection(viewModel: SettingsViewModel) {
    val scope = rememberCoroutineScope()

    SectionCard(stringResource(R.string.section_gestures)) {
        Text(
            text = stringResource(R.string.gesture_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        val options = listOf(
            GestureAction.None to stringResource(R.string.gesture_none),
            GestureAction.OpenDrawer to stringResource(R.string.gesture_open_drawer),
            GestureAction.OpenSearch to stringResource(R.string.gesture_open_search),
            GestureAction.OpenSettings to stringResource(R.string.gesture_open_settings),
            GestureAction.OpenWallpaperPicker to stringResource(R.string.gesture_open_wallpaper)
        )
        for (kind in GestureKind.entries) {
            val current by viewModel.prefs.gesture(kind).collectAsState(kind.default)
            ChoiceRow(
                label = kindLabel(kind),
                selected = options.firstOrNull { it.first.storageKey == current.storageKey }?.second
                    ?: current.storageKey,
                options = options
            ) { action -> scope.launch { viewModel.prefs.setGesture(kind, action) } }
        }
    }
}

@Composable
private fun kindLabel(kind: GestureKind): String = stringResource(
    when (kind) {
        GestureKind.SWIPE_UP -> R.string.gesture_swipe_up
        GestureKind.SWIPE_DOWN -> R.string.gesture_swipe_down
        GestureKind.DOUBLE_TAP -> R.string.gesture_double_tap
        GestureKind.LONG_PRESS -> R.string.gesture_long_press
    }
)

// -------------------------------------------------------------- performance

@Composable
private fun PerformanceSection(viewModel: SettingsViewModel) {
    val override by viewModel.prefs.profileOverride.collectAsState(null)
    val autoOptimize by viewModel.prefs.autoOptimize.collectAsState(true)
    val scope = rememberCoroutineScope()
    val capability = viewModel.capability

    SectionCard(stringResource(R.string.section_performance)) {
        Text(
            text = stringResource(R.string.profile_scope_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        val choices = listOf(
            null to stringResource(R.string.pref_profile_auto),
            ProfileId.LIGHTWEIGHT to stringResource(R.string.profile_lightweight),
            ProfileId.BALANCED to stringResource(R.string.profile_balanced),
            ProfileId.VISUAL_QUALITY to stringResource(R.string.profile_visual)
        )
        ChoiceRow(
            label = stringResource(R.string.pref_profile),
            selected = choices.firstOrNull { it.first == override }?.second
                ?: stringResource(R.string.pref_profile_auto),
            options = choices
        ) { id -> scope.launch { viewModel.prefs.setProfileOverride(id) } }

        Text(
            text = when (override) {
                ProfileId.LIGHTWEIGHT -> stringResource(R.string.profile_lightweight_desc)
                ProfileId.BALANCED -> stringResource(R.string.profile_balanced_desc)
                ProfileId.VISUAL_QUALITY -> stringResource(R.string.profile_visual_desc)
                null -> stringResource(
                    R.string.pref_profile_auto_detail,
                    capability.tier.name.lowercase(),
                    capability.totalMemoryMb,
                    capability.cpuCoreCount
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))
        SwitchRow(
            label = stringResource(R.string.pref_auto_optimize),
            checked = autoOptimize,
            subtitle = stringResource(R.string.pref_auto_optimize_desc)
        ) { scope.launch { viewModel.prefs.setAutoOptimize(it) } }
    }
}

// ------------------------------------------------------------------ privacy

@Composable
private fun PrivacySection() {
    SectionCard(stringResource(R.string.section_privacy)) {
        Text(
            text = stringResource(R.string.privacy_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AboutSection() {
    SectionCard(stringResource(R.string.section_about)) {
        Text(
            text = stringResource(
                R.string.about_version,
                com.rolexm.launcher.BuildConfig.VERSION_NAME,
                com.rolexm.launcher.BuildConfig.VERSION_CODE
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ------------------------------------------------------------- primitives

@Composable
private fun SliderRow(
    label: String,
    value: Number,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    isFloat: Boolean = false,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isFloat) "%.2f×".format(value.toFloat()) else "${value.toInt()}$suffix",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { next ->
                onValueChange(next.coerceIn(range.start, range.endInclusive))
            },
            valueRange = range
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    selected: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { expanded = true }) { Text(selected) }
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Card {
                Column(Modifier.padding(vertical = 8.dp)) {
                    options.forEach { (value, text) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = text == selected, onClick = {
                                onSelected(value)
                                expanded = false
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
