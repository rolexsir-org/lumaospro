package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultHiddenApp
import com.lumaos.pro.launcher.vault.core.VaultIndex
import com.lumaos.pro.launcher.vault.core.HiddenApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-time move of the old launcher-level Hide Apps list into the Vault.
 *
 * Two things must hold, and both are cheap to get wrong:
 *
 *  - a user who hid apps before the upgrade must keep them hidden afterwards;
 *  - running the import twice (or after a crash halfway through) must not
 *    duplicate, resurrect or drop anything.
 *
 * The manager-level orchestration is exercised here by driving the same index
 * operations it uses, so the rules are pinned without needing an Android context.
 */
class VaultLegacyHiddenAppsMigrationTests {

    private val bank = VaultHiddenApp("com.example.bank", "com.example.bank.Main")
    private val chat = VaultHiddenApp("com.example.chat", "com.example.chat.Main")

    private fun VaultIndex.withAll(apps: List<VaultHiddenApp>): VaultIndex {
        var index = this
        apps.forEach { app ->
            index = index.withHiddenApp(
                HiddenApp(app.packageName, app.className, app.userSerial, app.label, 0L)
            )
        }
        return index
    }

    @Test
    fun anEmptyIndexHasNothingHidden() {
        assertTrue(VaultIndex.empty("v").hiddenComponentKeys().isEmpty())
    }

    @Test
    fun anIndexWrittenBeforeTheFieldExistedStillDecodes() {
        // The format is additive: an older build wrote no "hiddenApps" key at all.
        val decoded = VaultIndex.fromMap(
            mapOf(
                "format" to VaultIndex.FORMAT,
                "vaultId" to "v",
                "generation" to 1,
                "updatedAt" to 0L,
                "settings" to mapOf("autoLockMs" to 0L)
            ),
            expectedVaultId = null
        )
        assertTrue(decoded.settings.hiddenApps.isEmpty())
    }

    @Test
    fun importingTheLegacyListHidesEveryApp() {
        val index = VaultIndex.empty("v").withAll(listOf(bank, chat))
        assertEquals(setOf(bank.componentKey, chat.componentKey), index.hiddenComponentKeys())
    }

    @Test
    fun importingTwiceIsANoOp() {
        val once = VaultIndex.empty("v").withAll(listOf(bank, chat))
        val twice = once.withAll(listOf(bank, chat))
        assertEquals(once.hiddenComponentKeys(), twice.hiddenComponentKeys())
        assertEquals(2, twice.settings.hiddenApps.size)
    }

    @Test
    fun aLegacyEntryAlreadyPresentIsNotDuplicated() {
        val index = VaultIndex.empty("v").withAll(listOf(bank))
        val after = index.withAll(listOf(bank, chat))
        assertEquals(2, after.settings.hiddenApps.size)
        assertEquals(1, after.settings.hiddenApps.count { it.componentKey == bank.componentKey })
    }

    @Test
    fun workProfileEntriesSurviveTheImport() {
        val work = VaultHiddenApp("com.example.mail", "com.example.mail.Main", userSerial = 10L)
        val index = VaultIndex.empty("v").withAll(listOf(work))
        assertEquals(setOf("10:com.example.mail/com.example.mail.Main"), index.hiddenComponentKeys())
    }

    @Test
    fun hidingThenRestoringIsReversible() {
        val hidden = VaultIndex.empty("v").withAll(listOf(bank))
        val restored = hidden.withoutHiddenApp(bank.componentKey)
        assertTrue(restored.hiddenComponentKeys().isEmpty())
        assertFalse(restored.hiddenComponentKeys().contains(bank.componentKey))
    }

    @Test
    fun theStoredEntryCarriesOnlyLauncherMetadata() {
        // The point of the whole change: the Vault records which launcher entry is
        // hidden, and nothing about the app itself. No path, no data, no state.
        val index = VaultIndex.empty("v").withAll(listOf(bank))
        val stored = index.settings.hiddenApps.single().toMap()
        assertEquals(
            setOf("pkg", "cls", "user", "label", "at"),
            stored.keys
        )
    }

    @Test
    fun hiddenKeysRoundTripThroughTheStoredIndex() {
        val index = VaultIndex.empty("v").withAll(listOf(bank, chat))
        val decoded = VaultIndex.fromMap(index.toMap(), expectedVaultId = "v")
        assertEquals(index.hiddenComponentKeys(), decoded.hiddenComponentKeys())
    }
}
