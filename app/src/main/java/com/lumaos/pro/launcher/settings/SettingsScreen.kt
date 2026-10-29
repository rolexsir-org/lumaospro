package com.lumaos.pro.launcher.settings

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.context.ProfileSchedule
import com.lumaos.pro.launcher.core.apps.AppMatcher
import com.lumaos.pro.launcher.ui.LumaIcon
import com.lumaos.pro.launcher.ui.LumaSpacing
import com.lumaos.pro.launcher.ui.theme.ExperienceProfile
import com.lumaos.pro.launcher.core.prefs.GestureKind
import com.lumaos.pro.launcher.intelligence.ProfileId
import com.lumaos.pro.launcher.launcher.model.DrawerPresentation
import com.lumaos.pro.launcher.launcher.model.GestureAction
import com.lumaos.pro.launcher.ui.ThemeMode
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.lumaos.pro.launcher.settings.ExperienceProfileController

@Composable
fun SettingsRoot(
    viewModel: SettingsViewModel,
    onRequestHomeRole: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenWallpaper: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            HomeRoleHeader(viewModel, onRequestHomeRole, onOpenHomeSettings)
            Spacer(Modifier.height(24.dp))

            CleanGroupTitle(R.string.settings_group_make_yours)
            CleanSection {
                ExperienceRow(viewModel)
                CleanDivider()
                StudioRow(onOpenStudio)
                CleanDivider()
                WallpaperRow(onOpenWallpaper)
                CleanDivider()
                WidgetsRow(viewModel)
            }
            Spacer(Modifier.height(20.dp))

            CleanGroupTitle(R.string.settings_group_fine_tune)
            CleanSection {
                AppearanceSection(viewModel)
            }
            Spacer(Modifier.height(12.dp))
            CleanSection {
                DrawerSection(viewModel)
            }
            Spacer(Modifier.height(12.dp))
            CleanSection {
                PerformanceSection(viewModel)
            }
            Spacer(Modifier.height(20.dp))

            CleanGroupTitle(R.string.settings_group_advanced)
            var advancedOpen by rememberSaveable { mutableStateOf(false) }
            CleanSection {
                CleanNavigationRow(
                    title = stringResource(if (advancedOpen) R.string.settings_advanced_hide else R.string.settings_advanced_show),
                    subtitle = stringResource(R.string.settings_advanced_summary),
                    onClick = { advancedOpen = !advancedOpen }
                )
            }
            if (advancedOpen) {
                Spacer(Modifier.height(12.dp))
                CleanSection { ContextSection(viewModel, onRequestNotifications) }
                Spacer(Modifier.height(12.dp))
                CleanSection { GestureSection(viewModel) }
                Spacer(Modifier.height(12.dp))
                CleanSection { PrivacySection() }
                Spacer(Modifier.height(12.dp))
                CleanSection { BackupSection(viewModel, onExportBackup, onImportBackup) }
                Spacer(Modifier.height(12.dp))
                CleanSection { AboutSection() }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------- Clean primitives ----------

@Composable
private fun CleanGroupTitle(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp)
    )
}

@Composable
private fun CleanSection(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun CleanDivider() {
    HorizontalDivider(
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun CleanRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    icon: LumaIcon? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val clickable = onClick != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = clickable, onClick = { onClick?.invoke() })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon.res),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (clickable && value != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun CleanNavigationRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    CleanRow(title = title, subtitle = subtitle, onClick = onClick, trailing = {
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f), fontSize = 18.sp)
    })
}

@Composable
private fun CompactSwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}

