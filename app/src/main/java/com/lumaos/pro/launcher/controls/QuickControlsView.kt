package com.lumaos.pro.launcher.controls

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.launcher.home.ThemedDrawables
import com.lumaos.pro.launcher.launcher.home.Typefaces
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.motion.LumaSpring
import com.lumaos.pro.launcher.motion.MotionPolicy
import com.lumaos.pro.launcher.ui.glass.GlassViews
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassColors
import com.lumaos.pro.launcher.ui.theme.GlassPalette
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import com.lumaos.pro.launcher.widgets.music.MusicCommand

/** What the controls section needs from its host. The host owns state and navigation. */
interface QuickControlsHost {
    fun theme(): ResolvedTheme
    fun glassPalette(): GlassPalette
    fun density(): Float
    fun motion(): MotionPolicy
    /** Width available to the panel in dp; drives the tile column count. */
    fun availableWidthDp(): Int
    /** Artwork for the session Android reports, or null. */
    fun mediaArtwork(): Drawable?
    /** True while Android's notification-access grant is in force. */
    fun mediaAccessGranted(): Boolean
    fun onTileTap(id: TileId)
    fun onTileLongPress(id: TileId)
    fun onBrightnessChanged(fraction: Float, commit: Boolean)
    fun onVolumeChanged(fraction: Float, commit: Boolean)
    fun onMediaCommand(command: MusicCommand)
    fun onNeedsWriteSettings()
    fun onNeedsNotificationPolicy()
    fun onNeedsNotificationAccess()
}

/**
 * The Quick Controls section of the LumaOS Pro shade: connectivity tiles,
 * brightness, volume and real media transport.
 *
 * Built in code so every surface, radius, colour and typeface comes from the
 * active Luma Glass material - one design system, not a parallel one for this
 * panel. Geometry is token-driven ([LumaTokens.quickTileHeight], `spaceXS`,
 * `cardPadding`) and the column count comes from
 * [QuickControlsPolicy.tileColumnsFor], so the panel adapts to narrow phones and
 * to wide screens without a second layout.
 *
 * Honesty rules enforced here:
 *  - a tile whose state Android will not report renders neutral with a "system"
 *    marker, never as off;
 *  - a tile that needs a grant shows the device's real state and routes the tap to
 *    the Android screen that grants it;
 *  - a slider Android refuses to let us write stays visible but is not
 *    interactive, with a one-line explanation, so nothing looks operable that is
 *    not;
 *  - media shows only what a real session reports, and a real empty state
 *    otherwise.
 */
