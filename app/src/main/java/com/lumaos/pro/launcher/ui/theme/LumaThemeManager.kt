package com.lumaos.pro.launcher.ui.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import com.lumaos.pro.launcher.core.prefs.LumaPrefs
import com.lumaos.pro.launcher.ui.glass.LumaWallpaperBlurCache
import com.lumaos.pro.launcher.wallpaper.WallpaperCatalog
import com.lumaos.pro.launcher.wallpaper.WallpaperLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Observable optical wallpaper palette extracted by [LumaThemeManager].
 *
 * Drives unified glass tinting, polarity (`< 0.40f` -> dark glass, `>= 0.40f` -> light glass),
 * vibrant accents, subtle text warmth, and O(1) per-region background luminance adaptation.
 */
data class LumaWallpaperPalette(
    val averageLuminance: Float,
    val dominantColor: Int,
    val accentColor: Int,
    val warmthColor: Int,
    val warmth: Float,
    val topLuminance: Float,
    val bottomLuminance: Float,
    val gridCols: Int = DEFAULT_GRID_COLS,
    val gridRows: Int = DEFAULT_GRID_ROWS,
    val luminanceGrid: FloatArray = FloatArray(DEFAULT_GRID_COLS * DEFAULT_GRID_ROWS) { averageLuminance }
) {
    /** `< 0.4f` -> dark glass mode; `>= 0.4f` -> light glass mode. */
    val isDarkGlass: Boolean
        get() = averageLuminance < LumaTokens.darkModeLuminanceThreshold

    /**
     * Samples the cached 1/8-downsampled wallpaper luminance behind a normalized
     * screen region (`0f..1f` coordinates). Runs in O(cells) with zero allocations.
     */
    fun sampleRegionLuminance(
        leftNorm: Float,
        topNorm: Float,
        rightNorm: Float,
        bottomNorm: Float
    ): Float {
        if (luminanceGrid.isEmpty() || gridCols <= 0 || gridRows <= 0) return averageLuminance
        val c0 = (leftNorm.coerceIn(0f, 1f) * (gridCols - 1)).toInt().coerceIn(0, gridCols - 1)
        val c1 = (rightNorm.coerceIn(0f, 1f) * (gridCols - 1)).roundToInt().coerceIn(c0, gridCols - 1)
        val r0 = (topNorm.coerceIn(0f, 1f) * (gridRows - 1)).toInt().coerceIn(0, gridRows - 1)
        val r1 = (bottomNorm.coerceIn(0f, 1f) * (gridRows - 1)).roundToInt().coerceIn(r0, gridRows - 1)
        var sum = 0f
        var count = 0
        for (r in r0..r1) {
            val rowOffset = r * gridCols
            for (c in c0..c1) {
                val idx = rowOffset + c
                if (idx in luminanceGrid.indices) {
                    sum += luminanceGrid[idx]
                    count++
                }
            }
        }
        return if (count > 0) (sum / count).coerceIn(0f, 1f) else averageLuminance
    }

    /**
     * Computes final glass fill opacity from [LumaTokens] base alpha, Z-[depth]
     * variation (within ±10%), and background [regionLuminance] adaptation
     * (+8% over bright >0.7 regions, -5% over deep dark <0.2 regions).
     */
    fun adaptiveGlassAlpha(
        isCard: Boolean = false,
        depth: Float = LumaTokens.depthHome,
        regionLuminance: Float? = null
    ): Float {
        val base = when {
            isDarkGlass && isCard -> LumaTokens.glassDarkCardAlpha
            isDarkGlass -> LumaTokens.glassDarkBgAlpha
            isCard -> LumaTokens.glassLightCardAlpha
            else -> LumaTokens.glassLightBgAlpha
        }
        // Depth variation: higher layers are slightly more opaque (+2.5% per step above Home, capped ±10% of base).
        val depthDelta = ((depth - LumaTokens.depthHome) * 0.022f).coerceIn(-base * 0.10f, base * 0.10f)
        val lum = regionLuminance ?: averageLuminance
        val lumDelta = when {
            lum > 0.70f -> 0.08f
            lum > 0.55f -> ((lum - 0.55f) / 0.15f) * 0.08f
            lum < 0.20f -> -0.05f
            lum < 0.30f -> -((0.30f - lum) / 0.10f) * 0.05f
            else -> 0f
        }
        return (base + depthDelta + lumDelta).coerceIn(0.14f, 0.88f)
    }

    /** Base RGB tint with 6.5% dominant wallpaper saturation blend (no alpha applied). */
    fun surfaceBaseRgb(): Int {
        val neutral = if (isDarkGlass) 0xFF0E121B.toInt() else 0xFFF5F7FB.toInt()
        return ColorMath.lerp(neutral, ColorMath.opaque(dominantColor), 0.065f)
    }

    /** Primary/secondary text color warmed subtly by [warmthColor] while preserving crisp legibility. */
    fun textColor(alpha: Float = 1f): Int {
        val anchor = if (isDarkGlass) 0xFFFFFFFF.toInt() else 0xFF10131A.toInt()
        val shift = (kotlin.math.abs(warmth) * 0.04f).coerceIn(0f, 0.04f)
        val tinted = ColorMath.lerp(anchor, ColorMath.opaque(warmthColor), shift)
        return ColorMath.withAlpha(tinted, alpha.coerceIn(0f, 1f))
    }

    fun encode(): String {
        val gridHex = buildString(luminanceGrid.size * 2) {
            for (v in luminanceGrid) {
                val b = (v.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
                append(HEX[b ushr 4])
                append(HEX[b and 0x0F])
            }
        }
        return String.format(
            Locale.ROOT,
            "v1|%d|%d|%d|%d|%.4f|%.4f|%.4f|%.4f|%d|%d|%s",
            dominantColor,
            accentColor,
            warmthColor,
            if (isDarkGlass) 1 else 0,
            averageLuminance,
            warmth,
            topLuminance,
            bottomLuminance,
            gridCols,
            gridRows,
            gridHex
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LumaWallpaperPalette) return false
        return averageLuminance == other.averageLuminance &&
            dominantColor == other.dominantColor &&
            accentColor == other.accentColor &&
            warmthColor == other.warmthColor &&
            warmth == other.warmth &&
            topLuminance == other.topLuminance &&
            bottomLuminance == other.bottomLuminance &&
            gridCols == other.gridCols &&
            gridRows == other.gridRows &&
            luminanceGrid.contentEquals(other.luminanceGrid)
    }

    override fun hashCode(): Int {
        var result = averageLuminance.hashCode()
        result = 31 * result + dominantColor
        result = 31 * result + accentColor
        result = 31 * result + warmthColor
        result = 31 * result + warmth.hashCode()
        result = 31 * result + luminanceGrid.contentHashCode()
        return result
    }

    companion object {
        const val DEFAULT_GRID_COLS = 8
        const val DEFAULT_GRID_ROWS = 16
        private const val HEX = "0123456789abcdef"

        val DEFAULT = LumaWallpaperPalette(
            averageLuminance = 0.08f,
            dominantColor = 0xFF0A0D14.toInt(),
            accentColor = 0xFF7C86FF.toInt(),
            warmthColor = 0xFF9FB4D6.toInt(),
            warmth = -0.12f,
            topLuminance = 0.07f,
            bottomLuminance = 0.09f
        )

        fun decode(raw: String?): LumaWallpaperPalette? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split('|')
            if (parts.size < 11 || parts[0] != "v1") return null
            val dom = parts[1].toIntOrNull() ?: return null
            val acc = parts[2].toIntOrNull() ?: return null
            val warmCol = parts[3].toIntOrNull() ?: return null
            val avg = parts[5].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
            val warm = parts[6].toFloatOrNull()?.coerceIn(-1f, 1f) ?: return null
            val top = parts[7].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
            val bot = parts[8].toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
            val cols = parts[9].toIntOrNull()?.coerceIn(1, 32) ?: return null
            val rows = parts[10].toIntOrNull()?.coerceIn(1, 64) ?: return null
            val hex = parts[11]
            val expected = cols * rows
            val grid = FloatArray(expected) { avg }
            if (hex.length == expected * 2) {
                for (i in 0 until expected) {
                    val hi = Character.digit(hex[i * 2], 16)
                    val lo = Character.digit(hex[i * 2 + 1], 16)
                    if (hi >= 0 && lo >= 0) {
                        grid[i] = ((hi shl 4) or lo) / 255f
                    }
                }
            }
            return LumaWallpaperPalette(
                averageLuminance = avg,
                dominantColor = dom,
                accentColor = acc,
                warmthColor = warmCol,
                warmth = warm,
                topLuminance = top,
                bottomLuminance = bot,
                gridCols = cols,
                gridRows = rows,
                luminanceGrid = grid
            )
        }

        /** Pure-Kotlin extraction from ARGB pixels for JVM testing & background decoding. */
        fun fromPixels(pixels: IntArray, width: Int, height: Int): LumaWallpaperPalette {
            if (width <= 0 || height <= 0 || pixels.size < width * height) return DEFAULT
            val seeds = WallpaperPaletteExtractor.extractFromPixels(pixels, width, height)
            var lumSum = 0.0
            var warmSum = 0.0
            val n = width * height
            for (i in 0 until n) {
                val c = ColorMath.opaque(pixels[i])
                lumSum += ColorMath.luminance(c)
                warmSum += (ColorMath.red(c) - ColorMath.blue(c)) / 255.0
            }
            val avgLum = (lumSum / n).toFloat().coerceIn(0f, 1f)
            val warmth = (warmSum / n).toFloat().coerceIn(-1f, 1f)
            val cols = min(DEFAULT_GRID_COLS, width).coerceAtLeast(1)
            val rows = min(DEFAULT_GRID_ROWS, height).coerceAtLeast(1)
            val grid = FloatArray(cols * rows)
            for (r in 0 until rows) {
                val y0 = (r * height) / rows
                val y1 = max(y0 + 1, ((r + 1) * height) / rows)
                for (col in 0 until cols) {
                    val x0 = (col * width) / cols
                    val x1 = max(x0 + 1, ((col + 1) * width) / cols)
                    var cellSum = 0.0
                    var cnt = 0
                    for (y in y0 until y1) {
                        val rowIdx = y * width
                        for (x in x0 until x1) {
                            cellSum += ColorMath.luminance(ColorMath.opaque(pixels[rowIdx + x]))
                            cnt++
                        }
                    }
                    grid[r * cols + col] = if (cnt > 0) (cellSum / cnt).toFloat().coerceIn(0f, 1f) else avgLum
                }
            }
            val warmthTone = seeds.secondary ?: seeds.tertiary ?: seeds.primary
            val rawAccent = seeds.accent ?: seeds.secondary ?: DEFAULT.accentColor
            return LumaWallpaperPalette(
                averageLuminance = avgLum,
                dominantColor = ColorMath.opaque(seeds.primary),
                accentColor = ColorMath.opaque(rawAccent),
                warmthColor = ColorMath.opaque(warmthTone),
                warmth = warmth,
                topLuminance = seeds.topLuminance ?: avgLum,
                bottomLuminance = seeds.bottomLuminance ?: avgLum,
                gridCols = cols,
                gridRows = rows,
                luminanceGrid = grid
            )
        }

        /** Downsamples [bitmap] by 1/8 (bounded) and extracts [LumaWallpaperPalette]. */
        fun fromBitmap(bitmap: Bitmap?): LumaWallpaperPalette? {
            if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
            return try {
                val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
                } else bitmap
                try {
                    val targetW = (readable.width / 8).coerceIn(16, 128)
                    val targetH = (readable.height / 8).coerceIn(16, 256)
                    val scaled = if (readable.width == targetW && readable.height == targetH) {
                        readable
                    } else {
                        Bitmap.createScaledBitmap(readable, targetW, targetH, true)
                    }
                    try {
                        val pixels = IntArray(scaled.width * scaled.height)
                        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
                        fromPixels(pixels, scaled.width, scaled.height)
                    } finally {
                        if (scaled !== readable && scaled !== bitmap) scaled.recycle()
                    }
                } finally {
                    if (readable !== bitmap) readable.recycle()
                }
            } catch (_: OutOfMemoryError) {
                null
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}

val LocalLumaPalette = staticCompositionLocalOf { LumaWallpaperPalette.DEFAULT }

/**
 * Adaptive Wallpaper-Driven Theme Manager for LumaOS Pro.
 *
 * - Restores the cached palette synchronously from DataStore on startup so cold
 *   starts render immediately with zero flash.
 * - Extracts luminance (`< 0.4` dark glass, `>= 0.4` light glass), dominant surface
 *   tint, vibrant accent, warmth tone, and a 1/8 downsampled region luminance grid
 *   on a background thread (`Dispatchers.Default`).
 * - Automatically re-extracts on system or Studio wallpaper changes.
 */
class LumaThemeManager(
    private val context: Context,
    private val prefs: LumaPrefs,
    private val scope: CoroutineScope
) {
    private val _palette = MutableStateFlow(loadInitialCachedPalette())
    val palette: StateFlow<LumaWallpaperPalette> = _palette.asStateFlow()

    @Volatile
    private var downsampledWallpaper: Bitmap? = null

    private var extractJob: Job? = null
    private var started = false

    private val wallpaperReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) {
                scheduleExtraction()
            }
        }
    }

    private fun loadInitialCachedPalette(): LumaWallpaperPalette =
        runCatching {
            val raw = runBlocking { prefs.cachedLumaPaletteRaw.first() }
            LumaWallpaperPalette.decode(raw)
        }.getOrNull() ?: LumaWallpaperPalette.DEFAULT

    fun start() {
        if (started) return
        started = true

        @Suppress("DEPRECATION")
        val filter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(wallpaperReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(wallpaperReceiver, filter)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            runCatching {
                val wm = WallpaperManager.getInstance(context)
                wm.addOnColorsChangedListener(
                    { _, which ->
                        if ((which and WallpaperManager.FLAG_SYSTEM) != 0) scheduleExtraction()
                    },
                    Handler(Looper.getMainLooper())
                )
            }
        }

        scope.launch {
            prefs.activeWallpaperId.distinctUntilChanged().collect {
                scheduleExtraction()
            }
        }

        scheduleExtraction()
    }

    /** Triggers a non-blocking background re-extraction of the active wallpaper. */
    fun scheduleExtraction() {
        extractJob?.cancel()
        extractJob = scope.launch(Dispatchers.Default) {
            val activeId = runCatching { prefs.activeWallpaperId.first() }.getOrNull()
            val extracted = extractCurrentWallpaper(activeId) ?: return@launch
            if (_palette.value != extracted) {
                _palette.value = extracted
                runCatching { prefs.setCachedLumaPaletteRaw(extracted.encode()) }
            }
        }
    }

    /** Alias used by Activity resume / debounced wallpaper-change hooks. */
    fun refreshFromSystemWallpaper() = scheduleExtraction()

    /** Updates palette directly when a caller has already decoded a fresh wallpaper bitmap. */
    fun updateFromBitmap(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        scope.launch(Dispatchers.Default) {
            val extracted = LumaWallpaperPalette.fromBitmap(bitmap) ?: return@launch
            cacheDownsampledBitmap(bitmap)
            if (_palette.value != extracted) {
                _palette.value = extracted
                runCatching { prefs.setCachedLumaPaletteRaw(extracted.encode()) }
            }
        }
    }

    /** Returns the cached 1/8 downsampled wallpaper bitmap if available (never decoded on UI thread). */
    fun cachedDownsampledBitmap(): Bitmap? = downsampledWallpaper?.takeIf { !it.isRecycled }

    /** Clears transient bitmaps under memory pressure while keeping the lightweight float grid. */
    fun trimMemory() {
        downsampledWallpaper = null
        LumaWallpaperBlurCache.clear()
    }

    @Composable
    fun collectPaletteAsState(): State<LumaWallpaperPalette> = palette.collectAsState()

    private suspend fun extractCurrentWallpaper(activeWallpaperId: String?): LumaWallpaperPalette? =
        withContext(Dispatchers.Default) {
            // 1. Try Luma Studio bundled or custom wallpaper if active.
            if (!activeWallpaperId.isNullOrBlank()) {
                decodeLumaWallpaperSample(activeWallpaperId)?.let { bmp ->
                    try {
                        cacheDownsampledBitmap(bmp)
                        LumaWallpaperPalette.fromBitmap(bmp)?.let { return@withContext it }
                    } finally {
                        if (bmp !== downsampledWallpaper) bmp.recycle()
                    }
                }
            }

            // 2. Try live WallpaperManager drawable (when permitted by the OS/role).
            val wm = runCatching { WallpaperManager.getInstance(context) }.getOrNull()
            val liveBitmap = runCatching {
                val drawable = wm?.drawable ?: return@runCatching null
                if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
                    val src = drawable.bitmap
                    val w = (src.width / 8).coerceIn(24, 135)
                    val h = (src.height / 8).coerceIn(24, 240)
                    Bitmap.createScaledBitmap(src, w, h, true)
                } else {
                    val w = (drawable.intrinsicWidth.takeIf { it > 0 } ?: 540).let { (it / 8).coerceIn(24, 135) }
                    val h = (drawable.intrinsicHeight.takeIf { it > 0 } ?: 1200).let { (it / 8).coerceIn(24, 240) }
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { out ->
                        val canvas = Canvas(out)
                        drawable.setBounds(0, 0, w, h)
                        drawable.draw(canvas)
                    }
                }
            }.getOrNull()

            if (liveBitmap != null) {
                cacheDownsampledBitmap(liveBitmap)
                LumaWallpaperPalette.fromBitmap(liveBitmap)?.let { return@withContext it }
            }

            // 3. Fallback on Android 8.1+ when READ_MEDIA_IMAGES restricts live drawable access:
            //    use WallpaperColors API so polarity, dominant tint, and accent still adapt!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && wm != null) {
                val platformColors: WallpaperColors? = runCatching {
                    wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                }.getOrNull()
                if (platformColors != null) {
                    val (seeds, _) = WallpaperPaletteExtractor.fromPlatformColors(platformColors)
                    val primary = ColorMath.opaque(seeds.primary)
                    val fallbackLum = ColorMath.luminance(primary).toFloat().coerceIn(0f, 1f)
                    val topLum = seeds.topLuminance ?: fallbackLum
                    val botLum = seeds.bottomLuminance ?: fallbackLum
                    val avgLum = ((topLum + botLum) * 0.5f).coerceIn(0f, 1f)
                    val warmCol = ColorMath.opaque(seeds.secondary ?: seeds.tertiary ?: primary)
                    val warm = ((ColorMath.red(warmCol) - ColorMath.blue(warmCol)) / 255f).coerceIn(-1f, 1f)
                    return@withContext LumaWallpaperPalette(
                        averageLuminance = avgLum,
                        dominantColor = primary,
                        accentColor = ColorMath.opaque(seeds.accent ?: primary),
                        warmthColor = warmCol,
                        warmth = warm,
                        topLuminance = topLum,
                        bottomLuminance = botLum
                    )
                }
            }

            null
        }

    private fun decodeLumaWallpaperSample(id: String): Bitmap? {
        return try {
            if (id.startsWith("art:")) {
                val entry = WallpaperCatalog.findBundled(id) ?: return null
                val resId = WallpaperArtResources.resForEntry(entry) ?: return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.resources.openRawResource(resId).use { BitmapFactory.decodeStream(it, null, bounds) }
                if (!WallpaperLimits.validSource(bounds.outWidth, bounds.outHeight)) return null
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = max(8, WallpaperLimits.sampleSize(bounds.outWidth, bounds.outHeight, 160, 160L * 256))
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.resources.openRawResource(resId).use { BitmapFactory.decodeStream(it, null, opts) }
            } else {
                val customName = WallpaperCatalog.customName(id) ?: return null
                val file = File(File(context.filesDir, "wallpaper_images"), "$customName.img")
                if (!file.isFile) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                file.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
                if (!WallpaperLimits.validSource(bounds.outWidth, bounds.outHeight)) return null
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = max(8, WallpaperLimits.sampleSize(bounds.outWidth, bounds.outHeight, 160, 160L * 256))
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                file.inputStream().use { BitmapFactory.decodeStream(it, null, opts) }
            }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun cacheDownsampledBitmap(source: Bitmap) {
        runCatching {
            val w = min(source.width, 135).coerceAtLeast(16)
            val h = min(source.height, 240).coerceAtLeast(16)
            val copy = if (source.width == w && source.height == h && source.config == Bitmap.Config.ARGB_8888) {
                source.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                Bitmap.createScaledBitmap(source, w, h, true)
            }
            downsampledWallpaper = copy
            val signature = sampleContentHash(copy)
            LumaWallpaperBlurCache.precompute(
                tag = "wp:${copy.width}x${copy.height}:$signature",
                source = copy,
                cacheDir = context.cacheDir
            )
        }
    }

    private fun sampleContentHash(bmp: Bitmap): Int {
        var h = 17
        val w = bmp.width
        val ht = bmp.height
        val stepX = (w / 4).coerceAtLeast(1)
        val stepY = (ht / 4).coerceAtLeast(1)
        var y = 0
        while (y < ht) {
            var x = 0
            while (x < w) {
                h = 31 * h + bmp.getPixel(x, y)
                x += stepX
            }
            y += stepY
        }
        return h
    }
}
