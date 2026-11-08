package com.lumaos.pro.launcher.vault.platform

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.vault.core.VaultScreenPresence
import com.lumaos.pro.launcher.vault.core.VaultSession

/**
 * Drives the session's lock rules from real device events.
 *
 * The rules themselves live in [VaultSession] and are unit-tested; this class is
 * only the wiring that turns "the screen went off", "the last vault screen is
 * gone" and "ten minutes of no touches" into calls. Keeping the split means a
 * Robolectric or JVM test can prove the policy without an emulator.
 *
 * It is registered once per process, from [com.lumaos.pro.launcher.core.LumaCore],
 * and it never holds a reference to an Activity beyond the callback that is
 * currently running.
 */
class VaultLifecycleGuard(
    private val context: Context,
    private val manager: VaultManager
) : Application.ActivityLifecycleCallbacks {

    /** Vault screens implement this so the guard only reacts to them. */
    interface VaultScreen

    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var lastScreenOnMs = System.currentTimeMillis()

    /**
     * Decides when a Vault screen departure really means "the user left".
     *
     * Counting screens alone is not enough: `startActivity()` is asynchronous, so
     * the count briefly touches zero during a normal hand-off from the lock screen
     * to the home screen. Conflating that with leaving the app concealed (and, with
     * lock-on-leave, locked) the vault the moment the PIN was accepted.
     */
    private val presence = VaultScreenPresence()

    private val ticker = object : Runnable {
        override fun run() {
            if (manager.session.isUnlocked) {
                manager.session.tick()
                handler.postDelayed(this, TICK_INTERVAL_MS)
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Locked unconditionally and immediately: no grace period, no
                    // "recently used" exception.
                    manager.lock(VaultSession.LockReason.SCREEN_OFF)
                }
                Intent.ACTION_SCREEN_ON -> lastScreenOnMs = System.currentTimeMillis()
                Intent.ACTION_USER_PRESENT -> Unit
            }
        }
    }

    private val sessionListener = object : VaultSession.Listener {
        override fun onUnlocked(keys: VaultSession.Keys) {
            handler.removeCallbacks(ticker)
            handler.postDelayed(ticker, TICK_INTERVAL_MS)
        }

        override fun onLocked(reason: VaultSession.LockReason) {
            handler.removeCallbacks(ticker)
        }
    }

    fun start() {
        if (started) return
        started = true
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(screenReceiver, filter)
        }
        manager.session.addListener(sessionListener)
        LumaLog.d("Vault lifecycle guard attached")
    }

    fun stop() {
        if (!started) return
        started = false
        (context.applicationContext as? Application)?.unregisterActivityLifecycleCallbacks(this)
        runCatching { context.unregisterReceiver(screenReceiver) }
        manager.session.removeListener(sessionListener)
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(settleConceal)
        presence.reset()
    }

    // ------------------------------------------------------------------
    // Activity lifecycle
    // ------------------------------------------------------------------

    override fun onActivityStarted(activity: Activity) {
        if (activity !is VaultScreen) return
        presence.onScreenStarted()
        manager.session.reveal()
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is VaultScreen) return
        manager.session.touch()
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity !is VaultScreen) return
        if (!presence.onScreenStopped()) return
        // The last Vault screen went away. Do not conceal on this frame: the next
        // Vault screen may be starting in the same transition. Give the hand-off
        // window to resolve, then settle.
        handler.removeCallbacks(settleConceal)
        handler.postDelayed(settleConceal, VaultScreenPresence.HANDOFF_WINDOW_MS)
    }

    /**
     * Runs after the hand-off window and conceals only if no Vault screen attached.
     *
     * This is the single place that decides the vault became invisible, so the
     * auto-lock timer and "lock when leaving" both start from a real departure
     * rather than from an activity transition.
     */
    private val settleConceal = object : Runnable {
        override fun run() {
            if (presence.settle()) manager.session.conceal()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        /**
         * How often the idle timeout is evaluated.
         *
         * A 5 s granularity keeps the handler almost free while guaranteeing that
         * a 30 s timeout locks within a few seconds of expiring; the screen-off
         * path does not wait for the ticker at all.
         */
        const val TICK_INTERVAL_MS = 5_000L
    }
}
