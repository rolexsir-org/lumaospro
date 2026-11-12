package com.lumaos.pro.launcher.context

import com.lumaos.pro.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentKeySetCodecTest {

    private val mail = ComponentKey("com.example.mail", "com.example.mail.Main")
    private val game = ComponentKey("com.example.game", "com.example.game.Play", 0L)
    private val workDocs = ComponentKey("com.example.docs", "com.example.docs.Main", 10L)

    @Test fun `round trip preserves keys including work profile serials`() {
        val keys = setOf(mail, game, workDocs)
        assertEquals(keys, ComponentKeySetCodec.decode(ComponentKeySetCodec.encode(keys)))
    }

    @Test fun `null blank and empty decode to empty`() {
        assertTrue(ComponentKeySetCodec.decode(null).isEmpty())
        assertTrue(ComponentKeySetCodec.decode("").isEmpty())
        assertTrue(ComponentKeySetCodec.decode("   ").isEmpty())
        assertEquals("", ComponentKeySetCodec.encode(emptySet()))
    }

    @Test fun `one corrupt entry does not lose the whole set`() {
        val raw = ComponentKeySetCodec.encode(setOf(mail, game)) + "|not-a-key|0:broken"
        assertEquals(setOf(mail, game), ComponentKeySetCodec.decode(raw))
    }

    @Test fun `duplicates are stored once`() {
        assertEquals(setOf(mail), ComponentKeySetCodec.decode(ComponentKeySetCodec.encode(listOf(mail, mail))))
    }
}
