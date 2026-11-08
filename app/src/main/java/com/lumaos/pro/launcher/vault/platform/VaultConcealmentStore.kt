package com.lumaos.pro.launcher.vault.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.lumaos.pro.launcher.backup.MiniJson
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.vault.core.VaultConcealment
import com.lumaos.pro.launcher.vault.core.VaultCrypto
import com.lumaos.pro.launcher.vault.core.VaultEncoding
import com.lumaos.pro.launcher.vault.core.VaultFiles
import com.lumaos.pro.launcher.vault.core.VaultPaths
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The device-bound, Keystore-sealed mirror of the Vault's hidden-app list.
 *
 * The launcher has to know what to hide *before* the user authenticates - after a
 * reboot there is no master key, so the encrypted Vault index cannot be read and
 * every hidden app would be back in the drawer. This store keeps a second, small
 * copy sealed under a key that lives in the Android Keystore and never leaves it.
 *
 * ## Key requirements, and why each one is set
 *
 *  - AES-256-GCM, no user-authentication flag: the launcher must read this in
 *    `onCreate`, before any prompt. Requiring authentication would recreate the
 *    original bug in a different place.
 *  - **No `setUnlockedDeviceRequired`.** The whole point is that the drawer is
 *    filtered immediately after a reboot, while the device has not been unlocked
 *    since boot. A `SET_UNLOCKED_DEVICE_REQUIRED` key is unusable until first
 *    unlock, which would leave hidden apps visible on the lock screen's drawer.
 *  - A random IV per write, stored beside the ciphertext, and the vault id bound
 *    in as associated data, so a mirror copied in from another vault - or edited
 *    byte-for-byte - fails authentication instead of hiding the wrong apps.
 *
 * ## Failure handling
 *
 * Every failure mode has a defined answer, and none of them is "pretend nothing is
 * hidden":
 *
 *  - **missing** file → [VaultConcealment.State.FRESH] (a vault that never hid
 *    anything, or one that has not written a mirror yet);
 *  - **corrupted / truncated / wrong key / wrong vault** → read the backup
 *    generation, and if that also fails, [VaultConcealment.State.DEGRADED];
 *  - **interrupted write** → `writeBytesAtomically` renames over the target, so a
 *    reader sees either the old file or the new one; the backup covers the case
 *    where the rename itself was torn;
 *  - **Keystore key gone** (device restored, key invalidated) → DEGRADED, and the
 *    next unlock re-seals a fresh mirror from the authoritative Vault index.
 *
 * A degraded mirror is repaired, never trusted as empty.
 */
