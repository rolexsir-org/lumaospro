package com.lumaos.pro.launcher.controls

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.widgets.music.MusicNotificationListener

/** What happened when the user tapped a tile. Rendered honestly by the panel. */
sealed interface TileTapOutcome {
    /** LumaOS Pro changed the device state itself. */
    data class Toggled(val on: Boolean) : TileTapOutcome

    /** Android's own panel or settings page was opened. */
    data object OpenedSystemRoute : TileTapOutcome

    /** A user grant is required; the panel explains it and offers the route. */
    data class NeedsGrant(@StringRes val explanation: Int) : TileTapOutcome

    /** This device cannot do it at all. */
    data class Unavailable(@StringRes val reason: Int) : TileTapOutcome
}

/**
 * The platform half of Quick Controls: real readings, real writes, and real
 * system destinations for everything Android reserves to itself.
 *
 * Rules this class obeys, because a launcher that lies about device state is
 * worse than one without the tile:
 *
 *  - a state it cannot read is reported as `on == null`, never as off;
 *  - a write Android forbids a third-party app is never attempted silently; the
 *    tile opens the real system panel and keeps showing the device's own state;
 *  - every platform call is guarded, because OEM builds routinely throw on
 *    settings reads that AOSP documents as safe;
 *  - observation is event-driven (broadcasts, torch callbacks, media-session
 *    callbacks) and only while the panel is open. There is no polling loop.
 *
 * Permissions: `ACCESS_WIFI_STATE` and `ACCESS_NETWORK_STATE` are install-time
 * (normal) and only read state. `WRITE_SETTINGS` is an app-op the user grants on
 * an Android screen; it powers brightness and rotation lock and is never assumed.
 * Notification policy access (Focus) is granted on its own Android screen.
 * No Bluetooth runtime permission is requested: on Android 13+ reading the
 * adapter state needs `BLUETOOTH_CONNECT`, so the tile reports "unknown" and
 * opens Android's Bluetooth page instead of prompting for a radio permission.
 */
