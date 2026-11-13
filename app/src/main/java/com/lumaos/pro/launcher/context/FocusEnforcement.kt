package com.lumaos.pro.launcher.context

import com.lumaos.pro.launcher.core.model.ComponentKey

/**
 * Centralized Focus Protection enforcement decisions (escalation layer 2).
 *
 * Layer 1 (launcher interception) lives in [ContextEngine.isRestricted] and
 * runs first: taps inside LumaOS Pro never start a restricted app. This layer
 * answers the follow-up question for launches LumaOS Pro did not make — a
 * notification tap, a Recents switch, an external app's intent — once the
 * foreground package is observable (currently via the optional
 * [FocusAccessibilityService]): given the active focus session, must the user
 * be sent back?
 *
 * One engine, two callers (the accessibility service and the standalone
 * restriction activity's stale-start guard), so both layers always agree.
 * Pure JVM logic: no Android imports, covered by unit tests.
 *
 * Honesty boundary: this is package-level foreground enforcement. It cannot
 * see or block individual screens inside an app (Reels, Shorts, feeds), and
 * it only observes the foreground while the user grants Accessibility and
 * the system keeps the service bound — both of which Android can revoke.
 */
object FocusEnforcement {

    /**
     * Everything the decision needs, snapshotted from DataStore at event
     * time. Snapshots are never cached across events: enforcement always
     * reflects the current context, toggle, restriction set and unlock, which
     * is also what makes process recreation and rapid context switching safe.
     */
    data class Snapshot(
        val active: ContextProfile,
        val focusEnabled: Boolean,
        val restrictedPackages: Set<String>,
        val unlockProfile: ContextProfile?,
        val unlockUntilMillis: Long
    )

    /**
     * True while an emergency unlock covers the active session. The unlock is
     * tied to the profile it was granted in, so switching contexts never
     * leaks an unlock from Work into Study (or vice versa), and expiry is a
     * strict wall-clock deadline.
     */
    fun unlockActive(snapshot: Snapshot, nowMillis: Long): Boolean =
        unlockActive(snapshot.active, snapshot.unlockProfile, snapshot.unlockUntilMillis, nowMillis)

    /** Field form for callers (like the launcher gate) that hold flows, not snapshots. */
    fun unlockActive(
        active: ContextProfile,
        unlockProfile: ContextProfile?,
        unlockUntilMillis: Long,
        nowMillis: Long
    ): Boolean = unlockProfile == active && nowMillis < unlockUntilMillis

    /**
     * True when [packageName] entering the observed foreground should trigger
     * a layer-2 intervention attempt. Personal and Sleep never enforce, an
     * unexpired unlock for the active profile allows everything, and
     * [FocusSafety] always wins over the restriction set. Returning true does
     * not guarantee Android will deliver the event or show the restriction UI.
     */
    fun shouldBlock(
        snapshot: Snapshot,
        packageName: String,
        ownPackage: String,
        nowMillis: Long
    ): Boolean {
        if (packageName.isBlank()) return false
        if (!ContextEngine.focusActive(snapshot.active, snapshot.focusEnabled)) return false
        if (unlockActive(snapshot, nowMillis)) return false
        if (FocusSafety.isProtected(packageName, ownPackage)) return false
        return packageName in snapshot.restrictedPackages
    }

    /** Foreground events carry only a package, so restriction is by package. */
    fun restrictedPackages(restricted: Set<ComponentKey>): Set<String> =
        restricted.mapTo(HashSet()) { it.packageName }
}

/**
 * Packages Focus Protection must never send the user back from, no matter
 * what the restriction set contains. Blocking any of these would trap the
 * user (Settings, permission screens), break uninstalling a restricted app
 * (package installer), or interfere with calling and system UI.
 */
object FocusSafety {
    val PROTECTED_PACKAGES: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )

    fun isProtected(packageName: String, ownPackage: String): Boolean =
        packageName == ownPackage || packageName in PROTECTED_PACKAGES
}

/**
 * Loop guard for foreground interventions.
 *
 * Window-state events fire repeatedly (every in-app navigation, every
 * configuration change), and sending the user home itself generates more
 * events. Without a guard, one blocked app would stack restriction screens
 * forever. A package earns at most one intervention per cooldown window; a
 * *different* restricted package still intervenes immediately, and returning
 * to the same app after the window re-blocks it.
 *
 * The clock is injectable so the behavior is unit tested without sleeps.
 */
class FocusInterventionGate(
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var lastPackage: String? = null
    private var lastMillis: Long = 0L

    @Synchronized
    fun shouldIntervene(packageName: String): Boolean {
        val now = clock()
        if (packageName == lastPackage && now - lastMillis < cooldownMillis) return false
        lastPackage = packageName
        lastMillis = now
        return true
    }

    @Synchronized
    fun reset() {
        lastPackage = null
        lastMillis = 0L
    }

    companion object {
        const val DEFAULT_COOLDOWN_MILLIS = 2000L
    }
}
