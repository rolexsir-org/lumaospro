package com.lumaos.pro.launcher.launcher

import android.content.ComponentName
import android.content.res.Resources
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import com.lumaos.pro.launcher.core.apps.AppRepository
import com.lumaos.pro.launcher.core.apps.IconCache
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.icons.IconPackManager
import com.lumaos.pro.launcher.icons.IconRenderSpec
import com.lumaos.pro.launcher.icons.IconRenderer
import com.lumaos.pro.launcher.icons.LivingIconDrawable
import com.lumaos.pro.launcher.icons.LivingIconManager
import com.lumaos.pro.launcher.icons.LivingIconOverride
import com.lumaos.pro.launcher.icons.LivingIconResolver
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Decodes and renders app icons off the main thread and fills [IconCache].
 *
 * Icon work is the single largest per-item cost in a launcher: a PNG decode,
 * an adaptive-icon composite, and here also LumaOS Pro's shape/plate treatment.
 * Doing that inside onBindViewHolder is what makes launchers stutter on old
 * phones, so every decode and render happens here on one background thread.
 *
 * A single thread on purpose - parallel decodes on a 2-core device would
 * contend with the very UI work we are trying to keep smooth.
 *
 * When the render spec changes (theme, shape, style, icon pack, size), the
 * generation counter advances: the cache is cleared and results computed for
 * the old spec are discarded instead of being cached.
 */
