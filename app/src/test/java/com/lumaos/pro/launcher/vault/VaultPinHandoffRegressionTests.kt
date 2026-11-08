package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultScreenPresence
import com.lumaos.pro.launcher.vault.core.VaultSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level guards for the wiring that the policy tests cannot reach.
 *
 * [VaultPresenceTests] proves the *policy* is right. These checks prove the
 * screens and the lifecycle guard still use it in the way the policy assumes -
 * which is where the original defect actually lived, and where a well-meaning
 * refactor could quietly reintroduce it.
 */
class VaultPinHandoffRegressionTests {

    private val moduleDir: File by lazy { resolveModuleDir() }

    private fun source(relative: String): String =
        moduleDir.resolve("src/main/java/com/lumaos/pro/launcher/vault/$relative").readText()

    /**
     * The lock screen must not be finished by a lock it is itself showing.
     *
     * If it were, screen-off or the idle timeout would close the vault door and
     * leave nothing attached: the user would be dropped out of the app rather
     * than asked for a PIN.
     */
    @Test
    fun theLockScreenHandlesItsOwnLockInsteadOfClosing() {
        val lock = source("ui/VaultLockActivity.kt")
        assertTrue(
            "VaultLockActivity must override onVaultLocked",
            lock.contains("override fun onVaultLocked()")
        )
        val override = lock.substringAfter("override fun onVaultLocked()")
        val body = override.substringBefore("\n    override ")
        assertFalse(
            "the lock screen must never finish itself in response to a lock",
            body.contains("finish()")
        )
        assertFalse(
            "and must not ask for the lock screen again",
            body.contains("openLockScreen()")
        )
        assertTrue("it should go back to PIN entry", body.contains("Stage.UNLOCK_PIN"))
    }

    /** Content screens, by contrast, must still leave: they hold decrypted pixels. */
    @Test
    fun contentScreensStillLeaveWhenTheVaultLocks() {
        val base = source("ui/VaultScreenActivity.kt")
        val open = base.substringAfter("protected open fun onVaultLocked()")
        assertTrue("the base must start the lock screen", open.contains("openLockScreen()"))
        assertTrue("and then finish itself", open.contains("finish()"))
    }

    /** No Vault screen may call `finishAffinity`: it closes the launcher's task. */
    @Test
    fun noVaultScreenFinishesItsAffinity() {
        val dir = moduleDir.resolve("src/main/java/com/lumaos/pro/launcher/vault")
        val offenders = dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                file.readLines().any { line ->
                    val code = line.substringBefore("//")
                    code.contains("finishAffinity()") && !code.trimStart().startsWith("*")
                }
            }
            .map { it.name }
            .toList()
        assertTrue("finishAffinity() must not be called: $offenders", offenders.isEmpty())
    }

    /** The presence window must be measured on a monotonic clock. */
    @Test
    fun theHandoffWindowUsesAMonotonicClock() {
        val presence = source("core/VaultScreenPresence.kt")
        assertTrue(
            "the window must not be measured with wall-clock time",
            presence.contains("System::nanoTime")
        )
        assertFalse(
            "currentTimeMillis jumps and would collapse the window",
            presence.contains("System::currentTimeMillis")
        )
    }

    /**
     * The very sequence that used to lock a freshly unlocked vault, driven through
     * the real [VaultSession] with lock-on-leave on, on a monotonic clock.
     */
    @Test
    fun aCorrectPinSurvivesTheHandoffOnAMonotonicClock() {
        var elapsed = 1_000_000_000L
        val presence = VaultScreenPresence { elapsed }
        val window = VaultScreenPresence.HANDOFF_WINDOW_MS * 1_000_000L
        val session = VaultSession { elapsed / 1_000_000L }
        session.configure(com.lumaos.pro.launcher.vault.core.VaultAutoLock.THREE_MINUTES, lockOnLeave = true)

        session.unlock(VaultSession.Keys(com.lumaos.pro.launcher.vault.core.VaultCrypto.newKey(), "v"))
        presence.onScreenStarted()      // lock screen visible
        presence.onScreenStopped()      // PIN accepted, finish() queued
        elapsed += 16 * 1_000_000L      // one frame later...
        presence.onScreenStarted()      // ...the home screen attaches
        // The guard's settle callback fires inside the window and must be a no-op.
        if (presence.settle(elapsed + 16 * 1_000_000L)) session.conceal()
        assertTrue("a correct PIN must leave the vault open", session.isUnlocked)

        // A real departure still locks, so the feature was not disabled.
        presence.onScreenStopped()
        if (presence.settle(elapsed + window)) session.conceal()
        assertFalse("leaving for real must still lock", session.isUnlocked)
    }

    private fun resolveModuleDir(): File {
        (System.getenv("VAULT_REPO") ?: System.getProperty("lumaos.repo"))?.let { root ->
            val candidate = File(root, "app/src/main/AndroidManifest.xml")
            if (candidate.isFile) return File(root, "app")
        }
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        while (directory != null) {
            val candidate = File(directory, "src/main/AndroidManifest.xml")
            if (candidate.isFile) return directory
            val nested = File(directory, "app/src/main/AndroidManifest.xml")
            if (nested.isFile) return File(directory, "app")
            directory = directory.parentFile
        }
        error("Could not locate the app module from ${System.getProperty("user.dir")}")
    }
}
