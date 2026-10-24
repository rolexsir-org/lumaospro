package com.rolexm.launcher.launcher

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rolexm.launcher.core.RolexApp
import com.rolexm.launcher.context.ContextProfile
import com.rolexm.launcher.core.apps.AppCategories
import com.rolexm.launcher.core.apps.AppEntry
import com.rolexm.launcher.core.apps.AppShortcut
import com.rolexm.launcher.core.apps.DefaultDockResolver
import com.rolexm.launcher.core.apps.DrawerCategory
import com.rolexm.launcher.core.apps.DrawerSort
import com.rolexm.launcher.core.model.ComponentKey
import com.rolexm.launcher.core.prefs.RolexPrefs
import com.rolexm.launcher.core.usage.Frecency
import com.rolexm.launcher.launcher.model.DrawerFilter
import com.rolexm.launcher.launcher.model.DrawerModel
import com.rolexm.launcher.launcher.model.HomeItem
import com.rolexm.launcher.launcher.model.HomeLayout
import com.rolexm.launcher.launcher.model.HomeLayoutCodec
import com.rolexm.launcher.launcher.model.HomeLayoutOps
import com.rolexm.launcher.launcher.model.ShortcutRef
import com.rolexm.launcher.launcher.model.SmartKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * State for the home screen and app drawer.
 *
 * Everything the UI shows is derived from StateFlows that survive configuration
 * changes, so rotating the phone or being recreated after a process death
 * rebuilds exactly the same screen with no saved-instance-state plumbing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val core = (application as RolexApp).core
    private val repository = core.appRepository
    val prefs: RolexPrefs = core.prefs
    private val history = core.launchHistory

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps
    private var refreshJob: Job? = null

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _filter = MutableStateFlow<DrawerFilter>(DrawerFilter.All)
    val filter: StateFlow<DrawerFilter> = _filter

    val activeContextProfile: StateFlow<ContextProfile> = prefs.activeContextProfile
        .stateIn(viewModelScope, SharingStarted.Eagerly, ContextProfile.PERSONAL)

    private val hiddenKeys: StateFlow<Set<ComponentKey>> = activeContextProfile.flatMapLatest(prefs::hiddenApps)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val pinnedKeys: StateFlow<List<ComponentKey>> = activeContextProfile.flatMapLatest(prefs::pinnedApps)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val drawerSort: StateFlow<DrawerSort> = prefs.drawerSort
        .stateIn(viewModelScope, SharingStarted.Eagerly, DrawerSort.ALPHABETICAL)
    val suggestionsEnabled: StateFlow<Boolean> = prefs.suggestionsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    private val hiddenInSearch: StateFlow<Boolean> = prefs.hiddenInSearch
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val categoriesEnabled: StateFlow<Boolean> = prefs.drawerCategoriesEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    private val drawerColumns: StateFlow<Int> = prefs.drawerColumns
        .stateIn(viewModelScope, SharingStarted.Eagerly, RolexPrefs.DEFAULT_DRAWER_COLUMNS)

    private val _shortcuts = MutableStateFlow<List<AppShortcut>>(emptyList())

    /** Categories that occur among visible apps (chips are only offered when non-empty). */
    val categories: StateFlow<List<DrawerCategory>> = combine(_apps, hiddenKeys) { list, hidden ->
        AppCategories.present(list.filterNot { it.key in hidden })
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val searchTargets = MutableStateFlow(Pair(false, false))

    /**
     * Drawer contents. WhileSubscribed(5s) rather than Eagerly so that when the
     * drawer is closed and the home screen is idle, filtering work stops.
     */
    val drawer: StateFlow<DrawerModel.Output> = combine(
        combine(_apps, _query, _filter, drawerSort, hiddenKeys) { a, q, f, s, h -> DrawerInputs(a, q, f, s, h) },
        combine(hiddenInSearch, history.records, suggestionsEnabled, drawerColumns, _shortcuts) { his, rec, sug, cols, sc ->
            Extra(his, rec.values, sug, cols, sc)
        },
        searchTargets,
        pinnedKeys
    ) { d, e, targets, pinned ->
        val now = System.currentTimeMillis()
        DrawerModel.build(
            DrawerModel.Input(
                apps = d.apps, query = d.query, filter = d.filter, sort = d.sort, hidden = d.hidden,
                hiddenInSearch = e.hiddenInSearch, history = e.records, suggestionsEnabled = e.suggestions,
                pinned = pinned, nowMillis = now, hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                suggestionCount = e.columns,
                shortcuts = e.shortcuts.map { ShortcutRef(it.packageName, it.id, it.label) },
                webSearchAvailable = targets.first, storeAvailable = targets.second
            )
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrawerModel.Output(emptyList(), emptyList(), 0))

    private data class DrawerInputs(
        val apps: List<AppEntry>, val query: String, val filter: DrawerFilter,
        val sort: DrawerSort, val hidden: Set<ComponentKey>
    )

    private data class Extra(
        val hiddenInSearch: Boolean, val records: Collection<com.rolexm.launcher.core.usage.LaunchRecord>,
        val suggestions: Boolean, val columns: Int, val shortcuts: List<AppShortcut>
    )

    /** Stored layout for the active profile. */
    private val _layout = MutableStateFlow(HomeLayout.EMPTY)

    /** What is drawn: hidden apps removed, plus the display->stored index maps. */
    private val _display = MutableStateFlow(DisplayLayout(HomeLayout.EMPTY, emptyList(), emptyList()))
    val layout: StateFlow<HomeLayout> = MutableStateFlow(HomeLayout.EMPTY).also { out ->
        viewModelScope.launch { _display.collect { out.value = it.layout } }
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    /** Bumps when launch history changes, so smart-folder icons rebind. */
    val historyVersion: StateFlow<Int> = MutableStateFlow(0).also { out ->
        viewModelScope.launch { history.records.collect { out.value = out.value + 1 } }
    }

    init {
        history.load()
        viewModelScope.launch {
            activeContextProfile.flatMapLatest(prefs::homeLayoutRaw).collect { raw ->
                _layout.value = HomeLayoutCodec.decode(raw)
            }
        }
        viewModelScope.launch {
            combine(_layout, hiddenKeys) { current, hidden -> project(current, hidden) }
                .collect { _display.value = it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val web = runCatching { Intent(Intent.ACTION_WEB_SEARCH).resolveActivity(pm) != null }.getOrDefault(false)
            val store = runCatching {
                Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=rolexm")).resolveActivity(pm) != null
            }.getOrDefault(false)
            searchTargets.value = web to store
        }
        refreshApps()
    }

    /**
     * Reloads the installed-app list.
     *
     * Always off the main thread: LauncherApps.getActivityList is a binder call
     * per profile and can take hundreds of milliseconds on a loaded device.
     */
    fun refreshApps() {
        // Resume and several package callbacks can arrive together. Coalesce
        // them rather than racing binder enumeration and default-dock writes.
        if (refreshJob?.isActive == true) return
        _isLoading.value = true
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = repository.refresh()
                withContext(Dispatchers.Main) { _apps.value = list }
                seedDefaultDockIfNeeded(list)
                pruneUninstalled(list)
                history.retainOnly(list.mapTo(HashSet()) { it.key })
            } finally {
                withContext(Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }

    /** Loads app shortcuts for search (only possible while ROLEXM is the default home app). */
    fun loadShortcuts() {
        viewModelScope.launch(Dispatchers.IO) {
            _shortcuts.value = core.shortcuts.allShortcuts()
        }
    }

    fun shortcutFor(ref: ShortcutRef): AppShortcut? =
        _shortcuts.value.firstOrNull { it.packageName == ref.packageName && it.id == ref.id }

    /** First run only: fill the dock with the device's real dialler/browser/etc. */
    private suspend fun seedDefaultDockIfNeeded(apps: List<AppEntry>) {
        if (apps.isEmpty()) return
        if (prefs.layoutSeeded.first()) return
        val keys = DefaultDockResolver.resolve(getApplication(), apps)
        if (keys.isEmpty()) return
        val seeded = keys.fold(HomeLayout.EMPTY) { acc, key -> HomeLayoutOps.addToDock(acc, key) }
        persistLayout(seeded)
        // Keep the original layout as the seed for profiles not customized yet.
        prefs.setHomeLayoutRaw(HomeLayoutCodec.encode(seeded))
        prefs.setLayoutSeeded(true)
    }

    /**
     * Removes home items whose app is gone, and collapses folders that have
     * fallen to a single member. Runs after every package change so the home
     * screen never shows a dead icon.
     */
    private suspend fun pruneUninstalled(apps: List<AppEntry>) {
        val installed = apps.mapTo(HashSet()) { it.key }
        for (profile in ContextProfile.entries) {
            val current = HomeLayoutCodec.decode(prefs.homeLayoutRaw(profile).first())
            val pruned = HomeLayoutOps.prune(current, installed)
            if (pruned != current) {
                prefs.setHomeLayoutRaw(profile, HomeLayoutCodec.encode(pruned))
                if (profile == activeContextProfile.value) {
                    withContext(Dispatchers.Main) { _layout.value = pruned }
                }
            }
        }
    }

    private suspend fun persistLayout(
        layout: HomeLayout,
        profile: ContextProfile = activeContextProfile.value
    ) {
        if (profile == activeContextProfile.value) {
            withContext(Dispatchers.Main) { _layout.value = layout }
        }
        prefs.setHomeLayoutRaw(profile, HomeLayoutCodec.encode(layout))
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setFilter(value: DrawerFilter) {
        _filter.value = value
    }

    fun setSort(value: DrawerSort) {
        viewModelScope.launch { prefs.setDrawerSort(value) }
    }

    fun labelFor(key: ComponentKey): String =
        _apps.value.firstOrNull { it.key == key }?.label ?: key.packageName

    fun entryFor(key: ComponentKey): AppEntry? = _apps.value.firstOrNull { it.key == key }

    fun installedKeys(): Set<ComponentKey> = _apps.value.mapTo(HashSet()) { it.key }

    /** Records a launch made through ROLEXM, if the user allows learning. */
    fun recordLaunch(key: ComponentKey) {
        if (suggestionsEnabled.value) history.recordLaunch(key)
    }

    /** Live members of a smart folder, from real data only. */
    fun smartMembers(kind: SmartKind, limit: Int = 12): List<ComponentKey> {
        val hidden = hiddenKeys.value
        val apps = _apps.value
        val installed = apps.mapTo(HashSet()) { it.key }
        val eligible: (ComponentKey) -> Boolean = { it in installed && it !in hidden }
        val records = if (suggestionsEnabled.value) history.records.value.values else emptyList()
        return when (kind) {
            SmartKind.FREQUENT -> Frecency.frequent(records, System.currentTimeMillis(), eligible, limit)
            SmartKind.RECENT -> Frecency.recent(records, eligible, limit)
            SmartKind.NEW_APPS -> apps.asSequence().filter { eligible(it.key) && it.installedAt > 0 }
                .sortedByDescending { it.installedAt }.take(limit).map { it.key }.toList()
        }
    }

    // --- Layout mutations. All persist immediately and use display positions,
    //     mapped onto the stored layout so hidden apps are never disturbed. ---

    fun addToHome(key: ComponentKey) = mutate { HomeLayoutOps.addToHome(it, key) }

    fun addToDock(key: ComponentKey) = mutate { HomeLayoutOps.addToDock(it, key) }

    fun removeFromHome(displayPosition: Int) = mutateStored { layout, d ->
        // Widgets are removed through removeWidget(), which the activity calls
        // after releasing the id from its AppWidgetHost.
        val stored = d.pageMap.getOrNull(displayPosition) ?: return@mutateStored layout
        HomeLayoutOps.removeFromHome(layout, 0, stored)
    }

    fun removeFromDock(displayPosition: Int) = mutateStored { layout, d ->
        val stored = d.dockMap.getOrNull(displayPosition) ?: return@mutateStored layout
        HomeLayoutOps.removeFromDock(layout, stored)
    }

    fun createFolder(firstPosition: Int, secondPosition: Int) = mutateStored { layout, d ->
        val a = d.pageMap.getOrNull(firstPosition) ?: return@mutateStored layout
        val b = d.pageMap.getOrNull(secondPosition) ?: return@mutateStored layout
        HomeLayoutOps.createFolder(layout, 0, a, b, "folder_${System.currentTimeMillis()}", folderName(layout, a, b))
    }

    /** Drag-and-drop: [source] dropped onto [target] (an app -> new folder; a folder -> joins it). */
    fun dropOnto(sourcePosition: Int, targetPosition: Int) = mutateStored { layout, d ->
        val s = d.pageMap.getOrNull(sourcePosition) ?: return@mutateStored layout
        val t = d.pageMap.getOrNull(targetPosition) ?: return@mutateStored layout
        when (layout.page(0).getOrNull(t)) {
            is HomeItem.Folder -> HomeLayoutOps.dropIntoFolder(layout, 0, s, t)
            is HomeItem.App -> HomeLayoutOps.createFolder(layout, 0, t, s, "folder_${System.currentTimeMillis()}", folderName(layout, t, s))
            else -> layout
        }
    }

    private fun folderName(layout: HomeLayout, a: Int, b: Int): String? {
        val ka = (layout.page(0).getOrNull(a) as? HomeItem.App)?.key
        val kb = (layout.page(0).getOrNull(b) as? HomeItem.App)?.key
        return AppCategories.sharedTitle(ka?.let(::entryFor), kb?.let(::entryFor))
    }

    /** Applies the order arranged by drag-and-drop (display items, new order). */
    fun reorderHome(arranged: List<HomeItem>) = mutateStored { layout, d ->
        val stored = layout.page(0)
        val displayToStored = d.layout.page(0).withIndex().associate { (i, item) ->
            HomeGridIdentity.of(item) to d.pageMap.getOrNull(i)
        }
        val used = HashSet<Int>()
        val ordered = arranged.mapNotNull { item ->
            displayToStored[HomeGridIdentity.of(item)]?.let { idx -> used += idx; stored.getOrNull(idx) }
        }
        val rest = stored.filterIndexed { i, _ -> i !in used }
        HomeLayoutOps.setPage(layout, 0, ordered + rest)
    }

    fun renameFolder(position: Int, name: String) = mutateStored { layout, d ->
        val stored = d.pageMap.getOrNull(position) ?: return@mutateStored layout
        HomeLayoutOps.renameFolder(layout, 0, stored, name)
    }

    fun renameFolderById(folderId: String, name: String) = mutate { layout ->
        val index = layout.page(0).indexOfFirst {
            (it is HomeItem.Folder && it.id == folderId) || (it is HomeItem.SmartFolder && it.id == folderId)
        }
        if (index < 0) layout else HomeLayoutOps.renameFolder(layout, 0, index, name)
    }

    /** Removes an app from a folder; the folder collapses if one child is left. */
    fun removeFromFolder(folderId: String, key: ComponentKey) = mutate { layout ->
        val page = layout.page(0)
        val index = page.indexOfFirst { it is HomeItem.Folder && it.id == folderId }
        val folder = page.getOrNull(index) as? HomeItem.Folder ?: return@mutate layout
        val remaining = folder.children - key
        val replacement: List<HomeItem> = when (remaining.size) {
            0 -> emptyList()
            1 -> listOf(HomeItem.App(remaining.first()))
            else -> listOf(folder.copy(children = remaining))
        }
        val updated = page.toMutableList().apply {
            removeAt(index)
            addAll(index, replacement)
        }
        // The removed app goes back to the page unless it is still elsewhere.
        val withApp = if (HomeLayoutOps.containsKey(layout.copy(pages = listOf(updated)), key)) updated
        else updated + HomeItem.App(key)
        HomeLayoutOps.setPage(layout, 0, withApp)
    }

    fun moveHomeToDock(position: Int) = mutateStored { layout, d ->
        val stored = d.pageMap.getOrNull(position) ?: return@mutateStored layout
        HomeLayoutOps.moveHomeToDock(layout, 0, stored)
    }

    fun moveDockToHome(position: Int) = mutateStored { layout, d ->
        val stored = d.dockMap.getOrNull(position) ?: return@mutateStored layout
        HomeLayoutOps.moveDockToHome(layout, stored)
    }

    fun addWidget(widget: HomeItem.Widget, position: Int = 0) = mutate { HomeLayoutOps.addWidget(it, 0, widget, position) }

    fun resizeWidget(appWidgetId: Int, rows: Int) = mutate { HomeLayoutOps.resizeWidget(it, appWidgetId, rows) }

    fun removeWidget(appWidgetId: Int) = mutate { HomeLayoutOps.removeWidget(it, appWidgetId) }

    fun addSmartFolder(kind: SmartKind, name: String) = mutate {
        HomeLayoutOps.addSmartFolder(it, 0, HomeItem.SmartFolder("smart_${kind.storageKey}", kind, name))
    }

    fun isPinned(key: ComponentKey): Boolean = key in pinnedKeys.value

    fun setAppPinned(key: ComponentKey, pinned: Boolean) {
        viewModelScope.launch { prefs.setAppPinned(activeContextProfile.value, key, pinned) }
    }

    /** Hides an app in the active context profile (reversible in Settings). */
    fun hideApp(key: ComponentKey) {
        viewModelScope.launch { prefs.setAppHidden(activeContextProfile.value, key, true) }
    }

    /** Widget ids referenced by any profile's layout (for orphan clean-up). */
    suspend fun allWidgetIds(): Set<Int> = ContextProfile.entries.flatMapTo(HashSet()) { profile ->
        HomeLayoutCodec.decode(prefs.homeLayoutRaw(profile).first()).widgetIds()
    }

    private fun mutate(transform: (HomeLayout) -> HomeLayout) {
        viewModelScope.launch {
            val next = transform(_layout.value)
            if (next != _layout.value) persistLayout(next)
        }
    }

    private fun mutateStored(transform: (HomeLayout, DisplayLayout) -> HomeLayout) {
        viewModelScope.launch {
            val next = transform(_layout.value, _display.value)
            if (next != _layout.value) persistLayout(next)
        }
    }

    /** Display layout plus, for page 0 and the dock, the stored index of each displayed item. */
    data class DisplayLayout(val layout: HomeLayout, val pageMap: List<Int>, val dockMap: List<Int>)

    companion object {
        fun project(current: HomeLayout, hidden: Set<ComponentKey>): DisplayLayout {
            fun visible(items: List<HomeItem>): Pair<List<HomeItem>, List<Int>> {
                val out = ArrayList<HomeItem>()
                val map = ArrayList<Int>()
                items.forEachIndexed { index, item ->
                    val shown: HomeItem? = when (item) {
                        is HomeItem.App -> item.takeUnless { it.key in hidden }
                        is HomeItem.Folder -> {
                            val children = item.children.filterNot { it in hidden }
                            when (children.size) {
                                0 -> null
                                1 -> HomeItem.App(children.single())
                                else -> item.copy(children = children)
                            }
                        }
                        is HomeItem.Widget, is HomeItem.SmartFolder -> item
                    }
                    if (shown != null) {
                        out += shown
                        map += index
                    }
                }
                return out to map
            }
            val (page0, pageMap) = visible(current.page(0))
            val (dock, dockMap) = visible(current.dock)
            val pages = current.pages.toMutableList().apply { if (isEmpty()) add(emptyList()) }
            pages[0] = page0
            return DisplayLayout(current.copy(dock = dock, pages = pages), pageMap, dockMap)
        }
    }
}

/** Identity of a home item independent of its contents (folder renames keep identity). */
object HomeGridIdentity {
    fun of(item: HomeItem): String = when (item) {
        is HomeItem.App -> "a:" + item.key.serialize()
        is HomeItem.Folder -> "f:" + item.id
        is HomeItem.SmartFolder -> "s:" + item.id
        is HomeItem.Widget -> "w:" + item.appWidgetId
    }
}
