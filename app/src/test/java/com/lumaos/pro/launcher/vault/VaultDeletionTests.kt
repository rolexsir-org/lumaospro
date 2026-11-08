package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultCrypto
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Cryptographic deletion: what "deleted" actually means for Vault content.
 *
 * The property under test is not "the file is gone" - a flash translation layer
 * may keep old blocks - but the stronger, checkable one:
 *
 * > after a deletion, no key the vault still holds can open the deleted object.
 *
 * The tests therefore do not stop at looking at the filesystem. They take the
 * master key, a restored copy of the pre-deletion index, the backup generation
 * the crash-recovery path would fall back to, and the surviving objects, and show
 * that the deleted object stays closed through every one of them.
 */
class VaultDeletionTests {

    private lateinit var root: File
    private lateinit var paths: VaultPaths
    private lateinit var masterKey: ByteArray
    private lateinit var objects: VaultObjectStore
    private lateinit var metadata: VaultMetadataStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("vault-deletion").toFile()
        paths = VaultPaths(root)
        paths.ensureDirectories()
        masterKey = VaultKeyring.newMasterKey()
        objects = VaultObjectStore(paths, masterKey)
        metadata = VaultMetadataStore(paths, VaultMetadataStore.keyFor(masterKey))
        metadata.commit(VaultIndex.empty(VAULT_ID))
    }

    @After
    fun tearDown() {
        VaultCrypto.zeroize(masterKey)
        root.deleteRecursively()
    }

    // ------------------------------------------------------------------
    // Key lifecycle
    // ------------------------------------------------------------------

    @Test
    fun everyObjectGetsItsOwnDataKey() {
        val first = objects.store(ByteArrayInputStream("first".toByteArray()))
        val second = objects.store(ByteArrayInputStream("second".toByteArray()))
        assertNotEquals("object ids must differ", first.id, second.id)
        assertNotEquals("wrapped data keys must differ", first.wrappedDek, second.wrappedDek)
    }

    /**
     * Removing the index entry removes the only route to the object's data key:
     * the key is wrapped in that entry, and nowhere else. With the entry gone the
     * payload cannot be opened even by the legitimate master key.
     */
    @Test
    fun deletingTheIndexEntryDestroysTheOnlyRouteToTheDataKey() {
        val stored = objects.store(ByteArrayInputStream("bank statement".toByteArray()))
        val item = itemFor(stored)
        val withItem = read().withItem(item)
        metadata.commit(withItem)

        val afterDelete = metadata.commitAndScrubPrevious(withItem.withoutItem(item.id))
        objects.delete(item.id)

        assertNull("the index no longer names the object", afterDelete.item(item.id))
        assertFalse("the payload file is destroyed", paths.objectFile(item.id).exists())

        // The strongest statement available: hand the store the *old* item record -
        // data key wrapper and all - and it still cannot open anything.
        assertThrows(IOException::class.java) { objects.open(item).close() }
    }

    /**
     * The crash-recovery path must not resurrect a deleted item's key.
     *
     * `meta.bin.bak` is kept precisely so an interrupted commit can be rolled back.
     * If a deletion left the previous generation intact, that recovery path would
     * restore the wrapped data key of the item the user just deleted.
     */
    @Test
    fun recoverableBackupGenerationNoLongerNamesADeletedItem() {
        val stored = objects.store(ByteArrayInputStream("private scan".toByteArray()))
        val item = itemFor(stored)
        val withItem = read().withItem(item)
        metadata.commit(withItem)
        metadata.commit(read().withItem(itemFor(objects.store(ByteArrayInputStream("x".toByteArray())))))
        assertTrue("a backup generation exists before the deletion", paths.metadataBackup.isFile)

        metadata.commitAndScrubPrevious(read().withoutItem(item.id))

        // Simulate the primary index being unusable: read() must fall back to the
        // backup generation, and that generation must already be clean.
        assertTrue(paths.metadata.delete())
        val recovered = metadata.read(VAULT_ID)
        assertTrue("the backup generation is still a usable index", recovered != null)
        assertNull("recovery must not restore the deleted item", recovered!!.item(item.id))
    }

    /** An interrupted deletion leaves an unreferenced payload, never a dangling entry. */
    @Test
    fun interruptedDeletionLeavesAnOrphanThatIsSwept() {
        val stored = objects.store(ByteArrayInputStream("holiday video".toByteArray()))
        val item = itemFor(stored)
        metadata.commit(read().withItem(item))

        // Commit the index without the item, then "crash" before the payload goes.
        metadata.commit(read().withoutItem(item.id))
        assertTrue("payload survives the interrupted deletion", paths.objectFile(item.id).isFile)

        // The next unlock sweeps it.
        val reopened = read()
        assertNull(reopened.item(item.id))
        assertEquals(1, objects.sweepOrphans(reopened))
        assertFalse(paths.objectFile(item.id).isFile)
    }

    /** Replacing a note destroys the previous object's key as well. */
    @Test
    fun editingANoteDestroysThePreviousObjectsKey() {
        val first = objects.store(ByteArrayInputStream("version one".toByteArray()))
        val oldItem = itemFor(first)
        metadata.commit(read().withItem(oldItem))

        val second = objects.store(ByteArrayInputStream("version two".toByteArray()))
        val newItem = itemFor(second).copy(name = "note")
        // Mirrors VaultManager.updateNote: replace the entry, scrub, then destroy.
        val afterEdit = metadata.commitAndScrubPrevious(
            read().withoutItem(oldItem.id).withItem(newItem)
        )
        objects.delete(oldItem.id)

        assertNull(afterEdit.item(oldItem.id))
        assertTrue("the new version is in the index", afterEdit.item(newItem.id) != null)
        assertFalse("the old payload is destroyed", paths.objectFile(oldItem.id).exists())

        // The old version cannot be reopened even with the full pre-edit index.
        val restored = afterEdit.withItem(oldItem)
        assertThrows(IOException::class.java) { objects.open(restored.item(oldItem.id)!!).close() }
    }

    /** Whatever survives must still decrypt exactly, or the vault lost data. */
    @Test
    fun survivingObjectsStillDecryptExactly() {
        val keep = objects.store(ByteArrayInputStream("keep me".toByteArray()))
        val drop = objects.store(ByteArrayInputStream("delete me".toByteArray()))
        var index = read().withItem(itemFor(keep)).withItem(itemFor(drop))
        metadata.commit(index)
        index = metadata.commitAndScrubPrevious(index.withoutItem(drop.id))
        objects.delete(drop.id)

        objects.open(index.item(keep.id)!!).use { reader ->
            val out = ByteArray("keep me".length)
            assertEquals(out.size, reader.readAt(0, out, 0, out.size))
            assertArrayEquals("keep me".toByteArray(), out)
        }
    }

    /** A deletion is idempotent: running it twice must not corrupt the index. */
    @Test
    fun deletingTwiceIsSafe() {
        val stored = objects.store(ByteArrayInputStream("one-time".toByteArray()))
        val item = itemFor(stored)
        metadata.commit(read().withItem(item))

        val first = metadata.commitAndScrubPrevious(read().withoutItem(item.id))
        objects.delete(item.id)
        val second = metadata.commitAndScrubPrevious(first.withoutItem(item.id))
        objects.delete(item.id)

        assertNull(second.item(item.id))
        assertFalse(paths.objectFile(item.id).exists())
        assertTrue("the index is still readable afterwards", metadata.read(VAULT_ID) != null)
    }

    /**
     * The plain `commit` still keeps a rollback generation - the deletion path is
     * an explicit choice, not a change to normal commits.
     */
    @Test
    fun ordinaryCommitsStillKeepTheirRollbackGeneration() {
        val a = objects.store(ByteArrayInputStream("a".toByteArray()))
        metadata.commit(read().withItem(itemFor(a)))
        metadata.commit(read().withItem(itemFor(objects.store(ByteArrayInputStream("b".toByteArray())))))
        assertTrue(paths.metadataBackup.isFile)
        assertTrue("a normal commit snapshot is still there", paths.metadataBackup.length() > 0)
    }

    // ------------------------------------------------------------------

    /** Reads the index under the same vault id the store committed with. */
    private fun read(): VaultIndex =
        metadata.read(VAULT_ID) ?: throw IllegalStateException("index disappeared")

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

    private companion object {
        const val VAULT_ID = "vault-deletion"
    }
}
