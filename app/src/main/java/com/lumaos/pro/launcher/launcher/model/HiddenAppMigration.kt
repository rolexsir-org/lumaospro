package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.vault.core.VaultHiddenApp

/**
 * Carries the old Hide Apps selection into the Vault.
 *
 * Before this change, hiding an app wrote a `ComponentKey` into a plain
 * DataStore preference (`hidden_apps_<profile>`). Hiding is now owned by the
 * Vault, so on the first unlock after upgrading, those keys are converted into
 * [VaultHiddenApp] entries and added to the encrypted index.
 *
 * Rules, because this touches data the user did not ask to have rewritten:
 *
 *  - **additive**: entries are only ever added. The old preference is not
 *    deleted, so a downgrade, a failed import or a bug in this code can never
 *    lose the selection.
 *  - **never guesses**: a key that will not parse is skipped rather than
 *    reinterpreted, because a misinterpreted key would hide a different app.
 *  - **profile-qualified**: the old list was per context profile; the Vault's
 *    hidden list is global, so entries from several profiles are merged and
 *    de-duplicated by component key.
 *  - **labels are optional**: the legacy preference stored keys only. An entry
 *    with no label shows its package name until the launcher resolves a label.
 *
 * Pure Kotlin and free of Android types, so the conversion is unit tested.
 */
object HiddenAppMigration {

    /**
     * Converts the legacy per-profile hidden lists into Vault entries.
     *
     * @param legacy raw preference values keyed by profile, exactly as stored
     *   (`serial:pkg/cls` joined with `|`). Missing or blank values are fine.
     * @return one entry per distinct app, in first-seen order, with a blank label.
     */
    fun fromLegacy(legacy: Map<String, String?>): List<VaultHiddenApp> {
        val seen = HashSet<ComponentKey>()
        val out = ArrayList<VaultHiddenApp>()
        legacy.forEach { (_, raw) ->
            raw.orEmpty().split('|').forEach { value ->
                val key = HiddenAppKeys.parse(value.trim()) ?: return@forEach
                // Distinct by the full component identity, not by package: one
                // package can expose several launcher icons.
                if (!seen.add(key)) return@forEach
                out += VaultHiddenApp(
                    packageName = key.packageName,
                    className = key.className,
                    userSerial = key.userSerial,
                    label = "",
                    hiddenAtMs = 0L
                )
            }
        }
        return out
    }

    /**
     * Legacy entries that are not yet in the Vault.
     *
     * Used for reporting and for deciding whether an import is worth attempting;
     * the actual write is idempotent on the Vault side as well.
     */
    fun pending(legacy: List<VaultHiddenApp>, existingKeys: Set<String>): List<VaultHiddenApp> =
        legacy.filterNot { it.componentKey in existingKeys }.distinctBy { it.componentKey }

    /** True when the Vault now holds everything the legacy preference listed. */
    fun isFullyImported(legacy: List<VaultHiddenApp>, existingKeys: Set<String>): Boolean =
        legacy.all { it.componentKey in existingKeys }
}
