package com.lumaos.pro.launcher.settings.studio

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.effects.EffectsLevel
import com.lumaos.pro.launcher.effects.EffectsPreference
import com.lumaos.pro.launcher.launcher.model.DockStyle
import com.lumaos.pro.launcher.launcher.model.HomePreset
import com.lumaos.pro.launcher.motion.MotionPreference
import com.lumaos.pro.launcher.settings.LocalLumaTheme
import com.lumaos.pro.launcher.settings.SettingsViewModel
import com.lumaos.pro.launcher.settings.StudioConfiguration
import com.lumaos.pro.launcher.settings.StudioApplyState
import com.lumaos.pro.launcher.settings.StudioPreviewIcons
import com.lumaos.pro.launcher.ui.LumaSizes
import com.lumaos.pro.launcher.ui.LumaSpacing
import com.lumaos.pro.launcher.ui.glass.LumaGlassChoice
import com.lumaos.pro.launcher.ui.glass.LumaGlassPreviewFrame
import com.lumaos.pro.launcher.ui.glass.LumaGlassSectionTitle
import com.lumaos.pro.launcher.ui.glass.LumaGlassSurface
import com.lumaos.pro.launcher.ui.theme.GlassGeometry
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.LumaGlassIdentity
import com.lumaos.pro.launcher.ui.theme.Backdrop
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.IconShape
import com.lumaos.pro.launcher.ui.theme.IconStyle
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemeCardPalette
import com.lumaos.pro.launcher.ui.theme.ThemePresets
import com.lumaos.pro.launcher.ui.theme.ThemeSpec
import com.lumaos.pro.launcher.ui.theme.ThemeSwitching
import com.lumaos.pro.launcher.ui.theme.TypeStyle
import com.lumaos.pro.launcher.ui.theme.WallpaperArt
import com.lumaos.pro.launcher.ui.theme.WallpaperArtResources
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Customisation Studio: one draft, one live preview, one explicit Apply.
 * Nothing here writes home layout positions or fabricates wallpaper/app data.
 */
