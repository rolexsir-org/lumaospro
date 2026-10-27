package com.lumaos.pro.launcher.settings

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.context.ProfileSchedule
import com.lumaos.pro.launcher.core.apps.AppMatcher
import com.lumaos.pro.launcher.ui.LumaIcon
import com.lumaos.pro.launcher.ui.glass.LumaGlassChoice
import com.lumaos.pro.launcher.ui.glass.LumaGlassSectionTitle
import com.lumaos.pro.launcher.ui.glass.LumaGlassSlider
import com.lumaos.pro.launcher.ui.glass.LumaGlassSurface
import com.lumaos.pro.launcher.ui.glass.LumaGlassSwitchRow
import com.lumaos.pro.launcher.ui.glass.LumaGlassTextField
import com.lumaos.pro.launcher.ui.theme.ExperienceProfile
import com.lumaos.pro.launcher.ui.theme.GlassCapabilities
import com.lumaos.pro.launcher.ui.theme.GlassGeometry
import com.lumaos.pro.launcher.ui.theme.GlassMaterials
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.LumaSpacing
import com.lumaos.pro.launcher.core.prefs.GestureKind
import com.lumaos.pro.launcher.intelligence.ProfileId
import com.lumaos.pro.launcher.launcher.model.DrawerPresentation
import com.lumaos.pro.launcher.launcher.model.GestureAction
import com.lumaos.pro.launcher.ui.ThemeMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * LumaOS Pro settings.
 *
 * Every control writes straight to DataStore; the home screen observes the same
 * flows, so changes take effect without a restart and without a manual
 * "apply" step.
 */
@Composable
fun SettingsRoot(
    viewModel: SettingsViewModel,
    onRequestHomeRole: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onOpenStudio: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = LumaSpacing.screenHorizontal,
                    vertical = LumaSpacing.screenVertical
                ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // The Home role is the one system-level decision the launcher needs
            // visible immediately, so it stays above the three customization levels.
            HomeRoleSection(viewModel, onRequestHomeRole, onOpenHomeSettings)

            SettingsGroupHeader(R.string.settings_group_make_yours)
            ExperienceProfileSection(viewModel)
            StudioSection(onOpenStudio)
            WidgetsSection(viewModel)

            SettingsGroupHeader(R.string.settings_group_fine_tune)
            AppearanceSection(viewModel)
            DrawerSection(viewModel)
            PerformanceSection(viewModel)

            SettingsGroupHeader(R.string.settings_group_advanced)
            ContextSection(viewModel, onRequestNotifications)
            GestureSection(viewModel)
            PrivacySection()
            BackupSection(viewModel, onExportBackup, onImportBackup)
            AboutSection()
            Spacer(Modifier.height(LumaSpacing.screenEnd))
        }
    }
}

@Composable
private fun SettingsGroupHeader(titleRes: Int) {
    val theme = LocalLumaTheme.current
    LumaGlassSectionTitle(
        theme = theme,
        text = stringResource(titleRes),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LumaSpacing.sectionGap, bottom = LumaSpacing.sm)
    )
}

/**
 * Level 1: the three coordinated experience profiles. Applying one writes only
 * appearance preferences through [ExperienceProfileController], so layouts,
 * widgets and folders are never moved or erased.
 */
