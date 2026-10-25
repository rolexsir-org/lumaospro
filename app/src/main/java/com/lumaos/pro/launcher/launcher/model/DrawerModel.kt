package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.apps.AppCategories
import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.core.apps.AppMatcher
import com.lumaos.pro.launcher.core.apps.DrawerCategory
import com.lumaos.pro.launcher.core.apps.DrawerSort
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.core.usage.Frecency
import com.lumaos.pro.launcher.core.usage.LaunchRecord
import java.util.Locale

/** Visual presentation only; filtering, ordering and launch behavior stay shared. */
enum class DrawerPresentation(val storageKey: String) {
    /** One-handed vertical rows with large labels and an A–Z rail. */
    LIST("list"),
    /** The original icon grid, retained for existing and preferred grid setups. */
    GRID("grid");

    companion object {
        fun fromStorageKey(raw: String?): DrawerPresentation? =
            entries.firstOrNull { it.storageKey == raw }
    }
}

/** Which slice of the app list the drawer shows. */
sealed interface DrawerFilter {
    data object All : DrawerFilter
    /** Apps launched through LumaOS Pro, most used first. */
    data object Frequent : DrawerFilter
    data class Category(val category: DrawerCategory) : DrawerFilter
}

enum class DrawerAction { WEB_SEARCH, PLAY_STORE }

/** A shortcut as seen by the pure drawer model (the platform object stays in the repository). */
data class ShortcutRef(val packageName: String, val id: String, val label: String)

/** One row of the drawer list. */
sealed interface DrawerRow {
    val stableKey: String

    data class Header(val id: String, val title: String) : DrawerRow {
        override val stableKey: String get() = "h:$id"
    }

    data class App(val entry: AppEntry, val section: String) : DrawerRow {
        override val stableKey: String get() = "a:$section:${entry.key.serialize()}"
    }

    data class Shortcut(val ref: ShortcutRef, val appLabel: String) : DrawerRow {
        override val stableKey: String get() = "s:${ref.packageName}/${ref.id}"
    }

    data class Action(val action: DrawerAction, val query: String) : DrawerRow {
        override val stableKey: String get() = "x:${action.name}"
    }
}

/**
 * Builds the drawer contents from real data only: the installed-app list,
 * LumaOS Pro's own launch history, app-declared categories and the shortcuts the
 * system reports. Pure, so ranking and filtering are unit tested.
 */
object DrawerModel {

    data class Input(
        val apps: List<AppEntry>,
        val query: String = "",
        val filter: DrawerFilter = DrawerFilter.All,
        val sort: DrawerSort = DrawerSort.ALPHABETICAL,
        val hidden: Set<ComponentKey> = emptySet(),
        /** Opt-in: hidden apps may be found by explicit search. */
        val hiddenInSearch: Boolean = false,
        val history: Collection<LaunchRecord> = emptyList(),
        val suggestionsEnabled: Boolean = true,
        /** Explicit, user-pinned apps in their saved order. Not usage-ranked. */
        val pinned: List<ComponentKey> = emptyList(),
        val nowMillis: Long = 0L,
        val hourOfDay: Int = 12,
        val suggestionCount: Int = 4,
        val shortcuts: List<ShortcutRef> = emptyList(),
        val webSearchAvailable: Boolean = false,
        val storeAvailable: Boolean = false
    )

    data class Output(
        val rows: List<DrawerRow>,
        /** A-Z index: bucket -> adapter position; empty when the index does not apply. */
        val buckets: List<Pair<Char, Int>>,
        /** Apps the current view contains (for the header count). */
        val appCount: Int
    )

    const val SECTION_PINNED = "pinned"
    const val SECTION_SUGGESTED = "suggested"
    const val SECTION_ALL = "all"
    const val SECTION_RESULTS = "results"

    /**
     * Keeps the selected chip in sync with the filters currently offered by
     * Settings and the installed-app snapshot. A filter whose chip disappeared
     * must not leave the drawer silently stuck on an empty, unresettable view.
     */
    fun availableFilter(
        selected: DrawerFilter,
        categories: List<DrawerCategory>,
        categoriesEnabled: Boolean,
        frequentEnabled: Boolean
    ): DrawerFilter = when (selected) {
        DrawerFilter.All -> DrawerFilter.All
        DrawerFilter.Frequent -> if (frequentEnabled) DrawerFilter.Frequent else DrawerFilter.All
        is DrawerFilter.Category -> if (categoriesEnabled && selected.category in categories) selected else DrawerFilter.All
    }

