package com.lumaos.pro.launcher.robolectric

import android.app.Application
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.widgets.builtin.BuiltinWidgetSettings
import com.lumaos.pro.launcher.widgets.builtin.ScreenTime
import com.lumaos.pro.launcher.widgets.builtin.WeatherData
import com.lumaos.pro.launcher.widgets.builtin.WeatherPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BuiltinWidgetsRuntimeTest {
    @Test
    fun allBundledLayoutsInflateAsNativeRemoteViews() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val ids = listOf(R.layout.widget_luma_digital, R.layout.widget_luma_analog,
            R.layout.widget_luma_reading, R.layout.widget_luma_battery, R.layout.widget_luma_music)
        for (id in ids) assertNotNull(RemoteViews(app.packageName, id).apply(app, FrameLayout(app)))
    }

    @Test
    fun widgetConfigurationRemapsAndDeletesById() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val place = WeatherPlace(31.1, -80.2)
        BuiltinWidgetSettings.save(app, 4, 26, place)
        BuiltinWidgetSettings.save(app, 5, 14, null)
        BuiltinWidgetSettings.restore(app, intArrayOf(4, 5), intArrayOf(5, 7))
        assertNull(BuiltinWidgetSettings.radius(app, 4))
        assertEquals(place, BuiltinWidgetSettings.place(app, 5))
        assertEquals(26f, BuiltinWidgetSettings.radius(app, 5) ?: -1f, 0f)
        assertEquals(14f, BuiltinWidgetSettings.radius(app, 7) ?: -1f, 0f)
        BuiltinWidgetSettings.delete(app, intArrayOf(5))
        assertNull(BuiltinWidgetSettings.place(app, 5))
        assertEquals(14f, BuiltinWidgetSettings.radius(app, 7) ?: -1f, 0f)
    }

    @Test
    fun forecastParserReadsApiValuesAndSettingsValidateCoordinates() {
        assertEquals(WeatherPlace(33.1, 74.0), WeatherPlace.parse("33,1", "74"))
        assertNull(WeatherPlace.parse("NaN", "74"))
        assertNull(WeatherPlace.parse("45", "181"))
        val data = WeatherData.parse("""{"current":{"temperature_2m":12.4,"weather_code":3}}""", 123L)
        assertEquals(12.4, data.celsius, 0.001)
        assertEquals(3, data.code)
        assertEquals(123L, data.fetchedAt)
    }

    @Test
    fun historicalBarDoesNotUseEventsFromTheFollowingDay() {
        val historical = ScreenTime(start = 100, now = 200)
        historical.record(240, interactive = true)
        assertNull(historical.total())
        val activeAtMidnight = ScreenTime(start = 100, now = 200)
        activeAtMidnight.record(80, interactive = true)
        activeAtMidnight.record(220, interactive = false)
        assertEquals(100L, activeAtMidnight.total())
    }

    @Test
    fun screenTimeCountsOnlyInteractiveTimeInsideLocalDay() {
        val timer = ScreenTime(start = 100, now = 300)
        assertNull(timer.total())
        timer.record(70, interactive = true)
        timer.record(140, interactive = false)
        timer.record(200, interactive = true)
        timer.record(220, interactive = true)
        assertEquals(140L, timer.total())
    }
}