@Composable
private fun ExperienceProfileSection(viewModel: SettingsViewModel) {
    val theme = LocalLumaTheme.current
    val selected by viewModel.prefs.experienceProfile.collectAsState(ExperienceProfile.DEFAULT)
    val scope = rememberCoroutineScope()
    SettingsSection(stringResource(R.string.onboarding_profiles_title), LumaIcon.APPEARANCE) {
        ExperienceProfile.entries.forEach { profile ->
            val title = when (profile) {
                ExperienceProfile.PURE -> stringResource(R.string.experience_pure)
                ExperienceProfile.LIQUID -> stringResource(R.string.experience_liquid)
                ExperienceProfile.MIDNIGHT -> stringResource(R.string.experience_midnight)
            }
            val description = when (profile) {
                ExperienceProfile.PURE -> stringResource(R.string.experience_pure_desc)
                ExperienceProfile.LIQUID -> stringResource(R.string.experience_liquid_desc)
                ExperienceProfile.MIDNIGHT -> stringResource(R.string.experience_midnight_desc)
            }
            LumaGlassSurface(
                theme = theme,
                role = GlassRole.SETTINGS_CONTROL,
                selected = profile == selected,
                onClick = { scope.launch { ExperienceProfileController.apply(viewModel.prefs, profile) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = profile == selected
                    }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Level 1 shortcut into the full theme Studio, the single home for deep visual edits. */
@Composable
private fun StudioSection(onOpenStudio: () -> Unit) {
    SettingsSection(stringResource(R.string.studio_themes), LumaIcon.APPEARANCE) {
        LumaGlassChoice(
            theme = LocalLumaTheme.current,
            label = stringResource(R.string.tab_studio),
            selected = false,
            onClick = onOpenStudio,
            caption = stringResource(R.string.onboarding_profiles_subtitle)
        )
    }
}

/** Level 1: widget controls in one approachable place, separate from grid tuning. */
@Composable
private fun WidgetsSection(viewModel: SettingsViewModel) {
    val widgetsEnabled by viewModel.prefs.widgetsEnabled.collectAsState(true)
    val scope = rememberCoroutineScope()
    SettingsSection(stringResource(R.string.section_widgets), LumaIcon.WIDGETS) {
        SwitchRow(
            label = stringResource(R.string.pref_widgets),
            checked = widgetsEnabled,
            subtitle = stringResource(R.string.pref_widgets_desc)
        ) { enabled -> scope.launch { viewModel.prefs.setWidgetsEnabled(enabled) } }
        Text(
            stringResource(R.string.pref_widgets_manage_open),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = LumaSpacing.sm)
        )
    }
}

/**
 * One settings section: a quiet category glyph, a title in the theme's heading
 * voice, its rows, then a divider. The icon is decorative and hidden from
 * accessibility, because the visible title already names the section.
 */
@Composable
private fun SettingsSection(
    title: String,
    icon: LumaIcon,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalLumaTheme.current
    val spec = GlassMaterials.palette(theme, GlassCapabilities.SOLID)[GlassRole.SECONDARY]
    val contentColor = Color(spec.rest.content)
    Column(Modifier.fillMaxWidth().padding(bottom = LumaSpacing.sectionGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(icon.res),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
        }
        Spacer(Modifier.height(LumaSpacing.md))
        LumaGlassSurface(theme, GlassRole.SECONDARY, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(GlassGeometry.GUTTER_DP.dp)) {
                content()
            }
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

    SettingsSection(stringResource(R.string.section_home_role), LumaIcon.HOME) {
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

// --------------------------------------------------------- context profiles

@Composable
private fun ContextSection(viewModel: SettingsViewModel, onRequestNotifications: () -> Unit) {
    val active by viewModel.prefs.activeContextProfile.collectAsState(ContextProfile.PERSONAL)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val choices = ContextProfile.entries.map { it to it.title }
    val schedule by viewModel.prefs.profileSchedule(active)
        .collectAsState(ProfileSchedule.defaults(active))
    val alertsEnabled by viewModel.prefs.contextNotificationsEnabled.collectAsState(false)
    val alertProfiles by viewModel.prefs.contextNotificationProfiles.collectAsState(emptySet())
    val dndProfiles by viewModel.prefs.contextDndProfiles.collectAsState(emptySet())
    val hasDndAccess by viewModel.hasDndAccess.collectAsState(false)
    fun openDndAccessSettings() {
        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            .onFailure {
                Toast.makeText(context, R.string.dnd_access_unavailable, Toast.LENGTH_LONG).show()
            }
    }

    SettingsSection(stringResource(R.string.section_context_profiles), LumaIcon.CONTEXT) {
        Text(
            "Each profile has its own home layout and visible apps. Long-press icons on the home screen to arrange the selected profile.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ChoiceRow(
            label = "Current context",
            selected = active.title,
            options = choices,
            onSelected = viewModel::selectContextProfile
        )

        SwitchRow(
            label = "Automatic schedule",
            checked = schedule.enabled,
            subtitle = "Switch at local-time boundaries; schedules run without periodic polling."
        ) { enabled -> viewModel.setSchedule(active, schedule.copy(enabled = enabled)) }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = {
                val now = schedule.start
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute -> viewModel.setSchedule(active, schedule.copy(start = LocalTime.of(hour, minute))) },
                    now.hour, now.minute, true
                ).show()
            }) { Text("From ${schedule.start}") }
            OutlinedButton(onClick = {
                val now = schedule.end
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute -> viewModel.setSchedule(active, schedule.copy(end = LocalTime.of(hour, minute))) },
                    now.hour, now.minute, true
                ).show()
            }) { Text("Until ${schedule.end}") }
        }
        Text("Active days", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val labels = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
            DayOfWeek.values().forEachIndexed { index, day ->
                val selected = day in schedule.days
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            val days = if (selected) schedule.days - day else schedule.days + day
                            viewModel.setSchedule(active, schedule.copy(days = days))
                        }
                        .semantics {
                            contentDescription = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
                            role = Role.Checkbox
                            this.selected = selected
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        labels[index],
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

    }

    SettingsSection(stringResource(R.string.section_notifications), LumaIcon.PRIVACY) {
        SwitchRow(
            label = "Context change alerts",
            checked = alertsEnabled,
            subtitle = "LumaOS Pro notifications only; this does not control notifications from other apps."
        ) { enabled ->
            if (enabled) onRequestNotifications()
            else scope.launch { viewModel.prefs.setContextNotificationsEnabled(false) }
        }
        if (!alertsEnabled && Build.VERSION.SDK_INT >= 33) {
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }.onFailure { Toast.makeText(context, R.string.notification_settings_unavailable, Toast.LENGTH_LONG).show() }
            }) { Text("Manage notification permission") }
        }
        SwitchRow(
            label = "Alert when entering ${active.title}",
            checked = active in alertProfiles,
            subtitle = "Per-profile notification preference"
        ) { enabled -> scope.launch { viewModel.prefs.setContextNotificationEnabled(active, enabled) } }
        SwitchRow(
            label = "Do Not Disturb in ${active.title}",
            checked = active in dndProfiles,
            subtitle = "Uses Android Notification Policy Access; restores the prior filter on exit."
        ) { enabled ->
            scope.launch { viewModel.prefs.setContextDndEnabled(active, enabled) }
            if (enabled && !hasDndAccess) {
                openDndAccessSettings()
            }
        }
        if (!hasDndAccess) {
            OutlinedButton(onClick = { openDndAccessSettings() }) { Text("Allow Do Not Disturb access") }
        }
    }
}

