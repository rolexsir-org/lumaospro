package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.model.ComponentKey

/**
 * What the user has arranged on the home screen.
 *
 * Pure data + pure operations: adding, removing, moving and foldering are
 * functions that return a new layout, so they are unit tested without a device.
 *
 * The model holds [ComponentKey]s only, never labels or icons. Labels are
 * re-resolved from the live app list at render time, so a rename, an uninstall
 * or a locale change shows up immediately instead of being frozen into the
 * saved layout.
 */
sealed interface HomeItem {
    data class App(val key: ComponentKey) : HomeItem

    data class Folder(
        val id: String,
        val name: String,
        val children: List<ComponentKey>
    ) : HomeItem

    /**
     * An app widget hosted by LumaOS Pro's AppWidgetHost. [appWidgetId] is only
     * meaningful on this device and this install, which is why widgets are
     * stripped from backups.
     */
    data class Widget(
        val appWidgetId: Int,
        /** Flattened provider ComponentName, kept for diagnostics and re-binding checks. */
        val provider: String,
        /** Height in home-grid rows (widgets span the full grid width). */
        val rows: Int
    ) : HomeItem

    /**
     * A folder whose members are computed live from real data - LumaOS Pro's own
     * launch history or install times - instead of being stored.
     */
    data class SmartFolder(
        val id: String,
        val kind: SmartKind,
        val name: String
    ) : HomeItem
}

enum class SmartKind(val storageKey: String) {
    /** Most launched through LumaOS Pro (frecency). */
    FREQUENT("frequent"),
    /** Most recently launched through LumaOS Pro. */
    RECENT("recent"),
    /** Most recently installed (PackageManager first-install time). */
    NEW_APPS("new");

    companion object {
        fun fromStorageKey(raw: String?): SmartKind? = entries.firstOrNull { it.storageKey == raw }
    }
}

data class HomeLayout(
    /** Bottom dock, always visible. Kept separate from the page grid. */
    val dock: List<HomeItem> = emptyList(),
    /**
     * Home pages. Page 0 is rendered; the model is a list so that multi-page
     * support does not require a data migration later.
     */
    val pages: List<List<HomeItem>> = listOf(emptyList())
) {
    val pageCount: Int get() = pages.size

    fun page(index: Int): List<HomeItem> = pages.getOrElse(index) { emptyList() }

    /** Widget ids referenced anywhere in the layout. */
    fun widgetIds(): Set<Int> =
        (dock + pages.flatten()).filterIsInstance<HomeItem.Widget>().mapTo(HashSet()) { it.appWidgetId }

    companion object {
        val EMPTY = HomeLayout()
        const val MAX_DOCK = 7
        const val MIN_WIDGET_ROWS = 1
        const val MAX_WIDGET_ROWS = 4
    }
}

/**
 * Layout mutations. Every function returns a fresh [HomeLayout]; none mutate
 * in place, which keeps history/undo straightforward and makes the functions
 * trivially testable.
 */
object HomeLayoutOps {

    fun setPage(layout: HomeLayout, index: Int, items: List<HomeItem>): HomeLayout {
        val pages = layout.pages.toMutableList()
        while (pages.size <= index) pages.add(emptyList())
        pages[index] = items
        return layout.copy(pages = pages)
    }

    /** Adds an app to the end of page 0. No-op if already present. */
    fun addToHome(layout: HomeLayout, key: ComponentKey): HomeLayout {
        if (containsKey(layout, key)) return layout
        val items = layout.page(0) + HomeItem.App(key)
        return setPage(layout, 0, items)
    }

    fun addToDock(layout: HomeLayout, key: ComponentKey): HomeLayout {
        // Match addToHome: an app has one launcher location, not a duplicate
        // entry in both the home grid and dock.
        if (containsKey(layout, key)) return layout
        if (layout.dock.size >= HomeLayout.MAX_DOCK) return layout
        return layout.copy(dock = layout.dock + HomeItem.App(key))
    }

    /** Seeds the first-run dock only when no user content has been arranged yet. */
    fun seedDockIfEmpty(layout: HomeLayout, keys: List<ComponentKey>): HomeLayout {
        if (layout.dock.isNotEmpty() || layout.pages.any { it.isNotEmpty() }) return layout
        return keys.fold(layout) { current, key -> addToDock(current, key) }
    }

    fun containsKey(layout: HomeLayout, key: ComponentKey): Boolean =
        layout.dock.any { matches(it, key) } || layout.pages.any { page -> page.any { matches(it, key) } }

    private fun matches(item: HomeItem, key: ComponentKey): Boolean = when (item) {
        is HomeItem.App -> item.key == key
        is HomeItem.Folder -> item.children.contains(key)
        is HomeItem.Widget, is HomeItem.SmartFolder -> false
    }

