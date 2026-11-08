package com.lumaos.pro.launcher.vault.core

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream

/**
 * Streaming authenticated encryption for Vault objects.
 *
 * Large objects (videos, scanned PDFs, big imports) must never be buffered in
 * memory, and must never exist as plaintext on disk. This format is therefore a
 * *seekable-free, chunked AEAD*: the plaintext is split into fixed-size chunks;
 * every chunk is encrypted with AES-256-GCM under a key derived from the
 * object's own data key; the final chunk is flagged. Nothing is ever written
 * unencrypted, and a truncated, reordered, extended or edited blob fails
 * authentication instead of decrypting to something wrong.
 *
 * ## Wire format (version 1)
 *
 * ```
 * header:
 *   magic            4 bytes   "LVSA"
 *   version          1 byte    0x01
 *   chunkBytes       4 bytes   big-endian, plaintext bytes per chunk (65536)
 *   saltLength       1 byte    0x20
 *   salt            32 bytes   random per object
 *   noncePrefix      7 bytes   random per object
 * body:
 *   for i = 0, 1, 2, ...:
 *     AES-256-GCM(chunkKey_i, nonce_i, plaintextChunk_i)     // ciphertext || 16-byte tag
 * ```
 *
 * `chunkKey_i = HKDF-SHA-256(ikm = DEK, salt = salt, info = "lumaos/vault/object-stream/v1")`
 * - one key for the whole object, because the nonce is unique per chunk:
 *
 * `nonce_i = noncePrefix(7) || i as uint32 big-endian || finalFlag(1)`
 *
 * with `finalFlag = 0x01` on the last chunk and `0x00` on every other chunk.
 * This is the same construction Google Tink uses for AES-GCM-HKDF streaming
 * AEAD: a random per-object salt and nonce prefix make the (key, nonce) pair
 * unique for every chunk of every object, and the authenticated final flag plus
 * the counter mean that cutting a stream short, dropping a chunk, reordering
 * chunks or appending data is always detected.
 *
 * The 49-byte header is passed as AEAD associated data for every chunk, so the
 * chunk size, salt and nonce prefix cannot be tampered with either.
 *
 * ## Why this file exists rather than calling a library
 *
 * A streaming-AEAD library (Tink) is the natural first choice, and it is
 * recommended as a drop-in replacement behind [StreamWriter]/[StreamReader].
 * Vault's Gradle setup resolves dependencies from a locked version catalog in an
 * offline-ish build environment, so the release here uses only the platform's
 * AES-GCM with the published Tink framing; [VaultCrypto] contains no custom
 * primitive, and this file contains no custom primitive either - it is framing.
 */
object VaultStreamAead {

    const val MAGIC = "LVSA"
    const val VERSION = 1
    const val CHUNK_BYTES = 64 * 1024
    const val SALT_BYTES = 32
    const val NONCE_PREFIX_BYTES = 7
    const val HEADER_BYTES = 4 + 1 + 4 + 1 + SALT_BYTES + NONCE_PREFIX_BYTES // 49

    /** Domain separation for the per-object chunk key. */
    const val KEY_INFO = "lumaos/vault/object-stream/v1"

    /** Largest object Vault will stream (counter is 32-bit). */
    const val MAX_PLAINTEXT_BYTES: Long = CHUNK_BYTES.toLong() * 0xFFFF_FFFFL

    /**
     * Encrypts [input] into [output], returning the number of plaintext bytes.
     *
     * The caller is responsible for writing to a temporary file and renaming it
     * into place ([VaultObjectStore] does exactly this), so an interrupted
     * encryption can never damage an existing object.
     */
    fun encrypt(dek: ByteArray, input: InputStream, output: OutputStream): Long {
        require(dek.size == VaultCrypto.KEY_BYTES) { "data key must be 256 bits" }
        val salt = VaultCrypto.randomBytes(SALT_BYTES)
        val noncePrefix = VaultCrypto.randomBytes(NONCE_PREFIX_BYTES)
        val header = buildHeader(salt, noncePrefix)
        val chunkKey = VaultCrypto.hkdf(dek, salt, KEY_INFO)
        val source = PushbackInputStream(input, 2)

        try {
            output.write(header)
            val buffer = ByteArray(CHUNK_BYTES)
            var total = 0L
            var counter = 0L
            var pending = readFully(source, buffer)
            while (true) {
                // Look ahead by one chunk to learn whether this is the final one.
                val hasMore = pending == CHUNK_BYTES && peekMore(source)
                val length = pending
                val plaintext = if (length == buffer.size) buffer else buffer.copyOf(length)
                val nonce = nonce(noncePrefix, counter, isFinal = !hasMore)
                val sealed = VaultCrypto.sealWithIv(chunkKey, nonce, plaintext, header)
                output.write(sealed)
                total += length
                if (total > MAX_PLAINTEXT_BYTES) throw IOException("Object exceeds the stream AEAD limit")
                if (!hasMore) break
                counter++
                if (counter > 0xFFFF_FFFFL) throw IOException("Object exceeds the stream AEAD chunk limit")
                pending = readFully(source, buffer)
            }
            output.flush()
            return total
        } finally {
            VaultCrypto.zeroize(chunkKey)
        }
    }