// --------------------------------------------------------------- appearance

@Composable
private fun AppearanceSection(viewModel: SettingsViewModel) {
    val themeMode by viewModel.prefs.themeMode.collectAsState(ThemeMode.SYSTEM)
    val gridColumns by viewModel.prefs.gridColumns.collectAsState(4)
    val gridRows by viewModel.prefs.gridRows.collectAsState(5)
    val drawerColumns by viewModel.prefs.drawerColumns.collectAsState(4)
    val drawerPresentation by viewModel.prefs.drawerPresentation.collectAsState(DrawerPresentation.LIST)
    val iconSizeDp by viewModel.prefs.iconSizeDp.collectAsState(48)
    val showLabels by viewModel.prefs.showLabels.collectAsState(true)
    val textScale by viewModel.prefs.textScale.collectAsState(1.0f)
    val scope = rememberCoroutineScope()

    SettingsSection(stringResource(R.string.section_appearance), LumaIcon.APPEARANCE) {
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
        ChoiceRow(
            label = stringResource(R.string.pref_drawer_presentation),
            selected = stringResource(
                if (drawerPresentation == DrawerPresentation.LIST) R.string.drawer_presentation_list
                else R.string.drawer_presentation_grid
            ),
            options = listOf(
                DrawerPresentation.LIST to stringResource(R.string.drawer_presentation_list),
                DrawerPresentation.GRID to stringResource(R.string.drawer_presentation_grid)
            )
        ) { scope.launch { viewModel.prefs.setDrawerPresentation(it) } }
        if (drawerPresentation == DrawerPresentation.GRID) {
            SliderRow(stringResource(R.string.pref_drawer_columns), drawerColumns, 3f..7f) {
                scope.launch { viewModel.prefs.setDrawerColumns(it.roundToInt()) }
            }
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

// ------------------------------------------------------------- apps & search

@Composable
private fun DrawerSection(viewModel: SettingsViewModel) {
    val categories by viewModel.prefs.drawerCategoriesEnabled.collectAsState(true)
    val suggestions by viewModel.prefs.suggestionsEnabled.collectAsState(true)
    val hiddenInSearch by viewModel.prefs.hiddenInSearch.collectAsState(false)
    val hiddenLock by viewModel.prefs.hiddenLock.collectAsState(false)
    val activeProfile by viewModel.prefs.activeContextProfile.collectAsState(ContextProfile.PERSONAL)
    val hidden by remember(activeProfile) { viewModel.prefs.hiddenApps(activeProfile) }.collectAsState(emptySet())
    val apps by viewModel.apps.collectAsState()
    val appsLoading by viewModel.appsLoading.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyguard = remember { context.getSystemService(KeyguardManager::class.java) }
    var managerOpen by remember { mutableStateOf(false) }
    var managerQuery by remember { mutableStateOf("") }

    val unlockLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) managerOpen = true
    }
    val openManager: () -> Unit = {
        managerQuery = ""
        if (!hiddenLock) {
            managerOpen = true
        } else {
            val intent = keyguard?.createConfirmDeviceCredentialIntent(
                context.getString(R.string.hidden_unlock_title),
                context.getString(R.string.pref_hidden_lock_desc)
            )
            if (intent == null) {
                Toast.makeText(context, R.string.hidden_no_lock, Toast.LENGTH_SHORT).show()
            } else {
                runCatching { unlockLauncher.launch(intent) }.onFailure {
                    Toast.makeText(context, R.string.hidden_locked, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    SettingsSection(stringResource(R.string.section_drawer), LumaIcon.DRAWER) {
        Text(
            stringResource(R.string.pref_pinned_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SwitchRow(
            label = stringResource(R.string.pref_drawer_categories),
            checked = categories,
            subtitle = stringResource(R.string.pref_drawer_categories_desc)
        ) { scope.launch { viewModel.prefs.setDrawerCategoriesEnabled(it) } }
        SwitchRow(
            label = stringResource(R.string.pref_suggestions),
            checked = suggestions,
            subtitle = stringResource(R.string.pref_suggestions_desc)
        ) { scope.launch { viewModel.prefs.setSuggestionsEnabled(it) } }
        SwitchRow(
            label = stringResource(R.string.pref_hidden_search),
            checked = hiddenInSearch && !hiddenLock,
            enabled = !hiddenLock,
            subtitle = stringResource(
                if (hiddenLock) R.string.pref_hidden_search_locked else R.string.pref_hidden_search_desc
            )
        ) { scope.launch { viewModel.prefs.setHiddenInSearch(it) } }
        SwitchRow(
            label = stringResource(R.string.pref_hidden_lock),
            checked = hiddenLock,
            subtitle = stringResource(R.string.pref_hidden_lock_desc)
        ) { enabled ->
            if (enabled && keyguard?.isDeviceSecure != true) {
                Toast.makeText(context, R.string.hidden_no_lock, Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    if (enabled) viewModel.prefs.setHiddenInSearch(false)
                    viewModel.prefs.setHiddenLock(enabled)
                }
            }
        }
        Text(
            stringResource(R.string.hidden_manage_summary, hidden.size, activeProfile.title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = openManager, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.hidden_manage))
        }
        OutlinedButton(onClick = {
            scope.launch {
                viewModel.clearLaunchHistory()
                Toast.makeText(context, R.string.pref_history_cleared, Toast.LENGTH_SHORT).show()
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pref_clear_history))
        }
    }

    if (managerOpen) {
        Dialog(onDismissRequest = { managerOpen = false }) {
            GlassDialogSurface {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        stringResource(R.string.hidden_manage_title, activeProfile.title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    LumaGlassTextField(
                        value = managerQuery,
                        onValueChange = { managerQuery = it },
                        theme = LocalLumaTheme.current,
                        placeholder = stringResource(R.string.search_hint),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    val filteredApps = remember(apps, managerQuery) { AppMatcher.filter(apps, managerQuery) }
                    when {
                        appsLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        filteredApps.isEmpty() -> Text(
                            stringResource(R.string.empty_no_apps),
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            items(filteredApps, key = { it.key.serialize() }) { app ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            app.key.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Checkbox(
                                        checked = app.key in hidden,
                                        onCheckedChange = { checked ->
                                            viewModel.setAppHidden(activeProfile, app.key, checked)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { managerOpen = false },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(stringResource(R.string.edit_done)) }
                }
            }
        }
    }
}

// ----------------------------------------------------------------- gestures

@Composable
private fun GestureSection(viewModel: SettingsViewModel) {
    val scope = rememberCoroutineScope()

    SettingsSection(stringResource(R.string.section_gestures), LumaIcon.GESTURES) {
        Text(
            text = stringResource(R.string.gesture_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        val options = GestureAction.BUILT_IN.map { it to gestureActionLabel(it) }
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
        GestureKind.SWIPE_LEFT -> R.string.gesture_swipe_left
        GestureKind.SWIPE_RIGHT -> R.string.gesture_swipe_right
        GestureKind.PINCH_IN -> R.string.gesture_pinch_in
    }
)

@Composable
private fun gestureActionLabel(action: GestureAction): String = stringResource(
    when (action) {
        GestureAction.None -> R.string.gesture_none
        GestureAction.OpenDrawer -> R.string.gesture_open_drawer
        GestureAction.OpenSearch -> R.string.gesture_open_search
        GestureAction.OpenSettings -> R.string.gesture_open_settings
        GestureAction.OpenWallpaperPicker -> R.string.gesture_open_wallpaper
        GestureAction.EnterEditMode -> R.string.gesture_edit_mode
        GestureAction.OpenStudio -> R.string.gesture_open_studio
        GestureAction.NextContextProfile -> R.string.gesture_next_context
        GestureAction.VoiceCommand -> R.string.gesture_voice
        is GestureAction.OpenApp -> R.string.gesture_open_app
    }
)

// -------------------------------------------------------------- performance

@Composable
private fun PerformanceSection(viewModel: SettingsViewModel) {
    val override by viewModel.prefs.profileOverride.collectAsState(null)
    val autoOptimize by viewModel.prefs.autoOptimize.collectAsState(true)
    val scope = rememberCoroutineScope()
    val capability = viewModel.capability

    SettingsSection(stringResource(R.string.section_performance), LumaIcon.PERFORMANCE) {
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
    SettingsSection(stringResource(R.string.section_privacy), LumaIcon.PRIVACY) {
        Text(
            text = stringResource(R.string.privacy_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BackupSection(
    viewModel: SettingsViewModel,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    val state by viewModel.backupState.collectAsState()
    SettingsSection(stringResource(R.string.section_backup), LumaIcon.BACKUP) {
        Text(
            text = stringResource(R.string.backup_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onExportBackup,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.backup_export)) }
        OutlinedButton(
            onClick = onImportBackup,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.backup_import)) }
        if (state.busy) {
            Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.backup_working),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        state.message?.let { message ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message,
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { viewModel.consumeBackupMessage() }) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
        }
    }
}

@Composable
private fun AboutSection() {
    var showLicenses by remember { mutableStateOf(false) }
    val context = LocalContext.current
    SettingsSection(stringResource(R.string.section_about), LumaIcon.INFO) {
        Text(
            text = stringResource(
                R.string.about_version,
                com.lumaos.pro.launcher.BuildConfig.VERSION_NAME,
                com.lumaos.pro.launcher.BuildConfig.VERSION_CODE
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(LumaSpacing.sm))
        Text(
            text = stringResource(R.string.about_fonts_line),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { showLicenses = true }) {
            Text(stringResource(R.string.about_licenses_open))
        }
    }
    if (showLicenses) {
        // The bundled licences ship in assets; showing them satisfies the OFL
        // attribution requirement without a network call or a second screen.
        // Read off the main thread: even a few KB of asset I/O during a scroll
        // would show up as a dropped frame on the devices this launcher targets.
        val text by produceState("") {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                listOf("licenses/INTER-OFL.txt", "licenses/SPACE-GROTESK-OFL.txt").joinToString("\n\n") { name ->
                    runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }
                        .getOrDefault("")
                }
            }
        }
        Dialog(onDismissRequest = { showLicenses = false }) {
            GlassDialogSurface {
                Column(Modifier.padding(LumaSpacing.contentPadding)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.about_licenses),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = { showLicenses = false }) {
                            Text(stringResource(R.string.about_licenses_close))
                        }
                    }
                    Spacer(Modifier.height(LumaSpacing.sm))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------- primitives

/** One glass surface for every settings dialog. */
@Composable
private fun GlassDialogSurface(content: @Composable BoxScope.() -> Unit) {
    val theme = LocalLumaTheme.current
    LumaGlassSurface(theme, GlassRole.MENU, Modifier.fillMaxWidth()) {
        content()
    }
}


@Composable
private fun SliderRow(
    label: String,
    value: Number,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    isFloat: Boolean = false,
    onValueChange: (Float) -> Unit
) {
    val theme = LocalLumaTheme.current
    val valueLabel = if (isFloat) "%.2f×".format(value.toFloat()) else "${value.toInt()}$suffix"
    LumaGlassSlider(
        label = label,
        value = value.toFloat(),
        onValueChange = { next -> onValueChange(next.coerceIn(range.start, range.endInclusive)) },
        theme = theme,
        valueRange = range,
        valueLabel = valueLabel
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val theme = LocalLumaTheme.current
    LumaGlassSwitchRow(
        label = label,
        checked = checked,
        onCheckedChange = onCheckedChange,
        theme = theme,
        subtitle = subtitle,
        enabled = enabled
    )
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    selected: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit
) {
    val theme = LocalLumaTheme.current
    var expanded by remember { mutableStateOf(false) }
    val selectedText = selected

    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(GlassMaterials.palette(theme, GlassCapabilities.SOLID)[GlassRole.SETTINGS_CONTROL].rest.content),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        LumaGlassChoice(
            theme = theme,
            label = selected,
            selected = false,
            onClick = { expanded = true },
            modifier = Modifier.widthIn(max = 164.dp)
        )
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            LumaGlassSurface(theme, GlassRole.MENU, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    options.forEach { (value, text) ->
                        LumaGlassChoice(
                            theme = theme,
                            label = text,
                            selected = text == selectedText,
                            onClick = {
                                onSelected(value)
                                expanded = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
