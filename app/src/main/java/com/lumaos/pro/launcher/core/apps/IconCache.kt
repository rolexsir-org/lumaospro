package com.lumaos.pro.launcher.core.apps

import android.graphics.drawable.Drawable
import android.util.LruCache
import com.lumaos.pro.launcher.core.model.ComponentKey

/**
 * In-memory cache of decoded app icons.
 *
 * Icons dominate a launcher's heap: a 48dp icon at xhdpi is roughly 96x96 ARGB
 * = ~36 KB, so a few hundred of them is several megabytes of mostly-static
 * memory. The cache is therefore sized from the active profile and can be
 * trimmed without dropping everything being shown.
 *
 * Sized in entries, not bytes, because [Drawable] has no reliable byte size.
 * The trim() overloads let the optimizer free memory in a controlled way.
 */
class IconCache(maxEntries: Int) {

    private var cache = LruCache<ComponentKey, Drawable>(maxEntries.coerceAtLeast(1))

    val maxEntries: Int get() = cache.maxSize()
    val size: Int get() = cache.size()

    /** Rough heap estimate in KB, used for the diagnostics display. */
    val approximateKb: Int get() = size * ENTRY_APPROX_KB

    @Synchronized
    operator fun get(key: ComponentKey): Drawable? = cache[key]

    @Synchronized
    operator fun set(key: ComponentKey, drawable: Drawable) {
        cache.put(key, drawable)
    }

    @Synchronized
    fun remove(key: ComponentKey) {
        cache.remove(key)
    }

    /** Removes every cached component belonging to [packageName] across profiles. */
    @Synchronized
    fun removePackage(packageName: String): List<ComponentKey> {
        val matching = cache.snapshot().keys.filter { it.packageName == packageName }
        matching.forEach { cache.remove(it) }
        return matching
    }

    /** Changes the ceiling, evicting least-recently-used entries if needed. */
    @Synchronized
    fun resize(newMaxEntries: Int) {
        val target = newMaxEntries.coerceAtLeast(1)
        if (target == cache.maxSize()) return
        cache.resize(target)
        cache.trimToSize(target)
    }

    /**
     * Frees the coldest [fraction] of the cache. [keep] entries are always
     * retained so that the visible grid does not have to re-decode.
     */
    @Synchronized
    fun trim(fraction: Float, keep: Int = 0) {
        val floor = keep.coerceAtLeast(0)
        val target = (cache.size() * (1f - fraction.coerceIn(0f, 1f))).toInt().coerceAtLeast(floor)
        cache.trimToSize(target)
        cache.resize(cache.maxSize())
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
    }

    companion object {
        /** 96x96 ARGB_8888 ≈ 36 KB, rounded up to cover adaptive icon layers. */
        private const val ENTRY_APPROX_KB = 40
    }
}
