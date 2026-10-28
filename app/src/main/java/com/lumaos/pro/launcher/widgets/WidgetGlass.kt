package com.lumaos.pro.launcher.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.RadiusRole
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import com.lumaos.pro.launcher.ui.theme.ThemeColors

/**
 * Pure-Kotlin Liquid Glass palette for `RemoteViews` AppWidgets (Battery & Music).
 *
 * Because Android AppWidgets can be placed on external launcher hosts over an
 * arbitrary bright or dark wallpaper without real-time `RenderEffect` blur, the
 * plate maintains a high-clarity ~94% glass fill tinted by [ResolvedTheme.colors]
 * and guarantees WCAG AA (>= 4.5:1) contrast for both primary and muted text
 * when composited over pure white *and* pure black backgrounds.
 */
data class WidgetGlassColors(
    val fillTop: Int,
    val fillBottom: Int,
    val edgeTop: Int,
    val edgeBottom: Int,
    val specular: Int,
    val primaryText: Int,
    val mutedText: Int,
    val accent: Int
)

object WidgetGlassPalette {

    fun fromTheme(theme: ResolvedTheme): WidgetGlassColors {
        val c = theme.colors
        val accentOpaque = ColorMath.opaque(c.accent)
        val rawTop = ColorMath.lerp(
            ColorMath.opaque(c.surface2),
            accentOpaque,
            if (theme.isDark) 0.08f else 0.05f
        )
        val rawBottom = ColorMath.lerp(
            ColorMath.opaque(c.surface1),
            accentOpaque,
            if (theme.isDark) 0.04f else 0.025f
        )

        // Clamp luminance so a very pale or ultra-deep dynamic surface still yields
        // >= 4.5:1 for muted text even after 94% alpha compositing over WHITE or BLACK.
        val clampedTop = clampPlateLuminance(rawTop, theme.isDark)
        val clampedBottom = clampPlateLuminance(rawBottom, theme.isDark)

        val fillTop = ColorMath.withAlpha(clampedTop, 0.94f)
        val fillBottom = ColorMath.withAlpha(clampedBottom, 0.95f)

        val edgeAnchor = if (theme.isDark) ColorMath.WHITE else ColorMath.BLACK
        val edgeTop = ColorMath.withAlpha(
            ColorMath.lerp(edgeAnchor, accentOpaque, 0.16f),
            if (theme.isDark) 0.34f else 0.18f
        )
        val edgeBottom = ColorMath.withAlpha(
            edgeAnchor,
            if (theme.isDark) 0.10f else 0.08f
        )
        val specular = ColorMath.withAlpha(
            ColorMath.WHITE,
            if (theme.isDark) 0.24f else 0.62f
        )

        val worstCaseBackgrounds = listOf(
            ColorMath.composite(fillTop, ColorMath.WHITE),
            ColorMath.composite(fillTop, ColorMath.BLACK),
            ColorMath.composite(fillBottom, ColorMath.WHITE),
            ColorMath.composite(fillBottom, ColorMath.BLACK)
        )

        val primary = ensureAcrossAll(ColorMath.opaque(c.onSurface), worstCaseBackgrounds, 7.0)
        val muted = ensureAcrossAll(ColorMath.opaque(c.onSurfaceMuted), worstCaseBackgrounds, 4.7)
        val accent = ensureAcrossAll(accentOpaque, worstCaseBackgrounds, 3.2)

        return WidgetGlassColors(
            fillTop = fillTop,
            fillBottom = fillBottom,
            edgeTop = edgeTop,
            edgeBottom = edgeBottom,
            specular = specular,
            primaryText = primary,
            mutedText = muted,
            accent = accent
        )
    }

    private fun clampPlateLuminance(color: Int, isDark: Boolean): Int {
        val lum = ColorMath.luminance(color)
        return when {
            isDark && lum > 0.10 -> ColorMath.lerp(color, ColorMath.BLACK, 0.45f)
            !isDark && lum < 0.72 -> ColorMath.lerp(color, ColorMath.WHITE, 0.45f)
            else -> color
        }
    }

    private fun ensureAcrossAll(fg: Int, backgrounds: List<Int>, minRatio: Double): Int {
        var resolved = fg
        for (bg in backgrounds) {
            resolved = ColorMath.ensureContrast(resolved, bg, minRatio)
        }
        return resolved
    }
}

/**
 * Renders a lightweight, parcel-safe Luma Glass rounded plate bitmap for AppWidgets,
 * cached per resolved theme so repeated metadata or battery updates allocate nothing.
 */
object WidgetGlassRenderer {

    private data class CacheKey(
        val colors: ThemeColors,
        val isDark: Boolean,
        val radiusDp: Float,
        val densityDpi: Int
    )

    @Volatile
    private var cached: Pair<CacheKey, Bitmap>? = null

    fun plateBitmap(context: Context, theme: ResolvedTheme): Bitmap {
        val dm = context.resources.displayMetrics
        val radiusDp = theme.radius(RadiusRole.LARGE)
        val key = CacheKey(theme.colors, theme.isDark, radiusDp, dm.densityDpi)
        cached?.takeIf { it.first == key && !it.second.isRecycled }?.let { return it.second }

        val palette = WidgetGlassPalette.fromTheme(theme)
        val width = 320
        val height = 180
        val radius = (radiusDp.coerceIn(16f, 30f) * 1.35f)
        val stroke = 1.5f

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(stroke, stroke, width - stroke, height - stroke)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    ColorMath.lerp(palette.fillTop, palette.specular, 0.22f),
                    palette.fillTop,
                    palette.fillBottom
                ),
                floatArrayOf(0f, 0.08f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                palette.edgeTop,
                palette.edgeBottom,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        // Subtle inner top specular reflection inside the corner curvature
        val specInset = radius * 0.65f
        val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.1f
            shader = LinearGradient(
                specInset, 0f, width - specInset, 0f,
                intArrayOf(0x00FFFFFF, palette.specular, 0x00FFFFFF),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val specY = stroke + 1.2f
        canvas.drawLine(specInset, specY, width - specInset, specY, specPaint)

        cached = key to bitmap
        return bitmap
    }
}
