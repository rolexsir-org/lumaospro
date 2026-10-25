package com.lumaos.pro.launcher.core.model

/**
 * Stable identity for a launchable activity.
 *
 * A component is not identified by package name alone: the same package can
 * expose several launcher icons (Calculator / Calculator widget, dialer
 * shortcuts, etc.), and profile-aware devices can install one package for
 * several users. Both parts are needed.
 *
 * This is a plain value type with no Android imports so that layout, sorting
 * and persistence logic can be unit tested on the JVM.
 */
data class ComponentKey(
    val packageName: String,
    val className: String,
    val userSerial: Long = 0L
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(className.isNotBlank()) { "className must not be blank" }
    }

    /** Text form used by [com.lumaos.pro.launcher.launcher.model.HomeLayoutCodec]. */
    fun serialize(): String = "$userSerial:$packageName/$className"

    companion object {
        /**
         * Parses the [serialize] form. Returns null rather than throwing so that
         * a corrupted or outdated layout entry degrades to "item missing"
         * instead of taking down the home screen.
         */
        fun parse(value: String): ComponentKey? {
            val colon = value.indexOf(':')
            if (colon <= 0) return null
            val serial = value.substring(0, colon).toLongOrNull() ?: return null
            val slash = value.indexOf('/', colon + 1)
            if (slash <= colon + 1 || slash == value.length - 1) return null
            val pkg = value.substring(colon + 1, slash)
            val cls = value.substring(slash + 1)
            if (pkg.isBlank() || cls.isBlank()) return null
            return ComponentKey(pkg, cls, serial)
        }
    }
}
