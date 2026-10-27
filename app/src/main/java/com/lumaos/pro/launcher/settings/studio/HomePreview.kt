package com.lumaos.pro.launcher.settings.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
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
 * typography, user icons and the shared Liquid Glass recipes. There is no
 * decorative chronometer and no context chip: one clock/date, one search entry
 * and one dock. When the backdrop is the wallpaper, reported wallpaper colours
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
    /** Bundled wallpaper art for the draft theme, when it has one. */
    artwork: ImageBitmap? = null,
    timeText: String,
    dateText: String,
    readout: String,
    modifier: Modifier = Modifier
) {
    val palette = GlassMaterials.palette(
        theme,
        GlassCapabilities(translucency = true, blur = false, depth = true)
    )
    BoxWithConstraints(
        modifier.semantics {
            contentDescription = "Preview of ${theme.displayName} with the ${preset.displayName} layout"
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val c = theme.colors
            val unit = w / 100f
            drawBackdrop(theme, wallpaper, artwork)

            // One refined clock/date focal point.
            val top = h * 0.07f
            val compact = preset.clock == ClockStyle.COMPACT
            val clockPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = c.homeForeground
                typeface = Typefaces.clock(theme.typography)
                letterSpacing = theme.typography.clockTracking
                textAlign = if (compact) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.CENTER
                if (theme.backdrop.usesArtwork) setShadowLayer(unit * 0.6f, 0f, unit * 0.2f, c.homeShadow)
            }
            val readPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = c.homeMuted
                typeface = Typefaces.readout(theme.typography)
                letterSpacing = theme.typography.readoutTracking
                textSize = unit * 3.1f
                textAlign = clockPaint.textAlign
            }
            val dateOnly = if (theme.typography.readoutCaps) dateText.uppercase() else dateText
            val x = if (compact) unit * 6f else w / 2f
            clockPaint.textSize = if (compact) unit * 10f else unit * 21f
            drawContext.canvas.nativeCanvas.drawText(timeText, x, top + unit * if (compact) 9f else 20f, clockPaint)
            drawContext.canvas.nativeCanvas.drawText(dateOnly, x, top + unit * if (compact) 14f else 27f, readPaint)

            // Clean, aligned icon grid.
            val muted = Color(c.homeMuted)
            val columns = 4
            val rows = if (preset.showGrid) min(preset.defaultRows, 4) else 0
            val gridTop = top + unit * if (compact) 26f else 44f
            val gutter = unit * (GlassGeometry.GUTTER_DP / 3.6f)
            val cellW = (w - gutter * 2f) / columns
            val iconSize = min(cellW * 0.58f, unit * 14f)
            var index = 0
            for (r in 0 until rows) for (col in 0 until columns) {
                val img = icons.getOrNull(index++) ?: continue
                val ix = gutter + col * cellW + (cellW - iconSize) / 2f
                val iy = gridTop + r * (iconSize + unit * 9f)
                if (iy + iconSize > h * 0.74f) continue
                drawImageFit(img, ix, iy, iconSize)
                drawRoundRect(
                    muted.copy(alpha = 0.55f),
                    Offset(ix + iconSize * 0.15f, iy + iconSize + unit * 2f),
                    Size(iconSize * 0.7f, unit * 1.2f),
                    CornerRadius(unit)
                )
            }

            // Primary search: translucent fill, directional edge, one reflection.
            val search = palette[GlassRole.SEARCH].rest
            val pillH = unit * 10f
            val pillY = h - unit * 34f
            val searchRadius = GlassGeometry.ROLE_RADIUS_DP.getValue(GlassRole.SEARCH) /
                GlassGeometry.SEARCH_HEIGHT_DP * pillH
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
                style = Stroke(unit * 0.25f)
            )
            drawRoundRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color(search.specular), Color.Transparent)
                ),
                Offset(gutter + searchRadius, pillY + unit * 0.3f),
                Size(w - gutter * 2f - searchRadius * 2f, unit * 0.25f),
                CornerRadius(unit * 0.2f)
            )
            drawCircle(Color(search.muted), unit * 1.6f, Offset(gutter + unit * 3f, pillY + pillH / 2f), style = Stroke(unit * 0.5f))
            drawRoundRect(
                Color(search.muted).copy(alpha = 0.5f),
                Offset(gutter + unit * 8f, pillY + pillH / 2f - unit * 0.6f),
                Size(w * 0.35f, unit * 1.2f), CornerRadius(unit)
            )

            // Floating glass dock.
            val dockSpec = palette[GlassRole.DOCK].rest
            val dockH = unit * 18f
            val dockY = h - unit * 22f
            val inset = gutter
            if (dock != DockStyle.MINIMAL) {
                val dockRadius = GlassGeometry.ROLE_RADIUS_DP.getValue(GlassRole.DOCK) /
                    GlassGeometry.DOCK_HEIGHT_DP * dockH
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
                drawRoundRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(dockSpec.specular), Color.Transparent)
                    ),
                    Offset(inset + dockRadius, dockY + unit * 0.3f),
                    Size(w - inset * 2f - dockRadius * 2f, unit * 0.25f),
                    CornerRadius(unit * 0.2f)
                )
            }
            val dockCell = (w - inset * 2f) / 4f
            val dockIcon = min(dockCell * 0.6f, dockH * 0.62f)
            dockIcons.forEachIndexed { i, img ->
                drawImageFit(img, inset + i * dockCell + (dockCell - dockIcon) / 2f, dockY + (dockH - dockIcon) / 2f, dockIcon)
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

private fun DrawScope.drawBackdrop(theme: ResolvedTheme, wallpaper: WallpaperSeeds?, artwork: ImageBitmap?) {
    val w = size.width
    val h = size.height
    if (theme.backdrop == Backdrop.WALLPAPER) {
        val colors = listOfNotNull(wallpaper?.primary, wallpaper?.secondary, wallpaper?.tertiary)
            // No wallpaper colours reported: stand in with the draft theme's own
            // surface pair rather than inventing a palette.
            .ifEmpty { listOf(theme.colors.surface2, theme.colors.background) }
            .map { Color(it) }
        drawRect(Brush.linearGradient(if (colors.size == 1) colors + colors else colors, Offset.Zero, Offset(w, h)))
    }
    if (theme.backdrop == Backdrop.LUMA && artwork != null) {
        // Centre-crop with the artwork's focal point, exactly as the home screen does.
        val art = WallpaperArt.forTheme(theme.id)
        val focalX = art?.focalX ?: 0.5f
        val focalY = art?.focalY ?: 0.5f
        val iw = artwork.width.toFloat()
        val ih = artwork.height.toFloat()
        val scale = max(w / iw, h / ih)
        val dw = iw * scale
        val dh = ih * scale
        val left = (w - dw) * focalX
        val top = (h - dh) * focalY
        drawImage(
            artwork,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(artwork.width, artwork.height),
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(dw.toInt(), dh.toInt())
        )
    }
    for (layer in AtmospherePlan.layers(theme, 0.18f, true)) when (layer) {
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
    val scrim = GlassBackdrop.scrim(theme)
    drawRect(
        Brush.verticalGradient(
            listOf(Color(scrim.top), Color(scrim.center), Color(scrim.bottom))
        )
    )
}
