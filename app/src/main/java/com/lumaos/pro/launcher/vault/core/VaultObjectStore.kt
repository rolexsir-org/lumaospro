package com.lumaos.pro.launcher.vault.core

import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Encrypted object storage.
 *
 * An object is one file under `objects/<id>.obj`, holding nothing but a
 * streaming-AEAD blob (see [VaultStreamAead]). Its data key is random per object,
 * wrapped by the vault master key, and stored in the encrypted metadata index -
 * so the payload file on its own is meaningless: no header of ours names it, and
 * the DEK is not inside it.
 *
 * Writes always land in `staging/` first and are renamed into place only after
 * `fsync`, and the metadata index is committed afterwards. A crash can therefore
 * leave an orphaned *encrypted* file (swept at next unlock) but can never leave
 * an index entry pointing at a half-written object, and can never damage an
 * object that already existed.
 */
class VaultObjectStore(private val paths: VaultPaths, private val masterKey: ByteArray) {

    /** Result of storing a payload: the identity of a new object plus its wrapped DEK. */
    class Stored(
        val id: String,
        val dekIv: String,
        val wrappedDek: String,
        val sizeBytes: Long
    )

    /**
     * Encrypts [source] into a new object.
     *
     * The source stream is consumed exactly once and never copied to disk in the
     * clear: bytes go from the caller's stream (a SAF descriptor, a camera
     * scratch file, an encoder output) straight through AES-GCM into the staging
     * file. On any failure the staging file is shredded and nothing is committed.
     */
    fun store(source: InputStream, objectId: String = VaultEncoding.newId()): Stored {
        paths.ensureDirectories()
        val dek = VaultCrypto.newKey()
        val target = paths.objectFile(objectId)
        val staging = paths.stagingFile(".obj")
        try {
            val plaintextBytes = FileOutputStream(staging).use { out ->
                VaultStreamAead.encrypt(dek, source, out)
            }
            FileOutputStream(staging, true).use { it.fd.sync() }

            // Read the freshly written blob back before publishing it: an object
            // that cannot be decrypted is never allowed into the vault.
            verifyReadable(staging, dek, plaintextBytes)

            if (!staging.renameTo(target)) {
                throw IOException("Could not move object into place")
            }
            val wrapped = wrapDek(dek, objectId)
            return Stored(objectId, VaultEncoding.base64(wrapped.iv), VaultEncoding.base64(wrapped.ciphertext), plaintextBytes)
        } catch (e: Exception) {
            VaultFiles.shred(staging)
            throw e
        } finally {
            VaultCrypto.zeroize(dek)
        }
    }

    private fun verifyReadable(file: File, dek: ByteArray, plaintextBytes: Long) {
        VaultObjectReader.open(file, dek.copyOf(), plaintextBytes).use { reader ->
            if (reader.length != plaintextBytes) throw IOException("Object length mismatch after write")
            val probe = ByteArray(minOf(64L, plaintextBytes).toInt())
            if (probe.isNotEmpty()) {
                val head = reader.readAt(0, probe, 0, probe.size)
                if (head != probe.size) throw IOException("Object is unreadable after write")
                if (plaintextBytes > probe.size) {
                    val last = reader.readAt(plaintextBytes - 1, probe, 0, 1)
                    if (last != 1) throw IOException("Object tail is unreadable after write")
                }
            }
        }
    }

    /** Opens an object for full read access, verifying integrity as it goes. */
    fun open(item: VaultItem): VaultObjectReader {
        val dek = unwrapDek(item)
        return try {
            // The reader takes ownership of its key copy and zeroizes it on close.
            VaultObjectReader.open(paths.objectFile(item.id), dek.copyOf(), item.sizeBytes)
        } catch (e: Exception) {
            VaultCrypto.zeroize(dek)
            throw e
        }
    }

    /**
     * Decrypts an object into [sink], verifying every chunk.
     *
     * Used for export and for streaming playback where random access is not
     * needed. Returns the number of plaintext bytes written.
     */
    fun copyTo(item: VaultItem, sink: java.io.OutputStream): Long {
        val dek = unwrapDek(item)
        try {
            FileInputStream(paths.objectFile(item.id)).use { input ->
                return VaultStreamAead.decrypt(dek, input, sink)
            }
        } finally {
            VaultCrypto.zeroize(dek)
        }
    }

    /** Unwraps this object's data key. Fails if the index was tampered with. */
    fun unwrapDek(item: VaultItem): ByteArray {
        val wrapperKey = VaultKeyring.objectKeyWrapperKey(masterKey, item.id)
        return try {
            VaultCrypto.open(
                wrapperKey,
                VaultEncoding.fromBase64(item.dekIv),
                VaultEncoding.fromBase64(item.wrappedDek),
                aad(item.id)
            )
        } finally {
            VaultCrypto.zeroize(wrapperKey)
        }
    }

    /** Deletes an object's payload. The index entry must be removed by the caller. */
    fun delete(itemId: String) {
        VaultFiles.shred(paths.objectFile(itemId))
    }

    /** Removes encrypted payloads that no index entry references. */
    fun sweepOrphans(index: VaultIndex): Int {
        val referenced = index.items.map { "${it.id}.obj" }.toSet()
        val orphans = paths.objects.listFiles()?.filter { it.name !in referenced } ?: return 0
        orphans.forEach { it.delete() }
        return orphans.size
    }

    /** True when the payload file exists and is at least one authenticated chunk. */
    fun isReadable(item: VaultItem): Boolean {
        val file = paths.objectFile(item.id)
        return file.isFile && file.length() >= VaultStreamAead.HEADER_BYTES + VaultCrypto.GCM_TAG_BYTES
    }

