package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.lumaos.pro.launcher.ui.theme.AtmosphereLayer
import com.lumaos.pro.launcher.ui.theme.AtmospherePlan
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import kotlin.math.max

/**
 * Paints the theme's atmosphere behind the home screen (see AtmospherePlan).
 *
 * Cost model: gradients are drawn with cached shaders positioned by a local
 * matrix, so a frame allocates nothing. When ambient motion is off (the
 * default below the FULL effects tier, under battery saver, or with reduced
 * motion) the view draws once and stays idle. When it is on, it redraws at a
 * capped rate (24 fps) and only while the window is visible.
 */
class AmbientBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var theme: ResolvedTheme? = null
    private var previous: List<AtmosphereLayer> = emptyList()
    private var current: List<AtmosphereLayer> = emptyList()
    private var fadeStart = 0L
    private var fadeMs = 0L

    private var ambient = false
    private var fps = 24
    private var detail = true
    private var windowVisible = false
    private var fallbackSolid = false
    private var phaseOrigin = SystemClock.uptimeMillis()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val matrix = Matrix()
    private val glowShaders = HashMap<Int, RadialGradient>()
    private val fadeShaders = HashMap<AtmosphereLayer.VerticalFade, LinearGradient>()
    private var fadeShaderHeight = 0f
    private var linePoints = FloatArray(0)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    fun setTheme(newTheme: ResolvedTheme, crossfadeMs: Long) {
        if (newTheme == theme) return
        previous = if (crossfadeMs > 0 && current.isNotEmpty()) current else emptyList()
        theme = newTheme
        fadeMs = crossfadeMs
        fadeStart = SystemClock.uptimeMillis()
        rebuild()
    }

    /**
     * Paints the theme background even when the plan expects a photographic
     * surface behind it. Used when a bundled wallpaper cannot be decoded, so the
     * home screen still has a legible, intentional backdrop.
     */
    fun setFallbackSolid(enabled: Boolean) {
        if (fallbackSolid == enabled) return
        fallbackSolid = enabled
        invalidate()
    }

    fun setEffects(ambientMotion: Boolean, framesPerSecond: Int, atmosphereDetail: Boolean) {
        val changed = ambient != ambientMotion || detail != atmosphereDetail
        ambient = ambientMotion
        fps = framesPerSecond.coerceIn(1, 60)
        detail = atmosphereDetail
        if (changed) rebuild()
    }

    private fun phase(): Float {
        if (!ambient) return 0.18f
        val periodMs = 48_000L
        return ((SystemClock.uptimeMillis() - phaseOrigin) % periodMs) / periodMs.toFloat()
    }

    private fun rebuild() {
        val t = theme ?: return
        current = AtmospherePlan.layers(t, phase(), detail)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        windowVisible = true
        phaseOrigin = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        windowVisible = false
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        windowVisible = visibility == VISIBLE
        if (windowVisible) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        if (fallbackSolid) {
            paint.shader = null
            paint.color = theme?.colors?.background ?: ColorMath.BLACK
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        val now = SystemClock.uptimeMillis()
        val fade = if (fadeMs <= 0) 1f else ((now - fadeStart) / fadeMs.toFloat()).coerceIn(0f, 1f)
        if (fade < 1f && previous.isNotEmpty()) {
            drawLayers(canvas, previous, w, h, 1f)
            drawLayers(canvas, current, w, h, fade)
            postInvalidateOnAnimation()
        } else {
            previous = emptyList()
            drawLayers(canvas, current, w, h, 1f)
        }
        if (ambient && windowVisible && isShown) {
            current = theme?.let { AtmospherePlan.layers(it, phase(), detail) } ?: current
            postInvalidateDelayed(1000L / fps)
        }
    }

    private fun drawLayers(canvas: Canvas, layers: List<AtmosphereLayer>, w: Float, h: Float, alpha: Float) {
        val big = max(w, h)
        for (layer in layers) when (layer) {
            is AtmosphereLayer.Solid -> {
                paint.shader = null
                paint.color = ColorMath.withAlpha(layer.color, ColorMath.alpha(layer.color) / 255f * alpha)
                canvas.drawRect(0f, 0f, w, h, paint)
            }
            is AtmosphereLayer.Glow -> {
                val shader = glowShaders.getOrPut(layer.color) {
                    RadialGradient(0f, 0f, 1f, layer.color, ColorMath.withAlpha(layer.color, 0f), Shader.TileMode.CLAMP)
                }
                val r = layer.radius * big
                matrix.reset()
                matrix.setScale(r, r * layer.scaleY)
                matrix.postTranslate(layer.cx * w, layer.cy * h)
                shader.setLocalMatrix(matrix)
                paint.shader = shader
                paint.alpha = (255 * alpha).toInt()
                canvas.drawRect(0f, 0f, w, h, paint)
                paint.alpha = 255
            }
            is AtmosphereLayer.VerticalFade -> {
                if (fadeShaderHeight != h) {
                    fadeShaders.clear()
                    fadeShaderHeight = h
                }
                paint.shader = fadeShaders.getOrPut(layer) {
                    LinearGradient(0f, layer.y0 * h, 0f, layer.y1 * h, layer.color0, layer.color1, Shader.TileMode.CLAMP)
                }
                paint.alpha = (255 * alpha).toInt()
                canvas.drawRect(0f, layer.y0 * h, w, layer.y1 * h, paint)
                paint.alpha = 255
            }
            is AtmosphereLayer.Lines -> {
                val segs = layer.segments
                if (linePoints.size < segs.size) linePoints = FloatArray(segs.size)
                for (i in segs.indices step 2) {
                    linePoints[i] = segs[i] * w
                    linePoints[i + 1] = segs[i + 1] * h
                }
                linePaint.strokeWidth = layer.strokeDp * resources.displayMetrics.density
                linePaint.color = ColorMath.withAlpha(layer.color, ColorMath.alpha(layer.color) / 255f * alpha)
                canvas.drawLines(linePoints, 0, segs.size, linePaint)
            }
        }
        paint.shader = null
    }
}
