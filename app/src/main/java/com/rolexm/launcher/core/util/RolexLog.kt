package com.rolexm.launcher.core.util

import android.util.Log
import com.rolexm.launcher.BuildConfig

/**
 * Thin logging wrapper.
 *
 * Debug-only: calls are guarded by [BuildConfig.DEBUG] so R8 can strip them
 * entirely from release builds (the ProGuard rule is a backstop, this is the
 * actual mechanism).
 *
 * Nothing here ever receives user content - no app labels, no search queries,
 * no URLs, no blocklist contents.
 */
object RolexLog {

    private const val TAG = "ROLEXM"

    fun v(message: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, message)
    }

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun i(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message)
    }

    fun w(message: String) {
        // Warnings survive release builds: they indicate a real problem, and
        // losing them would make field diagnosis impossible.
        Log.w(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
