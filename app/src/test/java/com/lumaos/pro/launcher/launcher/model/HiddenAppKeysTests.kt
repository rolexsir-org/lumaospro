package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drawer's half of Hidden Apps: turning the Vault's stored keys back into the
 * [ComponentKey] the app list uses.
 *
 * The drawer must never hide the wrong app, so this is deliberately strict and
 * pinned by tests rather than left to the activity wiring.
 */
class HiddenAppKeysTests {

    @Test
    fun aKeyRoundTripsThroughItsStoredForm() {
        val key = ComponentKey("com.example.private", "com.example.private.Main", 0L)
        val stored = HiddenAppKeys.serialize(key)
        assertEquals("0:com.example.private/com.example.private.Main", stored)
        assertEquals(key, HiddenAppKeys.parse(stored))
        assertEquals(setOf(key), HiddenAppKeys.toComponentKeys(listOf(stored)))
    }

    @Test
    fun workProfileSerialsArePreserved() {
        val work = ComponentKey("com.example.work", "com.example.work.Main", 10L)
        val parsed = HiddenAppKeys.parse(HiddenAppKeys.serialize(work))
        assertEquals(10L, parsed!!.userSerial)
        // The personal copy of the same package is a different app and stays visible.
        assertTrue(
            HiddenAppKeys.toComponentKeys(listOf(HiddenAppKeys.serialize(work)))
                .none { it.userSerial == 0L }
        )
    }

    @Test
    fun malformedKeysAreDroppedNotGuessed() {
        assertNull(HiddenAppKeys.parse(""))
        assertNull(HiddenAppKeys.parse("no-serial:com.a/b"))
        assertNull(HiddenAppKeys.parse("0:"))
        assertNull(HiddenAppKeys.parse("0:com.a"))
        assertNull(HiddenAppKeys.parse("0:/com.a.Main"))
        assertNull(HiddenAppKeys.parse("0:com.a/"))
        assertTrue(HiddenAppKeys.toComponentKeys(listOf("", "junk", "0:ok/ok.Main")).size == 1)
    }

    /** A corrupt entry must not take the rest of the hidden set with it. */
    @Test
    fun oneBadEntryDoesNotLoseTheOthers() {
        val good = listOf(
            "0:com.a/com.a.Main",
            "BROKEN",
            "10:com.b/com.b.Main"
        )
        val parsed = HiddenAppKeys.toComponentKeys(good)
        assertEquals(2, parsed.size)
        assertEquals(setOf("com.a", "com.b"), parsed.mapTo(HashSet()) { it.packageName })
    }

    /** An empty set is the honest answer for "locked" and for "nothing hidden". */
    @Test
    fun anEmptySetParsesToNothingHidden() {
        assertTrue(HiddenAppKeys.toComponentKeys(emptyList()).isEmpty())
    }
}
