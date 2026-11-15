package com.lumaos.pro.launcher.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.launcher.model.GestureAction
import com.lumaos.pro.launcher.core.prefs.GestureKind

/**
 * Localised names for the gesture bindings.
 *
 * Shared by Settings → Gestures and the onboarding gesture page so the tour can
 * print the user's *current* bindings instead of a hard-coded legend that drifts
 * from the configuration. Localising in one place also keeps the two surfaces
 * from disagreeing about names.
 */
@Composable
fun gestureKindLabel(kind: GestureKind): String = stringResource(
    when (kind) {
        GestureKind.SWIPE_UP -> R.string.gesture_swipe_up
        GestureKind.SWIPE_DOWN -> R.string.gesture_swipe_down
        GestureKind.DOUBLE_TAP -> R.string.gesture_double_tap
        GestureKind.LONG_PRESS -> R.string.gesture_long_press
        GestureKind.SWIPE_LEFT -> R.string.gesture_swipe_left
        GestureKind.SWIPE_RIGHT -> R.string.gesture_swipe_right
        GestureKind.PINCH_IN -> R.string.gesture_pinch_in
    }
)

@Composable
fun gestureActionLabel(action: GestureAction): String = stringResource(
    when (action) {
        GestureAction.None -> R.string.gesture_none
        GestureAction.OpenDrawer -> R.string.gesture_open_drawer
        GestureAction.OpenSearch -> R.string.gesture_open_search
        GestureAction.OpenQuickControls -> R.string.gesture_open_quick_controls
        GestureAction.OpenSettings -> R.string.gesture_open_settings
        GestureAction.OpenWallpaperPicker -> R.string.gesture_open_wallpaper
        GestureAction.EnterEditMode -> R.string.gesture_edit_mode
        GestureAction.OpenStudio -> R.string.gesture_open_studio
        GestureAction.NextContextProfile -> R.string.gesture_next_context
        GestureAction.VoiceCommand -> R.string.gesture_voice
        is GestureAction.OpenApp -> R.string.gesture_open_app
    }
)
