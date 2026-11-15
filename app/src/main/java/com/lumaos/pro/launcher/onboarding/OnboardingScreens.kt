package com.lumaos.pro.launcher.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.context.FocusAccessDisclosureContent
import com.lumaos.pro.launcher.context.FocusAccessUiState
import com.lumaos.pro.launcher.launcher.model.GestureAction
import com.lumaos.pro.launcher.core.prefs.GestureKind
import com.lumaos.pro.launcher.settings.gestureActionLabel
import com.lumaos.pro.launcher.settings.gestureKindLabel
import com.lumaos.pro.launcher.ui.glass.LumaGlassCard
import com.lumaos.pro.launcher.ui.glass.LumaGlassChoice
import com.lumaos.pro.launcher.ui.glass.LumaGlassTopBar
import com.lumaos.pro.launcher.ui.theme.ExperienceProfile
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme

/** A gesture and the action the user has actually bound to it. */
data class GestureBinding(val kind: GestureKind, val action: GestureAction)

/**
 * The chrome shared by every step: progress, a back affordance and a scrollable
 * column with a comfortable reading width on tablets and foldables.
 *
 * Only chrome lives here. Each step owns its content and its own actions, so
 * "skip" can mean the right thing on each screen instead of a single opaque
 * global button. Everything is one vertical scroll, which keeps the flow usable
 * at 200% font scale and on short landscape windows without nested scrolling.
 */
@Composable
fun OnboardingShell(
    theme: ResolvedTheme,
    state: OnboardingState,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val progressLabel = stringResource(
        R.string.onboarding_step_progress,
        OnboardingFlow.stepNumber(state.step),
        OnboardingFlow.totalSteps
    )
    val canGoBack = !OnboardingFlow.isFirstStep(state.step)
    // Explicit type: the slot is a nullable composable lambda, and an `if`
    // expression with a lambda literal in one branch and `null` in the other
    // needs the expected type spelled out.
    val navigation: (@Composable () -> Unit)? = if (canGoBack) {
        {
            LumaGlassChoice(
                theme = theme,
                label = stringResource(R.string.onboarding_back),
                selected = false,
                onClick = onBack
            )
        }
    } else {
        null
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(theme.colors.background))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        LumaGlassTopBar(
            theme = theme,
            title = stringResource(R.string.onboarding_title),
            subtitle = progressLabel,
            navigationIcon = navigation
        )
        StepProgress(theme = theme, step = state.step, label = progressLabel)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

@Composable
private fun StepProgress(theme: ResolvedTheme, step: OnboardingStep, label: String) {
    val current = OnboardingFlow.stepNumber(step)
    val active = Color(theme.colors.primary)
    val track = Color(theme.colors.onSurfaceMuted).copy(alpha = 0.22f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OnboardingFlow.steps.forEachIndexed { index, _ ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < current) active else track)
            )
        }
    }
}

@Composable
private fun StepHeading(theme: ResolvedTheme, title: String, body: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = Color(theme.colors.onSurface),
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = body,
            color = Color(theme.colors.onSurfaceMuted),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun PrimaryAction(theme: ResolvedTheme, label: String, onClick: () -> Unit) {
    LumaGlassChoice(
        theme = theme,
        label = label,
        selected = true,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    )
}

@Composable
private fun SecondaryAction(theme: ResolvedTheme, label: String, onClick: () -> Unit) {
    LumaGlassChoice(
        theme = theme,
        label = label,
        selected = false,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    )
}

// ---------------------------------------------------------------- 1. Welcome

@Composable
fun WelcomeStep(
    theme: ResolvedTheme,
    mode: OnboardingMode,
    onStart: () -> Unit,
    onSkipTour: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(88.dp)
        )
        Text(
            text = stringResource(R.string.onboarding_welcome),
            color = Color(theme.colors.onSurface),
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            color = Color(theme.colors.onSurfaceMuted),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
    }
    if (mode == OnboardingMode.REPLAY) {
        LumaGlassCard(theme = theme, title = stringResource(R.string.onboarding_replay_note_title)) {
            Text(
                text = stringResource(R.string.onboarding_replay_note),
                color = Color(theme.colors.onSurfaceMuted),
                fontSize = 13.sp
            )
        }
    }
    LumaGlassCard(theme = theme, title = stringResource(R.string.onboarding_welcome_points_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WelcomePoint(theme, stringResource(R.string.onboarding_contexts))
            WelcomePoint(theme, stringResource(R.string.onboarding_focus_point_short))
            WelcomePoint(theme, stringResource(R.string.onboarding_welcome_point_look))
        }
    }
    Text(
        text = stringResource(R.string.onboarding_time_note),
        color = Color(theme.colors.onSurfaceMuted),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    PrimaryAction(theme, stringResource(R.string.onboarding_start), onStart)
    if (mode == OnboardingMode.REPLAY) {
        SecondaryAction(theme, stringResource(R.string.onboarding_close), onClose)
    } else {
        // Even the welcome screen can be left: skipping goes to the home-app
        // question, never straight past it.
        SecondaryAction(theme, stringResource(R.string.onboarding_skip_tour), onSkipTour)
    }
}

@Composable
private fun WelcomePoint(theme: ResolvedTheme, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(theme.colors.primary))
        )
        Spacer(Modifier.size(10.dp))
        Text(text, color = Color(theme.colors.onSurface), fontSize = 13.sp)
    }
}

