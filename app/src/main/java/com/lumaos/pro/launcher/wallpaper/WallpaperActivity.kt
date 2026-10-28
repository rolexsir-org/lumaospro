package com.lumaos.pro.launcher.wallpaper

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.settings.LumaOSTheme
import com.lumaos.pro.launcher.ui.glass.LumaGlassBottomSheet
import com.lumaos.pro.launcher.ui.glass.LumaGlassCard
import com.lumaos.pro.launcher.ui.glass.LumaGlassChoice
import com.lumaos.pro.launcher.ui.glass.LumaGlassDialog
import com.lumaos.pro.launcher.ui.glass.LumaGlassPreviewFrame
import com.lumaos.pro.launcher.ui.glass.LumaGlassSectionTitle
import com.lumaos.pro.launcher.ui.glass.LumaGlassSurface
import com.lumaos.pro.launcher.ui.glass.LumaGlassSwitchRow
import com.lumaos.pro.launcher.ui.glass.LumaGlassTopBar
import com.lumaos.pro.launcher.ui.theme.Backdrop
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassBackdrop
import com.lumaos.pro.launcher.ui.theme.GlassCapabilities
import com.lumaos.pro.launcher.ui.theme.GlassMaterials
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import com.lumaos.pro.launcher.ui.theme.WallpaperSeeds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Luma Glass Wallpaper Studio: 30 curated high-resolution compositions across 6
 * editorial collections, private user photo imports, live Liquid Glass previewing,
 * and coordinated dynamic wallpaper theming.
 */
class WallpaperActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val core = (application as LumaApp).core
        val store = WallpaperImageStore(applicationContext)
        setContent {
            val theme by core.themes.theme.collectAsState()
            val textScale by core.prefs.textScale.collectAsState(1f)
            LumaOSTheme(theme, textScale) {
                LaunchedEffect(theme.isDark) {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !theme.isDark
                        isAppearanceLightNavigationBars = !theme.isDark
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(theme.colors.background))
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        LumaGlassTopBar(
                            theme = theme,
                            title = stringResource(R.string.wallpaper_studio_title),
                            subtitle = stringResource(R.string.wallpaper_studio_subtitle),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            navigationIcon = {
                                LumaGlassChoice(
                                    theme = theme,
                                    label = stringResource(R.string.wallpaper_back),
                                    selected = false,
                                    onClick = { finish() }
                                )
                            }
                        )
                        WallpaperStudio(
                            store = store,
                            modifier = Modifier.weight(1f),
                            onSystemPicker = {
                                runCatching { startActivity(core.appRepository.wallpaperPickerIntent()) }
                                    .onFailure {
                                        Toast.makeText(this@WallpaperActivity, R.string.wallpaper_no_picker, Toast.LENGTH_LONG).show()
                                    }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as LumaApp).core.themes.refresh()
    }
}

@Composable
private fun WallpaperStudio(
    store: WallpaperImageStore,
    modifier: Modifier,
    onSystemPicker: () -> Unit
) {
    val context = LocalContext.current
    val core = (context.applicationContext as LumaApp).core
    val theme by core.themes.theme.collectAsState()
    val themeSettings by core.themes.settings.collectAsState()
    val activeWallpaperId by core.themes.activeWallpaperId.collectAsState()
    val favorites by core.prefs.wallpaperFavorites.collectAsState(emptySet())
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<WallpaperEntry>?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryName by rememberSaveable { mutableStateOf(WallpaperCategory.ALL.name) }
    var collectionKey by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showTargets by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val category = WallpaperCategory.entries.firstOrNull { it.name == categoryName } ?: WallpaperCategory.ALL
    val collection = WallpaperCollection.fromStorageKey(collectionKey)
    val all = entries.orEmpty()
    val visible = remember(all, category, favorites, collection) {
        WallpaperCatalog.filter(all, category, favorites, collection)
    }
    val chosen = remember(visible, selectedId, activeWallpaperId) {
        visible.firstOrNull { it.id == selectedId }
            ?: visible.firstOrNull { it.id == activeWallpaperId }
            ?: visible.firstOrNull()
    }

    suspend fun refreshEntries() {
        entries = store.entries()
    }

    fun showStatus(message: String) {
        status = message
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun failure(e: Exception, fallback: Int) {
        showStatus(context.getString((e as? WallpaperException)?.messageRes ?: fallback))
    }

    LaunchedEffect(store) {
        refreshEntries()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val imported = store.import(uri)
                refreshEntries()
                selectedId = imported.id
                categoryName = WallpaperCategory.PHOTOS.name
                collectionKey = null
                showStatus(context.getString(R.string.wallpaper_import_success))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure(e, R.string.wallpaper_import_failed)
            } finally {
                busy = false
            }
        }
    }

    val previewResult by produceState<Pair<String, Bitmap?>?>(null, chosen?.id) {
        val entry = chosen ?: return@produceState
        val bitmap = try {
            store.preview(entry, 960)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
        value = entry.id to bitmap
    }
    val preview = previewResult?.takeIf { it.first == chosen?.id }?.second
    val previewLoading = chosen != null && previewResult?.first != chosen.id

    val seedsResult by produceState<Pair<String, WallpaperSeeds?>?>(null, chosen?.id, preview) {
        val entry = chosen ?: return@produceState
        entry.precomputedSeeds?.let {
            value = entry.id to it
            return@produceState
        }
        val extracted = try {
            store.extractSeeds(entry, preview)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        value = entry.id to extracted
    }
    val chosenSeeds = chosen?.precomputedSeeds
        ?: seedsResult?.takeIf { it.first == chosen?.id }?.second

    val colors = remember(chosenSeeds) {
        if (chosenSeeds == null) emptyList()
        else listOfNotNull(chosenSeeds.accent, chosenSeeds.primary, chosenSeeds.secondary)
            .map(ColorMath::opaque)
            .distinct()
    }

    val livePreviewTheme = remember(themeSettings, chosenSeeds, chosen?.id) {
        core.themes.previewWithWallpaper(
            draft = themeSettings.copy(backdrop = Backdrop.WALLPAPER),
            wallpaperSeeds = chosenSeeds,
            hasSelectedArtwork = chosen != null
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Category pills
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WallpaperCategory.entries.forEach { item ->
                val labelRes = when (item) {
                    WallpaperCategory.ALL -> R.string.wallpaper_all
                    WallpaperCategory.LUMA -> R.string.wallpaper_luma
                    WallpaperCategory.PHOTOS -> R.string.wallpaper_photos
                    WallpaperCategory.FAVORITES -> R.string.wallpaper_favorites
                }
                LumaGlassChoice(
                    theme = theme,
                    label = stringResource(labelRes),
                    selected = item == category,
                    onClick = {
                        categoryName = item.name
                        if (item == WallpaperCategory.PHOTOS) collectionKey = null
                    }
                )
            }
        }

        // Editorial collections strip (shown whenever curated wallpapers can appear)
        if (category != WallpaperCategory.PHOTOS) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LumaGlassSectionTitle(
                    theme = theme,
                    text = stringResource(R.string.wallpaper_collections_heading),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LumaGlassChoice(
                        theme = theme,
                        label = stringResource(R.string.wallpaper_collection_all),
                        selected = collection == null,
                        onClick = { collectionKey = null }
                    )
                    WallpaperCollection.entries.forEach { col ->
                        LumaGlassChoice(
                            theme = theme,
                            label = col.title,
                            selected = col == collection,
                            onClick = {
                                collectionKey = if (col == collection) null else col.storageKey
                            }
                        )
                    }
                }
                if (collection != null) {
                    Text(
                        collection.subtitle,
                        color = Color(theme.colors.onSurfaceMuted),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        // Gallery strip
        when {
            entries == null -> {
                LumaGlassSurface(theme, GlassRole.SECONDARY, Modifier.fillMaxWidth().height(168.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(22.dp),
                                color = Color(theme.colors.accent),
                                strokeWidth = 2.dp
                            )
                            Text(
                                stringResource(R.string.wallpaper_loading),
                                color = Color(theme.colors.onSurfaceMuted),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
            visible.isEmpty() -> {
                LumaGlassSurface(theme, GlassRole.SECONDARY, Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.wallpaper_empty),
                        color = Color(theme.colors.onSurfaceMuted),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { entry ->
                        WallpaperThumbnailCard(
                            theme = theme,
                            store = store,
                            entry = entry,
                            selected = entry.id == chosen?.id,
                            active = entry.id == activeWallpaperId,
                            favorite = entry.id in favorites,
                            onClick = { selectedId = entry.id }
                        )
                    }
                }
            }
        }

        // Selected Wallpaper Hero Preview + Metadata + Actions
        if (chosen != null) {
            WallpaperLiveHeroCard(
                outerTheme = theme,
                previewTheme = livePreviewTheme,
                entry = chosen,
                bitmap = preview,
                loading = previewLoading,
                isActive = chosen.id == activeWallpaperId
            )

            LumaGlassCard(
                theme = theme,
                title = chosen.title,
                subtitle = buildString {
                    chosen.collection?.let { append(it.title).append(" · ") }
                    if (chosen.mood.isNotBlank()) append(chosen.mood).append(" — ")
                    append(chosen.description)
                }
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LumaGlassChoice(
                        theme = theme,
                        label = stringResource(
                            if (busy) R.string.wallpaper_applying else R.string.wallpaper_set
                        ),
                        selected = true,
                        enabled = !busy && (preview != null || !chosen.isCustom),
                        onClick = { showTargets = true },
                        modifier = Modifier.weight(1.3f)
                    )
                    LumaGlassChoice(
                        theme = theme,
                        label = stringResource(
                            if (chosen.id in favorites) R.string.wallpaper_favorite_remove
                            else R.string.wallpaper_favorite_add
                        ),
                        selected = chosen.id in favorites,
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                try {
                                    core.prefs.setWallpaperFavorite(chosen.id, chosen.id !in favorites)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    failure(e, R.string.wallpaper_save_failed)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (chosen.isCustom) {
                    Spacer(Modifier.height(10.dp))
                    LumaGlassChoice(
                        theme = theme,
                        label = stringResource(R.string.wallpaper_delete),
                        selected = false,
                        enabled = !busy,
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Dynamic Wallpaper Theming & Optical Palette Card
        LumaGlassSwitchRow(
            label = stringResource(R.string.wallpaper_dynamic_title),
            subtitle = stringResource(R.string.wallpaper_dynamic_subtitle),
            checked = themeSettings.dynamicWallpaperColors,
            enabled = !busy,
            onCheckedChange = { enabled ->
                scope.launch {
                    try {
                        core.prefs.setDynamicWallpaperColors(enabled)
                        showStatus(
                            context.getString(
                                if (enabled) R.string.wallpaper_dynamic_enabled
                                else R.string.wallpaper_dynamic_manual_restored
                            )
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failure(e, R.string.wallpaper_save_failed)
                    }
                }
            },
            theme = theme
        )

        if (chosenSeeds != null && colors.isNotEmpty()) {
            LumaGlassCard(
                theme = theme,
                title = stringResource(R.string.wallpaper_palette_heading),
                subtitle = stringResource(
                    if (chosenSeeds.supportsDarkText == true) R.string.wallpaper_light_surface_badge
                    else R.string.wallpaper_dark_surface_badge
                )
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val roles = listOf(
                        R.string.wallpaper_swatch_accent to (chosenSeeds.accent ?: chosenSeeds.primary),
                        R.string.wallpaper_swatch_dominant to chosenSeeds.primary,
                        R.string.wallpaper_swatch_secondary to (chosenSeeds.secondary ?: chosenSeeds.primary)
                    )
                    roles.forEach { (roleLabel, swatchColor) ->
                        val opaqueSwatch = ColorMath.opaque(swatchColor)
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = !busy) {
                                    scope.launch {
                                        try {
                                            core.prefs.setWallpaperAccent(opaqueSwatch)
                                            showStatus(context.getString(R.string.wallpaper_accent_done))
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            failure(e, R.string.wallpaper_save_failed)
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(opaqueSwatch))
                                    .border(
                                        BorderStroke(1.dp, Color(theme.colors.outlineStrong)),
                                        RoundedCornerShape(12.dp)
                                    )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(roleLabel),
                                color = Color(theme.colors.onSurfaceMuted),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LumaGlassChoice(
                        theme = theme,
                        label = stringResource(R.string.wallpaper_accent),
                        selected = themeSettings.accentOverride == colors.first(),
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                try {
                                    core.prefs.setWallpaperAccent(colors.first())
                                    showStatus(context.getString(R.string.wallpaper_accent_done))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    failure(e, R.string.wallpaper_save_failed)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (themeSettings.dynamicWallpaperColors || themeSettings.accentOverride != null) {
                        LumaGlassChoice(
                            theme = theme,
                            label = stringResource(R.string.wallpaper_dynamic_return_manual),
                            selected = false,
                            enabled = !busy,
                            onClick = {
                                scope.launch {
                                    try {
                                        core.prefs.applyThemeSettings(
                                            themeSettings.copy(
                                                dynamicWallpaperColors = false,
                                                accentOverride = null,
                                                backgroundOverride = null
                                            )
                                        )
                                        showStatus(context.getString(R.string.wallpaper_dynamic_manual_restored))
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        failure(e, R.string.wallpaper_save_failed)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.wallpaper_palette_note),
                    color = Color(theme.colors.onSurfaceMuted),
                    fontSize = 12.sp
                )
            }
        }

        // Photo Import, System Backdrop & External Picker Card
        LumaGlassCard(
            theme = theme,
            title = stringResource(R.string.wallpaper_photos),
            subtitle = stringResource(R.string.wallpaper_import_note)
        ) {
            LumaGlassChoice(
                theme = theme,
                label = stringResource(R.string.wallpaper_add_photo),
                selected = false,
                enabled = !busy,
                onClick = {
                    runCatching { picker.launch(arrayOf("image/*")) }
                        .onFailure { showStatus(context.getString(R.string.wallpaper_import_failed)) }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            LumaGlassChoice(
                theme = theme,
                label = stringResource(
                    if (theme.backdrop == Backdrop.WALLPAPER) R.string.wallpaper_show_home_done
                    else R.string.wallpaper_show_home
                ),
                caption = stringResource(R.string.wallpaper_home_note),
                selected = theme.backdrop == Backdrop.WALLPAPER,
                enabled = !busy && theme.backdrop != Backdrop.WALLPAPER,
                onClick = {
                    scope.launch {
                        try {
                            core.prefs.showSystemWallpaperOnHome()
                            showStatus(context.getString(R.string.wallpaper_show_home_done))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failure(e, R.string.wallpaper_save_failed)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            LumaGlassChoice(
                theme = theme,
                label = stringResource(R.string.wallpaper_system_picker),
                caption = stringResource(R.string.wallpaper_system_picker_note),
                selected = false,
                enabled = !busy,
                onClick = onSystemPicker,
                modifier = Modifier.fillMaxWidth()
            )
        }

        status?.let { message ->
            Text(
                message,
                color = Color(theme.colors.accent),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showTargets && chosen != null) {
        LumaGlassBottomSheet(
            theme = theme,
            title = stringResource(R.string.wallpaper_set_title),
            onDismissRequest = { showTargets = false }
        ) {
            Text(
                stringResource(R.string.wallpaper_set_explanation),
                color = Color(theme.colors.onSurfaceMuted),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            val targetOptions = listOf(
                Triple(
                    WallpaperTarget.HOME,
                    R.string.wallpaper_target_home,
                    R.string.wallpaper_target_home_caption
                ),
                Triple(
                    WallpaperTarget.BOTH,
                    R.string.wallpaper_target_both,
                    R.string.wallpaper_target_both_caption
                ),
                Triple(
                    WallpaperTarget.LOCK,
                    R.string.wallpaper_target_lock,
                    R.string.wallpaper_target_lock_caption
                ),
                Triple(
                    WallpaperTarget.LUMA_HOME,
                    R.string.wallpaper_target_luma_home,
                    R.string.wallpaper_target_luma_home_caption
                )
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                targetOptions.forEach { (target, titleRes, captionRes) ->
                    LumaGlassChoice(
                        theme = theme,
                        label = stringResource(titleRes),
                        caption = stringResource(captionRes),
                        selected = false,
                        enabled = !busy,
                        onClick = {
                            showTargets = false
                            scope.launch {
                                busy = true
                                try {
                                    val sysId = store.apply(chosen, target)
                                    val seeds = store.extractSeeds(chosen, preview)
                                    when (target) {
                                        WallpaperTarget.HOME, WallpaperTarget.BOTH -> {
                                            core.prefs.recordAppliedWallpaper(
                                                wallpaperId = chosen.id,
                                                systemWallpaperId = sysId,
                                                backdrop = Backdrop.WALLPAPER
                                            )
                                            seeds?.let { core.prefs.setCachedWallpaperSeedsRaw(it.encode(chosen.id)) }
                                            core.themes.refresh()
                                            showStatus(context.getString(R.string.wallpaper_applied))
                                        }
                                        WallpaperTarget.LUMA_HOME -> {
                                            core.prefs.recordAppliedWallpaper(
                                                wallpaperId = chosen.id,
                                                systemWallpaperId = null,
                                                backdrop = Backdrop.LUMA
                                            )
                                            seeds?.let { core.prefs.setCachedWallpaperSeedsRaw(it.encode(chosen.id)) }
                                            showStatus(context.getString(R.string.wallpaper_applied_luma_home))
                                        }
                                        WallpaperTarget.LOCK -> {
                                            showStatus(context.getString(R.string.wallpaper_applied))
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    failure(e, R.string.wallpaper_apply_failed)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(4.dp))
                LumaGlassChoice(
                    theme = theme,
                    label = stringResource(R.string.wallpaper_cancel),
                    selected = false,
                    onClick = { showTargets = false },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (confirmDelete && chosen != null) {
        LumaGlassDialog(
            theme = theme,
            title = stringResource(R.string.wallpaper_delete_title),
            message = stringResource(R.string.wallpaper_delete_message),
            confirmLabel = stringResource(R.string.wallpaper_confirm_delete),
            dismissLabel = stringResource(R.string.wallpaper_cancel),
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    busy = true
                    try {
                        if (!store.delete(chosen)) throw WallpaperException(R.string.wallpaper_delete_failed)
                        core.prefs.setWallpaperFavorite(chosen.id, false)
                        core.prefs.clearActiveWallpaperIfMatches(chosen.id)
                        selectedId = null
                        refreshEntries()
                        showStatus(context.getString(R.string.wallpaper_delete_success))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failure(e, R.string.wallpaper_delete_failed)
                    } finally {
                        busy = false
                    }
                }
            }
        )
    }
}

@Composable
private fun WallpaperLiveHeroCard(
    outerTheme: ResolvedTheme,
    previewTheme: ResolvedTheme,
    entry: WallpaperEntry,
    bitmap: Bitmap?,
    loading: Boolean,
    isActive: Boolean
) {
    val scrim = remember(previewTheme) { GlassBackdrop.scrim(previewTheme) }
    val glassPalette = remember(previewTheme) {
        GlassMaterials.palette(
            previewTheme,
            GlassCapabilities(translucency = true, blur = previewTheme.usesBlur, depth = true)
        )
    }
    val searchColors = glassPalette[GlassRole.SEARCH].rest
    val dockColors = glassPalette[GlassRole.DOCK].rest
    val alignment = remember(entry.focalX, entry.focalY) {
        BiasAlignment(
            horizontalBias = ((entry.focalX - 0.5f) * 2f).coerceIn(-1f, 1f),
            verticalBias = ((entry.focalY - 0.5f) * 2f).coerceIn(-1f, 1f)
        )
    }

    LumaGlassPreviewFrame(
        theme = outerTheme,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp, max = 360.dp)
            .height(330.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(previewTheme.colors.background))
        ) {
            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = entry.title,
                        alignment = alignment,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            Modifier.size(28.dp),
                            color = Color(outerTheme.colors.accent),
                            strokeWidth = 2.5.dp
                        )
                    }
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.wallpaper_image_unavailable),
                            color = Color(outerTheme.colors.onSurfaceMuted),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }

            // Live atmosphere & split-horizon scrim matching the real Home screen
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(scrim.top),
                            0.46f to Color(scrim.center),
                            1f to Color(scrim.bottom)
                        )
                    )
            )

            // Live Home Clock + Glass Search + Floating Glass Dock overlay
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            "09:41",
                            color = Color(previewTheme.colors.homeForeground),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Light
                        )
                        Text(
                            entry.mood.ifBlank { entry.collection?.title.orEmpty() },
                            color = Color(previewTheme.colors.homeMuted),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (isActive) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(Color(previewTheme.colors.accentContainer))
                                .border(1.dp, Color(previewTheme.colors.accent), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                stringResource(R.string.wallpaper_active_badge),
                                color = Color(previewTheme.colors.onSurface),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Simulated Liquid Glass Search Pill
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(searchColors.fillTop), Color(searchColors.fillBottom))
                                )
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(Color(searchColors.edgeTop), Color(searchColors.edgeBottom))
                                    )
                                ),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(previewTheme.colors.accent))
                        )
                        Text(
                            stringResource(R.string.wallpaper_preview_search_hint),
                            color = Color(searchColors.muted),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    // Simulated Floating Liquid Glass Dock
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(dockColors.fillTop), Color(dockColors.fillBottom))
                                )
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(Color(dockColors.edgeTop), Color(dockColors.edgeBottom))
                                    )
                                ),
                                RoundedCornerShape(26.dp)
                            )
                            .padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconShades = listOf(
                            previewTheme.colors.accent,
                            previewTheme.colors.secondary,
                            ColorMath.lerp(previewTheme.colors.surface3, previewTheme.colors.accent, 0.45f),
                            ColorMath.lerp(previewTheme.colors.surface2, previewTheme.colors.onSurface, 0.28f)
                        )
                        iconShades.forEach { shade ->
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(ColorMath.withAlpha(shade, 0.88f)))
                                    .border(
                                        1.dp,
                                        Color(dockColors.edgeTop),
                                        RoundedCornerShape(10.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperThumbnailCard(
    theme: ResolvedTheme,
    store: WallpaperImageStore,
    entry: WallpaperEntry,
    selected: Boolean,
    active: Boolean,
    favorite: Boolean,
    onClick: () -> Unit
) {
    val image by produceState<Bitmap?>(null, entry.id) {
        value = try {
            store.preview(entry, 320)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }
    val alignment = remember(entry.focalX, entry.focalY) {
        BiasAlignment(
            horizontalBias = ((entry.focalX - 0.5f) * 2f).coerceIn(-1f, 1f),
            verticalBias = ((entry.focalY - 0.5f) * 2f).coerceIn(-1f, 1f)
        )
    }
    val placeholderColor = entry.precomputedSeeds?.primary ?: theme.colors.surface2

    LumaGlassSurface(
        theme = theme,
        role = GlassRole.SETTINGS_CONTROL,
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .size(width = 122.dp, height = 178.dp)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = entry.title
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(placeholderColor))
            ) {
                image?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        alignment = alignment,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (active) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color(ColorMath.withAlpha(theme.colors.background, 0.78f)))
                            .border(1.dp, Color(theme.colors.accent), CircleShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            stringResource(R.string.wallpaper_active_badge),
                            color = Color(theme.colors.onSurface),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (favorite) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(theme.colors.accent))
                    )
                }
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    entry.title,
                    color = Color(theme.colors.onSurface),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val caption = entry.mood.ifBlank { entry.collection?.title.orEmpty() }
                if (caption.isNotBlank()) {
                    Text(
                        caption,
                        color = Color(theme.colors.onSurfaceMuted),
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
