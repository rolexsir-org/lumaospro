package com.lumaos.pro.launcher.controls

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.widgets.music.MusicBridge
import com.lumaos.pro.launcher.widgets.music.MusicCommand
import com.lumaos.pro.launcher.widgets.music.MusicLogic
import com.lumaos.pro.launcher.widgets.music.SessionCandidate
import com.lumaos.pro.launcher.widgets.music.SessionKind

/**
 * Media playback for Quick Controls, from Android's real media sessions.
 *
 * This is deliberately a second, short-lived reader rather than a rewrite of
 * [MusicBridge]: the bridge exists to serve hosted app widgets and stops itself
 * when the last widget disappears, while the shade needs sessions only while it
 * is open. Both use the *same* notification-listener component, so the single
 * Android grant the user already gave for the music widget also powers this, and
 * both reuse [MusicLogic] for session choice and advertised-action checks - no
 * second opinion about what a player supports.
 *
 * There is no invented playback: when Android reports no compatible session the
 * snapshot is null and the panel renders its empty state.
 */
class MediaSessionSource(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var manager: MediaSessionManager? = null

    @Volatile
    private var controllers: List<MediaController> = emptyList()
    private val callbacks = LinkedHashMap<android.media.session.MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var registered = false
    private var onChanged: (() -> Unit)? = null

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        attach(sessions.orEmpty())
        notifyChanged()
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = notifyChanged()
        override fun onMetadataChanged(metadata: MediaMetadata?) = notifyChanged()
        override fun onSessionDestroyed() = refresh()
        override fun onAudioInfoChanged() = notifyChanged()
    }

    fun hasAccess(): Boolean = MusicBridge.hasAccess(appContext)

    /** Binds to Android's sessions. Safe to call repeatedly; a no-op when bound. */
    fun start(callback: () -> Unit) {
        onChanged = callback
        refresh()
    }

    fun refresh() {
        if (!hasAccess()) {
            detach()
            notifyChanged()
            return
        }
        val current = manager ?: runCatching {
            appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        }.getOrNull()
        if (current == null) {
            LumaLog.w("Media session manager is unavailable")
            detach()
            notifyChanged()
            return
        }
        manager = current
        val component = ComponentNames.listener(appContext)
        val active = runCatching { current.getActiveSessions(component) }
            .getOrElse {
                // Access can be revoked while the panel is open; that is a state,
                // not a crash.
                LumaLog.w("Active media sessions could not be read")
                detach()
                notifyChanged()
                return
            }
        if (!registered) {
            runCatching { current.addOnActiveSessionsChangedListener(sessionsChanged, component, main) }
                .onSuccess { registered = true }
                .onFailure { LumaLog.w("Media session changes cannot be observed") }
        }
        attach(active)
        notifyChanged()
    }

    private fun attach(incoming: List<MediaController>) {
        val next = incoming.take(MAX_SESSIONS).distinctBy { it.sessionToken }
        val wanted = next.mapTo(HashSet()) { it.sessionToken }
        callbacks.keys.filter { it !in wanted }.toList().forEach { token ->
            callbacks.remove(token)?.let { (controller, callback) ->
                runCatching { controller.unregisterCallback(callback) }
            }
        }
        next.forEach { controller ->
            val token = controller.sessionToken
            if (token !in callbacks) {
                runCatching { controller.registerCallback(controllerCallback, main) }
                    .onSuccess { callbacks[token] = controller to controllerCallback }
                    .onFailure { LumaLog.w("Media session callback could not be registered") }
            }
        }
        controllers = next
    }

    private fun detach() {
        if (registered) {
            runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsChanged) }
            registered = false
        }
        callbacks.values.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        callbacks.clear()
        controllers = emptyList()
    }

    /** Releases every platform handle. Called when the shade closes. */
    fun stop() {
        detach()
        manager = null
        onChanged = null
        main.removeCallbacksAndMessages(null)
    }

    private fun notifyChanged() {
        val callback = onChanged ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) callback() else main.post(callback)
    }

    private fun selected(): MediaController? {
        val candidates = controllers.map { controller ->
            val state = runCatching { controller.playbackState }.getOrNull()
            SessionCandidate(kindOf(state), runCatching { controller.metadata != null }.getOrDefault(false))
        }
        return MusicLogic.chooseSession(candidates)?.let(controllers::get)
    }

    private fun kindOf(state: PlaybackState?): SessionKind = when (state?.state) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> SessionKind.PLAYING
        PlaybackState.STATE_PAUSED -> SessionKind.PAUSED
        PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE, PlaybackState.STATE_ERROR -> SessionKind.STOPPED
        else -> SessionKind.UNKNOWN
    }

    /** The real session state, or null when Android reports nothing playable. */
    fun snapshot(): MediaSnapshot? {
        val controller = selected() ?: return null
        val metadata = runCatching { controller.metadata }.getOrNull()
        val state = runCatching { controller.playbackState }.getOrNull()
        val actions = state?.actions ?: 0L
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val playing = kindOf(state) == SessionKind.PLAYING
        val artwork = artworkBitmap(metadata)
        // A session with neither metadata nor a usable state is not worth a card.
        if (title.isNullOrBlank() && artist.isNullOrBlank() && artwork == null && actions == 0L) return null
        return MediaSnapshot(
            title = title?.takeIf { it.isNotBlank() },
            artist = artist?.takeIf { it.isNotBlank() },
            playing = playing,
            canPlayPause = MusicLogic.supports(MusicCommand.PLAY, actions) ||
                MusicLogic.supports(MusicCommand.PAUSE, actions),
            canSkipNext = MusicLogic.supports(MusicCommand.NEXT, actions),
            canSkipPrevious = MusicLogic.supports(MusicCommand.PREVIOUS, actions),
            hasArtwork = artwork != null
        )
    }

    /** Album art as Android supplies it, downscaled for a 56dp tile. */
    fun artwork(maxEdgePx: Int = ARTWORK_EDGE_PX): Bitmap? = artworkBitmap(
        runCatching { selected()?.metadata }.getOrNull(), maxEdgePx
    )

    private fun artworkBitmap(metadata: MediaMetadata?, maxEdgePx: Int = ARTWORK_EDGE_PX): Bitmap? {
        if (metadata == null) return null
        val source = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON
        ).firstNotNullOfOrNull { key -> runCatching { metadata.getBitmap(key) }.getOrNull() } ?: return null
        if (source.isRecycled) return null
        val longest = maxOf(source.width, source.height)
        if (longest <= maxEdgePx || longest <= 0) return source
        val scale = maxEdgePx.toFloat() / longest
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return runCatching { Bitmap.createScaledBitmap(source, w, h, true) }.getOrDefault(source)
    }

    /**
     * Sends a transport command to the session Android reports.
     *
     * Returns false when the player does not advertise the action or the call was
     * refused, so the panel never animates a button as though it had worked.
     */
    fun command(command: MusicCommand): Boolean {
        val controller = selected() ?: return false
        val actions = runCatching { controller.playbackState?.actions ?: 0L }.getOrDefault(0L)
        val operation = MusicLogic.transportAction(command, actions) ?: return false
        val controls = controller.transportControls
        return runCatching {
            when (operation) {
                com.lumaos.pro.launcher.widgets.music.TransportAction.PREVIOUS -> controls.skipToPrevious()
                com.lumaos.pro.launcher.widgets.music.TransportAction.NEXT -> controls.skipToNext()
                com.lumaos.pro.launcher.widgets.music.TransportAction.PLAY -> controls.play()
                com.lumaos.pro.launcher.widgets.music.TransportAction.PAUSE -> controls.pause()
                com.lumaos.pro.launcher.widgets.music.TransportAction.TOGGLE -> controls.play()
            }
            true
        }.getOrElse {
            LumaLog.w("Media transport command was refused")
            false
        }
    }

    companion object {
        private const val MAX_SESSIONS = 8
        private const val ARTWORK_EDGE_PX = 160
    }
}

/** The listener component both media access and the notification shade are granted through. */
internal object ComponentNames {
    fun listener(context: Context): android.content.ComponentName =
        android.content.ComponentName(
            context.applicationContext,
            com.lumaos.pro.launcher.widgets.music.MusicNotificationListener::class.java
        )
}
