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
 * ## What this file can and cannot do
 *
 * It cannot be made tamper-proof, and this implementation does not pretend
 * otherwise. It has to be readable *before* the vault is unlocked, which means any
 * key that could authenticate it would have to be available to whoever can read the
 * file. So:
 *
 *  - **Deleting it resets the local counter.** That is a real bypass of the delay
 *    and is documented in `docs/VAULT_THREAT_MODEL.md`.
 *  - **A damaged record is not treated as "no failures".** If the file exists but
 *    cannot be parsed, the next attempt starts from a small penalty rather than a
 *    clean slate, so corrupting the file gains nothing over leaving it alone.
 *  - **Absurd values are clamped.** A forged record can never impose a permanent
 *    lockout (the cap is fifteen minutes) or a negative/overflowing failure count.
 *
 * What deleting this file can never do is open the vault: the PIN still has to
 * unwrap the master key through PBKDF2 and AES-GCM. The real offline defence is the
 * iteration count and the entropy of the PIN, not this file.
 */
class VaultUnlockGuard(private val file: File) {

    data class State(
        val failures: Int = 0,
        val lockedUntilMs: Long = 0L,
        val lastFailureMs: Long = 0L,
        /** True when the record existed but had to be interpreted defensively. */
        val damaged: Boolean = false
    ) {

        fun remainingDelayMs(nowMs: Long): Long = (lockedUntilMs - nowMs).coerceAtLeast(0L)

        fun isThrottled(nowMs: Long): Boolean = lockedUntilMs > nowMs
    }

    /**
     * Current throttle state, clamped so that a forged file cannot trap the owner.
     *
     * Missing and unreadable are treated differently on purpose: a missing file is
     * indistinguishable from a first run (so it starts clean, and deleting the file
     * is a documented bypass), while a file that is present but malformed is
     * evidence of damage or tampering and starts from a penalty instead of zero.
     *
     * No disk write happens here: this is called while rendering the lock screen.
     */
    fun state(nowMs: Long = System.currentTimeMillis()): State {
        if (!file.isFile) return State()
        val parsed = runCatching {
            val root = MiniJson.parse(file.readText()) as? Map<*, *> ?: return@runCatching null
            val failures = (root["failures"] as? Number)?.toInt() ?: return@runCatching null
            val lockedUntil = (root["lockedUntil"] as? Number)?.toLong() ?: return@runCatching null
            val lastFailure = (root["lastFailure"] as? Number)?.toLong() ?: return@runCatching null
            // A negative count or timestamp is not a legitimate record; treat it as
            // damage rather than quietly reading it as "no failures yet".
            if (failures < 0 || failures > MAX_TRACKED_FAILURES) return@runCatching null
            if (lockedUntil < 0L || lastFailure < 0L) return@runCatching null
            State(failures = failures, lockedUntilMs = lockedUntil, lastFailureMs = lastFailure)
        }.getOrNull()

        if (parsed == null) return damagedState()
        return State(
            failures = parsed.failures,
            // A record may not hold the owner out for longer than the cap - a clock
            // rollback must not turn into a lockout either - and may not claim a
            // deadline or failure in the future beyond that window.
            lockedUntilMs = parsed.lockedUntilMs.coerceIn(0L, nowMs + MAX_DELAY_MS),
            lastFailureMs = parsed.lastFailureMs.coerceIn(0L, nowMs),
            damaged = false
        )
    }

    /**
     * State for a record that exists but cannot be read.
     *
     * The failure count is seeded above the free attempts so tampering never buys a
     * clean counter, and the file's own modification time anchors the short delay
     * so the penalty cannot renew itself forever. The countdown expires, the next
     * attempt is answered, and a successful unlock rewrites a valid record.
     */
    private fun damagedState(): State {
        val failures = FREE_ATTEMPTS + 1
        val anchor = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        return State(
            failures = failures,
            lockedUntilMs = anchor + delayForFailures(failures),
            lastFailureMs = anchor,
            damaged = true
        )
    }

    /** Records a failed attempt and persists the new delay window. */
    fun recordFailure(nowMs: Long = System.currentTimeMillis()): State {
        val previous = state(nowMs)
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

        /** Longest delay this guard will ever impose. */
        const val MAX_DELAY_MS: Long = 15 * 60_000L

        /** Failure counts above this are clamped; the cap is already in force. */
        const val MAX_TRACKED_FAILURES = 1_000

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
            else -> MAX_DELAY_MS
        }
    }
}
