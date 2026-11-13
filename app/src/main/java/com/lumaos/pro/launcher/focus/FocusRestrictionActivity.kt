package com.lumaos.pro.launcher.focus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.context.ContextEngine
import com.lumaos.pro.launcher.context.FocusEnforcement
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.launcher.home.FocusRestrictionView
import com.lumaos.pro.launcher.settings.SettingsActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Native LumaOS restriction screen for enforcement layer 2.
 *
 * Started by [com.lumaos.pro.launcher.context.FocusAccessibilityService] after
 * it sends a restricted foreground app to Home. Reuses [FocusRestrictionView],
 * so both layers render the identical experience, and adds the emergency
 * temporary unlock plus a guaranteed return-to-Home.
 *
 * Anti-trap and anti-loop rules, all enforced here rather than trusted to
 * callers:
 *
 *  - `singleTask` (manifest): repeated interventions update this instance via
 *    `onNewIntent` instead of stacking activities;
 *  - every presentation re-evaluates [FocusEnforcement] fresh from DataStore
 *    and finishes immediately when there is nothing to enforce (stale start,
 *    session ended, toggle off, unlock granted elsewhere);
 *  - `onResume` re-checks for the same reason: disabling Focus Protection in
 *    Settings and returning here must not strand a dead restriction screen;
 *  - Back and Dismiss always send Home first, so this screen can never drop
 *    the user back into the blocked app — and finishing is unconditional, so
 *    it can never trap them either.
 */
class FocusRestrictionActivity : ComponentActivity() {

    private lateinit var restriction: FocusRestrictionView
    private var blockedPackage: String = ""
    private var blockedLabel: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        restriction = FocusRestrictionView(this)
        setContentView(
            restriction,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = returnHome()
        })
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // The session may have ended (or an unlock granted) while this screen
        // was behind Settings: re-evaluate rather than showing stale UI.
        lifecycleScope.launch {
            if (!stillBlocked()) finish()
        }
    }

    private fun handleIntent(intent: Intent?) {
        blockedPackage = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        blockedLabel = intent?.getStringExtra(EXTRA_LABEL)?.takeIf { it.isNotBlank() } ?: blockedPackage
        lifecycleScope.launch {
            val core = (application as LumaApp).core
            val snapshot = readSnapshot(core.prefs)
            if (snapshot == null || !FocusEnforcement.shouldBlock(
                    snapshot, blockedPackage, packageName, System.currentTimeMillis()
                )
            ) {
                finish()
                return@launch
            }
            val minutes = core.prefs.focusUnlockMinutes().first()
            restriction.show(
                theme = core.themes.theme.value,
                appLabel = blockedLabel,
                contextTitle = snapshot.active.title,
                onDismiss = { returnHome() },
                onManage = {
                    startActivity(Intent(this@FocusRestrictionActivity, SettingsActivity::class.java))
                },
                onUnlock = { grantUnlock(minutes) },
                unlockLabel = getString(R.string.focus_unlock_button, minutes)
            )
        }
    }

    private suspend fun stillBlocked(): Boolean {
        if (blockedPackage.isBlank()) return false
        val prefs = (application as LumaApp).core.prefs
        val snapshot = readSnapshot(prefs) ?: return false
        return FocusEnforcement.shouldBlock(snapshot, blockedPackage, packageName, System.currentTimeMillis())
    }

    private suspend fun readSnapshot(prefs: com.lumaos.pro.launcher.core.prefs.LumaPrefs): FocusEnforcement.Snapshot? {
        val active = prefs.activeContextProfile.first()
        if (active !in ContextEngine.FOCUS_PROFILES) return null
        return FocusEnforcement.Snapshot(
            active = active,
            focusEnabled = prefs.focusEnabled(active).first(),
            restrictedPackages = FocusEnforcement.restrictedPackages(prefs.focusRestricted(active).first()),
            unlockProfile = prefs.focusUnlockProfile().first(),
            unlockUntilMillis = prefs.focusUnlockUntil().first()
        )
    }

    private fun grantUnlock(minutes: Int) {
        lifecycleScope.launch {
            val prefs = (application as LumaApp).core.prefs
            val active = prefs.activeContextProfile.first()
            prefs.setFocusUnlock(active, System.currentTimeMillis() + minutes * 60_000L)
            Toast.makeText(
                this@FocusRestrictionActivity,
                getString(R.string.focus_unlock_granted, minutes),
                Toast.LENGTH_SHORT
            ).show()
            // Honor the tap that led here: the user asked for this app.
            val launch = packageManager.getLaunchIntentForPackage(blockedPackage)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launch != null) {
                runCatching { startActivity(launch) }.onFailure {
                    LumaLog.w("Unlocked app could not be opened")
                }
            }
            finish()
        }
    }

    private fun returnHome() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "com.lumaos.pro.launcher.extra.FOCUS_PACKAGE"
        const val EXTRA_LABEL = "com.lumaos.pro.launcher.extra.FOCUS_LABEL"

        fun intent(context: Context, packageName: String, label: String): Intent =
            Intent(context.applicationContext, FocusRestrictionActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_LABEL, label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
