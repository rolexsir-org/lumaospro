package com.lumaos.pro.launcher.settings.studio

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.effects.EffectsLevel
import com.lumaos.pro.launcher.effects.EffectsPreference
import com.lumaos.pro.launcher.launcher.model.DockStyle
import com.lumaos.pro.launcher.launcher.model.HomePreset
import com.lumaos.pro.launcher.motion.MotionPreference
import com.lumaos.pro.launcher.settings.SettingsViewModel
import com.lumaos.pro.launcher.settings.StudioConfiguration
import com.lumaos.pro.launcher.settings.StudioPreviewIcons
import com.lumaos.pro.launcher.ui.LumaCorners
import com.lumaos.pro.launcher.ui.LumaSizes
import com.lumaos.pro.launcher.ui.LumaSpacing
import com.lumaos.pro.launcher.ui.theme.Backdrop
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.DarkWindow
import com.lumaos.pro.launcher.ui.theme.IconShape
import com.lumaos.pro.launcher.ui.theme.IconStyle
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemePresets
import com.lumaos.pro.launcher.ui.theme.ThemeSpec
import com.lumaos.pro.launcher.ui.theme.ThemeSwitching
import com.lumaos.pro.launcher.ui.theme.TypeStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Customisation Studio: one draft, one live preview, one explicit Apply.
 * Nothing here writes home layout positions or fabricates wallpaper/app data.
 */
