package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.model.ComponentKey

/**
 * Conversion between the Vault's hidden-app keys and the drawer's [ComponentKey].
 *
 * Hidden apps are stored in the Vault's encrypted index as `serial:pkg/cls` text -
 * the same canonical form [ComponentKey.serialize] produces - and the drawer needs
 * them back as [ComponentKey]. Both directions live here, as pure functions with no
 * Android types, so the round trip is covered by fast unit tests instead of only by
 * a device run.
 *
 * Deliberately strict: a malformed entry is dropped rather than guessed at. A key
 * that cannot be parsed must never become "hide some other app", and a corrupt
 * entry must not stop the drawer from listing everything else.
 */
object HiddenAppKeys {

    /**
     * Parses the Vault's stored keys into drawer keys.
     *
     * Unparseable entries are skipped. An empty result means "nothing is hidden",
     * which is also what a locked vault reports.
     */
    fun toComponentKeys(values: Collection<String>): Set<ComponentKey> =
        values.mapNotNullTo(LinkedHashSet()) { parse(it) }

    /** Parses one `serial:pkg/cls` key, or null when it is malformed. */
    fun parse(value: String): ComponentKey? {
        val colon = value.indexOf(':')
        if (colon <= 0) return null
        val serial = value.substring(0, colon).toLongOrNull() ?: return null
        val slash = value.indexOf('/', colon + 1)
        if (slash <= colon + 1 || slash == value.length - 1) return null
        val packageName = value.substring(colon + 1, slash)
        val className = value.substring(slash + 1)
        if (packageName.isBlank() || className.isBlank()) return null
        return ComponentKey(packageName, className, serial)
    }

    /** Renders a drawer key in the form the Vault stores. */
    fun serialize(key: ComponentKey): String = key.serialize()
}
