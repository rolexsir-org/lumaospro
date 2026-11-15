package com.lumaos.pro.launcher.robolectric

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.context.FocusAccessConsent
import com.lumaos.pro.launcher.context.FocusAccessConsentRecord
import com.lumaos.pro.launcher.context.FocusAccessConsentState
import com.lumaos.pro.launcher.context.FocusAccessDisclosureScreen
import com.lumaos.pro.launcher.context.FocusAccessUiState
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

/**
 * Real Compose measurement of the prominent disclosure: the promised sections
 * exist, AGREE and DECLINE are both reachable on a first run, and the actions
 * rendered for each stored state are the right ones.
 *
 * The activity (not this composable) owns persistence and navigation, so these
 * tests prove the visible contract: a first-time user can only choose Agree or
 * Decline, and only a consented user sees the manage/withdraw pair.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FocusAccessDisclosureScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context.getString(id)

    private fun setScreen(
        state: FocusAccessUiState,
        permissionGranted: Boolean = false,
        decidedAtMillis: Long = 0L,
        onAgree: () -> Unit = {},
        onDecline: () -> Unit = {},
        onManage: () -> Unit = {},
        onWithdraw: () -> Unit = {},
        onClose: () -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        val theme = ThemeEngine.resolve(
            ThemeSettings(),
            ThemeEnvironment(systemDark = true, minuteOfDay = 600)
        )
        compose.setContent {
            LumaOSTheme(theme, 1f) {
                FocusAccessDisclosureScreen(
                    theme = theme,
                    state = state,
                    permissionGranted = permissionGranted,
                    decidedAtMillis = decidedAtMillis,
                    onAgree = onAgree,
                    onDecline = onDecline,
                    onManage = onManage,
                    onWithdraw = onWithdraw,
                    onClose = onClose,
                    onBack = onBack
                )
            }
        }
    }

    @Test fun `first time discloses why what and storage before any permission`() {
        setScreen(FocusAccessUiState.NEEDS_DISCLOSURE)
        compose.onNodeWithText(text(R.string.focus_access_why_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.focus_access_how_title)).assertExists()
        compose.onNodeWithText(text(R.string.focus_access_reads_title)).assertExists()
        compose.onNodeWithText(text(R.string.focus_access_stores_title)).assertExists()
        compose.onNodeWithText(text(R.string.focus_access_control_title)).assertExists()
        // The disclosure names the actual event source and access limits.
        compose.onNodeWithText(text(R.string.focus_access_reads_body)).assertExists()
        compose.onNodeWithText(text(R.string.focus_access_agree)).assertExists()
        compose.onNodeWithText(text(R.string.focus_access_decline)).assertExists()
        compose.onNodeWithText(text(R.string.focus_access_manage)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.focus_access_withdraw)).assertDoesNotExist()
    }

    @Test fun `agree and decline are both explicit and wired`() {
        var agree = 0
        var decline = 0
        setScreen(
            FocusAccessUiState.NEEDS_DISCLOSURE,
            onAgree = { agree++ },
            onDecline = { decline++ }
        )
        compose.onNodeWithText(text(R.string.focus_access_agree)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.focus_access_decline)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.focus_access_agree)).performClick()
        compose.onNodeWithText(text(R.string.focus_access_decline)).performClick()
        assertEquals(1, agree)
        assertEquals(1, decline)
    }

    @Test fun `declined state offers a reviewed agreement and a close, not a prompt loop`() {
        var close = 0
        setScreen(
            FocusAccessUiState.DECLINED,
            decidedAtMillis = 1_700_000_000_000L,
            onClose = { close++ }
        )
        compose.onNodeWithText(text(R.string.focus_access_status_declined)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.focus_access_agree)).assertExists()
        compose.onNodeWithText(text(R.string.focus_access_close)).performScrollTo().performClick()
        assertEquals(1, close)
        compose.onNodeWithText(text(R.string.focus_access_decline)).assertDoesNotExist()
        // No system settings route when Android holds no grant.
        compose.onNodeWithText(text(R.string.focus_access_manage)).assertDoesNotExist()
    }

    @Test fun `a grant without consent still exposes the route to switch it off`() {
        var manage = 0
        setScreen(
            FocusAccessUiState.NEEDS_DISCLOSURE,
            permissionGranted = true,
            onManage = { manage++ }
        )
        // The disclosure is still the primary content...
        compose.onNodeWithText(text(R.string.focus_access_reads_title)).assertExists()
        // ...and the system switch stays reachable for the unwanted grant.
        compose.onNodeWithText(text(R.string.focus_access_manage)).performScrollTo().performClick()
        assertEquals(1, manage)
    }

    @Test fun `withdrawn consent with the grant still on keeps the settings route`() {
        var manage = 0
        setScreen(
            FocusAccessUiState.DECLINED,
            permissionGranted = true,
            onManage = { manage++ }
        )
        compose.onNodeWithText(text(R.string.focus_access_manage)).performScrollTo().performClick()
        assertEquals(1, manage)
    }

    @Test fun `consented states expose revoke and withdraw instead of agree`() {
        var manage = 0
        var withdraw = 0
        setScreen(
            FocusAccessUiState.AWAITING_PERMISSION,
            decidedAtMillis = 1_700_000_000_000L,
            onManage = { manage++ },
            onWithdraw = { withdraw++ }
        )
        compose.onNodeWithText(text(R.string.focus_access_status_awaiting)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.focus_access_manage)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.focus_access_withdraw)).performScrollTo().performClick()
        assertEquals(1, manage)
        assertEquals(1, withdraw)
        compose.onNodeWithText(text(R.string.focus_access_agree)).assertDoesNotExist()
    }

    @Test fun `active state states that it is on and still allows revocation`() {
        setScreen(FocusAccessUiState.ACTIVE, decidedAtMillis = 1_700_000_000_000L)
        compose.onNodeWithText(text(R.string.focus_access_status_active)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.focus_access_withdraw)).assertExists()
    }

    @Test fun `status text is derived from stored consent and the real grant`() {
        val agreed = FocusAccessConsentRecord(
            state = FocusAccessConsentState.GRANTED,
            disclosureVersion = FocusAccessConsent.CURRENT_DISCLOSURE_VERSION,
            decidedAtMillis = 1_700_000_000_000L
        )
        assertEquals(
            FocusAccessUiState.ACTIVE,
            FocusAccessConsent.uiState(agreed, permissionGranted = true)
        )
        assertEquals(
            FocusAccessUiState.AWAITING_PERMISSION,
            FocusAccessConsent.uiState(agreed, permissionGranted = false)
        )
        assertTrue(FocusAccessConsent.grantsAccess(agreed))
        assertTrue(
            FocusAccessConsent.uiState(FocusAccessConsent.NOT_ASKED, permissionGranted = true) ==
                FocusAccessUiState.NEEDS_DISCLOSURE
        )
    }
}
