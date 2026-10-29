package com.lumaos.pro.launcher.ui.glass

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lumaos.pro.launcher.settings.LumaOSTheme
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeEnvironment
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises real Compose measurement, not a source-text assertion. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassTopBarLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wallpaperDestinationHeaderLeavesRoomForGallery() =
        verifyHeader("Wallpaper Studio", 360, 1f)

    @Test fun musicDestinationHeaderLeavesRoomForAccessContent() =
        verifyHeader("Luma Glass Music", 320, 2f)

    @Test fun settingsHeaderWithoutNavigationRemainsCompact() =
        verifyHeader("LumaOS Pro", 360, 1f, navigation = false)

    private fun verifyHeader(title: String, width: Int, fontScale: Float, navigation: Boolean = true) {
        val theme = ThemeEngine.resolve(ThemeSettings(), ThemeEnvironment(systemDark = true, minuteOfDay = 600))
        var backs = 0
        compose.setContent {
            LumaOSTheme(theme, 1f) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                    Column(Modifier.width(width.dp).fillMaxSize()) {
                        LumaGlassTopBar(
                            theme = theme,
                            title = title,
                            subtitle = "Screen description",
                            modifier = Modifier.testTag("header"),
                            navigationIcon = if (navigation) ({
                                LumaGlassChoice(theme, "Back", false, onClick = { backs++ })
                            }) else null
                        )
                        Text("Destination content", Modifier.testTag("content"))
                    }
                }
            }
        }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithTag("content").assertIsDisplayed()
        val header = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        val titleBounds = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        assertTrue("Title must not be starved by a fill-width Back control", titleBounds.width > header.width * 0.25f)
        assertTrue("Header must not consume the screen", header.height < header.width * 0.75f)
        assertTrue(content.top >= header.bottom)
        if (navigation) {
            compose.onNodeWithText("Back").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(1, backs) }
        }
    }
}
