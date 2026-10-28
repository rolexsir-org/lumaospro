package com.lumaos.pro.launcher.ui.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassCapabilities
import com.lumaos.pro.launcher.ui.theme.GlassColors
import com.lumaos.pro.launcher.ui.theme.GlassGeometry
import com.lumaos.pro.launcher.ui.theme.GlassMaterials
import com.lumaos.pro.launcher.ui.theme.GlassPalette
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.GlassSpec
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeEnvironment
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import java.util.Locale

/**
 * Compose-side adapter for the exact same [GlassMaterials] recipes used by the
 * View launcher. Settings use [GlassCapabilities.SOLID]: legible opaque glass
 * layers with no full-screen real-time blur.
 */
/**
 * Optional snapshot of the palette for the currently applied theme. Component
 * helpers still resolve from their explicit `theme` argument so Studio previews
 * of an unsaved draft never accidentally use the applied theme.
 */
val LocalGlassPalette = staticCompositionLocalOf {
    GlassMaterials.palette(
        ThemeEngine.resolve(ThemeSettings(), ThemeEnvironment(systemDark = true, minuteOfDay = 0)),
        GlassCapabilities.SOLID
    )
}

@Composable
fun rememberGlassPalette(theme: ResolvedTheme): GlassPalette =
    remember(theme) { GlassMaterials.palette(theme, GlassCapabilities.SOLID) }

@Composable
private fun rememberGlassSpec(theme: ResolvedTheme, role: GlassRole): GlassSpec =
    rememberGlassPalette(theme)[role]

@Composable
private fun glassShape(spec: GlassSpec): RoundedCornerShape {
    val r: Dp = spec.radiusDp.dp
    return if (spec.topOnly) {
        RoundedCornerShape(topStart = r, topEnd = r, bottomStart = 0.dp, bottomEnd = 0.dp)
    } else {
        RoundedCornerShape(r)
    }
}

private fun fillBrush(colors: GlassColors): Brush = Brush.verticalGradient(
    0f to Color(ColorMath.lerp(colors.fillTop, colors.specular, 0.38f)),
    0.045f to Color(colors.fillTop),
    1f to Color(colors.fillBottom)
)

private fun edgeBrush(colors: GlassColors): Brush = Brush.verticalGradient(
    listOf(Color(colors.edgeTop), Color(colors.edgeBottom))
)

private fun specularBrush(colors: GlassColors): Brush = Brush.horizontalGradient(
    listOf(Color.Transparent, Color(colors.specular), Color.Transparent)
)

/**
 * The one Compose glass surface. All other glass components compose it, so
 * pressed, focused and selected states cannot drift from the Android Views.
 */
@Composable
fun LumaGlassSurface(
    theme: ResolvedTheme,
    role: GlassRole,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focused: Boolean = false,
    pressed: Boolean? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val spec = rememberGlassSpec(theme, role)
    val interactionSource = remember { MutableInteractionSource() }
    val interactionPressed by interactionSource.collectIsPressedAsState()
    val isPressed = pressed ?: interactionPressed
    val colors = when {
        isPressed -> spec.states.pressed
        selected -> spec.states.selected
        focused -> spec.states.focused
        else -> spec.states.rest
    }
    val shape = glassShape(spec)
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(clickModifier)
            .shadow(
                elevation = spec.elevationDp.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(theme.colors.shadowColor),
                spotColor = Color(theme.colors.shadowColor)
            )
            .clip(shape)
            .background(fillBrush(colors), shape)
            .border(BorderStroke(spec.borderDp.dp, edgeBrush(colors)), shape),
        content = {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(horizontal = (spec.radiusDp * 0.55f).dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(specularBrush(colors))
                )
            }
            content()
        }
    )
}