class DeviceControls(private val context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val notificationManager: NotificationManager?
        get() = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val audioManager: AudioManager?
        get() = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val wifiManager: WifiManager?
        get() = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager: ConnectivityManager?
        get() = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val cameraManager: CameraManager?
        get() = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    // ── Torch ──────────────────────────────────────────────────────────────

    /** Camera id whose flash unit drives the tile, or null when there is none. */
    private var torchCameraId: String? = null
    private var torchOn: Boolean? = null
    private var torchUnavailable = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                torchOn = enabled
                torchUnavailable = false
                notifyChanged()
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchCameraId) {
                torchUnavailable = true
                notifyChanged()
            }
        }
    }

    private var torchCallbackRegistered = false

    /**
     * Capability probe. Runs once per panel open: the camera list does not change
     * while a launcher is in the foreground, and re-querying it per frame would be
     * exactly the kind of background work this panel must not do.
     */
    fun torchVerdict(): TorchPolicy.Verdict {
        val manager = cameraManager ?: return TorchPolicy.Verdict.NO_CAMERA
        val ids = runCatching { manager.cameraIdList }.getOrElse {
            LumaLog.w("Camera list could not be read for the torch tile")
            return TorchPolicy.Verdict.NO_CAMERA
        }
        val withFlash = ids.firstOrNull { id ->
            runCatching {
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }.getOrDefault(false)
        }
        torchCameraId = withFlash
        if (withFlash == null) return TorchPolicy.Verdict.NO_FLASH_UNIT
        if (torchUnavailable) return TorchPolicy.Verdict.IN_USE_BY_CAMERA_APP
        return TorchPolicy.Verdict.AVAILABLE
    }

    fun torchCapable(): Boolean = TorchPolicy.canToggle(torchVerdict())

    fun setTorch(on: Boolean): Boolean {
        val manager = cameraManager ?: return false
        val id = torchCameraId ?: torchVerdict().let { torchCameraId } ?: return false
        val result = runCatching { manager.setTorchMode(id, on) }
        result.onFailure { LumaLog.w("Torch could not be switched") }
        if (result.isSuccess) {
            torchOn = on
            torchUnavailable = false
        }
        return result.isSuccess
    }

    // ── Snapshot ───────────────────────────────────────────────────────────

    /** One complete, truthful reading of everything the panel shows. */
    fun snapshot(media: MediaSnapshot?, mediaActive: Boolean): QuickControlsSnapshot = QuickControlsSnapshot(
        wifi = wifiState(),
        mobileData = mobileDataState(),
        bluetooth = bluetoothState(),
        airplane = airplaneState(),
        hotspot = hotspotState(),
        torch = torchState(),
        rotationLock = rotationLockState(),
        focus = focusState(),
        focusFilter = focusFilter(),
        brightness = brightnessState(),
        volume = volumeState(),
        battery = batteryReading(),
        media = if (mediaActive) media else null,
        castAvailable = isResolvable(castIntent()),
        cameraAvailable = isResolvable(cameraIntent()),
        canWriteSystemSettings = canWriteSystemSettings(),
        hasNotificationPolicyAccess = hasNotificationPolicyAccess()
    )

    private fun wifiState(): SwitchState {
        val enabled = runCatching { wifiManager?.isWifiEnabled }.getOrNull() ?: return SwitchState.unavailable()
        // Android 10 removed the third-party Wi-Fi write; the system panel is the
        // supported route and it does toggle Wi-Fi for real.
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            SwitchState.direct(enabled)
        } else {
            SwitchState.systemRoute(enabled)
        }
    }

    private fun mobileDataState(): SwitchState {
        val on = runCatching {
            val network = connectivityManager?.activeNetwork ?: return@runCatching null
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }.getOrNull()
        // Only the system can switch the radio; the panel reports what is live.
        return SwitchState.systemRoute(on)
    }

    private fun bluetoothState(): SwitchState {
        val adapter = runCatching {
            (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        }.getOrNull() ?: return SwitchState.systemRoute(null)
        val enabled = runCatching { adapter.isEnabled }.getOrNull()
        // null means Android refused the read (a missing BLUETOOTH_CONNECT on
        // API 33+); the tile then shows "system-managed", not "off".
        return SwitchState.systemRoute(enabled)
    }

    private fun airplaneState(): SwitchState {
        val on = runCatching {
            Settings.Global.getInt(appContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        }.getOrNull() ?: return SwitchState.systemRoute(null)
        return SwitchState.systemRoute(on)
    }

    private fun hotspotState(): SwitchState {
        // Android exposes no public hotspot state or toggle to third-party apps.
        return SwitchState(on = null, capability = Capability.SYSTEM_ROUTE)
    }

    private fun torchState(): SwitchState {
        val verdict = torchVerdict()
        return when {
            !TorchPolicy.canToggle(verdict) -> SwitchState(
                on = torchOn,
                capability = Capability.UNAVAILABLE,
                detail = null
            )
            else -> SwitchState(torchOn, Capability.DIRECT)
        }
    }

    private fun rotationLockState(): SwitchState {
        val locked = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
        }.getOrNull() ?: return SwitchState.systemRoute(null)
        return if (canWriteSystemSettings()) SwitchState.direct(locked) else SwitchState.needsGrant(locked)
    }

    private fun focusFilter(): FocusFilter = when (runCatching { notificationManager?.currentInterruptionFilter }.getOrNull()) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> FocusFilter.ALL
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> FocusFilter.PRIORITY
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> FocusFilter.ALARMS_ONLY
        NotificationManager.INTERRUPTION_FILTER_NONE -> FocusFilter.TOTAL_SILENCE
        else -> FocusFilter.UNKNOWN
    }

    private fun focusState(): SwitchState {
        val filter = focusFilter()
        val on = if (filter == FocusFilter.UNKNOWN) null else filter != FocusFilter.ALL
        return if (hasNotificationPolicyAccess()) {
            SwitchState.direct(on, detail = null)
        } else {
            SwitchState.needsGrant(on)
        }
    }

    private fun brightnessState(): SliderState {
        val resolver = appContext.contentResolver
        val automatic = runCatching {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }.getOrDefault(false)
        val value = runCatching {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, DEFAULT_MAX_BRIGHTNESS)
        }.getOrNull() ?: return SliderState.UNAVAILABLE
        val capability = when {
            canWriteSystemSettings() -> Capability.DIRECT
            else -> Capability.NEEDS_GRANT
        }
        return SliderState(
            value = value.coerceIn(0, DEFAULT_MAX_BRIGHTNESS),
            min = 0,
            max = DEFAULT_MAX_BRIGHTNESS,
            capability = capability,
            automatic = automatic
        )
    }

    private fun volumeState(): SliderState {
        val manager = audioManager ?: return SliderState.UNAVAILABLE
        val max = runCatching { manager.getStreamMaxVolume(MUSIC_STREAM) }.getOrNull()
            ?: return SliderState.UNAVAILABLE
        val current = runCatching { manager.getStreamVolume(MUSIC_STREAM) }.getOrNull() ?: 0
        return SliderState(
            value = current.coerceIn(0, max),
            min = 0,
            max = max.coerceAtLeast(0),
            // setStreamVolume on STREAM_MUSIC needs no permission.
            capability = Capability.DIRECT
        )
    }

    private fun batteryReading(): BatteryReading {
        val sticky = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        if (sticky != null) {
            val percent = QuickControlsPolicy.batteryPercent(
                sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            )
            val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            if (percent != null) return BatteryReading(percent, charging)
        }
        // Fallback for OEM builds that do not hand out the sticky broadcast.
        val capacity = runCatching {
            (appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrNull()
        return if (capacity != null && capacity > 0) BatteryReading(capacity.coerceIn(0, 100), false)
        else BatteryReading.UNKNOWN
    }

    fun canWriteSystemSettings(): Boolean =
        runCatching { Settings.System.canWrite(appContext) }.getOrDefault(false)

    fun hasNotificationPolicyAccess(): Boolean =
        runCatching { notificationManager?.isNotificationPolicyAccessGranted == true }.getOrDefault(false)

    // ── Writes ─────────────────────────────────────────────────────────────

    /**
     * Applies a brightness slider position. Returns false when Android refused;
     * the panel then keeps the device's real value rather than the dragged one.
     */
    fun applyBrightness(fraction: Float): Boolean {
        if (!canWriteSystemSettings()) return false
        val level = QuickControlsPolicy.sliderToLevel(fraction, 0, DEFAULT_MAX_BRIGHTNESS, allowZero = false)
        val resolver = appContext.contentResolver
        // Leaving automatic mode on would make the write invisible, so a manual
        // drag first switches Android to manual brightness - the documented,
        // user-visible consequence of taking control.
        val switched = runCatching {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        }.getOrDefault(false)
        val written = runCatching {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, level)
        }.getOrDefault(false)
        if (!written) LumaLog.w("System brightness could not be written")
        if (!switched) LumaLog.w("Brightness mode could not be set to manual")
        return written
    }

    /** True while Android owns brightness (the slider shows, but does not write). */
    fun automaticBrightness(): Boolean = runCatching {
        Settings.System.getInt(
            appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrDefault(false)

    fun applyVolume(fraction: Float): Boolean {
        val manager = audioManager ?: return false
        val max = runCatching { manager.getStreamMaxVolume(MUSIC_STREAM) }.getOrNull() ?: return false
        // Mute is a real intent for media volume, so zero is allowed here.
        val level = QuickControlsPolicy.sliderToLevel(fraction, 0, max, allowZero = true)
        return runCatching { manager.setStreamVolume(MUSIC_STREAM, level, 0); true }
            .getOrElse {
                LumaLog.w("Media stream volume could not be set")
                false
            }
    }

    fun applyRotationLock(lock: Boolean): Boolean {
        if (!canWriteSystemSettings()) return false
        val resolver = appContext.contentResolver
        return runCatching {
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (lock) 0 else 1)
        }.getOrElse {
            LumaLog.w("Rotation lock could not be written")
            false
        }
    }

    /**
     * Sets Android's interruption filter. Returns the filter actually in force,
     * or null when the write was refused - the caller must not assume success.
     */
    fun applyFocusFilter(filter: Int): Int? {
        val manager = notificationManager ?: return null
        if (!hasNotificationPolicyAccess()) return null
        val before = runCatching { manager.currentInterruptionFilter }.getOrNull() ?: return null
        val ok = runCatching { manager.setInterruptionFilter(filter); true }
            .getOrElse {
                LumaLog.w("Interruption filter could not be changed")
                false
            }
        if (!ok) return null
        // Some OEM builds accept the call and ignore it; re-read to be sure.
        return runCatching { manager.currentInterruptionFilter }.getOrNull()?.takeIf { it != before || it == filter }
    }

    fun currentInterruptionFilter(): Int =
        runCatching { notificationManager?.currentInterruptionFilter }
            .getOrNull() ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN

    // ── Tile taps ──────────────────────────────────────────────────────────

    /**
     * Performs the real action behind a tile. Every branch either changes device
     * state or opens Android's own UI; none of them only animates.
     */
    fun onTileTap(id: TileId): TileTapOutcome = when (id) {
        TileId.WIFI -> toggleWifi()
        TileId.MOBILE_DATA -> openRoute(mobileDataIntent(), R.string.qc_route_mobile_data)
        TileId.BLUETOOTH -> openRoute(bluetoothIntent(), R.string.qc_route_bluetooth)
        TileId.AIRPLANE -> openRoute(airplaneIntent(), R.string.qc_route_airplane)
        TileId.HOTSPOT -> openRoute(hotspotIntent(), R.string.qc_route_hotspot)
        TileId.TORCH -> toggleTorch()
        TileId.ROTATION_LOCK -> toggleRotationLock()
        TileId.FOCUS -> toggleFocus()
        TileId.CAST -> openRoute(castIntent(), R.string.qc_route_cast, displayFallbackIntent())
        TileId.CAMERA -> openRoute(cameraIntent(), R.string.qc_unavailable_camera)
    }

    private fun toggleWifi(): TileTapOutcome {
        val manager = wifiManager ?: return TileTapOutcome.Unavailable(R.string.qc_unavailable_wifi)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return openRoute(wifiPanelIntent(), R.string.qc_route_wifi)
        }
        val current = runCatching { manager.isWifiEnabled }.getOrNull()
            ?: return openRoute(wifiPanelIntent(), R.string.qc_route_wifi)
        @Suppress("DEPRECATION")
        val ok = runCatching { manager.setWifiEnabled(!current) }.getOrDefault(false)
        return if (ok) TileTapOutcome.Toggled(!current)
        else openRoute(wifiPanelIntent(), R.string.qc_route_wifi)
    }

    private fun toggleTorch(): TileTapOutcome {
        val verdict = torchVerdict()
        if (!TorchPolicy.canToggle(verdict)) {
            val reason = when (verdict) {
                TorchPolicy.Verdict.NO_CAMERA, TorchPolicy.Verdict.NO_FLASH_UNIT -> R.string.qc_unavailable_torch
                TorchPolicy.Verdict.IN_USE_BY_CAMERA_APP -> R.string.qc_torch_in_use
                TorchPolicy.Verdict.AVAILABLE -> R.string.qc_unavailable_torch
            }
            return TileTapOutcome.Unavailable(reason)
        }
        val next = torchOn != true
        return if (setTorch(next)) TileTapOutcome.Toggled(next)
        else TileTapOutcome.Unavailable(R.string.qc_torch_failed)
    }

    private fun toggleRotationLock(): TileTapOutcome {
        if (!canWriteSystemSettings()) {
            return TileTapOutcome.NeedsGrant(R.string.qc_grant_write_settings)
        }
        val locked = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
        }.getOrDefault(false)
        return if (applyRotationLock(!locked)) TileTapOutcome.Toggled(!locked)
        else TileTapOutcome.Unavailable(R.string.qc_rotation_failed)
    }

    private fun toggleFocus(): TileTapOutcome {
        if (!hasNotificationPolicyAccess()) {
            return TileTapOutcome.NeedsGrant(R.string.qc_grant_notification_policy)
        }
        val current = focusFilter()
        val target = if (current == FocusFilter.ALL) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        val applied = applyFocusFilter(target) ?: return TileTapOutcome.Unavailable(R.string.qc_focus_failed)
        return TileTapOutcome.Toggled(applied != NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    private fun openRoute(
        intent: Intent?,
        @StringRes unavailable: Int,
        fallback: Intent? = null
    ): TileTapOutcome = when {
        intent != null && launch(intent) -> TileTapOutcome.OpenedSystemRoute
        fallback != null && launch(fallback) -> TileTapOutcome.OpenedSystemRoute
        else -> TileTapOutcome.Unavailable(unavailable)
    }

    // ── System destinations ────────────────────────────────────────────────

    fun wifiPanelIntent(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        panelIntent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    } else {
        Intent(Settings.ACTION_WIFI_SETTINGS)
    }

    fun mobileDataIntent(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        panelIntent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    } else {
        Intent(Settings.ACTION_WIRELESS_SETTINGS)
    }

    fun hotspotIntent(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        panelIntent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    } else {
        Intent(Settings.ACTION_WIRELESS_SETTINGS)
    }

    fun bluetoothIntent(): Intent? = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

    fun airplaneIntent(): Intent? = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)

    /**
     * Android's cast screen. Some builds do not ship one, so the caller passes
     * [displayFallbackIntent] as a second choice rather than this method guessing
     * with `resolveActivity`: on Android 11+ package-visibility filtering can hide
     * a system destination from resolution even though starting it works.
     */
    fun castIntent(): Intent = Intent(Settings.ACTION_CAST_SETTINGS)

    fun displayFallbackIntent(): Intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)

    fun cameraIntent(): Intent? = Intent(MediaStore.INTENT_ACTION_STILL_CAMERA)
        .addCategory(Intent.CATEGORY_DEFAULT)
        .takeIf { isResolvable(it) } ?: Intent(MediaStore.ACTION_IMAGE_CAPTURE)

    /** The Android screen where WRITE_SETTINGS is granted; brightness and rotation need it. */
    fun writeSettingsGrantIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData(Uri.parse("package:${appContext.packageName}"))

    /** The Android screen where notification policy access (Focus) is granted. */
    fun notificationPolicyIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /**
     * A `Settings.Panel` destination.
     *
     * Resolution is deliberately not probed here: panels are served by the system
     * settings package, which package-visibility filtering can hide from
     * `resolveActivity` on Android 11+ even though `startActivity` succeeds. The
     * attempt in [launch] is the verification, and its failure is what makes the
     * tile report "unavailable" instead of a guess.
     */
    private fun panelIntent(action: String): Intent = Intent(action)

    /**
     * Starts a system destination.
     *
     * `Settings.Panel` destinations only render as a panel when they are started
     * from an activity, so the host activity is preferred and the application
     * context (with NEW_TASK) is the fallback for the tile service. The result is
     * verified by the platform rather than assumed: an OEM build without the
     * destination throws, and the tile then reports "unavailable" instead of
     * pretending the panel opened.
     */
    private fun launch(intent: Intent): Boolean = runCatching {
        val host = context
        if (host is android.app.Activity && !host.isFinishing && !host.isDestroyed) {
            host.startActivity(intent)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        }
        true
    }.getOrElse {
        LumaLog.w("System destination could not be opened")
        false
    }

    private fun isResolvable(intent: Intent?): Boolean = intent != null && runCatching {
        intent.resolveActivity(appContext.packageManager) != null
    }.getOrDefault(false)

    // ── Event-driven observation ───────────────────────────────────────────

    private var onChanged: (() -> Unit)? = null
    private var observing = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> notifyChanged()
                else -> notifyChanged()
            }
        }
    }

    private fun notifyChanged() {
        val callback = onChanged ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) callback() else main.post(callback)
    }

    /**
     * Starts listening for the platform events that can change a tile while the
     * panel is open. Registered on open, unregistered on close: the launcher does
     * not keep a receiver alive for a panel nobody is looking at.
     */
    fun startObserving(callback: () -> Unit) {
        onChanged = callback
        if (observing) return
        observing = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            }
        }
        runCatching {
            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onFailure { LumaLog.w("Quick Controls observation could not start") }
        if (torchCapable() && !torchCallbackRegistered) {
            runCatching { cameraManager?.registerTorchCallback(torchCallback, main) }
                .onSuccess { torchCallbackRegistered = true }
                .onFailure { LumaLog.w("Torch observation could not start") }
        }
        // One immediate reading, so the panel opens with the device's real state.
        notifyChanged()
    }

    fun stopObserving() {
        if (!observing) return
        observing = false
        runCatching { appContext.unregisterReceiver(receiver) }
            .onFailure { LumaLog.w("Quick Controls observation could not stop") }
        if (torchCallbackRegistered) {
            runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
            torchCallbackRegistered = false
        }
        onChanged = null
        torchOn = null
        torchUnavailable = false
    }

    /**
     * The notification-listener component LumaOS Pro already owns.
     *
     * Media sessions and the notification shade both need Android's
     * notification-access grant, and Android grants it per component. Reusing the
     * existing component means one grant powers both features and a user who
     * already enabled it for the music widget gets the shade without a second
     * trip to Settings.
     */
    fun listenerComponent(): ComponentName = ComponentName(appContext, MusicNotificationListener::class.java)

    companion object {
        /** The music stream: the one a media panel is expected to change. */
        const val MUSIC_STREAM = AudioManager.STREAM_MUSIC

        /**
         * Android's conventional brightness ceiling. The platform exposes no
         * public maximum, and every AOSP device since API 26 uses 0..255.
         */
        const val DEFAULT_MAX_BRIGHTNESS = 255
    }
}