    fun build(input: Input): Output {
        val query = input.query.trim()
        val visible = input.apps.filterNot { it.key in input.hidden }
        val installed = input.apps.mapTo(HashSet()) { it.key }
        val visibleKeys = visible.mapTo(HashSet()) { it.key }
        val byKey = input.apps.associateBy { it.key }

        if (query.isNotEmpty()) {
            val pool = if (input.hiddenInSearch) input.apps else visible
            val matches = AppMatcher.filter(pool, query)
            val rows = ArrayList<DrawerRow>()
            matches.mapTo(rows) { DrawerRow.App(it, SECTION_RESULTS) }
            val q = AppMatcher.normalize(query)
            // Shortcuts of hidden apps follow the same opt-in as the apps.
            val allowedPackages = pool.mapTo(HashSet()) { it.key.packageName }
            val shortcutMatches = input.shortcuts.asSequence()
                .filter { it.packageName in allowedPackages }
                .filter { AppMatcher.normalize(it.label).contains(q) }
                .take(MAX_SHORTCUT_RESULTS)
                .toList()
            if (shortcutMatches.isNotEmpty()) {
                rows += DrawerRow.Header("shortcuts", "Shortcuts")
                shortcutMatches.forEach { ref ->
                    val appLabel = input.apps.firstOrNull { it.key.packageName == ref.packageName }?.label ?: ref.packageName
                    rows += DrawerRow.Shortcut(ref, appLabel)
                }
            }
            if (input.webSearchAvailable || input.storeAvailable) {
                rows += DrawerRow.Header("elsewhere", "Search elsewhere")
                if (input.webSearchAvailable) rows += DrawerRow.Action(DrawerAction.WEB_SEARCH, query)
                if (input.storeAvailable) rows += DrawerRow.Action(DrawerAction.PLAY_STORE, query)
            }
            return Output(rows, emptyList(), matches.size)
        }

        val eligible: (ComponentKey) -> Boolean = { it in visibleKeys && it in installed }
        val records = if (input.suggestionsEnabled) input.history else emptyList()
        val list: List<AppEntry> = when (val f = input.filter) {
            DrawerFilter.All -> visible
            DrawerFilter.Frequent -> Frecency.frequent(records, input.nowMillis, eligible, Int.MAX_VALUE)
                .mapNotNull { byKey[it] }
            is DrawerFilter.Category -> visible.filter { AppCategories.of(it.category) == f.category }
        }
        val sorted = if (input.filter == DrawerFilter.Frequent) list else sort(list, input.sort, records, input.nowMillis)

        val pinnedApps = if (input.filter == DrawerFilter.All) {
            input.pinned.asSequence().distinct().mapNotNull { byKey[it] }.filter { it.key in visibleKeys }.toList()
        } else emptyList()
        val pinnedKeys = pinnedApps.mapTo(HashSet()) { it.key }

        val rows = ArrayList<DrawerRow>(sorted.size + pinnedApps.size + input.suggestionCount + 4)
        if (pinnedApps.isNotEmpty()) {
            rows += DrawerRow.Header(SECTION_PINNED, "Pinned")
            pinnedApps.mapTo(rows) { DrawerRow.App(it, SECTION_PINNED) }
        }
        if (input.filter == DrawerFilter.All && input.suggestionsEnabled) {
            val suggested = Frecency.suggestions(
                records, input.nowMillis, input.hourOfDay,
                { eligible(it) && it !in pinnedKeys }, input.suggestionCount
            ).mapNotNull { byKey[it] }
            if (suggested.isNotEmpty()) {
                rows += DrawerRow.Header(SECTION_SUGGESTED, "Suggested")
                suggested.mapTo(rows) { DrawerRow.App(it, SECTION_SUGGESTED) }
            }
        }
        if (rows.isNotEmpty()) rows += DrawerRow.Header(SECTION_ALL, "All apps")
        val firstAppRow = rows.size
        sorted.mapTo(rows) { DrawerRow.App(it, SECTION_ALL) }

        val buckets = if (input.sort == DrawerSort.ALPHABETICAL && input.filter !is DrawerFilter.Frequent) {
            AppMatcher.buckets(sorted).map { (c, i) -> c to i + firstAppRow }
        } else emptyList()
        return Output(rows, buckets, sorted.size)
    }

    fun sort(apps: List<AppEntry>, sort: DrawerSort, history: Collection<LaunchRecord>, now: Long): List<AppEntry> =
        when (sort) {
            DrawerSort.ALPHABETICAL -> apps
            DrawerSort.NEWEST -> apps.sortedWith(compareByDescending<AppEntry> { it.installedAt }.then(AppMatcher.comparator))
            DrawerSort.MOST_USED -> {
                val scores = history.associate { it.key to Frecency.decayed(it.score, it.scoreAt, now) }
                apps.sortedWith(compareByDescending<AppEntry> { scores[it.key] ?: 0.0 }.then(AppMatcher.comparator))
            }
        }

    /** Header text drawn in the active typography. */
    fun headerText(title: String, caps: Boolean): String = if (caps) title.uppercase(Locale.getDefault()) else title

    const val MAX_SHORTCUT_RESULTS = 6
}