@Composable
fun StudioRoot(viewModel: SettingsViewModel) {
    val loaded by viewModel.studioConfiguration.collectAsState()
    val saved = loaded
    if (saved == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.studio_loading), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    var draft by remember { mutableStateOf(saved) }
    var lastSaved by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) {
        val wasClean = draft == lastSaved
        lastSaved = saved
        if (wasClean) draft = saved
    }
    val dirty = draft != saved
    val applyState by viewModel.studioApplyState.collectAsState()
    val applying = applyState == StudioApplyState.APPLYING

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
    val context = LocalContext.current
    val artwork by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, resolved.id, resolved.backdrop) {
        value = if (resolved.backdrop == Backdrop.LUMA) {
            WallpaperArt.forTheme(resolved.id)?.let { art ->
                runCatching {
                    val res = WallpaperArtResources.res(art)
                    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                    context.resources.openRawResource(res).use {
                        android.graphics.BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        } else null
    }
    val homeIcons = remember(bitmaps) { bitmaps.home.map { it.asImageBitmap() } }
    val dockIcons = remember(bitmaps) { bitmaps.dock.map { it.asImageBitmap() } }

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
            LumaGlassPreviewFrame(
                theme = resolved,
                modifier = Modifier.fillMaxWidth().height(340.dp)
            ) {
                HomePreview(
                    theme = resolved,
                    preset = draft.homePreset,
                    dock = draft.dockStyle ?: draft.homePreset.dock,
                    icons = homeIcons,
                    dockIcons = dockIcons,
                    wallpaper = wallpaper,
                    artwork = artwork,
                    timeText = timeText,
                    dateText = dateText,
                    readout = activeProfile.title,
                    modifier = Modifier.fillMaxSize()
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
            Text(
                stringResource(
                    when {
                        applying -> R.string.studio_applying
                        dirty -> R.string.studio_unsaved
                        else -> R.string.studio_applied
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { draft = saved }, enabled = dirty && !applying) {
                    Text(stringResource(R.string.studio_revert))
                }
                Button(onClick = { viewModel.applyStudio(draft) }, enabled = dirty && !applying) {
                    Text(stringResource(R.string.studio_apply))
                }
            }
            if (applyState == StudioApplyState.FAILED) {
                Text(
                    stringResource(R.string.studio_apply_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TextButton(onClick = { draft = StudioConfiguration() }, enabled = !applying && (dirty || saved != StudioConfiguration())) {
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

            StudioSection(LumaGlassIdentity.NAME) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LumaGlassIdentity.ACCENTS.forEach { accent ->
                        LumaGlassChoice(
                            theme = resolved,
                            label = accent.name,
                            selected = draft.theme.accentOverride == accent.color &&
                                draft.theme.themeId == accent.darkAnchor,
                            onClick = { draft = draft.copy(theme = accent.applyTo(draft.theme)) },
                            modifier = Modifier.widthIn(min = 112.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    LumaGlassIdentity.TAGLINE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

            var advancedOpen by rememberSaveable { mutableStateOf(false) }
            Spacer(Modifier.height(LumaSpacing.sectionGap))
            LumaGlassChoice(
                theme = LocalLumaTheme.current,
                label = stringResource(if (advancedOpen) R.string.studio_advanced_hide else R.string.studio_advanced_show),
                caption = stringResource(R.string.studio_advanced_summary),
                selected = advancedOpen,
                onClick = { advancedOpen = !advancedOpen },
                modifier = Modifier.fillMaxWidth()
            )
            if (advancedOpen) {
                StudioSection(stringResource(R.string.studio_background)) {
                    SwatchRow(
                        colors = ThemePresets.BACKGROUND_SWATCHES,
                        selected = draft.theme.backgroundOverride,
                        defaultLabel = stringResource(R.string.studio_theme_default),
                        onSelected = { draft = draft.copy(theme = draft.theme.copy(backgroundOverride = it)) }
                    )
                    val backdropOptions = listOf<Backdrop?>(null, Backdrop.WALLPAPER, Backdrop.THEME) +
                        listOfNotNull(Backdrop.LUMA.takeIf { WallpaperArt.forTheme(resolved.id) != null })
                    StudioChoice(
                        label = stringResource(R.string.studio_backdrop),
                        selected = backdropLabel(draft.theme.backdrop, resolved.id),
                        options = backdropOptions,
                        optionLabel = { backdropLabel(it, resolved.id) },
                        onSelected = { draft = draft.copy(theme = draft.theme.copy(backdrop = it)) }
                    )
                    if (WallpaperArt.forTheme(resolved.id) != null) {
                        Text(
                            stringResource(R.string.studio_luma_wallpaper_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StudioSection(stringResource(R.string.studio_typography)) {
                    StudioChoice(
                        label = stringResource(R.string.studio_typography),
                        selected = draft.theme.typography?.let { typeLabel(it) } ?: stringResource(R.string.studio_theme_default),
                        options = listOf<TypeStyle?>(null) + TypeStyle.entries,
                        optionLabel = { it?.let { value -> typeLabel(value) } ?: stringResource(R.string.studio_theme_default) },
                        onSelected = { draft = draft.copy(theme = draft.theme.copy(typography = it)) }
                    )
                    Text(
                        stringResource(R.string.studio_typography_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.studio_motion_character, resolved.motionCharacter.name.lowercase()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            }
            Spacer(Modifier.height(LumaSpacing.screenEnd))
        }
    }
}

@Composable
private fun StudioSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalLumaTheme.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        LumaGlassSectionTitle(theme, title)
        Spacer(Modifier.height(4.dp))
        LumaGlassSurface(theme, GlassRole.SECONDARY, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(GlassGeometry.GUTTER_DP.dp)) {
                content()
            }
        }
        Spacer(Modifier.height(16.dp))
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
    val theme = LocalLumaTheme.current
    val card = remember(spec) { ThemeCardPalette.forSpec(spec) }
    val textColor = Color(card.content)
    val cardMinimum = LumaSizes.studioThemeCardHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)
    LumaGlassSurface(
        theme = theme,
        role = GlassRole.PRIMARY,
        modifier = Modifier
            .width(LumaSizes.studioThemeCardWidth)
            .heightIn(min = cardMinimum)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = "${spec.displayName}. ${spec.tagline}"
            },
        selected = selected,
        onClick = onClick
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = cardMinimum)
                .background(
                    Brush.linearGradient(listOf(Color(card.background), Color(card.middle), Color(card.end)))
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    spec.tagline,
                    color = Color(card.muted),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "${spec.typography.character} · ${spec.motionCharacter.name.lowercase()}",
                    color = Color(card.muted),
                    style = MaterialTheme.typography.labelSmall
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
    val theme = LocalLumaTheme.current
    var expanded by remember { mutableStateOf(false) }
    val selectedText = selected
    @Composable
    fun ChoiceControl() {
        Box {
            LumaGlassChoice(
                theme = theme,
                label = selected,
                selected = false,
                onClick = { expanded = true },
                modifier = Modifier.widthIn(max = 220.dp).heightIn(min = LumaSizes.minimumTouchTarget)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.Transparent)
            ) {
                LumaGlassSurface(theme, GlassRole.MENU, Modifier.widthIn(min = 180.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        options.forEach { option ->
                            val text = optionLabel(option)
                            LumaGlassChoice(
                                theme = theme,
                                label = text,
                                selected = text == selectedText,
                                onClick = {
                                    onSelected(option)
                                    expanded = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        val stack = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f
        if (stack) {
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                ChoiceControl()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                ChoiceControl()
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
        val theme = LocalLumaTheme.current
        LumaGlassChoice(
            theme = theme,
            label = defaultLabel,
            selected = selected == null,
            onClick = { onSelected(null) }
        )
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
    val theme = LocalLumaTheme.current
    LumaGlassChoice(
        theme = theme,
        label = "$label %02d:%02d".format(minute / 60, minute % 60),
        selected = false,
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, min -> onSelected(hour * 60 + min) },
                minute / 60,
                minute % 60,
                android.text.format.DateFormat.is24HourFormat(context)
            ).show()
        }
    )
}

@Composable
private fun typeLabel(value: TypeStyle): String = stringResource(
    when (value) {
        TypeStyle.PRECISION -> R.string.type_precision
        TypeStyle.AMBIENT -> R.string.type_ambient
        TypeStyle.MONOGRAPH -> R.string.type_monograph
        TypeStyle.SERENE -> R.string.type_serene
        TypeStyle.GROTESK -> R.string.type_grotesk
        TypeStyle.SERIF -> R.string.type_serif
        TypeStyle.MONO -> R.string.type_mono
    }
)

/** Backdrop labels, including the bundled LumaOS wallpaper only where one exists. */
@Composable
private fun backdropLabel(value: Backdrop?, themeId: ThemeId): String = when (value) {
    null -> stringResource(R.string.studio_theme_default)
    Backdrop.WALLPAPER -> stringResource(R.string.studio_backdrop_wallpaper)
    Backdrop.THEME -> stringResource(R.string.studio_backdrop_theme)
    Backdrop.LUMA -> if (WallpaperArt.forTheme(themeId) != null) {
        stringResource(R.string.studio_backdrop_luma)
    } else {
        stringResource(R.string.studio_backdrop_theme)
    }
}

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
