package com.rolexm.launcher.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComponentKeyTest {

    private val key = ComponentKey("com.example.app", "com.example.app.MainActivity", 0L)

    @Test
    fun `round trips through its serialized form`() {
        assertEquals(key, ComponentKey.parse(key.serialize()))
    }

    @Test
    fun `preserves a non-zero user serial`() {
        val multiUser = key.copy(userSerial = 10L)
        assertEquals(10L, ComponentKey.parse(multiUser.serialize())?.userSerial)
    }

    @Test
    fun `returns null rather than throwing on malformed input`() {
        // A corrupt layout entry must degrade to "item missing", never crash
        // the home screen.
        assertNull(ComponentKey.parse(""))
        assertNull(ComponentKey.parse("noColonHere"))
        assertNull(ComponentKey.parse(":pkg/cls"))
        assertNull(ComponentKey.parse("notANumber:pkg/cls"))
        assertNull(ComponentKey.parse("0:pkgonly"))
        assertNull(ComponentKey.parse("0:pkg/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a blank package name`() {
        ComponentKey("  ", "cls")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a blank class name`() {
        ComponentKey("pkg", " ")
    }
}
