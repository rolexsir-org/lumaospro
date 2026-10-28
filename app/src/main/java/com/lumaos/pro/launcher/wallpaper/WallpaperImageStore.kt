package com.lumaos.pro.launcher.wallpaper

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.ui.theme.WallpaperArtResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * Private, bounded copies; no broad photo permission or long-lived document grant.
 * Android backup rules deliberately do not include this directory. Never log a URI
 * or EXIF contents. A partially copied file is not published to the gallery.
 */
class WallpaperImageStore(private val context: Context) {
    private val dir get() = File(context.filesDir, "wallpaper_images")

    suspend fun entries(): List<WallpaperEntry> = withContext(Dispatchers.IO) {
        val photos = dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".img") && WallpaperCatalog.customId(it.name.removeSuffix(".img")) != null }
            .sortedByDescending { it.lastModified() }
            .mapIndexedNotNull { index, file ->
                val id = WallpaperCatalog.customId(file.name.removeSuffix(".img")) ?: return@mapIndexedNotNull null
                WallpaperEntry(id, context.getString(R.string.wallpaper_photo_number, index + 1), context.getString(R.string.wallpaper_private_photo))
            }
        WallpaperCatalog.bundled() + photos
    }

    suspend fun import(uri: Uri): WallpaperEntry = importLock.withLock {
        withContext(Dispatchers.IO) {
            // A previous process may have died mid-copy. The mutex serializes imports
            // from multiple Studio instances, so only abandoned temp files are removed.
            dir.listFiles().orEmpty().filter { it.name.startsWith(".import-") && it.name.endsWith(".tmp") }.forEach { it.delete() }
            if (dir.listFiles().orEmpty().count { it.isFile && it.name.endsWith(".img") } >= WallpaperLimits.MAX_IMPORTS) {
                throw WallpaperException(R.string.wallpaper_limit_count)
            }
            if (!dir.isDirectory && !dir.mkdirs()) throw WallpaperException(R.string.wallpaper_import_failed)
            val temp = File.createTempFile(".import-", ".tmp", dir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().buffered().use { output ->
                        val bytes = ByteArray(32 * 1024)
                        var total = 0L
                        var emptyReads = 0
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(bytes)
                            if (n < 0) break
                            if (n == 0) {
                                if (++emptyReads > 4) throw WallpaperException(R.string.wallpaper_import_failed)
                                continue
                            }
                            emptyReads = 0
                            total += n
                            if (total > WallpaperLimits.MAX_FILE_BYTES) throw WallpaperException(R.string.wallpaper_limit_size)
                            output.write(bytes, 0, n)
                        }
                    }
                } ?: throw WallpaperException(R.string.wallpaper_import_failed)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                temp.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
                if (!WallpaperLimits.validSource(bounds.outWidth, bounds.outHeight)) {
                    throw WallpaperException(R.string.wallpaper_bad_image)
                }
                // Also verify the encoded file can actually be decoded, not just its header.
                val options = BitmapFactory.Options().apply {
                    inSampleSize = WallpaperLimits.sampleSize(bounds.outWidth, bounds.outHeight, 128, 128L * 128)
                }
                val probe = temp.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
                    ?: throw WallpaperException(R.string.wallpaper_bad_image)
                probe.recycle()
                val name = UUID.randomUUID().toString()
                val destination = File(dir, "$name.img")
                if (!temp.renameTo(destination)) throw WallpaperException(R.string.wallpaper_import_failed)
                WallpaperEntry("photo:$name", context.getString(R.string.wallpaper_my_photo), context.getString(R.string.wallpaper_private_photo))
            } finally {
                temp.delete()
            }
        }
    }

    suspend fun delete(entry: WallpaperEntry): Boolean = withContext(Dispatchers.IO) {
        fileFor(entry)?.let { !it.exists() || it.delete() } ?: false
    }

    /** Decoding and optional rotation happen on IO, with an upper memory bound. */
    suspend fun preview(entry: WallpaperEntry, maxEdge: Int = 720): Bitmap? = withContext(Dispatchers.IO) {
        decode(entry, maxEdge, maxEdge.toLong() * maxEdge)
    }

    suspend fun colors(bitmap: Bitmap): List<Int> = withContext(Dispatchers.Default) {
        if (Build.VERSION.SDK_INT >= 27) {
            runCatching {
                val colors = WallpaperColors.fromBitmap(bitmap)
                listOfNotNull(colors.primaryColor.toArgb(), colors.secondaryColor?.toArgb(), colors.tertiaryColor?.toArgb())
            }.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(averageColor(bitmap))
        } else listOf(averageColor(bitmap))
    }

    suspend fun apply(entry: WallpaperEntry, target: WallpaperTarget): Unit = withContext(Dispatchers.IO) {
        val manager = WallpaperManager.getInstance(context)
        if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) {
            throw WallpaperException(R.string.wallpaper_not_allowed)
        }
        val bitmap = try { decode(entry, WallpaperLimits.MAX_APPLY_EDGE, WallpaperLimits.MAX_APPLY_PIXELS) }
            catch (_: OutOfMemoryError) { null }
            ?: throw WallpaperException(R.string.wallpaper_image_unavailable)
        try {
            val flags = when (target) {
                WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            // Custom photo copies and system-set custom wallpapers are not requested for backup.
            val id = manager.setBitmap(bitmap, null, !entry.isCustom, flags)
            if (id <= 0) throw WallpaperException(R.string.wallpaper_apply_failed)
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        private val importLock = Mutex()
    }

    private fun fileFor(entry: WallpaperEntry): File? =
        WallpaperCatalog.customName(entry.id)?.let { File(dir, "$it.img") }

    private fun open(entry: WallpaperEntry): InputStream? =
        if (entry.art != null) context.resources.openRawResource(WallpaperArtResources.res(entry.art))
        else fileFor(entry)?.takeIf { it.isFile }?.inputStream()

    private fun decode(entry: WallpaperEntry, maxEdge: Int, maxPixels: Long): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds Android deliberately returns a null Bitmap;
        // only a missing stream (not that null result) means the image is absent.
        val header = open(entry) ?: return null
        header.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (!WallpaperLimits.validSource(bounds.outWidth, bounds.outHeight)) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = WallpaperLimits.sampleSize(bounds.outWidth, bounds.outHeight, maxEdge, maxPixels)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = open(entry)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val orientation = fileFor(entry)?.let { file ->
            runCatching { ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        if (matrix.isIdentity) return bitmap
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } catch (t: Throwable) {
            bitmap.recycle()
            throw t
        }
    }

    private fun averageColor(bitmap: Bitmap): Int {
        // Sampling, not allocating another copy of a user photo.
        var r = 0L; var g = 0L; var b = 0L; var count = 0L
        val stepX = (bitmap.width / 32).coerceAtLeast(1)
        val stepY = (bitmap.height / 32).coerceAtLeast(1)
        for (y in 0 until bitmap.height step stepY) for (x in 0 until bitmap.width step stepX) {
            val c = bitmap.getPixel(x, y)
            r += (c ushr 16) and 255; g += (c ushr 8) and 255; b += c and 255; count++
        }
        return (0xff000000L or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)).toInt()
    }
}

enum class WallpaperTarget { HOME, LOCK, BOTH }
class WallpaperException(val messageRes: Int) : IOException()
