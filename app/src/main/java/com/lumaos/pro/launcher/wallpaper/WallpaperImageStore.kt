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
import android.util.LruCache
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.launcher.home.WallpaperBitmaps
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.WallpaperArtResources
import com.lumaos.pro.launcher.ui.theme.WallpaperPaletteExtractor
import com.lumaos.pro.launcher.ui.theme.WallpaperSeeds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
                WallpaperEntry(
                    id = id,
                    title = context.getString(R.string.wallpaper_photo_number, index + 1),
                    description = context.getString(R.string.wallpaper_private_photo),
                    mood = context.getString(R.string.wallpaper_private_photo),
                    precomputedSeeds = customSeedsCache[id]
                )
            }
        WallpaperCatalog.bundled() + photos
    }

    /** Resolves an active `art:...` or `photo:...` ID without scanning the full gallery when possible. */
    suspend fun findEntry(id: String?): WallpaperEntry? {
        if (id.isNullOrBlank()) return null
        WallpaperCatalog.findBundled(id)?.let { return it }
        if (!id.startsWith("photo:")) return null
        return withContext(Dispatchers.IO) {
            val name = WallpaperCatalog.customName(id) ?: return@withContext null
            val file = File(dir, "$name.img")
            if (!file.isFile) return@withContext null
            WallpaperEntry(
                id = id,
                title = context.getString(R.string.wallpaper_my_photo),
                description = context.getString(R.string.wallpaper_private_photo),
                mood = context.getString(R.string.wallpaper_private_photo),
                precomputedSeeds = customSeedsCache[id]
            )
        }
    }

    fun customFile(entry: WallpaperEntry): File? = fileFor(entry)?.takeIf { it.isFile }

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
                // Verify the encoded file can actually be decoded and seed its palette in one pass.
                val options = BitmapFactory.Options().apply {
                    inSampleSize = WallpaperLimits.sampleSize(bounds.outWidth, bounds.outHeight, 160, 160L * 160)
                }
                val probe = temp.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
                    ?: throw WallpaperException(R.string.wallpaper_bad_image)
                val extractedSeeds = try {
                    WallpaperPaletteExtractor.extractFromBitmap(probe)
                } finally {
                    probe.recycle()
                }
                val name = UUID.randomUUID().toString()
                val destination = File(dir, "$name.img")
                if (!temp.renameTo(destination)) throw WallpaperException(R.string.wallpaper_import_failed)
                val id = "photo:$name"
                if (extractedSeeds != null) {
                    customSeedsCache[id] = extractedSeeds
                }
                WallpaperEntry(
                    id = id,
                    title = context.getString(R.string.wallpaper_my_photo),
                    description = context.getString(R.string.wallpaper_private_photo),
                    mood = context.getString(R.string.wallpaper_private_photo),
                    precomputedSeeds = extractedSeeds
                )
            } finally {
                temp.delete()
            }
        }
    }

    suspend fun delete(entry: WallpaperEntry): Boolean = withContext(Dispatchers.IO) {
        evictCachedFor(entry.id)
        WallpaperBitmaps.evict(entry.id)
        customSeedsCache.remove(entry.id)
        fileFor(entry)?.let { !it.exists() || it.delete() } ?: false
    }

    /**
     * Decoding and optional EXIF rotation happen on IO, with an upper memory bound.
     * Gallery-sized thumbnails (`maxEdge <= 640`) are cached in a shared LRU cache so
     * scrolling or rotating Wallpaper Studio never re-decodes the 30 bundled WebPs;
     * callers must treat returned preview bitmaps as immutable and non-recyclable.
     */
    suspend fun preview(entry: WallpaperEntry, maxEdge: Int = 720): Bitmap? = withContext(Dispatchers.IO) {
        val safeEdge = maxEdge.coerceIn(64, WallpaperLimits.MAX_APPLY_EDGE)
        val cacheable = safeEdge <= 640
        val cacheKey = "${entry.id}:$safeEdge"
        if (cacheable) {
            synchronized(previewCache) {
                previewCache.get(cacheKey)?.takeIf { !it.isRecycled }
            }?.let { return@withContext it }
        }
        val decoded = decode(entry, safeEdge, safeEdge.toLong() * safeEdge)
        if (decoded != null && cacheable) {
            synchronized(previewCache) {
                previewCache.put(cacheKey, decoded)
            }
        }
        decoded
    }

    /**
     * Returns optical [WallpaperSeeds] for [entry], using pre-computed studio seeds
     * for bundled wallpapers and off-main-thread OKLCH quantization (merged with
     * `WallpaperColors` on API 27+) for user-imported photos.
     */
    suspend fun extractSeeds(entry: WallpaperEntry, bitmap: Bitmap? = null): WallpaperSeeds? {
        entry.precomputedSeeds?.let { return it }
        customSeedsCache[entry.id]?.let { return it }
        return withContext(Dispatchers.Default) {
            val ownedBitmap = if (bitmap == null || bitmap.isRecycled) {
                withContext(Dispatchers.IO) { decode(entry, 256, 256L * 256) }
            } else null
            val source = ownedBitmap ?: bitmap ?: return@withContext null
            try {
                val optical = WallpaperPaletteExtractor.extractFromBitmap(source)
                val platform = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    runCatching {
                        WallpaperPaletteExtractor.fromPlatformColors(WallpaperColors.fromBitmap(source)).first
                    }.getOrNull()
                } else null
                val merged = WallpaperPaletteExtractor.merge(platform, optical)
                if (merged != null && entry.isCustom) {
                    customSeedsCache[entry.id] = merged
                }
                merged
            } finally {
                ownedBitmap?.recycle()
            }
        }
    }

    suspend fun colors(bitmap: Bitmap): List<Int> = withContext(Dispatchers.Default) {
        val optical = WallpaperPaletteExtractor.extractFromBitmap(bitmap)
        val platform = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            runCatching {
                WallpaperPaletteExtractor.fromPlatformColors(WallpaperColors.fromBitmap(bitmap)).first
            }.getOrNull()
        } else null
        val seeds = WallpaperPaletteExtractor.merge(platform, optical)
        if (seeds != null) {
            distinctSwatches(listOfNotNull(seeds.accent, seeds.primary, seeds.secondary))
        } else {
            listOf(averageColor(bitmap))
        }
    }

    suspend fun colors(entry: WallpaperEntry, bitmap: Bitmap? = null): List<Int> {
        val seeds = extractSeeds(entry, bitmap) ?: return bitmap?.let { colors(it) } ?: emptyList()
        return distinctSwatches(listOfNotNull(seeds.accent, seeds.primary, seeds.secondary))
    }

    /**
     * Applies [entry] to the requested [target] and returns the platform system
     * wallpaper ID when the system home wallpaper was updated, or `null` for
     * lock-only / Luma-Home-only targets.
     */
    suspend fun apply(entry: WallpaperEntry, target: WallpaperTarget): Int? = withContext(Dispatchers.IO) {
        if (target == WallpaperTarget.LUMA_HOME) {
            // Verify the asset exists and is decodable before marking it active.
            val check = decodeBoundsOnly(entry)
            if (!check) throw WallpaperException(R.string.wallpaper_image_unavailable)
            return@withContext null
        }
        val manager = WallpaperManager.getInstance(context)
        if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) {
            throw WallpaperException(R.string.wallpaper_not_allowed)
        }
        val bitmap = decode(entry, WallpaperLimits.MAX_APPLY_EDGE, WallpaperLimits.MAX_APPLY_PIXELS)
            ?: throw WallpaperException(R.string.wallpaper_image_unavailable)
        try {
            val flags = when (target) {
                WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                WallpaperTarget.LUMA_HOME -> WallpaperManager.FLAG_SYSTEM
            }
            // Custom photo copies and system-set custom wallpapers are not requested for backup.
            val setId = manager.setBitmap(bitmap, null, !entry.isCustom, flags)
            if (setId <= 0) throw WallpaperException(R.string.wallpaper_apply_failed)
            if (target == WallpaperTarget.HOME || target == WallpaperTarget.BOTH) {
                runCatching { manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }
                    .getOrDefault(setId)
            } else {
                null
            }
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        private val importLock = Mutex()
        private val customSeedsCache = ConcurrentHashMap<String, WallpaperSeeds>()

        // 10 MB thumbnail cache (~25-40 gallery cards) bounded by actual byte count.
        private val previewCache = object : LruCache<String, Bitmap>(10 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.allocationByteCount / 1024).coerceAtLeast(1)
        }

        private fun evictCachedFor(id: String) {
            synchronized(previewCache) {
                val keys = previewCache.snapshot().keys.filter { it.startsWith("$id:") }
                for (key in keys) previewCache.remove(key)
            }
        }

        fun trimCache(fraction: Float = 0.5f) {
            synchronized(previewCache) {
                val target = (previewCache.size() * fraction.coerceIn(0f, 1f)).toInt()
                previewCache.trimToSize(target)
            }
        }

        fun clearCache() {
            synchronized(previewCache) { previewCache.evictAll() }
        }

        private fun distinctSwatches(candidates: List<Int>): List<Int> {
            val out = ArrayList<Int>(3)
            for (color in candidates) {
                val opaque = ColorMath.opaque(color)
                if (out.none { ColorMath.contrast(it, opaque) < 1.12 }) {
                    out += opaque
                } else if (out.none { it == opaque } && out.size < 2) {
                    out += opaque
                }
                if (out.size == 3) break
            }
            return out.ifEmpty { candidates.take(1).map(ColorMath::opaque) }
        }
    }

    private fun fileFor(entry: WallpaperEntry): File? =
        WallpaperCatalog.customName(entry.id)?.let { File(dir, "$it.img") }

    private fun open(entry: WallpaperEntry): InputStream? = when {
        !entry.isCustom -> WallpaperArtResources.resForEntry(entry)?.let { resId ->
            runCatching { context.resources.openRawResource(resId) }.getOrNull()
        }
        else -> fileFor(entry)?.takeIf { it.isFile }?.let { file ->
            runCatching { file.inputStream() }.getOrNull()
        }
    }

    private fun decodeBoundsOnly(entry: WallpaperEntry): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val header = open(entry) ?: return false
        header.use { BitmapFactory.decodeStream(it, null, bounds) }
        return WallpaperLimits.validSource(bounds.outWidth, bounds.outHeight)
    }

    private fun decode(entry: WallpaperEntry, maxEdge: Int, maxPixels: Long): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds Android deliberately returns a null Bitmap;
        // only a missing stream (not that null result) means the image is absent.
        val header = open(entry) ?: return null
        header.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (!WallpaperLimits.validSource(bounds.outWidth, bounds.outHeight)) return null
        val baseSample = WallpaperLimits.sampleSize(bounds.outWidth, bounds.outHeight, maxEdge, maxPixels)
        val bitmap = decodeWithFallback(entry, baseSample) ?: return null
        val orientation = fileFor(entry)?.let { file ->
            runCatching {
                ExifInterface(file.absolutePath)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
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
        } catch (_: OutOfMemoryError) {
            bitmap
        } catch (t: Throwable) {
            bitmap.recycle()
            throw t
        }
    }

    private fun decodeWithFallback(entry: WallpaperEntry, sampleSize: Int): Bitmap? {
        val primaryOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        try {
            return open(entry)?.use { BitmapFactory.decodeStream(it, null, primaryOptions) }
        } catch (_: OutOfMemoryError) {
            // Fall through to memory-conservative RGB_565 decode at 2x downsample.
        }
        val fallbackOptions = BitmapFactory.Options().apply {
            inSampleSize = (sampleSize * 2).coerceAtLeast(2)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return try {
            open(entry)?.use { BitmapFactory.decodeStream(it, null, fallbackOptions) }
        } catch (_: OutOfMemoryError) {
            null
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
        if (count == 0L) return ColorMath.BLACK
        return (0xff000000L or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)).toInt()
    }
}

enum class WallpaperTarget {
    HOME,
    LOCK,
    BOTH,
    /** Applies inside LumaOS Pro's Home surface (`Backdrop.LUMA`) without altering the system wallpaper. */
    LUMA_HOME
}

class WallpaperException(val messageRes: Int) : IOException()
