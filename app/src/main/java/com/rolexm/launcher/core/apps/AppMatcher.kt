package com.rolexm.launcher.core.apps

import com.rolexm.launcher.core.model.ComponentKey
import java.text.Collator
import java.util.Locale

/**
 * A launchable app as shown by the drawer and home screen.
 *
 * [sortLabel] and [searchText] are pre-computed by the repository so that
 * sorting and filtering never touch the display label on the UI thread.
 */
data class AppEntry(
    val key: ComponentKey,
    val label: String,
    val sortLabel: String = AppMatcher.normalize(label),
    val searchText: String = AppMatcher.normalize(label),
    /** ApplicationInfo.category as declared by the app (-1 = undefined). */
    val category: Int = AppCategories.UNDEFINED,
    /** PackageInfo.firstInstallTime in epoch millis; 0 when unknown. */
    val installedAt: Long = 0L
)

/**
 * Sorting, bucketing and search over the app list.
 *
 * Pure JVM logic: no Android imports, so it is covered by unit tests rather
 * than needing an emulator. [Collator] comes from the JDK and behaves
 * identically on device.
 */
object AppMatcher {

    /** Bucket character for apps that do not begin with a letter. */
    const val OTHER_BUCKET = '#'
    private val WHITESPACE = Regex("\\s+")

    // Collator is mutable and not thread-safe. Search filtering runs off the
    // main thread, so use one configured instance per worker thread.
    private val collators = ThreadLocal.withInitial {
        Collator.getInstance().apply {
            strength = Collator.TERTIARY
            decomposition = Collator.CANONICAL_DECOMPOSITION
        }
    }

    fun normalize(label: String): String =
        label.trim().lowercase(Locale.getDefault()).replace(WHITESPACE, " ")

    /**
     * Which index character an app belongs to. Letters group by upper-case
     * initial; digits, symbols and CJK/other scripts fall into '#'.
     */
    fun bucketOf(sortLabel: String): Char {
        val first = sortLabel.firstOrNull { !it.isWhitespace() } ?: return OTHER_BUCKET
        return if (Character.isLetter(first)) first.uppercaseChar() else OTHER_BUCKET
    }

    /**
     * Alphabetical order, '#' last, ties broken by package then class so the
     * order is stable across reloads (a launcher that reshuffles on every
     * package event is worse than a slow one).
     */
    val comparator: Comparator<AppEntry> = Comparator { a, b ->
        val ba = bucketOf(a.sortLabel)
        val bb = bucketOf(b.sortLabel)
        when {
            ba != bb -> when {
                ba == OTHER_BUCKET -> 1
                bb == OTHER_BUCKET -> -1
                else -> ba.compareTo(bb)
            }
            else -> {
                val byLabel = collators.get().compare(a.sortLabel, b.sortLabel)
                if (byLabel != 0) byLabel
                else {
                    val byPkg = a.key.packageName.compareTo(b.key.packageName)
                    if (byPkg != 0) byPkg else a.key.className.compareTo(b.key.className)
                }
            }
        }
    }

    fun sort(apps: List<AppEntry>): List<AppEntry> = apps.sortedWith(comparator)

    /**
     * Relevance tiers, lower is better. Kept as named constants so the ranking
     * is legible rather than a magic-number soup.
     */
    internal const val SCORE_EXACT = 0
    internal const val SCORE_PREFIX = 1
    internal const val SCORE_WORD = 2
    internal const val SCORE_SUBSTRING = 3
    internal const val SCORE_PACKAGE = 4

    /** Best relevance tier for [query], or null when the app does not match. */
    internal fun score(entry: AppEntry, query: String): Int? {
        val label = entry.sortLabel
        if (label == query) return SCORE_EXACT
        if (label.startsWith(query)) return SCORE_PREFIX
        if (matchesWordStart(label, query)) return SCORE_WORD
        if (label.contains(query)) return SCORE_SUBSTRING
        if (entry.key.packageName.contains(query)) return SCORE_PACKAGE
        return null
    }

    /** True when any word inside [label] starts with [query]. */
    private fun matchesWordStart(label: String, query: String): Boolean {
        var atWordStart = true
        var i = 0
        while (i < label.length) {
            if (label[i].isLetterOrDigit()) {
                if (atWordStart && label.regionMatches(i, query, 0, query.length, ignoreCase = true)) {
                    return true
                }
                atWordStart = false
            } else {
                atWordStart = true
            }
            i++
        }
        return false
    }

    /**
     * Filters by relevance tier, preserving alphabetical order within a tier.
     * A blank query returns the input unchanged (no copy, no re-sort).
     */
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> {
        val q = normalize(query)
        if (q.isBlank()) return apps
        val ranked = ArrayList<Pair<Int, AppEntry>>(apps.size)
        for (entry in apps) {
            val s = score(entry, q)
            if (s != null) ranked.add(s to entry)
        }
        ranked.sortWith(
            compareBy<Pair<Int, AppEntry>> { it.first }
                .thenComparator { a, b -> comparator.compare(a.second, b.second) }
        )
        return ranked.map { it.second }
    }

    /**
     * Index for the alphabetical fast-scroller: bucket character -> first
     * position in [sorted]. Computed once per list change, not per scroll.
     */
    fun buckets(sorted: List<AppEntry>): List<Pair<Char, Int>> {
        val out = ArrayList<Pair<Char, Int>>()
        var last: Char? = null
        sorted.forEachIndexed { index, entry ->
            val b = bucketOf(entry.sortLabel)
            if (b != last) {
                out.add(b to index)
                last = b
            }
        }
        return out
    }
}
