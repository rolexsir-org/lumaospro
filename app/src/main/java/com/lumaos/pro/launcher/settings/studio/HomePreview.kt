package com.lumaos.pro.launcher.settings.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.lumaos.pro.launcher.launcher.model.ClockStyle
import com.lumaos.pro.launcher.launcher.model.DockStyle
import com.lumaos.pro.launcher.launcher.model.HomePreset
import com.lumaos.pro.launcher.launcher.home.Typefaces
import com.lumaos.pro.launcher.ui.theme.AtmosphereLayer
import com.lumaos.pro.launcher.ui.theme.AtmospherePlan
import com.lumaos.pro.launcher.ui.theme.Backdrop
import com.lumaos.pro.launcher.ui.theme.GlassBackdrop
import com.lumaos.pro.launcher.ui.theme.GlassCapabilities
import com.lumaos.pro.launcher.ui.theme.GlassGeometry
import com.lumaos.pro.launcher.ui.theme.GlassMaterials
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import com.lumaos.pro.launcher.ui.theme.WallpaperArt
import com.lumaos.pro.launcher.ui.theme.WallpaperSeeds
import kotlin.math.max
import kotlin.math.min

/**
 * Miniature of the home screen using the real atmosphere plan, theme roles,
 * typography, user icons and the shared material recipes. Clock, date, readout
 * and grid visibility follow the selected home composition. When the backdrop
 * is the wallpaper, reported wallpaper colours
 * stand in because LumaOS Pro does not request storage permission.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun HomePreview(
    theme: ResolvedTheme,
    preset: HomePreset,
    dock: DockStyle,
    icons: List<ImageBitmap>,
    dockIcons: List<ImageBitmap>,
    wallpaper: WallpaperSeeds?,
    /** Bundled or Studio-selected wallpaper art, when available. */
    artwork: ImageBitmap? = null,
    focalX: Float? = null,
    focalY: Float? = null,
    timeText: String,
    dateText: String,
    readout: String,
    modifier: Modifier = Modifier
) {
    val palette = GlassMaterials.palette(
        theme,
        GlassCapabilities(translucency = true, blur = theme.usesBlur, depth = true)
    )
    Box(
        modifier.semantics {
            contentDescription = "Preview of ${theme.displayName} with the ${preset.displayName} layout"
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val c = theme.colors
            val unit = w / 100f
            drawBackdrop(theme, wallpaper, artwork, focalX, focalY)

            // Theme-voiced clock, date & context readout.
            val isMonolith = theme.id == com.lumaos.pro.launcher.ui.theme.ThemeId.MONOLITH
            val isAtmosphere = theme.id == com.lumaos.pro.launcher.ui.theme.ThemeId.ATMOSPHERE
            val centered = when (theme.id) {
                com.lumaos.pro.launcher.ui.theme.ThemeId.ATMOSPHERE,
                com.lumaos.pro.launcher.ui.theme.ThemeId.MONOLITH -> false
                else -> preset.clock == ClockStyle.HERO
            }
            val top = when {
                isMonolith -> h * 0.045f
                isAtmosphere -> h * 0.06f
                else -> h * 0.08f
            }
            val gutter = unit * (GlassGeometry.GUTTER_DP / 3.6f)
            val clockPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = c.homeForeground
                typeface = Typefaces.clock(theme.typography)
                letterSpacing = theme.typography.clockTracking
                textAlign = if (centered) android.graphics.Paint.Align.CENTER else android.graphics.Paint.Align.LEFT
                if (theme.backdrop.usesArtwork && theme.usesBlur) {
                    setShadowLayer(unit * 0.6f, 0f, unit * 0.2f, c.homeShadow)
                }
            }
            val readPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = c.homeMuted
                typeface = Typefaces.readout(theme.typography)
                letterSpacing = theme.typography.readoutTracking
                textSize = if (isMonolith) unit * 2.8f else unit * 3.1f
                textAlign = clockPaint.textAlign
            }
            val dateFormatted = if (theme.typography.readoutCaps) dateText.uppercase() else dateText
            val x = if (centered) w / 2f else gutter
            val clockTextSize = unit * 18.5f * (theme.clockSp / 64f) *
                (if (preset.clock == ClockStyle.COMPACT) 0.82f else 1f)
            clockPaint.textSize = clockTextSize
            val clockBaseline = top + clockTextSize * 0.88f
            val dateBaseline = clockBaseline + unit * 6.2f
            val detailBaseline = if (preset.showDate) dateBaseline else clockBaseline
            drawContext.canvas.nativeCanvas.drawText(timeText, x, clockBaseline, clockPaint)
            if (preset.showDate) {
                drawContext.canvas.nativeCanvas.drawText(dateFormatted, x, dateBaseline, readPaint)
            }

            if (preset.showContextLine && readout.isNotBlank()) {
                val contextFormatted = if (theme.typography.readoutCaps) readout.uppercase() else readout
                when {
                    isMonolith -> {
                        val ruleY = detailBaseline + unit * 2.8f
                        drawLine(
                            Color(c.outline),
                            Offset(gutter, ruleY),
                            Offset(w - gutter, ruleY),
                            strokeWidth = max(1f, unit * 0.28f)
                        )
                        drawLine(
                            Color(c.accent),
                            Offset(gutter, ruleY),
                            Offset(gutter + unit * 8f, ruleY),
                            strokeWidth = max(1.5f, unit * 0.35f)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            contextFormatted,
                            x,
                            ruleY + unit * 4.4f,
                            readPaint
                        )
                    }
                    isAtmosphere -> {
                        val chipSpec = palette[GlassRole.SETTINGS_CONTROL].rest
                        val textW = readPaint.measureText(contextFormatted)
                        val chipW = textW + unit * 5f
                        val chipH = unit * 5.2f
                        val chipY = detailBaseline + unit * 2.2f
                        drawRoundRect(
                            Brush.verticalGradient(listOf(Color(chipSpec.fillTop), Color(chipSpec.fillBottom))),
                            Offset(gutter, chipY),
                            Size(chipW, chipH),
                            CornerRadius(chipH / 2f)
                        )
                        drawRoundRect(
                            Color(chipSpec.edgeTop),
                            Offset(gutter, chipY),
                            Size(chipW, chipH),
                            CornerRadius(chipH / 2f),
                            style = Stroke(unit * 0.25f)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            contextFormatted,
                            gutter + unit * 2.5f,
                            chipY + chipH * 0.68f,
                            readPaint.apply { color = chipSpec.content }
                        )
                    }
                    theme.id == com.lumaos.pro.launcher.ui.theme.ThemeId.LUMA_GLASS ||
                        theme.id == com.lumaos.pro.launcher.ui.theme.ThemeId.LUMA_MONO -> {
                        val readSpec = palette[GlassRole.SETTINGS_CONTROL]
                        val chip = readSpec.rest
                        val textW = readPaint.measureText(contextFormatted)
                        val chipH = unit * 5.2f
                        val chipY = detailBaseline + unit * 2.2f
                        val radius = (readSpec.radiusDp / GlassGeometry.SEARCH_HEIGHT_DP * chipH)
                            .coerceAtMost(chipH / 2f)
                        drawRoundRect(
                            Color(chip.fillTop), Offset(gutter, chipY),
                            Size(textW + unit * 5f, chipH), CornerRadius(radius)
                        )
                        drawRoundRect(
                            Color(chip.edgeTop), Offset(gutter, chipY),
                            Size(textW + unit * 5f, chipH), CornerRadius(radius),
                            style = Stroke(unit * 0.25f)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            contextFormatted, gutter + unit * 2.5f,
                            chipY + chipH * 0.68f, readPaint.apply { color = chip.content }
                        )
                    }
                    else -> {
                        drawContext.canvas.nativeCanvas.drawText(
                            contextFormatted,
                            x,
                            detailBaseline + unit * 4.6f,
                            readPaint
                        )
                    }
                }
            }

            // Follow the selected row count, including six-row layouts. Fit
            // icons into the space above search rather than drawing under it.
            val muted = Color(c.homeMuted)
            val columns = 4
            val pillTop = h - unit * 34f
            val rows = if (preset.showGrid) preset.defaultRows else 0
            val gridTop = detailBaseline + unit * (if (preset.showContextLine && readout.isNotBlank()) 13f else 8f)
            val cellW = (w - gutter * 2f) / columns
            val gridBottom = pillTop - unit * 3f
            val available = (gridBottom - gridTop).coerceAtLeast(0f)
            val rowPitch = if (rows > 0) min(unit * 22.5f, available / rows) else unit * 22.5f
            val iconSize = min(min(cellW * 0.58f, unit * 14f), (rowPitch - unit * 4.5f).coerceAtLeast(unit * 7f))
            var index = 0
            for (r in 0 until rows) for (col in 0 until columns) {
                val img = icons.getOrNull(index++) ?: continue
                val ix = gutter + col * cellW + (cellW - iconSize) / 2f
                val iy = gridTop + r * rowPitch
                // Icon plus its label bar must clear the search field.
                if (iy + iconSize + unit * 3.2f > gridBottom) continue
                drawImageFit(img, ix, iy, iconSize)
                drawRoundRect(
                    muted.copy(alpha = 0.55f),
                    Offset(ix + iconSize * 0.15f, iy + iconSize + unit * 2f),
                    Size(iconSize * 0.7f, unit * 1.2f),
                    CornerRadius(if (isMonolith) unit * 0.2f else unit)
                )
            }

            // Primary search: respects theme radius (pill on Signature/Atmosphere, sharp 4dp on Monolith).
            val searchSpec = palette[GlassRole.SEARCH]
            val search = searchSpec.rest
            val pillH = unit * 10f
            val pillY = pillTop
            val searchRadius = (searchSpec.radiusDp / GlassGeometry.SEARCH_HEIGHT_DP * pillH).coerceAtMost(pillH / 2f)
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(search.fillTop), Color(search.fillBottom))),
                Offset(gutter, pillY), Size(w - gutter * 2f, pillH), CornerRadius(searchRadius)
            )
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color(search.edgeTop), Color(search.edgeBottom)),
                    startY = pillY, endY = pillY + pillH
                ),
                Offset(gutter, pillY), Size(w - gutter * 2f, pillH), CornerRadius(searchRadius),
                style = Stroke(unit * 0.28f)
            )
            if (theme.usesBlur) {
                drawRoundRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(search.specular), Color.Transparent)
                    ),
                    Offset(gutter + searchRadius, pillY + unit * 0.3f),
                    Size((w - gutter * 2f - searchRadius * 2f).coerceAtLeast(1f), unit * 0.25f),
                    CornerRadius(unit * 0.2f)
                )
            }
            drawCircle(Color(search.muted), unit * 1.6f, Offset(gutter + unit * 3.5f, pillY + pillH / 2f), style = Stroke(unit * 0.5f))
            drawRoundRect(
                Color(search.muted).copy(alpha = 0.5f),
                Offset(gutter + unit * 8f, pillY + pillH / 2f - unit * 0.6f),
                Size(w * 0.35f, unit * 1.2f), CornerRadius(if (isMonolith) unit * 0.2f else unit)
            )

            // Dock: floating translucent capsule (FLOAT), edge-anchored solid stone bar (BEZEL), or bare (MINIMAL).
            val dockRoleSpec = palette[GlassRole.DOCK]
            val dockSpec = dockRoleSpec.rest
            val dockH = unit * 18f
            val dockY = if (dock == DockStyle.BEZEL) h - dockH else h - unit * 22f
            val inset = if (dock == DockStyle.BEZEL) 0f else gutter
            when (dock) {
                DockStyle.MINIMAL -> Unit
                DockStyle.BEZEL -> {
                    drawRect(
                        Color(c.surface1),
                        topLeft = Offset(0f, dockY),
                        size = Size(w, dockH)
                    )
                    drawLine(
                        Color(c.outline),
                        Offset(0f, dockY),
                        Offset(w, dockY),
                        strokeWidth = max(1f, unit * 0.32f)
                    )
                }
                DockStyle.FLOAT -> {
                    val dockRadius = (dockRoleSpec.radiusDp / GlassGeometry.DOCK_HEIGHT_DP * dockH).coerceAtMost(dockH / 2f)
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color(dockSpec.fillTop), Color(dockSpec.fillBottom))),
                        Offset(inset, dockY), Size(w - inset * 2f, dockH), CornerRadius(dockRadius)
                    )
                    drawRoundRect(
                        Brush.verticalGradient(
                            listOf(Color(dockSpec.edgeTop), Color(dockSpec.edgeBottom)),
                            startY = dockY, endY = dockY + dockH
                        ),
                        Offset(inset, dockY), Size(w - inset * 2f, dockH), CornerRadius(dockRadius),
                        style = Stroke(unit * 0.3f)
                    )
                    if (theme.usesBlur) {
                        drawRoundRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color(dockSpec.specular), Color.Transparent)
                            ),
                            Offset(inset + dockRadius, dockY + unit * 0.3f),
                            Size((w - inset * 2f - dockRadius * 2f).coerceAtLeast(1f), unit * 0.25f),
                            CornerRadius(unit * 0.2f)
                        )
                    }
                }
            }
            val dockContentInset = gutter
            val dockCell = (w - dockContentInset * 2f) / 4f
            val dockIcon = min(dockCell * 0.6f, dockH * 0.62f)
            dockIcons.forEachIndexed { i, img ->
                drawImageFit(
                    img,
                    dockContentInset + i * dockCell + (dockCell - dockIcon) / 2f,
                    dockY + (dockH - dockIcon) / 2f,
                    dockIcon
                )
            }
        }
    }
}

