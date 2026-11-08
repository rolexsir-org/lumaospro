package com.lumaos.pro.launcher.vault.core

import com.lumaos.pro.launcher.backup.MiniJson
import java.io.File

/**
 * Progressive throttling for repeated unlock failures.
 *
 * Two defences protect a stolen phone: the PIN is stretched with PBKDF2 (so each
 * guess is expensive), and this guard makes *rate* limited guessing impossible by
 * requiring the attacker to wait. The first attempts are free - typos happen - and
 * the delay then grows geometrically to a hard cap.
 *
 * ## Why the record is authenticated
 *
 * State has to live outside the encrypted index, in `guard.json`, because it must be
 * readable *before* the vault is unlocked. Left unauthenticated that created a real
 * bypass: anyone able to write to the app's private directory could reset the
 * counter between attempts, or simply delete the file, and the progressive delay
 * would never engage.
 *
 * Every record is therefore signed with a key held in the Android Keystore that the
 * app cannot export ([VaultGuardAuthenticator]). That changes the failure semantics
 * from "trust whatever is on disk" to:
 *
 *  - **valid tag** - the record is obeyed (after clamping);
 *  - **present but invalid, damaged or from another device** - treated as
 *    tampering: the counter is seeded above the free attempts, so editing the file
 *    can only make the attacker's life worse;
 *  - **absent while the signing key exists** - treated as *deletion*, and also
 *    seeded above the free attempts. This is the witness that makes [clear] and
 *    `rm guard.json` useless as a reset. Only [resetForNewVault] can establish a
 *    genuinely fresh state, and it requires the Keystore entry to be gone or
 *    explicitly re-initialised;
 *  - **absent with no signing key** - a first run. This is the only state that
 *    starts clean, and it can only be reached on a device where this vault has never
 *    failed an unlock.
 *
 * ## What this cannot do
 *
 * The Keystore provides no rollback protection for *file contents*: an attacker who
 * can read a previously valid `guard.json` and restore that byte-for-byte is
 * replaying a genuine record, and the HMAC will verify. Counter monotonicity would
 * need a hardware counter the platform does not offer to apps. Likewise, an attacker
 * who can execute code as this app - rooted device, or a compromised OS - can ask
 * the Keystore for the tag. Both limits are documented in
 * `docs/VAULT_THREAT_MODEL.md` rather than papered over.
 *
 * What deleting the record can never do is *open* the vault: the PIN still has to
 * unwrap the master key through PBKDF2 and AES-GCM. The real offline defence is the
 * iteration count and the entropy of the PIN, not this file.
 */
