package com.lumaos.pro.launcher.widgets.music

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Source contract checks; these do not replace a merged-manifest or device test. */
class PlatformContractTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private fun parse(path: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File(path))
    private fun element(root: org.w3c.dom.Document, tag: String, name: String): Element =
        (0 until root.getElementsByTagName(tag).length).map { root.getElementsByTagName(tag).item(it) as Element }
            .first { it.getAttributeNS(android, "name") == name }

    @Test fun `listener binding is protected and playback receiver is private`() {
        val manifest = parse("src/main/AndroidManifest.xml")
        val listener = element(manifest, "service", ".widgets.music.MusicNotificationListener")
        assertEquals("true", listener.getAttributeNS(android, "exported"))
        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", listener.getAttributeNS(android, "permission"))
        val receiver = element(manifest, "receiver", ".widgets.music.MusicWidgetControlReceiver")
        assertEquals("false", receiver.getAttributeNS(android, "exported"))
        val provider = element(manifest, "receiver", ".widgets.music.MusicWidgetProvider")
        assertEquals("true", provider.getAttributeNS(android, "exported"))
    }

    @Test fun `widget is home only and requests no periodic wakeups`() {
        val provider = parse("src/main/res/xml/luma_music_widget.xml").documentElement
        assertEquals("home_screen", provider.getAttributeNS(android, "widgetCategory"))
        assertEquals("0", provider.getAttributeNS(android, "updatePeriodMillis"))
        assertEquals("horizontal|vertical", provider.getAttributeNS(android, "resizeMode"))
    }

    @Test fun `battery widget has a bounded platform refresh and no global power broadcasts`() {
        val provider = parse("src/main/res/xml/luma_battery_widget.xml").documentElement
        assertEquals("home_screen", provider.getAttributeNS(android, "widgetCategory"))
        assertEquals("1800000", provider.getAttributeNS(android, "updatePeriodMillis"))
        val manifest = parse("src/main/AndroidManifest.xml")
        val receiver = element(manifest, "receiver", ".widgets.battery.BatteryWidgetProvider")
        val actions = receiver.getElementsByTagName("action")
        assertEquals(1, actions.length)
        assertEquals("android.appwidget.action.APPWIDGET_UPDATE", (actions.item(0) as Element).getAttributeNS(android, "name"))
    }

    @Test fun `wallpaper permission exists and no keyguard bypass is declared`() {
        val manifest = parse("src/main/AndroidManifest.xml")
        val permissions = manifest.getElementsByTagName("uses-permission")
        val names = (0 until permissions.length).map { (permissions.item(it) as Element).getAttributeNS(android, "name") }.toSet()
        assertTrue("android.permission.SET_WALLPAPER" in names)
        assertFalse("android.permission.DISABLE_KEYGUARD" in names)
        assertFalse("android.permission.SYSTEM_ALERT_WINDOW" in names)
        val activities = manifest.getElementsByTagName("activity")
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            assertNotEquals("true", activity.getAttributeNS(android, "showWhenLocked"))
            assertNotEquals("true", activity.getAttributeNS(android, "turnScreenOn"))
        }
    }
}
