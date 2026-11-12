package com.lumaos.pro.launcher.settings

import android.app.Activity
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.onFocusChanged
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
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.settings.studio.rememberShippedTheme
import com.lumaos.pro.launcher.settings.studio.PremiumThemeCard
import com.lumaos.pro.launcher.ui.theme.LumaDesignTokens
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import com.lumaos.pro.launcher.context.ContextEngine
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.context.FocusAccess
import com.lumaos.pro.launcher.context.ProfileSchedule
import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.wallpaper.WallpaperActivity
import com.lumaos.pro.launcher.ui.LumaIcon
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
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontFamily
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemeLauncherPackage
import com.lumaos.pro.launcher.ui.theme.ThemePresets

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
            Spacer(Modifier.height(20.dp))
            ActiveThemeBanner(viewModel = viewModel, onOpenStudio = onOpenStudio)
            Spacer(Modifier.height(32.dp))
            HomeRoleHeader(viewModel, onRequestHomeRole, onOpenHomeSettings)
            Spacer(Modifier.height(32.dp))

            CleanGroupTitle(R.string.settings_group_make_yours)
            CleanSection {
                WallpaperRow(onOpenWallpaper)
                CleanDivider()
                WidgetsRow(viewModel)
            }
            Spacer(Modifier.height(32.dp))

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
            Spacer(Modifier.height(32.dp))

            CleanGroupTitle(R.string.settings_group_private)
            CleanSection { VaultRow() }
            Spacer(Modifier.height(32.dp))

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
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun CleanSection(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        content()
    }
}

