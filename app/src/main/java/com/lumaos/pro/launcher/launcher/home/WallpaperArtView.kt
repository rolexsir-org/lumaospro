package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.LruCache
import android.view.View
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.ui.theme.WallpaperArt
import com.lumaos.pro.launcher.ui.theme.WallpaperArtResources
import com.lumaos.pro.launcher.wallpaper.WallpaperCatalog
import com.lumaos.pro.launcher.wallpaper.WallpaperEntry
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.max

/**
 * Draws the active theme's bundled wallpaper or the user's selected Wallpaper Studio
 * artwork (`art:...` or `photo:...`).
 *
 * Design rules implemented here:
 *  - centre-crop with the artwork's declared focal point; never stretched and
 *    never letterboxed;
 *  - decode off the main thread, downsampled to the screen, with a small
 *    bitmap cache so switching away and back does not re-decode;
 *  - cross-fade between wallpapers on a theme or selection change instead of flashing;
 *  - draw a flat theme colour until the bitmap is ready, so the first frame is
 *    never a black rectangle;
 *  - report an unavailable asset instead of silently showing an empty view.
 *
 * The legibility scrim is not painted here: [AmbientBackdropView] draws it from
 * the same AtmospherePlan used by the Studio preview, so contrast is managed in
 * one place.
 */
