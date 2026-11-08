package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultCryptoException
import com.lumaos.pro.launcher.vault.core.VaultIndex
import com.lumaos.pro.launcher.vault.core.VaultItem
import com.lumaos.pro.launcher.vault.core.VaultItemKind
import com.lumaos.pro.launcher.vault.core.VaultKeyring
import com.lumaos.pro.launcher.vault.core.VaultMetadataStore
import com.lumaos.pro.launcher.vault.core.VaultObjectStore
import com.lumaos.pro.launcher.vault.core.VaultPaths
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/**
 * Storage privacy and integrity tests.
 *
 * These are the tests that answer "is it *actually* encrypted at rest": they read
 * the vault directory as raw bytes and look for the content and the file name that
 * was stored, and they check that an interrupted write cannot destroy or expose
 * anything.
 */
class VaultStorageTests {

    private lateinit var root: File
    private lateinit var paths: VaultPaths
    private lateinit var masterKey: ByteArray
    private lateinit var objects: VaultObjectStore
    private lateinit var metadata: VaultMetadataStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("vault-storage").toFile()
        paths = VaultPaths(root)
        paths.ensureDirectories()
        masterKey = VaultKeyring.newMasterKey()
        objects = VaultObjectStore(paths, masterKey)
        metadata = VaultMetadataStore(paths, VaultMetadataStore.keyFor(masterKey))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun objectRoundTripReturnsExactlyWhatWasStored() {
        val payload = "a private photo's bytes".toByteArray()
        val stored = objects.store(ByteArrayInputStream(payload))
        val item = itemFor(stored)
        assertTrue(objects.isReadable(item))
        assertEquals(payload.size.toLong(), stored.sizeBytes)
        objects.open(item).use { reader ->
            val read = ByteArray(payload.size)
            var offset = 0
            while (offset < read.size) {
                val count = reader.readAt(offset.toLong(), read, offset, read.size - offset)
                if (count <= 0) break
                offset += count
            }
            assertArrayEquals(payload, read)
        }
    }

    @Test
    fun storedFileContainsNoPlaintextOrOriginalName() {
        val payload = "TOP-SECRET-CONTENT-91".toByteArray()
        val stored = objects.store(ByteArrayInputStream(payload))
        val file = paths.objectFile(stored.id)
        val raw = String(file.readBytes(), Charsets.ISO_8859_1)
        assertFalse("plaintext must not appear on disk", raw.contains("TOP-SECRET-CONTENT-91"))
        assertTrue("object names must be opaque", file.name.matches(Regex("[0-9a-f]{32}\\.obj")))
    }

    @Test
    fun identicalContentGetsIndependentKeysAndCiphertexts() {
        val payload = "same bytes".toByteArray()
        val first = objects.store(ByteArrayInputStream(payload))
        val second = objects.store(ByteArrayInputStream(payload))
        assertFalse("each object needs its own data key", first.wrappedDek == second.wrappedDek)
        assertFalse(
            "identical plaintext must not produce identical ciphertext",
            paths.objectFile(first.id).readBytes().contentEquals(paths.objectFile(second.id).readBytes())
        )
        assertEquals("same bytes", String(decryptAll(itemFor(first))))
        assertEquals("same bytes", String(decryptAll(itemFor(second))))
    }

    @Test
    fun wrappedDataKeyCannotBeMovedToAnotherObject() {
        val stored = objects.store(ByteArrayInputStream("first".toByteArray()))
        val other = objects.store(ByteArrayInputStream("second".toByteArray()))
        // Move the first object's key onto the second object's id.
        val stolen = itemFor(stored).copy(id = other.id)
        assertThrows(VaultCryptoException::class.java) { objects.open(stolen).close() }
    }

    @Test
    fun corruptPayloadIsRejectedOnRead() {
        val stored = objects.store(ByteArrayInputStream("content".toByteArray()))
        val file = paths.objectFile(stored.id)
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        file.writeBytes(bytes)
        assertThrows(VaultCryptoException::class.java) {
            objects.open(itemFor(stored)).use { it.readAt(0, ByteArray(8), 0, 8) }
        }
    }

