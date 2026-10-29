package com.lumaos.pro.launcher.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.core.util.LumaLog
import java.io.InputStream

/**
 * Drawable wrapper around a decoded LumaOS Pro Living Icon bitmap.
 *
 * Carries [iconId] so [IconRenderer] can map the optical-glass silhouette's
 * exact measured bounds onto the launcher icon grid without adding an opaque
 * plate or clipping its 3D glass corners. When drawn directly (for example in
 * an icon-override picker or onboarding preview), it normalizes its own inner
 * padding inside [getBounds] with bilinear filtering and full alpha preservation.
 */
class LivingIconDrawable(
    val iconId: LivingIconId,
    val bitmap: Bitmap
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dstRect = RectF()

    override fun draw(canvas: Canvas) {
        val b: Rect = bounds
        if (b.isEmpty || bitmap.isRecycled) return
        val targetSpan = minOf(b.width(), b.height()) * 0.92f
        val fullSpan = targetSpan / iconId.visibleSpanFraction.coerceAtLeast(0.5f)
        val cx = b.exactCenterX() - (iconId.centerXFraction - 0.5f) * fullSpan
        val cy = b.exactCenterY() - (iconId.centerYFraction - 0.5f) * fullSpan
        dstRect.set(
            cx - fullSpan * 0.5f,
            cy - fullSpan * 0.5f,
            cx + fullSpan * 0.5f,
            cy + fullSpan * 0.5f
        )
        canvas.drawBitmap(bitmap, null, dstRect, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = bitmap.width
    override fun getIntrinsicHeight(): Int = bitmap.height
}

/**
 * Loads and caches bundled LumaOS Pro Living Icons from `assets/living_icons/`.
 *
 * Thread-safe: background icon workers and Studio preview coroutines may resolve
 * and decode assets concurrently without duplicating work on the UI thread.
 */
class LivingIconManager(
    context: Context,
    private val assetOpener: (String) -> InputStream = { path -> context.applicationContext.assets.open(path) }
) {

    @Volatile
    var enabled: Boolean = true
        private set

    @Volatile
    var overrides: Map<ComponentKey, LivingIconOverride> = emptyMap()
        private set

    private val cacheSizeKb: Int = run {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        (maxKb / 16).coerceIn(4 * 1024, 14 * 1024)
    }

    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    /** Updates configuration and returns `true` when effective resolution changed. */
    fun updateConfiguration(
        livingIconsEnabled: Boolean,
        overrideMap: Map<ComponentKey, LivingIconOverride>
    ): Boolean {
        val changed = (enabled != livingIconsEnabled) || (overrides != overrideMap)
        enabled = livingIconsEnabled
        overrides = overrideMap
        return changed
    }

    /** Resolves which [LivingIconId], if any, applies to [key] under current settings. */
    fun resolveId(key: ComponentKey): LivingIconId? =
        LivingIconResolver.resolve(key, livingIconsEnabled = enabled, overrides = overrides)

    /** Returns `true` when [key] explicitly requests the app's native system icon. */
    fun forcesSystemIcon(key: ComponentKey): Boolean {
        val exact = overrides[key]
            ?: if (key.userSerial != 0L) overrides[key.copy(userSerial = 0L)] else null
        return exact == LivingIconOverride.UseSystem
    }

    /** Returns `true` when [key] has an explicit user-assigned [LivingIconOverride.UseLiving]. */
    fun hasExplicitLivingOverride(key: ComponentKey): Boolean {
        val exact = overrides[key]
            ?: if (key.userSerial != 0L) overrides[key.copy(userSerial = 0L)] else null
        return exact is LivingIconOverride.UseLiving
    }

    /**
     * Resolves and decodes the bundled Living Icon for [key], or `null` if the
     * app is unmapped, disabled by user preference, or its asset could not decode.
     */
    fun drawableFor(key: ComponentKey, targetSizePx: Int = 256): LivingIconDrawable? {
        val id = resolveId(key) ?: return null
        return drawableForId(id, targetSizePx)
    }

    /** Package-level lookup for app group headers (such as the widget picker). */
    fun drawableForPackage(packageName: String, targetSizePx: Int = 128): LivingIconDrawable? {
        val id = LivingIconResolver.resolvePackage(packageName, livingIconsEnabled = enabled) ?: return null
        return drawableForId(id, targetSizePx)
    }

    /** Decodes and returns the cached [Bitmap] for [iconId], or `null` if unavailable. */
    fun bitmapForId(iconId: LivingIconId, targetSizePx: Int = 256): Bitmap? =
        drawableForId(iconId, targetSizePx)?.bitmap

    /** Decodes a specific [LivingIconId] directly (used by previews and the override picker). */
    fun drawableForId(iconId: LivingIconId, targetSizePx: Int = 256): LivingIconDrawable? {
        val sampleSize = computeSampleSize(targetSizePx)
        val cacheKey = "${iconId.storageKey}@$sampleSize"
        synchronized(bitmapCache) {
            bitmapCache.get(cacheKey)?.takeIf { !it.isRecycled }?.let { cached ->
                return LivingIconDrawable(iconId, cached)
            }
        }
        val decoded = decodeBitmap(iconId, sampleSize) ?: return null
        synchronized(bitmapCache) {
            val raced = bitmapCache.get(cacheKey)?.takeIf { !it.isRecycled }
            if (raced != null) {
                return LivingIconDrawable(iconId, raced)
            }
            bitmapCache.put(cacheKey, decoded)
        }
        return LivingIconDrawable(iconId, decoded)
    }

    private fun decodeBitmap(iconId: LivingIconId, sampleSize: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sampleSize
            }
            assetOpener(iconId.assetPath).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }?.takeIf { it.width > 0 && it.height > 0 }
        } catch (e: Exception) {
            LumaLog.w("Living Icon asset unavailable (${iconId.assetPath}): ${e.javaClass.simpleName}")
            null
        }
    }

    fun trimMemory(clearAll: Boolean) {
        synchronized(bitmapCache) {
            if (clearAll) bitmapCache.evictAll()
            else bitmapCache.trimToSize(bitmapCache.maxSize() / 2)
        }
    }

    companion object {
        /**
         * 1024px masters decode at 512px (`inSampleSize = 2`) for all phone/tablet
         * launcher targets up to 320px (>1.6x supersampled, 4x lower allocation),
         * and at full 1024px (`inSampleSize = 1`) for larger canvases.
         */
        internal fun computeSampleSize(targetSizePx: Int): Int = when {
            targetSizePx <= 384 -> 2
            else -> 1
        }
    }
}
