package com.lumaos.pro.launcher.widgets.music

import org.junit.Assert.*
import org.junit.Test

class MusicLogicTest {
    @Test fun `playing player wins over paused and system order breaks ties`() {
        val sessions = listOf(
            SessionCandidate(SessionKind.PAUSED, true),
            SessionCandidate(SessionKind.PLAYING, true),
            SessionCandidate(SessionKind.PLAYING, true)
        )
        assertEquals(1, MusicLogic.chooseSession(sessions))
        assertEquals(0, MusicLogic.chooseSession(listOf(sessions[0], sessions[1].copy(kind = SessionKind.PAUSED))))
    }

    @Test fun `switches player when its playback state changes`() {
        val first = SessionCandidate(SessionKind.PLAYING, true)
        val second = SessionCandidate(SessionKind.PAUSED, true)
        assertEquals(0, MusicLogic.chooseSession(listOf(first, second)))
        assertEquals(1, MusicLogic.chooseSession(listOf(first.copy(kind = SessionKind.PAUSED), second.copy(kind = SessionKind.PLAYING))))
    }

    @Test fun `stopped and data-less sessions never create fake playback`() {
        assertNull(MusicLogic.chooseSession(emptyList()))
        assertNull(MusicLogic.chooseSession(listOf(SessionCandidate(SessionKind.STOPPED, true), SessionCandidate(SessionKind.UNKNOWN, false))))
        assertEquals(0, MusicLogic.chooseSession(listOf(SessionCandidate(SessionKind.UNKNOWN, true))))
    }

    @Test fun `transport controls obey each advertised flag`() {
        assertFalse(MusicLogic.supports(MusicCommand.PLAY, 0))
        assertFalse(MusicLogic.supports(MusicCommand.PAUSE, MusicLogic.ACTION_PLAY))
        assertTrue(MusicLogic.supports(MusicCommand.PLAY, MusicLogic.ACTION_PLAY))
        assertTrue(MusicLogic.supports(MusicCommand.PAUSE, MusicLogic.ACTION_PAUSE))
        assertTrue(MusicLogic.supports(MusicCommand.PLAY, MusicLogic.ACTION_PLAY_PAUSE))
        assertTrue(MusicLogic.supports(MusicCommand.PAUSE, MusicLogic.ACTION_PLAY_PAUSE))
        assertTrue(MusicLogic.supports(MusicCommand.PREVIOUS, MusicLogic.ACTION_PREVIOUS))
        assertFalse(MusicLogic.supports(MusicCommand.NEXT, MusicLogic.ACTION_PREVIOUS))
        assertTrue(MusicLogic.supports(MusicCommand.NEXT, MusicLogic.ACTION_NEXT))
    }

    @Test fun `dispatch plan refuses unsupported controls and uses toggle only when necessary`() {
        assertNull(MusicLogic.transportAction(MusicCommand.PREVIOUS, MusicLogic.ACTION_PLAY))
        assertEquals(TransportAction.NEXT, MusicLogic.transportAction(MusicCommand.NEXT, MusicLogic.ACTION_NEXT))
        assertEquals(TransportAction.PLAY, MusicLogic.transportAction(MusicCommand.PLAY, MusicLogic.ACTION_PLAY or MusicLogic.ACTION_PLAY_PAUSE))
        assertEquals(TransportAction.PAUSE, MusicLogic.transportAction(MusicCommand.PAUSE, MusicLogic.ACTION_PAUSE or MusicLogic.ACTION_PLAY_PAUSE))
        assertEquals(TransportAction.TOGGLE, MusicLogic.transportAction(MusicCommand.PLAY, MusicLogic.ACTION_PLAY_PAUSE))
        assertEquals(TransportAction.TOGGLE, MusicLogic.transportAction(MusicCommand.PAUSE, MusicLogic.ACTION_PLAY_PAUSE))
    }

    @Test fun `widget text colors have contrast over both translucent gradient endpoints`() {
        val math = com.lumaos.pro.launcher.ui.theme.ColorMath
        val layers = listOf(
            Triple(0xf0293547.toInt(), 0xfff8faff.toInt(), 0xffc1cfdb.toInt()),
            Triple(0xf0141c2a.toInt(), 0xfff8faff.toInt(), 0xffc1cfdb.toInt()),
            Triple(0xf8ffffff.toInt(), 0xff142332.toInt(), 0xff40536a.toInt()),
            Triple(0xf5e4ebf2.toInt(), 0xff142332.toInt(), 0xff40536a.toInt())
        )
        for ((fill, primary, muted) in layers) for (under in listOf(math.WHITE, math.BLACK)) {
            val actual = math.composite(fill, under)
            assertTrue(math.contrast(primary, actual) >= 4.5)
            assertTrue(math.contrast(muted, actual) >= 4.5)
        }
    }

    @Test fun `progress uses real elapsed time and clamps to duration`() {
        assertEquals(4000L, MusicLogic.position(3000, 10000, 1000, 2000, true))
        assertEquals(3000L, MusicLogic.position(3000, 10000, 1000, 2000, false))
        assertEquals(10000L, MusicLogic.position(9000, 10000, 1000, 5000, true))
        assertEquals(3000L, MusicLogic.position(3000, 10000, 3000, 2000, true))
        assertEquals(Long.MAX_VALUE, MusicLogic.position(Long.MAX_VALUE - 1, Long.MAX_VALUE, 1, Long.MAX_VALUE, true))
        assertNull(MusicLogic.position(-1, 10000, 1, 2, true))
        assertNull(MusicLogic.position(100, 0, 1, 2, true))
    }

    @Test fun `time formatting supports long tracks without wrapping`() {
        assertEquals("0:00", MusicLogic.formatTime(-1))
        assertEquals("1:05", MusicLogic.formatTime(65_000))
        assertEquals("1:01:01", MusicLogic.formatTime(3_661_000))
    }
}
