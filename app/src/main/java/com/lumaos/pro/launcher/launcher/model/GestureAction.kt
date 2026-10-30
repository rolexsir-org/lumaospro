package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.model.ComponentKey

/**
 * What a gesture does.
 *
 * Deliberately absent: "expand Android's notification shade". There is no public
 * API for a third-party launcher to do that - StatusBarManager is hidden, and the
 * only route is an AccessibilityService, which would mean asking the user for a
 * permission that can observe and act on everything on screen. That is a bad trade
 * for LumaOS Pro, so the option is not offered rather than offered and faked. The
 * same reasoning excludes "show recent apps" and "lock the screen" (the latter
 * needs Device Admin).
 *
 * [OpenQuickControls] is *not* that action: it opens LumaOS Pro's own panel, an
 * overlay inside this app's window, which needs no system privilege to show. It
 * renders the device's real notifications through the notification-listener grant
 * the music widget already uses, and real device state through public APIs - a
 * custom panel, not a substitute for the system Quick Settings, which Android
 * still owns and still opens from the status bar.
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

    /**
     * LumaOS Pro's own pull-down panel: Quick Controls above, the device's real
     * notifications below. Not bound by default - every existing gesture keeps the
     * action the user already has.
     */
    data object OpenQuickControls : GestureAction {
        override val storageKey: String get() = "open_quick_controls"
    }

    /** Android speech recognition; LumaOS Pro parses the transcript locally. */
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
            None, OpenDrawer, OpenSearch, OpenQuickControls, EnterEditMode, OpenStudio,
            OpenSettings, OpenWallpaperPicker, NextContextProfile, VoiceCommand
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
                OpenQuickControls.storageKey -> OpenQuickControls
                NextContextProfile.storageKey -> NextContextProfile
                VoiceCommand.storageKey -> VoiceCommand
                else -> None
            }
        }
    }
}