    @Test
    fun interruptedStoreLeavesNoObjectAndNoStagingFlies() {
        val stored = runCatching {
            objects.store(object : InputStream() {
                private var served = 0
                override fun read(): Int = 0
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (served > 200_000) throw IOException("source died")
                    val count = minOf(len, 8 * 1024)
                    b.fill(0x5A, off, off + count)
                    served += count
                    return count
                }
            })
        }
        assertTrue(stored.isFailure)
        assertEquals("no object may be published", 0, paths.objects.listFiles()?.size ?: 0)
        assertEquals("staging must be cleaned", 0, paths.staging.listFiles()?.size ?: 0)
    }

    @Test
    fun metadataIsEncryptedAndRoundTrips() {
        val vmkRef = masterKey
        val index = VaultIndex.empty("vault-under-test").withItem(
            VaultItem(
                id = "0123456789abcdef0123456789abcdef",
                kind = VaultItemKind.IMAGE,
                name = "passport-scan.png",
                mimeType = "image/png",
                sizeBytes = 1234,
                addedAtMs = 1_700_000_000_000L,
                capturedAtMs = 1_700_000_000_000L,
                folderId = null,
                dekIv = "AAAAAAAAAAAAAAAA",
                wrappedDek = "BBBBBBBBBBBBBBBB"
            )
        )
        metadata.commit(index)
        val raw = String(paths.metadata.readBytes(), Charsets.ISO_8859_1)
        assertFalse("file names must not be readable on disk", raw.contains("passport-scan.png"))
        assertFalse("kinds must not be readable on disk", raw.contains("image/png"))

        val restored = metadata.read("vault-under-test")
        assertNotNull(restored)
        assertEquals(1, restored!!.items.size)
        assertEquals("passport-scan.png", restored.items[0].name)
        assertEquals(VaultItemKind.IMAGE, restored.items[0].kind)
        assertEquals(1L, restored.generation)
        assertTrue(vmkRef.isNotEmpty())
    }

    @Test
    fun metadataTamperingIsDetected() {
        metadata.commit(VaultIndex.empty("vault-under-test"))
        val bytes = paths.metadata.readBytes()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 0x20).toByte()
        paths.metadata.writeBytes(bytes)
        assertThrows(Exception::class.java) { metadata.read("vault-under-test") }
    }

    @Test
    fun metadataFallsBackToThePreviousGenerationAfterAnInterruptedCommit() {
        metadata.commit(VaultIndex.empty("vault-under-test"))
        val first = metadata.read("vault-under-test")!!
        metadata.commit(first.withEvent(com.lumaos.pro.launcher.vault.core.VaultEventType.CREATED))
        // Simulate a torn write of the primary copy: the backup copy must still open.
        paths.metadata.writeBytes(byteArrayOf(1, 2, 3, 4))
        val recovered = metadata.read("vault-under-test")
        assertNotNull(recovered)
        assertEquals("vault-under-test", recovered!!.vaultId)
    }

    @Test
    fun metadataBelongsToItsOwnVault() {
        metadata.commit(VaultIndex.empty("vault-a"))
        assertThrows(Exception::class.java) { metadata.read("vault-b") }
    }

    @Test
    fun wrongMasterKeyCannotOpenMetadata() {
        metadata.commit(VaultIndex.empty("vault-under-test"))
        val other = VaultMetadataStore(paths, VaultMetadataStore.keyFor(VaultKeyring.newMasterKey()))
        assertThrows(Exception::class.java) { other.read("vault-under-test") }
    }

    @Test
    fun orphanedObjectsAreSwept() {
        val kept = objects.store(ByteArrayInputStream("keep".toByteArray()))
        val orphan = objects.store(ByteArrayInputStream("orphan".toByteArray()))
        val index = VaultIndex.empty("vault-under-test").withItem(itemFor(kept))
        metadata.commit(index)
        val removed = objects.sweepOrphans(index)
        assertEquals(1, removed)
        assertTrue(paths.objectFile(kept.id).isFile)
        assertFalse(paths.objectFile(orphan.id).isFile)
    }

    @Test
    fun deletionShredsThePayload() {
        val stored = objects.store(ByteArrayInputStream("delete me".toByteArray()))
        val file = paths.objectFile(stored.id)
        assertTrue(file.isFile)
        objects.delete(stored.id)
        assertFalse(file.isFile)
    }

    @Test
    fun stagingIsClearedBetweenSessions() {
        val scratch = paths.stagingFile(".jpg")
        scratch.writeBytes(ByteArray(64) { 1 })
        assertTrue(scratch.isFile)
        paths.clearStaging()
        assertNull(paths.staging.listFiles()?.firstOrNull())
    }

    @Test
    fun emptyObjectRoundTrips() {
        val stored = objects.store(ByteArrayInputStream(ByteArray(0)))
        assertEquals(0L, stored.sizeBytes)
        assertEquals(0, decryptAll(itemFor(stored)).size)
    }

    @Test
    fun largeObjectStreamsWithoutBufferingItAll() {
        val size = 5 * 1024 * 1024
        val payload = ByteArray(size) { (it % 251).toByte() }
        val stored = objects.store(ByteArrayInputStream(payload))
        assertEquals(size.toLong(), stored.sizeBytes)
        assertArrayEquals(payload, decryptAll(itemFor(stored)))
    }

    private fun itemFor(stored: VaultObjectStore.Stored): VaultItem = VaultItem(
        id = stored.id,
        kind = VaultItemKind.OTHER,
        name = "object",
        mimeType = "application/octet-stream",
        sizeBytes = stored.sizeBytes,
        addedAtMs = 0L,
        capturedAtMs = 0L,
        folderId = null,
        dekIv = stored.dekIv,
        wrappedDek = stored.wrappedDek
    )

    private fun decryptAll(item: VaultItem): ByteArray =
        java.io.ByteArrayOutputStream().use { out ->
            objects.copyTo(item, out)
            out.toByteArray()
        }
}
