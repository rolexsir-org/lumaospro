package com.lumaos.pro.launcher.vault.core

import com.lumaos.pro.launcher.backup.MiniJson
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Encrypted Vault backup: a single self-contained file that can be written to any
 * Storage Access Framework destination (a local folder, a USB stick, or the
 * user's own cloud drive app) and restored on this or another device.
 *
 * ## What is inside
 *
 * Only ciphertext, plus the minimum needed to find a key again:
 *
 * ```
 * "LVBK" | version | flags | saltLen | salt(32)
 * headerLen | header JSON  <- vault id, KDF params, recovery-wrapped master key,
 *                          item count, byte count, creation time. No names.
 * bodyLen   | body records <- the encrypted metadata index blob and every
 *                          encrypted object payload, byte for byte
 * sha256(everything above) <- integrity trailer
 * ```
 *
 * The body is not encrypted *again*: its parts are already AES-256-GCM
 * ciphertext under keys the backup never contains. The only key material in the
 * file is the vault master key **wrapped under the recovery key**, which is why a
 * backup is useless without the recovery key that the user wrote down, and why
 * the backup destination never needs to be trusted.
 *
 * ## Why the recovery wrapper and not the PIN wrapper
 *
 * A backup has to outlive the phone. A PIN is short, re-established on a new
 * device, and tied to a user who may forget it; the recovery key is independent
 * of the device, of biometrics and of the PIN. Restoring therefore asks for the
 * recovery key, then establishes a new PIN locally. The master key - and with it
 * every object - stays exactly the same, so nothing needs re-encrypting.
 */
object VaultBackup {

    const val MAGIC = "LVBK"
    const val VERSION = 1
    const val FORMAT_JSON = 1

    private const val RECORD_METADATA: Int = 1
    private const val RECORD_OBJECT: Int = 2

    /** Anything smaller than header + trailer is not a backup. */
    private const val MIN_CONTAINER_BYTES = 4 + 1 + 1 + 1 + 32 + 4 + 8 + 32

    class Restored(
        val vaultId: String,
        val createdAtMs: Long,
        val itemCount: Int,
        val totalBytes: Long
    )

    class Info(
        val vaultId: String,
        val createdAtMs: Long,
        val itemCount: Int,
        val totalBytes: Long,
        val recoveryWrapper: VaultWrappedKey
    )

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    /**
     * Writes a complete backup of [paths] to [out].
     *
     * Counts and sizes are computed *before* the header is written, and every
     * object the index references must be present: a backup that silently skips
     * a file would report success while leaving the user unprotected, so a
     * missing payload aborts the whole operation instead.
     */
    fun write(
        paths: VaultPaths,
        vaultId: String,
        recoveryWrapper: VaultWrappedKey,
        out: OutputStream,
        /** Every object the index references; a missing payload aborts the backup. */
        requiredObjectIds: Set<String> = emptySet(),
        nowMs: Long = System.currentTimeMillis(),
        onProgress: (writtenBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        val counter = CountingDigestOutputStream(out, digest)

        val indexBlob = FileInputStream(paths.metadata).use { readExactly(it, paths.metadata.length()) }
        val objectFiles = paths.objects.listFiles()?.sortedBy { it.name }.orEmpty()
        val presentIds = objectFiles.map { it.name.removeSuffix(".obj") }.toSet()
        val missing = requiredObjectIds - presentIds
        if (missing.isNotEmpty()) {
            // Reporting success here would leave the user believing they are
            // protected while part of their vault is not in the file.
            throw IOException("Backup is incomplete: ${missing.size} object(s) are missing")
        }
        // Record framing is counted up front so the reader can detect a truncated
        // body: 1 byte type + 4 bytes id length + id + 8 bytes payload length.
        var bodyBytes = 1L + 4L + 8L + indexBlob.size
        for (file in objectFiles) {
            bodyBytes += 1L + 4L + file.name.removeSuffix(".obj").toByteArray(Charsets.UTF_8).size + 8L + file.length()
        }
        val headerJson = MiniJson.write(
            linkedMapOf(
                "format" to FORMAT_JSON,
                "vaultId" to vaultId,
                "createdAtMs" to nowMs,
                "kdf" to recoveryWrapper.scheme,
                "recoveryWrapper" to recoveryWrapper.toMap(),
                "itemCount" to objectFiles.size,
                "totalBytes" to bodyBytes
            )
        ).toByteArray(Charsets.UTF_8)

        val salt = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES)
        counter.write(MAGIC.toByteArray(Charsets.US_ASCII))
        counter.write(VERSION)
        counter.write(0) // flags, reserved
        counter.write(salt.size)
        counter.write(salt)
        writeInt(counter, headerJson.size)
        counter.write(headerJson)
        writeLong(counter, bodyBytes)

        // The body: metadata index first, then every payload verbatim.
        counter.write(RECORD_METADATA)
        writeInt(counter, 0)
        writeLong(counter, indexBlob.size.toLong())
        counter.write(indexBlob)

        for (file in objectFiles) {
            val id = file.name.removeSuffix(".obj")
            val idBytes = id.toByteArray(Charsets.UTF_8)
            counter.write(RECORD_OBJECT)
            writeInt(counter, idBytes.size)
            counter.write(idBytes)
            writeLong(counter, file.length())
            FileInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024)
                var remaining = file.length()
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) throw IOException("Object $id ended early")
                    counter.write(buffer, 0, read)
                    remaining -= read
                    onProgress(counter.count, bodyBytes)
                }
            }
        }
        out.write(digest.digest())
        out.flush()
        return counter.count + 32
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /**
     * Reads only the header, so the UI can show what a backup contains and which
     * vault it belongs to before the user is asked for a recovery key.
     */
    fun inspect(input: InputStream): Info {
        val stream = input.buffered()
        val magic = ByteArray(4)
        if (stream.read(magic) != 4) throw VaultCryptoException("Not a vault backup")
        if (String(magic, Charsets.US_ASCII) != MAGIC) throw VaultCryptoException("Not a vault backup")
        val version = stream.read()
        if (version != VERSION) throw VaultCryptoException("Unsupported backup version $version")
        stream.read() // flags
        val saltLength = stream.read()
        if (saltLength != VaultCrypto.SALT_BYTES) throw VaultCryptoException("Backup salt is malformed")
        readExactly(stream, saltLength.toLong())
        val headerLength = readInt(stream)
        if (headerLength <= 0 || headerLength > 64 * 1024) throw VaultCryptoException("Backup header is malformed")
        val headerJson = String(readExactly(stream, headerLength.toLong()), Charsets.UTF_8)
        val root = MiniJson.parse(headerJson) as? Map<*, *> ?: throw VaultCryptoException("Backup header is not an object")
        val format = (root["format"] as? Number)?.toInt() ?: 0
        if (format != FORMAT_JSON) throw VaultCryptoException("Unsupported backup format $format")
        val vaultId = root["vaultId"] as? String ?: throw VaultCryptoException("Backup has no vault id")
        val wrapper = VaultWrappedKey.fromMap(root["recoveryWrapper"])
            ?: throw VaultCryptoException("Backup has no recovery key wrapper")
        return Info(
            vaultId = vaultId,
            createdAtMs = (root["createdAtMs"] as? Number)?.toLong() ?: 0L,
            itemCount = (root["itemCount"] as? Number)?.toInt() ?: 0,
            totalBytes = (root["totalBytes"] as? Number)?.toLong() ?: 0L,
            recoveryWrapper = wrapper
        )
    }

    /**
     * Restores a backup into [target], which must be an empty directory.
     *
     * Order matters: the master key is unwrapped and *proven* (by decrypting the
     * metadata index) before anything is written, then every record is written
     * and the trailer checksum is verified. Callers stage this into a temporary
     * directory and only swap it into place after it returns successfully.
     */
    fun restore(
        input: InputStream,
        target: VaultPaths,
        recoverySecret: ByteArray,
        onProgress: (writtenBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Restored {
        // A SAF stream cannot be rewound, so the header is parsed once, here, and
        // its exact bytes are fed into the integrity digest.
        val stream = input
        val header = readHeader(stream)
        val masterKey = try {
            VaultKeyring.unwrapWithRecovery(header.info.recoveryWrapper, recoverySecret, header.info.vaultId)
        } catch (e: VaultCryptoException) {
            throw VaultCryptoException("This recovery key does not open this backup", e)
        }

        target.ensureDirectories()
        val digest = MessageDigest.getInstance("SHA-256")
        val counting = CountingInputStream(stream, digest)
        // Header bytes are already consumed; hash them for the trailer check.
        digest.update(header.rawBytes)

        var indexWritten: File? = null
        var objectCount = 0
        var written = 0L
        try {
            var remaining = header.bodyBytes
            while (remaining > 0) {
                val type = counting.read()
                if (type < 0) throw EOFException("Backup ended early")
                val idLength = readInt(counting)
                val id = if (idLength > 0) String(readExactly(counting, idLength.toLong()), Charsets.UTF_8) else ""
                val length = readLong(counting)
                if (length < 0 || length > remaining) throw VaultCryptoException("Backup record length is invalid")
                remaining -= 1 + 4 + idLength + 8 + length
                when (type) {
                    RECORD_METADATA -> {
                        val blob = readExactly(counting, length)
                        indexWritten = target.metadata
                        VaultFiles.writeBytesAtomically(target.metadata, blob)
                        written += length
                    }
                    RECORD_OBJECT -> {
                        if (id.isEmpty() || !id.all { it.isLetterOrDigit() }) {
                            throw VaultCryptoException("Backup object id is invalid")
                        }
                        val file = target.objectFile(id)
                        FileOutputStream(file).use { out ->
                            var left = length
                            val buffer = ByteArray(64 * 1024)
                            while (left > 0) {
                                val read = counting.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                                if (read < 0) throw EOFException("Backup ended inside an object")
                                out.write(buffer, 0, read)
                                left -= read
                                onProgress(counting.count, header.bodyBytes)
                            }
                            out.flush()
                            out.fd.sync()
                        }
                        objectCount++
                        written += length
                    }
                    else -> throw VaultCryptoException("Unknown backup record type $type")
                }
            }
            if (remaining != 0L) throw VaultCryptoException("Backup body length mismatch")
            val expected = readExactly(stream, 32)
            val actual = digest.digest()
            if (!VaultCrypto.constantTimeEquals(expected, actual)) {
                throw VaultCryptoException("Backup integrity check failed")
            }
            if (indexWritten == null) throw VaultCryptoException("Backup contains no metadata index")

            // Prove the unwrapped master key really opens this backup's index.
            val metadataKey = VaultMetadataStore.keyFor(masterKey)
            val index = VaultMetadataStore(target, metadataKey).read(header.info.vaultId)
                ?: throw VaultCryptoException("Backup index is empty")
            if (index.vaultId != header.info.vaultId) throw VaultCryptoException("Backup vault id mismatch")

            // The key ring for a restored vault starts with the recovery wrapper
            // only: the PIN and biometric wrappers are created on this device.
            val keyFile = VaultKeyFile(
                vaultId = header.info.vaultId,
                createdAtMs = header.info.createdAtMs.takeIf { it > 0 } ?: System.currentTimeMillis(),
                pin = null,
                recovery = header.info.recoveryWrapper,
                biometric = null
            )
            VaultFiles.writeBytesAtomically(
                target.keyRing,
                VaultKeyring.toJson(keyFile).toByteArray(Charsets.UTF_8)
            )
            VaultCrypto.zeroize(metadataKey)
            return Restored(
                vaultId = header.info.vaultId,
                createdAtMs = keyFile.createdAtMs,
                itemCount = objectCount,
                totalBytes = written
            )
        } finally {
            VaultCrypto.zeroize(masterKey)
        }
    }

    /** Convenience wrapper: restore from a file. */
    fun restore(file: File, target: VaultPaths, recoverySecret: ByteArray): Restored =
        FileInputStream(file).use { restore(it, target, recoverySecret) }

    /** True when [file] looks like a vault backup container. */
    fun looksLikeBackup(file: File): Boolean {
        if (file.length() < MIN_CONTAINER_BYTES) return false
        return runCatching {
            FileInputStream(file).use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 && String(magic, Charsets.US_ASCII) == MAGIC
            }
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private class Header(val info: Info, val rawBytes: ByteArray, val bodyBytes: Long)

    private fun readHeader(input: InputStream): Header {
        val raw = java.io.ByteArrayOutputStream()
        fun consume(length: Int): ByteArray {
            val bytes = readExactly(input, length.toLong())
            raw.write(bytes)
            return bytes
        }
        val magic = consume(4)
        if (String(magic, Charsets.US_ASCII) != MAGIC) throw VaultCryptoException("Not a vault backup")
        val version = consume(1)[0].toInt()
        if (version != VERSION) throw VaultCryptoException("Unsupported backup version $version")
        consume(1) // flags
        val saltLength = consume(1)[0].toInt() and 0xFF
        if (saltLength != VaultCrypto.SALT_BYTES) throw VaultCryptoException("Backup salt is malformed")
        consume(saltLength)
        val headerLength = readIntFromBytes(consume(4))
        if (headerLength <= 0 || headerLength > 64 * 1024) throw VaultCryptoException("Backup header is malformed")
        val headerJson = String(consume(headerLength), Charsets.UTF_8)
        val bodyBytes = readLongFromBytes(consume(8))
        if (bodyBytes < 0) throw VaultCryptoException("Backup body length is invalid")
        val root = MiniJson.parse(headerJson) as? Map<*, *> ?: throw VaultCryptoException("Backup header is not an object")
        val wrapper = VaultWrappedKey.fromMap(root["recoveryWrapper"])
            ?: throw VaultCryptoException("Backup has no recovery key wrapper")
        val info = Info(
            vaultId = root["vaultId"] as? String ?: throw VaultCryptoException("Backup has no vault id"),
            createdAtMs = (root["createdAtMs"] as? Number)?.toLong() ?: 0L,
            itemCount = (root["itemCount"] as? Number)?.toInt() ?: 0,
            totalBytes = (root["totalBytes"] as? Number)?.toLong() ?: 0L,
            recoveryWrapper = wrapper
        )
        return Header(info, raw.toByteArray(), bodyBytes)
    }

    private class CountingDigestOutputStream(
        private val delegate: OutputStream,
        private val digest: MessageDigest
    ) : OutputStream() {
        var count: Long = 0L
            private set

        override fun write(b: Int) {
            delegate.write(b)
            digest.update(b.toByte())
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            digest.update(b, off, len)
            count += len
        }

        override fun flush() = delegate.flush()

        override fun close() = delegate.close()
    }

    private class CountingInputStream(
        private val delegate: InputStream,
        private val digest: MessageDigest
    ) : InputStream() {
        var count: Long = 0L
            private set

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) {
                digest.update(value.toByte())
                count++
            }
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = delegate.read(b, off, len)
            if (read > 0) {
                digest.update(b, off, read)
                count += read
            }
            return read
        }
    }

    private fun writeInt(out: OutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeLong(out: OutputStream, value: Long) {
        for (shift in 56 downTo 0 step 8) out.write(((value ushr shift) and 0xFF).toInt())
    }

    private fun readInt(input: InputStream): Int = readIntFromBytes(readExactly(input, 4))

    private fun readLong(input: InputStream): Long = readLongFromBytes(readExactly(input, 8))

    private fun readIntFromBytes(bytes: ByteArray): Int {
        var value = 0
        for (byte in bytes) value = (value shl 8) or (byte.toInt() and 0xFF)
        return value
    }

    private fun readLongFromBytes(bytes: ByteArray): Long {
        var value = 0L
        for (byte in bytes) value = (value shl 8) or (byte.toLong() and 0xFF)
        return value
    }

    private fun readExactly(input: InputStream, length: Long): ByteArray {
        if (length > Int.MAX_VALUE) throw IOException("Record too large")
        val out = ByteArray(length.toInt())
        var offset = 0
        while (offset < out.size) {
            val read = input.read(out, offset, out.size - offset)
            if (read < 0) throw EOFException("Unexpected end of backup")
            offset += read
        }
        return out
    }
}
