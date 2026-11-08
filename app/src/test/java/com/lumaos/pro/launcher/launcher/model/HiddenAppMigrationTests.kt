package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.vault.core.VaultHiddenApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Legacy Hide Apps -> Vault migration.
 *
 * This is the one code path that can make an app the user hid reappear in the
 * drawer, so it is pinned by tests: everything the old preference listed must
 * arrive in the Vault, malformed entries must be dropped rather than guessed at,
 * and the conversion must be safe to run more than once.
 */
class HiddenAppMigrationTests {

    private fun legacy(vararg keys: String) = keys.joinToString("|")

    @Test
    fun everyLegacyKeyBecomesAVaultEntry() {
        val raw = mapOf(
            "PERSONAL" to legacy(
                "0:com.example.bank/com.example.bank.Main",
                "0:com.example.chat/com.example.chat.Main"
            )
        )
        val migrated = HiddenAppMigration.fromLegacy(raw)
        assertEquals(2, migrated.size)
        assertEquals("com.example.bank", migrated[0].packageName)
        assertEquals("com.example.bank.Main", migrated[0].className)
        // The old preference stored keys only; an empty label is honest about that
        // and the launcher fills in a real label once it can resolve the app.
        assertEquals("", migrated[0].label)
    }

    @Test
    fun entriesFromSeveralContextProfilesAreMergedWithoutDuplicates() {
        // The old list was per context profile; the Vault's list is one list.
        val raw = mapOf(
            "PERSONAL" to legacy("0:com.example.bank/com.example.bank.Main"),
            "WORK" to legacy(
                "0:com.example.bank/com.example.bank.Main",
                "10:com.example.bank/com.example.bank.Main"
            )
        )
        val migrated = HiddenAppMigration.fromLegacy(raw)
        assertEquals(2, migrated.size)
        assertEquals(setOf(0L, 10L), migrated.mapTo(HashSet()) { it.userSerial })
    }

    @Test
    fun theSamePackageUnderTwoProfilesStaysTwoEntries() {
        val raw = mapOf(
            "PERSONAL" to legacy("0:com.example.mail/com.example.mail.Main"),
            "WORK" to legacy("10:com.example.mail/com.example.mail.Main")
        )
        val migrated = HiddenAppMigration.fromLegacy(raw)
        assertEquals(2, migrated.size)
        assertEquals(2, migrated.mapTo(HashSet()) { it.componentKey }.size)
    }

    @Test
    fun malformedLegacyValuesAreDroppedNotReinterpreted() {
        val raw = mapOf(
            "PERSONAL" to listOf(
                "0:com.example.bank/com.example.bank.Main", // valid
                "",                                        // empty slot
                "garbage",                                 // no colon
                "0:com.example.bank",                      // no class
                "x:com.example.bank/com.example.bank.Main", // non-numeric serial
                "|"                                        // separator only
            ).joinToString("|")
        )
        val migrated = HiddenAppMigration.fromLegacy(raw)
        assertEquals(1, migrated.size)
        assertEquals("com.example.bank", migrated[0].packageName)
    }

    @Test
    fun anEmptyOrMissingPreferenceMigratesNothing() {
        assertTrue(HiddenAppMigration.fromLegacy(emptyMap()).isEmpty())
        assertTrue(HiddenAppMigration.fromLegacy(mapOf("PERSONAL" to "")).isEmpty())
        assertTrue(HiddenAppMigration.fromLegacy(mapOf("PERSONAL" to null)).isEmpty())
    }

    @Test
    fun runningItTwiceProducesTheSameEntries() {
        val raw = mapOf("PERSONAL" to legacy("0:a.b/a.b.Main", "0:a.c/a.c.Main"))
        val first = HiddenAppMigration.fromLegacy(raw)
        val second = HiddenAppMigration.fromLegacy(raw)
        assertEquals(first.map { it.componentKey }, second.map { it.componentKey })
    }

    @Test
    fun pendingListsOnlyWhatTheVaultDoesNotAlreadyHold() {
        val legacy = listOf(
            VaultHiddenApp("com.example.bank", "com.example.bank.Main", 0L),
            VaultHiddenApp("com.example.chat", "com.example.chat.Main", 0L)
        )
        val existing = setOf("0:com.example.bank/com.example.bank.Main")
        val pending = HiddenAppMigration.pending(legacy, existing)
        assertEquals(1, pending.size)
        assertEquals("com.example.chat", pending[0].packageName)
    }

    @Test
    fun aFullyImportedSelectionIsRecognised() {
        val legacy = listOf(VaultHiddenApp("com.example.bank", "com.example.bank.Main", 0L))
        val keys = setOf("0:com.example.bank/com.example.bank.Main")
        assertTrue(HiddenAppMigration.isFullyImported(legacy, keys))
        assertFalse(HiddenAppMigration.isFullyImported(legacy, emptySet()))
        // An empty legacy selection is trivially complete - no import is needed.
        assertTrue(HiddenAppMigration.isFullyImported(emptyList(), emptySet()))
    }

    @Test
    fun migrationNeverTouchesAnythingButTheHiddenList() {
        // The conversion is a pure function of the preference text: no deletion, no
        // side effects. This test documents that contract explicitly, because the
        // operation runs against real user data on first unlock.
        val raw = mapOf("PERSONAL" to legacy("0:com.example.bank/com.example.bank.Main"))
        val snapshot = HashMap(raw)
        HiddenAppMigration.fromLegacy(raw)
        assertEquals(snapshot, raw)
    }
}