    private fun conflictsWithExistingItem(layout: HomeLayout, item: HomeItem): Boolean = when (item) {
        is HomeItem.App -> containsKey(layout, item.key)
        is HomeItem.Folder -> item.children.any { containsKey(layout, it) }
        is HomeItem.Widget, is HomeItem.SmartFolder -> false
    }

    /** Removes an item at [position] on page [pageIndex]. */
    fun removeFromHome(layout: HomeLayout, pageIndex: Int, position: Int): HomeLayout {
        val items = layout.page(pageIndex).toMutableList()
        if (position !in items.indices) return layout
        items.removeAt(position)
        return setPage(layout, pageIndex, items)
    }

    fun removeFromDock(layout: HomeLayout, position: Int): HomeLayout {
        if (position !in layout.dock.indices) return layout
        return layout.copy(dock = layout.dock.toMutableList().apply { removeAt(position) })
    }

    /** Reorders an item within a page (drag and drop). Out-of-range moves are ignored. */
    fun move(layout: HomeLayout, pageIndex: Int, from: Int, to: Int): HomeLayout {
        val items = layout.page(pageIndex).toMutableList()
        if (from !in items.indices || to !in items.indices || from == to) return layout
        val item = items.removeAt(from)
        items.add(to, item)
        return setPage(layout, pageIndex, items)
    }

    fun moveInDock(layout: HomeLayout, from: Int, to: Int): HomeLayout {
        val items = layout.dock.toMutableList()
        if (from !in items.indices || to !in items.indices || from == to) return layout
        val item = items.removeAt(from)
        items.add(to, item)
        return layout.copy(dock = items)
    }

    /** Moves a home app or folder into the dock (widgets stay on the page). */
    fun moveHomeToDock(layout: HomeLayout, pageIndex: Int, position: Int): HomeLayout {
        val items = layout.page(pageIndex)
        val item = items.getOrNull(position) ?: return layout
        if (item is HomeItem.Widget) return layout
        val removed = removeFromHome(layout, pageIndex, position)
        if (conflictsWithExistingItem(removed, item)) {
            // Old or restored layouts may contain a duplicate. A plain app can
            // safely be de-duplicated by removing the source cell; a folder is
            // left untouched because dropping any member would lose data.
            return if (item is HomeItem.App) removed else layout
        }
        if (layout.dock.size >= HomeLayout.MAX_DOCK) return layout
        return removed.copy(dock = removed.dock + item)
    }

    fun moveDockToHome(layout: HomeLayout, position: Int, pageIndex: Int = 0): HomeLayout {
        val item = layout.dock.getOrNull(position) ?: return layout
        val removed = removeFromDock(layout, position)
        if (conflictsWithExistingItem(removed, item)) {
            return if (item is HomeItem.App) removed else layout
        }
        return setPage(removed, pageIndex, removed.page(pageIndex) + item)
    }

    /**
     * Replaces two home apps with a folder containing both. Returns the input
     * unchanged unless both positions hold plain apps.
     */
    fun createFolder(
        layout: HomeLayout,
        pageIndex: Int,
        firstPosition: Int,
        secondPosition: Int,
        folderId: String,
        name: String? = null
    ): HomeLayout {
        if (firstPosition == secondPosition) return layout
        val items = layout.page(pageIndex).toMutableList()
        if (firstPosition !in items.indices || secondPosition !in items.indices) return layout
        val a = items[firstPosition]
        val b = items[secondPosition]
        if (a !is HomeItem.App || b !is HomeItem.App) return layout

        val folderName = name?.takeIf { it.isNotBlank() } ?: folderNameFrom(a, b)
        val keys = listOf(a.key, b.key)
        // Insert at the lower index; removing the higher index first keeps
        // positions valid.
        val high = maxOf(firstPosition, secondPosition)
        val low = minOf(firstPosition, secondPosition)
        items.removeAt(high)
        items.removeAt(low)
        items.add(low, HomeItem.Folder(folderId, folderName, keys))
        return setPage(layout, pageIndex, items)
    }

    fun addToFolder(
        layout: HomeLayout,
        pageIndex: Int,
        folderPosition: Int,
        key: ComponentKey
    ): HomeLayout {
        val items = layout.page(pageIndex).toMutableList()
        val folder = items.getOrNull(folderPosition) as? HomeItem.Folder ?: return layout
        if (folder.children.contains(key)) return layout
        items[folderPosition] = folder.copy(children = folder.children + key)
        return setPage(layout, pageIndex, items)
    }

