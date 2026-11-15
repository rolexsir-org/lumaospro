package com.lumaos.pro.launcher.context

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.ui.glass.LumaGlassCard
import com.lumaos.pro.launcher.ui.glass.LumaGlassChoice
import com.lumaos.pro.launcher.ui.glass.LumaGlassTopBar
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import java.text.DateFormat
import java.util.Date

/**
 * The prominent in-app disclosure that must be answered before LumaOS Pro
 * routes to Android's accessibility screen.
 *
 * Two layers, on purpose:
 *
 *  - [FocusAccessDisclosureContent] is the disclosure itself: status, the five
 *    explanatory sections, and the explicit AGREE / DECLINE / manage / withdraw
 *    actions. It owns no scrolling, so both this screen and the first-run
 *    onboarding step render the *same* text and the *same* actions inside their
 *    own layout. There is exactly one copy of the disclosure in the product.
 *  - [FocusAccessDisclosureScreen] is the standalone screen (Settings →
 *    Privacy / Contexts), adding the top bar and the scrolling container.
 *
 * Stateless on purpose: the caller owns persistence and navigation, this
 * composable only renders the [FocusAccessUiState] and the real grant flag it
 * is given. The grant flag is separate from the state because a permission can
 * exist without consent, and the user must still be able to reach the system
 * switch to turn it off.
 *
 * The text is not a summary of the feature: it states the event type, the one
 * field read, the actions performed, what is not accessed, and what is stored,
 * collected or shared. Those claims match the implementation documented in
 * docs/ACCESSIBILITY_DISCLOSURE.md; if the service ever reads more, the
 * disclosure version must be bumped so the user is asked again.
 */
@Composable
fun FocusAccessDisclosureScreen(
    theme: ResolvedTheme,
    state: FocusAccessUiState,
    permissionGranted: Boolean,
    decidedAtMillis: Long,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onManage: () -> Unit,
    onWithdraw: () -> Unit,
    onClose: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        LumaGlassTopBar(
            theme = theme,
            title = stringResource(R.string.focus_access_disclosure_screen_title),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            navigationIcon = {
                LumaGlassChoice(
                    theme = theme,
                    label = stringResource(R.string.focus_access_back),
                    selected = false,
                    onClick = onBack
                )
            }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            FocusAccessDisclosureContent(
                theme = theme,
                state = state,
                permissionGranted = permissionGranted,
                decidedAtMillis = decidedAtMillis,
                onAgree = onAgree,
                onDecline = onDecline,
                onManage = onManage,
                onWithdraw = onWithdraw,
                onClose = onClose
            )
        }
    }
}

/**
 * The disclosure body, without scrolling or chrome, so a host screen can place
 * its own navigation around it. [footer] is rendered after the action buttons
 * (onboarding uses it for "Skip for now" / "Continue"); the standalone screen
 * passes nothing.
 */
