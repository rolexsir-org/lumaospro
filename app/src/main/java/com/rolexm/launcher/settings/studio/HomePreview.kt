package com.rolexm.launcher.settings.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import com.rolexm.launcher.launcher.model.ClockStyle
import com.rolexm.launcher.launcher.model.DockStyle
import com.rolexm.launcher.launcher.model.HomePreset
import com.rolexm.launcher.ui.theme.AtmosphereLayer
import com.rolexm.launcher.ui.theme.AtmospherePlan
import com.rolexm.launcher.ui.theme.Backdrop
import com.rolexm.launcher.ui.theme.ColorMath
import com.rolexm.launcher.ui.theme.ResolvedTheme
import com.rolexm.launcher.ui.theme.WallpaperSeeds
import com.rolexm.launcher.launcher.home.Typefaces
import kotlin.math.max
import kotlin.math.min

/**
 * Miniature of the home screen, drawn with the same atmosphere plan, theme
 * roles, typography and rendered icons as the real one. Icons are the user's
 * actual home apps rendered with the draft icon treatment; nothing is mocked.
 * When the backdrop is the wallpaper, ROLEXM cannot read the wallpaper image
 * without a storage permission, so its reported colours stand in for it.
 */
@Composable
fun HomePreview(
    theme: ResolvedTheme,
    preset: HomePreset,
    dock: DockStyle,
    icons: List<ImageBitmap>,
    dockIcons: List<ImageBitmap>,
    wallpaper: WallpaperSeeds?,
    timeText: String,
    dateText: String,
    readout: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.semantics { contentDescription = "Preview of ${theme.displayName} with the ${preset.displayName} layout" }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawBackdrop(theme, wallpaper)
            val c = theme.colors
            val muted = Color(c.homeMuted)
            val unit = w / 100f

            // Chronometer / clock block.
            val top = h * 0.07f
            val clockPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = c.homeForeground
                typeface = Typefaces.clock(theme.typography)
                letterSpacing = theme.typography.clockTracking
                setShadowLayer(unit * 0.8f, 0f, unit * 0.2f, c.homeShadow)
            }
            val readPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = c.homeMuted
                typeface = Typefaces.readout(theme.typography)
                letterSpacing = theme.typography.readoutTracking
                textSize = unit * 3.1f
            }
            when (preset.clock) {
                ClockStyle.DIAL -> {
                    val r = unit * 8f
                    val cx = unit * 6f + r
                    val cy = top + r
                    drawDial(cx, cy, r, theme)
                    clockPaint.textSize = unit * 14f
                    drawContext.canvas.nativeCanvas.drawText(timeText, cx + r + unit * 4f, cy + unit * 2f, clockPaint)
                    readPaint.textSize = unit * 3.2f
                    drawContext.canvas.nativeCanvas.drawText(dateText, cx + r + unit * 4.4f, cy + unit * 7.5f, readPaint)
                    drawContext.canvas.nativeCanvas.drawText(readout, unit * 6f, cy + r + unit * 7f, readPaint)
                }
                ClockStyle.HERO -> {
                    clockPaint.textSize = unit * 21f
                    clockPaint.textAlign = android.graphics.Paint.Align.CENTER
                    readPaint.textAlign = android.graphics.Paint.Align.CENTER
                    drawContext.canvas.nativeCanvas.drawText(timeText, w / 2f, top + unit * 20f, clockPaint)
                    drawContext.canvas.nativeCanvas.drawText(dateText, w / 2f, top + unit * 27f, readPaint)
                    drawContext.canvas.nativeCanvas.drawText(readout, w / 2f, top + unit * 32f, readPaint)
                }
                ClockStyle.COMPACT -> {
                    clockPaint.textSize = unit * 10f
                    drawContext.canvas.nativeCanvas.drawText(timeText, unit * 6f, top + unit * 9f, clockPaint)
                    drawContext.canvas.nativeCanvas.drawText(dateText, unit * 6f, top + unit * 14f, readPaint)
                    drawContext.canvas.nativeCanvas.drawText(readout, unit * 6f, top + unit * 19f, readPaint)
                }
            }

            // Icon grid.
            val columns = 4
            val rows = if (preset.showGrid) min(preset.defaultRows, 4) else 0
            val gridTop = when (preset.clock) {
                ClockStyle.DIAL -> top + unit * 36f
                ClockStyle.HERO -> top + unit * 44f
                ClockStyle.COMPACT -> top + unit * 26f
            }
            val cellW = (w - unit * 8f) / columns
            val iconSize = min(cellW * 0.58f, unit * 14f)
            var index = 0
            for (r in 0 until rows) for (col in 0 until columns) {
                val img = icons.getOrNull(index++) ?: continue
                val x = unit * 4f + col * cellW + (cellW - iconSize) / 2f
                val y = gridTop + r * (iconSize + unit * 9f)
                if (y + iconSize > h * 0.74f) continue
                drawImageFit(img, x, y, iconSize)
                drawRoundRect(muted.copy(alpha = 0.55f), Offset(x + iconSize * 0.15f, y + iconSize + unit * 2f),
                    Size(iconSize * 0.7f, unit * 1.2f), CornerRadius(unit))
            }

            // Search pill.
            val pillH = unit * 9f
            val pillY = h - unit * 34f
            drawRoundRect(
                Color(ColorMath.withAlpha(c.surface1, theme.surfaceAlpha(true) * 0.96f)),
                Offset(unit * 8f, pillY), Size(w - unit * 16f, pillH), CornerRadius(pillH / 2f)
            )
            drawRoundRect(Color(c.outline), Offset(unit * 8f, pillY), Size(w - unit * 16f, pillH), CornerRadius(pillH / 2f), style = Stroke(unit * 0.25f))
            drawCircle(Color(c.onSurfaceMuted), unit * 1.6f, Offset(unit * 14f, pillY + pillH / 2f), style = Stroke(unit * 0.5f))
            drawRoundRect(Color(c.onSurfaceMuted).copy(alpha = 0.5f), Offset(unit * 19f, pillY + pillH / 2f - unit * 0.6f),
                Size(w * 0.35f, unit * 1.2f), CornerRadius(unit))

            // Dock.
            val dockH = unit * 18f
            val dockY = h - unit * 22f
            val inset = when (dock) { DockStyle.BEZEL -> unit * 4f; DockStyle.FLOAT -> unit * 11f; DockStyle.MINIMAL -> unit * 4f }
            if (dock != DockStyle.MINIMAL) {
                val radius = if (dock == DockStyle.FLOAT) dockH / 2f else unit * 6f * theme.cornerScale
                drawRoundRect(Color(ColorMath.withAlpha(c.surface1, theme.surfaceAlpha(true) * 0.94f)),
                    Offset(inset, dockY), Size(w - inset * 2, dockH), CornerRadius(radius))
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = if (theme.isDark) 0.16f else 0.7f), Color(c.outlineStrong).copy(alpha = 0.5f)),
                        startY = dockY, endY = dockY + dockH),
                    Offset(inset, dockY), Size(w - inset * 2, dockH), CornerRadius(radius), style = Stroke(unit * 0.3f)
                )
            }
            val dockCell = (w - inset * 2) / 4f
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