class WallpaperArtView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Called on the main thread when the requested artwork cannot be decoded. */
    var onUnavailable: (() -> Unit)? = null

    private data class Request(
        val id: String,
        val art: WallpaperArt?,
        val entry: WallpaperEntry?,
        val focalX: Float,
        val focalY: Float
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private var current: Bitmap? = null
    private var previous: Bitmap? = null
    private var activeRequest: Request? = null
    private var prevFocalX: Float = 0.5f
    private var prevFocalY: Float = 0.5f
    private var fallbackColor: Int = Color.BLACK
    private var fadeStart = 0L
    private var fadeMs = 0L
    private var generation = 0
    private var decodePending = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    fun show(artwork: WallpaperArt, fallback: Int, crossfadeMs: Long) {
        applyRequest(
            Request(
                id = "art:${artwork.storageKey}",
                art = artwork,
                entry = null,
                focalX = artwork.focalX,
                focalY = artwork.focalY
            ),
            fallback = fallback,
            crossfadeMs = crossfadeMs
        )
    }

    fun showEntry(entry: WallpaperEntry, fallback: Int, crossfadeMs: Long) {
        applyRequest(
            Request(
                id = entry.id,
                art = entry.art,
                entry = entry,
                focalX = entry.focalX,
                focalY = entry.focalY
            ),
            fallback = fallback,
            crossfadeMs = crossfadeMs
        )
    }

    private fun applyRequest(request: Request, fallback: Int, crossfadeMs: Long) {
        val changed = activeRequest?.id != request.id
        fallbackColor = fallback
        // Called on every theme-animation frame, so an unchanged request must not
        // restart the decode (that would cancel itself forever and never paint).
        if (!changed && (current != null || decodePending)) return
        val prior = activeRequest
        activeRequest = request
        if (changed && current != null) {
            previous = current
            prevFocalX = prior?.focalX ?: 0.5f
            prevFocalY = prior?.focalY ?: 0.5f
            current = null
            fadeMs = crossfadeMs.coerceAtLeast(0L)
            fadeStart = SystemClock.uptimeMillis()
        } else {
            previous = null
            fadeMs = 0L
        }
        requestDecode()
        invalidate()
    }

    /** Hides the artwork and releases the reference (the cache still holds the bitmap). */
    fun clear() {
        generation++
        decodePending = false
        activeRequest = null
        current = null
        previous = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (activeRequest != null && current == null) requestDecode()
    }

    override fun onDetachedFromWindow() {
        generation++
        decodePending = false
        super.onDetachedFromWindow()
    }

    private fun requestDecode() {
        val request = activeRequest ?: return
        val token = ++generation
        decodePending = true
        val targetWidth = max(width, resources.displayMetrics.widthPixels).coerceAtLeast(360)
        WallpaperBitmaps.load(context, request.id, request.art, request.entry, targetWidth) { bitmap ->
            if (token != generation || activeRequest?.id != request.id) return@load
            decodePending = false
            if (bitmap == null) {
                LumaLog.w("Wallpaper art unavailable: ${request.id}")
                onUnavailable?.invoke()
                return@load
            }
            if (previous == null) {
                current = bitmap
            } else {
                current = bitmap
                fadeStart = SystemClock.uptimeMillis()
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        canvas.drawColor(fallbackColor)
        val prev = previous
        if (prev != null && !prev.isRecycled) {
            drawBitmap(canvas, prev, prevFocalX, prevFocalY, w, h, 1f)
        }
        val now = current
        if (now != null && !now.isRecycled) {
            val req = activeRequest
            val fx = req?.focalX ?: 0.5f
            val fy = req?.focalY ?: 0.5f
            val fade = if (fadeMs <= 0L || prev == null) 1f
            else ((SystemClock.uptimeMillis() - fadeStart) / fadeMs.toFloat()).coerceIn(0f, 1f)
            drawBitmap(canvas, now, fx, fy, w, h, fade)
            if (fade < 1f) postInvalidateOnAnimation()
            else previous = null
        }
    }

    private fun drawBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        focalX: Float,
        focalY: Float,
        w: Float,
        h: Float,
        alpha: Float
    ) {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        if (bw <= 0 || bh <= 0) return
        val scale = max(w / bw, h / bh)
        val dw = bw * scale
        val dh = bh * scale
        val left = (w - dw) * focalX.coerceIn(0f, 1f)
        val top = (h - dh) * focalY.coerceIn(0f, 1f)
        dst.set(left, top, left + dw, top + dh)
        src.set(0, 0, bitmap.width, bitmap.height)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawBitmap(bitmap, src, dst, paint)
        paint.alpha = 255
    }
}

/**
 * Background decoder for bundled and Studio-selected wallpaper art.
 *
 * One daemon thread and a bounded 8 MB LRU cache keep memory predictable even
 * across 30 high-resolution wallpapers and user photos.
 */
internal object WallpaperBitmaps {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lumaos-wallpaper").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    private val main = Handler(Looper.getMainLooper())

    private val cache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    fun load(
        context: Context,
        id: String,
        art: WallpaperArt?,
        entry: WallpaperEntry?,
        targetWidth: Int,
        onReady: (Bitmap?) -> Unit
    ) {
        val app = context.applicationContext
        val key = "$id:${app.resources.configuration.orientation}:$targetWidth"
        synchronized(cache) { cache.get(key)?.takeIf { !it.isRecycled } }?.let { cached ->
            main.post { onReady(cached) }
            return
        }
        try {
            executor.execute {
                val bitmap = runCatching { decode(app, id, art, entry, targetWidth) }
                    .onFailure { LumaLog.w("Wallpaper decode failed for $id") }
                    .getOrNull()
                if (bitmap != null) {
                    synchronized(cache) { cache.put(key, bitmap) }
                }
                main.post { onReady(bitmap) }
            }
        } catch (_: RejectedExecutionException) {
            main.post { onReady(null) }
        }
    }

    private fun decode(
        context: Context,
        id: String,
        art: WallpaperArt?,
        entry: WallpaperEntry?,
        targetWidth: Int
    ): Bitmap? {
        val resId = when {
            art != null -> WallpaperArtResources.res(art)
            entry != null && !entry.isCustom -> WallpaperArtResources.resForEntry(entry)
            id.startsWith("art:") -> WallpaperCatalog.findBundled(id)?.let { WallpaperArtResources.resForEntry(it) }
            else -> null
        }
        if (resId != null) {
            return decodeResource(context, resId, targetWidth)
        }
        val customName = WallpaperCatalog.customName(id) ?: return null
        val file = File(File(context.filesDir, "wallpaper_images"), "$customName.img")
        if (!file.isFile) return null
        return decodeFile(file, targetWidth)
    }

    private fun decodeResource(context: Context, resId: Int, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.resources.openRawResource(resId).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth && sample < 8) sample *= 2
        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.resources.openRawResource(resId).use { BitmapFactory.decodeStream(it, null, options) }
        } catch (_: OutOfMemoryError) {
            val fallback = BitmapFactory.Options().apply {
                inSampleSize = (sample * 2).coerceAtLeast(2)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            runCatching {
                context.resources.openRawResource(resId).use { BitmapFactory.decodeStream(it, null, fallback) }
            }.getOrNull()
        }
    }

    private fun decodeFile(file: File, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        file.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth && sample < 16) sample *= 2
        val raw = try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            file.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
        } catch (_: OutOfMemoryError) {
            val fallback = BitmapFactory.Options().apply {
                inSampleSize = (sample * 2).coerceAtLeast(2)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            runCatching { file.inputStream().use { BitmapFactory.decodeStream(it, null, fallback) } }.getOrNull()
        } ?: return null

        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
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
        if (matrix.isIdentity) return raw
        return runCatching {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
                if (it !== raw) raw.recycle()
            }
        }.getOrDefault(raw)
    }

    fun evict(id: String) {
        synchronized(cache) {
            val keys = cache.snapshot().keys.filter { it.startsWith("$id:") }
            for (k in keys) cache.remove(k)
        }
    }

    fun trim(fraction: Float = 0.5f) = synchronized(cache) {
        cache.trimToSize((cache.size() * fraction.coerceIn(0f, 1f)).toInt())
    }

    fun clear() = synchronized(cache) { cache.evictAll() }
}