@Composable
private fun CleanDivider() {
    HorizontalDivider(
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 16.dp)
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
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.hasFocus }
            .background(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .heightIn(min = 56.dp)
            .clickable(enabled = clickable, onClick = { onClick?.invoke() })
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
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
                modifier = Modifier.padding(start = 8.dp).widthIn(max = 112.dp)
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
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
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
    var focused by remember { mutableStateOf(false) }
    val selectedText = selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.hasFocus }
            .background(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .heightIn(min = 56.dp)
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f), maxLines = 2
        )
        Text(
            text = selected,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 6.dp).widthIn(max = 120.dp)
        )
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f), fontSize = 18.sp)
    }
    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
                Column(Modifier.padding(vertical = 8.dp).width(280.dp)
                    .heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
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
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.section_home_role), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape)
                .background(if (role.isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (role.isDefault) R.string.role_status_default else R.string.role_status_not_default),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.role_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!role.isDefault && role.currentDefaultLabel != null) {
                    Text(
                        text = stringResource(R.string.role_current_default, role.currentDefaultLabel ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // Full-width actions remain discoverable on narrow phones and at large
        // display/font sizes; a horizontally clipped second button does not.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!role.isDefault) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable { onRequestHomeRole() }
                ) {
                    Text(
                        text = stringResource(R.string.role_set_default),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable { onOpenHomeSettings() }
            ) {
                Text(
                    text = stringResource(R.string.role_switch_away),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

// ---------- Active Theme Banner (v3.2 Reference) ----------

/**
 * The theme hub at the top of Settings: the active identity, the premium trio
 * as live miniatures (one tap applies the full launcher package), the other
 * identities as quiet chips, and the way into Studio for previewing and
 * fine-tuning before applying.
 */
@Composable
private fun ActiveThemeBanner(
    viewModel: SettingsViewModel,
    onOpenStudio: () -> Unit
) {
    val context = LocalContext.current
    val resolved by viewModel.themes.theme.collectAsState()
    val environment by viewModel.themes.environment.collectAsState()
    val pkg = remember(resolved.id) { ThemeLauncherPackage.forTheme(resolved.id) }
    val scope = rememberCoroutineScope()
    fun apply(id: com.lumaos.pro.launcher.ui.theme.ThemeId) {
        scope.launch {
            try {
                viewModel.prefs.applyThemePackage(id)
            } catch (e: Exception) {
                Toast.makeText(context, R.string.appearance_apply_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_active_identity).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = resolved.displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = pkg.subtitle + "  ·  " + pkg.effectsSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(LumaDesignTokens.SP_5.dp))

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(LumaDesignTokens.SP_4.dp)
            ) {
                ThemePresets.PREMIUM.forEach { spec ->
                    PremiumThemeCard(
                        spec = spec,
                        theme = rememberShippedTheme(spec, environment),
                        selected = resolved.id == spec.id,
                        miniatureHeight = 168.dp,
                        modifier = Modifier.width(132.dp),
                        onClick = { apply(spec.id) }
                    )
                }
            }

            Spacer(Modifier.height(LumaDesignTokens.SP_5.dp))

            // Every other identity, one tap away, without competing with the trio.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(LumaDesignTokens.SP_3.dp)
            ) {
                (ThemePresets.SIGNATURE + ThemePresets.SCREENSHOT).forEach { spec ->
                    val isSelected = resolved.id == spec.id
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { apply(spec.id) }
                            .semantics {
                                role = Role.RadioButton
                                selected = isSelected
                            }
                    ) {
                        Row(
                            Modifier.padding(horizontal = LumaDesignTokens.SP_4.dp, vertical = LumaDesignTokens.SP_3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(spec.accent))
                            )
                            Spacer(Modifier.width(LumaDesignTokens.SP_3.dp))
                            Text(
                                text = spec.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(LumaDesignTokens.SP_5.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().clickable { onOpenStudio() }
                    .padding(vertical = LumaDesignTokens.SP_5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.gesture_open_studio),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(R.drawable.ic_rx_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
    }
}

/**
 * The Launcher's entry point into LumaOS Vault.
 *
 * Vault is part of this app, not a second one: it opens here, in the launcher's
 * own settings, and every Vault screen is unexported so nothing else on the device
 * can launch it. The lock screen it opens authenticates before anything is
 * decrypted.
 */
@Composable
private fun VaultRow() {
    val context = LocalContext.current
    CleanNavigationRow(
        title = stringResource(R.string.vault_entry_title),
        subtitle = stringResource(R.string.vault_entry_subtitle),
        onClick = {
            // Compose rows are hosted by an activity, not an Activity context, so a
            // new task is mandatory for the Vault's own task. `VaultLockActivity.intent`
            // supplies it, and both Vault entry points therefore behave identically.
            runCatching {
                context.startActivity(com.lumaos.pro.launcher.vault.ui.VaultLockActivity.intent(context))
            }.onFailure {
                Toast.makeText(context, R.string.vault_unavailable, Toast.LENGTH_LONG).show()
            }
        }
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
        // Same reason as the Vault row above: this context is not an Activity, so
        // the launch needs its own task or the platform raises.
        onClick = {
            runCatching {
                context.startActivity(
                    Intent(context, com.lumaos.pro.launcher.widgets.music.MusicAccessActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { Toast.makeText(context, R.string.screen_unavailable, Toast.LENGTH_LONG).show() }
        }
    )
}

// ---------- Appearance ----------

@Composable
private fun AppearanceSection(viewModel: SettingsViewModel) {
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
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

// ---------- Drawer ----------

@Composable
private fun DrawerSection(viewModel: SettingsViewModel) {
    val categories by viewModel.prefs.drawerCategoriesEnabled.collectAsState(true)
    val suggestions by viewModel.prefs.suggestionsEnabled.collectAsState(true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
        // Hide Apps used to live here as a dialog with its own checkbox list and its
        // own screen-lock prompt. Both are gone: hiding is a Vault operation, stored
        // in the Vault's encrypted index, and this row only opens that section. The
        // old per-profile lists are imported into the Vault on first unlock, and the
        // note explains the move so it does not look like the selection was lost.
        CleanRow(
            title = stringResource(R.string.hidden_moved_title),
            subtitle = stringResource(R.string.hidden_moved_summary),
            onClick = { openVaultHiddenApps(context) }
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
}

/**
 * Opens the Vault's Hidden Apps section.
 *
 * The Vault is a separate task, so `NEW_TASK` is required here: without it the
 * activity manager can throw, which `runCatching` turned into a silent no-op tap.
 */
private fun openVaultHiddenApps(context: Context) {
    runCatching {
        context.startActivity(
            com.lumaos.pro.launcher.vault.ui.VaultLockActivity.intent(
                context,
                com.lumaos.pro.launcher.vault.ui.VaultLockActivity.DESTINATION_HIDDEN_APPS
            )
        )
    }.onFailure {
        Toast.makeText(context, R.string.vault_unavailable, Toast.LENGTH_LONG).show()
    }
}

// ---------- Performance ----------

@Composable
private fun PerformanceSection(viewModel: SettingsViewModel) {
    val override by viewModel.prefs.profileOverride.collectAsState(null)
    // Delegated property reads have a custom getter, so they cannot be smart-cast
    // inside a `when` branch. Keep one snapshot of the current value instead.
    val overrideId: ProfileId? = override
    val autoOptimize by viewModel.prefs.autoOptimize.collectAsState(true)
    val scope = rememberCoroutineScope()
    val capability = viewModel.capability

    Column {
        CleanSectionHeader(R.string.section_performance)
        ChoiceRowCompact(
            label = stringResource(R.string.pref_profile),
            selected = when (overrideId) {
                null -> stringResource(R.string.pref_profile_auto)
                ProfileId.LIGHTWEIGHT -> stringResource(R.string.profile_lightweight)
                ProfileId.BALANCED -> stringResource(R.string.profile_balanced)
                ProfileId.VISUAL_QUALITY -> stringResource(R.string.profile_visual)
                else -> overrideId.name
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
                text = when (overrideId) {
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
    // Which profile the editors below configure. Defaults to the active one, but
    // configuring a context must not require switching to it first. Plain
    // remember (enums are not saveable); following the active context on
    // switch is the desired behaviour anyway.
    var target by remember(active) { mutableStateOf(active) }
    val schedule by viewModel.prefs.profileSchedule(target).collectAsState(ProfileSchedule.defaults(target))
    val storedOverride by viewModel.manualOverride.collectAsState()
    val liveOverride = storedOverride?.takeIf { it.isActive(System.currentTimeMillis()) }
    val installedApps by viewModel.installedApps.collectAsState()
    val hiddenApps by viewModel.prefs.drawerHidden(target).collectAsState(emptySet())
    val focusOn by viewModel.prefs.focusEnabled(target).collectAsState(false)
    val restrictedApps by viewModel.prefs.focusRestricted(target).collectAsState(emptySet())
    val focusServiceOn by viewModel.focusServiceOn.collectAsState()
    val unlockMinutes by viewModel.prefs.focusUnlockMinutes().collectAsState(5)
    var appsDialog by remember { mutableStateOf(false) }
    var focusDialog by remember { mutableStateOf(false) }
    var accessDialog by remember { mutableStateOf(false) }
    val alertsEnabled by viewModel.prefs.contextNotificationsEnabled.collectAsState(false)
    val alertProfiles by viewModel.prefs.contextNotificationProfiles.collectAsState(emptySet())
    val dndProfiles by viewModel.prefs.contextDndProfiles.collectAsState(emptySet())
    val hasDndAccess by viewModel.hasDndAccess.collectAsState(false)
    fun openDnd() {
        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            .onFailure { Toast.makeText(context, R.string.dnd_access_unavailable, Toast.LENGTH_LONG).show() }
    }
    fun openWallpaper() {
        runCatching {
            context.startActivity(
                Intent(context, WallpaperActivity::class.java)
                    .putExtra(WallpaperActivity.EXTRA_TARGET_CONTEXT, target.name)
            )
        }.onFailure { Toast.makeText(context, R.string.screen_unavailable, Toast.LENGTH_LONG).show() }
    }
    // The accessibility grant lives in system settings: LumaOS Pro can only
    // explain first (disclosure dialog) and then route there.
    fun openAccessSettings() {
        val intent = FocusAccess.settingsIntent(context)
        if (intent == null) {
            Toast.makeText(context, R.string.screen_unavailable, Toast.LENGTH_LONG).show()
        } else {
            runCatching { context.startActivity(intent) }
                .onFailure { Toast.makeText(context, R.string.screen_unavailable, Toast.LENGTH_LONG).show() }
        }
    }
    Column {
        Text("Contexts", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        ChoiceRowCompact(label = "Current context", selected = active.title, options = choices.map { it as Pair<Any,String> }, onSelected = { viewModel.selectContextProfile(it as ContextProfile) })
        if (liveOverride != null) {
            CleanDivider()
            val held = liveOverride.profile.title
            val until = if (liveOverride.untilMillis == Long.MAX_VALUE) {
                stringResource(R.string.context_override_indefinite, held)
            } else {
                val time = LocalTime.ofInstant(
                    java.time.Instant.ofEpochMilli(liveOverride.untilMillis),
                    java.time.ZoneId.systemDefault()
                )
                stringResource(R.string.context_override_active, held, time.toString())
            }
            CleanRow(
                title = stringResource(R.string.context_resume_schedule),
                subtitle = until,
                onClick = {
                    viewModel.resumeSchedule()
                    Toast.makeText(context, R.string.context_schedule_resumed, Toast.LENGTH_SHORT).show()
                }
            )
        }
        CleanDivider()
        Text("Edit settings for", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ContextProfile.entries.forEach { profile ->
                val selected = profile == target
                Box(
                    Modifier.weight(1f).height(40.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { target = profile },
                    contentAlignment = Alignment.Center
                ) {
                    Text(profile.title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        CleanDivider()
        CompactSwitchRow(title = "Automatic schedule", subtitle = "Switch to ${target.title} at local-time boundaries", checked = schedule.enabled, onCheckedChange = { viewModel.setSchedule(target, schedule.copy(enabled = it)) })
        if (schedule.enabled) {
            CleanDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactPill(label = "From ${schedule.start}", onClick = {
                    android.app.TimePickerDialog(context, { _, h, m -> viewModel.setSchedule(target, schedule.copy(start = LocalTime.of(h, m))) }, schedule.start.hour, schedule.start.minute, true).show()
                }, modifier = Modifier.weight(1f))
                CompactPill(label = "Until ${schedule.end}", onClick = {
                    android.app.TimePickerDialog(context, { _, h, m -> viewModel.setSchedule(target, schedule.copy(end = LocalTime.of(h, m))) }, schedule.end.hour, schedule.end.minute, true).show()
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
                                viewModel.setSchedule(target, schedule.copy(days = days))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(labels[index], style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        CleanDivider()
        val visibleCount = (installedApps.size - hiddenApps.size).coerceAtLeast(0)
        CleanRow(
            title = "Apps in ${target.title}",
            subtitle = if (installedApps.isEmpty()) "Loading apps…" else "$visibleCount of ${installedApps.size} visible",
            onClick = { viewModel.refreshInstalledApps(); appsDialog = true }
        )
        CleanDivider()
        CleanRow(
            title = "Wallpaper for ${target.title}",
            subtitle = "Each context keeps its own home wallpaper",
            onClick = { openWallpaper() }
        )
        CleanDivider()
        if (target in ContextEngine.FOCUS_PROFILES) {
            CompactSwitchRow(
                title = "Focus Protection",
                subtitle = "Restrict chosen apps while ${target.title} is active",
                checked = focusOn,
                onCheckedChange = { viewModel.setFocusEnabled(target, it) }
            )
            CleanDivider()
            CleanRow(
                title = "Restricted apps",
                subtitle = if (restrictedApps.isEmpty()) "None selected" else "${restrictedApps.size} selected",
                onClick = { viewModel.refreshInstalledApps(); focusDialog = true }
            )
            CleanDivider()
            ChoiceRowCompact(
                label = stringResource(R.string.focus_unlock_length),
                selected = "$unlockMinutes min",
                options = listOf(1 to "1 min", 5 to "5 min", 15 to "15 min", 30 to "30 min").map { it as Pair<Any, String> },
                onSelected = { viewModel.setFocusUnlockMinutes(it as Int) }
            )
            CleanDivider()
            CleanRow(
                title = stringResource(R.string.focus_access_title),
                subtitle = stringResource(if (focusServiceOn) R.string.focus_access_on else R.string.focus_access_off),
                value = if (focusServiceOn) "On" else "Off",
                onClick = { if (focusServiceOn) openAccessSettings() else accessDialog = true }
            )
            Text(
                stringResource(R.string.focus_layer_note),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else {
            Text(
                "Focus Protection is available for Work and Study. ${target.title} stays unrestricted.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        CleanDivider()
        CompactSwitchRow(title = "Context alerts", subtitle = "Notify when entering ${target.title}", checked = target in alertProfiles, onCheckedChange = { scope.launch { viewModel.prefs.setContextNotificationEnabled(target, it) } })
        CompactSwitchRow(title = "Do Not Disturb in ${target.title}", checked = target in dndProfiles, onCheckedChange = {
            scope.launch { viewModel.prefs.setContextDndEnabled(target, it) }
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
        CompactSwitchRow(title = "System notifications", checked = alertsEnabled, onCheckedChange = { if (it) onRequestNotifications() else scope.launch { viewModel.prefs.setContextNotificationsEnabled(false) } })
    }
    if (appsDialog) {
        ContextAppPickerDialog(
            title = "Apps in ${target.title}",
            subtitle = "Hidden apps stay installed with their data; they reappear when unhidden or in other contexts.",
            apps = installedApps,
            checked = { it !in hiddenApps },
            onToggle = { key, show -> viewModel.setAppVisible(target, key, show) },
            primaryLabel = "Show all",
            onPrimary = { viewModel.showAllApps(target) },
            onSuggest = { viewModel.applyVisibilitySuggestions(target) },
            onDismiss = { appsDialog = false }
        )
    }
    if (focusDialog) {
        ContextAppPickerDialog(
            title = "Restricted during ${target.title}",
            subtitle = "Launching a restricted app shows the LumaOS restriction screen instead.",
            apps = installedApps,
            checked = { it in restrictedApps },
            onToggle = { key, restrict -> viewModel.setAppRestricted(target, key, restrict) },
            primaryLabel = "Clear all",
            onPrimary = { viewModel.clearFocusRestrictions(target) },
            onSuggest = { viewModel.applyFocusSuggestions(target) },
            onDismiss = { focusDialog = false }
        )
    }
    if (accessDialog) {
        AlertDialog(
            onDismissRequest = { accessDialog = false },
            title = { Text(stringResource(R.string.focus_access_disclosure_title)) },
            text = { Text(stringResource(R.string.focus_access_disclosure)) },
            confirmButton = {
                TextButton(onClick = { accessDialog = false; openAccessSettings() }) {
                    Text(stringResource(R.string.focus_access_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { accessDialog = false }) { Text("Not now") }
            }
        )
    }
}

@Composable
private fun CompactPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.clickable { onClick() }) {
        Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/**
 * Multi-select app list for per-context visibility and focus restrictions.
 * Toggles persist immediately; there is no draft to lose.
 */
@Composable
private fun ContextAppPickerDialog(
    title: String,
    subtitle: String,
    apps: List<AppEntry>,
    checked: (ComponentKey) -> Boolean,
    onToggle: (ComponentKey, Boolean) -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onSuggest: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.key.packageName.contains(query, ignoreCase = true)
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactPill(label = primaryLabel, onClick = onPrimary, modifier = Modifier.weight(1f))
                    CompactPill(label = "Use suggestions", onClick = onSuggest, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                if (apps.isEmpty()) {
                    Text(
                        text = "Loading apps…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally)
                    )
                } else if (filtered.isEmpty()) {
                    Text(
                        text = "No apps match \"$query\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filtered, key = { it.key.serialize() }) { app ->
                            val isChecked = checked(app.key)
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onToggle(app.key, !isChecked) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isChecked, onCheckedChange = { onToggle(app.key, it) })
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.key.packageName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
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
        GestureAction.OpenQuickControls -> R.string.gesture_open_quick_controls
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
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(if (isFloat) "%.2f×".format(sliderValue) else "${sliderValue.toInt()}$suffix", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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