    /**
     * Drag-and-drop of the app at [sourcePosition] onto the folder at
     * [folderPosition]: the app joins the folder and leaves its cell.
     */
    fun dropIntoFolder(layout: HomeLayout, pageIndex: Int, sourcePosition: Int, folderPosition: Int): HomeLayout {
        val items = layout.page(pageIndex)
        val source = items.getOrNull(sourcePosition) as? HomeItem.App ?: return layout
        val folder = items.getOrNull(folderPosition) as? HomeItem.Folder ?: return layout
        if (sourcePosition == folderPosition) return layout
        val updated = items.toMutableList()
        updated[folderPosition] = if (folder.children.contains(source.key)) folder
        else folder.copy(children = folder.children + source.key)
        updated.removeAt(sourcePosition)
        return setPage(layout, pageIndex, updated)
    }

    fun renameFolder(
        layout: HomeLayout,
        pageIndex: Int,
        folderPosition: Int,
        name: String
    ): HomeLayout {
        val items = layout.page(pageIndex).toMutableList()
        when (val item = items.getOrNull(folderPosition)) {
            is HomeItem.Folder -> items[folderPosition] = item.copy(name = name)
            is HomeItem.SmartFolder -> items[folderPosition] = item.copy(name = name)
            else -> return layout
        }
        return setPage(layout, pageIndex, items)
    }

    /** Inserts a widget at [position] (end of page when out of range). */
    fun addWidget(layout: HomeLayout, pageIndex: Int, widget: HomeItem.Widget, position: Int = -1): HomeLayout {
        if (layout.widgetIds().contains(widget.appWidgetId)) return layout
        val clamped = widget.copy(rows = widget.rows.coerceIn(HomeLayout.MIN_WIDGET_ROWS, HomeLayout.MAX_WIDGET_ROWS))
        val items = layout.page(pageIndex).toMutableList()
        if (position in 0..items.size) items.add(position, clamped) else items.add(clamped)
        return setPage(layout, pageIndex, items)
    }

    fun resizeWidget(layout: HomeLayout, appWidgetId: Int, rows: Int): HomeLayout {
        val target = rows.coerceIn(HomeLayout.MIN_WIDGET_ROWS, HomeLayout.MAX_WIDGET_ROWS)
        var changed = false
        val pages = layout.pages.map { page ->
            page.map { item ->
                if (item is HomeItem.Widget && item.appWidgetId == appWidgetId && item.rows != target) {
                    changed = true
                    item.copy(rows = target)
                } else item
            }
        }
        return if (changed) layout.copy(pages = pages) else layout
    }

    fun removeWidget(layout: HomeLayout, appWidgetId: Int): HomeLayout {
        val pages = layout.pages.map { page ->
            page.filterNot { it is HomeItem.Widget && it.appWidgetId == appWidgetId }
        }
        return if (pages == layout.pages) layout else layout.copy(pages = pages)
    }

    /** Removes every widget (used when restoring a backup on another device). */
    fun stripWidgets(layout: HomeLayout): HomeLayout = layout.copy(
        dock = layout.dock.filterNot { it is HomeItem.Widget },
        pages = layout.pages.map { page -> page.filterNot { it is HomeItem.Widget } }
    )

    fun addSmartFolder(layout: HomeLayout, pageIndex: Int, folder: HomeItem.SmartFolder): HomeLayout {
        val exists = layout.pages.flatten().any { it is HomeItem.SmartFolder && it.kind == folder.kind }
        if (exists) return layout
        return setPage(layout, pageIndex, layout.page(pageIndex) + folder)
    }

    /**
     * Drops folder entries whose apps are no longer installed, and folders that
     * ended up with a single child (a one-item folder is just an icon).
     * Widgets and smart folders are not tied to one app and are kept; widgets
     * whose provider vanished are removed by the widget host instead.
     *
     * Called after package changes so a layout never shows dead entries.
     */
    fun prune(layout: HomeLayout, installed: Set<ComponentKey>): HomeLayout {
        fun pruneList(items: List<HomeItem>): List<HomeItem> = items.mapNotNull { item ->
            when (item) {
                is HomeItem.App -> if (item.key in installed) item else null
                is HomeItem.Folder -> {
                    val kept = item.children.filter { it in installed }
                    when {
                        kept.isEmpty() -> null
                        kept.size == 1 -> HomeItem.App(kept.first())
                        else -> item.copy(children = kept)
                    }
                }
                is HomeItem.Widget, is HomeItem.SmartFolder -> item
            }
        }
        return layout.copy(
            dock = pruneList(layout.dock),
            pages = layout.pages.map(::pruneList)
        )
    }

    /** Neutral default name; callers pass a better one (e.g. a shared category) when they have it. */
    internal fun folderNameFrom(a: HomeItem.App, b: HomeItem.App): String = "Folder"
}
