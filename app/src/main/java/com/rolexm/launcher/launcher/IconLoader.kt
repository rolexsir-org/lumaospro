package com.rolexm.launcher.launcher

import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import com.rolexm.launcher.core.apps.AppRepository
import com.rolexm.launcher.core.apps.IconCache
import com.rolexm.launcher.core.model.ComponentKey
import com.rolexm.launcher.core.util.RolexLog
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decodes app icons off the main thread and fills [IconCache].
 *
 * Icon decoding is the single largest per-item cost in a launcher: each icon is
 * a PNG decode plus, for adaptive icons, a composite. Doing that inside
 * onBindViewHolder is what makes third-party launchers stutter on old phones,
 * so every decode happens here on one background thread.
 *
 * A single thread on purpose - icon decoding is mostly I/O-bound, and parallel
 * decodes on a 2-core device would contend with the very UI work we are trying
 * to keep smooth.
 */
class IconLoader(
    private val repository: AppRepository,
    private val cache: IconCache
) {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rolexm-icons").apply { isDaemon = true }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val inFlight = Collections.synchronizedSet(HashSet<ComponentKey>())
    private val shutdown = AtomicBoolean(false)

    /** Notified on the main thread once a key is either cached or known-absent. */
    @Volatile
    var onIconReady: ((ComponentKey) -> Unit)? = null

    /**
     * Returns a cached icon immediately, or schedules a decode and returns null.
     * The caller shows a placeholder and repaints when [onIconReady] fires.
     */
    fun get(key: ComponentKey, density: Int): Drawable? {
        cache[key]?.let { return it }
        if (shutdown.get()) return null
        if (!inFlight.add(key)) return null

        try {
            executor.execute {
                val drawable = try {
                    repository.iconFor(key, density)
                } catch (t: Throwable) {
                    RolexLog.w("Icon decode failed for ${key.packageName}")
                    null
                }
                inFlight.remove(key)
                if (drawable != null) {
                    cache[key] = drawable
                }
                // Post even on failure so callers are not left waiting on a
                // key that will never resolve.
                handler.post { onIconReady?.invoke(key) }
            }
        } catch (e: RejectedExecutionException) {
            inFlight.remove(key)
        }
        return null
    }

    /**
     * Queues icons for the whole list. Used when the active profile requests
     * preloading; otherwise icons arrive on demand as rows bind.
     */
    fun preload(keys: Collection<ComponentKey>, density: Int) {
        if (shutdown.get()) return
        for (key in keys) {
            if (cache[key] == null) get(key, density)
        }
    }

    fun shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            executor.shutdownNow()
            inFlight.clear()
        }
    }
}
