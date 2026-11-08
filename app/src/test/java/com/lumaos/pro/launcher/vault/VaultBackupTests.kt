package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultBackup
import com.lumaos.pro.launcher.vault.core.VaultCrypto
import com.lumaos.pro.launcher.vault.core.VaultCryptoException
import com.lumaos.pro.launcher.vault.core.VaultIndex
import com.lumaos.pro.launcher.vault.core.VaultItem
import com.lumaos.pro.launcher.vault.core.VaultItemKind
import com.lumaos.pro.launcher.vault.core.VaultKeyring
import com.lumaos.pro.launcher.vault.core.VaultMetadataStore
import com.lumaos.pro.launcher.vault.core.VaultObjectStore
import com.lumaos.pro.launcher.vault.core.VaultPaths
import com.lumaos.pro.launcher.vault.core.VaultRecoveryCode
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * Backup and restore tests.
 *
 * The scenario that matters most is "the phone was lost": a vault is built in one
 * directory, a backup is written, and a *completely separate* vault directory with
 * no PIN, no Keystore and no prior state is restored from that file using only the
 * recovery key. Everything must still decrypt afterwards.
 */
class VaultBackupTests {

    private lateinit var root: File
    private lateinit var paths: VaultPaths
    private lateinit var masterKey: ByteArray
    private lateinit var objects: VaultObjectStore
    private lateinit var metadata: VaultMetadataStore
    private lateinit var recoverySecret: ByteArray
    private val vaultId = "0123456789abcdef0123456789abcdef"

