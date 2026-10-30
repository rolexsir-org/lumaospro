package com.lumaos.pro.launcher.controls

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.widgets.music.MusicCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

/**
 * Owns the Quick Controls reading/writing lifecycle for as long as the shade is
 * open, and nothing longer.
 *
 * Readings happen on one daemon thread and are published on the main thread with
 * a generation check, so a burst of platform events (a Wi-Fi change plus a battery
 * tick plus a media callback) produces one consistent snapshot instead of several
 * partial ones, and a slow binder call can never block a drag. Writes are
 * throttled during a slider drag and always committed on release.
 *
 * Everything is started by [start] and torn down by [stop]; there is no periodic
 * timer and no work while the shade is closed.
 */
class QuickControlsController(context: Context) {

    private val appContext = context.applicationContext
    private val devices = DeviceControls(context)
    private val media = MediaSessionSource(context)
    private val main = Handler(Looper.getMainLooper())

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "luma-quick-controls").apply { isDaemon = true }
    }

    private val _state = MutableStateFlow(QuickControlsSnapshot.EMPTY)
    val state: StateFlow<QuickControlsSnapshot> = _state

    @Volatile
    private var generation = 0

    @Volatile
    private var running = false

    /** True while this device has a usable flash unit; decides whether the tile exists. */
    @Volatile
    var torchCapable: Boolean = false
        private set

    private var lastBrightnessWrite = 0L
    private var lastVolumeWrite = 0L

    private val refreshPass = Runnable {
        val stamp = ++generation
        worker.execute {
            val snapshot = read()
            // Only the newest pass may publish; an older result would undo a newer
            // state the user has already seen.
            if (stamp == generation) main.post { _state.value = snapshot }
        }
    }

    private fun read(): QuickControlsSnapshot {
        val capable = runCatching { devices.torchCapable() }.getOrElse {
            LumaLog.w("Torch capability could not be read")
            false
        }
        torchCapable = capable
        val session = runCatching { media.snapshot() }.getOrElse {
            LumaLog.w("The media session could not be read")
            null
        }
        return devices.snapshot(session, mediaActive = true)
    }

    fun start() {
        if (running) return
        running = true
        devices.startObserving { coalesce() }
        media.start { coalesce() }
        coalesce()
    }

    fun stop() {
        if (!running) return
        running = false
        devices.stopObserving()
        media.stop()
        main.removeCallbacks(refreshPass)
        // Invalidates any read already in flight: its generation no longer matches.
        generation++
    }

    /** Releases the worker thread. Called when the host activity is destroyed. */
    fun shutdown() {
        stop()
        worker.shutdown()
    }

    /** Asks for one fresh snapshot, coalesced with any other pending request. */
    fun refresh() {
        if (running) coalesce()
    }

    private fun coalesce() {
        main.removeCallbacks(refreshPass)
        main.postDelayed(refreshPass, COALESCE_MS)
    }

    // ── Actions ────────────────────────────────────────────────────────────

    /** Performs a tile's real action and re-reads the device afterwards. */
    fun onTileTap(id: TileId): TileTapOutcome {
        val outcome = runCatching { devices.onTileTap(id) }.getOrElse {
            LumaLog.e("A Quick Controls tile action failed", it)
            TileTapOutcome.Unavailable(com.lumaos.pro.launcher.R.string.qc_action_failed)
        }
        if (id == TileId.FOCUS && outcome is TileTapOutcome.Toggled) {
            onFocusChangedByUser()
        }
        coalesce()
        return outcome
    }

    /** The system page behind a tile; used by long press and by restricted taps. */
    fun systemRouteFor(id: TileId): Intent? = when (id) {
        TileId.WIFI -> devices.wifiPanelIntent()
        TileId.MOBILE_DATA -> devices.mobileDataIntent()
        TileId.BLUETOOTH -> devices.bluetoothIntent()
        TileId.AIRPLANE -> devices.airplaneIntent()
        TileId.HOTSPOT -> devices.hotspotIntent()
        TileId.CAST -> devices.castIntent()
        TileId.CAMERA -> devices.cameraIntent()
        TileId.TORCH -> null
        TileId.ROTATION_LOCK -> if (devices.canWriteSystemSettings()) null else devices.writeSettingsGrantIntent()
        TileId.FOCUS -> if (devices.hasNotificationPolicyAccess()) null else devices.notificationPolicyIntent()
    }

    /**
     * Brightness from the slider.
     *
     * @return false when Android refused the write; the caller must then restore
     *         the device's real value rather than keep the dragged one.
     */
    fun applyBrightness(fraction: Float, commit: Boolean): Boolean {
        val now = SystemClock.uptimeMillis()
        if (!commit && now - lastBrightnessWrite < WRITE_THROTTLE_MS) return true
        lastBrightnessWrite = now
        val written = devices.applyBrightness(fraction)
        if (!written) LumaLog.w("Brightness was not applied")
        if (commit) coalesce()
        return written
    }

    fun applyVolume(fraction: Float, commit: Boolean): Boolean {
        val now = SystemClock.uptimeMillis()
        if (!commit && now - lastVolumeWrite < WRITE_THROTTLE_MS) return true
        lastVolumeWrite = now
        val written = devices.applyVolume(fraction)
        if (commit) coalesce()
        return written
    }

    /**
     * Reports a user-driven Focus change so the existing context-profile DND
     * automation stops claiming ownership of the filter.
     */
    private fun onFocusChangedByUser() {
        onDndChangedByUser?.invoke(devices.currentInterruptionFilter())
    }

    /** Installed by the host, which owns preferences. */
    var onDndChangedByUser: ((newFilter: Int) -> Unit)? = null

    fun previousInterruptionFilter(): Int = devices.currentInterruptionFilter()

    fun mediaCommand(command: MusicCommand): Boolean {
        val ok = media.command(command)
        if (!ok) LumaLog.w("The player refused a transport command")
        coalesce()
        return ok
    }

    fun mediaArtwork(): Drawable? {
        val bitmap = runCatching { media.artwork() }.getOrNull() ?: return null
        return android.graphics.drawable.BitmapDrawable(appContext.resources, bitmap)
    }

    fun hasNotificationAccess(): Boolean = media.hasAccess()

    fun canWriteSystemSettings(): Boolean = devices.canWriteSystemSettings()

    fun hasNotificationPolicyAccess(): Boolean = devices.hasNotificationPolicyAccess()

    fun writeSettingsIntent(): Intent = devices.writeSettingsGrantIntent()

    fun notificationPolicyIntent(): Intent = devices.notificationPolicyIntent()

    /** Android's interruption-filter constants, for the Focus tile's detail line. */
    object Filters {
        const val ALL = android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        const val PRIORITY = android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
    }

    companion object {
        /** Enough to merge a burst of broadcasts, short enough to feel immediate. */
        private const val COALESCE_MS = 90L

        /** Slider writes during a drag; the release always writes. */
        private const val WRITE_THROTTLE_MS = 70L
    }
}
