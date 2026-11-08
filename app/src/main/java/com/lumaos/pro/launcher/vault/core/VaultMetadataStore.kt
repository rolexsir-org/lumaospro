package com.lumaos.pro.launcher.vault.core

import com.lumaos.pro.launcher.backup.MiniJson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

/**
 * The vault's encrypted metadata index (`meta.bin`).
 *
 * ## Why a single encrypted document instead of SQLite
 *
 * LumaOS Pro has no database module beyond a diagnostics store; adding SQLCipher
 * would pull a native library into a launcher whose entire premise is being
 * small, and a plaintext-schema Room database would leak exactly what Vault must
 * not leak (names, sizes, counts of private items). The index is therefore one
 * JSON document sealed with AES-256-GCM: every field, including file names and
 * folder names, is inside the ciphertext, and any modification to the file is
 * detected before a single field is trusted.
 *
 * Commits are atomic (temp file, `fsync`, rename) and keep the previous
 * generation as `meta.bin.bak`, so an interrupted write or a power loss falls
 * back to the last known-good index instead of losing the vault's contents.
 *
 * ## File format
 *
 * ```
 * "LVM1" 4 bytes | version 1 byte | saltLen 1 byte | salt 32 bytes
 *               | iv 12 bytes | AES-256-GCM(JSON index)
 * ```
 */
class VaultMetadataStore(private val paths: VaultPaths, private val metadataKey: ByteArray) {

    fun exists(): Boolean = paths.metadata.isFile

    /**
     * Zeroizes the derived index key.
     *
     * The master key lives in `VaultSession` and is destroyed when the vault locks;
     * this key is a *separate* HKDF output, so it needs its own destruction or it
     * would sit in the heap after the session key is gone. The store is unusable
     * afterwards - every read after this call fails authentication, which is the
     * intended behaviour for a locked vault.
     */
    fun destroy() {
        VaultCrypto.zeroize(metadataKey)
    }

    /**
     * Reads and decrypts the index.
     *
     * Returns null when no vault index exists yet. Throws [VaultCryptoException]
     * when the index cannot be authenticated, including the case where *both*
     * copies are unreadable.
     */
    fun read(vaultId: String? = null): VaultIndex? {
        if (!paths.metadata.isFile && !paths.metadataBackup.isFile) return null
        var primaryFailure: Exception? = null
        if (paths.metadata.isFile) {
            try {
                return decrypt(paths.metadata, vaultId)
            } catch (e: Exception) {
                primaryFailure = e
            }
        }
        if (paths.metadataBackup.isFile) {
            try {
                return decrypt(paths.metadataBackup, vaultId)
            } catch (e: Exception) {
                primaryFailure?.addSuppressed(e)
            }
        }
        throw primaryFailure ?: VaultCryptoException("Vault index could not be read")
    }

    /**
     * Encrypts and commits [index], bumping its generation counter.
     *
     * Returns the committed index (with the new generation and timestamp) so the
     * caller's in-memory copy always matches what is on disk.
     */
    fun commit(index: VaultIndex): VaultIndex {
        paths.ensureDirectories()
        val next = index.copy(generation = index.generation + 1, updatedAtMs = System.currentTimeMillis())
        val plaintext = MiniJson.write(next.toMap()).toByteArray(Charsets.UTF_8)
        val blob = try {
            encrypt(plaintext, next.vaultId)
        } finally {
            // The plaintext index is the most sensitive buffer in the app.
            VaultCrypto.zeroize(plaintext)
        }
        VaultFiles.writeAtomically(paths.metadata) { it.write(blob) }
        return next
    }

    /**
     * Commits [index] and then removes every *previous* on-disk copy that still
     * named the items that were just deleted.
     *
     * This is the cryptographic half of a deletion. Removing a payload file is not
     * enough on its own: the index holds that object's wrapped data key, and the
     * `.bak` generation kept by [commit] holds the previous index verbatim. Anyone
     * who later recovers the old ciphertext blocks *and* the stale index could
     * re-derive the data key - so a deletion commits the new index and then
     * overwrites the backup generation and the leftover temp file with it.
     *
     * The rewrite is idempotent: if the process dies halfway, the next unlock's
     * [read] still sees a valid index (primary first, backup second) and the next
     * deletion or commit finishes the job. Deleting twice is therefore safe.
     */
    fun commitAndScrubPrevious(index: VaultIndex): VaultIndex {
        val committed = commit(index)
        scrubPreviousGeneration(committed)
        return committed
    }