class IconLoader(
    private val repository: AppRepository,
    private val cache: IconCache,
    private val packs: IconPackManager,
    private val resources: Resources,
    private val livingIcons: LivingIconManager? = null
) {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lumaos-icons").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val inFlight = Collections.synchronizedSet(HashSet<ComponentKey>())
    /** Negative results are cached until the package changes or the render spec does. */
    private val unavailable = Collections.synchronizedSet(HashSet<ComponentKey>())
    private val keyGenerations = ConcurrentHashMap<ComponentKey, Int>()
    private val shutdown = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val renderer = IconRenderer()

    @Volatile
    var spec: IconRenderSpec? = null
        private set

    /** Notified on the main thread once a key is either cached or known-absent. */
    @Volatile
    var onIconReady: ((ComponentKey) -> Unit)? = null

    /** Returns true when the spec changed and icons will be re-rendered. */
    fun updateSpec(newSpec: IconRenderSpec): Boolean {
        if (newSpec == spec) return false
        spec = newSpec
        generation.incrementAndGet()
        inFlight.clear()
        unavailable.clear()
        cache.clear()
        return true
    }

    /**
     * Returns a cached icon immediately, or schedules a decode and returns null.
     * The caller shows a placeholder and repaints when [onIconReady] fires.
     */
    fun get(key: ComponentKey, density: Int): Drawable? {
        cache[key]?.let { return it }
        if (shutdown.get() || unavailable.contains(key)) return null
        if (!inFlight.add(key)) return null
        val gen = generation.get()
        val keyGen = keyGenerations[key] ?: 0
        val s = spec
        try {
            executor.execute {
                val drawable = try {
                    produce(key, density, s)
                } catch (t: Throwable) {
                    LumaLog.w("Icon decode failed for ${key.packageName}")
                    null
                }
                if (gen == generation.get()) {
                    inFlight.remove(key)
                    // A package callback may have invalidated this exact icon
                    // while its old decode was running. Never let that stale
                    // result overwrite the package's new generation.
                    if (keyGen == (keyGenerations[key] ?: 0)) {
                        if (drawable != null) cache[key] = drawable else unavailable.add(key)
                    }
                    // Post on success, absence and invalidation. Absence is
                    // remembered, so this cannot become a decode/notify loop.
                    handler.post { onIconReady?.invoke(key) }
                }
            }
        } catch (e: RejectedExecutionException) {
            inFlight.remove(key)
        }
        return null
    }

    private fun produce(key: ComponentKey, density: Int, s: IconRenderSpec?): Drawable? {
        val targetPx = s?.sizePx ?: 256
        val resolved = resolveSourceDrawable(
            key = key,
            density = density,
            targetPx = targetPx,
            iconPackPackage = s?.iconPack,
            livingIconsEnabled = s?.livingIcons ?: (livingIcons?.enabled ?: true),
            overrides = livingIcons?.overrides.orEmpty()
        ) ?: return null
        if (s == null) return repository.badgeIfManagedProfile(resolved.drawable, key.userSerial)
        val rendered = BitmapDrawable(
            resources,
            renderer.render(resolved.drawable, s, fromIconPack = resolved.fromIconPack)
        )
        return if (resolved.drawable is LivingIconDrawable && key.userSerial != 0L) {
            repository.badgeIfManagedProfile(rendered, key.userSerial)
        } else {
            rendered
        }
    }

    /** Renders an arbitrary drawable (shortcut icons) with the current treatment. Background thread. */
    fun renderSync(source: Drawable): Drawable {
        val s = spec ?: return source
        return BitmapDrawable(resources, renderer.render(source, s, fromIconPack = false))
    }

    /**
     * Renders one app with a draft spec for live previews; bypasses the cache.
     * Must be called off the main thread.
     */
    fun renderPreview(
        key: ComponentKey,
        density: Int,
        draft: IconRenderSpec,
        overrides: Map<ComponentKey, LivingIconOverride> = livingIcons?.overrides.orEmpty()
    ): android.graphics.Bitmap? {
        val resolved = resolveSourceDrawable(
            key = key,
            density = density,
            targetPx = draft.sizePx,
            iconPackPackage = draft.iconPack,
            livingIconsEnabled = draft.livingIcons,
            overrides = overrides
        ) ?: return null
        return renderer.render(resolved.drawable, draft, fromIconPack = resolved.fromIconPack)
    }

    private data class ResolvedSource(val drawable: Drawable, val fromIconPack: Boolean)

    private fun resolveSourceDrawable(
        key: ComponentKey,
        density: Int,
        targetPx: Int,
        iconPackPackage: String?,
        livingIconsEnabled: Boolean,
        overrides: Map<ComponentKey, LivingIconOverride>
    ): ResolvedSource? {
        // Confirm the component exists before returning a bundled or pack icon,
        // so uninstalled or disabled entries never masquerade as installed apps.
        if (!repository.isInstalled(key)) return null

        val exactOverride = overrides[key]
            ?: if (key.userSerial != 0L) overrides[key.copy(userSerial = 0L)] else null

        if (exactOverride is LivingIconOverride.UseLiving && livingIcons != null) {
            livingIcons.drawableForId(exactOverride.icon, targetPx)?.let {
                return ResolvedSource(it, fromIconPack = false)
            }
        }

        if (exactOverride == LivingIconOverride.UseSystem) {
            return repository.iconFor(key, density)?.let { ResolvedSource(it, fromIconPack = false) }
        }

        if (!iconPackPackage.isNullOrBlank()) {
            packs.iconFor(iconPackPackage, ComponentName(key.packageName, key.className), density)?.let {
                return ResolvedSource(it, fromIconPack = true)
            }
        }

        if (livingIcons != null) {
            val id = LivingIconResolver.resolve(key, livingIconsEnabled = livingIconsEnabled, overrides = overrides)
            if (id != null) {
                livingIcons.drawableForId(id, targetPx)?.let {
                    return ResolvedSource(it, fromIconPack = false)
                }
            }
        }

        return repository.iconFor(key, density)?.let { ResolvedSource(it, fromIconPack = false) }
    }

    /** Runs [task] on the icon thread (used for shortcut icons and previews). */
    fun submit(task: () -> Unit) {
        if (shutdown.get()) return
        try {
            executor.execute(task)
        } catch (_: RejectedExecutionException) {
        }
    }

    /** Invalidates one installed component after a package add/remove/update. */
    fun invalidate(key: ComponentKey) {
        cache.remove(key)
        unavailable.remove(key)
        keyGenerations.merge(key, 1) { old, delta -> old + delta }
    }

    /** Invalidates all cached or negative entries belonging to [packageName]. */
    fun invalidatePackage(packageName: String) {
        val removedCached = cache.removePackage(packageName)
        val removedUnavailable = synchronized(unavailable) {
            val matching = unavailable.filter { it.packageName == packageName }
            unavailable.removeAll(matching.toSet())
            matching
        }
        val knownKeys = HashSet<ComponentKey>().apply {
            addAll(removedCached)
            addAll(removedUnavailable)
            addAll(keyGenerations.keys.filter { it.packageName == packageName })
        }
        knownKeys.forEach { key ->
            keyGenerations.merge(key, 1) { old, delta -> old + delta }
        }
    }

    /** Synchronously peeks a cached icon without scheduling background work. */
    fun peek(key: ComponentKey): Drawable? = cache[key]

    /**
     * Queues icons for the whole list. Used when the active profile requests
     * preloading; otherwise icons arrive on demand as rows bind.
     */
    fun preload(keys: Collection<ComponentKey>, density: Int, maxCount: Int = 260) {
        if (shutdown.get()) return
        var queued = 0
        for (key in keys) {
            if (queued >= maxCount) break
            if (cache[key] == null) {
                get(key, density)
                queued++
            }
        }
    }

    fun shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            onIconReady = null
            handler.removeCallbacksAndMessages(null)
            executor.shutdownNow()
            inFlight.clear()
            unavailable.clear()
        }
    }
}