    private fun wrapDek(dek: ByteArray, objectId: String): VaultCrypto.Sealed {
        val wrapperKey = VaultKeyring.objectKeyWrapperKey(masterKey, objectId)
        return try {
            VaultCrypto.seal(wrapperKey, dek, aad(objectId))
        } finally {
            VaultCrypto.zeroize(wrapperKey)
        }
    }

    private fun aad(objectId: String): ByteArray = "lumaos/vault/object-dek/v1|$objectId".toByteArray(Charsets.UTF_8)

    companion object {
        /**
         * Writes an object directly without a pre-existing index entry, used by
         * backup restore which imports payloads plus their wrapped DEKs verbatim.
         */
        fun importPayload(paths: VaultPaths, objectId: String, source: InputStream): Long {
            paths.ensureDirectories()
            val target = paths.objectFile(objectId)
            val staging = paths.stagingFile(".obj")
            try {
                val written = FileOutputStream(staging).use { out ->
                    var total = 0L
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        total += read
                    }
                    out.flush()
                    out.fd.sync()
                    total
                }
                if (!staging.renameTo(target)) throw IOException("Could not move restored object into place")
                return written
            } catch (e: Exception) {
                staging.delete()
                throw e
            }
        }
    }
}

/**
 * Random-access decrypting reader for one vault object.
 *
 * Playback and image decoding need to read parts of an object without decrypting
 * all of it: the chunked AEAD format makes that possible, because every chunk but
 * the last has the same ciphertext length. Each chunk is authenticated on its own
 * before a single byte of it is returned.
 *
 * The reader owns a copy of the object's data key and zeroizes it on [close].
 */
class VaultObjectReader private constructor(
    private val file: RandomAccessFile,
    private val dek: ByteArray,
    private val header: VaultStreamAead.Header,
    private val chunkKey: ByteArray,
    val length: Long
) : Closeable {

    private val chunkBytes = header.chunkBytes
    private val cipherChunkBytes = VaultStreamAead.fullCipherChunkBytes(chunkBytes)
    private var cachedIndex = -1L
    private var cachedPlain: ByteArray = VaultCrypto.EMPTY

    /** Number of plaintext chunks, always at least one (an empty object has one). */
    private val chunkCount: Long = if (length <= 0L) 1L else (length + chunkBytes - 1) / chunkBytes

    /**
     * Reads up to [size] plaintext bytes starting at [position].
     *
     * Returns the number of bytes written into [buffer], or -1 at end of object.
     */
    fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0L) throw IOException("Negative position")
        if (position >= length) return -1
        if (size <= 0) return 0
        var remaining = minOf(size.toLong(), length - position).toInt()
        var outOffset = offset
        var pos = position
        var written = 0
        while (remaining > 0) {
            val chunkIndex = pos / chunkBytes
            val chunkOffset = (pos % chunkBytes).toInt()
            val plain = chunk(chunkIndex)
            val available = plain.size - chunkOffset
            if (available <= 0) return if (written == 0) -1 else written
            val take = minOf(available, remaining)
            System.arraycopy(plain, chunkOffset, buffer, outOffset, take)
            written += take
            outOffset += take
            pos += take
            remaining -= take
        }
        return written
    }

    /** Sequential view over [readAt], suitable for `BitmapFactory.decodeStream`. */
    fun asInputStream(): InputStream = object : InputStream() {
        private var position = 0L
        private val single = ByteArray(1)

        override fun read(): Int {
            val read = readAt(position, single, 0, 1)
            if (read <= 0) return -1
            position += 1
            return single[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = readAt(position, b, off, len)
            if (read > 0) position += read
            return read
        }

        override fun available(): Int = (length - position).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun chunk(index: Long): ByteArray {
        if (index == cachedIndex) return cachedPlain
        if (index < 0 || index >= chunkCount) return VaultCrypto.EMPTY
        val isFinal = index == chunkCount - 1
        val cipherLength = if (isFinal) {
            val consumed = index * chunkBytes
            (length - consumed + VaultCrypto.GCM_TAG_BYTES).toInt()
        } else {
            cipherChunkBytes
        }
        val offset = VaultStreamAead.HEADER_BYTES + index * cipherChunkBytes
        if (cipherLength < VaultCrypto.GCM_TAG_BYTES) throw VaultCryptoException("Corrupt object chunk")
        val ciphertext = ByteArray(cipherLength)
        file.seek(offset)
        file.readFully(ciphertext)
        val plaintext = VaultCrypto.open(
            chunkKey,
            VaultStreamAead.chunkNonce(header, index, isFinal),
            ciphertext,
            header.raw
        )
        cachedIndex = index
        cachedPlain = plaintext
        return plaintext
    }

    override fun close() {
        runCatching { file.close() }
        VaultCrypto.zeroize(chunkKey)
        VaultCrypto.zeroize(cachedPlain)
        VaultCrypto.zeroize(dek)
        cachedPlain = VaultCrypto.EMPTY
        cachedIndex = -1
    }

    companion object {
        /**
         * Opens [file] and validates its header. [plaintextLength] comes from the
         * authenticated metadata index, so a payload cannot claim a different
         * length than the one the vault recorded for it.
         */
        fun open(file: File, dek: ByteArray, plaintextLength: Long): VaultObjectReader {
            if (!file.isFile) throw IOException("Object payload is missing")
            val header = FileInputStream(file).use { VaultStreamAead.readHeader(it) }
            val raf = RandomAccessFile(file, "r")
            val minimum = VaultStreamAead.HEADER_BYTES + VaultCrypto.GCM_TAG_BYTES
            if (raf.length() < minimum) {
                raf.close()
                throw VaultCryptoException("Object payload is truncated")
            }
            return VaultObjectReader(raf, dek, header, VaultStreamAead.chunkKey(dek, header), plaintextLength)
        }
    }
}
