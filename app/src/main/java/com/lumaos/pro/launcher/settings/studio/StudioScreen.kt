package com.lumaos.pro.launcher.settings.studio

import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.lumaos.pro.launcher.settings.LocalLumaTheme
import com.lumaos.pro.launcher.settings.SettingsViewModel
import com.lumaos.pro.launcher.settings.StudioApplyState
import com.lumaos.pro.launcher.settings.StudioConfiguration
import com.lumaos.pro.launcher.settings.StudioPreviewIcons
import com.lumaos.pro.launcher.wallpaper.WallpaperActivity
import com.lumaos.pro.launcher.wallpaper.WallpaperCatalog
import com.lumaos.pro.launcher.wallpaper.WallpaperImageStore
import com.lumaos.pro.launcher.ui.theme.Backdrop
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.IconShape
import com.lumaos.pro.launcher.ui.theme.IconStyle
import com.lumaos.pro.launcher.ui.theme.LumaGlassIdentity
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemeCardPalette
import com.lumaos.pro.launcher.ui.theme.ThemePresets
import com.lumaos.pro.launcher.ui.theme.ThemeSpec
import com.lumaos.pro.launcher.ui.theme.ThemeSwitching
import com.lumaos.pro.launcher.ui.theme.TypeStyle
import com.lumaos.pro.launcher.ui.theme.WallpaperArt
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
    val wallpaper = environment.wallpaper
    val activeWallpaper by viewModel.themes.activeWallpaper.collectAsState()
    val effectsDecision by viewModel.effectsState.collectAsState()
    val packs by viewModel.iconPacks.collectAsState()
    val activeProfile by viewModel.prefs.activeContextProfile.collectAsState(ContextProfile.PERSONAL)
    val resolved = remember(draft.theme, environment) { ThemeEngine.resolve(draft.theme, environment) }
    val previewDepth = when (draft.effects) {
        EffectsPreference.MINIMAL -> false
        EffectsPreference.BALANCED, EffectsPreference.FULL -> true
        EffectsPreference.AUTO -> effectsDecision.iconDepth
    }
    val bitmaps by produceState(StudioPreviewIcons(emptyList(), emptyList()), resolved, draft.iconPack, draft.livingIconsEnabled, draft.theme.iconStyle, previewDepth) {
        value = viewModel.loadPreviewIcons(resolved, draft.iconPack, previewDepth, draft.livingIconsEnabled, draft.theme.iconStyle == IconStyle.TINTED)
    }
    val context = LocalContext.current
    val artwork by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, resolved.id, resolved.backdrop, activeWallpaper?.id) {
        val selectedEntry = activeWallpaper
        value = when {
            selectedEntry != null && (resolved.backdrop == Backdrop.LUMA || resolved.backdrop == Backdrop.WALLPAPER) ->
                runCatching { WallpaperImageStore(context.applicationContext).preview(selectedEntry, 640)?.asImageBitmap() }.getOrNull()
            resolved.backdrop == Backdrop.LUMA -> WallpaperArt.forTheme(resolved.id)?.let { art ->
                // The wallpaper store's own cached IO decode, through the legacy
                // entry that wraps this theme art. produceState runs on the
                // composition's main-thread context, and a bundled wallpaper is a
                // full-screen webp: decoding it inline - with a fixed inSampleSize
                // and no bounds pass, so still near full size - was what made
                // Studio drop frames while scrolling themes, and re-decoded the
                // same art on every recomposition instead of hitting the cache.
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
    val now = remember { Date() }
    val timeText = remember { SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale).format(now) }
    val dateText = remember { SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEE MMM d"), locale).format(now) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        // --- Restrained preview frame ---
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth().height(280.dp).border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), MaterialTheme.shapes.large)
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.studio_contrast, String.format(locale, "%.1f", ColorMath.contrast(resolved.colors.onSurface, resolved.colors.surface1))),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Apply bar — mint primary, revert secondary
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(when { applying -> R.string.studio_applying; dirty -> R.string.studio_unsaved; else -> R.string.studio_applied }),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = if (dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (dirty) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable(enabled = !applying) { draft = saved }) {
                        Text(stringResource(R.string.studio_revert), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
                Surface(shape = MaterialTheme.shapes.small, color = if (dirty && !applying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable(enabled = dirty && !applying) { viewModel.applyStudio(draft) }) {
                    Text(stringResource(R.string.studio_apply), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = if (dirty && !applying) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
        if (applyState == StudioApplyState.FAILED) {
            Text(stringResource(R.string.studio_apply_failed), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(20.dp))

        // --- Themes horizontal ---
        StudioSectionHeader(stringResource(R.string.studio_themes))
        StudioCard {
            CompactThemeRow(
                heading = stringResource(R.string.studio_theme_signature),
                presets = ThemePresets.SIGNATURE,
                selectedId = draft.theme.themeId,
                onSelected = { draft = draft.copy(theme = draft.theme.withPreset(it)) }
            )
            HorizontalDividerThin()
            CompactThemeRow(
                heading = stringResource(R.string.studio_theme_additional),
                presets = ThemePresets.ADDITIONAL,
                selectedId = draft.theme.themeId,
                onSelected = { draft = draft.copy(theme = draft.theme.withPreset(it)) }
            )
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(ThemePresets.of(draft.theme.themeId).tagline, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))

        // Glass identity — compact horizontal chips
        StudioSectionHeader(LumaGlassIdentity.NAME)
        StudioCard {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LumaGlassIdentity.ACCENTS.forEach { accent ->
                    val selected = draft.theme.accentOverride == accent.color && draft.theme.themeId == accent.darkAnchor
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { draft = draft.copy(theme = accent.applyTo(draft.theme)) }
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(accent.color)))
                            Spacer(Modifier.width(6.dp))
                            Text(accent.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            HorizontalDividerThin()
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(LumaGlassIdentity.TAGLINE, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))

        // Switching — segmented
        StudioSectionHeader(stringResource(R.string.studio_switching))
        StudioCard {
            Box(Modifier.padding(12.dp)) {
                SegmentedControl(
                    options = listOf(ThemeSwitching.FIXED to stringResource(R.string.studio_switch_fixed), ThemeSwitching.SYSTEM to stringResource(R.string.studio_switch_system), ThemeSwitching.SCHEDULE to stringResource(R.string.studio_switch_schedule)),
                    selected = draft.theme.switching,
                    onSelected = { draft = draft.copy(theme = draft.theme.copy(switching = it)) }
                )
            }
            if (draft.theme.switching != ThemeSwitching.FIXED) {
                HorizontalDividerThin()
                CompactChoiceRow(label = stringResource(R.string.studio_light_theme), selected = ThemePresets.of(draft.theme.lightThemeId).displayName, options = ThemePresets.ALL.map { it.id }) { draft = draft.copy(theme = draft.theme.copy(lightThemeId = it)) }
                HorizontalDividerThin()
                CompactChoiceRow(label = stringResource(R.string.studio_dark_theme), selected = ThemePresets.of(draft.theme.darkThemeId).displayName, options = ThemePresets.ALL.map { it.id }) { draft = draft.copy(theme = draft.theme.copy(darkThemeId = it)) }
            }
            if (draft.theme.switching == ThemeSwitching.SCHEDULE) {
                HorizontalDividerThin()
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimePill(stringResource(R.string.studio_dark_from), draft.theme.darkWindow.startMinute) { draft = draft.copy(theme = draft.theme.copy(darkWindow = draft.theme.darkWindow.copy(startMinute = it))) }
                    TimePill(stringResource(R.string.studio_dark_until), draft.theme.darkWindow.endMinute) { draft = draft.copy(theme = draft.theme.copy(darkWindow = draft.theme.darkWindow.copy(endMinute = it))) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Accent — swatches
        StudioSectionHeader(stringResource(R.string.studio_accent))
        StudioCard {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Wallpaper theming", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
                Switch(checked = draft.theme.dynamicWallpaperColors, onCheckedChange = { enabled ->
                    draft = draft.copy(theme = draft.theme.withDynamicWallpaperColors(enabled).let { if (enabled) it.copy(accentOverride = null, backgroundOverride = null) else it })
                }, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White))
            }
            HorizontalDividerThin()
            Box(Modifier.padding(12.dp)) {
                SwatchCompact(colors = ThemePresets.ACCENT_SWATCHES, selected = draft.theme.accentOverride, onSelected = { draft = draft.copy(theme = draft.theme.copy(accentOverride = it)) })
            }
            HorizontalDividerThin()
            CompactNavRow(title = stringResource(R.string.wallpaper_studio_title), subtitle = activeWallpaper?.let { "${it.title} · ${it.mood}" } ?: stringResource(R.string.wallpaper_discover_caption), onClick = { context.startActivity(android.content.Intent(context, WallpaperActivity::class.java)) })
        }
        Spacer(Modifier.height(12.dp))

        // Icons — shape / style / pack
        StudioSectionHeader(stringResource(R.string.studio_icons))
        StudioCard {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_living_icons), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    Text(stringResource(R.string.pref_living_icons_desc), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = draft.livingIconsEnabled, onCheckedChange = { draft = draft.copy(livingIconsEnabled = it) }, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White))
            }
            HorizontalDividerThin()
            CompactChoiceRow(label = stringResource(R.string.studio_icon_shape), selected = draft.theme.iconShape?.let { shapeLabel(it) } ?: stringResource(R.string.studio_theme_default), options = listOf<IconShape?>(null) + IconShape.entries, labelFor = { it?.let { shapeLabel(it) } ?: stringResource(R.string.studio_theme_default) }) { draft = draft.copy(theme = draft.theme.copy(iconShape = it)) }
            HorizontalDividerThin()
            CompactChoiceRow(label = stringResource(R.string.studio_icon_style), selected = draft.theme.iconStyle?.let { iconStyleLabel(it) } ?: stringResource(R.string.studio_theme_default), options = listOf<IconStyle?>(null) + IconStyle.entries, labelFor = { it?.let { iconStyleLabel(it) } ?: stringResource(R.string.studio_theme_default) }) { draft = draft.copy(theme = draft.theme.copy(iconStyle = it)) }
            HorizontalDividerThin()
            val packOptions = listOf<String?>(null) + packs.map { it.packageName } + listOfNotNull(draft.iconPack.takeIf { p -> p != null && packs.none { it.packageName == p } })
            CompactChoiceRow(label = stringResource(R.string.studio_icon_pack), selected = packs.firstOrNull { it.packageName == draft.iconPack }?.label ?: draft.iconPack ?: stringResource(R.string.studio_icon_pack_none), options = packOptions, labelFor = { p -> if (p == null) stringResource(R.string.studio_icon_pack_none) else packs.firstOrNull { it.packageName == p }?.label ?: p }) { draft = draft.copy(iconPack = it) }
        }
        Spacer(Modifier.height(12.dp))

        // Layout / Dock
        StudioSectionHeader(stringResource(R.string.studio_layout))
        StudioCard {
            CompactChoiceRow(label = stringResource(R.string.studio_layout), selected = draft.homePreset.displayName, options = HomePreset.entries.toList(), labelFor = { it.displayName }) { draft = draft.copy(homePreset = it, dockStyle = null) }
            HorizontalDividerThin()
            CompactChoiceRow(label = stringResource(R.string.studio_dock_style), selected = draft.dockStyle?.let { dockLabel(it) } ?: stringResource(R.string.studio_theme_default), options = listOf<DockStyle?>(null) + DockStyle.entries, labelFor = { it?.let { dockLabel(it) } ?: stringResource(R.string.studio_theme_default) }) { draft = draft.copy(dockStyle = it) }
        }
        Spacer(Modifier.height(12.dp))

        // Advanced collapsible
        var advancedOpen by rememberSaveable { mutableStateOf(false) }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().clickable { advancedOpen = !advancedOpen }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (advancedOpen) R.string.studio_advanced_hide else R.string.studio_advanced_show), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp))
                    Text(stringResource(R.string.studio_advanced_summary), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (advancedOpen) "−" else "+", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
            }
        }
        if (advancedOpen) {
            Spacer(Modifier.height(12.dp))
            StudioSectionHeader(stringResource(R.string.studio_background))
            StudioCard {
                Box(Modifier.padding(12.dp)) { SwatchCompact(colors = ThemePresets.BACKGROUND_SWATCHES, selected = draft.theme.backgroundOverride, onSelected = { draft = draft.copy(theme = draft.theme.copy(backgroundOverride = it)) }) }
                HorizontalDividerThin()
                val hasLumaArt = WallpaperArt.forTheme(resolved.id) != null || activeWallpaper != null
                val backdropOptions = listOf<Backdrop?>(null, Backdrop.WALLPAPER, Backdrop.THEME) + listOfNotNull(Backdrop.LUMA.takeIf { hasLumaArt })
                CompactChoiceRow(label = stringResource(R.string.studio_backdrop), selected = backdropLabel(draft.theme.backdrop, resolved.id, hasLumaArt), options = backdropOptions, labelFor = { backdropLabel(it, resolved.id, hasLumaArt) }) { draft = draft.copy(theme = draft.theme.copy(backdrop = it)) }
            }
            Spacer(Modifier.height(12.dp))
            StudioSectionHeader(stringResource(R.string.studio_typography))
            StudioCard {
                CompactChoiceRow(label = stringResource(R.string.studio_typography), selected = draft.theme.typography?.let { typeLabel(it) } ?: stringResource(R.string.studio_theme_default), options = listOf<TypeStyle?>(null) + TypeStyle.entries, labelFor = { it?.let { typeLabel(it) } ?: stringResource(R.string.studio_theme_default) }) { draft = draft.copy(theme = draft.theme.copy(typography = it)) }
            }
            Spacer(Modifier.height(12.dp))
            StudioSectionHeader(stringResource(R.string.studio_effects))
            StudioCard {
                CompactChoiceRow(label = stringResource(R.string.studio_effects), selected = effectsLabel(draft.effects), options = EffectsPreference.entries.toList(), labelFor = { effectsLabel(it) }) { draft = draft.copy(effects = it) }
                HorizontalDividerThin()
                CompactChoiceRow(label = stringResource(R.string.studio_motion), selected = motionLabel(draft.motion), options = MotionPreference.entries.toList(), labelFor = { motionLabel(it) }) { draft = draft.copy(motion = it) }
                HorizontalDividerThin()
                Box(Modifier.padding(12.dp)) {
                    Column {
                        Text(stringResource(R.string.studio_effects_now, effectsLevelLabel(effectsDecision.level)), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        effectsDecision.reasons.take(2).forEach { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            StudioSectionHeader(stringResource(R.string.studio_profile_themes))
            StudioCard {
                ContextProfile.entries.forEachIndexed { idx, profile ->
                    if (idx > 0) HorizontalDividerThin()
                    CompactChoiceRow(label = profile.title, selected = draft.theme.profileThemes[profile]?.let { ThemePresets.of(it).displayName } ?: stringResource(R.string.studio_profile_theme_none), options = listOf<ThemeId?>(null) + ThemePresets.ALL.map { it.id }, labelFor = { it?.let { ThemePresets.of(it).displayName } ?: stringResource(R.string.studio_profile_theme_none) }) { id ->
                        val m = draft.theme.profileThemes.toMutableMap()
                        if (id == null) m.remove(profile) else m[profile] = id
                        draft = draft.copy(theme = draft.theme.copy(profileThemes = m))
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ---------- Clean primitives for Studio ----------

@Composable private fun StudioSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 4.dp))
}
@Composable private fun StudioCard(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) { Column { content() } }
}
@Composable private fun HorizontalDividerThin() { HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp)) }

@Composable private fun CompactThemeRow(heading: String, presets: List<ThemeSpec>, selectedId: ThemeId, onSelected: (ThemeId) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(heading, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { spec ->
                val selected = selectedId == spec.id
                val card = remember(spec) { ThemeCardPalette.forSpec(spec) }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Color(card.background),
                    modifier = Modifier.width(132.dp).height(88.dp).border(if (selected) 2.dp else 0.6.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small).clickable { onSelected(spec.id) }.semantics { role = Role.RadioButton; this.selected = selected; contentDescription = spec.displayName }
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(spec.displayName, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Color(card.content), maxLines = 1)
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

// `labelFor` runs while this row is composed (the option labels are resolved
// during composition), and the call sites resolve them from string resources and
// composable label helpers. It therefore has to be a composable lambda type.
@Composable private fun <T> CompactChoiceRow(label: String, selected: String, options: List<T>, labelFor: @Composable (T) -> String = { it.toString() }, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { expanded = true }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        Row(Modifier.fillMaxWidth().clickable { onSelected(opt); expanded = false }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal), color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            if (isSel) Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun CompactNavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
    }
}

@Composable private fun <T> SegmentedControl(options: List<Pair<T, String>>, selected: T, onSelected: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEach { (value, label) ->
            val sel = value == selected
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (sel) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (sel) 1.dp else 0.dp,
                modifier = Modifier.weight(1f).clickable { onSelected(value) }
            ) {
                Text(label, modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium), color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable private fun SwatchCompact(colors: List<Int>, selected: Int?, onSelected: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable { onSelected(null) }.semantics { role = Role.RadioButton; this.selected = selected == null }) {
            Text(stringResource(R.string.studio_theme_default), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
        }
        colors.forEach { c ->
            val chosen = selected == c
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c)).clickable { onSelected(c) }.border(if (chosen) 2.dp else 0.dp, if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape).semantics { role = Role.RadioButton; this.selected = chosen; contentDescription = ColorMath.toHex(c) }, contentAlignment = Alignment.Center) {
                if (chosen) Box(Modifier.size(8.dp).clip(CircleShape).background(Color(ThemeEngine.bestTextOn(c))))
            }
        }
    }
}

@Composable private fun TimePill(label: String, minute: Int, onSelected: (Int) -> Unit) {
    val context = LocalContext.current
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable {
        TimePickerDialog(context, { _, h, m -> onSelected(h * 60 + m) }, minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
    }) {
        Text("$label %02d:%02d".format(minute / 60, minute % 60), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable private fun typeLabel(v: TypeStyle) = stringResource(when(v){ TypeStyle.PRECISION -> R.string.type_precision; TypeStyle.AMBIENT -> R.string.type_ambient; TypeStyle.MONOGRAPH -> R.string.type_monograph; TypeStyle.SERENE -> R.string.type_serene; TypeStyle.GROTESK -> R.string.type_grotesk; TypeStyle.SERIF -> R.string.type_serif; TypeStyle.MONO -> R.string.type_mono })
@Composable private fun backdropLabel(v: Backdrop?, id: ThemeId, hasSelected: Boolean) = when(v){ null -> stringResource(R.string.studio_theme_default); Backdrop.WALLPAPER -> stringResource(R.string.studio_backdrop_wallpaper); Backdrop.THEME -> stringResource(R.string.studio_backdrop_theme); Backdrop.LUMA -> if (WallpaperArt.forTheme(id) != null || hasSelected) stringResource(R.string.studio_backdrop_luma) else stringResource(R.string.studio_backdrop_theme) }
@Composable private fun shapeLabel(v: IconShape) = stringResource(when(v){ IconShape.SYSTEM -> R.string.shape_system; IconShape.SQUIRCLE -> R.string.shape_squircle; IconShape.CIRCLE -> R.string.shape_circle; IconShape.ROUNDED -> R.string.shape_rounded; IconShape.TEARDROP -> R.string.shape_teardrop; IconShape.OCTAGON -> R.string.shape_octagon })
@Composable private fun iconStyleLabel(v: IconStyle) = stringResource(when(v){ IconStyle.NATURAL -> R.string.style_natural; IconStyle.PLATE -> R.string.style_plate; IconStyle.TINTED -> R.string.style_tinted })
@Composable private fun dockLabel(v: DockStyle) = stringResource(when(v){ DockStyle.BEZEL -> R.string.dock_bezel; DockStyle.FLOAT -> R.string.dock_float; DockStyle.MINIMAL -> R.string.dock_minimal })
@Composable private fun effectsLabel(v: EffectsPreference) = stringResource(when(v){ EffectsPreference.AUTO -> R.string.effects_auto; EffectsPreference.MINIMAL -> R.string.effects_minimal; EffectsPreference.BALANCED -> R.string.effects_balanced; EffectsPreference.FULL -> R.string.effects_full })
@Composable private fun motionLabel(v: MotionPreference) = stringResource(when(v){ MotionPreference.FULL -> R.string.motion_full; MotionPreference.REDUCED -> R.string.motion_reduced; MotionPreference.OFF -> R.string.motion_off })
@Composable private fun effectsLevelLabel(v: EffectsLevel) = stringResource(when(v){ EffectsLevel.MINIMAL -> R.string.effects_level_minimal; EffectsLevel.BALANCED -> R.string.effects_level_balanced; EffectsLevel.FULL -> R.string.effects_level_full })

