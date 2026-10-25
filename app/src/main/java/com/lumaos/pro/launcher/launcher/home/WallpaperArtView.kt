package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.LruCache
import android.view.View
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.ui.theme.WallpaperArt
import com.lumaos.pro.launcher.ui.theme.WallpaperArtResources
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.max

/**
 * Draws the active theme's bundled wallpaper.
 *
 * Design rules implemented here:
 *  - centre-crop with the artwork's declared focal point; never stretched and
 *    never letterboxed;
 *  - decode off the main thread, downsampled to the screen, with a small
 *    bitmap cache so switching away and back does not re-decode;
 *  - cross-fade between wallpapers on a theme change instead of flashing;
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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private var current: Bitmap? = null
    private var previous: Bitmap? = null
    private var art: WallpaperArt? = null
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
        val changed = art != artwork
        fallbackColor = fallback
        // Called on every theme-animation frame, so an unchanged request must not
        // restart the decode (that would cancel itself forever and never paint).
        if (!changed && (current != null || decodePending)) return
        art = artwork
        if (changed && current != null) {
            previous = current
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
        art = null
        current = null
        previous = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (art != null && current == null) requestDecode()
    }

    override fun onDetachedFromWindow() {
        generation++
        decodePending = false
        super.onDetachedFromWindow()
    }

    private fun requestDecode() {
        val artwork = art ?: return
        val token = ++generation
        decodePending = true
        val targetWidth = max(width, resources.displayMetrics.widthPixels).coerceAtLeast(360)
        WallpaperBitmaps.load(context, artwork, targetWidth) { bitmap ->
            if (token != generation || art != artwork) return@load
            decodePending = false
            if (bitmap == null) {
                LumaLog.w("Wallpaper art unavailable: ${artwork.storageKey}")
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
        if (prev != null) drawBitmap(canvas, prev, w, h, 1f)
        val now = current
        if (now != null) {
            val fade = if (fadeMs <= 0L || prev == null) 1f
            else ((SystemClock.uptimeMillis() - fadeStart) / fadeMs.toFloat()).coerceIn(0f, 1f)
            drawBitmap(canvas, now, w, h, fade)
            if (fade < 1f) postInvalidateOnAnimation()
            else previous = null
        }
    }

    private fun drawBitmap(canvas: Canvas, bitmap: Bitmap, w: Float, h: Float, alpha: Float) {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        if (bw <= 0 || bh <= 0) return
        val focalX = art?.focalX ?: 0.5f
        val focalY = art?.focalY ?: 0.5f
        val scale = max(w / bw, h / bh)
        val dw = bw * scale
        val dh = bh * scale
        val left = (w - dw) * focalX
        val top = (h - dh) * focalY
        dst.set(left, top, left + dw, top + dh)
        src.set(0, 0, bitmap.width, bitmap.height)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawBitmap(bitmap, src, dst, paint)
        paint.alpha = 255
    }
}

/**
 * Background decoder for bundled wallpaper art.
 *
 * One thread and a small LRU cache: a launcher that spawns a decode pool for
 * three bundled images is spending memory it does not have. The decoded size is
 * capped near screen width, which keeps a full-screen bitmap to a few megabytes
 * on the low-memory devices LumaOS Pro targets.
 */
internal object WallpaperBitmaps {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lumaos-wallpaper").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    private val main = Handler(Looper.getMainLooper())

    private val cache = object : LruCache<String, Bitmap>(6 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(context: Context, art: WallpaperArt, targetWidth: Int, onReady: (Bitmap?) -> Unit) {
        val app = context.applicationContext
        val key = "${art.storageKey}:${app.resources.configuration.orientation}:$targetWidth"
        cache.get(key)?.let { cached ->
            main.post { onReady(cached) }
            return
        }
        try {
            executor.execute {
                val bitmap = runCatching { decode(app, art, targetWidth) }
                    .onFailure { LumaLog.w("Wallpaper decode failed for ${art.storageKey}") }
                    .getOrNull()
                if (bitmap != null) cache.put(key, bitmap)
                main.post { onReady(bitmap) }
            }
        } catch (_: RejectedExecutionException) {
            main.post { onReady(null) }
        }
    }

    private fun decode(context: Context, art: WallpaperArt, targetWidth: Int): Bitmap? {
        val resId = WallpaperArtResources.res(art)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.resources.openRawResource(resId).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth && sample < 8) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.resources.openRawResource(resId).use { BitmapFactory.decodeStream(it, null, options) }
    }

    fun clear() = cache.evictAll()
}
