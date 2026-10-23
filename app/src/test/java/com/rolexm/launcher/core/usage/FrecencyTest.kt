package com.rolexm.launcher.core.usage

import com.rolexm.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrecencyTest {

    private fun key(p: String) = ComponentKey(p, "$p.Main")
    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_700_000_000_000L

    private fun launches(k: ComponentKey, times: List<Long>, hour: Int = 9): LaunchRecord? =
        times.fold(null as LaunchRecord?) { acc, t -> Frecency.bump(acc, k, t, hour) }

    @Test fun `bump counts and stamps launches`() {
        val r = launches(key("a"), listOf(t0, t0 + 1000))!!
        assertEquals(2, r.count)
        assertEquals(t0 + 1000, r.lastLaunchedAt)
        assertEquals(2, r.hours[9])
    }

    @Test fun `scores halve after one half-life`() {
        assertEquals(0.5, Frecency.decayed(1.0, t0, t0 + Frecency.HALF_LIFE_MS), 1e-9)
        assertEquals(1.0, Frecency.decayed(1.0, t0, t0 - 5), 0.0)
    }

    @Test fun `frequent ranks by decayed score, not raw count`() {
        val old = launches(key("old"), (0 until 10).map { t0 + it })!!          // 10 launches 4 weeks ago
        val fresh = launches(key("fresh"), (0 until 4).map { t0 + 28 * day + it })!!
        val now = t0 + 28 * day + 10
        assertEquals(listOf(key("fresh"), key("old")), Frecency.frequent(listOf(old, fresh), now, { true }, 5))
    }

    @Test fun `apps unused for two months drop out of frequent`() {
        val old = launches(key("old"), (0 until 10).map { t0 + it })!!          // 10 launches 8 weeks ago
        val now = t0 + 56 * day
        assertTrue(Frecency.frequent(listOf(old), now, { true }, 5).isEmpty())
    }

    @Test fun `ineligible apps (hidden or uninstalled) are never returned`() {
        val a = launches(key("a"), listOf(t0))!!
        val b = launches(key("b"), listOf(t0))!!
        assertEquals(listOf(key("b")), Frecency.frequent(listOf(a, b), t0, { it != key("a") }, 5))
        assertEquals(listOf(key("b")), Frecency.recent(listOf(a, b), { it != key("a") }, 5))
    }

    @Test fun `recent orders by last launch`() {
        val a = launches(key("a"), listOf(t0))!!
        val b = launches(key("b"), listOf(t0 + 5))!!
        assertEquals(listOf(key("b"), key("a")), Frecency.recent(listOf(a, b), { true }, 5))
    }

    @Test fun `no suggestions until there is real history`() {
        val a = launches(key("a"), listOf(t0, t0 + 1))!!
        assertTrue(Frecency.suggestions(listOf(a), t0 + 2, 9, { true }, 4).isEmpty())
    }

    @Test fun `time of day affinity lifts the app used at this hour`() {
        val morning = launches(key("news"), (0 until 5).map { t0 + it }, hour = 8)!!
        val evening = launches(key("tv"), (0 until 5).map { t0 + it }, hour = 21)!!
        assertEquals(key("news"), Frecency.suggestions(listOf(morning, evening), t0 + 10, 8, { true }, 2).first())
        assertEquals(key("tv"), Frecency.suggestions(listOf(morning, evening), t0 + 10, 21, { true }, 2).first())
    }

    @Test fun `hour histograms round trip and reject garbage`() {
        val h = IntArray(24) { it }
        assertTrue(h.contentEquals(Frecency.decodeHours(Frecency.encodeHours(h))))
        assertTrue(IntArray(24).contentEquals(Frecency.decodeHours("1,2,3")))
    }
}
