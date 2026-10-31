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
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

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
 * pending requests are invalidated: the cache is cleared and results computed for
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
    private val lock = Any()
    private val inFlight = IconRequestTracker<ComponentKey>()
    /** Negative results are cached until the package changes or the render spec does. */
    private val unavailable = HashSet<ComponentKey>()
    private var activeDensity: Int? = null
    private val shutdown = AtomicBoolean(false)
    private val renderer = IconRenderer()

    @Volatile
    var spec: IconRenderSpec? = null
        private set

    /** Notified on the main thread once a key is either cached or known-absent. */
    @Volatile
    var onIconReady: ((ComponentKey) -> Unit)? = null

    /** Returns true when the spec changed and icons will be re-rendered. */
    fun updateSpec(newSpec: IconRenderSpec): Boolean = synchronized(lock) {
        if (newSpec == spec) return@synchronized false
        spec = newSpec
        inFlight.clear()
        unavailable.clear()
        cache.clear()
        true
    }

    /** Returns a cached icon, or schedules one decode for the current configuration. */
    fun get(key: ComponentKey, density: Int): Drawable? {
        val token: Any
        val renderSpec: IconRenderSpec?
        synchronized(lock) {
            if (shutdown.get()) return null
            // The shared cache is keyed by component; only one density may occupy it.
            if (activeDensity != null && activeDensity != density) {
                inFlight.clear()
                unavailable.clear()
                cache.clear()
            }
            activeDensity = density
            cache[key]?.let { return it }
            if (key in unavailable) return null
            token = inFlight.start(key) ?: return null
            renderSpec = spec
        }
        try {
            executor.execute {
                if (!synchronized(lock) { !shutdown.get() && inFlight.isCurrent(key, token) }) return@execute
                var failed = false
                val drawable = try {
                    produce(key, density, renderSpec)
                } catch (e: Exception) {
                    LumaLog.e("Icon decode failed for ${key.packageName}", e)
                    failed = true
                    null
                }
                val accepted = synchronized(lock) {
                    if (shutdown.get() || !inFlight.finish(key, token)) false
                    else if (failed) {
                        // Retry on a future bind, but don't trigger an immediate
                        // decode/rebind loop for a persistently broken resource.
                        false
                    } else {
                        if (drawable != null) cache[key] = drawable else unavailable.add(key)
                        true
                    }
                }
                if (accepted) handler.post { if (!shutdown.get()) onIconReady?.invoke(key) }
            }
        } catch (e: RejectedExecutionException) {
            synchronized(lock) { inFlight.finish(key, token) }
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
    fun invalidate(key: ComponentKey) = synchronized(lock) {
        cache.remove(key)
        unavailable.remove(key)
        inFlight.invalidate(key)
    }

    /** Invalidates cached, negative and still-running requests across profiles. */
    fun invalidatePackage(packageName: String) = synchronized(lock) {
        cache.removePackage(packageName)
        unavailable.removeAll { it.packageName == packageName }
        inFlight.invalidateWhere { it.packageName == packageName }
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
            synchronized(lock) {
                inFlight.clear()
                unavailable.clear()
            }
        }
    }
}