private fun DrawScope.drawBackdrop(theme: ResolvedTheme, wallpaper: WallpaperSeeds?) {
    val w = size.width
    val h = size.height
    if (theme.backdrop == Backdrop.WALLPAPER) {
        val colors = listOfNotNull(wallpaper?.primary, wallpaper?.secondary, wallpaper?.tertiary)
            .ifEmpty { listOf(0xFF2B3038.toInt(), 0xFF15171A.toInt()) }
            .map { Color(it) }
        drawRect(Brush.linearGradient(if (colors.size == 1) colors + colors else colors, Offset.Zero, Offset(w, h)))
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
}

private fun DrawScope.drawDial(cx: Float, cy: Float, r: Float, theme: ResolvedTheme) {
    val c = theme.colors
    val wallpaper = theme.backdrop == Backdrop.WALLPAPER
    val ring = Color(ColorMath.withAlpha(c.homeForeground, 0.42f))
    for (hr in 0 until 24) {
        val major = hr % 6 == 0
        val a = Math.toRadians(hr * 15.0 - 90.0)
        val len = if (major) r * 0.16f else r * 0.08f
        drawLine(ring,
            Offset(cx + ((r - len) * kotlin.math.cos(a)).toFloat(), cy + ((r - len) * kotlin.math.sin(a)).toFloat()),
            Offset(cx + (r * kotlin.math.cos(a)).toFloat(), cy + (r * kotlin.math.sin(a)).toFloat()),
            strokeWidth = if (major) r * 0.04f else r * 0.025f)
    }
    val pr = r * 0.74f
    val day = java.util.Calendar.getInstance().let { (it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE)) / 1440f }
    drawCircle(ring.copy(alpha = 0.15f), pr, Offset(cx, cy), style = Stroke(r * 0.06f))
    val progress = Color(if (wallpaper) c.homeForeground else c.accent)
    drawArc(progress, -90f, day * 360f, false, Offset(cx - pr, cy - pr), Size(pr * 2, pr * 2), style = Stroke(r * 0.06f))
    drawCircle(progress, r * 0.07f, Offset(cx, cy))
}
