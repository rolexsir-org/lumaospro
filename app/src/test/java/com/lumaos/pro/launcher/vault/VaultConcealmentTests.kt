package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultConcealment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device-bound concealment index: what it stores, what it refuses, and what it
 * does when a generation is unreadable.
 *
 * The Android Keystore sealing itself cannot run on a plain JVM, so the crypto is
 * covered by the `VaultConcealmentStore` on-device checklist and the format rules
 * here. Everything that decides *which apps stay hidden* is pure and is pinned
 * below - because getting this wrong shows the user's private apps to whoever
 * picks up the phone.
 */
class VaultConcealmentTests {

    private fun entry(pkg: String, label: String = "", user: Long = 0L) =
        VaultConcealment.Entry(pkg, "$pkg.Main", user, label)

    @Test
    fun theSelectionRoundTripsThroughTheStoredPayload() {
        val entries = listOf(entry("com.example.bank", "Bank"), entry("com.example.chat", "Chat"))
        val payload = VaultConcealment.encode("vault-1", entries)
        val state = VaultConcealment.decode(payload, "vault-1")
        assertNotNull(state)
        assertEquals(
            setOf("0:com.example.bank/com.example.bank.Main", "0:com.example.chat/com.example.chat.Main"),
            state!!.keys
        )
        assertEquals("Bank", state.entries.first { it.packageName == "com.example.bank" }.label)
    }

    @Test
    fun anEmptySelectionRoundTripsAsEmptyNotAsFailure() {
        val payload = VaultConcealment.encode("vault-1", emptyList())
        val state = VaultConcealment.decode(payload, "vault-1")
        assertNotNull("an empty list is a valid state", state)
        assertTrue(state!!.isEmpty)
        assertFalse("and it is not degraded", state.degraded)
    }

    @Test
    fun workProfileCopiesStayDistinct() {
        val personal = VaultConcealment.Entry("com.example.mail", "com.example.mail.Main", 0L)
        val work = VaultConcealment.Entry("com.example.mail", "com.example.mail.Main", 10L)
        val payload = VaultConcealment.encode("v", listOf(personal, work))
        val keys = VaultConcealment.decode(payload, "v")!!.keys
        assertEquals(2, keys.size)
        assertTrue(keys.contains("0:com.example.mail/com.example.mail.Main"))
        assertTrue(keys.contains("10:com.example.mail/com.example.mail.Main"))
    }

    @Test
    fun aPayloadFromAnotherVaultIsRejected() {
        // Copying a mirror between vaults, or restoring one from a backup taken on a
        // different install, must not hide that vault's apps.
        val payload = VaultConcealment.encode("vault-A", listOf(entry("com.example.bank")))
        assertNull(VaultConcealment.decode(payload, "vault-B"))
    }

    @Test
    fun aTruncatedOrGarbagePayloadIsRejected() {
        val payload = VaultConcealment.encode("v", listOf(entry("com.example.bank")))
        assertNull(VaultConcealment.decode(payload.copyOf(payload.size / 2), "v"))
        assertNull(VaultConcealment.decode("not json at all".toByteArray(), "v"))
        assertNull(VaultConcealment.decode(ByteArray(0), "v"))
    }

    @Test
    fun anUnknownFormatVersionIsRejectedRatherThanGuessed() {
        val json = """{"format":99,"vaultId":"v","entries":[]}"""
        assertNull(VaultConcealment.decode(json.toByteArray(), "v"))
    }

    @Test
    fun oneMalformedRowDoesNotDiscardTheRest() {
        // A single damaged entry must not un-hide every other app.
        val json = """{"format":1,"vaultId":"v","entries":[
            {"pkg":"com.example.bank","cls":"com.example.bank.Main","user":0,"label":"Bank"},
            {"pkg":"","cls":"","user":0,"label":"broken"},
            {"cls":"com.example.chat.Main","user":0,"label":"no package"},
            {"pkg":"com.example.chat","cls":"com.example.chat.Main","user":-5,"label":"bad serial"},
            {"pkg":"com.example.notes","cls":"com.example.notes.Main","user":0,"label":"Notes"}
        ]}"""
        val state = VaultConcealment.decode(json.toByteArray(), "v")
        assertNotNull(state)
        assertEquals(
            "the two valid entries survive",
            setOf(
                "0:com.example.bank/com.example.bank.Main",
                "0:com.example.notes/com.example.notes.Main"
            ),
            state!!.keys
        )
    }