    /**
     * Overwrites the backup generation and any leftover temp file with the current
     * (already committed) index. Called after a deletion.
     */
    private fun scrubPreviousGeneration(index: VaultIndex) {
        val plaintext = MiniJson.write(index.toMap()).toByteArray(Charsets.UTF_8)
        val blob = try {
            encrypt(plaintext, index.vaultId)
        } finally {
            VaultCrypto.zeroize(plaintext)
        }
        runCatching { VaultFiles.writeReplacingNoBackup(paths.metadataBackup, blob) }
        // A temp file from an interrupted commit can hold the pre-deletion index.
        runCatching { VaultFiles.shred(File(paths.metadata.path + ".tmp")) }
        // And so can a snapshot-of-a-snapshot left by an older build.
        runCatching { VaultFiles.shred(File(paths.metadataBackup.path + ".bak")) }
    }

    /** Deletes both copies; used only by "delete vault". */
    fun deleteAll() {
        paths.metadata.delete()
        paths.metadataBackup.delete()
    }

    private fun encrypt(plaintext: ByteArray, vaultId: String): ByteArray {
        val salt = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES)
        val key = deriveKey(salt)
        return try {
            val sealed = VaultCrypto.seal(key, plaintext, aad(vaultId))
            ByteArrayOutputStream(plaintext.size + 64).use { out ->
                out.write(MAGIC.toByteArray(Charsets.US_ASCII))
                out.write(VERSION)
                out.write(salt.size)
                out.write(salt)
                out.write(sealed.iv)
                out.write(sealed.ciphertext)
                out.toByteArray()
            }
        } finally {
            VaultCrypto.zeroize(key)
        }
    }

    private fun decrypt(file: File, vaultId: String?): VaultIndex {
        val bytes = file.readBytes()
        if (bytes.size < 4 + 1 + 1 + VaultCrypto.SALT_BYTES + VaultCrypto.GCM_IV_BYTES + VaultCrypto.GCM_TAG_BYTES) {
            throw VaultCryptoException("Vault index is truncated")
        }
        if (String(bytes, 0, 4, Charsets.US_ASCII) != MAGIC) throw VaultCryptoException("Not a vault index")
        val version = bytes[4].toInt() and 0xFF
        if (version != VERSION) throw VaultCryptoException("Unsupported vault index version $version")
        val saltLength = bytes[5].toInt() and 0xFF
        var offset = 6
        if (saltLength != VaultCrypto.SALT_BYTES || bytes.size < offset + saltLength) {
            throw VaultCryptoException("Vault index salt is malformed")
        }
        val salt = bytes.copyOfRange(offset, offset + saltLength)
        offset += saltLength
        val iv = bytes.copyOfRange(offset, offset + VaultCrypto.GCM_IV_BYTES)
        offset += VaultCrypto.GCM_IV_BYTES
        val ciphertext = bytes.copyOfRange(offset, bytes.size)

        val key = deriveKey(salt)
        val plaintext = try {
            VaultCrypto.open(key, iv, ciphertext, aad(vaultId ?: ""))
        } finally {
            VaultCrypto.zeroize(key)
        }
        try {
            val root = MiniJson.parse(String(plaintext, Charsets.UTF_8)) as? Map<*, *>
                ?: throw VaultCryptoException("Vault index is not an object")
            return VaultIndex.fromMap(root, vaultId)
        } catch (e: VaultCryptoException) {
            throw e
        } catch (e: Exception) {
            throw VaultCryptoException("Vault index is unreadable", e)
        } finally {
            VaultCrypto.zeroize(plaintext)
        }
    }

    private fun deriveKey(salt: ByteArray): ByteArray =
        VaultCrypto.hkdf(metadataKey, salt, "lumaos/vault/metadata-file/v1")

    private fun aad(vaultId: String): ByteArray = "lumaos/vault/metadata-aad/v1|$vaultId".toByteArray(Charsets.UTF_8)

    companion object {
        private const val MAGIC = "LVM1"
        private const val VERSION = 1

        /** Metadata key for a vault master key. */
        fun keyFor(masterKey: ByteArray): ByteArray = VaultKeyring.metadataKey(masterKey)
    }
}
