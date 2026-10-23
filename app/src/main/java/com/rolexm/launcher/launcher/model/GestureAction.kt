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

    /** Home edit mode: rearrange, add widgets, change layout preset. */
    data object EnterEditMode : GestureAction {
        override val storageKey: String get() = "edit_mode"
    }

    /** Customization Studio (themes, icons, layout) with live preview. */
    data object OpenStudio : GestureAction {
        override val storageKey: String get() = "open_studio"
    }

    /** Cycles Work -> Study -> Personal -> Sleep manually. */
    data object NextContextProfile : GestureAction {
        override val storageKey: String get() = "next_context"
    }

    /** Android speech recognition; ROLEXM parses the transcript locally. */
    data object VoiceCommand : GestureAction {
        override val storageKey: String get() = "voice"
    }

    data class OpenApp(override val storageKey: String) : GestureAction {
        val key: ComponentKey? get() = ComponentKey.parse(storageKey.removePrefix(PREFIX))
        companion object {
            const val PREFIX = "app:"
            fun of(key: ComponentKey): OpenApp = OpenApp(PREFIX + key.serialize())
        }
    }

    companion object {
        /** Every non-app action, in the order offered in settings. */
        val BUILT_IN: List<GestureAction> = listOf(
            None, OpenDrawer, OpenSearch, EnterEditMode, OpenStudio, OpenSettings,
            OpenWallpaperPicker, NextContextProfile, VoiceCommand
        )

        fun parse(raw: String?): GestureAction {
            if (raw.isNullOrBlank()) return None
            if (raw.startsWith(OpenApp.PREFIX)) return OpenApp(raw)
            return when (raw) {
                None.storageKey -> None
                OpenDrawer.storageKey -> OpenDrawer
                OpenSearch.storageKey -> OpenSearch
                OpenSettings.storageKey -> OpenSettings
                OpenWallpaperPicker.storageKey -> OpenWallpaperPicker
                EnterEditMode.storageKey -> EnterEditMode
                OpenStudio.storageKey -> OpenStudio
                NextContextProfile.storageKey -> NextContextProfile
                VoiceCommand.storageKey -> VoiceCommand
                else -> None
            }
        }
    }
}
