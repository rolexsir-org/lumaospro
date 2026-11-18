package com.lumaos.pro.launcher.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.lumaos.pro.launcher.ui.theme.ColorMath

/** Small parcel-safe illustrations; all measured values are supplied by the provider. */
internal object WidgetLabArt {
    private inline fun draw(w: Int, h: Int, block: (Canvas, Paint) -> Unit): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { block(Canvas(it), Paint(Paint.ANTI_ALIAS_FLAG)) }

    fun battery(level: Int?, colors: WidgetGlassColors) = draw(144, 144) { canvas, ink ->
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = 6f
        ink.strokeCap = Paint.Cap.ROUND
        val bounds = RectF(10f, 10f, 134f, 134f)
        for (i in 0 until 30) {
            ink.color = if (level != null && (i + 0.5f) * 100 / 30 <= level) colors.accent else colors.edgeTop
            canvas.drawArc(bounds, 140f + i * 9f, 5.4f, false, ink)
        }
    }

    fun partitions(used: Float, colors: WidgetGlassColors) = draw(256, 16) { canvas, ink ->
        val width = 256 * used.coerceIn(0f, 1f)
        ink.color = colors.edgeTop
        canvas.drawRoundRect(0f, 3f, 256f, 13f, 5f, 5f, ink)
        if (width > 0) {
            ink.shader = LinearGradient(0f, 0f, 256f, 0f, ColorMath.lerp(colors.accent, colors.primaryText, 0.35f), colors.accent, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(0f, 3f, width, 13f, 5f, 5f, ink)
            ink.shader = null
        }
        ink.color = ColorMath.opaque(colors.fillBottom)
        for (i in 1..4) canvas.drawRect(i * 51.2f, 3f, i * 51.2f + 1.5f, 13f, ink)
    }

    /** Unknown days stay empty; bars are chronological and today's is accented. */
    fun history(days: List<Long?>, colors: WidgetGlassColors) = draw(168, 56) { canvas, ink ->
        val max = days.filterNotNull().maxOrNull()?.coerceAtLeast(1) ?: 1
        days.forEachIndexed { i, elapsed ->
            if (elapsed == null) return@forEachIndexed
            val x = i * 24f + 4f
            val height = (elapsed.toFloat() / max * 48f).coerceIn(2f, 48f)
            ink.color = if (i == days.lastIndex) colors.accent else colors.edgeTop
            canvas.drawRoundRect(x, 55f - height, x + 16f, 55f, 3f, 3f, ink)
        }
    }

    fun album(colors: WidgetGlassColors) = draw(128, 128) { canvas, ink ->
        ink.shader = LinearGradient(0f, 0f, 128f, 128f, colors.accent,
            ColorMath.opaque(colors.fillBottom), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f, 0f, 128f, 128f, 19f, 19f, ink)
        ink.shader = null
        ink.color = ColorMath.withAlpha(colors.primaryText, 0.35f)
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = 1.35f
        for (r in listOf(18f, 27f, 36f, 45f)) canvas.drawCircle(87f, 79f, r, ink)
    }
}
