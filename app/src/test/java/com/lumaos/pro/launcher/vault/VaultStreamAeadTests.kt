package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultCrypto
import com.lumaos.pro.launcher.vault.core.VaultCryptoException
import com.lumaos.pro.launcher.vault.core.VaultObjectReader
import com.lumaos.pro.launcher.vault.core.VaultStreamAead
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * Streaming AEAD tests.
 *
 * The cases that matter are the hostile ones: a chunk flipped, a stream cut short,
 * a stream extended, two chunks swapped, the header rewritten, and the wrong key.
 * Each must fail authentication rather than produce bytes.
 */
class VaultStreamAeadTests {

    private val dek = VaultCrypto.newKey()

    @Test
    fun roundTripsEveryInterestingLength() {
        val chunk = VaultStreamAead.CHUNK_BYTES
        val lengths = listOf(0, 1, 15, 16, 1024, chunk - 1, chunk, chunk + 1, 2 * chunk, 2 * chunk + 7)
        for (length in lengths) {
            val plaintext = VaultCrypto.randomBytes(maxOf(1, length)).copyOf(length)
            val sealed = encrypt(plaintext)
            assertTrue("header must be present", sealed.size > VaultStreamAead.HEADER_BYTES)
            assertArrayEquals("length $length", plaintext, decrypt(sealed))
        }
    }

    @Test
    fun ciphertextRevealsNoPlaintext() {
        val marker = "PRIVATE-PAYLOAD-MARKER".toByteArray()
        val plaintext = marker + ByteArray(200_000) { 0x41 } + marker
        val sealed = encrypt(plaintext)
        val haystack = String(sealed, Charsets.ISO_8859_1)
        assertTrue(!haystack.contains("PRIVATE-PAYLOAD-MARKER"))
    }

    @Test
    fun rejectsWrongKey() {
        val sealed = encrypt("secret".toByteArray())
        assertThrows(VaultCryptoException::class.java) { decrypt(sealed, VaultCrypto.newKey()) }
    }

    @Test
    fun rejectsFlippedByteAnywhereInBody() {
        val sealed = encrypt(VaultCrypto.randomBytes(VaultStreamAead.CHUNK_BYTES * 2 + 11))
        val targets = listOf(
            VaultStreamAead.HEADER_BYTES,               // first chunk's first byte
            VaultStreamAead.HEADER_BYTES + 7,           // first chunk's middle
            sealed.size - 1,                            // very last tag byte
            sealed.size / 2                             // a middle chunk
        )
        for (index in targets) {
            val tampered = sealed.copyOf()
            tampered[index] = (tampered[index].toInt() xor 0x40).toByte()
            assertThrows("byte $index", VaultCryptoException::class.java) { decrypt(tampered) }
        }
    }

    @Test
    fun rejectsTamperedHeader() {
        val sealed = encrypt("secret".toByteArray())
        val tampered = sealed.copyOf()
        // Flip a bit inside the salt: the derived chunk key changes, so the tag fails.
        tampered[12] = (tampered[12].toInt() xor 0x01).toByte()
        assertThrows(VaultCryptoException::class.java) { decrypt(tampered) }
    }

    @Test
    fun rejectsTruncatedStream() {
        val sealed = encrypt(VaultCrypto.randomBytes(VaultStreamAead.CHUNK_BYTES * 2))
        // Drop the final chunk entirely.
        val truncated = sealed.copyOf(sealed.size - (VaultStreamAead.CHUNK_BYTES + 16))
        assertThrows(VaultCryptoException::class.java) { decrypt(truncated) }
    }

    @Test
    fun rejectsAppendedData() {
        val sealed = encrypt("secret".toByteArray())
        val extended = sealed + ByteArray(16)
        assertThrows(VaultCryptoException::class.java) { decrypt(extended) }
    }

    @Test
    fun rejectsReorderedChunks() {
        val chunk = VaultStreamAead.CHUNK_BYTES
        val sealed = encrypt(VaultCrypto.randomBytes(chunk * 3))
        val first = sealed.copyOfRange(VaultStreamAead.HEADER_BYTES, VaultStreamAead.HEADER_BYTES + chunk + 16)
        val second = sealed.copyOfRange(
            VaultStreamAead.HEADER_BYTES + chunk + 16,
            VaultStreamAead.HEADER_BYTES + 2 * (chunk + 16)
        )
        val swapped = sealed.copyOfRange(0, VaultStreamAead.HEADER_BYTES) + second + first +
            sealed.copyOfRange(VaultStreamAead.HEADER_BYTES + 2 * (chunk + 16), sealed.size)
        assertThrows(VaultCryptoException::class.java) { decrypt(swapped) }
    }

    @Test
    fun rejectsNonVaultData() {
        assertThrows(VaultCryptoException::class.java) { decrypt("hello world, not a vault object".toByteArray()) }
    }

    @Test
    fun randomAccessReaderMatchesSequentialDecryption() {
        val plaintext = VaultCrypto.randomBytes(VaultStreamAead.CHUNK_BYTES * 3 + 777)
        val file = writeObjectFile(plaintext)
        try {
            VaultObjectReader.open(file, dek.copyOf(), plaintext.size.toLong()).use { reader ->
                assertEquals(plaintext.size.toLong(), reader.length)
                // Read the whole thing, sequentially.
                val read = ByteArray(plaintext.size)
                var position = 0
                while (position < read.size) {
                    val count = reader.readAt(position.toLong(), read, position, read.size - position)
                    if (count <= 0) break
                    position += count
                }
                assertArrayEquals(plaintext, read)

                // And out of order, crossing chunk boundaries.
                val buffer = ByteArray(1000)
                for (start in listOf(0L, 1L, 65_000L, 65_536L, 131_072L, 200_000L)) {
                    val count = reader.readAt(start, buffer, 0, buffer.size)
                    if (count <= 0) continue
                    assertArrayEquals(
                        "offset $start",
                        plaintext.copyOfRange(start.toInt(), start.toInt() + count),
                        buffer.copyOf(count)
                    )
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun randomAccessReaderRejectsCorruption() {
        val plaintext = VaultCrypto.randomBytes(VaultStreamAead.CHUNK_BYTES * 2)
        val file = writeObjectFile(plaintext)
        try {
            // Corrupt the second chunk on disk.
            val bytes = file.readBytes()
            val index = VaultStreamAead.HEADER_BYTES + (VaultStreamAead.CHUNK_BYTES + 16) + 3
            bytes[index] = (bytes[index].toInt() xor 0x11).toByte()
            file.writeBytes(bytes)
            VaultObjectReader.open(file, dek.copyOf(), plaintext.size.toLong()).use { reader ->
                assertThrows(VaultCryptoException::class.java) {
                    reader.readAt(VaultStreamAead.CHUNK_BYTES.toLong(), ByteArray(10), 0, 10)
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        VaultStreamAead.encrypt(dek, ByteArrayInputStream(plaintext), out)
        return out.toByteArray()
    }

    private fun decrypt(sealed: ByteArray, key: ByteArray = dek): ByteArray {
        val out = ByteArrayOutputStream()
        VaultStreamAead.decrypt(key, ByteArrayInputStream(sealed), out)
        return out.toByteArray()
    }

    private fun writeObjectFile(plaintext: ByteArray): File {
        val directory = Files.createTempDirectory("vault-stream").toFile()
        val file = File(directory, "object.obj")
        file.outputStream().use { VaultStreamAead.encrypt(dek, ByteArrayInputStream(plaintext), it) }
        file.deleteOnExit()
        directory.deleteOnExit()
        return file
    }
}
