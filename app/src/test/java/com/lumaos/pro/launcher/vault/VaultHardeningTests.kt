package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultBackup
import com.lumaos.pro.launcher.vault.core.VaultCrypto
import com.lumaos.pro.launcher.vault.core.VaultCryptoException
import com.lumaos.pro.launcher.vault.core.VaultIndex
import com.lumaos.pro.launcher.vault.core.VaultItem
import com.lumaos.pro.launcher.vault.core.VaultItemKind
import com.lumaos.pro.launcher.vault.core.VaultKeyFile
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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Hardening tests added during the pre-release security review.
 *
 * Everything here targets a *hostile input* or a *failure mid-flight*, because
 * those are the cases that separate a demo from a product: a backup file is
 * attacker-supplied data, a key ring is unauthenticated metadata that is read
 * before any secret is known, and a recovery key is typed by a human under stress.
 */
class VaultHardeningTests {

    private lateinit var root: File
    private lateinit var paths: VaultPaths
    private lateinit var masterKey: ByteArray
    private lateinit var objects: VaultObjectStore
    private lateinit var metadata: VaultMetadataStore
    private lateinit var recoverySecret: ByteArray
    private val vaultId = "0123456789abcdef0123456789abcdef"
    private val pin = "48193027"

    @Before
    fun setUp() {
        root = Files.createTempDirectory("vault-hardening").toFile()
        paths = VaultPaths(root)
        paths.ensureDirectories()
        masterKey = VaultKeyring.newMasterKey()
        objects = VaultObjectStore(paths, masterKey)
        metadata = VaultMetadataStore(paths, VaultMetadataStore.keyFor(masterKey))
        recoverySecret = VaultRecoveryCode.generate()
        metadata.commit(VaultIndex.empty(vaultId))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    // ------------------------------------------------------------------
    // Key ring: unbounded work is a pre-authentication attack surface
    // ------------------------------------------------------------------

    @Test
    fun keyRingRejectsAnAbsurdIterationCountInsteadOfHanging() {
        val wrapper = VaultKeyring.wrapWithPin(masterKey, pin.toCharArray(), vaultId, iterations = 210_000)
        // A key ring is plain JSON next to the ciphertext: anyone who can copy the
        // vault directory can edit it. Claiming four billion iterations must be
        // rejected, not obeyed for hours on a lock screen.
        val forged = wrapper.copy(iterations = 4_000_000_000.toInt())
        assertThrows(IllegalArgumentException::class.java) {
            VaultKeyring.unwrapWithPin(forged, pin.toCharArray(), vaultId)
        }
    }

    @Test
    fun keyRingRejectsADowngradedIterationCount() {
        val wrapper = VaultKeyring.wrapWithPin(masterKey, pin.toCharArray(), vaultId, iterations = 210_000)
        val forged = wrapper.copy(iterations = 1_000)
        assertThrows(IllegalArgumentException::class.java) {
            VaultKeyring.unwrapWithPin(forged, pin.toCharArray(), vaultId)
        }
    }

    @Test
    fun keyRingJsonRoundTripPreservesTheCeilingCheck() {
        val keyFile = VaultKeyFile(
            vaultId = vaultId,
            createdAtMs = 1_000L,
            pin = VaultKeyring.wrapWithPin(masterKey, pin.toCharArray(), vaultId, iterations = 210_000)
                .copy(iterations = 9_000_000),
            recovery = null,
            biometric = null
        )
        val reloaded = VaultKeyring.fromJson(VaultKeyring.toJson(keyFile))
        assertThrows(IllegalArgumentException::class.java) {
            VaultKeyring.unwrapWithPin(reloaded.pin!!, pin.toCharArray(), vaultId)
        }
    }

    // ------------------------------------------------------------------
    // Recovery key transcription
    // ------------------------------------------------------------------

    @Test
    fun everyRecoveryCodeSurvivesHumanAliasTranscription() {
        // Regression: the checksum used to be compared character by character, so a
        // correctly transcribed key whose *checksum* contained a 1 or a 0 was
        // rejected when the user wrote l or O - roughly one key in eight. That is
        // the worst possible time to tell someone their only way back in is wrong.
        repeat(400) {
            val secret = VaultRecoveryCode.generate()
            val sloppy = VaultRecoveryCode.format(secret)
                .replace('0', 'O')
                .replace('1', 'l')
                .replace("-", "")
                .lowercase()
            assertArrayEquals(secret, VaultRecoveryCode.parse(sloppy)!!)
        }
    }

    @Test
    fun recoveryCodeStillRejectsAMistypedCharacter() {
        val secret = VaultRecoveryCode.generate()
        val text = VaultRecoveryCode.format(secret).replace("-", "")
        // Change one body character to a different, valid alphabet character.
        val index = 10
        val original = text[index]
        val replacement = if (original == 'A') 'B' else 'A'
        val mistyped = text.substring(0, index) + replacement + text.substring(index + 1)
        // A ten-bit checksum catches almost every single substitution; on the rare
        // collision the only requirement is that it never yields the original key.
        val parsed = VaultRecoveryCode.parse(mistyped)
        assertTrue("a mistyped character must not decode to the original key",
            parsed == null || !parsed.contentEquals(secret))
    }

    @Test
    fun recoveryCodeRejectsCharactersOutsideCrockford() {
        val secret = VaultRecoveryCode.generate()
        val text = VaultRecoveryCode.format(secret).replace("-", "")
        // U is excluded from the alphabet on purpose; it must not decode.
        assertNull(VaultRecoveryCode.parse("U" + text.substring(1)))
    }

    // ------------------------------------------------------------------
    // Backup container verification (used before anything is uploaded)
    // ------------------------------------------------------------------

    @Test
    fun writeToFileStagesAVerifiedContainer() {
        addNote("note", "body text")
        val target = File(root.parentFile, "staged-${System.nanoTime()}.lvb")
        val written = VaultBackup.writeToFile(
            paths = paths,
            vaultId = vaultId,
            recoveryWrapper = VaultKeyring.wrapWithRecovery(masterKey, recoverySecret, vaultId),
            requiredObjectIds = metadata.read(vaultId)!!.items.map { it.id }.toSet(),
            file = target
        )
        assertEquals(target.length(), written)
        assertTrue("a container written by Vault must verify", VaultBackup.verifyFile(target))
        target.delete()
    }

    @Test
    fun verifyFileRejectsTruncationAndTampering() {
        addNote("note", "body text")
        val target = File(root.parentFile, "verified-${System.nanoTime()}.lvb")
        VaultBackup.writeToFile(
            paths = paths,
            vaultId = vaultId,
            recoveryWrapper = VaultKeyring.wrapWithRecovery(masterKey, recoverySecret, vaultId),
            requiredObjectIds = metadata.read(vaultId)!!.items.map { it.id }.toSet(),
            file = target
        )
        val good = target.readBytes()
        assertTrue(VaultBackup.verifyFile(target))

        target.writeBytes(good.copyOf(good.size / 2))
        assertFalse("a half-written backup must never verify", VaultBackup.verifyFile(target))

        val flipped = good.copyOf()
        flipped[flipped.size / 3] = (flipped[flipped.size / 3].toInt() xor 0x01).toByte()
        target.writeBytes(flipped)
        assertFalse(VaultBackup.verifyFile(target))
        target.delete()
    }

    // ------------------------------------------------------------------
    // Restore: hostile containers
    // ------------------------------------------------------------------

    @Test
    fun restoreRejectsObjectIdsThatAreNotCanonical() {
        addNote("note", "body text")
        val container = Container.parse(writeBackup())
        val record = container.records.first { it.type == TYPE_OBJECT }
        record.id = "../../../../../../../../../../etc".take(32).toByteArray(StandardCharsets.UTF_8)
        val hostile = container.write(File(root.parentFile, "hostile-id-${System.nanoTime()}.lvb"))
        val fresh = VaultPaths(Files.createTempDirectory("vault-hostile").toFile())
        assertThrows(VaultCryptoException::class.java) {
            VaultBackup.restore(hostile, fresh, recoverySecret)
        }
        // Nothing may have been written outside the objects directory.
        assertEquals(0, fresh.objects.listFiles()?.size ?: 0)
        hostile.delete()
        fresh.root.deleteRecursively()
    }

    @Test
    fun restoreRejectsAnOversizedIdLengthBeforeAllocating() {
        addNote("note", "body text")
        val container = Container.parse(writeBackup())
        container.records.first { it.type == TYPE_OBJECT }.idLengthOverride = Int.MAX_VALUE - 1
        val hostile = container.write(File(root.parentFile, "hostile-len-${System.nanoTime()}.lvb"))
        val fresh = VaultPaths(Files.createTempDirectory("vault-hostile").toFile())
        val started = System.nanoTime()
        assertThrows(VaultCryptoException::class.java) {
            VaultBackup.restore(hostile, fresh, recoverySecret)
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("a 2 GB id must be rejected immediately, took ${elapsedMs}ms", elapsedMs < 5_000)
        hostile.delete()
        fresh.root.deleteRecursively()
    }

    @Test
    fun restoreRejectsAnOversizedIndexRecord() {
        addNote("note", "body text")
        val container = Container.parse(writeBackup())
        container.records.first { it.type == TYPE_METADATA }.lengthOverride =
            VaultBackup.MAX_INDEX_RECORD_BYTES + 1
        val hostile = container.write(File(root.parentFile, "hostile-index-${System.nanoTime()}.lvb"))
        val fresh = VaultPaths(Files.createTempDirectory("vault-hostile").toFile())
        assertThrows(VaultCryptoException::class.java) {
            VaultBackup.restore(hostile, fresh, recoverySecret)
        }
        hostile.delete()
        fresh.root.deleteRecursively()
    }

    @Test
    fun restoreRejectsAContainerThatOmitsAnObject() {
        addNote("first", "one")
        addNote("second", "two")
        val container = Container.parse(writeBackup())
        container.records.removeAll { it.type == TYPE_OBJECT }.let { removed ->
            assertTrue("the fixture must contain objects", removed)
        }
        container.records.add(Container.Record(TYPE_OBJECT, "00".repeat(16).toByteArray(), ByteArray(0)))
        val incomplete = container.write(File(root.parentFile, "incomplete-${System.nanoTime()}.lvb"))
        val fresh = VaultPaths(Files.createTempDirectory("vault-hostile").toFile())
        // Internally consistent digest, but the index references objects that are
        // not in the file: restoring it would hand the user an empty-looking vault.
        assertThrows(VaultCryptoException::class.java) {
            VaultBackup.restore(incomplete, fresh, recoverySecret)
        }
        incomplete.delete()
        fresh.root.deleteRecursively()
    }

    @Test
    fun aFailedRestoreLeavesTheExistingVaultUntouched() {
        addNote("existing", "keep me")
        val before = snapshot(paths.root)
        val corrupt = File(root.parentFile, "corrupt-${System.nanoTime()}.lvb")
        corrupt.writeBytes(ByteArray(4096) { (it % 251).toByte() })
        val fresh = VaultPaths(Files.createTempDirectory("vault-stage").toFile())
        assertThrows(Exception::class.java) { VaultBackup.restore(corrupt, fresh, recoverySecret) }
        assertEquals("staged restore must not touch the live vault", before, snapshot(paths.root))
        corrupt.delete()
        fresh.root.deleteRecursively()
    }

    // ------------------------------------------------------------------
    // Storage hygiene
    // ------------------------------------------------------------------

    @Test
    fun objectFileRefusesATraversingIdentifier() {
        assertThrows(IllegalArgumentException::class.java) { paths.objectFile("../keyring") }
        assertThrows(IllegalArgumentException::class.java) { paths.objectFile("..") }
        assertThrows(IllegalArgumentException::class.java) { paths.objectFile("ABCDEF0123456789ABCDEF0123456789") }
    }

    @Test
    fun stagingIsClearedAndLeftEmpty() {
        val scratch = paths.stagingFile(".m4a")
        scratch.writeBytes(ByteArray(1024) { 7 })
        assertTrue(scratch.isFile)
        paths.clearStaging()
        assertFalse("recorder scratch must not survive a lock", scratch.exists())
        assertEquals(0, paths.staging.listFiles()?.size ?: 0)
    }

    @Test
    fun duplicateContentGetsIndependentKeysAndCiphertext() {
        val payload = "identical bytes".toByteArray()
        val one = objects.store(ByteArrayInputStream(payload))
        val two = objects.store(ByteArrayInputStream(payload))
        assertTrue(one.id != two.id)
        assertFalse("per-object keys must not repeat", one.wrappedDek == two.wrappedDek)
        val a = paths.objectFile(one.id).readBytes()
        val b = paths.objectFile(two.id).readBytes()
        assertFalse("identical plaintext must not produce identical ciphertext", a.contentEquals(b))
    }

    /**
     * Several threads storing into one vault at the same time - the recorder
     * finishing while a capture is written, for instance. Each object is a separate
     * file, so this must be safe; if it ever stops being safe, this test says so.
     */
    @Test
    fun concurrentStoresRemainReadable() {
        val count = 4
        val latch = CountDownLatch(count)
        val results = java.util.Collections.synchronizedList(ArrayList<Pair<VaultObjectStore.Stored, ByteArray>>())
        val errors = java.util.Collections.synchronizedList(ArrayList<Throwable>())
        val threads = (0 until count).map { index ->
            Thread {
                try {
                    val payload = ByteArray(64 * 1024) { (it + index).toByte() }
                    results.add(objects.store(ByteArrayInputStream(payload)) to payload)
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    latch.countDown()
                }
            }.also { it.start() }
        }
        latch.await(60, TimeUnit.SECONDS)
        threads.forEach { it.join() }
        assertTrue("concurrent stores threw: ${errors.firstOrNull()}", errors.isEmpty())
        assertEquals(count, results.size)
        for ((stored, payload) in results) {
            assertTrue(stored.id.matches(Regex("^[0-9a-f]{32}$")))
            val item = VaultItem(
                id = stored.id,
                kind = VaultItemKind.OTHER,
                name = "concurrent.bin",
                mimeType = "application/octet-stream",
                sizeBytes = stored.sizeBytes,
                addedAtMs = 0L,
                capturedAtMs = 0L,
                folderId = null,
                dekIv = stored.dekIv,
                wrappedDek = stored.wrappedDek
            )
            val read = ByteArrayOutputStream().use { out ->
                objects.copyTo(item, out)
                out.toByteArray()
            }
            assertArrayEquals("each concurrent write must decrypt back exactly", payload, read)
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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
        val current = metadata.read(vaultId) ?: VaultIndex.empty(vaultId)
        metadata.commit(current.withItem(item))
        return item
    }

    private fun writeBackup(): File {
        val target = File(root.parentFile, "backup-${System.nanoTime()}.lvb")
        val wrapper = VaultKeyring.wrapWithRecovery(masterKey, recoverySecret, vaultId)
        val referenced = metadata.read(vaultId)?.items?.map { it.id }?.toSet() ?: emptySet()
        target.outputStream().use { out ->
            VaultBackup.write(paths, vaultId, wrapper, out, requiredObjectIds = referenced)
        }
        return target
    }

    private fun snapshot(directory: File): Map<String, String> =
        (directory.walkTopDown().filter { it.isFile }.associate {
            it.relativeTo(directory).path to it.readBytes().let { bytes ->
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { b -> "%02x".format(b) }
            }
        }).toSortedMap()

    /**
     * A backup container taken apart so a test can rebuild it with hostile framing.
     *
     * This mirrors the on-disk format deliberately: the test is asserting what the
     * reader does with a *file it did not write*, which is exactly the situation a
     * malicious backup creates.
     */
    private class Container(
        private val prefix: ByteArray,
        val records: MutableList<Record>
    ) {

        class Record(
            val type: Int,
            var id: ByteArray,
            var payload: ByteArray
        ) {
            var idLengthOverride: Int? = null
            var lengthOverride: Long? = null
        }

        fun write(file: File): File {
            val body = ByteArrayOutputStream()
            for (record in records) {
                body.write(record.type)
                val idLength = record.idLengthOverride ?: record.id.size
                body.write(intBytes(idLength))
                if (record.idLengthOverride == null) body.write(record.id)
                body.write(longBytes(record.lengthOverride ?: record.payload.size.toLong()))
                if (record.lengthOverride == null) body.write(record.payload)
            }
            val bodyBytes = body.toByteArray()
            val head = ByteArrayOutputStream()
            head.write(prefix)
            head.write(longBytes(bodyBytes.size.toLong()))
            head.write(bodyBytes)
            val digest = MessageDigest.getInstance("SHA-256").digest(head.toByteArray())
            head.write(digest)
            file.writeBytes(head.toByteArray())
            return file
        }

        companion object {
            fun parse(file: File): Container {
                val bytes = file.readBytes()
                var cursor = 0
                require(String(bytes, 0, 4, StandardCharsets.US_ASCII) == VaultBackup.MAGIC)
                cursor += 4                       // magic
                cursor += 1                       // version
                cursor += 1                       // flags
                val saltLength = bytes[cursor].toInt() and 0xFF
                cursor += 1 + saltLength
                val headerLength = readInt(bytes, cursor)
                cursor += 4 + headerLength
                // `prefix` is everything before the 8-byte body length field.
                val prefix = bytes.copyOfRange(0, cursor)
                val bodyLength = readLong(bytes, cursor)
                cursor += 8
                val end = cursor + bodyLength.toInt()
                val records = ArrayList<Record>()
                while (cursor < end) {
                    val type = bytes[cursor].toInt() and 0xFF
                    cursor += 1
                    val idLength = readInt(bytes, cursor)
                    cursor += 4
                    val id = bytes.copyOfRange(cursor, cursor + idLength)
                    cursor += idLength
                    val length = readLong(bytes, cursor)
                    cursor += 8
                    val payload = bytes.copyOfRange(cursor, cursor + length.toInt())
                    cursor += length.toInt()
                    records.add(Record(type, id, payload))
                }
                return Container(prefix, records)
            }

            private fun readInt(bytes: ByteArray, offset: Int): Int =
                ((bytes[offset].toInt() and 0xFF) shl 24) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 3].toInt() and 0xFF)

            private fun readLong(bytes: ByteArray, offset: Int): Long {
                var value = 0L
                for (index in 0 until 8) {
                    value = (value shl 8) or (bytes[offset + index].toLong() and 0xFF)
                }
                return value
            }
        }
    }

    private companion object {
        const val TYPE_METADATA = 1
        const val TYPE_OBJECT = 2

        fun intBytes(value: Int): ByteArray = byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        )

        fun longBytes(value: Long): ByteArray = ByteArray(8) { index ->
            (value ushr ((7 - index) * 8)).toByte()
        }
    }
}
