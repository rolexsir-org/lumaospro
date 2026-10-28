package com.lumaos.pro.launcher.widgets.music

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import com.lumaos.pro.launcher.wallpaper.WallpaperLimits
import com.lumaos.pro.launcher.widgets.WidgetGlassPalette
import com.lumaos.pro.launcher.widgets.WidgetGlassRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Event-driven MediaSession bridge. It holds application context only, subscribes
 * while at least one widget exists, and releases listeners/callbacks when the
 * last widget disappears or access is revoked. No playback or notification data
 * is persisted by Luma. RemoteViews may be cached by the Android widget host.
 */
object MusicBridge {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var application: Context? = null
    private var manager: MediaSessionManager? = null
    private var registered = false
    private var themeJob: Job? = null
    private var artworkJob: Job? = null
    private var artKey: String? = null
    private var artLoaded = false
    private var artBitmap: Bitmap? = null
    private var directSource: Bitmap? = null
    private var directScaled: Bitmap? = null
    private val progressEndTask = Runnable { render() }
    private val callbacks = linkedMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var controllers: List<MediaController> = emptyList()
    private val renderTask = Runnable { render() }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        attach(sessions.orEmpty())
        scheduleRender()
    }

    fun hasAccess(context: Context): Boolean {
        val component = ComponentName(context, MusicNotificationListener::class.java)
        return if (Build.VERSION.SDK_INT >= 27) {
            runCatching {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .isNotificationListenerAccessGranted(component)
            }.getOrDefault(false)
        } else {
            val enabled = runCatching { Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") }.getOrNull()
            enabled.orEmpty().split(':').mapNotNull(ComponentName::unflattenFromString).contains(component)
        }
    }

    fun refreshIfActive(context: Context) {
        val app = context.applicationContext
        if (widgetIds(app).isEmpty()) return
        refresh(app)
    }

    fun refresh(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { refresh(context.applicationContext) }; return }
        val app = context.applicationContext
        application = app
        if (widgetIds(app).isEmpty()) { stop(); return }
        startTheme(app)
        if (!hasAccess(app)) {
            clearSessions()
            render()
            return
        }
        try {
            val currentManager = manager ?: (app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager).also { manager = it }
            val component = ComponentName(app, MusicNotificationListener::class.java)
            if (!registered) {
                currentManager.addOnActiveSessionsChangedListener(sessionsChanged, component, main)
                registered = true
            }
            attach(currentManager.getActiveSessions(component))
            render()
        } catch (_: SecurityException) {
            LumaLog.w("Media session access was denied")
            clearSessions()
            render()
        } catch (_: RuntimeException) {
            LumaLog.w("Media session listener is unavailable")
            clearSessions()
            render()
        }
    }

    fun listenerDisconnected(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { listenerDisconnected(context.applicationContext) }; return }
        application = context.applicationContext
        clearSessions()
        render() // Show permission or temporary-unavailable state; await system reconnect.
    }

    fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { stop() }; return }
        clearSessions()
        themeJob?.cancel(); themeJob = null
        main.removeCallbacks(renderTask)
        main.removeCallbacks(progressEndTask)
        application = null
    }

    fun command(context: Context, command: MusicCommand) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { command(context.applicationContext, command) }; return }
        if (!hasAccess(context)) { refresh(context); return }
        // Re-query, so an old PendingIntent cannot control a player that is no longer selected.
        refresh(context)
        val controller = selectedController() ?: return
        val actions = runCatching { controller.playbackState?.actions ?: 0L }.getOrDefault(0L)
        val operation = MusicLogic.transportAction(command, actions) ?: return
        runCatching {
            when (operation) {
                TransportAction.PREVIOUS -> controller.transportControls.skipToPrevious()
                TransportAction.NEXT -> controller.transportControls.skipToNext()
                TransportAction.PLAY -> controller.transportControls.play()
                TransportAction.PAUSE -> controller.transportControls.pause()
                TransportAction.TOGGLE -> sendToggle(controller)
            }
        }.onFailure { LumaLog.w("Media transport command failed") }
    }

    private fun sendToggle(controller: MediaController) {
        val down = controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        val up = controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        if (!down || !up) LumaLog.w("Media toggle was not accepted")
    }

    private fun startTheme(context: Context) {
        if (themeJob != null) return
        val themes = (context as LumaApp).core.themes
        themeJob = scope.launch { themes.theme.collect { scheduleRender() } }
    }

    private fun clearSessions() {
        if (registered) runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        registered = false
        callbacks.values.forEach { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        callbacks.clear(); controllers = emptyList(); manager = null
        clearArtwork()
    }

    private fun attach(incoming: List<MediaController>) {
        // System priority order is retained; a bounded set prevents an unbounded callback fan-out.
        val next = incoming.take(16).distinctBy { it.sessionToken }
        val wanted = next.mapTo(hashSetOf()) { it.sessionToken }
        callbacks.keys.filter { it !in wanted }.toList().forEach { token ->
            callbacks.remove(token)?.let { (c, callback) -> runCatching { c.unregisterCallback(callback) } }
        }
        next.forEach { controller ->
            val token = controller.sessionToken
            if (token !in callbacks) {
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) { scheduleRender() }
                    override fun onMetadataChanged(metadata: MediaMetadata?) { clearArtwork(); scheduleRender() }
                    override fun onSessionDestroyed() { application?.let { refresh(it) } }
                }
                runCatching { controller.registerCallback(callback, main) }
                    .onSuccess { callbacks[token] = controller to callback }
                    .onFailure { LumaLog.w("Media session callback registration failed") }
            }
        }
        controllers = next
    }

    private fun scheduleRender() {
        main.removeCallbacks(renderTask)
        main.postDelayed(renderTask, 200) // Coalesce bursts; not a periodic timer.
    }

    private fun selectedController(): MediaController? {
        val snapshot = controllers.map { controller ->
            val state = runCatching { controller.playbackState }.getOrNull()
            SessionCandidate(kind(state), runCatching { controller.metadata != null }.getOrDefault(false))
        }
        return MusicLogic.chooseSession(snapshot)?.let(controllers::get)
    }

    private fun kind(state: PlaybackState?): SessionKind = when (state?.state) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> SessionKind.PLAYING
        PlaybackState.STATE_PAUSED -> SessionKind.PAUSED
        PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE, PlaybackState.STATE_ERROR -> SessionKind.STOPPED
        else -> SessionKind.UNKNOWN
    }

    private fun render() {
        val app = application ?: return
        main.removeCallbacks(progressEndTask)
        val ids = widgetIds(app)
        if (ids.isEmpty()) { stop(); return }
        val theme = (app as LumaApp).core.themes.theme.value
        val access = hasAccess(app)
        val controller = if (access && registered) selectedController() else null
        val message = when {
            !access -> R.string.music_empty_permission
            !registered -> R.string.music_empty_unavailable
            controller == null -> R.string.music_empty_session
            else -> null
        }
        val metadata = controller?.let { runCatching { it.metadata }.getOrNull() }
        val state = controller?.let { runCatching { it.playbackState }.getOrNull() }
        val artwork = metadata?.let { artwork(app, it) }
        val widgetManager = AppWidgetManager.getInstance(app)
        ids.forEach { id ->
            val views = RemoteViews(app.packageName, R.layout.widget_luma_music)
            applyTheme(app, views, theme)
            if (message != null || controller == null) {
                views.setViewVisibility(R.id.music_empty, View.VISIBLE)
                views.setViewVisibility(R.id.music_active, View.GONE)
                views.setTextViewText(R.id.music_empty, app.getString(message ?: R.string.music_empty_session))
                views.setOnClickPendingIntent(R.id.music_empty, accessIntent(app, id))
            } else {
                views.setViewVisibility(R.id.music_empty, View.GONE)
                views.setViewVisibility(R.id.music_active, View.VISIBLE)
                populate(app, views, id, controller, metadata, state, artwork, theme)
            }
            runCatching { widgetManager.updateAppWidget(id, views) }
                .onFailure { LumaLog.w("Music widget update failed") }
        }
    }

    private fun populate(app: Context, views: RemoteViews, id: Int, controller: MediaController,
                         metadata: MediaMetadata?, state: PlaybackState?, artwork: Bitmap?, theme: ResolvedTheme) {
        fun text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
            runCatching { metadata?.getText(key)?.toString()?.trim()?.take(200)?.takeIf(String::isNotEmpty) }.getOrNull()
        }
        val title = text(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: app.getString(R.string.music_unknown_title)
        val artist = text(MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: app.getString(R.string.music_unknown_artist)
        val player = runCatching {
            val pm = app.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(controller.packageName, 0)).toString().take(100)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: app.getString(R.string.music_player)
        views.setTextViewText(R.id.music_player_label, player)
        views.setTextViewText(R.id.music_title, title)
        views.setTextViewText(R.id.music_artist, artist)
        if (artwork != null) {
            views.setInt(R.id.music_art, "setColorFilter", 0)
            views.setImageViewBitmap(R.id.music_art, artwork)
        } else {
            views.setImageViewResource(R.id.music_art, R.drawable.ic_music_note)
            views.setContentDescription(R.id.music_art, app.getString(R.string.music_artwork_unavailable))
            views.setInt(R.id.music_art, "setColorFilter", WidgetGlassPalette.fromTheme(theme).accent)
        }
        runCatching { controller.sessionActivity }.getOrNull()?.let {
            views.setOnClickPendingIntent(R.id.music_art, it)
            views.setContentDescription(R.id.music_art, app.getString(R.string.music_open_player))
        }

        val playing = kind(state) == SessionKind.PLAYING
        val toggle = if (playing) MusicCommand.PAUSE else MusicCommand.PLAY
        val actions = state?.actions ?: 0L
        views.setImageViewResource(R.id.music_toggle, if (playing) R.drawable.ic_music_pause else R.drawable.ic_music_play)
        views.setContentDescription(R.id.music_toggle, app.getString(if (playing) R.string.music_pause else R.string.music_play))
        listOf(R.id.music_previous to MusicCommand.PREVIOUS, R.id.music_toggle to toggle, R.id.music_next to MusicCommand.NEXT)
            .forEach { (view, command) ->
                val supported = MusicLogic.supports(command, actions)
                views.setViewVisibility(view, if (supported) View.VISIBLE else View.INVISIBLE)
                if (supported) views.setOnClickPendingIntent(view, controlIntent(app, id, command))
            }
        val duration = runCatching { metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L }.getOrDefault(0L)
        val now = SystemClock.elapsedRealtime()
        val isRunning = state?.state == PlaybackState.STATE_PLAYING && state.playbackSpeed == 1f &&
            state.lastPositionUpdateTime in 1..now
        val canShow = state != null && (isRunning || state.state == PlaybackState.STATE_PAUSED)
        val position = if (canShow) MusicLogic.position(state!!.position, duration, state.lastPositionUpdateTime, now, isRunning) else null
        if (position != null) {
            views.setViewVisibility(R.id.music_progress_row, View.VISIBLE)
            val durationText = MusicLogic.formatTime(duration)
            views.setTextViewText(R.id.music_duration, durationText)
            views.setContentDescription(R.id.music_duration, app.getString(R.string.music_duration_description, durationText))
            if (isRunning && position < duration) {
                // One event at the predicted end prevents the host clock from running
                // beyond duration if a player is late to publish its next state.
                val remaining = duration - position
                if (remaining <= 24L * 60 * 60 * 1000) {
                    main.removeCallbacks(progressEndTask)
                    main.postDelayed(progressEndTask, remaining + 100)
                }
                views.setViewVisibility(R.id.music_elapsed_clock, View.VISIBLE)
                views.setViewVisibility(R.id.music_elapsed_static, View.GONE)
                // The host's Chronometer advances itself; no app-side polling or service timer.
                views.setChronometer(R.id.music_elapsed_clock, SystemClock.elapsedRealtime() - position, null, true)
            } else {
                views.setViewVisibility(R.id.music_elapsed_clock, View.GONE)
                views.setViewVisibility(R.id.music_elapsed_static, View.VISIBLE)
                val elapsedText = MusicLogic.formatTime(position)
                views.setTextViewText(R.id.music_elapsed_static, elapsedText)
                views.setContentDescription(R.id.music_elapsed_static, app.getString(R.string.music_elapsed_description, elapsedText))
            }
        } else views.setViewVisibility(R.id.music_progress_row, View.GONE)
    }

    private fun applyTheme(app: Context, views: RemoteViews, theme: ResolvedTheme) {
        val palette = WidgetGlassPalette.fromTheme(theme)
        runCatching { WidgetGlassRenderer.plateBitmap(app, theme) }
            .onSuccess { views.setImageViewBitmap(R.id.music_glass_plate, it) }
            .onFailure {
                views.setImageViewResource(
                    R.id.music_glass_plate,
                    if (theme.isDark) R.drawable.bg_music_glass_dark else R.drawable.bg_music_glass_light
                )
            }
        listOf(R.id.music_title, R.id.music_empty).forEach { views.setTextColor(it, palette.primaryText) }
        listOf(
            R.id.music_artist,
            R.id.music_player_label,
            R.id.music_elapsed_clock,
            R.id.music_elapsed_static,
            R.id.music_duration
        ).forEach { views.setTextColor(it, palette.mutedText) }
        listOf(R.id.music_previous, R.id.music_toggle, R.id.music_next).forEach {
            views.setInt(it, "setColorFilter", palette.accent)
        }
    }

    private fun artwork(app: Context, metadata: MediaMetadata): Bitmap? {
        val direct = listOf(MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON).firstNotNullOfOrNull { key ->
            runCatching { metadata.getBitmap(key) }.getOrNull()
        }
        if (direct != null && !direct.isRecycled) {
            if (directSource !== direct || directScaled?.isRecycled != false) {
                directSource = direct
                directScaled = try { scaleArtwork(direct) } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
            }
            return directScaled
        }
        val uriString = listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI).firstNotNullOfOrNull { key ->
            runCatching { metadata.getString(key)?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2048 } }.getOrNull()
        } ?: return null
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()?.takeIf { it.scheme == "content" } ?: return null
        if (artKey != uriString) { clearArtwork(); artKey = uriString }
        if (!artLoaded && artworkJob == null) {
            artworkJob = scope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        // Read once: a hostile provider cannot present safe bounds in
                        // one stream and a decompression bomb in a second stream.
                        val bytes = app.contentResolver.openInputStream(uri)?.use { readArtwork(it) }
                        if (bytes == null) null else {
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            if (!WallpaperLimits.validSource(bounds.outWidth, bounds.outHeight)) null else {
                                val options = BitmapFactory.Options().apply {
                                    inSampleSize = WallpaperLimits.sampleSize(bounds.outWidth, bounds.outHeight, 192, 192L * 192)
                                }
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let(::scaleArtwork)
                            }
                        }
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
                }
                if (artKey == uriString) { artBitmap = result; artLoaded = true; artworkJob = null; scheduleRender() }
            }
        }
        return artBitmap
    }

    private fun clearArtwork() {
        artworkJob?.cancel(); artworkJob = null; artKey = null; artLoaded = false; artBitmap = null
        directSource = null; directScaled = null
    }

    private fun scaleArtwork(bitmap: Bitmap): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        val scaled = if (max <= 192) bitmap else {
            val width = (bitmap.width * 192f / max).roundToInt().coerceAtLeast(1)
            val height = (bitmap.height * 192f / max).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        // A software bitmap is safely parcelable to hosts on all supported APIs.
        return if (scaled.config == Bitmap.Config.HARDWARE) {
            scaled.copy(Bitmap.Config.ARGB_8888, false) ?: throw java.io.IOException("Artwork copy unavailable")
        } else scaled
    }

    /** Bound untrusted content and honor cancellation between reads. */
    private suspend fun readArtwork(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream(32 * 1024)
        val buffer = ByteArray(32 * 1024)
        var total = 0
        var emptyReads = 0
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                if (++emptyReads > 4) return null
                continue
            }
            emptyReads = 0
            total += count
            if (total > 4 * 1024 * 1024) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray().takeIf { it.isNotEmpty() }
    }

    private fun widgetIds(context: Context): IntArray = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MusicWidgetProvider::class.java))
    }.onFailure { LumaLog.w("Music widget lookup failed") }.getOrDefault(intArrayOf())

    private fun accessIntent(context: Context, id: Int): PendingIntent = PendingIntent.getActivity(context, id,
        Intent(context, MusicAccessActivity::class.java).setData(Uri.parse("luma-music://access/$id")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun controlIntent(context: Context, id: Int, command: MusicCommand): PendingIntent = PendingIntent.getBroadcast(context, id,
        Intent(context, MusicWidgetControlReceiver::class.java).setAction(command.name)
            .setData(Uri.parse("luma-music://control/$id/${command.name}"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
