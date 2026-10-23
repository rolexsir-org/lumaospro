package com.rolexm.launcher.ui.theme

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Colour arithmetic for the theme engine.
 *
 * Pure Kotlin on packed ARGB ints (the same representation Android uses), so
 * every contrast guarantee the engine makes is verified by JVM unit tests
 * rather than by eye.
 *
 * Two colour models are used:
 *  - WCAG relative luminance / contrast ratio, because that is the published
 *    legibility standard ROLEXM promises to meet;
 *  - OKLab / OKLCH (Björn Ottosson, 2020), a perceptually uniform space, for
 *    generating tones from a seed colour. Changing L in OKLCH changes perceived
 *    lightness without shifting hue, which HSL cannot do.
 */
object ColorMath {

    fun alpha(c: Int): Int = (c ushr 24) and 0xFF
    fun red(c: Int): Int = (c shr 16) and 0xFF
    fun green(c: Int): Int = (c shr 8) and 0xFF
    fun blue(c: Int): Int = c and 0xFF

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Same colour with alpha replaced by [fraction] (0..1). */
    fun withAlpha(c: Int, fraction: Float): Int =
        (c and 0x00FFFFFF) or ((fraction.coerceIn(0f, 1f) * 255f).roundToInt() shl 24)

    fun opaque(c: Int): Int = c or (0xFF shl 24)

    // ------------------------------------------------------------ luminance

    fun srgbToLinear(channel: Int): Double {
        val v = channel / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    fun linearToSrgb(v: Double): Int {
        val c = v.coerceIn(0.0, 1.0)
        val s = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1.0 / 2.4) - 0.055
        return (s * 255.0).roundToInt().coerceIn(0, 255)
    }

    /** WCAG 2.x relative luminance of the opaque colour, 0 (black) .. 1 (white). */
    fun luminance(c: Int): Double =
        0.2126 * srgbToLinear(red(c)) + 0.7152 * srgbToLinear(green(c)) + 0.0722 * srgbToLinear(blue(c))

    /** WCAG contrast ratio between two opaque colours, 1..21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * The highest contrast any colour can reach against [bg] (white or black).
     * Never below ~4.58:1, so WCAG AA text is always achievable; 7:1 is not
     * achievable on mid-tone backgrounds.
     */
    fun maxContrast(bg: Int): Double = maxOf(contrast(WHITE, opaque(bg)), contrast(BLACK, opaque(bg)))

    /** Alpha-composites [fg] over an opaque [bg]. */
    fun composite(fg: Int, bg: Int): Int {
        val a = alpha(fg) / 255.0
        if (a >= 1.0) return fg
        fun mix(f: Int, b: Int) = (f * a + b * (1 - a)).roundToInt()
        return argb(255, mix(red(fg), red(bg)), mix(green(fg), green(bg)), mix(blue(fg), blue(bg)))
    }

    /** Per-channel interpolation including alpha; used for animated theme changes. */
    fun lerp(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun ch(a: Int, b: Int) = (a + (b - a) * f).roundToInt()
        return argb(
            ch(alpha(from), alpha(to)), ch(red(from), red(to)),
            ch(green(from), green(to)), ch(blue(from), blue(to))
        )
    }

    /** A colour is treated as "dark" when white text on it beats black text. */
    fun isDark(c: Int): Boolean = contrast(c, WHITE) >= contrast(c, BLACK)

