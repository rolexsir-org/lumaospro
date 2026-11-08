package com.lumaos.pro.launcher.vault.core

/**
 * Which Vault screen an authenticated launch should open.
 *
 * A caller that says "authenticate, then take me to Hidden Apps" (the drawer's
 * long-press entry, the Settings row) must arrive there rather than at the Vault
 * home, or the tap appears to do nothing. Equally, an *unknown* or stale value
 * must never send the user somewhere unexpected: the destination is a closed set
 * of known names, and anything else falls back to the Vault home.
 *
 * Lives in the Vault's own core, not the launcher's: the launcher already depends
 * on the Vault (it reads hidden apps from it), so the dependency must not run the
 * other way as well.
 *
 * Pure Kotlin so the mapping is pinned by unit tests instead of only by a device
 * run; [VaultLockActivity][com.lumaos.pro.launcher.vault.ui.VaultLockActivity]
 * turns the result into a class reference.
 */
object VaultDestination {

    /** Explicit destination: the Hidden Apps section. */
    const val HIDDEN_APPS = "hidden_apps"

    /** The default: the Vault home screen. */
    const val HOME = "home"

    /** Normalises whatever arrived in the launch intent. */
    fun normalize(raw: String?): String =
        if (raw == HIDDEN_APPS) HIDDEN_APPS else HOME

    /** True when unlocking should continue into the Hidden Apps section. */
    fun opensHiddenApps(raw: String?): Boolean = normalize(raw) == HIDDEN_APPS
}
