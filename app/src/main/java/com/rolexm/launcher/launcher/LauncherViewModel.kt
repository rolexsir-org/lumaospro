package com.rolexm.launcher.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rolexm.launcher.core.RolexApp
import com.rolexm.launcher.core.apps.AppEntry
import com.rolexm.launcher.core.apps.AppMatcher
import com.rolexm.launcher.core.apps.DefaultDockResolver
import com.rolexm.launcher.core.model.ComponentKey
import com.rolexm.launcher.core.prefs.RolexPrefs
import com.rolexm.launcher.launcher.model.HomeItem
import com.rolexm.launcher.launcher.model.HomeLayout
import com.rolexm.launcher.launcher.model.HomeLayoutCodec
import com.rolexm.launcher.launcher.model.HomeLayoutOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State for the home screen and app drawer.
 *
 * Everything the UI shows is derived from StateFlows that survive configuration
 * changes, so rotating the phone or being recreated after a process death
 * rebuilds exactly the same screen with no saved-instance-state plumbing.
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val core = (application as RolexApp).core
    private val repository = core.appRepository
    val prefs: RolexPrefs = core.prefs

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /**
     * Drawer contents: the full list filtered by the current query.
     *
     * WhileSubscribed(5s) rather than Eagerly so that when the drawer is closed
     * and the home screen is idle, filtering work stops.
     */
    val drawerApps: StateFlow<List<AppEntry>> = combine(_apps, _query) { list, query ->
        AppMatcher.filter(list, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _layout = MutableStateFlow(HomeLayout.EMPTY)
    val layout: StateFlow<HomeLayout> = _layout

    /** Index for the drawer's A-Z strip, recomputed only when the list changes. */
    private val _buckets = MutableStateFlow<List<Pair<Char, Int>>>(emptyList())
    val buckets: StateFlow<List<Pair<Char, Int>>> = _buckets

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        viewModelScope.launch {
            prefs.homeLayoutRaw.collect { raw -> _layout.value = HomeLayoutCodec.decode(raw) }
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
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.refresh()
            _isLoading.value = false
            withContext(Dispatchers.Main) {
                _apps.value = list
                _buckets.value = AppMatcher.buckets(list)
            }
            seedDefaultDockIfNeeded(list)
            pruneUninstalled(list)
        }
    }

    /** First run only: fill the dock with the device's real dialler/browser/etc. */
    private suspend fun seedDefaultDockIfNeeded(apps: List<AppEntry>) {
        if (apps.isEmpty()) return
        if (prefs.layoutSeededOnce()) return
        val keys = DefaultDockResolver.resolve(getApplication(), apps)
        if (keys.isEmpty()) return
        val seeded = keys.fold(HomeLayout.EMPTY) { acc, key -> HomeLayoutOps.addToDock(acc, key) }
        persistLayout(seeded)
        prefs.setLayoutSeeded(true)
    }

    /**
     * Removes home items whose app is gone, and collapses folders that have
     * fallen to a single member. Runs after every package change so the home
     * screen never shows a dead icon.
     */
    private suspend fun pruneUninstalled(apps: List<AppEntry>) {
        val installed = apps.mapTo(HashSet()) { it.key }
        val current = _layout.value
        val pruned = HomeLayoutOps.prune(current, installed)
        if (pruned != current) persistLayout(pruned)
    }

    private suspend fun persistLayout(layout: HomeLayout) {
        withContext(Dispatchers.Main) { _layout.value = layout }
        prefs.setHomeLayoutRaw(HomeLayoutCodec.encode(layout))
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun labelFor(key: ComponentKey): String =
        _apps.value.firstOrNull { it.key == key }?.label ?: key.packageName

    fun installedKeys(): Set<ComponentKey> = _apps.value.mapTo(HashSet()) { it.key }

    // --- Layout mutations. All persist immediately. ----------------------

    fun addToHome(key: ComponentKey) = mutate { HomeLayoutOps.addToHome(it, key) }

    fun addToDock(key: ComponentKey) = mutate { HomeLayoutOps.addToDock(it, key) }

    fun removeFromHome(position: Int) = mutate { HomeLayoutOps.removeFromHome(it, 0, position) }

    fun removeFromDock(position: Int) = mutate { HomeLayoutOps.removeFromDock(it, position) }

    fun createFolder(firstPosition: Int, secondPosition: Int) = mutate { layout ->
        HomeLayoutOps.createFolder(
            layout = layout,
            pageIndex = 0,
            firstPosition = firstPosition,
            secondPosition = secondPosition,
            folderId = "folder_${System.currentTimeMillis()}"
        )
    }

    fun renameFolder(position: Int, name: String) = mutate {
        HomeLayoutOps.renameFolder(it, 0, position, name)
    }

    fun addToFolder(folderPosition: Int, key: ComponentKey) = mutate {
        HomeLayoutOps.addToFolder(it, 0, folderPosition, key)
    }

    /** Removes an app from a folder; the folder collapses if one child is left. */
    fun removeFromFolder(folderPosition: Int, key: ComponentKey) = mutate { layout ->
        val folder = layout.page(0).getOrNull(folderPosition) as? HomeItem.Folder ?: return@mutate layout
        val remaining = folder.children - key
        when {
            remaining.isEmpty() -> HomeLayoutOps.removeFromHome(layout, 0, folderPosition)
            remaining.size == 1 -> {
                val replaced = HomeLayoutOps.removeFromHome(layout, 0, folderPosition)
                HomeLayoutOps.addToHome(replaced, remaining.first())
            }
            else -> HomeLayoutOps.setPage(
                layout, 0,
                layout.page(0).toMutableList().apply {
                    set(folderPosition, folder.copy(children = remaining))
                }
            )
        }
    }

    private fun mutate(transform: (HomeLayout) -> HomeLayout) {
        viewModelScope.launch {
            val next = transform(_layout.value)
            if (next != _layout.value) persistLayout(next)
        }
    }

    private suspend fun RolexPrefs.layoutSeededOnce(): Boolean = layoutSeeded.first()
}
