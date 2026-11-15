package com.lumaos.pro.launcher.core.prefs

import com.lumaos.pro.launcher.context.FocusAccessConsentRecord
import com.lumaos.pro.launcher.context.FocusAccessConsentStore
import kotlinx.coroutines.flow.first

/**
 * Production [FocusAccessConsentStore] over the existing DataStore.
 *
 * A deliberately thin adapter: the consent rules live in
 * [com.lumaos.pro.launcher.context.FocusAccessConsent] (pure Kotlin, unit
 * tested), and the schema lives in [LumaPrefs]. Nothing here reads or writes
 * Android's accessibility permission — that stays in `Settings.Secure`.
 */
class PrefsFocusAccessConsentStore(private val prefs: LumaPrefs) : FocusAccessConsentStore {

    override suspend fun readConsent(): FocusAccessConsentRecord = prefs.focusAccessConsent.first()

    override suspend fun writeConsent(record: FocusAccessConsentRecord) =
        prefs.setFocusAccessConsent(record)
}