class VaultConcealmentStore(
    private val context: Context,
    private val paths: VaultPaths
) {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /** Where the sealed blob lives. Exposed for the tests and for diagnostics. */
    fun file(): File = File(paths.root, VaultConcealment.FILE_NAME)

    private fun backupFile(): File = File(paths.root, VaultConcealment.BACKUP_FILE_NAME)

    /**
     * Reads the mirror. Never throws and never blocks on the UI thread for long:
     * this is a single small file plus one Keystore unwrap.
     *
     * Must be safe to call before the vault exists at all - the launcher calls it
     * from `onCreate`.
     */
    fun load(expectedVaultId: String? = null): VaultConcealment.State {
        val primary = readGeneration(file(), expectedVaultId)
        if (primary != null) return primary
        val backup = readGeneration(backupFile(), expectedVaultId)
        if (backup != null) {
            LumaLog.w("Vault concealment mirror: recovered from the previous generation")
            return backup
        }
        // Neither generation could be opened. Distinguish "never written" from
        // "written and broken": only the latter is damage worth reporting.
        val everWritten = file().exists() || backupFile().exists()
        return if (everWritten) {
            LumaLog.e("Vault concealment mirror is unreadable; it will be rebuilt on the next unlock")
            VaultConcealment.State.DEGRADED
        } else {
            VaultConcealment.State.FRESH
        }
    }

    /**
     * Replaces the mirror with [entries].
     *
     * Called on every hide, every restore, and after every unlock. Returns false on
     * failure so the caller can decide whether that matters; a failed mirror write
     * must never be reported as a successful hide (the Vault index already has it,
     * so the hide itself stands - only the pre-unlock view is stale, and the next
     * unlock repairs it).
     */
    fun write(vaultId: String, entries: Collection<VaultConcealment.Entry>): Boolean {
        return try {
            val payload = VaultConcealment.encode(vaultId, entries)
            val sealed = seal(vaultId, payload)
            VaultCrypto.zeroize(payload)
            paths.ensureDirectories()
            VaultFiles.writeBytesAtomically(file(), sealed)
            true
        } catch (e: Exception) {
            LumaLog.e("Vault concealment mirror could not be written: ${e.javaClass.simpleName}")
            false
        }
    }

    /** Shreds both generations. Only used when the vault itself is being destroyed. */
    fun clear() {
        runCatching { VaultFiles.shred(file()) }
        runCatching { VaultFiles.shred(backupFile()) }
    }

    // ------------------------------------------------------------------
    // Sealing
    // ------------------------------------------------------------------

    /**
     * Sealed blob layout: `[1-byte version][12-byte IV][ciphertext+GCM tag]`.
     *
     * A fixed header rather than JSON because the ciphertext is binary; the version
     * byte is what lets a future format change be detected instead of misparsed.
     */
    private fun seal(vaultId: String, payload: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
        cipher.updateAAD(aad(vaultId))
        val body = cipher.doFinal(payload)
        // The layout has a fixed IV field, so a provider that chose a different IV
        // length would write a blob this class then misparses. Refuse instead.
        check(cipher.iv.size == IV_BYTES) { "Unexpected GCM IV length" }
        return ByteArray(1 + cipher.iv.size + body.size).also { out ->
            out[0] = FORMAT_VERSION
            cipher.iv.copyInto(out, 1)
            body.copyInto(out, 1 + cipher.iv.size)
        }
    }

    private fun unseal(vaultId: String, blob: ByteArray): ByteArray {
        if (blob.size < 1 + IV_BYTES + TAG_BYTES) throw VaultConcealmentFormatException("too short")
        if (blob[0] != FORMAT_VERSION) throw VaultConcealmentFormatException("unknown format")
        val iv = blob.copyOfRange(1, 1 + IV_BYTES)
        val body = blob.copyOfRange(1 + IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, loadOrCreateKey(), GCMParameterSpec(TAG_BYTES * 8, iv))
        cipher.updateAAD(aad(vaultId))
        return cipher.doFinal(body)
    }

    private fun readGeneration(file: File, expectedVaultId: String?): VaultConcealment.State? {
        if (!file.isFile) return null
        return try {
            val blob = file.readBytes()
            // The vault id is not stored in the clear; it is inside the sealed
            // payload and also bound as AAD. To open the file we need the id, and
            // after a reboot the launcher has only the key ring's copy of it.
            val vaultId = expectedVaultId ?: readVaultIdFromKeyRing() ?: return null
            val plain = unseal(vaultId, blob)
            try {
                VaultConcealment.decode(plain, expectedVaultId ?: vaultId)
            } finally {
                VaultCrypto.zeroize(plain, blob)
            }
        } catch (e: VaultConcealmentFormatException) {
            LumaLog.e("Vault concealment mirror is damaged: ${e.message}")
            null
        } catch (e: Exception) {
            // Bad tag, wrong key, truncated body: all mean "cannot be trusted".
            LumaLog.e("Vault concealment mirror could not be opened: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * The vault id, read from the key ring.
     *
     * `keyring.json` holds only wrapped key material, salts and the id - it is
     * readable before unlock by design (the PIN has to be verified against it).
     */
    private fun readVaultIdFromKeyRing(): String? = runCatching {
        if (!paths.keyRing.isFile) return null
        val root = MiniJson.parse(paths.keyRing.readText()) as? Map<*, *> ?: return null
        (root["vaultId"] as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun loadOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // No user-authentication requirement and deliberately NOT
                // setUnlockedDeviceRequired: see the class comment. This key protects
                // which apps are hidden from other apps and from a stolen disk image;
                // it is not a secret from this app's own process on this device.
                .build()
        )
        val key = generator.generateKey()
        LumaLog.i("Vault concealment key created in the Android Keystore")
        return key
    }

    /** Destroys the Keystore key. The mirror becomes unreadable on purpose. */
    fun deleteKey() {
        runCatching {
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun aad(vaultId: String): ByteArray =
        "$AAD_PREFIX|$vaultId".toByteArray(Charsets.UTF_8)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORM = "AES/GCM/NoPadding"

        /**
         * Keystore alias. Deliberately *not* per-vault: there is one vault per
         * install, and a single alias keeps the key alive across a vault restore
         * where the id changes. The id is bound as AAD instead.
         */
        private const val KEY_ALIAS = "lumaos.vault.conceal"

        private const val AAD_PREFIX = "lumaos/vault/conceal/v1"
        private const val FORMAT_VERSION: Byte = 1
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16

        /** Exposed for diagnostics: the current sealed blob size, or -1. */
        fun describe(paths: VaultPaths): String {
            val file = File(paths.root, VaultConcealment.FILE_NAME)
            return if (file.isFile) "${file.length()} bytes sealed" else "absent"
        }

        /** Encoded blob length, for tests that assert the format is compact. */
        fun encodedLength(entries: Int): Int = 1 + IV_BYTES + TAG_BYTES + 64 + entries * 64
    }
}

/** The mirror exists but is not a shape this build understands. */
internal class VaultConcealmentFormatException(message: String) : Exception(message)
