package com.lumaos.pro.launcher.ui.theme

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Optical palette and luminance analyzer for bundled, imported, and system wallpapers.
 *
 * The core [extractFromPixels] routine is pure Kotlin over packed ARGB ints so
 * quantization, split-horizon luminance detection, monochrome preservation, and
 * WCAG contrast downstream in [ThemeEngine] are deterministically verifiable on
 * the JVM without an emulator.
 */
object WallpaperPaletteExtractor {

    private const val MAX_SAMPLE_WIDTH = 96
    private const val MAX_SAMPLE_HEIGHT = 192
    private const val HUE_SECTORS = 18
    private const val TWO_PI = Math.PI * 2.0

    val FALLBACK_SEEDS = WallpaperSeeds(
        primary = 0xFF0A0D14.toInt(),
        secondary = 0xFF4E6584.toInt(),
        tertiary = 0xFF1A2230.toInt(),
        supportsDarkText = false,
        accent = 0xFF7C86FF.toInt(),
        topLuminance = 0.04f,
        bottomLuminance = 0.04f
    )

    /**
     * Extracts [WallpaperSeeds] from a downsampled ARGB pixel buffer of [width] x [height].
     *
     * Separates three distinct optical signals:
     *  1. Canvas polarity ([WallpaperSeeds.supportsDarkText]) plus upper-clock and
     *     lower-dock luminance bands so split-horizon wallpapers (bright sunrise sky
     *     over dark alpine peaks) keep legible clock and dock treatment.
     *  2. Polarity-aligned dominant backdrop seed ([WallpaperSeeds.primary]) so
     *     neutral surfaces harmonize with the dominant mass of the wallpaper without
     *     muddying dark glass with bright sky tones.
     *  3. 18-sector OKLCH chromaticity clustering ([WallpaperSeeds.accent],
     *     [WallpaperSeeds.secondary]) so fine specular accents (champagne gold rims,
     *     aurora ribbons, taillight glints) are captured without hallucinating neon
     *     hues on genuinely monochrome graphite or obsidian artwork.
     */
    fun extractFromPixels(pixels: IntArray, width: Int, height: Int): WallpaperSeeds {
        val total = width.toLong() * height.toLong()
        if (width <= 0 || height <= 0 || pixels.isEmpty() || total <= 0L || pixels.size < total) {
            return FALLBACK_SEEDS
        }
        val n = total.toInt()
        val topRows = max(1, (height * 0.30f).roundToInt())
        val bottomStart = (height * 0.70f).roundToInt().coerceIn(0, height - 1)
        val topCount = topRows * width
        val bottomCount = max(1, (height - bottomStart) * width)

        var lumSum = 0.0
        var topSum = 0.0
        var bottomSum = 0.0

        for (i in 0 until n) {
            val lum = ColorMath.luminance(ColorMath.opaque(pixels[i]))
            val y = i / width
            lumSum += lum
            if (y < topRows) topSum += lum
            if (y >= bottomStart) bottomSum += lum
        }

        val avgLum = lumSum / n
        val topLum = (topSum / topCount).toFloat().coerceIn(0f, 1f)
        val bottomLum = (bottomSum / bottomCount).toFloat().coerceIn(0f, 1f)
        // A wallpaper supports dark home text only when the overall canvas is light
        // AND the lower icon/dock region is not deep shadow.
        val supportsDarkText = avgLum >= 0.34 && bottomLum >= 0.15f

        // 16x16x16 sRGB quantized buckets (4096 slots) for fast allocation-light dominance.
        val domCounts = IntArray(4096)
        val domR = LongArray(4096)
        val domG = LongArray(4096)
        val domB = LongArray(4096)

        val allCounts = IntArray(4096)
        val allR = LongArray(4096)
        val allG = LongArray(4096)
        val allB = LongArray(4096)

        val sectorCounts = IntArray(HUE_SECTORS)
        val sectorWeight = DoubleArray(HUE_SECTORS)
        val sectorR = DoubleArray(HUE_SECTORS)
        val sectorG = DoubleArray(HUE_SECTORS)
        val sectorB = DoubleArray(HUE_SECTORS)

        val hiCounts = IntArray(16)
        val hiR = LongArray(16)
        val hiG = LongArray(16)
        val hiB = LongArray(16)

        var polarityMatched = 0

        for (i in 0 until n) {
            val c = ColorMath.opaque(pixels[i])
            val r = ColorMath.red(c)
            val g = ColorMath.green(c)
            val b = ColorMath.blue(c)
            val lum = ColorMath.luminance(c)
            val bucket = ((r ushr 4) shl 8) or ((g ushr 4) shl 4) or (b ushr 4)

            allCounts[bucket]++
            allR[bucket] += r.toLong()
            allG[bucket] += g.toLong()
            allB[bucket] += b.toLong()

            val aligned = if (supportsDarkText) lum >= 0.18 else lum <= 0.26
            if (aligned) {
                domCounts[bucket]++
                domR[bucket] += r.toLong()
                domG[bucket] += g.toLong()
                domB[bucket] += b.toLong()
                polarityMatched++
            }

            val lch = Oklch.fromArgb(c)
            if (lch.l in 0.22..0.94 && lch.c >= 0.022) {
                val normalizedHue = ((lch.h % TWO_PI) + TWO_PI) % TWO_PI
                val sector = ((normalizedHue / TWO_PI) * HUE_SECTORS).toInt().coerceIn(0, HUE_SECTORS - 1)
                val w = lch.c.pow(1.35) * (1.0 - abs(lch.l - 0.68) * 0.50).coerceAtLeast(0.20)
                sectorCounts[sector]++
                sectorWeight[sector] += w
                sectorR[sector] += w * r
                sectorG[sector] += w * g
                sectorB[sector] += w * b
            } else if (lch.l in 0.28..0.92) {
                val hk = (r + g + b) / 48
                val slot = hk.coerceIn(0, 15)
                hiCounts[slot]++
                hiR[slot] += r.toLong()
                hiG[slot] += g.toLong()
                hiB[slot] += b.toLong()
            }
        }

        val useAligned = polarityMatched >= max(1, n / 12)
        val countsRef = if (useAligned) domCounts else allCounts
        val rRef = if (useAligned) domR else allR
        val gRef = if (useAligned) domG else allG
        val bRef = if (useAligned) domB else allB

        var bestDomIdx = 0
        var secondDomIdx = -1
        for (idx in 0 until 4096) {
            val cnt = countsRef[idx]
            if (cnt > countsRef[bestDomIdx]) {
                secondDomIdx = bestDomIdx
                bestDomIdx = idx
            } else if (cnt > 0 && (secondDomIdx < 0 || cnt > countsRef[secondDomIdx])) {
                secondDomIdx = idx
            }
        }

        fun bucketColor(idx: Int): Int {
            val cnt = countsRef[idx]
            if (cnt <= 0) return FALLBACK_SEEDS.primary
            return ColorMath.argb(
                255,
                (rRef[idx] / cnt).toInt(),
                (gRef[idx] / cnt).toInt(),
                (bRef[idx] / cnt).toInt()
            )
        }

        val dominant = bucketColor(bestDomIdx)
        val dominantRunnerUp = if (secondDomIdx >= 0 && countsRef[secondDomIdx] > 0) {
            bucketColor(secondDomIdx)
        } else dominant

        data class SectorCandidate(val score: Double, val color: Int, val hue: Double)
        val minSectorPixels = max(2, n / 4000)
        val candidates = ArrayList<SectorCandidate>(HUE_SECTORS)
        for (s in 0 until HUE_SECTORS) {
            val cnt = sectorCounts[s]
            val wSum = sectorWeight[s]
            if (cnt < minSectorPixels || wSum <= 0.0) continue
            val mr = (sectorR[s] / wSum).roundToInt().coerceIn(0, 255)
            val mg = (sectorG[s] / wSum).roundToInt().coerceIn(0, 255)
            val mb = (sectorB[s] / wSum).roundToInt().coerceIn(0, 255)
            val col = ColorMath.argb(255, mr, mg, mb)
            val lch = Oklch.fromArgb(col)
            val score = cnt.toDouble().pow(0.32) *
                (0.08 + lch.c * 6.0) *
                (1.0 - abs(lch.l - 0.68) * 0.45).coerceAtLeast(0.25)
            candidates += SectorCandidate(score, col, lch.h)
        }
        candidates.sortByDescending { it.score }

        val accent: Int
        val secondary: Int
        val tertiary: Int

        if (candidates.isNotEmpty()) {
            val top = candidates[0]
            accent = top.color
            val secCand = candidates.drop(1).firstOrNull {
                Oklch.hueDistance(it.hue, top.hue) >= 0.55 || colorManhattan(it.color, top.color) > 90
            }
            secondary = secCand?.color ?: dominantRunnerUp
            val terCand = candidates.drop(1).firstOrNull {
                it.color != secondary && colorManhattan(it.color, secondary) > 70
            }
            tertiary = terCand?.color ?: dominant
        } else {
            var bestHi = -1
            var secondHi = -1
            for (slot in 0 until 16) {
                val cnt = hiCounts[slot]
                if (cnt <= 0) continue
                if (bestHi < 0 || cnt > hiCounts[bestHi]) {
                    secondHi = bestHi
                    bestHi = slot
                } else if (secondHi < 0 || cnt > hiCounts[secondHi]) {
                    secondHi = slot
                }
            }
            if (bestHi >= 0) {
                val cnt = hiCounts[bestHi]
                accent = ColorMath.argb(
                    255,
                    (hiR[bestHi] / cnt).toInt(),
                    (hiG[bestHi] / cnt).toInt(),
                    (hiB[bestHi] / cnt).toInt()
                )
                secondary = if (secondHi >= 0 && hiCounts[secondHi] > 0) {
                    val c2 = hiCounts[secondHi]
                    ColorMath.argb(255, (hiR[secondHi] / c2).toInt(), (hiG[secondHi] / c2).toInt(), (hiB[secondHi] / c2).toInt())
                } else dominantRunnerUp
                tertiary = dominant
            } else {
                accent = dominantRunnerUp
                secondary = dominant
                tertiary = dominant
            }
        }

        return WallpaperSeeds(
            primary = dominant,
            secondary = secondary,
            tertiary = tertiary,
            supportsDarkText = supportsDarkText,
            accent = accent,
            topLuminance = topLum,
            bottomLuminance = bottomLum
        )
    }