@Composable
fun LumaGlassCard(
    theme: ResolvedTheme,
    title: String,
    modifier: Modifier = Modifier,
    role: GlassRole = GlassRole.SECONDARY,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val spec = rememberGlassSpec(theme, role)
    val colors = spec.rest
    LumaGlassSurface(theme, role, modifier.fillMaxWidth()) {
        Column(Modifier.padding(GlassGeometry.GUTTER_DP.dp)) {
            Text(
                title,
                color = Color(colors.content),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Color(colors.muted), fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun LumaGlassSectionTitle(
    theme: ResolvedTheme,
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = rememberGlassSpec(theme, GlassRole.SECONDARY).rest
    Text(
        text,
        modifier = modifier.padding(vertical = 8.dp),
        color = Color(colors.muted),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp
    )
}

@Composable
fun LumaGlassChoice(
    theme: ResolvedTheme,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    caption: String? = null
) {
    val spec = rememberGlassSpec(theme, GlassRole.SETTINGS_CONTROL)
    val colors = if (selected) spec.states.selected else spec.states.rest
    LumaGlassSurface(
        theme,
        GlassRole.SETTINGS_CONTROL,
        modifier.heightIn(min = GlassGeometry.CONTROL_DP.dp),
        selected = selected,
        enabled = enabled,
        onClick = onClick
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                color = Color(colors.content),
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            if (!caption.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(caption, color = Color(colors.muted), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun LumaGlassIconButton(
    theme: ResolvedTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    LumaGlassSurface(
        theme,
        GlassRole.SETTINGS_CONTROL,
        modifier.size(GlassGeometry.CONTROL_DP.dp),
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        content = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                content()
            }
        }
    )
}

@Composable
fun LumaGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    theme: ResolvedTheme,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    onTap: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val spec = rememberGlassSpec(theme, GlassRole.SEARCH)
    val colors = if (focused) spec.states.focused else spec.states.rest

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        textStyle = TextStyle(
            color = Color(colors.content),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(Color(colors.focusRing)),
        singleLine = singleLine,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            LumaGlassSurface(
                theme,
                GlassRole.SEARCH,
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = GlassGeometry.SEARCH_HEIGHT_DP.dp),
                focused = focused,
                onClick = if (readOnly) onTap else null
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (leadingIcon != null) {
                        leadingIcon()
                        Spacer(Modifier.width(12.dp))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(placeholder, color = Color(colors.muted), fontSize = 15.sp)
                        }
                        inner()
                    }
                    if (trailingIcon != null) {
                        Spacer(Modifier.width(12.dp))
                        trailingIcon()
                    }
                }
            }
        }
    )
}

@Composable
fun LumaGlassSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    theme: ResolvedTheme,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    valueLabel: String? = null
) {
    val spec = rememberGlassSpec(theme, GlassRole.SETTINGS_CONTROL)
    val colors = spec.rest
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), color = Color(colors.content), fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                valueLabel ?: String.format(Locale.US, "%.2f", value),
                color = Color(colors.muted),
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color(colors.focusRing),
                activeTrackColor = Color(colors.focusRing),
                inactiveTrackColor = Color(colors.edgeTop),
                disabledThumbColor = Color(colors.muted),
                disabledActiveTrackColor = Color(colors.muted),
                disabledInactiveTrackColor = Color(colors.edgeBottom)
            )
        )
    }
}

@Composable
fun LumaGlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    theme: ResolvedTheme,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val spec = rememberGlassSpec(theme, GlassRole.SETTINGS_CONTROL)
    val colors = spec.rest
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color(colors.content),
            checkedTrackColor = Color(colors.focusRing),
            uncheckedThumbColor = Color(colors.muted),
            uncheckedTrackColor = Color(colors.edgeTop),
            disabledCheckedThumbColor = Color(colors.muted),
            disabledCheckedTrackColor = Color(colors.edgeBottom),
            disabledUncheckedThumbColor = Color(colors.muted),
            disabledUncheckedTrackColor = Color(colors.edgeBottom)
        )
    )
}

@Composable
fun LumaGlassSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    theme: ResolvedTheme,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    val colors = rememberGlassSpec(theme, GlassRole.SECONDARY).rest
    LumaGlassSurface(theme, GlassRole.SECONDARY, modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = GlassGeometry.CONTROL_DP.dp)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(GlassGeometry.GUTTER_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = Color(colors.content), fontSize = 15.sp)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, color = Color(colors.muted), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            // The parent owns the single labelled accessibility toggle and target.
            LumaGlassSwitch(checked, null, theme, enabled = enabled)
        }
    }
}

@Composable
fun LumaGlassTopBar(
    theme: ResolvedTheme,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = rememberGlassSpec(theme, GlassRole.SECONDARY).rest
    LumaGlassSurface(theme, GlassRole.SECONDARY, modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = GlassGeometry.GUTTER_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Color(colors.content),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, color = Color(colors.muted), fontSize = 12.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@Composable
fun LumaGlassScaffold(
    theme: ResolvedTheme,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color(theme.colors.background))) {
        Column(modifier.fillMaxSize()) {
            LumaGlassTopBar(
                theme = theme,
                title = title,
                subtitle = subtitle,
                navigationIcon = navigationIcon,
                actions = actions
            )
            content(PaddingValues(top = 16.dp, bottom = 24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumaGlassBottomSheet(
    theme: ResolvedTheme,
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val spec = rememberGlassSpec(theme, GlassRole.SHEET)
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = spec.rest
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = state,
        shape = glassShape(spec),
        containerColor = Color.Transparent,
        contentColor = Color(colors.content),
        scrimColor = Color(theme.colors.scrim),
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        LumaGlassSurface(theme, GlassRole.SHEET, Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GlassGeometry.GUTTER_DP.dp, vertical = 12.dp)
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color(colors.edgeTop))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    title,
                    color = Color(colors.content),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
fun LumaGlassPopup(
    theme: ResolvedTheme,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: IntOffset = IntOffset.Zero,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return
    Popup(
        onDismissRequest = onDismissRequest,
        offset = offset,
        properties = PopupProperties(focusable = true)
    ) {
        LumaGlassSurface(
            theme,
            GlassRole.MENU,
            modifier.widthIn(min = 180.dp)
        ) {
            Column(Modifier.padding(8.dp), content = content)
        }
    }
}

@Composable
fun LumaGlassPreviewFrame(
    theme: ResolvedTheme,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    LumaGlassSurface(theme, GlassRole.PRIMARY, modifier, content = content)
}
