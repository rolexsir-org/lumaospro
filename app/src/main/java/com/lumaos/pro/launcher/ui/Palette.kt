package com.lumaos.pro.launcher.ui

/**
 * Compatibility control for the original light/dark/system preference.
 *
 * Concrete colors are resolved only by the shared theme engine in
 * `ui.theme`; keeping this small mode enum lets existing DataStore keys and
 * the compact Appearance control continue to work without maintaining a
 * second, competing palette.
 */
enum class ThemeMode(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    /** Whether this mode resolves to a dark theme. */
    fun resolveDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorageKey(key: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == key } ?: SYSTEM
    }
}