    @Test
    fun duplicatesCollapseToOneEntry() {
        val payload = VaultConcealment.encode("v", listOf(entry("com.example.bank"), entry("com.example.bank")))
        assertEquals(1, VaultConcealment.decode(payload, "v")!!.entries.size)
    }

    @Test
    fun aReadablePrimaryAlwaysWins() {
        val primary = VaultConcealment.State(entries = listOf(entry("com.example.new")), vaultId = "v")
        val backup = VaultConcealment.State(entries = listOf(entry("com.example.old")), vaultId = "v")
        assertEquals(primary.keys, VaultConcealment.reconcile(primary, backup).keys)
    }

    @Test
    fun anInterruptedWriteFallsBackToThePreviousGeneration() {
        // Primary torn and unreadable; the backup still describes the selection, so
        // the hidden apps stay hidden rather than reappearing.
        val backup = VaultConcealment.State(entries = listOf(entry("com.example.bank")), vaultId = "v")
        val reconciled = VaultConcealment.reconcile(primary = null, backup = backup)
        assertEquals(setOf("0:com.example.bank/com.example.bank.Main"), reconciled.keys)
        assertFalse("recovered is not the same as damaged", reconciled.degraded)
    }

    @Test
    fun bothGenerationsUnreadableIsDegradedNeverSilentlyEmpty() {
        val reconciled = VaultConcealment.reconcile(primary = null, backup = null)
        assertTrue("must be flagged as degraded", reconciled.degraded)
        assertFalse("and must not claim to be a first run", reconciled.fresh)
        assertTrue("no entries are invented", reconciled.isEmpty)
        assertTrue("the caller is told to repair it", VaultConcealment.needsRepair(reconciled))
    }

    @Test
    fun aFreshInstallIsEmptyButNotDegraded() {
        val fresh = VaultConcealment.State.FRESH
        assertTrue(fresh.fresh)
        assertFalse(fresh.degraded)
        assertFalse("a first run needs no repair", VaultConcealment.needsRepair(fresh))
    }

    @Test
    fun theMirrorFeedsTheLauncherInTheSameFormAsTheIndex() {
        // The drawer consumes VaultHiddenApp-shaped data from both sources, so the
        // mapping must not lose the profile serial or the label.
        val entries = listOf(
            VaultConcealment.Entry("com.example.bank", "com.example.bank.Main", 0L, "Bank"),
            VaultConcealment.Entry("com.example.work", "com.example.work.Main", 10L, "Work")
        )
        val apps = VaultConcealment.toHiddenApps(entries)
        assertEquals(2, apps.size)
        assertEquals(10L, apps.first { it.packageName == "com.example.work" }.userSerial)
        assertEquals("Bank", apps.first { it.packageName == "com.example.bank" }.label)
        assertEquals(entries.map { it.componentKey }.toSet(), apps.map { it.componentKey }.toSet())
    }

    @Test
    fun theSameAppSerialisedTwiceProducesIdenticalBytes() {
        // Stable output keeps an unchanged selection from rewriting the mirror and
        // keeps the tests deterministic.
        val entries = listOf(entry("b.example"), entry("a.example"))
        assertEquals(
            VaultConcealment.encode("v", entries).toList(),
            VaultConcealment.encode("v", entries.reversed()).toList()
        )
    }

    @Test
    fun invalidEntriesAreNeverWritten() {
        val junk = listOf(
            VaultConcealment.Entry("", "x.Main"),
            VaultConcealment.Entry("com.example.x", "")
        )
        val payload = VaultConcealment.encode("v", junk)
        assertTrue(VaultConcealment.decode(payload, "v")!!.isEmpty)
    }
}