    /** Estimates an optical saliency focal point `(focalX, focalY)` in `[0.25f..0.75f]`. */
    fun estimateFocalPoint(pixels: IntArray, width: Int, height: Int, dominantArgb: Int): Pair<Float, Float> {
        val total = width.toLong() * height.toLong()
        if (width <= 0 || height <= 0 || pixels.size < total) return 0.5f to 0.5f
        val dr = ColorMath.red(dominantArgb)
        val dg = ColorMath.green(dominantArgb)
        val db = ColorMath.blue(dominantArgb)
        var wSum = 0.0
        var xSum = 0.0
        var ySum = 0.0
        val n = total.toInt()
        for (i in 0 until n) {
            val c = pixels[i]
            val diff = (abs(ColorMath.red(c) - dr) + abs(ColorMath.green(c) - dg) + abs(ColorMath.blue(c) - db)) / 765.0
            val w = diff * kotlin.math.sqrt(diff)
            wSum += w
            xSum += w * (((i % width) + 0.5) / width)
            ySum += w * (((i / width) + 0.5) / height)
        }
        if (wSum <= 1e-4) return 0.5f to 0.5f
        return (xSum / wSum).toFloat().coerceIn(0.25f, 0.75f) to
            (ySum / wSum).toFloat().coerceIn(0.25f, 0.75f)
    }

