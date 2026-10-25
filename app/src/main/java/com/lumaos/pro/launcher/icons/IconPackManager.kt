package com.lumaos.pro.launcher.icons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import com.lumaos.pro.launcher.core.util.LumaLog
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** An installed third-party icon pack. */
data class IconPackInfo(val packageName: String, val label: String)

/**
 * Support for the de-facto standard icon-pack format (appfilter.xml mapping
 * "ComponentInfo{package/activity}" to drawable names), used by the thousands
 * of packs published for ADW/Nova-compatible launchers.
 *
 * Discovery uses the intents those packs declare; the matching <queries>
 * entries are in the manifest, so no QUERY_ALL_PACKAGES is needed. Apps a pack
 * does not cover keep their own icon with LumaOS Pro's shape and style.
 */
class IconPackManager(private val context: Context) {

    @Volatile
    private var loaded: LoadedPack? = null

    fun installedPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val seen = LinkedHashMap<String, IconPackInfo>()
        for (action in PACK_ACTIONS) {
            val infos = runCatching {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
            }.getOrDefault(emptyList())
            for (ri in infos) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == context.packageName || seen.containsKey(pkg)) continue
                val label = runCatching { ri.activityInfo.applicationInfo.loadLabel(pm).toString() }.getOrDefault(pkg)
                seen[pkg] = IconPackInfo(pkg, label)
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    /** Loads (or reuses) the pack's appfilter. Off the main thread only. */
    fun ensureLoaded(packageName: String?): LoadedPack? {
        if (packageName.isNullOrBlank()) {
            loaded = null
            return null
        }
        loaded?.takeIf { it.packageName == packageName }?.let { return it }
        val pack = runCatching { load(packageName) }
            .onFailure { LumaLog.w("Icon pack $packageName could not be loaded") }
            .getOrNull()
        loaded = pack
        return pack
    }

    /** The pack's icon for [component], or null when the pack does not cover it. */
    fun iconFor(packageName: String?, component: ComponentName, density: Int): Drawable? {
        val pack = ensureLoaded(packageName) ?: return null
        val name = pack.mapping[component.flattenToString()]
            ?: pack.mapping[component.packageName]
            ?: return null
        return pack.drawable(name, density)
    }

    @SuppressLint("DiscouragedApi")
    private fun load(packageName: String): LoadedPack {
        val res = context.packageManager.getResourcesForApplication(packageName)
        val mapping = HashMap<String, String>()
        val parser: XmlPullParser? = run {
            val id = res.getIdentifier("appfilter", "xml", packageName)
            if (id != 0) res.getXml(id) else runCatching {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(res.assets.open("appfilter.xml"), "UTF-8")
                }
            }.getOrNull()
        }
        if (parser != null) {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (component != null && drawable != null) {
                        parseComponent(component)?.let { key -> mapping.putIfAbsent(key, drawable) }
                    }
                }
                event = parser.next()
            }
        }
        LumaLog.i("Icon pack $packageName: ${mapping.size} mappings")
        return LoadedPack(packageName, res, mapping)
    }

    /** "ComponentInfo{pkg/cls}" -> flattened "pkg/cls" (relative ".cls" expanded). */
    private fun parseComponent(raw: String): String? {
        val inner = raw.substringAfter("ComponentInfo{", "").substringBefore("}", "")
        if (!inner.contains('/')) return null
        val pkg = inner.substringBefore('/')
        var cls = inner.substringAfter('/')
        if (cls.startsWith(".")) cls = pkg + cls
        if (pkg.isBlank() || cls.isBlank()) return null
        return ComponentName(pkg, cls).flattenToString()
    }

    class LoadedPack(val packageName: String, private val res: Resources, val mapping: Map<String, String>) {
        @SuppressLint("DiscouragedApi")
        fun drawable(name: String, density: Int): Drawable? {
            val id = res.getIdentifier(name, "drawable", packageName)
            if (id == 0) return null
            return runCatching { res.getDrawableForDensity(id, density, null) }.getOrNull()
        }
    }

    companion object {
        /** Intent actions declared by icon packs for the major launcher families. */
        val PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.anddoes.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "com.fede.launcher.THEME_ICONPACK",
            "net.oneplus.launcher.icons.ACTION_PICK_ICON"
        )
    }
}
