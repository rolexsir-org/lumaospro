package com.lumaos.pro.launcher.ui.glass

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Permanently cached 256x256 monochromatic procedural noise texture and
 * background-thread 1.5x parallax-oversized wallpaper blur cache (with disk PNG
 * persistence) for unified [LumaGlassSurface] rendering.
 *
 * Generated once on first use with a fast deterministic SplitMix32 PRNG so
 * zero I/O or per-frame allocation ever occurs.
 */
object LumaGlassNoise {

    @Volatile
    private var cachedBitmap: Bitmap? = null

    @Volatile
    private var cachedShader: BitmapShader? = null

    @Volatile
    private var cachedComposeBrush: ShaderBrush? = null

    fun bitmap(): Bitmap? {
        cachedBitmap?.takeIf { !it.isRecycled }?.let { return it }
        synchronized(this) {
            cachedBitmap?.takeIf { !it.isRecycled }?.let { return it }
            return runCatching {
                val size = LumaTokens.glassNoiseTileSizePx
                val pixels = IntArray(size * size)
                var seed = 0x6D2B79F5
                for (i in pixels.indices) {
                    seed += 0x6D2B79F5
                    var z = seed
                    z = (z xor (z ushr 15)) * (z or 1)
                    z = z xor (z + (z xor (z ushr 7)) * (z or 61))
                    val v = ((z xor (z ushr 14)) ushr 24) and 0xFF
                    // Full-alpha neutral grain; caller Paint/DrawScope applies 0.02f opacity.
                    pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                }
                Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)?.also {
                    cachedBitmap = it
                }
            }.getOrNull()
        }
    }

    fun shader(): BitmapShader? {
        cachedShader?.let {
            if (cachedBitmap?.isRecycled == false) return it
        }
        val bmp = bitmap() ?: return null
        synchronized(this) {
            cachedShader?.let {
                if (cachedBitmap?.isRecycled == false) return it
            }
            return runCatching {
                BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also {
                    cachedShader = it
                }
            }.getOrNull()
        }
    }

    fun composeBrush(): ShaderBrush? {
        cachedComposeBrush?.let {
            if (cachedBitmap?.isRecycled == false) return it
        }
        val s = shader() ?: return null
        synchronized(this) {
            cachedComposeBrush?.let { return it }
            return ShaderBrush(s).also { cachedComposeBrush = it }
        }
    }
}

/**
 * Pre-rendered 1.5x parallax-oversized wallpaper blur cache with disk PNG
 * persistence (`cacheDir/luma_wp_blur_<hash>.png`).
 *
 * Recomputed ONLY when the wallpaper signature (`tag`) changes; restored
 * directly from disk on cold start or after a 30s background memory trim.
 * All blur passes execute strictly off the UI thread.
 */
object LumaWallpaperBlurCache {

    private const val DISK_PREFIX = "luma_wp_blur_"

    @Volatile
    private var blurredWallpaper: Bitmap? = null

    @Volatile
    private var sourceTag: String? = null

    fun current(): Bitmap? = blurredWallpaper?.takeIf { !it.isRecycled }

    fun diskFileForTag(cacheDir: File, tag: String): File {
        val hex = tag.hashCode().toUInt().toString(16)
        return File(cacheDir, "${DISK_PREFIX}${hex}.png")
    }