private fun DrawScope.drawImageFit(img: ImageBitmap, x: Float, y: Float, size: Float) {
    drawImage(
        img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(x.toInt(), y.toInt()),
        dstSize = IntSize(size.toInt(), size.toInt())
    )
}

private fun DrawScope.drawBackdrop(
    theme: ResolvedTheme,
    wallpaper: WallpaperSeeds?,
    artwork: ImageBitmap?,
    focalX: Float?,
    focalY: Float?
) {
    val w = size.width
    val h = size.height
    if (artwork != null && (theme.backdrop == Backdrop.LUMA || theme.backdrop == Backdrop.WALLPAPER)) {
        // Centre-crop with the artwork's focal point, matching WallpaperArtView.
        val art = WallpaperArt.forTheme(theme.id)
        val fx = (focalX ?: art?.focalX ?: 0.5f).coerceIn(0f, 1f)
        val fy = (focalY ?: art?.focalY ?: 0.5f).coerceIn(0f, 1f)
        val iw = artwork.width.toFloat()
        val ih = artwork.height.toFloat()
        val scale = max(w / iw, h / ih)
        val dw = iw * scale
        val dh = ih * scale
        val left = (w - dw) * fx
        val top = (h - dh) * fy
        drawImage(
            artwork,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(artwork.width, artwork.height),
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(dw.toInt(), dh.toInt())
        )
    } else if (theme.backdrop == Backdrop.WALLPAPER) {
        val colors = listOfNotNull(
            wallpaper?.primary,
            wallpaper?.accent,
            wallpaper?.secondary,
            wallpaper?.tertiary
        )
            .distinct()
            // No wallpaper colours reported: stand in with the draft theme's own
            // surface pair rather than inventing a palette.
            .ifEmpty { listOf(theme.colors.surface2, theme.colors.background) }
            .map { Color(it) }
        drawRect(Brush.linearGradient(if (colors.size == 1) colors + colors else colors, Offset.Zero, Offset(w, h)))
    }
    drawAtmosphere(theme, 0.18f)
    val scrim = GlassBackdrop.scrim(theme)
    drawRect(
        Brush.verticalGradient(
            listOf(Color(scrim.top), Color(scrim.center), Color(scrim.bottom))
        )
    )
}

