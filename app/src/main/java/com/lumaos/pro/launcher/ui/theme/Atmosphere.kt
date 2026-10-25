package com.lumaos.pro.launcher.ui.theme

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Backdrop composition as plain data.
 *
 * The home screen (Canvas) and the Studio preview (Compose) both draw from the
 * same [AtmospherePlan], so what the preview shows is exactly what the home
 * screen paints. Coordinates are normalised to the drawing area (0..1).
 *
 * Every layer has a job: legibility gradients sit behind the clock and dock;
 * glows give the surface hierarchy a light source. Nothing here is random.
 */
sealed interface AtmosphereLayer {
    data class Solid(val color: Int) : AtmosphereLayer

    /** Radial falloff from [color] to transparent; [scaleY] < 1 flattens it into an ellipse. */
    data class Glow(
        val cx: Float,
        val cy: Float,
        /** Radius as a fraction of the larger side. */
        val radius: Float,
        val color: Int,
        val scaleY: Float = 1f
    ) : AtmosphereLayer

    /** Vertical gradient between two normalised y positions. */
    data class VerticalFade(
        val y0: Float,
        val y1: Float,
        val color0: Int,
        val color1: Int
    ) : AtmosphereLayer

    /** Hairlines; [segments] holds x0,y0,x1,y1 quadruples. */
    data class Lines(val segments: FloatArray, val color: Int, val strokeDp: Float) : AtmosphereLayer {
        override fun equals(other: Any?): Boolean =
            other is Lines && other.color == color && other.strokeDp == strokeDp && other.segments.contentEquals(segments)
        override fun hashCode(): Int = segments.contentHashCode() * 31 + color
    }
}

object AtmospherePlan {

    /**
     * @param phase   0..1 position in the slow ambient cycle; constant when
     *                motion is off, so the composition is still complete.
     * @param detail  whether fine detail (grid hairlines) is drawn; dropped
     *                only at the lowest effects level.
     */
    fun layers(theme: ResolvedTheme, phase: Float, detail: Boolean): List<AtmosphereLayer> {
        val c = theme.colors
        val out = ArrayList<AtmosphereLayer>(8)
        val wallpaper = theme.backdrop == Backdrop.WALLPAPER

        if (!wallpaper) out += AtmosphereLayer.Solid(c.background)

        if (wallpaper) {
            // Legibility only: darken (or lighten) behind the clock and dock.
            // The scrim takes the same side as the text shadow: dark behind
            // white text, light behind dark text.
            val darkScrim = ColorMath.isDark(ColorMath.opaque(c.homeShadow))
            val base = if (darkScrim) ColorMath.BLACK else ColorMath.WHITE
            out += AtmosphereLayer.VerticalFade(0f, 0.34f, ColorMath.withAlpha(base, 0.30f), ColorMath.withAlpha(base, 0f))
            out += AtmosphereLayer.VerticalFade(0.68f, 1f, ColorMath.withAlpha(base, 0f), ColorMath.withAlpha(base, 0.34f))
            if (theme.atmosphere == Atmosphere.HORIZON) {
                out += AtmosphereLayer.Glow(0.5f, 1.08f, 0.62f, ColorMath.withAlpha(c.accent, 0.12f), 0.55f)
            }
            if (theme.atmosphere == Atmosphere.TINT) {
                out += AtmosphereLayer.Glow(0.12f, 0.02f, 0.55f, ColorMath.withAlpha(c.accent, 0.12f), 0.7f)
            }
            return out
        }

        val t = (phase * 2 * PI).toFloat()
        when (theme.atmosphere) {
            Atmosphere.NONE -> Unit
            Atmosphere.HORIZON -> {
                out += AtmosphereLayer.Glow(0.5f, -0.12f, 0.95f, ColorMath.withAlpha(ColorMath.WHITE, 0.05f), 0.7f)
                out += AtmosphereLayer.Glow(0.5f, 1.10f, 0.78f, ColorMath.withAlpha(c.accent, 0.16f), 0.5f)
                out += AtmosphereLayer.Glow(0.88f + 0.02f * sin(t), 0.30f, 0.35f, ColorMath.withAlpha(c.secondary, 0.05f))
            }
            Atmosphere.AURORA -> {
                val mix = ColorMath.lerp(c.accent, c.secondary, 0.5f)
                out += AtmosphereLayer.Glow(0.26f + 0.06f * sin(t), 0.26f + 0.025f * cos(t), 0.72f,
                    ColorMath.withAlpha(c.accent, 0.14f), 0.42f)
                out += AtmosphereLayer.Glow(0.78f - 0.05f * sin(t + 1.9f), 0.36f + 0.018f * sin(t * 2), 0.62f,
                    ColorMath.withAlpha(c.secondary, 0.10f), 0.48f)
                out += AtmosphereLayer.Glow(0.50f + 0.035f * cos(t + 0.7f), 0.56f + 0.03f * sin(t + 2.4f), 0.85f,
                    ColorMath.withAlpha(mix, 0.065f), 0.32f)
                out += AtmosphereLayer.VerticalFade(0.62f, 1f, ColorMath.withAlpha(c.background, 0f),
                    ColorMath.withAlpha(c.background, 0.72f))
            }
            Atmosphere.GRID -> {
                val horizon = 0.74f
                out += AtmosphereLayer.Glow(0.5f, horizon, 0.58f, ColorMath.withAlpha(c.accent, 0.10f), 0.32f)
                out += AtmosphereLayer.Glow(0.86f, 0.10f, 0.36f, ColorMath.withAlpha(c.secondary, 0.055f))
                if (detail) {
                    out += AtmosphereLayer.Lines(gridSegments(horizon, phase), ColorMath.withAlpha(c.accent, 0.07f), 1f)
                    out += AtmosphereLayer.Lines(floatArrayOf(0f, horizon, 1f, horizon),
                        ColorMath.withAlpha(c.accent, 0.16f), 1f)
                }
                out += AtmosphereLayer.VerticalFade(0f, 0.30f, ColorMath.withAlpha(c.background, 0.55f),
                    ColorMath.withAlpha(c.background, 0f))
            }
            Atmosphere.HALO -> {
                // A soft daylight lift rather than a white bloom over the whole surface.
                out += AtmosphereLayer.Glow(0.12f, 0.02f, 0.95f, ColorMath.withAlpha(c.secondary, 0.10f), 0.9f)
                out += AtmosphereLayer.Glow(0.96f, 0.98f, 0.70f, ColorMath.withAlpha(c.accent, 0.045f), 0.8f)
                out += AtmosphereLayer.Glow(0.90f + 0.015f * sin(t), 0.18f, 0.40f, ColorMath.withAlpha(c.secondary, 0.035f))
            }
            Atmosphere.TINT -> {
                out += AtmosphereLayer.Glow(0.15f, 0.05f, 0.80f, ColorMath.withAlpha(c.accent, 0.18f), 0.7f)
                out += AtmosphereLayer.Glow(0.90f, 0.85f, 0.70f, ColorMath.withAlpha(c.secondary, 0.12f), 0.7f)
            }
        }
        return out
    }