class VaultUnlockGuard(
    private val file: File,
    private val authenticator: VaultGuardAuthenticator? = null,
    /**
     * Binds records to this vault, so one vault's record cannot be replayed into
     * another. A provider rather than a value because the id is only known once a
     * key ring exists, and a vault can be created after this guard is constructed.
     */
    private val vaultId: () -> String? = { null }
) {

    data class State(
        val failures: Int = 0,
        val lockedUntilMs: Long = 0L,
        val lastFailureMs: Long = 0L,
        /** True when the record existed but had to be interpreted defensively. */
        val damaged: Boolean = false,
        /** True when the record was removed while the guard had already been used. */
        val deleted: Boolean = false,
        /** True when a present record failed its authentication tag. */
        val tampered: Boolean = false
    ) {

        fun remainingDelayMs(nowMs: Long): Long = (lockedUntilMs - nowMs).coerceAtLeast(0L)

        fun isThrottled(nowMs: Long): Boolean = lockedUntilMs > nowMs

        /** Any reason a caller should not treat this as a clean first run. */
        val suspicious: Boolean get() = damaged || deleted || tampered
    }

    /**
     * Current throttle state, clamped so that a forged record cannot trap the owner.
     *
     * Normally a read-only call, made while rendering the lock screen. It writes in
     * exactly one case: an authenticated vault whose record has been deleted, where
     * the deletion penalty must be persisted so that the next attempt is refused
     * rather than the file being re-examined for a free guess.
     *
     * All access is serialised on this instance. The lock screen reads from the main
     * thread and unlock runs on a background lane, so the alternative is two writers
     * racing over the same file - and the failure mode of that is a lost failure
     * count, which is the very thing this class exists to prevent.
     */
    @Synchronized
    fun state(nowMs: Long = System.currentTimeMillis()): State {
        if (!file.isFile) return missingState(nowMs)
        val bytes = runCatching { file.readBytes() }.getOrNull()
            ?: return suspendedState(nowMs, tampered = false)

        return when (val record = decode(bytes)) {
            is Decoded.Ok -> record.state.clamped(nowMs)
            // Well-formed but wrongly signed: someone edited a valid record.
            Decoded.Tampered -> suspendedState(nowMs, tampered = true)
            // Not parseable at all. That is indistinguishable from a write that was
            // cut short by a crash, so it is labelled damage rather than tampering -
            // the same penalty applies either way, but the UI must not accuse the
            // owner of an attack that may have been a power loss.
            Decoded.Malformed -> suspendedState(nowMs, tampered = false)
        }
    }

    /**
     * What an absent record means.
     *
     * With an authenticator present, the Keystore entry is a witness that this vault
     * has been used before. Deleting `guard.json` therefore cannot reset anything:
     * it is reported as a deletion and seeded above the free attempts, exactly like a
     * tampered record. Without one (never initialised), this is a genuine first run.
     */
    private fun missingState(nowMs: Long): State {
        if (!protected) return State()

        // The vault has been used before (the Keystore witness says so) but the
        // record is gone. That is a deletion, and it must not buy a free attempt:
        // imposing no delay here would let an attacker delete the file before every
        // guess and never wait at all.
        //
        // The penalty is therefore *written* immediately, so it is durable rather
        // than renewed on every read: the next read sees a valid record with a real
        // deadline and simply waits it out. Each further deletion re-anchors the same
        // fifteen minutes and still grants zero attempts. A successful unlock clears
        // it, so a legitimate owner who somehow lost the file waits once, not
        // forever.
        val penalty = State(
            failures = MAX_TRACKED_FAILURES,
            lockedUntilMs = nowMs + MAX_DELAY_MS,
            lastFailureMs = nowMs,
            deleted = true
        )
        write(penalty)
        return penalty
    }

    /**
     * State for a record that exists but cannot be trusted.
     *
     * The failure count is seeded above the free attempts so tampering never buys a
     * clean counter, and the file's own modification time anchors the short delay so
     * the penalty cannot renew itself forever. The countdown expires, the next
     * attempt is answered, and a successful unlock rewrites a valid record.
     *
     * `tampered` distinguishes "a valid-looking record was edited" from "the bytes
     * were not a record at all". Both are penalised identically; only the label
     * differs.
     */
    private fun suspendedState(nowMs: Long, tampered: Boolean): State {
        val failures = FREE_ATTEMPTS + 1
        val anchor = file.lastModified().takeIf { it > 0L } ?: nowMs
        return State(
            failures = failures,
            // Never longer than one penalty window from the anchor, so a hostile file
            // timestamp cannot hold the owner out indefinitely.
            lockedUntilMs = (anchor + delayForFailures(failures)).coerceAtMost(nowMs + MAX_DELAY_MS),
            lastFailureMs = anchor.coerceAtMost(nowMs),
            damaged = true,
            tampered = tampered
        )
    }

    private fun State.clamped(nowMs: Long): State = State(
        failures = failures.coerceIn(0, MAX_TRACKED_FAILURES),
        // A record may not hold the owner out for longer than the cap - a clock
        // rollback must not turn into a lockout either - and may not claim a
        // deadline or failure in the future beyond that window.
        lockedUntilMs = lockedUntilMs.coerceIn(0L, nowMs + MAX_DELAY_MS),
        lastFailureMs = lastFailureMs.coerceIn(0L, nowMs),
        damaged = false
    )

    /** Records a failed attempt and persists the new delay window. */
    @Synchronized
    fun recordFailure(nowMs: Long = System.currentTimeMillis()): State {
        // Read first, initialise second. Calling `ensureInitialised()` before this
        // would create the Keystore witness and make the very first failure look like
        // a deleted record.
        val previous = state(nowMs)
        val failures = (previous.failures + 1).coerceAtMost(MAX_TRACKED_FAILURES)
        val delay = delayForFailures(failures)
        val next = State(failures = failures, lockedUntilMs = nowMs + delay, lastFailureMs = nowMs)
        write(next)
        return next
    }

    /**
     * Clears the counter after a successful unlock.
     *
     * The zeroed record is written and signed rather than the file being deleted:
     * a deleted file is indistinguishable from an attacker removing it, and would be
     * reported as a deletion on the next read.
     */
    @Synchronized
    fun recordSuccess() {
        write(State())
    }

    /**
     * Establishes the clean state for a **new** vault.
     *
     * This is the only path that may reset the counter unconditionally, and it is
     * called exactly once when a vault is created or destroyed - never from the lock
     * screen. It exists so that "create a vault, delete it, create it again" is not
     * permanently penalised, while "delete `guard.json` and try 10 000 PINs" is.
     */
    @Synchronized
    fun resetForNewVault() {
        ensureInitialised()
        write(State())
    }

    /**
     * Removes the record and forgets that this guard was ever used.
     *
     * Only for the vault-destroyed path: after this, a fresh vault legitimately
     * starts clean. Deleting the file alone is *not* enough, which is the point.
     */
    @Synchronized
    fun clear() {
        if (file.isFile) file.delete()
    }

    // ------------------------------------------------------------------
    // Record format
    // ------------------------------------------------------------------

    private sealed class Decoded {
        class Ok(val state: State) : Decoded()
        /** Parsed, but the tag did not verify. */
        data object Tampered : Decoded()
        /** Not a record this build understands. */
        data object Malformed : Decoded()
    }

    /**
     * Decodes and verifies a record.
     *
     * With no authenticator configured the tag is not required - that is the
     * documented degraded mode for a build or device without a Keystore HMAC - but a
     * record that carries a tag which fails to verify is *always* rejected. A
     * verifier that silently ignores a bad signature is worse than no verifier.
     */
    private fun decode(bytes: ByteArray): Decoded {
        val root = runCatching {
            MiniJson.parse(String(bytes, Charsets.UTF_8)) as? Map<*, *>
        }.getOrNull() ?: return Decoded.Malformed

        val failures = (root["failures"] as? Number)?.toInt()
        val lockedUntil = (root["lockedUntil"] as? Number)?.toLong()
        val lastFailure = (root["lastFailure"] as? Number)?.toLong()
        if (failures == null || lockedUntil == null || lastFailure == null) return Decoded.Malformed
        // A negative count or timestamp is not a legitimate record; treat it as
        // damage rather than quietly reading it as "no failures yet".
        if (failures < 0 || failures > MAX_TRACKED_FAILURES) return Decoded.Malformed
        if (lockedUntil < 0L || lastFailure < 0L) return Decoded.Malformed

        val id = vaultId()
        val tag = (root["tag"] as? String)?.let { runCatching { VaultEncoding.fromBase64(it) }.getOrNull() }
        if (protected) {
            // The Keystore entry exists, so every record written from now on must be
            // tagged. An untagged record is one this build did not write.
            if (tag == null) return Decoded.Tampered
            val signed = signingPayload(id, failures, lockedUntil, lastFailure)
            val ok = runCatching { requireAuthenticator().verify(signed, tag) }.getOrDefault(false)
            if (!ok) return Decoded.Tampered
        } else if (tag != null) {
            // The record claims to be authenticated but this device has no usable
            // signing key to check it - e.g. it was restored from another phone.
            // Refusing is the only safe reading.
            return Decoded.Tampered
        }

        return Decoded.Ok(State(failures = failures, lockedUntilMs = lockedUntil, lastFailureMs = lastFailure))
    }

    /**
     * True when records are authenticated on this device.
     *
     * This is the switch between the two documented modes. It is *not* "an
     * authenticator was supplied": a device whose Keystore cannot create an HMAC
     * key would otherwise write untagged records and then read its own records as
     * tampered, locking the owner out progressively for a platform limitation. When
     * the key exists, protection is enforced; when it does not, the guard runs in the
     * old, documented degraded mode rather than inventing a failure.
     */
    private val protected: Boolean
        get() = runCatching { authenticator?.isInitialised() == true }.getOrDefault(false)

    /**
     * Creates the signing entry if this device can have one.
     *
     * Called before any write so that the *first* record is already protected -
     * otherwise the very first failure would be recorded untagged and read back as
     * tampering. Failure is contained: [protected] then reports false and the guard
     * runs in its documented degraded mode.
     */
    private fun ensureInitialised() {
        runCatching { authenticator?.ensureInitialised() }
    }

    private fun requireAuthenticator(): VaultGuardAuthenticator =
        authenticator ?: throw IllegalStateException("No authenticator")

    private fun write(state: State) {
        // Every write creates the signing entry first, so the first record this
        // vault ever stores is already authenticated.
        ensureInitialised()
        val id = vaultId()
        val payload = if (!protected) {
            emptyMap()
        } else {
            val tag = runCatching {
                requireAuthenticator()
                    .sign(signingPayload(id, state.failures, state.lockedUntilMs, state.lastFailureMs))
            }.getOrNull()
            if (tag == null) emptyMap() else linkedMapOf("tag" to VaultEncoding.base64(tag))
        }
        val root = linkedMapOf<String, Any?>(
            "failures" to state.failures,
            "lockedUntil" to state.lockedUntilMs,
            "lastFailure" to state.lastFailureMs
        )
        root.putAll(payload)
        runCatching {
            VaultFiles.writeBytesAtomically(file, MiniJson.write(root).toByteArray(Charsets.UTF_8))
        }
            .onFailure { com.lumaos.pro.launcher.core.util.LumaLog.e("Throttle record could not be written") }
    }

    /**
     * The exact bytes that are signed.
     *
     * Field-by-field and length-prefixed rather than "sign the whole JSON": a
     * record whose whitespace, key order or formatting was changed by a rewrite must
     * still verify, while any change to a *value* must not. The vault id is included
     * so a record cannot be moved between vaults, and the domain prefix keeps this
     * signature from being meaningful anywhere else.
     */
    private fun signingPayload(
        vaultId: String?,
        failures: Int,
        lockedUntilMs: Long,
        lastFailureMs: Long
    ): ByteArray {
        val id = vaultId ?: ""
        return buildString {
            append(DOMAIN)
            append('|')
            append(id.length).append(':').append(id)
            append('|').append(failures)
            append('|').append(lockedUntilMs)
            append('|').append(lastFailureMs)
        }.toByteArray(Charsets.UTF_8)
    }

    companion object {

        /** Attempts that are always free. */
        const val FREE_ATTEMPTS = 3

        /** Longest delay this guard will ever impose. */
        const val MAX_DELAY_MS: Long = 15 * 60_000L

        /** Failure counts above this are clamped; the cap is already in force. */
        const val MAX_TRACKED_FAILURES = 1_000

        private const val DOMAIN = "lumaos/vault/throttle/v2"

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