    /**
     * Returns [fg] adjusted (only in lightness, hue preserved) until it reaches
     * [minRatio] against [bg]. If even pure black/white cannot reach it, the
     * better of the two is returned. Alpha of [fg] is preserved.
     */
    fun ensureContrast(fg: Int, bg: Int, minRatio: Double): Int {
        val solidBg = opaque(bg)
        val solidFg = opaque(fg)
        if (contrast(solidFg, solidBg) >= minRatio) return fg
        val towardsLight = luminance(solidBg) < 0.18
        val lch = Oklch.fromArgb(solidFg)
        var lo = lch.l
        var hi = if (towardsLight) 1.0 else 0.0
        var best = if (towardsLight) WHITE else BLACK
        if (contrast(best, solidBg) < minRatio) {
            // Unreachable: return whichever extreme is stronger.
            val alt = if (towardsLight) BLACK else WHITE
            best = if (contrast(alt, solidBg) > contrast(best, solidBg)) alt else best
            return (best and 0x00FFFFFF) or (fg and 0xFF000000.toInt())
        }
        // Binary search the smallest lightness change that satisfies the ratio.
        repeat(24) {
            val mid = (lo + hi) / 2
            val candidate = Oklch(mid, lch.c, lch.h).toArgb()
            if (contrast(candidate, solidBg) >= minRatio) {
                best = candidate
                hi = mid
            } else {
                lo = mid
            }
        }
        return (best and 0x00FFFFFF) or (fg and 0xFF000000.toInt())
    }

    fun toHex(c: Int): String = String.format("#%08X", c)

    fun parseHex(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().removePrefix("#")
        val v = s.toLongOrNull(16) ?: return null
        return when (s.length) {
            6 -> (0xFF000000 or v).toInt()
            8 -> v.toInt()
            else -> null
        }
    }

    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
}

/**
 * OKLCH colour: L (0..1 lightness), C (chroma, ~0..0.37 in sRGB), h (radians).
 */
data class Oklch(val l: Double, val c: Double, val h: Double) {

    fun toArgb(alpha: Int = 255): Int {
        // Reduce chroma until the colour fits sRGB, keeping L and h exact.
        var chroma = c
        var rgb = toLinearRgb(l, chroma, h)
        if (!inGamut(rgb)) {
            var lo = 0.0
            var hi = chroma
            repeat(20) {
                val mid = (lo + hi) / 2
                if (inGamut(toLinearRgb(l, mid, h))) lo = mid else hi = mid
            }
            chroma = lo
            rgb = toLinearRgb(l, chroma, h)
        }
        return ColorMath.argb(
            alpha,
            ColorMath.linearToSrgb(rgb[0]),
            ColorMath.linearToSrgb(rgb[1]),
            ColorMath.linearToSrgb(rgb[2])
        )
    }

    fun withL(newL: Double) = copy(l = newL.coerceIn(0.0, 1.0))
    fun withC(newC: Double) = copy(c = newC.coerceAtLeast(0.0))

    companion object {
        fun fromArgb(argb: Int): Oklch {
            val r = ColorMath.srgbToLinear(ColorMath.red(argb))
            val g = ColorMath.srgbToLinear(ColorMath.green(argb))
            val b = ColorMath.srgbToLinear(ColorMath.blue(argb))
            val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
            val okL = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
            val okA = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
            val okB = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
            return Oklch(okL, hypot(okA, okB), atan2(okB, okA))
        }

        private fun toLinearRgb(l: Double, c: Double, h: Double): DoubleArray {
            val a = c * cos(h)
            val b = c * sin(h)
            val l1 = l + 0.3963377774 * a + 0.2158037573 * b
            val m1 = l - 0.1055613458 * a - 0.0638541728 * b
            val s1 = l - 0.0894841775 * a - 1.2914855480 * b
            val l3 = l1 * l1 * l1
            val m3 = m1 * m1 * m1
            val s3 = s1 * s1 * s1
            return doubleArrayOf(
                4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
                -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
                -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3
            )
        }

        private fun inGamut(rgb: DoubleArray): Boolean =
            rgb.all { it >= -EPS && it <= 1.0 + EPS }

        private const val EPS = 1e-4

        /** Angular distance between hues, radians, 0..pi. */
        fun hueDistance(a: Double, b: Double): Double {
            var d = abs(a - b) % (2 * Math.PI)
            if (d > Math.PI) d = 2 * Math.PI - d
            return d
        }
    }
}
