package com.lumaos.pro.launcher.widgets.music

/** Android-free policy; Android's session order breaks ties within a rank. */
enum class SessionKind { PLAYING, PAUSED, UNKNOWN, STOPPED }
enum class MusicCommand { PREVIOUS, PLAY, PAUSE, NEXT }
enum class TransportAction { PREVIOUS, NEXT, PLAY, PAUSE, TOGGLE }

data class SessionCandidate(val kind: SessionKind, val hasMetadata: Boolean)

object MusicLogic {
    fun chooseSession(candidates: List<SessionCandidate>): Int? {
        fun rank(candidate: SessionCandidate) = when (candidate.kind) {
            SessionKind.PLAYING -> 3
            SessionKind.PAUSED -> 2
            SessionKind.UNKNOWN -> if (candidate.hasMetadata) 1 else 0
            SessionKind.STOPPED -> 0
        }
        val best = candidates.indices.maxByOrNull { rank(candidates[it]) } ?: return null
        return best.takeIf { rank(candidates[it]) > 0 }
    }

    // PlaybackState action constants are stable public Android API values.
    const val ACTION_PAUSE = 1L shl 1
    const val ACTION_PLAY = 1L shl 2
    const val ACTION_PREVIOUS = 1L shl 4
    const val ACTION_NEXT = 1L shl 5
    const val ACTION_PLAY_PAUSE = 1L shl 9

    fun supports(command: MusicCommand, actions: Long): Boolean = when (command) {
        MusicCommand.PREVIOUS -> actions and ACTION_PREVIOUS != 0L
        MusicCommand.NEXT -> actions and ACTION_NEXT != 0L
        MusicCommand.PLAY -> actions and (ACTION_PLAY or ACTION_PLAY_PAUSE) != 0L
        MusicCommand.PAUSE -> actions and (ACTION_PAUSE or ACTION_PLAY_PAUSE) != 0L
    }

    /** Select the operation actually sent to the session, or refuse an unsupported action. */
    fun transportAction(command: MusicCommand, actions: Long): TransportAction? {
        if (!supports(command, actions)) return null
        return when (command) {
            MusicCommand.PREVIOUS -> TransportAction.PREVIOUS
            MusicCommand.NEXT -> TransportAction.NEXT
            MusicCommand.PLAY -> if (actions and ACTION_PLAY != 0L) TransportAction.PLAY else TransportAction.TOGGLE
            MusicCommand.PAUSE -> if (actions and ACTION_PAUSE != 0L) TransportAction.PAUSE else TransportAction.TOGGLE
        }
    }

    /** Position at rendering time; never infer progress with an unknown clock or speed. */
    fun position(position: Long, duration: Long, updateTime: Long, now: Long, playingAtNormalSpeed: Boolean): Long? {
        if (position < 0 || duration <= 0) return null
        val delta = if (playingAtNormalSpeed && updateTime > 0 && now >= updateTime) now - updateTime else 0
        val initial = position.coerceAtMost(duration)
        return initial + delta.coerceAtMost(duration - initial)
    }

    fun formatTime(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, h, m, s)
        else "%d:%02d".format(java.util.Locale.ROOT, m, s)
    }
}