    @Before
    fun setUp() {
        root = Files.createTempDirectory("vault-backup").toFile()
        paths = VaultPaths(root)
        paths.ensureDirectories()
        masterKey = VaultKeyring.newMasterKey()
        objects = VaultObjectStore(paths, masterKey)
        metadata = VaultMetadataStore(paths, VaultMetadataStore.keyFor(masterKey))
        recoverySecret = VaultRecoveryCode.generate()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun backupContainsCiphertextOnly() {
        val marker = "PHOTO-OF-MY-PASSPORT-77"
        val item = addImage(marker.toByteArray(), "passport.png")
        val file = writeBackup()
        val raw = String(file.readBytes(), Charsets.ISO_8859_1)
        assertFalse("payload must not be recoverable from the container", raw.contains(marker))
        assertFalse("file names must not appear in a backup", raw.contains("passport.png"))
        assertFalse("vault PIN never belongs in a backup", raw.contains("48193027"))
        // But the recovery-wrapped master key must be there, otherwise restore is impossible.
        assertTrue(VaultBackup.looksLikeBackup(file))
        assertEquals(item.id, metadata.read(vaultId)!!.items.first().id)
    }

    @Test
    fun restoreOnAFreshDeviceRecoversEveryObject() {
        val payload = ByteArray(300_000) { (it % 253).toByte() }
        val item = addImage(payload, "holiday.jpg")
        val note = addNote("Recovery test note", "the body of a private note")
        val file = writeBackup()

        // A brand-new device: new vault directory, no key ring, no Keystore.
        val freshRoot = Files.createTempDirectory("vault-restored").toFile()
        val freshPaths = VaultPaths(freshRoot)
        val restored = VaultBackup.restore(file, freshPaths, recoverySecret)
        assertEquals(vaultId, restored.vaultId)
        assertEquals(2, restored.itemCount)

        // The key ring on the new device carries the recovery wrapper, no PIN.
        val keyFile = VaultKeyring.fromJson(freshPaths.keyRing.readText())
        assertNull("a restored vault must not inherit the old PIN wrapper", keyFile.pin)
        assertNotNull(keyFile.recovery)
        val recoveredKey = VaultKeyring.unwrapWithRecovery(keyFile.recovery!!, recoverySecret, vaultId)

        // The recovered master key opens the restored index and both payloads.
        val store = VaultObjectStore(freshPaths, recoveredKey)
        val index = VaultMetadataStore(freshPaths, VaultMetadataStore.keyFor(recoveredKey)).read(vaultId)
        assertNotNull(index)
        assertEquals(2, index!!.items.size)
        val imageBytes = readAll(store, index.item(item.id)!!)
        assertArrayEquals(payload, imageBytes)
        val noteBytes = readAll(store, index.item(note.id)!!)
        assertEquals("the body of a private note", String(noteBytes))

        freshRoot.deleteRecursively()
    }

    @Test
    fun restoreWithTheWrongRecoveryKeyFails() {
        addNote("note", "body")
        val file = writeBackup()
        val fresh = VaultPaths(Files.createTempDirectory("vault-restored").toFile())
        assertThrows(VaultCryptoException::class.java) {
            VaultBackup.restore(file, fresh, VaultRecoveryCode.generate())
        }
        fresh.root.deleteRecursively()
    }

    @Test
    fun aSingleFlippedByteBreaksTheBackup() {
        addNote("note", "body")
        val file = writeBackup()
        val bytes = file.readBytes()
        // Flip a byte inside the body, well past the header.
        val index = bytes.size / 2
        bytes[index] = (bytes[index].toInt() xor 0x10).toByte()
        file.writeBytes(bytes)
        val fresh = VaultPaths(Files.createTempDirectory("vault-restored").toFile())
        assertThrows(VaultCryptoException::class.java) {
            VaultBackup.restore(file, fresh, recoverySecret)
        }
        fresh.root.deleteRecursively()
    }

    @Test
    fun aTruncatedBackupIsRejected() {
        addNote("note", "body")
        val file = writeBackup()
        val truncated = file.readBytes().copyOf(file.length().toInt() / 2)
        file.writeBytes(truncated)
        val fresh = VaultPaths(Files.createTempDirectory("vault-restored").toFile())
        assertThrows(Exception::class.java) { VaultBackup.restore(file, fresh, recoverySecret) }
        fresh.root.deleteRecursively()
    }

    @Test
    fun aTamperedTrailerIsRejected() {
        addNote("note", "body")
        val file = writeBackup()
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        file.writeBytes(bytes)
        val fresh = VaultPaths(Files.createTempDirectory("vault-restored").toFile())
        assertThrows(VaultCryptoException::class.java) {
            VaultBackup.restore(file, fresh, recoverySecret)
        }
        fresh.root.deleteRecursively()
    }

    @Test
    fun aMissingPayloadAbortsTheBackupInsteadOfReportingSuccess() {
        val item = addImage("bytes".toByteArray(), "photo.jpg")
        paths.objectFile(item.id).delete()
        assertThrows(Exception::class.java) { writeBackup() }
    }

    @Test
    fun headerDescribesTheBackupWithoutRevealingContent() {
        addImage(ByteArray(2048), "family.jpg")
        addNote("note", "body")
        val file = writeBackup()
        val info = file.inputStream().use { VaultBackup.inspect(it) }
        assertEquals(vaultId, info.vaultId)
        assertEquals(2, info.itemCount)
        assertEquals(VaultKeyring.RECOVERY_SCHEME, info.recoveryWrapper.scheme)
        // The header carries a real recovery wrapper: the recovery key unwraps the
        // very master key that protects this vault.
        assertArrayEquals(
            masterKey,
            VaultKeyring.unwrapWithRecovery(info.recoveryWrapper, recoverySecret, vaultId)
        )
    }

    @Test
    fun emptyVaultStillProducesARestorableBackup() {
        metadata.commit(VaultIndex.empty(vaultId))
        val file = writeBackup()
        val fresh = VaultPaths(Files.createTempDirectory("vault-restored").toFile())
        val restored = VaultBackup.restore(file, fresh, recoverySecret)
        assertEquals(0, restored.itemCount)
        assertTrue(fresh.keyRing.isFile)
        fresh.root.deleteRecursively()
    }

    // ------------------------------------------------------------------

    private fun writeBackup(): File {
        val target = File(root.parentFile, "backup-${VaultCrypto.randomBytes(4).joinToString("") { "%02x".format(it) }}.lvb")
        VaultKeyring.wrapWithRecovery(masterKey, recoverySecret, vaultId).let { wrapper ->
            val referenced = metadata.read(vaultId)?.items?.map { it.id }?.toSet() ?: emptySet()
            target.outputStream().use { out ->
                VaultBackup.write(paths, vaultId, wrapper, out, requiredObjectIds = referenced)
            }
        }
        target.deleteOnExit()
        return target
    }

    private fun addImage(payload: ByteArray, name: String): VaultItem {
        val stored = objects.store(ByteArrayInputStream(payload))
        val item = VaultItem(
            id = stored.id,
            kind = VaultItemKind.IMAGE,
            name = name,
            mimeType = "image/jpeg",
            sizeBytes = stored.sizeBytes,
            addedAtMs = System.currentTimeMillis(),
            capturedAtMs = System.currentTimeMillis(),
            folderId = null,
            dekIv = stored.dekIv,
            wrappedDek = stored.wrappedDek
        )
        commit(item)
        return item
    }

    private fun addNote(title: String, body: String): VaultItem {
        val stored = objects.store(ByteArrayInputStream(body.toByteArray()))
        val item = VaultItem(
            id = stored.id,
            kind = VaultItemKind.NOTE,
            name = title,
            mimeType = "text/plain",
            sizeBytes = stored.sizeBytes,
            addedAtMs = System.currentTimeMillis(),
            capturedAtMs = System.currentTimeMillis(),
            folderId = null,
            dekIv = stored.dekIv,
            wrappedDek = stored.wrappedDek
        )
        commit(item)
        return item
    }

    private fun commit(item: VaultItem) {
        val current = metadata.read(vaultId) ?: VaultIndex.empty(vaultId)
        metadata.commit(current.withItem(item))
    }

    private fun readAll(store: VaultObjectStore, item: VaultItem): ByteArray =
        ByteArrayOutputStream().use { out ->
            store.copyTo(item, out)
            out.toByteArray()
        }
}
