package com.lumaos.pro.launcher.settings

import com.lumaos.pro.launcher.context.ManualOverride
import kotlinx.coroutines.delay

/**
 * Suspends until a finite manual override reaches its wall-clock deadline.
 * The UI uses this to refresh deadline-based copy even when the inexact
 * schedule alarm has not emitted a DataStore change yet.
 */
internal suspend fun awaitManualOverrideExpiry(
    override: ManualOverride,
    nowMillis: () -> Long = System::currentTimeMillis
) {
    while (true) {
        val remaining = override.millisUntilExpiry(nowMillis()) ?: return
        if (remaining == 0L) return
        delay(remaining)
    }
}
