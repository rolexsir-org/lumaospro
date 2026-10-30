package com.lumaos.pro.launcher.controls

/**
 * Pure state model for the LumaOS Pro Quick Controls panel.
 *
 * Everything in this file is free of Android imports on purpose: the panel's
 * honesty rules (what a tile may claim, how a slider maps to a system value,
 * which route to take when Android forbids a direct write) are decisions that
 * must be unit-testable on the JVM. [DeviceControls] supplies the real platform
 * readings and performs the real platform writes; this file only describes and
 * reasons about them.
 *
 * The single design rule is that a control never claims a state it cannot
 * prove: `on == null` means "Android will not tell us", [Capability.SYSTEM_ROUTE]
 * means "Android will not let a launcher do this directly", and the UI renders
 * both truthfully instead of inventing a switch position.
 */

/** How much authority LumaOS Pro actually has over one control. */
enum class Capability {
    /** The value can be read and written with public Android APIs. */
    DIRECT,

    /**
     * Android reserves the write for the system, so the control opens the real
     * system panel or settings page. The displayed state is still the device's.
     */
    SYSTEM_ROUTE,

    /** A user grant is missing (WRITE_SETTINGS, notification policy access). */
    NEEDS_GRANT,

    /** No camera flash, no resolvable activity, no such feature on this device. */
    UNAVAILABLE
}

/** A binary control. `on == null` is an explicit "unknown", never a hidden false. */
data class SwitchState(
    val on: Boolean?,
    val capability: Capability,
    /** Short truthful detail line, e.g. the interruption filter name. */
    val detail: String? = null
) {
    companion object {
        val UNKNOWN = SwitchState(on = null, capability = Capability.UNAVAILABLE)

        fun direct(on: Boolean, detail: String? = null) = SwitchState(on, Capability.DIRECT, detail)
        fun systemRoute(on: Boolean?, detail: String? = null) = SwitchState(on, Capability.SYSTEM_ROUTE, detail)
        fun needsGrant(on: Boolean?, detail: String? = null) = SwitchState(on, Capability.NEEDS_GRANT, detail)
        fun unavailable(detail: String? = null) = SwitchState(on = null, capability = Capability.UNAVAILABLE, detail = detail)
    }
}

/** Android's interruption filter, mirrored without the platform constants. */
enum class FocusFilter { ALL, PRIORITY, ALARMS_ONLY, TOTAL_SILENCE, UNKNOWN }

/** A bounded slider: brightness or one audio stream. */
data class SliderState(
    val value: Int,
    val min: Int,
    val max: Int,
    val capability: Capability,
    /** True while Android owns the value (automatic brightness). */
    val automatic: Boolean = false
) {
    val span: Int get() = (max - min).coerceAtLeast(0)

    /** 0f..1f, safe for a zero span. */
    val fraction: Float get() = if (span <= 0) 0f else ((value - min).toFloat() / span).coerceIn(0f, 1f)

    companion object {
        val UNAVAILABLE = SliderState(0, 0, 0, Capability.UNAVAILABLE)
    }
}

/** Real battery reading from Android; never a hard-coded percentage. */
data class BatteryReading(
    /** Null when the sticky broadcast reported no level. */
    val percent: Int?,
    val charging: Boolean
) {
    companion object {
        val UNKNOWN = BatteryReading(percent = null, charging = false)
    }
}

/** Playback state of the session Android actually reports. */
data class MediaSnapshot(
    val title: String?,
    val artist: String?,
    val playing: Boolean,
    val canPlayPause: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    /** True when the platform supplied artwork for this session. */
    val hasArtwork: Boolean
) {
    companion object {
        val EMPTY = MediaSnapshot(null, null, false, false, false, false, false)
    }
}

