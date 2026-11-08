package com.lumaos.pro.launcher.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression guards for the three critical Vault defects, at the wiring level.
 *
 * The policy for each is unit-tested elsewhere (`VaultConcealmentTests`,
 * `VaultPresenceTests`, `VaultSessionTests`); these checks pin the plumbing that
 * connects the policy to the app, which is where each of the three actually broke:
 *
 *  1. hidden apps came back after a reboot because nothing the launcher could read
 *     before authentication existed;
 *  2. a 400 ms timer decided whether the user had left the Vault;
 *  3. `guard.json` could be deleted to erase the retry delay.
 *
 * They are source assertions on purpose. All three defects compiled perfectly and
 * passed the behavioural tests of the day, so the guard has to be about *absence*:
 * no timer, no unguarded pre-unlock read, no path that resets the throttle except
 * vault creation.
 */
class VaultCriticalFixesTests {

    private val moduleDir: File by lazy { resolveModuleDir() }

    private fun source(relative: String): String =
        moduleDir.resolve("src/main/java/com/lumaos/pro/launcher/$relative").readText()

    // ------------------------------------------------------------------
    // Issue 1: hidden apps visible after a reboot
    // ------------------------------------------------------------------

    /**
     * The drawer must not depend on the encrypted index alone.
     *
     * `hiddenComponentKeys()` is empty while the vault is locked - by construction,
     * because the index needs the master key. If the drawer filters from that alone,
     * every hidden app reappears at boot. The mirror is the only pre-unlock source,
     * so this asserts the view-model actually uses it.
     */
    @Test
    fun theDrawerFallsBackToTheDeviceBoundMirrorBeforeUnlock() {
        val viewModel = source("launcher/LauncherViewModel.kt")
        assertTrue(
            "the drawer must read the pre-unlock mirror",
            viewModel.contains("concealedComponentKeys()")
        )
        assertTrue(
            "and must still prefer the authoritative index when unlocked",
            viewModel.contains("hiddenComponentKeys()")
        )
        // The order matters: unlocked first, mirror second.
        val unlockedBranch = viewModel.indexOf("if (core.vault.session.isUnlocked)")
        assertTrue("the choice must be explicit", unlockedBranch >= 0)
        assertTrue(
            "the index must be the unlocked branch",
            viewModel.indexOf("hiddenComponentKeys()") > unlockedBranch
        )
    }

    /** Every hide and every restore must republish the mirror, not just bump a counter. */
    @Test
    fun everyHiddenAppChangeRewritesTheMirror() {
        val manager = source("vault/platform/VaultManager.kt")
        val body = manager.substringAfter("fun hideApp(").substringBefore("fun unhideApp(")
        assertTrue("hide must republish", body.contains("publishHiddenApps()"))
        val unhide = manager.substringAfter("fun unhideApp(").substringBefore("fun importLegacyHiddenApps(")
        assertTrue("restore must republish", unhide.contains("publishHiddenApps()"))
        val migration = manager.substringAfter("fun importLegacyHiddenApps(").substringBefore("fun hiddenApps(")
        assertTrue("legacy import must republish", migration.contains("publishHiddenApps()"))
    }

    /** Unlocking rebuilds the mirror from the authoritative index. */
    @Test
    fun unlockingRebuildsTheMirrorFromTheIndex() {
        val manager = source("vault/platform/VaultManager.kt")
        val unlock = manager.substringAfter("fun completeUnlock(").substringBefore("fun lock(")
        assertTrue(
            "a stale or missing mirror must be repaired while the key is available",
            unlock.contains("mirrorHiddenApps()")
        )
    }

    /**
     * The pre-unlock index must be sealed, and must not be readable while the device
     * has never been unlocked since boot.
     *
     * `setUnlockedDeviceRequired(true)` on this key would be a *bug*: it would make
     * the mirror unreadable before the first unlock after a reboot, which is exactly
     * when it is needed.
     */
    @Test
    fun theMirrorIsSealedUnderAKeystoreKeyUsableAtBoot() {
        val store = source("vault/platform/VaultConcealmentStore.kt")
        assertTrue("must use the Android Keystore", store.contains("AndroidKeyStore"))
        assertTrue("must be AES-GCM", store.contains("AES/GCM/NoPadding"))
        assertFalse(
            "must not require first unlock, or the reboot fix silently does nothing",
            store.contains("setUnlockedDeviceRequired(true)")
        )
        assertFalse(
            "must not require authentication, or it cannot be read from onCreate",
            store.contains("setUserAuthenticationRequired(true)")
        )
        assertTrue("must bind the vault id", store.contains("updateAAD"))
    }

    // ------------------------------------------------------------------
    // Issue 2: the 400 ms hand-off heuristic
    // ------------------------------------------------------------------

