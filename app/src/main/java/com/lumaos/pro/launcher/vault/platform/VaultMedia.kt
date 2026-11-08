package com.lumaos.pro.launcher.vault.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.util.LruCache
import com.lumaos.pro.launcher.vault.core.VaultFiles
import com.lumaos.pro.launcher.vault.core.VaultItem
import com.lumaos.pro.launcher.vault.core.VaultObjectReader
import com.lumaos.pro.launcher.vault.core.VaultObjectStore
import com.lumaos.pro.launcher.vault.core.VaultPaths
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * In-memory vault media.
 *
 * Decoded bitmaps and thumbnails exist **only in RAM**: nothing decoded is ever
 * written to the cache directory, to a thumbnail folder, or to MediaStore. The
 * cache is bounded (a fraction of the heap, so a launcher with a 64 MB budget does
 * not thrash) and is emptied on every lock, which is what makes "clear sensitive
 * visual state when the vault locks" true rather than aspirational.
 */
object VaultMediaCache {

    private val budgetBytes: Int = run {
        val heap = Runtime.getRuntime().maxMemory()
        min(24L * 1024 * 1024, max(4L * 1024 * 1024, heap / 10)).toInt()
    }

    private val cache = object : LruCache<String, Bitmap>(budgetBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount

        // Deliberately no recycle() here: an ImageView may still be drawing the
        // bitmap being evicted, and recycling it underneath would crash the grid.
        // Dropping the reference lets the collector take the pixels, and `clear()`
        // on lock drops them all at once.
    }

    /** Pixel dimensions without decoding the full image. */
    fun boundsOf(store: VaultObjectStore, item: VaultItem): Pair<Int, Int> {
        return try {
            store.open(item).use { reader ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                reader.asInputStream().use { BitmapFactory.decodeStream(it, null, options) }
                options.outWidth to options.outHeight
            }
        } catch (e: Exception) {
            0 to 0
        }
    }

    /**
     * Returns a cached or freshly decoded bitmap, downsampled to at most
     * [maxPixels] on the long edge.
     */
    fun bitmap(store: VaultObjectStore, item: VaultItem, maxPixels: Int): Bitmap? {
        val key = "${item.id}:${item.sizeBytes}:$maxPixels"
        cache.get(key)?.takeIf { !it.isRecycled }?.let { return it }
        return try {
            store.open(item).use { reader ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                reader.asInputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
                val longest = max(bounds.outWidth, bounds.outHeight)
                if (longest <= 0) return null
                var sample = 1
                while (longest / (sample * 2) >= maxPixels) sample *= 2
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = reader.asInputStream().use { BitmapFactory.decodeStream(it, null, options) }
                if (decoded == null) {
                    null
                } else {
                    cache.put(key, decoded)
                    decoded
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun evict(itemId: String) {
        cache.snapshot().keys.filter { it.startsWith("$itemId:") }.forEach { cache.remove(it) }
    }

    /** Called on lock: every decoded pixel of private content is dropped. */
    fun clear() {
        cache.evictAll()
    }

    fun sizeBytes(): Int = cache.size()
}

/**
 * Short-lived plaintext files.
 *
 * Almost nothing in Vault needs one - images, audio, video, notes and PDFs are
 * all decrypted in memory or through a seekable decrypting reader. The single
 * exception is `PdfRenderer`, which requires a real, seekable file descriptor.
 * When a scanned document is opened, its plaintext is written to a file inside
 * the vault's own staging directory, shredded the moment the viewer closes, and
 * shredded again on lock or on the next unlock if the process died in between.
 *
 * This is a documented, deliberate compromise rather than a silent one: the file
 * never leaves app-private storage, it is never indexed, and it exists for the
 * duration of one screen.
 */
object VaultTransientFiles {

    fun createForPdf(paths: VaultPaths): File {
        paths.ensureDirectories()
        return paths.stagingFile(".pdf")
    }

    fun purge(paths: VaultPaths) {
        paths.staging.listFiles()?.forEach { VaultFiles.shred(it) }
    }
}

/**
 * Files handed to platform encoders.
 *
 * `MediaRecorder`, `Camera2`'s video path and the document scanner's intermediate
 * images all need somewhere to write while they are running. That place is the
 * vault's staging directory, never `DCIM` or `Pictures`: no capture exists
 * anywhere the rest of the phone can see it, and it is encrypted and shredded as
 * soon as the capture finishes.
 */
object VaultCapture {

    fun scratch(paths: VaultPaths, suffix: String): File {
        paths.ensureDirectories()
        // Old scratch files can only be leftovers from a killed process.
        cleanupStale(paths)
        return paths.stagingFile(suffix)
    }

    /**
     * Removes staging files left by an interrupted capture.
     *
     * Called at capture start and at unlock; a camera crash, a process kill or a
     * dead battery therefore leaves at most one stale scratch file, never a
     * half-written vault object.
     */
    fun cleanupStale(paths: VaultPaths, olderThanMs: Long = 60_000L): Int {
        val cutoff = System.currentTimeMillis() - olderThanMs
        val files = paths.staging.listFiles() ?: return 0
        var removed = 0
        for (file in files) {
            if (file.lastModified() < cutoff) {
                VaultFiles.shred(file)
                removed++
            }
        }
        return removed
    }
}

/**
 * A `MediaDataSource` over an encrypted vault object.
 *
 * This is what lets the platform play private audio and video without ever
 * holding a decrypted copy on disk: `MediaPlayer` seeks, the reader decrypts and
 * authenticates just the chunk under the playhead, and the data key is zeroized
 * when playback ends.
 */
class VaultMediaDataSource(private val reader: VaultObjectReader) : MediaDataSource() {

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
        reader.readAt(position, buffer, offset, size)

    override fun getSize(): Long = reader.length

    override fun close() {
        reader.close()
    }
}