/** One control plus everything the panel needs to render and act on it. */
data class QuickControlsSnapshot(
    val wifi: SwitchState = SwitchState.UNKNOWN,
    val mobileData: SwitchState = SwitchState.UNKNOWN,
    val bluetooth: SwitchState = SwitchState.UNKNOWN,
    val airplane: SwitchState = SwitchState.UNKNOWN,
    val hotspot: SwitchState = SwitchState.UNKNOWN,
    val torch: SwitchState = SwitchState.UNKNOWN,
    val rotationLock: SwitchState = SwitchState.UNKNOWN,
    val focus: SwitchState = SwitchState.UNKNOWN,
    val focusFilter: FocusFilter = FocusFilter.UNKNOWN,
    val brightness: SliderState = SliderState.UNAVAILABLE,
    val volume: SliderState = SliderState.UNAVAILABLE,
    val battery: BatteryReading = BatteryReading.UNKNOWN,
    val media: MediaSnapshot? = null,
    val castAvailable: Boolean = false,
    val cameraAvailable: Boolean = false,
    /** True while the WRITE_SETTINGS app-op is granted to LumaOS Pro. */
    val canWriteSystemSettings: Boolean = false,
    /** True while Android allows changing the interruption filter. */
    val hasNotificationPolicyAccess: Boolean = false
) {
    companion object {
        val EMPTY = QuickControlsSnapshot()
    }
}

/** The tiles the panel renders, in order, and what each one does when tapped. */
enum class TileId {
    WIFI, MOBILE_DATA, BLUETOOTH, AIRPLANE, HOTSPOT, TORCH, ROTATION_LOCK, FOCUS, CAST, CAMERA
}

/**
 * Layout and policy decisions for the panel.
 *
 * Pure functions over values the platform layer supplies, so the panel's
 * geometry and its "is this control honest?" rules are covered by JVM tests.
 */
object QuickControlsPolicy {

    /** Tiles that need a real reading before they may claim a position. */
    private val CONNECTIVITY = listOf(TileId.WIFI, TileId.MOBILE_DATA, TileId.BLUETOOTH, TileId.AIRPLANE)
    private val DEVICE = listOf(TileId.TORCH, TileId.ROTATION_LOCK, TileId.FOCUS, TileId.CAST)

    /**
     * Tile columns for the available width. The panel keeps 4 columns on phones
     * so every tile stays at least 72dp wide and its label fits on one line;
     * wider screens gain columns rather than stretching tiles.
     */
    fun tileColumnsFor(availableWidthDp: Int): Int = when {
        availableWidthDp >= 720 -> 6
        availableWidthDp >= 560 -> 5
        availableWidthDp >= 400 -> 4
        availableWidthDp >= 300 -> 3
        else -> 2
    }

    /**
     * Tiles to render for this device, in order.
     *
     * A tile is dropped only when the device genuinely cannot do the thing at
     * all (no flash, no cast destination, no camera app). A tile Android merely
     * refuses to let us *write* stays, because opening the real system page is a
     * legitimate, useful action - and hiding it would leave the user hunting.
     * [QuickControlsPanel] never renders a tile whose state is invented.
     */
    fun tilesFor(snapshot: QuickControlsSnapshot, torchCapable: Boolean): List<TileId> {
        val tiles = ArrayList<TileId>(8)
        tiles += CONNECTIVITY
        if (torchCapable) tiles += TileId.TORCH
        tiles += TileId.ROTATION_LOCK
        tiles += TileId.FOCUS
        // The two destinations are only offered when this device really has one:
        // an unresolvable camera or cast page must not become a dead tile. Both
        // intents are declared in <queries>, so the probe is not defeated by
        // Android 11+ package-visibility filtering.
        if (snapshot.castAvailable) tiles += TileId.CAST
        if (snapshot.cameraAvailable) tiles += TileId.CAMERA
        return tiles
    }

    /** True when a tile's tap must go through Android's own UI rather than a write. */
    fun opensSystemRoute(id: TileId, snapshot: QuickControlsSnapshot): Boolean = when (id) {
        TileId.WIFI -> snapshot.wifi.capability != Capability.DIRECT
        TileId.MOBILE_DATA -> snapshot.mobileData.capability != Capability.DIRECT
        TileId.BLUETOOTH -> snapshot.bluetooth.capability != Capability.DIRECT
        TileId.AIRPLANE -> snapshot.airplane.capability != Capability.DIRECT
        TileId.HOTSPOT -> true
        TileId.TORCH -> snapshot.torch.capability != Capability.DIRECT
        TileId.ROTATION_LOCK -> snapshot.rotationLock.capability != Capability.DIRECT
        TileId.FOCUS -> snapshot.focus.capability != Capability.DIRECT
        TileId.CAST -> true
        TileId.CAMERA -> true
    }