/**
 * Paints [AtmospherePlan] for [theme] - the exact layers the home screen draws -
 * so Studio previews and theme cards can never disagree with the launcher.
 */
internal fun DrawScope.drawAtmosphere(theme: ResolvedTheme, phase: Float) {
    val w = size.width
    val h = size.height
    for (layer in AtmospherePlan.layers(theme, phase, true)) when (layer) {
        is AtmosphereLayer.Solid -> drawRect(Color(layer.color))
        is AtmosphereLayer.Glow -> {
            val r = layer.radius * max(w, h)
            val center = Offset(layer.cx * w, layer.cy * h)
            scale(1f, layer.scaleY, pivot = center) {
                drawCircle(Brush.radialGradient(listOf(Color(layer.color), Color(layer.color).copy(alpha = 0f)), center, r), r, center)
            }
        }
        is AtmosphereLayer.VerticalFade -> drawRect(
            Brush.verticalGradient(listOf(Color(layer.color0), Color(layer.color1)), startY = layer.y0 * h, endY = layer.y1 * h),
            topLeft = Offset(0f, layer.y0 * h), size = Size(w, (layer.y1 - layer.y0) * h)
        )
        is AtmosphereLayer.Lines -> {
            val s = layer.segments
            for (i in s.indices step 4) {
                drawLine(Color(layer.color), Offset(s[i] * w, s[i + 1] * h), Offset(s[i + 2] * w, s[i + 3] * h), strokeWidth = 1f)
            }
        }
    }
}
