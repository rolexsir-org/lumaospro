package com.lumaos.pro.launcher.settings.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.effects.EffectsLevel
import com.lumaos.pro.launcher.effects.EffectsPreference
import com.lumaos.pro.launcher.launcher.model.DockStyle
import com.lumaos.pro.launcher.launcher.model.HomePreset
import com.lumaos.pro.launcher.motion.MotionPreference
import com.lumaos.pro.launcher.settings.SettingsViewModel
import com.lumaos.pro.launcher.settings.StudioConfiguration
import com.lumaos.pro.launcher.settings.StudioApplyState
import com.lumaos.pro.launcher.settings.StudioPreviewIcons
import com.lumaos.pro.launcher.ui.theme.Backdrop
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.DarkWindow
import com.lumaos.pro.launcher.ui.theme.IconShape
import com.lumaos.pro.launcher.ui.theme.IconStyle
import com.lumaos.pro.launcher.ui.theme.LumaDesignTokens
import com.lumaos.pro.launcher.ui.theme.ThemeCardPalette
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemeLauncherPackage
import com.lumaos.pro.launcher.ui.theme.ThemePresets
import com.lumaos.pro.launcher.ui.theme.ThemeSpec
import com.lumaos.pro.launcher.ui.theme.ThemeSwitching
import com.lumaos.pro.launcher.ui.theme.TypeStyle
import com.lumaos.pro.launcher.ui.theme.WallpaperArt
import com.lumaos.pro.launcher.wallpaper.WallpaperActivity
import com.lumaos.pro.launcher.wallpaper.WallpaperCatalog
import com.lumaos.pro.launcher.wallpaper.WallpaperImageStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    // Staged draft: Apply alone persists. The ViewModel owns the session so
    // switching Settings tabs does not silently discard unsaved edits or Undo.
    LaunchedEffect(saved) { viewModel.syncStudioDraft(saved) }
    val session = viewModel.studioDraft.collectAsState().value
    val draft = session?.current ?: saved
    fun edit(next: StudioConfiguration) = viewModel.editStudioDraft(next)
    fun resetDraft(value: StudioConfiguration) = viewModel.resetStudioDraft(value)
    val studioPreset by viewModel.studioPreset.collectAsState()
    val dirty = draft != saved
    val applyState by viewModel.studioApplyState.collectAsState()
    val applying = applyState == StudioApplyState.APPLYING

    val environment by viewModel.themes.environment.collectAsState()
    val wallpaper = environment.wallpaper
    val activeWallpaper by viewModel.themes.activeWallpaper.collectAsState()
    val effectsDecision by viewModel.effectsState.collectAsState()
    val packs by viewModel.iconPacks.collectAsState()
    val activeProfile by viewModel.prefs.activeContextProfile.collectAsState(ContextProfile.PERSONAL)
    val iconSizeDp by viewModel.prefs.iconSizeDp.collectAsState(56)
    // With automatic light/dark switching the draft holds two themes. The
    // preview shows the one the user last touched (falling back to whichever
    // the environment selects right now), so tapping Ivory on a dark phone
    // previews Ivory instead of silently showing the dark slot.
    var previewFocus by remember { mutableStateOf<ThemeId?>(null) }
    val focus = previewFocus?.takeIf {
        draft.theme.switching != ThemeSwitching.FIXED &&
            (it == draft.theme.lightThemeId || it == draft.theme.darkThemeId)
    }
    val previewSettings = if (focus != null) draft.theme.copy(themeId = focus, switching = ThemeSwitching.FIXED) else draft.theme
    val resolved = remember(previewSettings, environment) { ThemeEngine.resolve(previewSettings, environment) }
    val previewDepth = when (draft.effects) {
        EffectsPreference.MINIMAL -> false
        EffectsPreference.BALANCED, EffectsPreference.FULL -> true
        EffectsPreference.AUTO -> effectsDecision.iconDepth && resolved.usesBlur
    }
    val bitmaps by produceState(
        StudioPreviewIcons(emptyList(), emptyList()),
        resolved,
        draft.iconPack,
        draft.livingIconsEnabled,
        draft.theme.iconStyle,
        previewDepth,
        iconSizeDp,
        activeProfile
    ) {
        value = viewModel.loadPreviewIcons(
            theme = resolved,
            iconPack = draft.iconPack,
            depth = previewDepth,
            livingIconsEnabled = draft.livingIconsEnabled,
            explicitTint = draft.theme.iconStyle == IconStyle.TINTED,
            iconSizeDp = iconSizeDp
        )
    }
    val context = LocalContext.current
    val artwork by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, resolved.id, resolved.backdrop, activeWallpaper?.id) {
        val selectedEntry = activeWallpaper
        value = when {
            selectedEntry != null && (resolved.backdrop == Backdrop.LUMA || resolved.backdrop == Backdrop.WALLPAPER) ->
                runCatching { WallpaperImageStore(context.applicationContext).preview(selectedEntry, 640)?.asImageBitmap() }.getOrNull()
            resolved.backdrop == Backdrop.LUMA -> WallpaperArt.forTheme(resolved.id)?.let { art ->
                val entry = WallpaperCatalog.findBundled("art:${art.storageKey}")
                if (entry == null) null else runCatching {
                    WallpaperImageStore(context.applicationContext).preview(entry, 640)?.asImageBitmap()
                }.getOrNull()
            }
            else -> null
        }
    }
    val previewFocalX = activeWallpaper?.focalX ?: WallpaperArt.forTheme(resolved.id)?.focalX
    val previewFocalY = activeWallpaper?.focalY ?: WallpaperArt.forTheme(resolved.id)?.focalY
    val homeIcons = remember(bitmaps) { bitmaps.home.map { it.asImageBitmap() } }
    val dockIcons = remember(bitmaps) { bitmaps.dock.map { it.asImageBitmap() } }
    val locale = Locale.getDefault()
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            kotlinx.coroutines.delay(30_000L)
        }
    }
    val timeText = remember(now, locale) { SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale).format(now) }
    val dateText = remember(now, locale) { SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEE MMM d"), locale).format(now) }
    val activePackage = remember(draft.theme.themeId) { ThemeLauncherPackage.forTheme(draft.theme.themeId) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        // --- Live Interactive Launcher Preview ---
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(292.dp)
                .border(
                    0.8.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    MaterialTheme.shapes.large
                )
        ) {
            HomePreview(
                theme = resolved,
                preset = draft.homePreset,
                dock = draft.dockStyle ?: draft.homePreset.dock,
                icons = homeIcons,
                dockIcons = dockIcons,
                wallpaper = wallpaper,
                artwork = artwork,
                focalX = previewFocalX,
                focalY = previewFocalY,
                timeText = timeText,
                dateText = dateText,
                readout = activeProfile.title,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.height(10.dp))

        // --- Status & Instant Apply Bar ---
        // Give the readout its own line; controls scroll rather than shrinking
        // labels off-screen on compact phones and at larger text scales.
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(resolved.colors.accent))
                    )
                    Text(
                        text = "${resolved.displayName} · ${activePackage.subtitle}",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                val contrastRatio = String.format(locale, "%.1f", ColorMath.contrast(resolved.colors.onSurface, resolved.colors.surface1))
                Text(
                    text = stringResource(
                        when {
                            applying -> R.string.studio_applying
                            dirty -> R.string.studio_unsaved
                            else -> R.string.studio_applied
                        }
                    ) + " · ${contrastRatio}:1",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = if (dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable(enabled = session?.canUndo == true && !applying) { viewModel.undoStudioDraft() }
                            .semantics { contentDescription = "Undo" }
                    ) {
                        Text(
                            stringResource(R.string.studio_undo),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = if (session?.canUndo == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable(enabled = session?.canRedo == true && !applying) { viewModel.redoStudioDraft() }
                            .semantics { contentDescription = "Redo" }
                    ) {
                        Text(
                            stringResource(R.string.studio_redo),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = if (session?.canRedo == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                    if (dirty) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.heightIn(min = 48.dp).clickable(enabled = !applying) { resetDraft(saved) }
                        ) {
                            Text(
                                stringResource(R.string.studio_revert),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (dirty && !applying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.heightIn(min = 48.dp).clickable(enabled = dirty && !applying) { viewModel.applyStudio(draft) }
                ) {
                    Text(
                        stringResource(R.string.studio_apply),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (dirty && !applying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
        // Session preset: save the candidate, wander off, restore it. Applying
        // remains separate and is what survives a restart (see strings note).
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(enabled = !applying) { viewModel.saveStudioPreset(draft) }
            ) {
                Text(
                    stringResource(R.string.studio_save_preset),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            if (studioPreset != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable(enabled = !applying) { edit(studioPreset!!) }
                ) {
                    Text(
                        stringResource(R.string.studio_restore_preset),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Text(
                    stringResource(R.string.studio_preset_note),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (applyState == StudioApplyState.FAILED) {
            Text(
                stringResource(R.string.studio_apply_failed),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        // --- Themes: the premium collection first, every other identity after ---
        StudioSectionHeader(stringResource(R.string.studio_section_themes))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(LumaDesignTokens.SP_4.dp)
        ) {
            ThemePresets.PREMIUM.forEach { spec ->
                PremiumThemeCard(
                    spec = spec,
                    theme = rememberShippedTheme(spec, environment),
                    selected = resolved.id == spec.id,
                    modifier = Modifier.width(148.dp),
                    onClick = {
                        previewFocus = spec.id
                        edit(draft.withThemeSelection(spec.id))
                    }
                )
            }
        }
        Spacer(Modifier.height(LumaDesignTokens.SP_6.dp))
        StudioSectionHeader(stringResource(R.string.studio_section_more_themes))
        MoreThemesRow(
            specs = ThemePresets.SIGNATURE + ThemePresets.SCREENSHOT,
            environment = environment,
            selectedId = resolved.id,
            onSelected = { id ->
                previewFocus = id
                edit(draft.withThemeSelection(id))
            }
        )

        Spacer(Modifier.height(LumaDesignTokens.SP_7.dp))

        // --- Appearance: one theme always, or a light/dark pair ---
        StudioSectionHeader(stringResource(R.string.studio_section_appearance))
        StudioCard {
            Column(Modifier.padding(LumaDesignTokens.SP_5.dp)) {
                SegmentedControl(
                    options = listOf(
                        ThemeSwitching.FIXED to stringResource(R.string.appearance_fixed),
                        ThemeSwitching.SYSTEM to stringResource(R.string.appearance_system),
                        ThemeSwitching.SCHEDULE to stringResource(R.string.appearance_schedule)
                    ),
                    selected = draft.theme.switching,
                    onSelected = { mode -> edit(draft.withAppearance(mode)) }
                )
                Spacer(Modifier.height(LumaDesignTokens.SP_3.dp))
                Text(
                    stringResource(
                        when (draft.theme.switching) {
                            ThemeSwitching.FIXED -> R.string.appearance_fixed_desc
                            ThemeSwitching.SYSTEM -> R.string.appearance_system_desc
                            ThemeSwitching.SCHEDULE -> R.string.appearance_schedule_desc
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (draft.theme.switching != ThemeSwitching.FIXED) {
                HorizontalDividerThin()
                val lightOptions = remember { ThemePresets.ALL.filter { !it.dark }.map { it.id } }
                val darkOptions = remember { ThemePresets.ALL.filter { it.dark }.map { it.id } }
                CompactChoiceRow(
                    label = stringResource(R.string.appearance_light_theme),
                    selected = ThemePresets.of(draft.theme.lightThemeId).displayName,
                    options = lightOptions,
                    labelFor = { ThemePresets.of(it).displayName }
                ) { id ->
                    previewFocus = id
                    edit(draft.copy(theme = draft.theme.copy(lightThemeId = id)))
                }
                HorizontalDividerThin()
                CompactChoiceRow(
                    label = stringResource(R.string.appearance_dark_theme),
                    selected = ThemePresets.of(draft.theme.darkThemeId).displayName,
                    options = darkOptions,
                    labelFor = { ThemePresets.of(it).displayName }
                ) { id ->
                    previewFocus = id
                    edit(draft.copy(theme = draft.theme.copy(darkThemeId = id)))
                }
                if (draft.theme.switching == ThemeSwitching.SCHEDULE) {
                    HorizontalDividerThin()
                    val windows = remember {
                        listOf(
                            DarkWindow.DEFAULT,
                            DarkWindow(18 * 60, 6 * 60),
                            DarkWindow(20 * 60, 7 * 60),
                            DarkWindow(21 * 60, 7 * 60),
                            DarkWindow(22 * 60, 6 * 60)
                        )
                    }
                    val windowLabel: (DarkWindow) -> String = { it.encode().replace("-", " – ") }
                    CompactChoiceRow(
                        label = stringResource(R.string.appearance_dark_window),
                        selected = windowLabel(draft.theme.darkWindow),
                        options = (windows + draft.theme.darkWindow).distinct(),
                        labelFor = { windowLabel(it) }
                    ) { window -> edit(draft.copy(theme = draft.theme.copy(darkWindow = window))) }
                }
                // Which half of the pair the preview above is showing.
                Text(
                    stringResource(
                        R.string.appearance_previewing,
                        "${resolved.displayName} · " + stringResource(
                            if (resolved.isDark) R.string.appearance_mode_dark else R.string.appearance_mode_light
                        )
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = LumaDesignTokens.SP_5.dp, end = LumaDesignTokens.SP_5.dp,
                        top = LumaDesignTokens.SP_2.dp, bottom = LumaDesignTokens.SP_4.dp
                    )
                )
            }
        }

        Spacer(Modifier.height(LumaDesignTokens.SP_7.dp))

        // --- Accent: curated for the theme being edited ---
        StudioSectionHeader(stringResource(R.string.studio_accent))
        StudioCard {
            Column(Modifier.padding(LumaDesignTokens.SP_5.dp)) {
                val accentSpec = ThemePresets.of(resolved.id)
                Text(
                    if (draft.theme.accentOverride == null && !draft.theme.dynamicWallpaperColors)
                        stringResource(R.string.accent_theme_default, accentSpec.displayName)
                    else stringResource(R.string.accent_custom),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(LumaDesignTokens.SP_4.dp))
                AccentSwatchRow(
                    themeId = resolved.id,
                    accentOverride = draft.theme.accentOverride,
                    onSelected = { acc ->
                        edit(
                            draft.copy(
                                theme = draft.theme.copy(accentOverride = acc).let {
                                    if (acc != null) it.withDynamicWallpaperColors(false) else it
                                }
                            )
                        )
                    }
                )
            }
            HorizontalDividerThin()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LumaDesignTokens.SP_5.dp, vertical = LumaDesignTokens.SP_4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.accent_dynamic), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.accent_dynamic_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(LumaDesignTokens.SP_4.dp))
                Switch(
                    checked = draft.theme.dynamicWallpaperColors,
                    onCheckedChange = { enabled ->
                        edit(
                            draft.copy(
                                theme = draft.theme.withDynamicWallpaperColors(enabled).let {
                                    if (enabled) it.copy(accentOverride = null, backgroundOverride = null) else it
                                }
                            )
                        )
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(Modifier.height(LumaDesignTokens.SP_7.dp))

        // --- Active Launcher Effects Readout ---
        StudioSectionHeader(
            stringResource(R.string.studio_section_summary),
            status = stringResource(if (dirty) R.string.studio_config_state_preview else R.string.studio_config_state_saved),
            statusHighlighted = dirty
        )
        LauncherEffectsReadoutCard(
            theme = resolved,
            preset = draft.homePreset,
            dock = draft.dockStyle ?: draft.homePreset.dock,
            effects = draft.effects,
            livingIcons = draft.livingIconsEnabled,
            backdropText = backdropLabel(draft.theme.backdrop, resolved.id, WallpaperArt.forTheme(resolved.id) != null || activeWallpaper != null),
            iconPackText = packs.firstOrNull { it.packageName == draft.iconPack }?.label
                ?: draft.iconPack ?: stringResource(R.string.studio_icon_pack_none)
        )

        Spacer(Modifier.height(LumaDesignTokens.SP_7.dp))

        // --- Pure Launcher Effects: Composition & Dock Architecture ---
        StudioSectionHeader(
            stringResource(R.string.studio_section_layout),
            actionLabel = stringResource(R.string.studio_reset_short),
            actionEnabled = !applying
        ) { edit(draft.copy(homePreset = saved.homePreset, dockStyle = saved.dockStyle)) }
        StudioCard {
            Column(Modifier.padding(LumaDesignTokens.SP_5.dp)) {
                Text(
                    text = stringResource(R.string.studio_layout),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = LumaDesignTokens.SP_3.dp)
                )
                SegmentedControl(
                    options = listOf(
                        HomePreset.INSTRUMENT to HomePreset.INSTRUMENT.displayName,
                        HomePreset.HORIZON to HomePreset.HORIZON.displayName,
                        HomePreset.GRID to HomePreset.GRID.displayName,
                        HomePreset.ZEN to HomePreset.ZEN.displayName
                    ),
                    selected = draft.homePreset,
                    onSelected = { preset ->
                        val updated = draft.copy(homePreset = preset, dockStyle = preset.dock)
                        edit(updated)
                    }
                )
            }
            HorizontalDividerThin()
            Column(Modifier.padding(LumaDesignTokens.SP_5.dp)) {
                Text(
                    text = stringResource(R.string.studio_dock_style),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = LumaDesignTokens.SP_3.dp)
                )
                val effectiveDock = draft.dockStyle ?: draft.homePreset.dock
                SegmentedControl(
                    options = listOf(
                        DockStyle.FLOAT to dockLabel(DockStyle.FLOAT),
                        DockStyle.BEZEL to dockLabel(DockStyle.BEZEL),
                        DockStyle.MINIMAL to dockLabel(DockStyle.MINIMAL)
                    ),
                    selected = effectiveDock,
                    onSelected = { style ->
                        val updated = draft.copy(dockStyle = style)
                        edit(updated)
                    }
                )
            }
        }

        Spacer(Modifier.height(LumaDesignTokens.SP_7.dp))

        // --- Pure Launcher Effects: Optical Material, Blur & Physics ---
        StudioSectionHeader(
            stringResource(R.string.studio_section_effects),
            actionLabel = stringResource(R.string.studio_reset_short),
            actionEnabled = !applying
        ) { edit(draft.copy(effects = saved.effects, motion = saved.motion, theme = draft.theme.copy(backdrop = saved.theme.backdrop))) }
        StudioCard {
            Column(Modifier.padding(LumaDesignTokens.SP_5.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = LumaDesignTokens.SP_3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.studio_effects),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.studio_effects_now, effectsLevelLabel(effectsDecision.level)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                SegmentedControl(
                    options = listOf(
                        EffectsPreference.AUTO to effectsLabel(EffectsPreference.AUTO),
                        EffectsPreference.FULL to effectsLabel(EffectsPreference.FULL),
                        EffectsPreference.BALANCED to effectsLabel(EffectsPreference.BALANCED),
                        EffectsPreference.MINIMAL to effectsLabel(EffectsPreference.MINIMAL)
                    ),
                    selected = draft.effects,
                    onSelected = { pref ->
                        val updated = draft.copy(effects = pref)
                        edit(updated)
                    }
                )
            }
            HorizontalDividerThin()
            Column(Modifier.padding(LumaDesignTokens.SP_5.dp)) {
                Text(
                    text = stringResource(R.string.studio_motion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = LumaDesignTokens.SP_3.dp)
                )
                SegmentedControl(
                    options = listOf(
                        MotionPreference.FULL to motionLabel(MotionPreference.FULL),
                        MotionPreference.REDUCED to motionLabel(MotionPreference.REDUCED),
                        MotionPreference.OFF to motionLabel(MotionPreference.OFF)
                    ),
                    selected = draft.motion,
                    onSelected = { motionPref ->
                        val updated = draft.copy(motion = motionPref)
                        edit(updated)
                    }
                )
            }
            HorizontalDividerThin()
            val hasLumaArt = WallpaperArt.forTheme(resolved.id) != null || activeWallpaper != null
            val backdropOptions = listOf<Backdrop?>(null, Backdrop.THEME, Backdrop.WALLPAPER) +
                listOfNotNull(Backdrop.LUMA.takeIf { hasLumaArt })
            CompactChoiceRow(
                label = stringResource(R.string.studio_backdrop),
                selected = backdropLabel(draft.theme.backdrop, resolved.id, hasLumaArt),
                options = backdropOptions,
                labelFor = { backdropLabel(it, resolved.id, hasLumaArt) }
            ) { bd ->
                val updated = draft.copy(theme = draft.theme.copy(backdrop = bd))
                edit(updated)
            }
            HorizontalDividerThin()
            CompactNavRow(
                title = stringResource(R.string.wallpaper_studio_title),
                subtitle = activeWallpaper?.let { "${it.title} · ${it.mood}" } ?: stringResource(R.string.wallpaper_discover_caption),
                onClick = { context.startActivity(android.content.Intent(context, WallpaperActivity::class.java)) }
            )
        }

        Spacer(Modifier.height(LumaDesignTokens.SP_7.dp))

        // --- Iconography & Glyph Treatment ---
        StudioSectionHeader(
            stringResource(R.string.studio_section_icons),
            actionLabel = stringResource(R.string.studio_reset_short),
            actionEnabled = !applying
        ) { edit(draft.copy(iconPack = saved.iconPack, livingIconsEnabled = saved.livingIconsEnabled, theme = draft.theme.copy(iconShape = saved.theme.iconShape, iconStyle = saved.theme.iconStyle))) }
        StudioCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LumaDesignTokens.SP_5.dp, vertical = LumaDesignTokens.SP_4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.pref_living_icons),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.pref_living_icons_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(LumaDesignTokens.SP_4.dp))
                Switch(
                    checked = draft.livingIconsEnabled,
                    onCheckedChange = { enabled ->
                        val updated = draft.copy(livingIconsEnabled = enabled)
                        edit(updated)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
            HorizontalDividerThin()
            CompactChoiceRow(
                label = stringResource(R.string.studio_icon_shape),
                selected = draft.theme.iconShape?.let { shapeLabel(it) } ?: stringResource(R.string.studio_theme_default),
                options = listOf<IconShape?>(null) + IconShape.entries,
                labelFor = { it?.let { shapeLabel(it) } ?: stringResource(R.string.studio_theme_default) }
            ) { shape ->
                val updated = draft.copy(theme = draft.theme.copy(iconShape = shape))
                edit(updated)
            }
            HorizontalDividerThin()
            CompactChoiceRow(
                label = stringResource(R.string.studio_icon_style),
                selected = draft.theme.iconStyle?.let { iconStyleLabel(it) } ?: stringResource(R.string.studio_theme_default),
                options = listOf<IconStyle?>(null) + IconStyle.entries,
                labelFor = { it?.let { iconStyleLabel(it) } ?: stringResource(R.string.studio_theme_default) }
            ) { style ->
                val updated = draft.copy(theme = draft.theme.copy(iconStyle = style))
                edit(updated)
            }
            if (packs.isNotEmpty() || draft.iconPack != null) {
                HorizontalDividerThin()
                val packOptions = listOf<String?>(null) + packs.map { it.packageName } +
                    listOfNotNull(draft.iconPack.takeIf { p -> p != null && packs.none { it.packageName == p } })
                CompactChoiceRow(
                    label = stringResource(R.string.studio_icon_pack),
                    selected = packs.firstOrNull { it.packageName == draft.iconPack }?.label
                        ?: draft.iconPack
                        ?: stringResource(R.string.studio_icon_pack_none),
                    options = packOptions,
                    labelFor = { p ->
                        if (p == null) stringResource(R.string.studio_icon_pack_none)
                        else packs.firstOrNull { it.packageName == p }?.label ?: p
                    }
                ) { pack ->
                    val updated = draft.copy(iconPack = pack)
                    edit(updated)
                }
            }
        }

        Spacer(Modifier.height(LumaDesignTokens.SP_7.dp))

        // --- Optional Fine-Tune Overrides (Collapsible) ---
        var advancedOpen by rememberSaveable { mutableStateOf(false) }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { advancedOpen = !advancedOpen }
        ) {
            Row(Modifier.fillMaxWidth().padding(LumaDesignTokens.SP_5.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (advancedOpen) R.string.studio_advanced_hide else R.string.studio_advanced_show),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.advanced_summary_premium),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(if (advancedOpen) "−" else "+", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
            }
        }

        if (advancedOpen) {
            Spacer(Modifier.height(LumaDesignTokens.SP_6.dp))
            StudioSectionHeader(stringResource(R.string.accent_all))
            StudioCard {
                Box(Modifier.padding(LumaDesignTokens.SP_5.dp)) {
                    SwatchCompact(
                        colors = ThemePresets.ACCENT_SWATCHES,
                        selected = draft.theme.accentOverride,
                        onSelected = { acc ->
                            val updated = draft.copy(
                                theme = draft.theme.copy(accentOverride = acc).let {
                                    if (acc != null) it.withDynamicWallpaperColors(false) else it
                                }
                            )
                            edit(updated)
                        }
                    )
                }
            }

            Spacer(Modifier.height(LumaDesignTokens.SP_6.dp))
            StudioSectionHeader(stringResource(R.string.studio_typography))
            StudioCard {
                CompactChoiceRow(
                    label = stringResource(R.string.studio_typography),
                    selected = draft.theme.typography?.let { typeLabel(it) } ?: stringResource(R.string.studio_theme_default),
                    options = listOf<TypeStyle?>(null) + TypeStyle.entries,
                    labelFor = { it?.let { typeLabel(it) } ?: stringResource(R.string.studio_theme_default) }
                ) { typo ->
                    val updated = draft.copy(theme = draft.theme.copy(typography = typo))
                    edit(updated)
                }
            }

            Spacer(Modifier.height(LumaDesignTokens.SP_6.dp))
            StudioSectionHeader(stringResource(R.string.studio_theme_additional))
            StudioCard {
                CompactThemeRow(
                    heading = stringResource(R.string.studio_theme_additional),
                    presets = ThemePresets.ADDITIONAL,
                    selectedId = draft.theme.themeId,
                    onSelected = { id ->
                        previewFocus = id
                        edit(draft.withThemeSelection(id))
                    }
                )
            }

            Spacer(Modifier.height(LumaDesignTokens.SP_6.dp))
            StudioSectionHeader(stringResource(R.string.studio_profile_themes))
            StudioCard {
                ContextProfile.entries.forEachIndexed { idx, profile ->
                    if (idx > 0) HorizontalDividerThin()
                    CompactChoiceRow(
                        label = profile.title,
                        selected = draft.theme.profileThemes[profile]?.let { ThemePresets.of(it).displayName }
                            ?: stringResource(R.string.studio_profile_theme_none),
                        options = listOf<ThemeId?>(null) + ThemePresets.ALL.map { it.id },
                        labelFor = { it?.let { ThemePresets.of(it).displayName } ?: stringResource(R.string.studio_profile_theme_none) }
                    ) { id ->
                        val m = draft.theme.profileThemes.toMutableMap()
                        if (id == null) m.remove(profile) else m[profile] = id
                        val updated = draft.copy(theme = draft.theme.copy(profileThemes = m))
                        edit(updated)
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Displays the active theme's holistic launcher effects configuration so the user
 * sees how the selected theme transforms layout, dock, refraction, and motion.
 */
@Composable
private fun LauncherEffectsReadoutCard(
    theme: com.lumaos.pro.launcher.ui.theme.ResolvedTheme,
    preset: HomePreset,
    dock: DockStyle,
    effects: EffectsPreference,
    livingIcons: Boolean,
    backdropText: String,
    iconPackText: String
) {
    val blurText = if (!theme.usesBlur || effects == EffectsPreference.MINIMAL) {
        "0dp Solid Stone"
    } else {
        "${theme.tokens?.material?.blurDp?.toInt() ?: 26}dp Blur · ${((theme.tokens?.material?.saturation ?: 1.35f) * 100).toInt()}% Sat"
    }
    val dockText = when (dock) {
        DockStyle.FLOAT -> "Floating (${theme.radius(com.lumaos.pro.launcher.ui.theme.RadiusRole.DOCK).toInt()}dp)"
        DockStyle.BEZEL -> "Edge Bezel (0dp)"
        DockStyle.MINIMAL -> "Bare Minimal"
    }
    val iconText = "${theme.iconTileRadiusDp.toInt()}dp ${theme.iconShape.name.lowercase().replaceFirstChar { it.uppercase() }}" +
        if (livingIcons) " · Living" else " · Glyph"
    val motionText = when (theme.id) {
        ThemeId.MONOLITH -> "Snappy (560 / 1.0)"
        ThemeId.ATMOSPHERE -> "Fluid (310 / 0.78)"
        else -> "Precision (380 / 0.84)"
    }

    StudioCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EffectBadge(
                    label = "COMPOSITION",
                    value = "${preset.displayName} · ${theme.clockSp.toInt()}sp",
                    modifier = Modifier.weight(1f)
                )
                EffectBadge(
                    label = "DOCK SURFACE",
                    value = dockText,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EffectBadge(
                    label = "MATERIAL",
                    value = blurText,
                    modifier = Modifier.weight(1f)
                )
                EffectBadge(
                    label = "ICONS & PHYSICS",
                    value = "$iconText · $motionText",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EffectBadge(
                    label = "BACKDROP",
                    value = backdropText,
                    modifier = Modifier.weight(1f)
                )
                EffectBadge(
                    label = "ICON PACK",
                    value = iconPackText,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EffectBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------- Clean primitives for Studio ----------

/**
 * Section heading: sentence-case title in the theme's title voice, with an
 * optional trailing action (Reset) or status on the same baseline, so no
 * section spends a whole row on a lone link.
 */
@Composable
private fun StudioSectionHeader(
    title: String,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    status: String? = null,
    statusHighlighted: Boolean = false,
    onAction: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = LumaDesignTokens.SP_2.dp,
                end = LumaDesignTokens.SP_1.dp,
                top = LumaDesignTokens.SP_2.dp,
                bottom = LumaDesignTokens.SP_3.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = if (actionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = actionEnabled, onClick = onAction)
                    .padding(horizontal = LumaDesignTokens.SP_3.dp, vertical = LumaDesignTokens.SP_2.dp)
            )
        }
    }
}

@Composable
private fun StudioCard(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column { content() }
    }
}

@Composable
private fun HorizontalDividerThin() {
    HorizontalDivider(
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun CompactThemeRow(
    heading: String,
    presets: List<ThemeSpec>,
    selectedId: ThemeId,
    onSelected: (ThemeId) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            heading,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { spec ->
                val selected = selectedId == spec.id
                val card = remember(spec) { ThemeCardPalette.forSpec(spec) }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Color(card.background),
                    modifier = Modifier
                        .width(124.dp)
                        .height(76.dp)
                        .border(
                            if (selected) 2.dp else 0.6.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            MaterialTheme.shapes.small
                        )
                        .clickable { onSelected(spec.id) }
                        .semantics {
                            role = Role.RadioButton
                            this.selected = selected
                            contentDescription = spec.displayName
                        }
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            spec.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = Color(card.content),
                            maxLines = 1
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(spec.accent, spec.secondary, spec.neutralTint).forEach { sw ->
                                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(sw)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> CompactChoiceRow(
    label: String,
    selected: String,
    options: List<T>,
    labelFor: @Composable (T) -> String = { it.toString() },
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
        Text(selected, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
    }
    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
                Column(Modifier.padding(vertical = 8.dp).width(280.dp)) {
                    options.forEach { opt ->
                        val text = labelFor(opt)
                        val isSel = text == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(opt)
                                    expanded = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSel) Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactNavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
    }
}

@Composable
private fun <T> SegmentedControl(options: List<Pair<T, String>>, selected: T, onSelected: (T) -> Unit) {
    // The selected segment must read at a glance: a raised white pill on
    // light paper, a lifted onSurface veil on dark glass (surface-on-surface
    // was nearly invisible on the dark themes).
    val dark = com.lumaos.pro.launcher.settings.LocalLumaTheme.current.isDark
    val selectedFill = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f) else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { (value, label) ->
            val sel = value == selected
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (sel) selectedFill else Color.Transparent,
                shadowElevation = if (sel && !dark) 1.dp else 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelected(value) }
                    .semantics {
                        role = Role.RadioButton
                        this.selected = sel
                        contentDescription = label
                    }
            ) {
                Text(
                    label,
                    modifier = Modifier
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SwatchCompact(colors: List<Int>, selected: Int?, onSelected: (Int?) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .clickable { onSelected(null) }
                .semantics {
                    role = Role.RadioButton
                    this.selected = selected == null
                }
        ) {
            Text(
                stringResource(R.string.studio_theme_default),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
        colors.forEach { c ->
            val chosen = selected == c
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .clickable { onSelected(c) }
                    .border(if (chosen) 2.dp else 0.dp, if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = chosen
                        contentDescription = ColorMath.toHex(c)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (chosen) Box(Modifier.size(8.dp).clip(CircleShape).background(Color(ThemeEngine.bestTextOn(c))))
            }
        }
    }
}

@Composable
private fun typeLabel(v: TypeStyle) = stringResource(
    when (v) {
        TypeStyle.PRECISION -> R.string.type_precision
        TypeStyle.AMBIENT -> R.string.type_ambient
        TypeStyle.MONOGRAPH -> R.string.type_monograph
        TypeStyle.SERENE -> R.string.type_serene
        TypeStyle.GROTESK -> R.string.type_grotesk
        TypeStyle.SERIF -> R.string.type_serif
        TypeStyle.MONO -> R.string.type_mono
    }
)

@Composable
private fun backdropLabel(v: Backdrop?, id: ThemeId, hasSelected: Boolean) = when (v) {
    null -> stringResource(R.string.studio_theme_default)
    Backdrop.WALLPAPER -> stringResource(R.string.studio_backdrop_wallpaper)
    Backdrop.THEME -> stringResource(R.string.studio_backdrop_theme)
    Backdrop.LUMA -> if (WallpaperArt.forTheme(id) != null || hasSelected) stringResource(R.string.studio_backdrop_luma) else stringResource(R.string.studio_backdrop_theme)
}

@Composable
private fun shapeLabel(v: IconShape) = when (v) {
    IconShape.SYSTEM -> stringResource(R.string.shape_system)
    IconShape.SQUIRCLE -> stringResource(R.string.shape_squircle)
    IconShape.CIRCLE -> stringResource(R.string.shape_circle)
    IconShape.ROUNDED -> stringResource(R.string.shape_rounded)
    IconShape.TEARDROP -> stringResource(R.string.shape_teardrop)
    IconShape.OCTAGON -> stringResource(R.string.shape_octagon)
}

@Composable
private fun iconStyleLabel(v: IconStyle) = stringResource(
    when (v) {
        IconStyle.NATURAL -> R.string.style_natural
        IconStyle.PLATE -> R.string.style_plate
        IconStyle.TINTED -> R.string.style_tinted
    }
)

@Composable
private fun dockLabel(v: DockStyle) = stringResource(
    when (v) {
        DockStyle.BEZEL -> R.string.dock_bezel
        DockStyle.FLOAT -> R.string.dock_float
        DockStyle.MINIMAL -> R.string.dock_minimal
    }
)

@Composable
private fun effectsLabel(v: EffectsPreference) = stringResource(
    when (v) {
        EffectsPreference.AUTO -> R.string.effects_auto
        EffectsPreference.MINIMAL -> R.string.effects_minimal
        EffectsPreference.BALANCED -> R.string.effects_balanced
        EffectsPreference.FULL -> R.string.effects_full
    }
)

@Composable
private fun motionLabel(v: MotionPreference) = stringResource(
    when (v) {
        MotionPreference.FULL -> R.string.motion_full
        MotionPreference.REDUCED -> R.string.motion_reduced
        MotionPreference.OFF -> R.string.motion_off
    }
)

@Composable
private fun effectsLevelLabel(v: EffectsLevel) = stringResource(
    when (v) {
        EffectsLevel.MINIMAL -> R.string.effects_level_minimal
        EffectsLevel.BALANCED -> R.string.effects_level_balanced
        EffectsLevel.FULL -> R.string.effects_level_full
    }
)
