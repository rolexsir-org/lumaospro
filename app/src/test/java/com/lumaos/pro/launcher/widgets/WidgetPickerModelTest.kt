package com.lumaos.pro.launcher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPickerModelTest {
    private val providers = listOf(
        WidgetPickerEntry("one", "example.first", "Clock", "Analog"),
        WidgetPickerEntry("two", "example.second", "Clock", "Digital"),
        WidgetPickerEntry("three", "example.first", "Clock", "World clock"),
        WidgetPickerEntry("four", "example.weather", "Weather", "Forecast")
    )

    @Test
    fun `different packages with the same app label both remain available`() {
        val groups = WidgetPickerModel.groups(providers, "")
        assertEquals(listOf("example.first", "example.second", "example.weather"), groups.map { it.packageName })
        assertEquals(listOf("one", "three"), groups.first().entries.map { it.provider })
        assertEquals("two", groups[1].entries.single().provider)
    }

    @Test
    fun `search uses cached widget and app labels and clearing restores every provider`() {
        assertEquals(listOf("two"), WidgetPickerModel.groups(providers, "  DIGITAL ").flatMap { it.entries }.map { it.provider })
        assertEquals(3, WidgetPickerModel.groups(providers, "clock").flatMap { it.entries }.size)
        assertTrue(WidgetPickerModel.groups(providers, "not installed").isEmpty())
        assertEquals(4, WidgetPickerModel.groups(providers, "  ").flatMap { it.entries }.size)
    }
}