    /** Perspective floor grid below [horizon]; [phase] scrolls the cross-lines toward the viewer. */
    internal fun gridSegments(horizon: Float, phase: Float): FloatArray {
        val segs = ArrayList<Float>(80)
        val vanishX = 0.5f
        // Rays from the vanishing point to evenly spaced points below the screen.
        for (i in -6..6) {
            val bottomX = vanishX + i * 0.18f
            segs += listOf(vanishX, horizon, bottomX, 1.0f + 0.02f)
        }
        // Cross-lines with quadratic perspective spacing.
        val rows = 7
        for (k in 0 until rows) {
            val f = ((k + phase.coerceIn(0f, 1f)) / rows)
            val y = horizon + (1f - horizon) * f.pow(2)
            if (y <= horizon + 0.004f) continue
            segs += listOf(0f, y, 1f, y)
        }
        return segs.toFloatArray()
    }
}

/**
 * Icon mask outlines as normalised polygons (0..1), shared by the Android icon
 * renderer and the Compose preview. [IconShape.SYSTEM] has no polygon: the
 * renderer uses the device's own adaptive-icon mask instead.
 */
object IconShapes {

    fun outline(shape: IconShape, points: Int = 72): FloatArray? = when (shape) {
        IconShape.SYSTEM -> null
        IconShape.CIRCLE -> superellipse(2.0, points)
        IconShape.SQUIRCLE -> superellipse(4.4, points)
        IconShape.ROUNDED -> roundedRect(floatArrayOf(0.24f, 0.24f, 0.24f, 0.24f), points / 4)
        IconShape.TEARDROP -> roundedRect(floatArrayOf(0.5f, 0.5f, 0.14f, 0.5f), points / 4)
        IconShape.OCTAGON -> octagon(0.24f)
    }

    /** |x|^n + |y|^n = 1, mapped to 0..1. */
    private fun superellipse(n: Double, points: Int): FloatArray {
        val out = FloatArray(points * 2)
        for (i in 0 until points) {
            val t = 2 * PI * i / points
            val ct = cos(t)
            val st = sin(t)
            val x = sign(ct) * abs(ct).pow(2.0 / n)
            val y = sign(st) * abs(st).pow(2.0 / n)
            out[2 * i] = (0.5 + 0.5 * x).toFloat()
            out[2 * i + 1] = (0.5 + 0.5 * y).toFloat()
        }
        return out
    }

    /** Radii order: top-left, top-right, bottom-right, bottom-left (fractions of size). */
    private fun roundedRect(radii: FloatArray, perCorner: Int): FloatArray {
        val pts = ArrayList<Float>()
        val n = perCorner.coerceAtLeast(4)
        // Corner centres and start angles, clockwise from top-left.
        val corners = listOf(
            Triple(radii[0], radii[0], PI),            // TL: centre (r, r), arc PI..1.5PI
            Triple(1f - radii[1], radii[1], 1.5 * PI), // TR
            Triple(1f - radii[2], 1f - radii[2], 0.0), // BR
            Triple(radii[3], 1f - radii[3], 0.5 * PI)  // BL
        )
        corners.forEachIndexed { index, (cx, cy, start) ->
            val r = radii[index]
            for (i in 0..n) {
                val a = start + (PI / 2) * i / n
                pts += (cx + r * cos(a)).toFloat()
                pts += (cy + r * sin(a)).toFloat()
            }
        }
        return pts.toFloatArray()
    }

    private fun octagon(cut: Float): FloatArray = floatArrayOf(
        cut, 0f, 1f - cut, 0f, 1f, cut, 1f, 1f - cut,
        1f - cut, 1f, cut, 1f, 0f, 1f - cut, 0f, cut
    )
}