// --------------------------------------------------------------- 2. Gestures

@Composable
fun GesturesStep(
    theme: ResolvedTheme,
    bindings: List<GestureBinding>,
    onContinue: () -> Unit,
    onSkipTour: () -> Unit
) {
    StepHeading(
        theme = theme,
        title = stringResource(R.string.onboarding_gestures_title),
        body = stringResource(R.string.onboarding_gestures_subtitle)
    )
    LumaGlassCard(
        theme = theme,
        title = stringResource(R.string.onboarding_gestures_bindings_title),
        subtitle = stringResource(R.string.onboarding_gestures_bindings_subtitle)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            bindings.forEach { binding ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = gestureKindLabel(binding.kind),
                        color = Color(theme.colors.onSurface),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = gestureActionLabel(binding.action),
                        color = Color(theme.colors.onSurfaceMuted),
                        fontSize = 14.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_gestures_note),
            color = Color(theme.colors.onSurfaceMuted),
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
    PrimaryAction(theme, stringResource(R.string.onboarding_continue), onContinue)
    SecondaryAction(theme, stringResource(R.string.onboarding_skip_tour), onSkipTour)
}

// ------------------------------------------------------------ 3. Personalize

@Composable
fun PersonalizeStep(
    theme: ResolvedTheme,
    selected: ExperienceProfile,
    livingIcons: List<Pair<String, ImageBitmap>>,
    onSelect: (ExperienceProfile) -> Unit,
    onOpenStudio: () -> Unit,
    onContinue: () -> Unit,
    onSkipTour: () -> Unit
) {
    StepHeading(
        theme = theme,
        title = stringResource(R.string.onboarding_personalize_title),
        body = stringResource(R.string.onboarding_personalize_subtitle)
    )
    if (livingIcons.isNotEmpty()) {
        LumaGlassCard(
            theme = theme,
            title = stringResource(R.string.onboarding_living_icons_title),
            subtitle = stringResource(R.string.onboarding_living_icons_caption)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Real Living Icons from this install, rendered by the same
                // renderer the home screen uses. Not sample artwork.
                livingIcons.forEach { (name, bitmap) ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = name,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }
    }
    ExperienceProfile.entries.forEach { profile ->
        ProfileChoice(theme = theme, profile = profile, selected = profile == selected, onSelected = { onSelect(profile) })
    }
    LumaGlassCard(theme = theme, title = stringResource(R.string.onboarding_personalize_studio_title)) {
        Text(
            text = stringResource(R.string.onboarding_personalize_studio_body),
            color = Color(theme.colors.onSurfaceMuted),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(10.dp))
        SecondaryAction(theme, stringResource(R.string.onboarding_open_studio), onOpenStudio)
    }
    PrimaryAction(theme, stringResource(R.string.onboarding_continue), onContinue)
    SecondaryAction(theme, stringResource(R.string.onboarding_skip_tour), onSkipTour)
}

@Composable
private fun ProfileChoice(
    theme: ResolvedTheme,
    profile: ExperienceProfile,
    selected: Boolean,
    onSelected: () -> Unit
) {
    val title = when (profile) {
        ExperienceProfile.PURE -> stringResource(R.string.experience_pure)
        ExperienceProfile.LIQUID -> stringResource(R.string.experience_liquid)
        ExperienceProfile.MIDNIGHT -> stringResource(R.string.experience_midnight)
    }
    val description = when (profile) {
        ExperienceProfile.PURE -> stringResource(R.string.experience_pure_desc)
        ExperienceProfile.LIQUID -> stringResource(R.string.experience_liquid_desc)
        ExperienceProfile.MIDNIGHT -> stringResource(R.string.experience_midnight_desc)
    }
    LumaGlassChoice(
        theme = theme,
        label = title,
        caption = description,
        selected = selected,
        onClick = onSelected,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
    )
}

// ------------------------------------------------------- 4. Focus Protection

@Composable
fun FocusProtectionStep(
    theme: ResolvedTheme,
    onContinue: () -> Unit,
    onSkipTour: () -> Unit
) {
    StepHeading(
        theme = theme,
        title = stringResource(R.string.onboarding_focus_title),
        body = stringResource(R.string.onboarding_focus_subtitle)
    )
    LumaGlassCard(theme = theme, title = stringResource(R.string.onboarding_focus_layers_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusPoint(
                theme = theme,
                title = stringResource(R.string.onboarding_focus_layer1_title),
                body = stringResource(R.string.onboarding_focus_layer1_body)
            )
            FocusPoint(
                theme = theme,
                title = stringResource(R.string.onboarding_focus_layer2_title),
                body = stringResource(R.string.onboarding_focus_layer2_body)
            )
        }
    }
    LumaGlassCard(theme = theme, title = stringResource(R.string.onboarding_focus_optional_title)) {
        Text(
            text = stringResource(R.string.onboarding_focus_optional_body),
            color = Color(theme.colors.onSurfaceMuted),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
    PrimaryAction(theme, stringResource(R.string.onboarding_continue), onContinue)
    SecondaryAction(theme, stringResource(R.string.onboarding_skip_tour), onSkipTour)
}

@Composable
private fun FocusPoint(theme: ResolvedTheme, title: String, body: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color(theme.colors.onSurface), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(body, color = Color(theme.colors.onSurfaceMuted), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

// ----------------------------------------------------------- 5. Accessibility

@Composable
fun AccessibilityStep(
    theme: ResolvedTheme,
    state: FocusAccessUiState,
    permissionGranted: Boolean,
    decidedAtMillis: Long,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onManage: () -> Unit,
    onWithdraw: () -> Unit,
    onContinue: () -> Unit,
    onSkipForNow: () -> Unit
) {
    StepHeading(
        theme = theme,
        title = stringResource(R.string.onboarding_accessibility_title),
        body = stringResource(R.string.onboarding_accessibility_subtitle)
    )
    // The same disclosure text and the same explicit actions as the standalone
    // screen: onboarding never asks for the grant on its own, and there is only
    // one copy of the wording to keep accurate.
    FocusAccessDisclosureContent(
        theme = theme,
        state = state,
        permissionGranted = permissionGranted,
        decidedAtMillis = decidedAtMillis,
        onAgree = onAgree,
        onDecline = onDecline,
        onManage = onManage,
        onWithdraw = onWithdraw,
        onClose = onSkipForNow,
        footer = {
            if (state == FocusAccessUiState.NEEDS_DISCLOSURE) {
                SecondaryAction(theme, stringResource(R.string.onboarding_skip_for_now), onSkipForNow)
            } else {
                PrimaryAction(theme, stringResource(R.string.onboarding_continue), onContinue)
            }
        }
    )
}

// --------------------------------------------------------------- 6. Home role

@Composable
fun HomeRoleStep(
    theme: ResolvedTheme,
    isDefaultHome: Boolean,
    onSetDefault: () -> Unit,
    onContinue: () -> Unit
) {
    StepHeading(
        theme = theme,
        title = stringResource(R.string.onboarding_home_title),
        body = stringResource(R.string.onboarding_home_role_explainer)
    )
    LumaGlassCard(
        theme = theme,
        title = stringResource(R.string.onboarding_home_status_title),
        subtitle = stringResource(
            if (isDefaultHome) R.string.role_status_default else R.string.onboarding_home_status_pending
        )
    ) {
        Text(
            text = stringResource(
                if (isDefaultHome) R.string.onboarding_home_status_default_body
                else R.string.onboarding_home_status_pending_body
            ),
            color = Color(theme.colors.onSurfaceMuted),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
    if (isDefaultHome) {
        PrimaryAction(theme, stringResource(R.string.onboarding_continue), onContinue)
    } else {
        PrimaryAction(theme, stringResource(R.string.onboarding_set_default), onSetDefault)
        SecondaryAction(theme, stringResource(R.string.onboarding_skip), onContinue)
    }
}

// ---------------------------------------------------------------- 7. Complete

@Composable
fun CompleteStep(
    theme: ResolvedTheme,
    isDefaultHome: Boolean,
    onFinish: () -> Unit
) {
    StepHeading(
        theme = theme,
        title = stringResource(R.string.onboarding_complete_title),
        body = stringResource(R.string.onboarding_complete_subtitle)
    )
    LumaGlassCard(theme = theme, title = stringResource(R.string.onboarding_complete_setup_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WelcomePoint(
                theme,
                stringResource(
                    if (isDefaultHome) R.string.onboarding_complete_home_done
                    else R.string.onboarding_complete_home_pending
                )
            )
            WelcomePoint(theme, stringResource(R.string.onboarding_complete_settings_hint))
        }
    }
    Text(
        text = stringResource(R.string.onboarding_complete_replay_hint),
        color = Color(theme.colors.onSurfaceMuted),
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
    PrimaryAction(theme, stringResource(R.string.onboarding_enter_home), onFinish)
}
