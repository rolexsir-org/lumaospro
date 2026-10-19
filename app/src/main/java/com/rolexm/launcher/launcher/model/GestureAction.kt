package com.rolexm.launcher.launcher.model

import com.rolexm.launcher.core.model.ComponentKey

/**
 * What a gesture does.
 *
 * Deliberately absent: "open the notification shade". There is no public API
 * for a third-party launcher to expand the notification panel - StatusBarManager
 * is hidden, and the only route is an AccessibilityService, which would mean
 * asking the user for a permission that can observe and act on everything on
 * screen. That is a bad trade for ROLEXM, so the option is not offered rather
 * than offered and faked. The same reasoning excludes "show recent apps" and
 * "lock the screen" (the latter needs Device Admin).
 */
sealed interface GestureAction {

    val storageKey: String

    data object None : GestureAction {
        override val storageKey: String get() = "none"
    }

    data object OpenDrawer : GestureAction {
        override val storageKey: String get() = "open_drawer"
    }

    data object OpenSearch : GestureAction {
        override val storageKey: String get() = "open_search"
    }

    data object OpenSettings : GestureAction {
        override val storageKey: String get() = "open_settings"
    }

    data object OpenWallpaperPicker : GestureAction {
        override val storageKey: String get() = "open_wallpaper"
    }

    data class OpenApp(override val storageKey: String) : GestureAction {
        val key: ComponentKey? get() = ComponentKey.parse(storageKey.removePrefix(PREFIX))
        companion object {
            const val PREFIX = "app:"
            fun of(key: ComponentKey): OpenApp = OpenApp(PREFIX + key.serialize())
        }
    }

    companion object {
        fun parse(raw: String?): GestureAction {
            if (raw.isNullOrBlank()) return None
            if (raw.startsWith(OpenApp.PREFIX)) return OpenApp(raw)
            return when (raw) {
                None.storageKey -> None
                OpenDrawer.storageKey -> OpenDrawer
                OpenSearch.storageKey -> OpenSearch
                OpenSettings.storageKey -> OpenSettings
                OpenWallpaperPicker.storageKey -> OpenWallpaperPicker
                else -> None
            }
        }
    }
}
