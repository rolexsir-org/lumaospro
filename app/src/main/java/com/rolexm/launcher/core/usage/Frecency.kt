package com.rolexm.launcher.core.usage

import com.rolexm.launcher.core.model.ComponentKey
import kotlin.math.pow

/**
 * One app's launch history, as recorded by ROLEXM itself.
 *
 * Only launches made *through ROLEXM* are counted (taps on the home screen,
 * dock, drawer, folders, search and gestures). ROLEXM does not request
 * PACKAGE_USAGE_STATS, so it never sees app use that happens elsewhere, and
 * it never invents history: an app with no record simply has no score.
 */
data class LaunchRecord(
    val key: ComponentKey,
    val count: Int,
    val lastLaunchedAt: Long,
    /** Exponentially decayed launch score, valid at [scoreAt]. */
    val score: Double,
    val scoreAt: Long,
    /** Launches per hour of day (24 buckets), for time-of-day relevance. */
    val hours: IntArray = IntArray(24)
) {
    override fun equals(other: Any?): Boolean =
        other is LaunchRecord && other.key == key && other.count == count &&
            other.lastLaunchedAt == lastLaunchedAt && other.score == score &&
            other.scoreAt == scoreAt && other.hours.contentEquals(hours)

    override fun hashCode(): Int = key.hashCode() * 31 + count
}

/**
 * Frecency ranking (frequency x recency) with a 7-day half-life, plus a
 * time-of-day affinity used for suggestions. Pure Kotlin, unit tested.
 */
object Frecency {

    const val HALF_LIFE_MS: Long = 7L * 24 * 60 * 60 * 1000

    fun decayed(score: Double, from: Long, to: Long): Double {
        if (to <= from) return score
        return score * 0.5.pow((to - from).toDouble() / HALF_LIFE_MS)
    }

    fun bump(previous: LaunchRecord?, key: ComponentKey, now: Long, hourOfDay: Int): LaunchRecord {
        val hours = previous?.hours?.copyOf() ?: IntArray(24)
        val h = Math.floorMod(hourOfDay, 24)
        hours[h] = (hours[h] + 1).coerceAtMost(Int.MAX_VALUE - 1)
        val base = previous?.let { decayed(it.score, it.scoreAt, now) } ?: 0.0
        return LaunchRecord(
            key = key,
            count = (previous?.count ?: 0) + 1,
            lastLaunchedAt = now,
            score = base + 1.0,
            scoreAt = now,
            hours = hours
        )
    }

    /** Share of an app's launches within one hour of [hourOfDay], 0..1. */
    fun hourAffinity(record: LaunchRecord, hourOfDay: Int): Double {
        val total = record.hours.sum()
        if (total == 0) return 0.0
        val h = Math.floorMod(hourOfDay, 24)
        val near = record.hours[Math.floorMod(h - 1, 24)] + record.hours[h] + record.hours[Math.floorMod(h + 1, 24)]
        return near.toDouble() / total
    }

    /** Most-used apps by decayed score. */
    fun frequent(
        records: Collection<LaunchRecord>,
        now: Long,
        eligible: (ComponentKey) -> Boolean,
        limit: Int
    ): List<ComponentKey> = records.asSequence()
        .filter { eligible(it.key) }
        .map { it to decayed(it.score, it.scoreAt, now) }
        .filter { it.second >= MIN_SCORE }
        .sortedWith(compareByDescending<Pair<LaunchRecord, Double>> { it.second }
            .thenByDescending { it.first.lastLaunchedAt })
        .take(limit)
        .map { it.first.key }
        .toList()

    /** Most recently launched apps. */
    fun recent(
        records: Collection<LaunchRecord>,
        eligible: (ComponentKey) -> Boolean,
        limit: Int
    ): List<ComponentKey> = records.asSequence()
        .filter { eligible(it.key) }
        .sortedByDescending { it.lastLaunchedAt }
        .take(limit)
        .map { it.key }
        .toList()

    /**
     * Suggestions for "now": frecency boosted by how often the app is used at
     * this time of day. Needs at least [MIN_LAUNCHES_FOR_SUGGESTIONS] recorded
     * launches overall before suggesting anything, so a fresh install shows no
     * made-up recommendations.
     */
    fun suggestions(
        records: Collection<LaunchRecord>,
        now: Long,
        hourOfDay: Int,
        eligible: (ComponentKey) -> Boolean,
        limit: Int
    ): List<ComponentKey> {
        if (records.sumOf { it.count } < MIN_LAUNCHES_FOR_SUGGESTIONS) return emptyList()
        return records.asSequence()
            .filter { eligible(it.key) }
            .map { r -> r to decayed(r.score, r.scoreAt, now) * (1.0 + 0.75 * hourAffinity(r, hourOfDay)) }
            .filter { it.second >= MIN_SCORE }
            .sortedWith(compareByDescending<Pair<LaunchRecord, Double>> { it.second }
                .thenByDescending { it.first.lastLaunchedAt })
            .take(limit)
            .map { it.first.key }
            .toList()
    }

    /** Below this decayed score an app is considered not used recently. */
    const val MIN_SCORE = 0.05
    const val MIN_LAUNCHES_FOR_SUGGESTIONS = 5

    fun encodeHours(hours: IntArray): String = hours.joinToString(",")
    fun decodeHours(raw: String?): IntArray {
        val parts = raw?.split(',').orEmpty()
        val out = IntArray(24)
        if (parts.size != 24) return out
        parts.forEachIndexed { i, p -> out[i] = p.toIntOrNull()?.coerceAtLeast(0) ?: 0 }
        return out
    }
}