@Composable
fun FocusAccessDisclosureContent(
    theme: ResolvedTheme,
    state: FocusAccessUiState,
    permissionGranted: Boolean,
    decidedAtMillis: Long,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onManage: () -> Unit,
    onWithdraw: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null
) {
    val bodyColor = Color(theme.colors.onSurface)
    val mutedColor = Color(theme.colors.onSurfaceMuted)
    val decidedLabel = remember(decidedAtMillis) {
        if (decidedAtMillis <= 0L) {
            null
        } else {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(decidedAtMillis))
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LumaGlassCard(
            theme = theme,
            title = stringResource(R.string.focus_access_status_title),
            subtitle = stringResource(statusTextRes(state))
        ) {
            Text(
                text = when {
                    decidedLabel != null && state == FocusAccessUiState.DECLINED ->
                        stringResource(R.string.focus_access_declined_on, decidedLabel)
                    decidedLabel != null ->
                        stringResource(R.string.focus_access_agreed_on, decidedLabel)
                    else -> stringResource(R.string.focus_access_not_answered)
                },
                color = mutedColor,
                fontSize = 12.sp
            )
        }

        DisclosureSection(
            theme = theme,
            bodyColor = bodyColor,
            title = stringResource(R.string.focus_access_why_title),
            body = stringResource(R.string.focus_access_why_body)
        )
        DisclosureSection(
            theme = theme,
            bodyColor = bodyColor,
            title = stringResource(R.string.focus_access_how_title),
            body = stringResource(R.string.focus_access_how_body)
        )
        DisclosureSection(
            theme = theme,
            bodyColor = bodyColor,
            title = stringResource(R.string.focus_access_reads_title),
            body = stringResource(R.string.focus_access_reads_body)
        )
        DisclosureSection(
            theme = theme,
            bodyColor = bodyColor,
            title = stringResource(R.string.focus_access_stores_title),
            body = stringResource(R.string.focus_access_stores_body)
        )
        DisclosureSection(
            theme = theme,
            bodyColor = bodyColor,
            title = stringResource(R.string.focus_access_control_title),
            body = stringResource(R.string.focus_access_control_body)
        )

        // Explicit actions. AGREE and DECLINE are two equally reachable
        // buttons; nothing is pre-selected and no navigation to system
        // settings happens before Agree has been persisted.
        when (state) {
            FocusAccessUiState.NEEDS_DISCLOSURE -> {
                LumaGlassChoice(
                    theme = theme,
                    label = stringResource(R.string.focus_access_agree),
                    selected = true,
                    onClick = onAgree,
                    modifier = Modifier.fillMaxWidth()
                )
                LumaGlassChoice(
                    theme = theme,
                    label = stringResource(R.string.focus_access_decline),
                    selected = false,
                    onClick = onDecline,
                    modifier = Modifier.fillMaxWidth()
                )
                // A grant can exist without consent (enabled in system settings).
                // The user must still be able to reach the switch to turn it off.
                if (permissionGranted) ManageAction(theme, onManage)
            }
            FocusAccessUiState.DECLINED -> {
                LumaGlassChoice(
                    theme = theme,
                    label = stringResource(R.string.focus_access_agree),
                    selected = true,
                    onClick = onAgree,
                    modifier = Modifier.fillMaxWidth()
                )
                LumaGlassChoice(
                    theme = theme,
                    label = stringResource(R.string.focus_access_close),
                    selected = false,
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth()
                )
                // After withdrawing consent the system grant may still be on;
                // keep the route to Android's switch available.
                if (permissionGranted) ManageAction(theme, onManage)
            }
            FocusAccessUiState.AWAITING_PERMISSION,
            FocusAccessUiState.ACTIVE -> {
                LumaGlassChoice(
                    theme = theme,
                    label = stringResource(R.string.focus_access_manage),
                    selected = true,
                    onClick = onManage,
                    modifier = Modifier.fillMaxWidth()
                )
                LumaGlassChoice(
                    theme = theme,
                    label = stringResource(R.string.focus_access_withdraw),
                    selected = false,
                    onClick = onWithdraw,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (footer != null) {
            footer.invoke(this)
        }

        Text(
            stringResource(R.string.focus_access_decline_note),
            color = mutedColor,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ManageAction(theme: ResolvedTheme, onManage: () -> Unit) {
    LumaGlassChoice(
        theme = theme,
        label = stringResource(R.string.focus_access_manage),
        selected = false,
        onClick = onManage,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DisclosureSection(
    theme: ResolvedTheme,
    bodyColor: Color,
    title: String,
    body: String
) {
    LumaGlassCard(theme = theme, title = title) {
        Text(body, color = bodyColor, fontSize = 14.sp)
    }
}

private fun statusTextRes(state: FocusAccessUiState): Int = when (state) {
    FocusAccessUiState.NEEDS_DISCLOSURE -> R.string.focus_access_status_needs_disclosure
    FocusAccessUiState.DECLINED -> R.string.focus_access_status_declined
    FocusAccessUiState.AWAITING_PERMISSION -> R.string.focus_access_status_awaiting
    FocusAccessUiState.ACTIVE -> R.string.focus_access_status_active
}