    @Test
    fun noHandoffTimerRemainsAnywhereInTheVault() {
        val vault = moduleDir.resolve("src/main/java/com/lumaos/pro/launcher/vault")
        val offenders = vault.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val code = file.readLines()
                    .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
                    .joinToString("\n")
                code.contains("HANDOFF_WINDOW_MS") || code.contains("settleConceal")
            }
            .map { it.name }
            .toList()
        assertTrue("the hand-off window must be gone: $offenders", offenders.isEmpty())
    }

    /**
     * The conceal decision must be synchronous with the stop callback.
     *
     * A conceal posted through a `Handler` is a conceal that a transition can cancel
     * or delay - which is the shape of the bug this replaced.
     */
    @Test
    fun concealIsDecidedSynchronouslyNotScheduled() {
        val guard = source("vault/platform/VaultLifecycleGuard.kt")
        val stop = guard.substringAfter("override fun onActivityStopped(activity: Activity)")
            .substringBefore("override fun onActivityPaused")
        assertTrue("the stop handler must conceal", stop.contains("session.conceal()"))
        assertFalse("and must not post the decision", stop.contains("postDelayed"))
    }

    /** A configuration change must never be treated as leaving the app. */
    @Test
    fun aRecreatedActivityIsNotADeparture() {
        val guard = source("vault/platform/VaultLifecycleGuard.kt")
        assertTrue(
            "rotation must be exempt or lock-on-leave fires on every rotate",
            guard.contains("isChangingConfigurations")
        )
    }

    /** Lock-on-leave and the idle timeout must still be reachable from the guard. */
    @Test
    fun theOtherLockPathsAreUntouched() {
        val guard = source("vault/platform/VaultLifecycleGuard.kt")
        assertTrue("screen-off must still lock immediately", guard.contains("ACTION_SCREEN_OFF"))
        assertTrue("the idle ticker must still run", guard.contains("session.tick()"))
        val session = source("vault/core/VaultSession.kt")
        assertTrue("lock-on-leave policy lives in the session", session.contains("lockOnLeave"))
    }

    // ------------------------------------------------------------------
    // Issue 3: guard.json
    // ------------------------------------------------------------------

    /** The throttle record must be authenticated by a Keystore key. */
    @Test
    fun theThrottleRecordIsAuthenticatedByTheKeystore() {
        val signer = source("vault/platform/VaultGuardSigner.kt")
        assertTrue("must live in the Keystore", signer.contains("AndroidKeyStore"))
        assertTrue("must be an HMAC", signer.contains("HmacSHA256"))
        assertTrue(
            "must not export key bytes",
            signer.contains("key.encoded != null")
        )
        val manager = source("vault/platform/VaultManager.kt")
        assertTrue(
            "the manager must construct the guard with the signer",
            manager.contains("VaultUnlockGuard(paths.guard, guardSigner)")
        )
    }

    /**
     * Deleting the record must not reset the delay.
     *
     * The only code allowed to establish a clean throttle state is vault creation,
     * via `resetForNewVault()`. Anything else that reset it would reopen the bypass.
     */
    @Test
    fun onlyVaultCreationResetsTheThrottle() {
        val manager = source("vault/platform/VaultManager.kt")
        val resets = Regex("guard\\.(resetForNewVault|clear)\\(").findAll(manager).count()
        assertEquals("exactly one reset path", 1, resets)
        val create = manager.substringAfter("fun createVault(pin: CharArray")
            .substringBefore("fun createVault(pin: String")
        assertTrue("and it is the create path", create.contains("guard.resetForNewVault()"))
    }

    /** The lock screen must render the throttle state without ever writing it. */
    @Test
    fun theLockScreenNeverRewritesTheThrottleRecord() {
        val lock = source("vault/ui/VaultLockActivity.kt")
        assertFalse("rendering must not reset the counter", lock.contains("recordSuccess()"))
        assertFalse("nor clear it", lock.contains("resetForNewVault()"))
    }

    /**
     * A penalty must be visible, not only enforced.
     *
     * The record can now be detected as edited or removed; enforcing the delay
     * without saying so would leave the owner unable to tell a security event from a
     * malfunction. The Security Centre must render all four states.
     */
    @Test
    fun theSecurityScreenReportsTheRecordIntegrity() {
        val security = source("vault/ui/VaultSecurityActivity.kt")
        assertTrue("a section must exist", security.contains("addThrottleSection()"))
        for (state in listOf("tampered", "deleted", "damaged")) {
            assertTrue("state '$state' must be rendered", security.contains("state.$state"))
        }
        assertTrue(
            "and the vault must expose it",
            source("vault/platform/VaultManager.kt").contains("fun throttleState()")
        )
    }

    /** Tampering and deletion must be reported, never silently sanitised. */
    @Test
    fun tamperingAndDeletionAreDistinguishedFromAFirstRun() {
        val guard = source("vault/core/VaultUnlockGuard.kt")
        assertTrue("deletion is detectable", guard.contains("isInitialised()"))
        assertTrue("and reported", guard.contains("deleted ="))
        assertTrue("as is a bad signature", guard.contains("tampered ="))
        assertTrue("and a malformed record", guard.contains("damaged ="))
    }

    private companion object {
        fun assertEquals(message: String, expected: Int, actual: Int) =
            org.junit.Assert.assertEquals(message, expected.toLong(), actual.toLong())

        fun resolveModuleDir(): File {
            var directory: File? = File(System.getProperty("user.dir")).absoluteFile
            while (directory != null) {
                if (File(directory, "src/main/AndroidManifest.xml").isFile) return directory
                val nested = File(directory, "app/src/main/AndroidManifest.xml")
                if (nested.isFile) return File(directory, "app")
                directory = directory.parentFile
            }
            error("Could not locate the app module from " + System.getProperty("user.dir"))
        }
    }
}
