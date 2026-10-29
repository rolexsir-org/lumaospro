package com.lumaos.pro.launcher.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Unified tactile haptic feedback across LumaOS Pro.
 *
 * Uses View.performHapticFeedback so user system haptic settings and Battery
 * Saver automatically govern vibration without requiring VIBRATE permission.
 */
object LumaHaptics {

    /** App icon / widget / row long-press. */
    fun longPress(view: View?) {
        perform(view, HapticFeedbackConstants.LONG_PRESS)
    }

    /** Subtle tick for alphabet scrubber letter steps, chips, and widget row snaps. */
    fun tick(view: View?) {
        val constant = if (Build.VERSION.SDK_INT >= 34) {
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        perform(view, constant)
    }

    /** Crisp tap for toggles, sort switches, and mode controls. */
    fun tap(view: View?) {
        perform(view, HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** Threshold / surface snap when Drawer or Folder settles open or closed. */
    fun snap(view: View?) {
        val constant = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.GESTURE_END
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        perform(view, constant)
    }

    /** Confirmation pulse for destructive actions, folder drop, or setup completion. */
    fun confirm(view: View?) {
        val constant = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        perform(view, constant)
    }

    private fun perform(view: View?, constant: Int) {
        if (view == null || !view.isAttachedToWindow) return
        runCatching { view.performHapticFeedback(constant) }
    }
}
