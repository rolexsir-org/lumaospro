package com.lumaos.pro.launcher.vault.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle

/**
 * Clipboard handling for Vault text.
 *
 * The user may deliberately copy text out of a note - that is their content and their
 * decision, and Vault does not take it away. What Vault can do is tell the platform
 * that the clip is sensitive, so Android 13 and later do not render it in the copy
 * preview, do not add it to the clipboard history, and mask it in the clipboard editor.
 *
 * Two limits are stated in `docs/VAULT_THREAT_MODEL.md` and are not glossed over here:
 *
 *  - the flag is a *hint*. A clipboard manager app, an input method or an app holding
 *    clipboard access may ignore it, and on Android 12 and older nothing reads it;
 *  - once text is on the clipboard it is shared state, outside the Vault's control.
 *
 * Only the flag is added; the clip content itself is never logged, never put in a
 * notification, and never written anywhere else.
 */
object VaultClipboard {

    /**
     * `ClipDescription.EXTRA_IS_SENSITIVE`, inlined.
     *
     * The platform constant is API 33. Referencing the literal keeps this file free of
     * a `NewApi` suppression and behaves identically on older releases, where the
     * extra is simply ignored.
     */
    const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    /**
     * Puts [text] on the clipboard marked sensitive.
     *
     * Returns false when the clipboard is unavailable; the caller must not fall back to
     * an unmarked copy, because that would silently produce the weaker behaviour.
     */
    fun copySensitive(context: Context, label: String, text: String): Boolean {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clip = ClipData.newPlainText(label, text)
        // ClipDescription extras are a PersistableBundle, and that is where the
        // platform looks for the sensitivity hint.
        clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
        return runCatching {
            manager.setPrimaryClip(clip)
            true
        }.getOrDefault(false)
    }
}
