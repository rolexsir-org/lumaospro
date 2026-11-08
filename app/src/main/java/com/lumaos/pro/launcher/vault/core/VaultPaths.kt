package com.lumaos.pro.launcher.vault.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Physical layout of a vault.
 *
 * ```
 * <root>/                       noBackupFilesDir/vault  (never in an Android backup)
 *   keyring.json                wrapped copies of the master key (ciphertext only)
 *   guard.json                  failed-attempt counters and lockout deadlines
 *   meta.bin                    encrypted metadata index (names, kinds, settings)
 *   meta.bin.bak                previous generation, used if a commit was interrupted
 *   objects/<id>.obj            encrypted object payloads, opaque names
 *   staging/                    in-flight writes and capture scratch, always wiped
 * ```
 *
 * Every name in here is deliberately opaque: no original file name, extension or
 * folder structure is ever visible on the filesystem. Original names live only
 * inside the encrypted metadata index.
 */
class VaultPaths(val root: File) {

    val keyRing: File get() = File(root, VaultKeyring.FILE_NAME)
    val metadata: File get() = File(root, "meta.bin")
    val metadataBackup: File get() = File(root, "meta.bin.bak")
    val guard: File get() = File(root, "guard.json")
    val objects: File get() = File(root, "objects")
    val staging: File get() = File(root, "staging")

    /**
     * Path of one object payload.
     *
     * Ids are opaque 128-bit hex strings generated here. The guard is defence in
     * depth: even if a caller ever passed a name read out of a backup, no id can
     * escape `objects/`.
     */
    fun objectFile(id: String): File {
        require(OBJECT_ID.matches(id)) { "Object id is not a canonical identifier" }
        return File(objects, "$id.obj")
    }

    fun stagingFile(suffix: String = ".tmp"): File {
        ensureDirectories()
        return File(staging, VaultEncoding.newId() + suffix)
    }

    fun ensureDirectories() {
        if (!root.exists() && !root.mkdirs() && !root.isDirectory) {
            throw IOException("Vault directory could not be created")
        }
        for (dir in listOf(objects, staging)) {
            if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory) {
                throw IOException("Vault subdirectory could not be created")
            }
        }
    }

    /**
     * Removes every in-flight file; called at unlock and at lock.
     *
     * Staging can hold the only plaintext Vault ever writes (a PDF handed to the
     * system renderer, a recorder scratch file), so it is shredded rather than
     * simply unlinked. That is best effort - flash wear levelling can keep old
     * blocks - and nothing in the UI claims more than that.
     */
    fun clearStaging() {
        val children = staging.listFiles() ?: return
        for (child in children) {
            runCatching {
                if (child.isDirectory) VaultFiles.deleteRecursively(child) else VaultFiles.shred(child)
            }
        }
    }

    fun exists(): Boolean = keyRing.isFile

    companion object {
        private val OBJECT_ID = Regex("^[0-9a-f]{32}$")
    }

    /** Bytes currently occupied by encrypted objects. */
    fun objectBytes(): Long = objects.listFiles()?.sumOf { it.length() } ?: 0L
}

/**
 * Durable file helpers.
 *
 * Every write that matters is: write a sibling temp file, flush it to the
 * physical device, then rename over the destination. `renameTo` within a
 * directory is atomic on Android's filesystems, so a crash leaves either the old
 * file or the new one - never a half file.
 */
object VaultFiles {

    fun writeAtomically(target: File, write: (FileOutputStream) -> Unit) {
        val temp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(temp).use { out ->
            write(out)
            out.flush()
            out.fd.sync()
        }
        if (target.exists()) {
            // Keep one previous copy for the metadata index; harmless elsewhere.
            runCatching { target.copyTo(File(target.parentFile, target.name + ".bak"), overwrite = true) }
        }
        if (!temp.renameTo(target)) {
            // Some filesystems refuse to rename over an existing file.
            if (!target.delete() || !temp.renameTo(target)) {
                temp.delete()
                throw IOException("Could not commit ${target.name}")
            }
        }
    }

    fun writeBytesAtomically(target: File, bytes: ByteArray) =
        writeAtomically(target) { it.write(bytes) }

    fun readBytes(file: File): ByteArray = file.readBytes()

    /**
     * Best-effort shred of a plaintext scratch file: overwrite with zeros, sync,
     * then delete. Flash storage may retain the physical blocks, so this reduces
     * the window rather than guaranteeing erasure - which is stated plainly in
     * the Privacy notes instead of being claimed.
     */
    fun shred(file: File) {
        if (!file.isFile) {
            file.delete()
            return
        }
        runCatching {
            val length = file.length().coerceAtMost(8L * 1024 * 1024)
            if (length > 0) {
                FileOutputStream(file).use { out ->
                    val zeros = ByteArray(8 * 1024)
                    var written = 0L
                    while (written < length) {
                        val chunk = minOf(zeros.size.toLong(), length - written).toInt()
                        out.write(zeros, 0, chunk)
                        written += chunk
                    }
                    out.flush()
                    out.fd.sync()
                }
            }
        }
        runCatching { file.delete() }
    }

    /** Recursive delete used for staged restores and vault wipes. */
    fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        file.delete()
    }
}