@Composable
fun StudioRoot(viewModel: SettingsViewModel) {
    val saved by viewModel.studioConfiguration.collectAsState()
    var draft by remember { mutableStateOf(saved) }
    var lastSaved by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) {
        val wasClean = draft == lastSaved
        lastSaved = saved
        if (wasClean) draft = saved
    }
    val dirty = draft != saved

    val environment by viewModel.themes.environment.collectAsState()
    val wallpaper by viewModel.themes.wallpaper.seeds.collectAsState()
    val effectsDecision by viewModel.effectsState.collectAsState()
    val packs by viewModel.iconPacks.collectAsState()
    val activeProfile by viewModel.prefs.activeContextProfile.collectAsState(ContextProfile.PERSONAL)
    val resolved = remember(draft.theme, environment) { ThemeEngine.resolve(draft.theme, environment) }
    val previewDepth = when (draft.effects) {
        EffectsPreference.MINIMAL -> false
        EffectsPreference.BALANCED, EffectsPreference.FULL -> true
        EffectsPreference.AUTO -> effectsDecision.iconDepth
    }
    val bitmaps by produceState(
        StudioPreviewIcons(emptyList(), emptyList()),
        resolved, draft.iconPack, previewDepth
    ) {
        value = viewModel.loadPreviewIcons(resolved, draft.iconPack, previewDepth)
    }
    val homeIcons = remember(bitmaps) { bitmaps.home.map { it.asImageBitmap() } }
    val dockIcons = remember(bitmaps) { bitmaps.dock.map { it.asImageBitmap() } }

    val context = LocalContext.current
    val locale = Locale.getDefault()
    val now = remember { Date() }
    val timeText = remember {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        SimpleDateFormat(pattern, locale).format(now)
    }
    val dateText = remember {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEE MMM d"), locale).format(now)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LumaSpacing.screenHorizontal, vertical = LumaSpacing.screenVertical),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = LumaSizes.studioPreviewMaxWidth).fillMaxWidth()) {
            Text(stringResource(R.string.studio_preview_label), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(LumaSpacing.elementGap))
            Card(
                shape = RoundedCornerShape(LumaCorners.large * resolved.cornerScale),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                HomePreview(
                    theme = resolved,
                    preset = draft.homePreset,
                    dock = draft.dockStyle ?: draft.homePreset.dock,
                    icons = homeIcons,
                    dockIcons = dockIcons,
                    wallpaper = wallpaper,
                    timeText = timeText,
                    dateText = dateText,
                    readout = activeProfile.title,
                    modifier = Modifier.fillMaxWidth().height(340.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    R.string.studio_contrast,
                    String.format(locale, "%.1f", ColorMath.contrast(resolved.colors.onSurface, resolved.colors.surface1))
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (resolved.backdrop == Backdrop.WALLPAPER) {
                Text(
                    stringResource(R.string.studio_wallpaper_preview_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (resolved.id == ThemeId.CHROMA && wallpaper == null) {
                Text(
                    stringResource(R.string.studio_no_wallpaper_colors),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (viewModel.themes.wallpaper.hintEstimated) {
                Text(
                    stringResource(R.string.studio_wallpaper_hint_estimated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (dirty) R.string.studio_unsaved else R.string.studio_applied),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { draft = saved }, enabled = dirty) {
                    Text(stringResource(R.string.studio_revert))
                }
                Button(onClick = { viewModel.applyStudio(draft) }, enabled = dirty) {
                    Text(stringResource(R.string.studio_apply))
                }
            }
            TextButton(onClick = { draft = StudioConfiguration() }, enabled = dirty || saved != StudioConfiguration()) {
                Text(stringResource(R.string.studio_reset))
            }

            StudioSection(stringResource(R.string.studio_themes)) {
                ThemePresetRow(
                    heading = stringResource(R.string.studio_theme_signature),
                    presets = ThemePresets.SIGNATURE,
                    selectedId = draft.theme.themeId,
                    onSelected = { id -> draft = draft.copy(theme = draft.theme.withPreset(id)) }
                )
                Spacer(Modifier.height(LumaSpacing.sectionGap))
                ThemePresetRow(
                    heading = stringResource(R.string.studio_theme_additional),
                    presets = ThemePresets.ADDITIONAL,
                    selectedId = draft.theme.themeId,
                    onSelected = { id -> draft = draft.copy(theme = draft.theme.withPreset(id)) }
                )
                Spacer(Modifier.height(LumaSpacing.elementGap))
                val selectedSpec = ThemePresets.of(draft.theme.themeId)
                Text(
                    selectedSpec.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                draft.theme.profileThemes[activeProfile]?.let { profileTheme ->
                    Text(
                        stringResource(
                            R.string.studio_profile_theme_precedence,
                            activeProfile.title,
                            ThemePresets.of(profileTheme).displayName
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            StudioSection(stringResource(R.string.studio_switching)) {
                StudioChoice(
                    label = stringResource(R.string.studio_switching),
                    selected = when (draft.theme.switching) {
                        ThemeSwitching.FIXED -> stringResource(R.string.studio_switch_fixed)
                        ThemeSwitching.SYSTEM -> stringResource(R.string.studio_switch_system)
                        ThemeSwitching.SCHEDULE -> stringResource(R.string.studio_switch_schedule)
                    },
                    options = ThemeSwitching.entries.toList(),
                    optionLabel = {
                        when (it) {
                            ThemeSwitching.FIXED -> stringResource(R.string.studio_switch_fixed)
                            ThemeSwitching.SYSTEM -> stringResource(R.string.studio_switch_system)
                            ThemeSwitching.SCHEDULE -> stringResource(R.string.studio_switch_schedule)
                        }
                    },
                    onSelected = { draft = draft.copy(theme = draft.theme.copy(switching = it)) }
                )
                if (draft.theme.switching != ThemeSwitching.FIXED) {
                    StudioChoice(
                        label = stringResource(R.string.studio_light_theme),
                        selected = ThemePresets.of(draft.theme.lightThemeId).displayName,
                        options = ThemePresets.ALL.map { it.id },
                        onSelected = { draft = draft.copy(theme = draft.theme.copy(lightThemeId = it)) }
                    )
                    StudioChoice(
                        label = stringResource(R.string.studio_dark_theme),
                        selected = ThemePresets.of(draft.theme.darkThemeId).displayName,
                        options = ThemePresets.ALL.map { it.id },
                        onSelected = { draft = draft.copy(theme = draft.theme.copy(darkThemeId = it)) }
                    )
                }
                if (draft.theme.switching == ThemeSwitching.SCHEDULE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeButton(stringResource(R.string.studio_dark_from), draft.theme.darkWindow.startMinute) { minute ->
                            draft = draft.copy(theme = draft.theme.copy(darkWindow = draft.theme.darkWindow.copy(startMinute = minute)))
                        }
                        TimeButton(stringResource(R.string.studio_dark_until), draft.theme.darkWindow.endMinute) { minute ->
                            draft = draft.copy(theme = draft.theme.copy(darkWindow = draft.theme.darkWindow.copy(endMinute = minute)))
                        }
                    }
                }
            }

            StudioSection(stringResource(R.string.studio_accent)) {
                SwatchRow(
                    colors = ThemePresets.ACCENT_SWATCHES,
                    selected = draft.theme.accentOverride,
                    defaultLabel = stringResource(R.string.studio_theme_default),
                    onSelected = { draft = draft.copy(theme = draft.theme.copy(accentOverride = it)) }
                )
            }

            StudioSection(stringResource(R.string.studio_background)) {
                SwatchRow(
                    colors = ThemePresets.BACKGROUND_SWATCHES,
                    selected = draft.theme.backgroundOverride,
                    defaultLabel = stringResource(R.string.studio_theme_default),
                    onSelected = { draft = draft.copy(theme = draft.theme.copy(backgroundOverride = it)) }
                )
                StudioChoice(
                    label = stringResource(R.string.studio_backdrop),
                    selected = when (draft.theme.backdrop) {
                        null -> stringResource(R.string.studio_theme_default)
                        Backdrop.WALLPAPER -> stringResource(R.string.studio_backdrop_wallpaper)
                        Backdrop.THEME -> stringResource(R.string.studio_backdrop_theme)
                    },
                    options = listOf<Backdrop?>(null, Backdrop.WALLPAPER, Backdrop.THEME),
                    optionLabel = {
                        when (it) {
                            null -> stringResource(R.string.studio_theme_default)
                            Backdrop.WALLPAPER -> stringResource(R.string.studio_backdrop_wallpaper)
                            Backdrop.THEME -> stringResource(R.string.studio_backdrop_theme)
                        }
                    },
                    onSelected = { draft = draft.copy(theme = draft.theme.copy(backdrop = it)) }
                )
            }

            StudioSection(stringResource(R.string.studio_typography)) {
                StudioChoice(
                    label = stringResource(R.string.studio_typography),
                    selected = draft.theme.typography?.let { typeLabel(it) } ?: stringResource(R.string.studio_theme_default),
                    options = listOf<TypeStyle?>(null) + TypeStyle.entries,
                    optionLabel = { it?.let { value -> typeLabel(value) } ?: stringResource(R.string.studio_theme_default) },
                    onSelected = { draft = draft.copy(theme = draft.theme.copy(typography = it)) }
                )
            }

            StudioSection(stringResource(R.string.studio_icons)) {
                StudioChoice(
                    label = stringResource(R.string.studio_icon_shape),
                    selected = draft.theme.iconShape?.let { shapeLabel(it) } ?: stringResource(R.string.studio_theme_default),
                    options = listOf<IconShape?>(null) + IconShape.entries,
                    optionLabel = { it?.let { value -> shapeLabel(value) } ?: stringResource(R.string.studio_theme_default) },
                    onSelected = { draft = draft.copy(theme = draft.theme.copy(iconShape = it)) }
                )
                StudioChoice(
                    label = stringResource(R.string.studio_icon_style),
                    selected = draft.theme.iconStyle?.let { iconStyleLabel(it) } ?: stringResource(R.string.studio_theme_default),
                    options = listOf<IconStyle?>(null) + IconStyle.entries,
                    optionLabel = { it?.let { value -> iconStyleLabel(value) } ?: stringResource(R.string.studio_theme_default) },
                    onSelected = { draft = draft.copy(theme = draft.theme.copy(iconStyle = it)) }
                )
                val packOptions = listOf<String?>(null) + packs.map { it.packageName } +
                    listOfNotNull(draft.iconPack.takeIf { pack -> pack != null && packs.none { it.packageName == pack } })
                StudioChoice(
                    label = stringResource(R.string.studio_icon_pack),
                    selected = packs.firstOrNull { it.packageName == draft.iconPack }?.label
                        ?: draft.iconPack ?: stringResource(R.string.studio_icon_pack_none),
                    options = packOptions,
                    optionLabel = { pack ->
                        when {
                            pack == null -> stringResource(R.string.studio_icon_pack_none)
                            else -> packs.firstOrNull { it.packageName == pack }?.label ?: pack
                        }
                    },
                    onSelected = { draft = draft.copy(iconPack = it) }
                )
                if (packs.isEmpty()) {
                    Text(
                        stringResource(R.string.studio_icon_pack_none_installed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            StudioSection(stringResource(R.string.studio_layout)) {
                StudioChoice(
                    label = stringResource(R.string.studio_layout),
                    selected = draft.homePreset.displayName,
                    options = HomePreset.entries.toList(),
                    optionLabel = { it.displayName },
                    onSelected = { draft = draft.copy(homePreset = it, dockStyle = null) }
                )
                Text(
                    draft.homePreset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StudioChoice(
                    label = stringResource(R.string.studio_dock_style),
                    selected = draft.dockStyle?.let { dockLabel(it) } ?: stringResource(R.string.studio_theme_default),
                    options = listOf<DockStyle?>(null) + DockStyle.entries,
                    optionLabel = { it?.let { value -> dockLabel(value) } ?: stringResource(R.string.studio_theme_default) },
                    onSelected = { draft = draft.copy(dockStyle = it) }
                )
            }

            StudioSection(stringResource(R.string.studio_effects)) {
                StudioChoice(
                    label = stringResource(R.string.studio_effects),
                    selected = effectsLabel(draft.effects),
                    options = EffectsPreference.entries.toList(),
                    optionLabel = { effectsLabel(it) },
                    onSelected = { draft = draft.copy(effects = it) }
                )
                StudioChoice(
                    label = stringResource(R.string.studio_motion),
                    selected = motionLabel(draft.motion),
                    options = MotionPreference.entries.toList(),
                    optionLabel = { motionLabel(it) },
                    onSelected = { draft = draft.copy(motion = it) }
                )
                Text(
                    stringResource(R.string.studio_effects_now, effectsLevelLabel(effectsDecision.level)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                effectsDecision.reasons.take(3).forEach { reason ->
                    Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            StudioSection(stringResource(R.string.studio_profile_themes)) {
                ContextProfile.entries.forEach { profile ->
                    StudioChoice(
                        label = profile.title,
                        selected = draft.theme.profileThemes[profile]?.let { ThemePresets.of(it).displayName }
                            ?: stringResource(R.string.studio_profile_theme_none),
                        options = listOf<ThemeId?>(null) + ThemePresets.ALL.map { it.id },
                        optionLabel = {
                            it?.let { id -> ThemePresets.of(id).displayName }
                                ?: stringResource(R.string.studio_profile_theme_none)
                        },
                        onSelected = { id ->
                            val mapping = draft.theme.profileThemes.toMutableMap()
                            if (id == null) mapping.remove(profile) else mapping[profile] = id
                            draft = draft.copy(theme = draft.theme.copy(profileThemes = mapping))
                        }
                    )
                }
            }
            Spacer(Modifier.height(LumaSpacing.screenEnd))
        }
    }
}

@Composable
private fun StudioSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(LumaSpacing.elementGap))
        content()
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ThemePresetRow(
    heading: String,
    presets: List<ThemeSpec>,
    selectedId: ThemeId,
    onSelected: (ThemeId) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            heading,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(LumaSpacing.compactGap))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(LumaSpacing.elementGap)
        ) {
            presets.forEach { spec ->
                ThemePresetCard(
                    spec = spec,
                    selected = selectedId == spec.id,
                    onClick = { onSelected(spec.id) }
                )
            }
        }
    }
}

@Composable
private fun ThemePresetCard(spec: ThemeSpec, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(LumaCorners.card)
    val textColor = Color(ThemeEngine.bestTextOn(spec.background))
    Card(
        modifier = Modifier
            .size(width = LumaSizes.studioThemeCardWidth, height = LumaSizes.studioThemeCardHeight)
            .clip(shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = "${spec.displayName}. ${spec.tagline}"
            },
        shape = shape,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) Color(spec.accent) else MaterialTheme.colorScheme.outline
        ),
        colors = CardDefaults.cardColors(containerColor = Color(spec.background)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(spec.background),
                            Color(ColorMath.withAlpha(spec.secondary, 0.10f)),
                            Color(ColorMath.withAlpha(spec.accent, 0.08f))
                        )
                    )
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(spec.accent, spec.secondary, spec.neutralTint).forEach { swatch ->
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(swatch))
                            .border(1.dp, textColor.copy(alpha = 0.28f), CircleShape)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    spec.displayName,
                    color = textColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    spec.tagline,
                    color = textColor.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun <T> StudioChoice(
    label: String,
    selected: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String = { TextValue(it) },
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.widthIn(max = 220.dp).heightIn(min = LumaSizes.minimumTouchTarget)
            ) {
                Text(selected, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/** Fallback for non-null enum/string options; specialised labels are supplied where needed. */
@Composable
private fun <T> TextValue(value: T): String = when (value) {
    is ThemeId -> ThemePresets.of(value).displayName
    else -> value.toString()
}

@Composable
private fun SwatchRow(
    colors: List<Int>,
    selected: Int?,
    defaultLabel: String,
    onSelected: (Int?) -> Unit
) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = { onSelected(null) }) { Text(defaultLabel) }
        colors.forEach { color ->
            val chosen = selected == color
            Box(
                Modifier
                    .size(LumaSizes.minimumTouchTarget)
                    .clip(CircleShape)
                    .background(Color(color))
                    .clickable { onSelected(color) }
                    .semantics {
                        role = Role.RadioButton
                        this.selected = chosen
                        contentDescription = context.getString(R.string.studio_color_option_description, ColorMath.toHex(color))
                    },
                contentAlignment = Alignment.Center
            ) {
                if (chosen) {
                    Box(
                        Modifier.size(16.dp).clip(CircleShape).background(Color(ThemeEngine.bestTextOn(color)))
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeButton(label: String, minute: Int, onSelected: (Int) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        TimePickerDialog(
            context,
            { _, hour, min -> onSelected(hour * 60 + min) },
            minute / 60,
            minute % 60,
            android.text.format.DateFormat.is24HourFormat(context)
        ).show()
    }) {
        Text("$label %02d:%02d".format(minute / 60, minute % 60))
    }
}

@Composable
private fun typeLabel(value: TypeStyle): String = stringResource(
    when (value) {
        TypeStyle.PRECISION -> R.string.type_precision
        TypeStyle.GROTESK -> R.string.type_grotesk
        TypeStyle.SERIF -> R.string.type_serif
        TypeStyle.MONO -> R.string.type_mono
    }
)

@Composable
private fun shapeLabel(value: IconShape): String = stringResource(
    when (value) {
        IconShape.SYSTEM -> R.string.shape_system
        IconShape.SQUIRCLE -> R.string.shape_squircle
        IconShape.CIRCLE -> R.string.shape_circle
        IconShape.ROUNDED -> R.string.shape_rounded
        IconShape.TEARDROP -> R.string.shape_teardrop
        IconShape.OCTAGON -> R.string.shape_octagon
    }
)

@Composable
private fun iconStyleLabel(value: IconStyle): String = stringResource(
    when (value) {
        IconStyle.NATURAL -> R.string.style_natural
        IconStyle.PLATE -> R.string.style_plate
        IconStyle.TINTED -> R.string.style_tinted
    }
)

@Composable
private fun dockLabel(value: DockStyle): String = stringResource(
    when (value) {
        DockStyle.BEZEL -> R.string.dock_bezel
        DockStyle.FLOAT -> R.string.dock_float
        DockStyle.MINIMAL -> R.string.dock_minimal
    }
)

@Composable
private fun effectsLabel(value: EffectsPreference): String = stringResource(
    when (value) {
        EffectsPreference.AUTO -> R.string.effects_auto
        EffectsPreference.MINIMAL -> R.string.effects_minimal
        EffectsPreference.BALANCED -> R.string.effects_balanced
        EffectsPreference.FULL -> R.string.effects_full
    }
)

@Composable
private fun motionLabel(value: MotionPreference): String = stringResource(
    when (value) {
        MotionPreference.FULL -> R.string.motion_full
        MotionPreference.REDUCED -> R.string.motion_reduced
        MotionPreference.OFF -> R.string.motion_off
    }
)

@Composable
private fun effectsLevelLabel(value: EffectsLevel): String = stringResource(
    when (value) {
        EffectsLevel.MINIMAL -> R.string.effects_level_minimal
        EffectsLevel.BALANCED -> R.string.effects_level_balanced
        EffectsLevel.FULL -> R.string.effects_level_full
    }
)
