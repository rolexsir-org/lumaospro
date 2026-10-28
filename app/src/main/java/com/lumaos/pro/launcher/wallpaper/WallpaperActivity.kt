package com.lumaos.pro.launcher.wallpaper

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.core.view.WindowCompat
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.settings.LumaOSTheme
import com.lumaos.pro.launcher.ui.glass.LumaGlassTopBar
import com.lumaos.pro.launcher.ui.theme.Backdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A separate wallpaper surface; the existing appearance Studio remains intact. */
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
                Scaffold(topBar = {
                    Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                        LumaGlassTopBar(theme, stringResource(R.string.wallpaper_studio_title),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            navigationIcon = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.wallpaper_back)) } })
                    }
                }) { padding ->
                    WallpaperStudio(store, Modifier.padding(padding), onSystemPicker = {
                        runCatching { startActivity(core.appRepository.wallpaperPickerIntent()) }
                            .onFailure { Toast.makeText(this, R.string.wallpaper_no_picker, Toast.LENGTH_LONG).show() }
                    })
                }
            }
        }
    }
}

@Composable
private fun WallpaperStudio(store: WallpaperImageStore, modifier: Modifier, onSystemPicker: () -> Unit) {
    val context = LocalContext.current
    val core = (context.applicationContext as LumaApp).core
    val theme by core.themes.theme.collectAsState()
    val favorites by core.prefs.wallpaperFavorites.collectAsState(emptySet())
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<WallpaperEntry>?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryName by rememberSaveable { mutableStateOf(WallpaperCategory.ALL.name) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showTargets by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val category = WallpaperCategory.entries.firstOrNull { it.name == categoryName } ?: WallpaperCategory.ALL
    val all = entries.orEmpty()
    val visible = WallpaperCatalog.filter(all, category, favorites)
    val chosen = visible.firstOrNull { it.id == selectedId } ?: visible.firstOrNull()

    suspend fun refresh() { entries = store.entries() }
    fun showStatus(message: String) {
        status = message
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    fun failure(e: Exception, fallback: Int) {
        showStatus(context.getString((e as? WallpaperException)?.messageRes ?: fallback))
    }
    LaunchedEffect(store) { refresh() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val imported = store.import(uri)
                refresh(); selectedId = imported.id; categoryName = WallpaperCategory.PHOTOS.name
                showStatus(context.getString(R.string.wallpaper_import_success))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e, R.string.wallpaper_import_failed) }
            finally { busy = false }
        }
    }
    val previewResult by produceState<Pair<String, android.graphics.Bitmap?>?>(null, chosen?.id) {
        val entry = chosen ?: return@produceState
        val bitmap = try { store.preview(entry) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        catch (_: OutOfMemoryError) { null }
        value = entry.id to bitmap
    }
    // A producer can briefly retain the last value when its key changes; never
    // display or apply that previous image's preview or extracted color.
    val preview = previewResult?.takeIf { it.first == chosen?.id }?.second
    val previewLoading = chosen != null && previewResult?.first != chosen.id
    val paletteResult by produceState<Pair<android.graphics.Bitmap, List<Int>>?>(null, preview) {
        val image = preview ?: return@produceState
        value = image to store.colors(image)
    }
    val colors = paletteResult?.takeIf { it.first === preview }?.second.orEmpty()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.wallpaper_studio_subtitle), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WallpaperCategory.entries.forEach { item ->
                val label = when (item) {
                    WallpaperCategory.ALL -> R.string.wallpaper_all
                    WallpaperCategory.LUMA -> R.string.wallpaper_luma
                    WallpaperCategory.PHOTOS -> R.string.wallpaper_photos
                    WallpaperCategory.FAVORITES -> R.string.wallpaper_favorites
                }
                FilterChip(selected = item == category, onClick = { categoryName = item.name }, label = { Text(stringResource(label)) })
            }
        }
        when {
            entries == null -> Text(stringResource(R.string.wallpaper_loading))
            visible.isEmpty() -> Text(stringResource(R.string.wallpaper_empty), style = MaterialTheme.typography.bodyMedium)
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible, key = { it.id }) { entry ->
                    WallpaperThumbnail(store, entry, entry.id == chosen?.id) { selectedId = entry.id }
                }
            }
        }
        if (chosen != null) {
            Text(chosen.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(chosen.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 340.dp).height(300.dp)
                .clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                val image = preview
                if (image != null) Image(image.asImageBitmap(), contentDescription = chosen.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else if (previewLoading) CircularProgressIndicator(Modifier.size(28.dp))
                else Text(stringResource(R.string.wallpaper_image_unavailable), Modifier.padding(24.dp))
            }
            OutlinedButton(onClick = {
                scope.launch {
                    try { core.prefs.setWallpaperFavorite(chosen.id, chosen.id !in favorites) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { failure(e, R.string.wallpaper_save_failed) }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (chosen.id in favorites) R.string.wallpaper_favorite_remove else R.string.wallpaper_favorite_add))
            }
            Button(onClick = { showTargets = true }, enabled = !busy && preview != null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (busy) R.string.wallpaper_applying else R.string.wallpaper_set))
            }
            if (colors.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    colors.forEach { color -> Box(Modifier.size(24.dp).clip(RoundedCornerShape(50)).background(Color(color))) }
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        try { core.prefs.setWallpaperAccent(colors.first()); showStatus(context.getString(R.string.wallpaper_accent_done)) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { failure(e, R.string.wallpaper_save_failed) }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wallpaper_accent)) }
                Text(stringResource(R.string.wallpaper_palette_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (chosen.isCustom) TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                Text(stringResource(R.string.wallpaper_delete))
            }
        }
        OutlinedButton(onClick = {
            runCatching { picker.launch(arrayOf("image/*")) }
                .onFailure { showStatus(context.getString(R.string.wallpaper_import_failed)) }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.wallpaper_add_photo))
        }
        Text(stringResource(R.string.wallpaper_import_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = {
            scope.launch {
                try { core.prefs.showSystemWallpaperOnHome(); showStatus(context.getString(R.string.wallpaper_show_home_done)) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { failure(e, R.string.wallpaper_save_failed) }
            }
        }, enabled = !busy && theme.backdrop != Backdrop.WALLPAPER, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (theme.backdrop == Backdrop.WALLPAPER) R.string.wallpaper_show_home_done else R.string.wallpaper_show_home))
        }
        Text(stringResource(R.string.wallpaper_home_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onSystemPicker, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.wallpaper_system_picker))
        }
        Text(stringResource(R.string.wallpaper_system_picker_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
        Spacer(Modifier.height(12.dp))
    }

    if (showTargets && chosen != null) AlertDialog(
        onDismissRequest = { showTargets = false },
        title = { Text(stringResource(R.string.wallpaper_set_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.wallpaper_set_explanation), style = MaterialTheme.typography.bodySmall)
                listOf(WallpaperTarget.HOME to R.string.wallpaper_target_home, WallpaperTarget.LOCK to R.string.wallpaper_target_lock,
                    WallpaperTarget.BOTH to R.string.wallpaper_target_both).forEach { (target, label) ->
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        showTargets = false
                        scope.launch {
                            busy = true
                            try { store.apply(chosen, target); showStatus(context.getString(R.string.wallpaper_applied)) }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { failure(e, R.string.wallpaper_apply_failed) }
                            finally { busy = false }
                        }
                    }) { Text(stringResource(label)) }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = { showTargets = false }) { Text(stringResource(R.string.wallpaper_cancel)) } }
    )
    if (confirmDelete && chosen != null) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.wallpaper_delete_title)) },
        text = { Text(stringResource(R.string.wallpaper_delete_message)) },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            scope.launch {
                busy = true
                try {
                    if (!store.delete(chosen)) throw WallpaperException(R.string.wallpaper_delete_failed)
                    selectedId = null; refresh()
                    core.prefs.setWallpaperFavorite(chosen.id, false)
                    showStatus(context.getString(R.string.wallpaper_delete_success))
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { failure(e, R.string.wallpaper_delete_failed) }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.wallpaper_confirm_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.wallpaper_cancel)) } }
    )
}

@Composable
private fun WallpaperThumbnail(store: WallpaperImageStore, entry: WallpaperEntry, selected: Boolean, onClick: () -> Unit) {
    val image by produceState<android.graphics.Bitmap?>(null, entry.id) {
        value = try { store.preview(entry, 240) } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
    }
    Column(Modifier.size(width = 112.dp, height = 160.dp)
        .clip(RoundedCornerShape(16.dp))
        .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .semantics { role = Role.RadioButton; this.selected = selected; contentDescription = entry.title }
        .background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
            image?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(entry.title, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), maxLines = 1,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}