@Composable
private fun ChoiceRowCompact(
    label: String,
    selected: String,
    options: List<Pair<Any, String>>,
    onSelected: (Any) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = selected,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f), fontSize = 18.sp)
    }
    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
                Column(Modifier.padding(vertical = 8.dp).width(280.dp)) {
                    options.forEach { (value, text) ->
                        val isSelected = text == selectedText
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(value)
                                    expanded = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Home Role ----------

@Composable
private fun HomeRoleHeader(
    viewModel: SettingsViewModel,
    onRequestHomeRole: () -> Unit,
    onOpenHomeSettings: () -> Unit
) {
    val role by viewModel.roleState.collectAsState()
    CleanSection {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(if (role.isDefault) R.string.role_status_default else R.string.role_status_not_default),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.role_explanation),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!role.isDefault && role.currentDefaultLabel != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.role_current_default, role.currentDefaultLabel ?: ""),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!role.isDefault) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.clickable { onRequestHomeRole() }
                    ) {
                        Text(
                            text = stringResource(R.string.role_set_default),
                            style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.clickable { onOpenHomeSettings() }
                ) {
                    Text(
                        text = stringResource(R.string.role_switch_away),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

// ---------- Experience ----------

@Composable
private fun ExperienceRow(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val selected by viewModel.prefs.experienceProfile.collectAsState(ExperienceProfile.DEFAULT)
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.onboarding_profiles_title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = selected.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExperienceProfile.entries.forEach { profile ->
                val isSelected = profile == selected
                val title = when (profile) {
                    ExperienceProfile.PURE -> stringResource(R.string.experience_pure)
                    ExperienceProfile.LIQUID -> stringResource(R.string.experience_liquid)
                    ExperienceProfile.MIDNIGHT -> stringResource(R.string.experience_midnight)
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    tonalElevation = 0.dp,
                    modifier = Modifier.weight(1f).clickable {
                        scope.launch {
                            try { ExperienceProfileController.apply(viewModel.prefs, profile) } catch (e: Exception) {
                                Toast.makeText(context, R.string.appearance_apply_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Column(
                        Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(
                                when (profile) {
                                    ExperienceProfile.PURE -> Color(0xFF7C86FF)
                                    ExperienceProfile.LIQUID -> Color(0xFF46E0B0)
                                    ExperienceProfile.MIDNIGHT -> Color(0xFF1A1C1E)
                                }
                            )
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(text = title, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
                        if (isSelected) {
                            Spacer(Modifier.height(3.dp))
                            Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioRow(onOpenStudio: () -> Unit) {
    CleanRow(
        title = stringResource(R.string.studio_themes),
        subtitle = stringResource(R.string.onboarding_profiles_subtitle),
        onClick = onOpenStudio
    )
}

@Composable
private fun WallpaperRow(onOpenWallpaper: () -> Unit) {
    CleanRow(
        title = stringResource(R.string.wallpaper_studio_title),
        subtitle = stringResource(R.string.wallpaper_discover_caption),
        onClick = onOpenWallpaper
    )
}

@Composable
private fun WidgetsRow(viewModel: SettingsViewModel) {
    val widgetsEnabled by viewModel.prefs.widgetsEnabled.collectAsState(true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    CompactSwitchRow(
        title = stringResource(R.string.pref_widgets),
        subtitle = stringResource(R.string.pref_widgets_desc),
        checked = widgetsEnabled,
        onCheckedChange = { scope.launch { viewModel.prefs.setWidgetsEnabled(it) } }
    )
    CleanDivider()
    CleanRow(
        title = stringResource(R.string.music_widget_name),
        subtitle = stringResource(R.string.music_widget_description),
        onClick = { context.startActivity(Intent(context, com.lumaos.pro.launcher.widgets.music.MusicAccessActivity::class.java)) }
    )
}

// ---------- Appearance ----------

@Composable
private fun AppearanceSection(viewModel: SettingsViewModel) {
    val themeMode by viewModel.prefs.themeMode.collectAsState(ThemeMode.SYSTEM)
    val livingIconsEnabled by viewModel.prefs.livingIconsEnabled.collectAsState(true)
    val livingOverrides by viewModel.prefs.livingIconOverrides.collectAsState(emptyMap())
    val gridColumns by viewModel.prefs.gridColumns.collectAsState(4)
    val gridRows by viewModel.prefs.gridRows.collectAsState(5)
    val drawerColumns by viewModel.prefs.drawerColumns.collectAsState(4)
    val drawerPresentation by viewModel.prefs.drawerPresentation.collectAsState(DrawerPresentation.LIST)
    val iconSizeDp by viewModel.prefs.iconSizeDp.collectAsState(48)
    val showLabels by viewModel.prefs.showLabels.collectAsState(true)
    val textScale by viewModel.prefs.textScale.collectAsState(1.0f)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column {
        CleanSectionHeader(R.string.section_appearance)
        CompactSwitchRow(
            title = stringResource(R.string.pref_living_icons),
            subtitle = stringResource(R.string.pref_living_icons_desc),
            checked = livingIconsEnabled,
            onCheckedChange = { scope.launch { viewModel.prefs.setLivingIconsEnabled(it) } }
        )
        if (livingOverrides.isNotEmpty()) {
            CleanDivider()
            CleanRow(
                title = stringResource(R.string.icon_override_reset_all, livingOverrides.size),
                subtitle = "Reset custom icons",
                onClick = {
                    scope.launch {
                        viewModel.prefs.clearLivingIconOverrides()
                        Toast.makeText(context, R.string.toast_icon_overrides_cleared, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        CleanDivider()
        ChoiceRowCompact(
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
            ).map { it as Pair<Any,String> },
            onSelected = { scope.launch { viewModel.prefs.setThemeMode(it as ThemeMode) } }
        )
        CleanDivider()
        SliderCompact(stringResource(R.string.pref_grid_columns), gridColumns.toFloat(), 3f..7f) {
            scope.launch { viewModel.prefs.setGridColumns(it.roundToInt()) }
        }
        CleanDivider()
        SliderCompact(stringResource(R.string.pref_grid_rows), gridRows.toFloat(), 3f..8f) {
            scope.launch { viewModel.prefs.setGridRows(it.roundToInt()) }
        }
        CleanDivider()
        ChoiceRowCompact(
            label = stringResource(R.string.pref_drawer_presentation),
            selected = stringResource(if (drawerPresentation == DrawerPresentation.LIST) R.string.drawer_presentation_list else R.string.drawer_presentation_grid),
            options = listOf(
                DrawerPresentation.LIST to stringResource(R.string.drawer_presentation_list),
                DrawerPresentation.GRID to stringResource(R.string.drawer_presentation_grid)
            ).map { it as Pair<Any,String> },
            onSelected = { scope.launch { viewModel.prefs.setDrawerPresentation(it as DrawerPresentation) } }
        )
        if (drawerPresentation == DrawerPresentation.GRID) {
            CleanDivider()
            SliderCompact(stringResource(R.string.pref_drawer_columns), drawerColumns.toFloat(), 3f..7f) {
                scope.launch { viewModel.prefs.setDrawerColumns(it.roundToInt()) }
            }
        }
        CleanDivider()
        SliderCompact(stringResource(R.string.pref_icon_size), iconSizeDp.toFloat(), 36f..72f, suffix = " dp") {
            scope.launch { viewModel.prefs.setIconSizeDp(it.roundToInt()) }
        }
        CleanDivider()
        SliderCompact(stringResource(R.string.pref_text_scale), textScale, 0.85f..1.6f, isFloat = true) {
            scope.launch { viewModel.prefs.setTextScale(it) }
        }
        CleanDivider()
        CompactSwitchRow(
            title = stringResource(R.string.pref_show_labels),
            checked = showLabels,
            onCheckedChange = { scope.launch { viewModel.prefs.setShowLabels(it) } }
        )
    }
}

@Composable
private fun CleanSectionHeader(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

// ---------- Drawer ----------

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
        if (!hiddenLock) managerOpen = true else {
            val intent = keyguard?.createConfirmDeviceCredentialIntent(
                context.getString(R.string.hidden_unlock_title),
                context.getString(R.string.pref_hidden_lock_desc)
            )
            if (intent == null) Toast.makeText(context, R.string.hidden_no_lock, Toast.LENGTH_SHORT).show()
            else runCatching { unlockLauncher.launch(intent) }.onFailure { Toast.makeText(context, R.string.hidden_locked, Toast.LENGTH_SHORT).show() }
        }
    }

    Column {
        CleanSectionHeader(R.string.section_drawer)
        CompactSwitchRow(
            title = stringResource(R.string.pref_drawer_categories),
            subtitle = stringResource(R.string.pref_drawer_categories_desc),
            checked = categories,
            onCheckedChange = { scope.launch { viewModel.prefs.setDrawerCategoriesEnabled(it) } }
        )
        CleanDivider()
        CompactSwitchRow(
            title = stringResource(R.string.pref_suggestions),
            subtitle = stringResource(R.string.pref_suggestions_desc),
            checked = suggestions,
            onCheckedChange = { scope.launch { viewModel.prefs.setSuggestionsEnabled(it) } }
        )
        CleanDivider()
        CompactSwitchRow(
            title = stringResource(R.string.pref_hidden_search),
            subtitle = stringResource(if (hiddenLock) R.string.pref_hidden_search_locked else R.string.pref_hidden_search_desc),
            checked = hiddenInSearch && !hiddenLock,
            enabled = !hiddenLock,
            onCheckedChange = { scope.launch { viewModel.prefs.setHiddenInSearch(it) } }
        )
        CleanDivider()
        CompactSwitchRow(
            title = stringResource(R.string.pref_hidden_lock),
            subtitle = stringResource(R.string.pref_hidden_lock_desc),
            checked = hiddenLock,
            onCheckedChange = { enabled ->
                if (enabled && keyguard?.isDeviceSecure != true) Toast.makeText(context, R.string.hidden_no_lock, Toast.LENGTH_SHORT).show()
                else scope.launch {
                    if (enabled) viewModel.prefs.setHiddenInSearch(false)
                    viewModel.prefs.setHiddenLock(enabled)
                }
            }
        )
        CleanDivider()
        CleanRow(
            title = stringResource(R.string.hidden_manage),
            subtitle = stringResource(R.string.hidden_manage_summary, hidden.size, activeProfile.title),
            onClick = openManager
        )
        CleanDivider()
        CleanRow(
            title = stringResource(R.string.pref_clear_history),
            onClick = {
                scope.launch {
                    viewModel.clearLaunchHistory()
                    Toast.makeText(context, R.string.pref_history_cleared, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (managerOpen) {
        Dialog(onDismissRequest = { managerOpen = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.hidden_manage_title, activeProfile.title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small).padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = managerQuery,
                            onValueChange = { managerQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (managerQuery.isEmpty()) Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                                inner()
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val filteredApps = remember(apps, managerQuery) { AppMatcher.filter(apps, managerQuery) }
                    when {
                        appsLoading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                        filteredApps.isEmpty() -> Text(stringResource(R.string.empty_no_apps), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                        else -> LazyColumn(Modifier.heightIn(max = 380.dp)) {
                            items(filteredApps, key = { it.key.serialize() }) { app ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(app.key.packageName, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                    Checkbox(checked = app.key in hidden, onCheckedChange = { checked -> viewModel.setAppHidden(activeProfile, app.key, checked) })
                                }
                                HorizontalDivider(thickness = 0.4.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                    TextButton(onClick = { managerOpen = false }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.edit_done)) }
                }
            }
        }
    }
}

// ---------- Performance ----------

@Composable
private fun PerformanceSection(viewModel: SettingsViewModel) {
    val override by viewModel.prefs.profileOverride.collectAsState(null)
    val autoOptimize by viewModel.prefs.autoOptimize.collectAsState(true)
    val scope = rememberCoroutineScope()
    val capability = viewModel.capability

    Column {
        CleanSectionHeader(R.string.section_performance)
        ChoiceRowCompact(
            label = stringResource(R.string.pref_profile),
            selected = when (override) {
                null -> stringResource(R.string.pref_profile_auto)
                ProfileId.LIGHTWEIGHT -> stringResource(R.string.profile_lightweight)
                ProfileId.BALANCED -> stringResource(R.string.profile_balanced)
                ProfileId.VISUAL_QUALITY -> stringResource(R.string.profile_visual)
                else -> override.name
            },
            options = listOf(
                null to stringResource(R.string.pref_profile_auto),
                ProfileId.LIGHTWEIGHT to stringResource(R.string.profile_lightweight),
                ProfileId.BALANCED to stringResource(R.string.profile_balanced),
                ProfileId.VISUAL_QUALITY to stringResource(R.string.profile_visual)
            ).map { it as Pair<Any,String> },
            onSelected = { scope.launch { viewModel.prefs.setProfileOverride(it as ProfileId?) } }
        )
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(
                text = when (override) {
                    ProfileId.LIGHTWEIGHT -> stringResource(R.string.profile_lightweight_desc)
                    ProfileId.BALANCED -> stringResource(R.string.profile_balanced_desc)
                    ProfileId.VISUAL_QUALITY -> stringResource(R.string.profile_visual_desc)
                    null -> stringResource(R.string.pref_profile_auto_detail, capability.tier.name.lowercase(), capability.totalMemoryMb, capability.cpuCoreCount)
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CleanDivider()
        CompactSwitchRow(
            title = stringResource(R.string.pref_auto_optimize),
            subtitle = stringResource(R.string.pref_auto_optimize_desc),
            checked = autoOptimize,
            onCheckedChange = { scope.launch { viewModel.prefs.setAutoOptimize(it) } }
        )
    }
}

// ---------- Context + Gestures + Privacy etc ----------

@Composable
private fun ContextSection(viewModel: SettingsViewModel, onRequestNotifications: () -> Unit) {
    val active by viewModel.prefs.activeContextProfile.collectAsState(ContextProfile.PERSONAL)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val choices = ContextProfile.entries.map { it to it.title }
    val schedule by viewModel.prefs.profileSchedule(active).collectAsState(ProfileSchedule.defaults(active))
    val alertsEnabled by viewModel.prefs.contextNotificationsEnabled.collectAsState(false)
    val alertProfiles by viewModel.prefs.contextNotificationProfiles.collectAsState(emptySet())
    val dndProfiles by viewModel.prefs.contextDndProfiles.collectAsState(emptySet())
    val hasDndAccess by viewModel.hasDndAccess.collectAsState(false)
    fun openDnd() {
        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            .onFailure { Toast.makeText(context, R.string.dnd_access_unavailable, Toast.LENGTH_LONG).show() }
    }
    Column {
        Text("Contexts", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        ChoiceRowCompact(label = "Current context", selected = active.title, options = choices.map { it as Pair<Any,String> }, onSelected = { viewModel.selectContextProfile(it as ContextProfile) })
        CleanDivider()
        CompactSwitchRow(label = "Automatic schedule", subtitle = "Switch at local-time boundaries", checked = schedule.enabled, onCheckedChange = { viewModel.setSchedule(active, schedule.copy(enabled = it)) })
        if (schedule.enabled) {
            CleanDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactPill(label = "From ${schedule.start}", onClick = {
                    android.app.TimePickerDialog(context, { _, h, m -> viewModel.setSchedule(active, schedule.copy(start = LocalTime.of(h, m))) }, schedule.start.hour, schedule.start.minute, true).show()
                }, modifier = Modifier.weight(1f))
                CompactPill(label = "Until ${schedule.end}", onClick = {
                    android.app.TimePickerDialog(context, { _, h, m -> viewModel.setSchedule(active, schedule.copy(end = LocalTime.of(h, m))) }, schedule.end.hour, schedule.end.minute, true).show()
                }, modifier = Modifier.weight(1f))
            }
            CleanDivider()
            Text("Active days", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val labels = listOf("M","T","W","T","F","S","S")
                DayOfWeek.values().forEachIndexed { index, day ->
                    val selected = day in schedule.days
                    Box(
                        Modifier.weight(1f).height(36.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                val days = if (selected) schedule.days - day else schedule.days + day
                                viewModel.setSchedule(active, schedule.copy(days = days))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(labels[index], style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        CleanDivider()
        CompactSwitchRow(label = "Context alerts", subtitle = "Notify when entering ${active.title}", checked = active in alertProfiles, onCheckedChange = { scope.launch { viewModel.prefs.setContextNotificationEnabled(active, it) } })
        CompactSwitchRow(label = "Do Not Disturb in ${active.title}", checked = active in dndProfiles, onCheckedChange = {
            scope.launch { viewModel.prefs.setContextDndEnabled(active, it) }
            if (it && !hasDndAccess) openDnd()
        })
        if (!hasDndAccess) {
            CleanDivider()
            CleanRow(title = "Allow DND access", subtitle = "Required for automatic DND", onClick = { openDnd() })
        }
        if (!alertsEnabled && Build.VERSION.SDK_INT >= 33) {
            CleanDivider()
            CleanRow(title = "Enable notifications", subtitle = "Allow alerts", onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
                    .onFailure { Toast.makeText(context, R.string.notification_settings_unavailable, Toast.LENGTH_LONG).show() }
            })
        }
        CompactSwitchRow(label = "System notifications", checked = alertsEnabled, onCheckedChange = { if (it) onRequestNotifications() else scope.launch { viewModel.prefs.setContextNotificationsEnabled(false) } })
    }
}

@Composable
private fun CompactPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.clickable { onClick() }) {
        Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun GestureSection(viewModel: SettingsViewModel) {
    val scope = rememberCoroutineScope()
    Column {
        Text("Gestures", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        Text(stringResource(R.string.gesture_note), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        val options = GestureAction.BUILT_IN.map { it to gestureActionLabel(it) }.map { it as Pair<Any,String> }
        for (kind in GestureKind.entries) {
            val current by viewModel.prefs.gesture(kind).collectAsState(kind.default)
            CleanDivider()
            ChoiceRowCompact(
                label = kindLabel(kind),
                selected = options.firstOrNull { (it.first as GestureAction).storageKey == current.storageKey }?.second ?: current.storageKey,
                options = options,
                onSelected = { scope.launch { viewModel.prefs.setGesture(kind, it as GestureAction) } }
            )
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

@Composable
private fun PrivacySection() {
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.section_privacy), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.privacy_summary), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BackupSection(viewModel: SettingsViewModel, onExportBackup: () -> Unit, onImportBackup: () -> Unit) {
    val state by viewModel.backupState.collectAsState()
    Column {
        CleanRow(title = stringResource(R.string.backup_export), subtitle = "Save to JSON file", onClick = { if (!state.busy) onExportBackup() })
        CleanDivider()
        CleanRow(title = stringResource(R.string.backup_import), subtitle = "Restore from file", onClick = { if (!state.busy) onImportBackup() })
        if (state.busy) {
            CleanDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
            }
        }
        state.message?.let {
            CleanDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { viewModel.consumeBackupMessage() }) { Text(stringResource(R.string.action_dismiss)) }
            }
        }
    }
}

@Composable
private fun AboutSection() {
    var showLicenses by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column {
        CleanRow(title = "Version", subtitle = "${com.lumaos.pro.launcher.BuildConfig.VERSION_NAME} (${com.lumaos.pro.launcher.BuildConfig.VERSION_CODE})", onClick = {})
        CleanDivider()
        CleanRow(title = stringResource(R.string.about_fonts_line), onClick = {})
        CleanDivider()
        CleanRow(title = stringResource(R.string.about_licenses_open), onClick = { showLicenses = true })
    }
    if (showLicenses) {
        Dialog(onDismissRequest = { showLicenses = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.about_licenses), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        TextButton(onClick = { showLicenses = false }) { Text("Close") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Inter & Space Grotesk — SIL OFL 1.1", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------- Slider compact ----------

@Composable
private fun SliderCompact(label: String, value: Float, range: ClosedFloatingPointRange<Float>, suffix: String = "", isFloat: Boolean = false, onValueChange: (Float) -> Unit) {
    var sliderValue by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
            Text(if (isFloat) "%.2f×".format(sliderValue) else "${sliderValue.toInt()}$suffix", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(2.dp))
        androidx.compose.material3.Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChange(sliderValue.coerceIn(range.start, range.endInclusive)) },
            valueRange = range,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

