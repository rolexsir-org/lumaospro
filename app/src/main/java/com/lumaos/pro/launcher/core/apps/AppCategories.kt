package com.lumaos.pro.launcher.core.apps

/**
 * Drawer categories from ApplicationInfo.category - the category each app
 * declares in its own manifest (android:appCategory, API 26+).
 *
 * This is real metadata supplied by developers, not a guess: apps that do not
 * declare a category are shown under "Other" instead of being classified by
 * name heuristics. Values mirror the android.content.pm.ApplicationInfo
 * constants so this stays a pure, JVM-tested mapping.
 */
enum class DrawerCategory(val title: String, val categoryValue: Int) {
    GAMES("Games", 0),
    SOCIAL("Social", 4),
    PRODUCTIVITY("Productivity", 7),
    AUDIO("Audio", 1),
    VIDEO("Video", 2),
    IMAGES("Photos", 3),
    NEWS("News", 5),
    MAPS("Maps", 6),
    ACCESSIBILITY("Accessibility", 8),
    OTHER("Other", AppCategories.UNDEFINED);
}

object AppCategories {
    const val UNDEFINED = -1

    fun of(category: Int): DrawerCategory =
        DrawerCategory.entries.firstOrNull { it.categoryValue == category && it != DrawerCategory.OTHER }
            ?: DrawerCategory.OTHER

    /**
     * Categories that actually occur in [apps], in display order. A category
     * chip is only offered when it would contain something.
     */
    fun present(apps: List<AppEntry>): List<DrawerCategory> {
        val seen = apps.mapTo(HashSet()) { of(it.category) }
        return DrawerCategory.entries.filter { it in seen }
    }

    /** A folder name shared by two apps, when they declare the same category. */
    fun sharedTitle(a: AppEntry?, b: AppEntry?): String? {
        if (a == null || b == null) return null
        val ca = of(a.category)
        return if (ca != DrawerCategory.OTHER && ca == of(b.category)) ca.title else null
    }
}

/** Drawer ordering. */
enum class DrawerSort(val storageKey: String) {
    ALPHABETICAL("alpha"),
    /** By LumaOS Pro's own launch frecency; apps never launched follow alphabetically. */
    MOST_USED("usage"),
    /** By first-install time, newest first. */
    NEWEST("newest");

    companion object {
        fun fromStorageKey(raw: String?): DrawerSort = entries.firstOrNull { it.storageKey == raw } ?: ALPHABETICAL
    }
}