    /**
     * Subsamples [bitmap] on a bounded stride grid without allocating a scaled
     * Bitmap copy. Safe on hardware bitmaps (copies a tiny software config only
     * if pixel access requires it) and resilient to OOM/recycled states.
     */
    fun extractFromBitmap(bitmap: Bitmap?): WallpaperSeeds? {
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        return try {
            val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            } else {
                bitmap
            }
            try {
                val w = readable.width
                val h = readable.height
                val stepX = max(1, (w + MAX_SAMPLE_WIDTH - 1) / MAX_SAMPLE_WIDTH)
                val stepY = max(1, (h + MAX_SAMPLE_HEIGHT - 1) / MAX_SAMPLE_HEIGHT)
                val sw = (w + stepX - 1) / stepX
                val sh = (h + stepY - 1) / stepY
                val samples = IntArray(sw * sh)
                var outIdx = 0
                var y = stepY / 2
                for (sy in 0 until sh) {
                    val py = y.coerceAtMost(h - 1)
                    var x = stepX / 2
                    for (sx in 0 until sw) {
                        samples[outIdx++] = readable.getPixel(x.coerceAtMost(w - 1), py)
                        x += stepX
                    }
                    y += stepY
                }
                extractFromPixels(samples, sw, sh)
            } finally {
                if (readable !== bitmap) readable.recycle()
            }
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    fun fromPlatformColors(colors: WallpaperColors): Pair<WallpaperSeeds, Boolean> {
        val primary = colors.primaryColor.toArgb()
        val secondary = colors.secondaryColor?.toArgb()
        val tertiary = colors.tertiaryColor?.toArgb()
        val samples = listOfNotNull(primary, secondary, tertiary)
        val weightedLum = (samples.mapIndexed { i, c -> ColorMath.luminance(ColorMath.opaque(c)) * (3 - i) }.sum() /
            samples.indices.sumOf { 3 - it }).toFloat().coerceIn(0f, 1f)
        val supportsDark: Boolean
        val estimated: Boolean
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            supportsDark = (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
            estimated = false
        } else {
            supportsDark = weightedLum > 0.45f
            estimated = true
        }
        // Choose the most chromatic reported color as the accent seed when distinct.
        val bestAccent = samples.maxByOrNull { c ->
            val lch = Oklch.fromArgb(ColorMath.opaque(c))
            lch.c * (1.0 - abs(lch.l - 0.65) * 0.45).coerceAtLeast(0.25)
        } ?: primary
        return WallpaperSeeds(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            supportsDarkText = supportsDark,
            accent = bestAccent,
            topLuminance = weightedLum,
            bottomLuminance = weightedLum
        ) to estimated
    }

    /**
     * Combines optical pixel sampling with Android's [WallpaperColors] when both
     * are available, preferring pixel-sampled split-horizon luminance and chromatic
     * accent clusters while honoring explicit platform dark-text hints on API 31+
     * when pixel sampling was unavailable.
     */
    fun merge(platform: WallpaperSeeds?, sampled: WallpaperSeeds?): WallpaperSeeds? {
        if (sampled == null) return platform
        if (platform == null) return sampled
        return sampled.copy(
            tertiary = sampled.tertiary ?: platform.tertiary ?: platform.secondary
        )
    }

    private fun colorManhattan(a: Int, b: Int): Int =
        abs(ColorMath.red(a) - ColorMath.red(b)) +
            abs(ColorMath.green(a) - ColorMath.green(b)) +
            abs(ColorMath.blue(a) - ColorMath.blue(b))
}