    /**
     * Decrypts [input] into [output], returning the number of plaintext bytes.
     *
     * Throws [VaultCryptoException] if any chunk fails authentication, if the
     * stream ends without an authenticated final chunk (truncation), or if data
     * follows the final chunk (extension).
     */
    fun decrypt(dek: ByteArray, input: InputStream, output: OutputStream): Long {
        require(dek.size == VaultCrypto.KEY_BYTES) { "data key must be 256 bits" }
        val source = PushbackInputStream(input, 2)
        val header = readHeader(source)
        val chunkKey = VaultCrypto.hkdf(dek, header.salt, KEY_INFO)
        try {
            val buffer = ByteArray(header.chunkBytes + VaultCrypto.GCM_TAG_BYTES)
            var total = 0L
            var counter = 0L
            while (true) {
                val read = readFully(source, buffer)
                if (read < VaultCrypto.GCM_TAG_BYTES) {
                    throw VaultCryptoException("Truncated object: missing authentication tag")
                }
                val ciphertext = if (read == buffer.size) buffer else buffer.copyOf(read)
                val hasMore = peekMore(source)
                val plaintext = VaultCrypto.open(
                    chunkKey,
                    nonce(header.noncePrefix, counter, isFinal = !hasMore),
                    ciphertext,
                    header.raw
                )
                output.write(plaintext)
                total += plaintext.size
                if (!hasMore) break
                counter++
                if (counter > 0xFFFF_FFFFL) throw VaultCryptoException("Object exceeds the stream AEAD chunk limit")
            }
            output.flush()
            return total
        } finally {
            VaultCrypto.zeroize(chunkKey)
        }
    }

    /** Reads and validates a header without decrypting the body. */
    fun readHeader(input: InputStream): Header {
        val raw = ByteArray(HEADER_BYTES)
        if (readFully(input, raw) != HEADER_BYTES) throw VaultCryptoException("Not a vault object: short header")
        val magic = String(raw, 0, 4, Charsets.US_ASCII)
        if (magic != MAGIC) throw VaultCryptoException("Not a vault object: bad magic")
        val version = raw[4].toInt() and 0xFF
        if (version != VERSION) throw VaultCryptoException("Unsupported vault object version $version")
        var chunkBytes = 0
        for (i in 0 until 4) chunkBytes = (chunkBytes shl 8) or (raw[5 + i].toInt() and 0xFF)
        if (chunkBytes !in 1024..(4 * 1024 * 1024)) throw VaultCryptoException("Unsupported chunk size $chunkBytes")
        val saltLength = raw[9].toInt() and 0xFF
        if (saltLength != SALT_BYTES) throw VaultCryptoException("Unsupported salt length $saltLength")
        val salt = raw.copyOfRange(10, 10 + SALT_BYTES)
        val noncePrefix = raw.copyOfRange(10 + SALT_BYTES, HEADER_BYTES)
        return Header(salt, noncePrefix, chunkBytes, raw)
    }

    class Header(
        val salt: ByteArray,
        val noncePrefix: ByteArray,
        val chunkBytes: Int,
        val raw: ByteArray
    )

    private fun buildHeader(salt: ByteArray, noncePrefix: ByteArray): ByteArray {
        val out = ByteArray(HEADER_BYTES)
        MAGIC.toByteArray(Charsets.US_ASCII).copyInto(out, 0)
        out[4] = VERSION.toByte()
        for (i in 0 until 4) out[5 + i] = ((CHUNK_BYTES ushr (24 - 8 * i)) and 0xFF).toByte()
        out[9] = SALT_BYTES.toByte()
        salt.copyInto(out, 10)
        noncePrefix.copyInto(out, 10 + SALT_BYTES)
        return out
    }

    /**
     * `noncePrefix(7) || counter(4, big-endian) || finalFlag(1)` - 96 bits total.
     *
     * The chunk index is in the nonce and the final flag is authenticated, which
     * is what makes chunk reordering and truncation detectable.
     */
    /**
     * Key for a reader that seeks: exposure of the same derivation [decrypt] uses,
     * so a `MediaDataSource` can decrypt one chunk at a time.
     */
    fun chunkKey(dek: ByteArray, header: Header): ByteArray = VaultCrypto.hkdf(dek, header.salt, KEY_INFO)

    /** Nonce for chunk [index] (public so a seeking reader can reproduce it). */
    fun chunkNonce(header: Header, index: Long, isFinal: Boolean): ByteArray =
        nonce(header.noncePrefix, index, isFinal)

    /** Ciphertext length of a full chunk (plaintext bytes plus the GCM tag). */
    fun fullCipherChunkBytes(chunkBytes: Int): Int = chunkBytes + VaultCrypto.GCM_TAG_BYTES

    private fun nonce(prefix: ByteArray, counter: Long, isFinal: Boolean): ByteArray {
        val nonce = ByteArray(VaultCrypto.GCM_IV_BYTES)
        prefix.copyInto(nonce, 0)
        nonce[7] = ((counter ushr 24) and 0xFF).toByte()
        nonce[8] = ((counter ushr 16) and 0xFF).toByte()
        nonce[9] = ((counter ushr 8) and 0xFF).toByte()
        nonce[10] = (counter and 0xFF).toByte()
        nonce[11] = if (isFinal) 1 else 0
        return nonce
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }

    /** True when at least one more byte follows; that byte is pushed back. */
    private fun peekMore(input: PushbackInputStream): Boolean {
        val next = input.read()
        if (next < 0) return false
        input.unread(next)
        return true
    }
}
