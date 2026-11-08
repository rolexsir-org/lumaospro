package com.lumaos.pro.launcher.vault.core

import com.lumaos.pro.launcher.backup.MiniJson
import java.io.File

/**
 * Progressive throttling for repeated unlock failures.
 *
 * Two defences protect a stolen phone: the PIN is stretched with PBKDF2 (so each
 * guess is expensive), and this guard makes *rate* limited guessing impossible
 * by requiring the attacker to wait. The first attempts are free - typos happen -
 * and the delay then grows geometrically to a hard cap.
 *
 * State lives in `guard.json` in the vault directory, outside the encrypted
 * index, because it must be readable *before* the vault is unlocked. It contains
 * no secrets: a count, two timestamps. Wiping it can therefore never take data
 * with it, and it is checked again after process death, so force-quitting the app
 * does not reset the delay.
 *
 * Limitation, stated honestly: a user who can change the system clock, or who
 * copies the vault directory to a powerful machine, is not slowed by this file.
 * Against offline attack the PBKDF2 iteration count and PIN entropy are the real
 * defences; against a person tapping at a lock screen, this is.
 */
class VaultUnlockGuard(private val file: File) {

    data class State(
        val failures: Int = 0,
        val lockedUntilMs: Long = 0L,
        val lastFailureMs: Long = 0L
    ) {

        fun remainingDelayMs(nowMs: Long): Long = (lockedUntilMs - nowMs).coerceAtLeast(0L)

        fun isThrottled(nowMs: Long): Boolean = lockedUntilMs > nowMs
    }

    fun state(): State {
        if (!file.isFile) return State()
        return try {
            val root = MiniJson.parse(file.readText()) as? Map<*, *> ?: return State()
            State(
                failures = (root["failures"] as? Number)?.toInt() ?: 0,
                lockedUntilMs = (root["lockedUntil"] as? Number)?.toLong() ?: 0L,
                lastFailureMs = (root["lastFailure"] as? Number)?.toLong() ?: 0L
            )
        } catch (e: Exception) {
            // An unreadable guard file must not lock the user out of their own
            // vault; it is simply treated as "no failures recorded".
            State()
        }
    }

    /** Records a failed attempt and persists the new delay window. */
    fun recordFailure(nowMs: Long = System.currentTimeMillis()): State {
        val previous = state()
        val failures = previous.failures + 1
        val delay = delayForFailures(failures)
        val next = State(failures = failures, lockedUntilMs = nowMs + delay, lastFailureMs = nowMs)
        write(next)
        return next
    }

    /** Clears the counter after a successful unlock. */
    fun recordSuccess() {
        if (file.isFile) VaultFiles.writeBytesAtomically(file, encode(State()))
    }

    fun clear() {
        if (file.isFile) file.delete()
    }

    private fun write(state: State) {
        VaultFiles.writeBytesAtomically(file, encode(state))
    }

    private fun encode(state: State): ByteArray = MiniJson.write(
        linkedMapOf(
            "failures" to state.failures,
            "lockedUntil" to state.lockedUntilMs,
            "lastFailure" to state.lastFailureMs
        )
    ).toByteArray(Charsets.UTF_8)

    companion object {

        /** Attempts that are always free. */
        const val FREE_ATTEMPTS = 3

        /**
         * Delay applied after the Nth consecutive failure.
         *
         * 1-3: none, 4: 10 s, 5: 30 s, 6: 60 s, 7: 5 min, 8+: 15 min (cap).
         */
        fun delayForFailures(failures: Int): Long = when {
            failures <= FREE_ATTEMPTS -> 0L
            failures == 4 -> 10_000L
            failures == 5 -> 30_000L
            failures == 6 -> 60_000L
            failures == 7 -> 5 * 60_000L
            else -> 15 * 60_000L
        }
    }
}
