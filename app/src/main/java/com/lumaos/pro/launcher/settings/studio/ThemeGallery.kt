package com.lumaos.pro.launcher.settings.studio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.launcher.model.DockStyle
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassCapabilities
import com.lumaos.pro.launcher.ui.theme.GlassMaterials
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.LumaDesignTokens
import com.lumaos.pro.launcher.ui.theme.RadiusRole
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeEnvironment
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemeLauncherPackage
import com.lumaos.pro.launcher.ui.theme.ThemePresets
import com.lumaos.pro.launcher.ui.theme.ThemeSpec
import kotlin.math.min

/**
 * Theme gallery for Studio.
 *
 * Every miniature here is painted from the theme's own resolved tokens -
 * [com.lumaos.pro.launcher.ui.theme.AtmospherePlan] for the backdrop and the
 * [GlassMaterials] palette for dock and tiles - so a card can never drift from
 * what the launcher actually draws, and no card owns a private hex value.
 */

/** The shipped identity of [spec]: its launcher package with no user overrides. */
@Composable
internal fun rememberShippedTheme(spec: ThemeSpec, environment: ThemeEnvironment): ResolvedTheme =
    remember(spec.id, environment.systemDark, environment.wallpaper) {
        val pkg = ThemeLauncherPackage.forTheme(spec.id)
        ThemeEngine.resolve(
            pkg.toThemeSettings().copy(dynamicWallpaperColors = false),
            environment.copy(profile = null, hasSelectedArtwork = false)
        )
    }

/** Themes whose home composition puts the clock on the leading edge. */
private val LEADING_CLOCK = setOf(ThemeId.ATMOSPHERE, ThemeId.MONOLITH, ThemeId.LUMA_GLASS, ThemeId.LUMA_MONO)

/**
 * A faithful miniature home screen: atmosphere, clock and date, two rows of
 * tiles in the theme's tile geometry, and its dock treatment.
 */
@Composable
internal fun ThemeMiniature(theme: ResolvedTheme, dock: DockStyle, modifier: Modifier = Modifier) {
    val glass = remember(theme) { GlassMaterials.palette(theme, GlassCapabilities.DEFAULT) }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (theme.backdrop.usesArtwork) {
            // No artwork in a thumbnail: stand in with the theme's own surface pair.
            drawRect(Brush.linearGradient(listOf(Color(theme.colors.surface2), Color(theme.colors.background)), Offset.Zero, Offset(w, h)))
        }
        drawAtmosphere(theme, 0.18f)

        val c = theme.colors
        val gutter = w * 0.11f
        val leading = theme.id in LEADING_CLOCK
        val clockW = w * 0.40f
        val clockH = h * 0.075f
        val clockX = if (leading) gutter else (w - clockW) / 2f
        val clockY = h * 0.13f
        val minor = min(w, h)
        drawRoundRect(
            Color(c.homeForeground).copy(alpha = 0.92f),
            Offset(clockX, clockY), Size(clockW, clockH), CornerRadius(clockH / 2f)
        )
        val dateW = w * 0.24f
        drawRoundRect(
            Color(c.homeMuted).copy(alpha = 0.75f),
            Offset(if (leading) gutter else (w - dateW) / 2f, clockY + clockH + h * 0.035f),
            Size(dateW, h * 0.028f), CornerRadius(h * 0.014f)
        )

        // Tiles: the theme's own tile radius ratio, filled from its palette so
        // the identity reads even at thumbnail size.
        val tileFills = listOf(c.accent, c.secondary, c.surface3, c.accentContainer, c.surface2, c.accent, c.surface3, c.secondary)
        val cols = 4
        val tile = w * 0.13f
        val gap = (w - gutter * 2f - tile * cols) / (cols - 1)
        val tileRadius = tile * (theme.iconTileRadiusDp / theme.iconTileDp).coerceIn(0.04f, 0.5f)
        val rows = 2
        for (r in 0 until rows) for (col in 0 until cols) {
            val x = gutter + col * (tile + gap)
            val y = h * 0.40f + r * (tile + h * 0.07f)
            val fill = tileFills[(r * cols + col) % tileFills.size]
            drawRoundRect(Color(ColorMath.opaque(fill)).copy(alpha = if (r == 0) 0.95f else 0.80f), Offset(x, y), Size(tile, tile), CornerRadius(tileRadius))
        }

        drawDock(theme, glass[GlassRole.DOCK], dock, gutter, minor)
    }
}