    /**
     * Computes or restores from [cacheDir] a 1.5x parallax-oversized 3-pass
     * separable box-blurred wallpaper bitmap. Must run off the UI thread.
     */
    fun precompute(
        tag: String,
        source: Bitmap,
        cacheDir: File? = null,
        radius: Int = 12
    ): Bitmap? {
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
        if (tag == sourceTag && blurredWallpaper?.isRecycled == false) {
            return blurredWallpaper
        }
        if (cacheDir != null) {
            val cachedFile = diskFileForTag(cacheDir, tag)
            if (cachedFile.isFile && cachedFile.length() > 0L) {
                val restored = runCatching {
                    BitmapFactory.decodeFile(
                        cachedFile.absolutePath,
                        BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    )
                }.getOrNull()
                if (restored != null && !restored.isRecycled) {
                    blurredWallpaper = restored
                    sourceTag = tag
                    return restored
                }
            }
        }
        return runCatching {
            val readable = if (android.os.Build.VERSION.SDK_INT >= 26 && source.config == Bitmap.Config.HARDWARE) {
                source.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            } else source
            try {
                // 1.5x parallax stage downsampled into compact bilinear-ready buffer (~290KB max).
                val targetW = ((readable.width * LumaTokens.wallpaperBlurOverscan) / 4f)
                    .roundToInt()
                    .coerceIn(72, 270)
                val targetH = ((readable.height * LumaTokens.wallpaperBlurOverscan) / 4f)
                    .roundToInt()
                    .coerceIn(72, 480)
                val scaled = Bitmap.createScaledBitmap(readable, targetW, targetH, true)
                val w = scaled.width
                val h = scaled.height
                val pixels = IntArray(w * h)
                scaled.getPixels(pixels, 0, w, 0, 0, w, h)
                if (scaled !== readable && scaled !== source) scaled.recycle()

                val blurred = blurPixels(pixels, w, h, radius)
                Bitmap.createBitmap(blurred, w, h, Bitmap.Config.ARGB_8888)?.also { out ->
                    blurredWallpaper = out
                    sourceTag = tag
                    if (cacheDir != null) {
                        persistToDisk(cacheDir, tag, out)
                    }
                }
            } finally {
                if (readable !== source) readable.recycle()
            }
        }.getOrNull()
    }

    /** Pure-Kotlin 3-pass separable box blur approximating a Gaussian kernel (JVM-testable). */
    fun blurPixels(sourcePixels: IntArray, width: Int, height: Int, radius: Int = 10): IntArray {
        if (width <= 0 || height <= 0 || sourcePixels.size < width * height) return sourcePixels
        val work = sourcePixels.copyOf(width * height)
        val scratch = IntArray(width * height)
        val r = radius.coerceIn(2, 24)
        repeat(3) {
            boxBlurHorizontal(work, scratch, width, height, r)
            boxBlurVertical(scratch, work, width, height, r)
        }
        return work
    }

    private fun persistToDisk(cacheDir: File, tag: String, bitmap: Bitmap) {
        runCatching {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val target = diskFileForTag(cacheDir, tag)
            val tmp = File(cacheDir, "${target.name}.tmp")
            tmp.outputStream().buffered().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            tmp.renameTo(target)
            cacheDir.listFiles { _, name ->
                name.startsWith(DISK_PREFIX) && name != target.name
            }?.forEach { stale -> runCatching { stale.delete() } }
        }
    }

    /** Releases RAM bitmap (disk cache remains intact for <8ms reload). */
    fun clear() {
        blurredWallpaper = null
        sourceTag = null
    }

    private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val div = radius * 2 + 1
        for (y in 0 until h) {
            val row = y * w
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (i in -radius..radius) {
                val c = src[row + i.coerceIn(0, w - 1)]
                rSum += ColorMath.red(c)
                gSum += ColorMath.green(c)
                bSum += ColorMath.blue(c)
            }
            for (x in 0 until w) {
                dst[row + x] = ColorMath.argb(255, rSum / div, gSum / div, bSum / div)
                val outCol = src[row + max(0, x - radius)]
                val inCol = src[row + min(w - 1, x + radius + 1)]
                rSum += ColorMath.red(inCol) - ColorMath.red(outCol)
                gSum += ColorMath.green(inCol) - ColorMath.green(outCol)
                bSum += ColorMath.blue(inCol) - ColorMath.blue(outCol)
            }
        }
    }

    private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val div = radius * 2 + 1
        for (x in 0 until w) {
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (i in -radius..radius) {
                val c = src[i.coerceIn(0, h - 1) * w + x]
                rSum += ColorMath.red(c)
                gSum += ColorMath.green(c)
                bSum += ColorMath.blue(c)
            }
            for (y in 0 until h) {
                dst[y * w + x] = ColorMath.argb(255, rSum / div, gSum / div, bSum / div)
                val outCol = src[max(0, y - radius) * w + x]
                val inCol = src[min(h - 1, y + radius + 1) * w + x]
                rSum += ColorMath.red(inCol) - ColorMath.red(outCol)
                gSum += ColorMath.green(inCol) - ColorMath.green(outCol)
                bSum += ColorMath.blue(inCol) - ColorMath.blue(outCol)
            }
        }
    }
}