    /**
     * Maps a 0f..1f slider position onto a system level range.
     *
     * Brightness is special: Android's usable floor is 1 on most devices and a
     * written 0 can leave the screen unreadable, so level 0 is never produced
     * for a manual brightness write. Volume keeps its full legal range,
     * including 0 (mute), because that is a real user intent.
     */
    fun sliderToLevel(fraction: Float, min: Int, max: Int, allowZero: Boolean): Int {
        if (max <= min) return min.coerceAtLeast(0)
        val span = max - min
        val raw = min + (fraction.coerceIn(0f, 1f) * span).toInt()
        val floor = if (allowZero) min else min.coerceAtLeast(1)
        return raw.coerceIn(floor, max)
    }

    /** Inverse of [sliderToLevel] for rendering an existing system value. */
    fun levelToFraction(value: Int, min: Int, max: Int): Float {
        if (max <= min) return 0f
        return ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    }

    /** Battery percentage from Android's level/scale pair, or null when unusable. */
    fun batteryPercent(level: Int, scale: Int): Int? =
        if (level < 0 || scale <= 0) null else (level * 100 / scale).coerceIn(0, 100)

    /** True when the battery reading is worth showing as a number. */
    fun showBatteryPercent(reading: BatteryReading?): Boolean =
        reading?.percent != null && reading.percent in 0..100
}

/**
 * Keeps the Quick Controls Focus tile and the existing context-profile DND
 * automation ([com.lumaos.pro.launcher.context.ContextAutomation]) from fighting
 * over one system setting.
 *
 * The automation records that *it* moved the interruption filter so it can put
 * the device back when the profile ends. If the user moves the filter by hand,
 * that bookkeeping is stale: the automation must stop claiming ownership, or it
 * would silently change a filter the user chose.
 */
object DndCoordination {

    /** What the automation's ownership flag should become after a manual change. */
    data class Decision(
        val managed: Boolean,
        /** Filter to restore when the managed profile ends; null keeps the stored one. */
        val restoreFilter: Int? = null
    )

    /**
     * @param managed the automation currently owns the filter.
     * @param previousFilter the filter before the user's change (platform constant).
     * @param newFilter the filter after the user's change (platform constant).
     * @param filterAll platform constant for "no do not disturb".
     */
    fun afterUserChange(
        managed: Boolean,
        previousFilter: Int,
        newFilter: Int,
        filterAll: Int,
        policyAccessGranted: Boolean
    ): Decision {
        if (!policyAccessGranted) return Decision(managed)
        // The user switched focus off: the automation no longer owns the filter
        // and must not "restore" a value the user just replaced.
        if (newFilter == filterAll) return Decision(managed = false)
        // The user switched focus on from a clean state: remember what to give
        // back, exactly as the automation would have.
        if (!managed && previousFilter == filterAll) {
            return Decision(managed = true, restoreFilter = previousFilter)
        }
        return Decision(managed)
    }
}

/**
 * Torch availability, decided before the panel offers the tile at all.
 *
 * A device with no flash unit, or a camera another app is holding, cannot be
 * toggled; showing a switch that cannot move would be a fake control.
 */
object TorchPolicy {

    enum class Verdict { AVAILABLE, NO_FLASH_UNIT, NO_CAMERA, IN_USE_BY_CAMERA_APP }

    fun verdict(cameraCount: Int, flashAvailable: Boolean, cameraInUse: Boolean): Verdict = when {
        cameraCount <= 0 -> Verdict.NO_CAMERA
        !flashAvailable -> Verdict.NO_FLASH_UNIT
        cameraInUse -> Verdict.IN_USE_BY_CAMERA_APP
        else -> Verdict.AVAILABLE
    }

    /** True while the torch may be driven through `CameraManager.setTorchMode`. */
    fun canToggle(verdict: Verdict): Boolean = verdict == Verdict.AVAILABLE
}