private fun DrawScope.drawDock(
    theme: ResolvedTheme,
    spec: com.lumaos.pro.launcher.ui.theme.GlassSpec,
    dock: DockStyle,
    gutter: Float,
    minor: Float
) {
    val w = size.width
    val h = size.height
    val dockH = h * 0.17f
    val rest = spec.rest
    when (dock) {
        DockStyle.MINIMAL -> Unit
        DockStyle.BEZEL -> {
            val y = h - dockH
            drawRect(Color(theme.colors.surface1), Offset(0f, y), Size(w, dockH))
            drawLine(Color(theme.colors.outline), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        DockStyle.FLOAT -> {
            val inset = gutter * 0.7f
            val y = h - dockH - h * 0.05f
            val radius = (theme.radius(RadiusRole.DOCK) / 64f * dockH * 1.4f).coerceAtMost(dockH / 2f)
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(rest.fillTop), Color(rest.fillBottom)), startY = y, endY = y + dockH),
                Offset(inset, y), Size(w - inset * 2f, dockH), CornerRadius(radius)
            )
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(rest.edgeTop), Color(rest.edgeBottom)), startY = y, endY = y + dockH),
                Offset(inset, y), Size(w - inset * 2f, dockH), CornerRadius(radius),
                style = Stroke(1f)
            )
        }
    }
    // Four dock icons, always present so MINIMAL still reads as a dock row.
    val y = if (dock == DockStyle.BEZEL) h - dockH else h - dockH - h * 0.05f
    val icon = dockH * 0.52f
    val cell = (w - gutter * 2f) / 4f
    val tileRadius = icon * (theme.iconTileRadiusDp / theme.iconTileDp).coerceIn(0.04f, 0.5f)
    for (i in 0 until 4) {
        drawRoundRect(
            Color(theme.colors.onSurface).copy(alpha = 0.16f),
            Offset(gutter + i * cell + (cell - icon) / 2f, y + (dockH - icon) / 2f),
            Size(icon, icon), CornerRadius(tileRadius)
        )
    }
}

/**
 * Large card for a premium theme: a tall miniature, the theme's name and the
 * line that says what it is. Selection is a calm 2 dp ring, not a glow.
 */
@Composable
internal fun PremiumThemeCard(
    spec: ThemeSpec,
    theme: ResolvedTheme,
    selected: Boolean,
    modifier: Modifier = Modifier,
    miniatureHeight: Dp = 148.dp,
    onClick: () -> Unit
) {
    val pkg = remember(spec.id) { ThemeLauncherPackage.forTheme(spec.id) }
    val shape = RoundedCornerShape(LumaDesignTokens.SP_6.dp)
    val ring by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        tween(LumaDesignTokens.Motion.DUR_2), label = "themeRing"
    )
    val ringWidth by animateDpAsState(if (selected) 2.dp else 1.dp, tween(LumaDesignTokens.Motion.DUR_2), label = "themeRingWidth")
    val description = stringResource(R.string.theme_card_cd, spec.displayName, pkg.subtitle)
    val selectedDescription = stringResource(R.string.theme_selected_cd, spec.displayName)
    Column(
        modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = if (selected) selectedDescription else description
            }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(miniatureHeight)
                .clip(shape)
                .border(ringWidth, ring, shape)
                .padding(if (selected) 3.dp else 0.dp)
                .clip(RoundedCornerShape((LumaDesignTokens.SP_6 - 3f).dp))
        ) {
            ThemeMiniature(theme, pkg.dock, Modifier.fillMaxSize())
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(LumaDesignTokens.SP_3.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✓",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        Spacer(Modifier.height(LumaDesignTokens.SP_3.dp))
        Text(
            spec.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = LumaDesignTokens.SP_1.dp)
        )
        Text(
            pkg.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = LumaDesignTokens.SP_1.dp)
        )
    }
}

/** Horizontally scrolling row of smaller cards for the other identities. */
@Composable
internal fun MoreThemesRow(
    specs: List<ThemeSpec>,
    environment: ThemeEnvironment,
    selectedId: ThemeId?,
    onSelected: (ThemeId) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(LumaDesignTokens.SP_4.dp)
    ) {
        specs.forEach { spec ->
            val theme = rememberShippedTheme(spec, environment)
            PremiumThemeCard(
                spec = spec,
                theme = theme,
                selected = spec.id == selectedId,
                miniatureHeight = 96.dp,
                modifier = Modifier.width(112.dp),
                onClick = { onSelected(spec.id) }
            )
        }
    }
}

/**
 * Curated accent swatches for the active theme. The first swatch is the
 * theme's own accent and maps to "no override", so returning to the designed
 * colour is always one tap. A custom accent from elsewhere (wallpaper,
 * the full swatch wall) is shown at the end so the selection stays visible.
 */
@Composable
internal fun AccentSwatchRow(
    themeId: ThemeId,
    accentOverride: Int?,
    onSelected: (Int?) -> Unit
) {
    val curated = remember(themeId) { ThemePresets.curatedAccents(themeId) }
    val themeAccent = curated.first()
    val custom = accentOverride?.let { ColorMath.opaque(it) }?.takeIf { it !in curated }
    val swatches = curated + listOfNotNull(custom)
    val ring = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(LumaDesignTokens.SP_4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        swatches.forEach { color ->
            val isThemeAccent = color == themeAccent
            val chosen = if (accentOverride == null) isThemeAccent else ColorMath.opaque(accentOverride) == color
            val label = ColorMath.toHex(color).takeLast(6)
            val cd = stringResource(R.string.accent_cd, label)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onSelected(if (isThemeAccent) null else color) }
                    .semantics {
                        role = Role.RadioButton
                        this.selected = chosen
                        contentDescription = cd
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(if (chosen) 2.dp else 0.dp, if (chosen) ring else Color.Transparent, CircleShape)
                        .padding(if (chosen) 4.dp else 0.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                )
            }
        }
    }
}
