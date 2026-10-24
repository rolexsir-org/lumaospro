package com.rolexm.launcher.core.apps

import com.rolexm.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class AppMatcherTest {

    private fun app(label: String, pkg: String = "com.${label.lowercase()}") =
        AppEntry(ComponentKey(pkg, "$pkg.Main"), label)

    private val apps = listOf(
        app("Camera"),
        app("Chrome"),
        app("Clock"),
        app("Google Play Store", "com.android.vending"),
        app("Phone", "com.android.dialer"),
        app("YouTube", "com.google.android.youtube"),
        app("1Weather"),
        app("@mailreader")
    )

    @Test
    fun `sorts alphabetically and is stable across reloads`() {
        val sorted = AppMatcher.sort(apps.shuffled())
        val labels = sorted.map { it.label }
        assertEquals(listOf("Camera", "Chrome", "Clock", "Google Play Store", "Phone", "YouTube"), labels.take(6))
        // Non-letters sink to the end rather than interleaving. Their relative
        // order is whatever the default Collator decides, so it is not asserted.
        assertEquals(setOf("1Weather", "@mailreader"), labels.takeLast(2).toSet())
    }

    @Test
    fun `sorting is idempotent and independent of input order`() {
        val a = AppMatcher.sort(apps).map { it.key }
        val b = AppMatcher.sort(apps.reversed()).map { it.key }
        assertEquals(a, b)
    }

    @Test
    fun `buckets group by initial letter with others last`() {
        val buckets = AppMatcher.buckets(AppMatcher.sort(apps))
        assertEquals('C', buckets.first().first)
        assertEquals(0, buckets.first().second)
        assertEquals('#', buckets.last().first)
        // Every bucket index points at the first app in that bucket.
        val sorted = AppMatcher.sort(apps)
        buckets.forEach { (letter, index) ->
            assertEquals(letter, AppMatcher.bucketOf(sorted[index].sortLabel))
        }
    }

    @Test
    fun `blank query returns the input list itself`() {
        val sorted = AppMatcher.sort(apps)
        assertTrue(AppMatcher.filter(sorted, "   ") === sorted)
    }

    @Test
    fun `exact match outranks prefix and substring`() {
        val sorted = AppMatcher.sort(apps)
        val result = AppMatcher.filter(sorted, "clock")
        assertEquals("Clock", result.first().label)
    }

    @Test
    fun `prefix outranks a mid-word substring match`() {
        val sorted = AppMatcher.sort(apps)
        // "play" is a word inside "Google Play Store"; "phone" starts with "ph".
        val result = AppMatcher.filter(sorted, "pho")
        assertEquals("Phone", result.first().label)
    }

    @Test
    fun `matches on a word boundary inside a multi word label`() {
        val sorted = AppMatcher.sort(apps)
        val result = AppMatcher.filter(sorted, "play")
        assertEquals("Google Play Store", result.first().label)
    }

    @Test
    fun `repeated whitespace and case are normalised`() {
        val result = AppMatcher.filter(AppMatcher.sort(apps), "  GOOGLE\t   PLAY  ")
        assertEquals("Google Play Store", result.first().label)
    }

    @Test
    fun `package name is a last resort match`() {
        val sorted = AppMatcher.sort(apps)
        val result = AppMatcher.filter(sorted, "com.android.vending")
        assertEquals("Google Play Store", result.first().label)
    }

    @Test
    fun `an unmatched query yields nothing`() {
        assertTrue(AppMatcher.filter(AppMatcher.sort(apps), "zzzzz").isEmpty())
    }

    @Test
    fun `search sorting is deterministic across worker threads`() {
        val sorted = AppMatcher.sort(apps)
        val expected = AppMatcher.filter(sorted, "o").map { it.label }
        val workers = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 8).map {
                workers.submit<List<String>> {
                    repeat(20) {
                        assertEquals(expected, AppMatcher.filter(sorted, "o").map { it.label })
                    }
                    expected
                }
            }
            tasks.forEach { assertEquals(expected, it.get()) }
        } finally {
            workers.shutdownNow()
        }
    }

    @Test
    fun `scoring tiers are ordered as documented`() {
        val clock = app("Clock")
        assertEquals(AppMatcher.SCORE_EXACT, AppMatcher.score(clock, "clock"))
        assertEquals(AppMatcher.SCORE_PREFIX, AppMatcher.score(clock, "clo"))
        assertEquals(AppMatcher.SCORE_SUBSTRING, AppMatcher.score(clock, "ock"))
        assertEquals(AppMatcher.SCORE_PACKAGE, AppMatcher.score(clock, "com.clock"))
        assertEquals(null, AppMatcher.score(clock, "xyz"))
    }
}