class QuickControlsView(
    context: Context,
    private val host: QuickControlsHost
) : LinearLayout(context) {

    private val density = host.density()
    private fun dp(value: Float): Int = (value * density).toInt()

    private val tileGrid = LinearLayout(context)
    private val brightnessRow = SliderRow(context, host, R.string.qc_brightness, R.drawable.ic_rx_brightness, bright = true)
    private val volumeRow = SliderRow(context, host, R.string.qc_volume, R.drawable.ic_rx_volume, bright = false)
    private val mediaCard = LinearLayout(context)
    private val tiles = LinkedHashMap<TileId, TileView>()

    private var renderedTiles: List<TileId> = emptyList()
    private var snapshot: QuickControlsSnapshot = QuickControlsSnapshot.EMPTY

    init {
        orientation = VERTICAL
        addView(tileGrid, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(brightnessRow, rowParams(LumaTokens.spaceSM))
        addView(volumeRow, rowParams(LumaTokens.spaceXS))
        addView(mediaCard, rowParams(LumaTokens.spaceSM))
    }

    private fun rowParams(top: Float) =
        LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
        }

    /** Rebuilds tiles and re-applies the material. Called on open and on theme change. */
    fun rebuild(state: QuickControlsSnapshot, torchCapable: Boolean) {
        snapshot = state
        buildTiles(QuickControlsPolicy.tilesFor(state, torchCapable))
        applyMaterial()
        bind(state)
    }

    /** Updates values only; the view tree stays, so nothing re-measures mid-gesture. */
    fun bind(state: QuickControlsSnapshot) {
        snapshot = state
        tiles.forEach { (id, view) -> view.bind(tileState(id, state)) }
        brightnessRow.bind(state.brightness) { host.onNeedsWriteSettings() }
        volumeRow.bind(state.volume, null)
        bindMedia(state)
    }

    private fun tileState(id: TileId, state: QuickControlsSnapshot): SwitchState = when (id) {
        TileId.WIFI -> state.wifi
        TileId.MOBILE_DATA -> state.mobileData
        TileId.BLUETOOTH -> state.bluetooth
        TileId.AIRPLANE -> state.airplane
        TileId.HOTSPOT -> state.hotspot
        TileId.TORCH -> state.torch
        TileId.ROTATION_LOCK -> state.rotationLock
        TileId.FOCUS -> state.focus
        // Casting and the camera are destinations, not switches: they never claim
        // an on/off state they cannot know.
        TileId.CAST, TileId.CAMERA -> SwitchState(on = null, capability = Capability.SYSTEM_ROUTE)
    }

    private fun buildTiles(ids: List<TileId>) {
        if (ids == renderedTiles && tileGrid.childCount > 0) return
        renderedTiles = ids
        tiles.clear()
        tileGrid.removeAllViews()
        val columns = QuickControlsPolicy.tileColumnsFor(host.availableWidthDp()).coerceAtLeast(1)
        var row: LinearLayout? = null
        ids.forEachIndexed { index, id ->
            if (index % columns == 0) {
                val nextRow = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                tileGrid.addView(
                    nextRow,
                    LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        if (index > 0) topMargin = dp(LumaTokens.spaceXS)
                    }
                )
                row = nextRow
            }
            val tile = TileView(context, host, id)
            tiles[id] = tile
            row?.addView(
                tile,
                LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index % columns != 0) marginStart = dp(LumaTokens.spaceXS)
                }
            )
        }
        // A partial last row keeps the column rhythm instead of stretching tiles.
        val remainder = ids.size % columns
        if (remainder != 0) {
            repeat(columns - remainder) {
                row?.addView(
                    View(context),
                    LayoutParams(0, dp(LumaTokens.quickTileHeight), 1f).apply {
                        marginStart = dp(LumaTokens.spaceXS)
                    }
                )
            }
        }
    }

    private fun applyMaterial() {
        val theme = host.theme()
        val palette = host.glassPalette()
        tiles.values.forEach { it.applyMaterial(theme, palette) }
        brightnessRow.applyMaterial(theme, palette)
        volumeRow.applyMaterial(theme, palette)
        mediaCard.orientation = VERTICAL
        mediaCard.setPadding(
            dp(LumaTokens.cardPadding), dp(LumaTokens.spaceSM),
            dp(LumaTokens.cardPadding), dp(LumaTokens.spaceSM)
        )
        GlassViews.apply(mediaCard, palette[GlassRole.SECONDARY], density, ripple = false, depth = LumaTokens.depthShade)
    }

    private fun bindMedia(state: QuickControlsSnapshot) {
        val theme = host.theme()
        val palette = host.glassPalette()
        val colors = palette[GlassRole.SECONDARY].rest
        mediaCard.removeAllViews()

        val media = state.media
        if (media == null) {
            val accessGranted = host.mediaAccessGranted()
            val message = TextView(context).apply {
                setText(if (accessGranted) R.string.qc_media_empty else R.string.qc_media_needs_access)
                setTextColor(colors.muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeCaptionSp)
                typeface = Typefaces.body(theme.typography)
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(LumaTokens.minTouchTarget * 0.75f)
                setPadding(dp(LumaTokens.spaceXXS), dp(LumaTokens.spaceXXS), dp(LumaTokens.spaceXXS), dp(LumaTokens.spaceXXS))
                if (!accessGranted) {
                    isClickable = true
                    isFocusable = true
                    background = ThemedDrawables.ripple(colors.ripple, null, LumaTokens.radiusSmall * density)
                    contentDescription = context.getString(R.string.qc_media_needs_access)
                    setOnClickListener { host.onNeedsNotificationAccess() }
                }
            }
            mediaCard.addView(message, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val art = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            background = ThemedDrawables.surface(
                ColorMath.withAlpha(colors.content, 0.10f), LumaTokens.radiusSmall * density
            )
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }
        host.mediaArtwork()?.let { art.setImageDrawable(it) }
        row.addView(art, LayoutParams(dp(ARTWORK_DP), dp(ARTWORK_DP)))

        val textColumn = LinearLayout(context).apply { orientation = VERTICAL }
        textColumn.addView(TextView(context).apply {
            text = media.title ?: context.getString(R.string.qc_media_unknown_title)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setTextColor(colors.content)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeBodySp)
            typeface = Typefaces.title(theme.typography)
        })
        textColumn.addView(TextView(context).apply {
            text = media.artist.orEmpty()
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            visibility = if (media.artist.isNullOrBlank()) View.GONE else View.VISIBLE
            setTextColor(colors.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeCaptionSp)
            typeface = Typefaces.body(theme.typography)
        })
        row.addView(
            textColumn,
            LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(LumaTokens.spaceSM)
                marginEnd = dp(LumaTokens.spaceXS)
            }
        )

        row.addView(mediaButton(R.drawable.ic_music_previous, R.string.qc_media_previous, media.canSkipPrevious, colors) {
            host.onMediaCommand(MusicCommand.PREVIOUS)
        })
        row.addView(
            mediaButton(
                if (media.playing) R.drawable.ic_music_pause else R.drawable.ic_music_play,
                if (media.playing) R.string.qc_media_pause else R.string.qc_media_play,
                media.canPlayPause, colors
            ) {
                host.onMediaCommand(if (media.playing) MusicCommand.PAUSE else MusicCommand.PLAY)
            }
        )
        row.addView(mediaButton(R.drawable.ic_music_next, R.string.qc_media_next, media.canSkipNext, colors) {
            host.onMediaCommand(MusicCommand.NEXT)
        })

        mediaCard.addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        mediaCard.contentDescription = context.getString(
            R.string.qc_media_description, media.title.orEmpty(), media.artist.orEmpty()
        )
    }

    private fun mediaButton(
        iconRes: Int,
        descriptionRes: Int,
        enabled: Boolean,
        colors: GlassColors,
        onClick: () -> Unit
    ): ImageButton = ImageButton(context).apply {
        setImageDrawable(tinted(iconRes, colors.content))
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(LumaTokens.spaceSM), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceSM))
        contentDescription = context.getString(descriptionRes)
        background = ThemedDrawables.ripple(colors.ripple, null, LumaTokens.radiusFull * density)
        isEnabled = enabled
        isClickable = enabled
        isFocusable = enabled
        alpha = if (enabled) 1f else DISABLED_ALPHA
        setOnClickListener {
            LumaHaptics.tap(this)
            onClick()
        }
    }

    private fun tinted(res: Int, color: Int): Drawable? =
        ContextCompat.getDrawable(context, res)?.mutate()?.also { it.setTint(color) }

    /**
     * One glass tile: glyph, label, truthful state.
     *
     * A [FrameLayout] so the "on" accent fill can sit between the Luma Glass
     * background (which keeps its wallpaper adaptation) and the content, instead
     * of replacing the material with a flat colour.
     */
    inner class TileView(
        context: Context,
        private val host: QuickControlsHost,
        private val id: TileId
    ) : FrameLayout(context) {

        private val activeFill = View(context)
        private val content = LinearLayout(context)
        private val icon = ImageView(context)
        private val label = TextView(context)
        private val marker = ImageView(context)
        private var state: SwitchState = SwitchState.UNKNOWN
        private var radiusPx = 0f

        init {
            minimumHeight = dp(LumaTokens.quickTileHeight)
            isClickable = true
            isFocusable = true

            content.orientation = LinearLayout.VERTICAL
            content.gravity = Gravity.CENTER
            content.setPadding(
                dp(LumaTokens.spaceXXS), dp(LumaTokens.spaceXS),
                dp(LumaTokens.spaceXXS), dp(LumaTokens.spaceXS)
            )
            content.minimumHeight = dp(LumaTokens.quickTileHeight)
            content.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

            icon.layoutParams = LinearLayout.LayoutParams(dp(LumaTokens.iconAction), dp(LumaTokens.iconAction))
            icon.scaleType = ImageView.ScaleType.FIT_CENTER
            icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            content.addView(icon)

            label.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(LumaTokens.spaceXXS) }
            label.gravity = Gravity.CENTER
            label.maxLines = 1
            label.ellipsize = android.text.TextUtils.TruncateAt.END
            label.includeFontPadding = false
            content.addView(label)

            marker.layoutParams = LinearLayout.LayoutParams(dp(MARKER_DP), dp(MARKER_DP)).apply {
                topMargin = dp(2f)
            }
            marker.scaleType = ImageView.ScaleType.FIT_CENTER
            marker.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            marker.visibility = View.GONE
            content.addView(marker)

            activeFill.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            activeFill.alpha = 0f
            activeFill.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(activeFill)
            addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        fun applyMaterial(theme: ResolvedTheme, palette: GlassPalette) {
            val spec = palette[GlassRole.SETTINGS_CONTROL]
            radiusPx = theme.corner(LumaTokens.radiusLarge) * density
            GlassViews.apply(this, spec, density, ripple = true, radiusOverrideDp = theme.corner(LumaTokens.radiusLarge))
            label.text = context.getString(labelRes(id))
            label.typeface = Typefaces.label(theme.typography)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeMicroSp)
            bind(state)
        }

        fun bind(next: SwitchState) {
            state = next
            val theme = host.theme()
            val spec = host.glassPalette()[GlassRole.SETTINGS_CONTROL]
            val colors = spec.rest
            val active = next.on == true
            val unavailable = next.capability == Capability.UNAVAILABLE

            // The material's own selected state stays responsible for the subtle
            // tint; the accent fill is what makes "on" unmistakable.
            isSelected = active
            activeFill.background = ThemedDrawables.surface(ColorMath.opaque(theme.colors.accent), radiusPx)
            val targetAlpha = if (active) 1f else 0f
            if (activeFill.alpha != targetAlpha) {
                LumaSpring.animate(activeFill, LumaSpring.Property.ALPHA, targetAlpha, motion = host.motion())
            }

            val contentColor = if (active) theme.colors.onAccent else colors.content
            icon.setImageDrawable(tinted(iconRes(id), contentColor))
            icon.alpha = if (unavailable) DISABLED_ALPHA else 1f
            label.setTextColor(contentColor)
            label.alpha = when {
                unavailable -> DISABLED_ALPHA
                next.on == null -> LumaTokens.typeMicroAlpha + 0.2f
                else -> 1f
            }

            val markerRes = when (next.capability) {
                Capability.SYSTEM_ROUTE -> R.drawable.ic_rx_chevron_right
                Capability.NEEDS_GRANT -> R.drawable.ic_rx_lock
                Capability.UNAVAILABLE -> R.drawable.ic_rx_info
                Capability.DIRECT -> null
            }
            if (markerRes == null) {
                marker.visibility = View.GONE
            } else {
                marker.visibility = View.VISIBLE
                marker.setImageDrawable(tinted(markerRes, ColorMath.withAlpha(contentColor, 0.75f)))
            }

            contentDescription = describe(next)
            setOnClickListener {
                LumaHaptics.tap(this)
                host.onTileTap(id)
            }
            // Long press goes straight to the system page behind the tile, the way
            // Android's own Quick Settings does: a second, intentional route.
            setOnLongClickListener {
                LumaHaptics.longPress(this)
                host.onTileLongPress(id)
                true
            }
        }

        private fun describe(next: SwitchState): String {
            val name = context.getString(labelRes(id))
            val stateText = when {
                next.capability == Capability.UNAVAILABLE -> context.getString(R.string.qc_state_unavailable)
                next.on == null -> context.getString(R.string.qc_state_system_managed)
                next.on == true -> context.getString(R.string.qc_state_on)
                else -> context.getString(R.string.qc_state_off)
            }
            val route = when (next.capability) {
                Capability.SYSTEM_ROUTE -> context.getString(R.string.qc_state_opens_system)
                Capability.NEEDS_GRANT -> context.getString(R.string.qc_state_needs_grant)
                Capability.DIRECT, Capability.UNAVAILABLE -> null
            }
            return listOfNotNull(name, stateText, route).joinToString(", ")
        }
    }

    /** Icon + glass slider on one row, with a caption for a restricted state. */
    inner class SliderRow(
        context: Context,
        private val host: QuickControlsHost,
        private val labelRes: Int,
        private val iconRes: Int,
        private val bright: Boolean
    ) : LinearLayout(context) {

        private val icon = ImageView(context)
        val slider = GlassSlider(context)
        private val caption = TextView(context)

        init {
            orientation = VERTICAL
            icon.layoutParams = LinearLayout.LayoutParams(dp(LumaTokens.iconAction), dp(LumaTokens.iconAction))
            icon.scaleType = ImageView.ScaleType.FIT_CENTER
            icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(LumaTokens.minTouchTarget)
            }
            row.addView(icon)
            row.addView(
                slider,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(LumaTokens.spaceSM)
                }
            )
            addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            caption.visibility = View.GONE
            caption.maxLines = 2
            caption.includeFontPadding = false
            addView(caption, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2f)
            })
        }

        fun applyMaterial(theme: ResolvedTheme, palette: GlassPalette) {
            val spec = palette[GlassRole.SECONDARY]
            GlassViews.apply(this, spec, density, ripple = false, depth = LumaTokens.depthShade)
            setPadding(dp(LumaTokens.spaceSM), dp(LumaTokens.spaceXXS), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceXXS))
            icon.setImageDrawable(tinted(iconRes, spec.rest.content))
            slider.contentDescription = context.getString(labelRes)
            val material = GlassSlider.material(spec.rest.content, theme.colors.accent, theme.isDark)
            slider.setMaterial(material.track, material.fill, material.rim, material.thumb, material.thumbRim)
            caption.setTextColor(spec.rest.muted)
            caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeMicroSp)
            caption.typeface = Typefaces.label(theme.typography)
            caption.setPadding(dp(LumaTokens.spaceSM), 0, dp(LumaTokens.spaceSM), dp(LumaTokens.spaceXXS))
        }

        /**
         * @param onRestricted invoked when the user drags or taps a slider Android
         *        will not let LumaOS Pro write; the host opens the real grant screen.
         */
        fun bind(state: SliderState, onRestricted: (() -> Unit)?) {
            val writable = state.capability == Capability.DIRECT && state.max > state.min
            slider.interactive = writable
            if (writable) {
                slider.setOnClickListener(null)
                slider.onValueChanged = { fraction, _ -> emit(fraction, commit = false) }
                slider.onCommit = { fraction -> emit(fraction, commit = true) }
            } else {
                slider.onValueChanged = null
                slider.onCommit = null
                slider.setOnClickListener { onRestricted?.invoke() }
            }
            slider.setValueSilently(state.fraction)

            val captionText = when {
                state.capability == Capability.NEEDS_GRANT -> context.getString(
                    if (bright) R.string.qc_brightness_needs_grant else R.string.qc_slider_needs_grant
                )
                state.max <= state.min -> context.getString(R.string.qc_state_unavailable)
                state.automatic -> context.getString(R.string.qc_brightness_automatic)
                else -> null
            }
            caption.visibility = if (captionText == null) View.GONE else View.VISIBLE
            captionText?.let { caption.text = it }
        }

        private fun emit(fraction: Float, commit: Boolean) {
            if (bright) host.onBrightnessChanged(fraction, commit) else host.onVolumeChanged(fraction, commit)
        }
    }

    companion object {
        private const val ARTWORK_DP = 44f
        private const val MARKER_DP = 12f
        private const val DISABLED_ALPHA = 0.4f

        fun labelRes(id: TileId): Int = when (id) {
            TileId.WIFI -> R.string.qc_tile_wifi
            TileId.MOBILE_DATA -> R.string.qc_tile_mobile_data
            TileId.BLUETOOTH -> R.string.qc_tile_bluetooth
            TileId.AIRPLANE -> R.string.qc_tile_airplane
            TileId.HOTSPOT -> R.string.qc_tile_hotspot
            TileId.TORCH -> R.string.qc_tile_torch
            TileId.ROTATION_LOCK -> R.string.qc_tile_rotation
            TileId.FOCUS -> R.string.qc_tile_focus
            TileId.CAST -> R.string.qc_tile_cast
            TileId.CAMERA -> R.string.qc_tile_camera
        }

        fun iconRes(id: TileId): Int = when (id) {
            TileId.WIFI -> R.drawable.ic_rx_wifi
            TileId.MOBILE_DATA -> R.drawable.ic_rx_mobile_data
            TileId.BLUETOOTH -> R.drawable.ic_rx_bluetooth
            TileId.AIRPLANE -> R.drawable.ic_rx_airplane
            TileId.HOTSPOT -> R.drawable.ic_rx_hotspot
            TileId.TORCH -> R.drawable.ic_rx_torch
            TileId.ROTATION_LOCK -> R.drawable.ic_rx_rotation
            TileId.FOCUS -> R.drawable.ic_rx_focus
            TileId.CAST -> R.drawable.ic_rx_cast
            TileId.CAMERA -> R.drawable.ic_rx_camera
        }
    }
}
