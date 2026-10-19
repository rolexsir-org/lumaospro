package com.rolexm.launcher.launcher.model

import com.rolexm.launcher.core.model.ComponentKey

/**
 * What the user has arranged on the home screen.
 *
 * Pure data + pure operations: adding, removing and foldering are functions
 * that return a new layout, so they are unit tested without a device.
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
}

data class HomeLayout(
    /** Bottom dock, always visible. Kept separate from the page grid. */
    val dock: List<HomeItem> = emptyList(),
    /**
     * Home pages. Version 1 renders page 0; the model is a list so that
     * multi-page support does not require a data migration later.
     */
    val pages: List<List<HomeItem>> = listOf(emptyList())
) {
    val pageCount: Int get() = pages.size

    fun page(index: Int): List<HomeItem> = pages.getOrElse(index) { emptyList() }

    companion object {
        val EMPTY = HomeLayout()
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

    /** Adds an app to the first free slot of page 0. No-op if already present. */
    fun addToHome(layout: HomeLayout, key: ComponentKey): HomeLayout {
        if (containsKey(layout, key)) return layout
        val items = layout.page(0) + HomeItem.App(key)
        return setPage(layout, 0, items)
    }

    fun addToDock(layout: HomeLayout, key: ComponentKey): HomeLayout {
        if (layout.dock.any { it is HomeItem.App && it.key == key }) return layout
        return layout.copy(dock = layout.dock + HomeItem.App(key))
    }

    fun containsKey(layout: HomeLayout, key: ComponentKey): Boolean =
        layout.dock.any { matches(it, key) } || layout.pages.any { page -> page.any { matches(it, key) } }

    private fun matches(item: HomeItem, key: ComponentKey): Boolean = when (item) {
        is HomeItem.App -> item.key == key
        is HomeItem.Folder -> item.children.contains(key)
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

    /**
     * Replaces two home apps with a folder containing both. Returns the input
     * unchanged unless both positions hold plain apps.
     */
    fun createFolder(
        layout: HomeLayout,
        pageIndex: Int,
        firstPosition: Int,
        secondPosition: Int,
        folderId: String
    ): HomeLayout {
        if (firstPosition == secondPosition) return layout
        val items = layout.page(pageIndex).toMutableList()
        if (firstPosition !in items.indices || secondPosition !in items.indices) return layout
        val a = items[firstPosition]
        val b = items[secondPosition]
        if (a !is HomeItem.App || b !is HomeItem.App) return layout

        val name = folderNameFrom(a, b)
        val keys = listOf(a.key, b.key)
        // Insert at the lower index; removing the higher index first keeps
        // positions valid.
        val high = maxOf(firstPosition, secondPosition)
        val low = minOf(firstPosition, secondPosition)
        items.removeAt(high)
        items.removeAt(low)
        items.add(low, HomeItem.Folder(folderId, name, keys))
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

    fun renameFolder(
        layout: HomeLayout,
        pageIndex: Int,
        folderPosition: Int,
        name: String
    ): HomeLayout {
        val items = layout.page(pageIndex).toMutableList()
        val folder = items.getOrNull(folderPosition) as? HomeItem.Folder ?: return layout
        items[folderPosition] = folder.copy(name = name)
        return setPage(layout, pageIndex, items)
    }

    /**
     * Drops folder entries whose apps are no longer installed, and folders that
     * ended up with a single child (a one-item folder is just an icon).
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
            }
        }
        return layout.copy(
            dock = pruneList(layout.dock),
            pages = layout.pages.map(::pruneList)
        )
    }

    /** Short, deterministic folder name derived from its first two members. */
    internal fun folderNameFrom(a: HomeItem.App, b: HomeItem.App): String =
        "Folder ${a.key.packageName.take(4)}${b.key.packageName.take(4)}".trim()
}
