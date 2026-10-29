package com.lumaos.pro.launcher.icons

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.IconShape
import com.lumaos.pro.launcher.ui.theme.IconShapes
import com.lumaos.pro.launcher.ui.theme.IconStyle
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme

/**
 * Everything that decides how an icon looks. Part of the icon-cache key: when
 * any field changes, cached icons are re-rendered.
 */
data class IconRenderSpec(
    val sizePx: Int,
    val shape: IconShape,
    val style: IconStyle,
    val plateColor: Int,
    val rimColor: Int,
    val tintColor: Int,
    val tintPlateColor: Int,
    /** Soft drop shadow and rim light (effects BALANCED and above). */
    val depth: Boolean,
    val iconPack: String?,
    /** Whether bundled LumaOS Pro Living Icons are active by default. */
    val livingIcons: Boolean = true,
    /** Hash/revision of per-app Living Icon overrides so cache invalidates on change. */
    val livingOverridesHash: Int = 0,
    /** True only when the user explicitly forced TINTED style (not merely a preset default). */
    val explicitTint: Boolean = false
) {
    companion object {
        fun from(
            theme: ResolvedTheme,
            sizePx: Int,
            depth: Boolean,
            iconPack: String?,
            livingIcons: Boolean = true,
            livingOverridesHash: Int = 0,
            explicitTint: Boolean = false
        ): IconRenderSpec {
            val c = theme.colors
            return IconRenderSpec(
                sizePx = sizePx.coerceAtLeast(16),
                shape = theme.iconShape,
                style = theme.iconStyle,
                plateColor = if (theme.isDark) c.surface2 else ColorMath.WHITE,
                rimColor = ColorMath.withAlpha(if (theme.isDark) c.outlineStrong else c.outline, 0.9f),
                tintColor = c.onAccentContainer,
                tintPlateColor = c.accentContainer,
                depth = depth,
                iconPack = iconPack,
                livingIcons = livingIcons,
                livingOverridesHash = livingOverridesHash,
                explicitTint = explicitTint
            )
        }
    }
}

/**
 * Composes a source icon into LumaOS Pro's icon treatment.
 *
 * Adaptive icons are re-masked from their own layers (so the artwork is never
 * cropped twice); legacy icons are either clipped full-bleed when they are
 * already square and opaque, or seated on a plate. Monochrome layers (Android
 * 13+) are used for TINTED; icons without one are desaturated and toned
 * instead, never replaced. Rendering happens on the icon thread into a
 * software bitmap; nothing here runs during a frame.
 */
class IconRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun render(source: Drawable, spec: IconRenderSpec, fromIconPack: Boolean): Bitmap {
        val size = spec.sizePx
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Leave a margin for the shadow so it is not clipped by the bitmap.
        val margin = if (spec.depth) size * 0.06f else size * 0.02f
        val rect = RectF(margin, margin * 0.7f, size - margin, size - margin * 1.3f)

        if (source is LivingIconDrawable) {
            renderLivingIcon(canvas, source, rect, size, spec)
            return bitmap
        }

        val adaptive = source as? AdaptiveIconDrawable
        val mask = maskPath(spec.shape, adaptive, rect)

        val plateNeeded = when (spec.style) {
            IconStyle.PLATE, IconStyle.TINTED -> true
            IconStyle.NATURAL -> !fromIconPack && adaptive == null && spec.shape != IconShape.SYSTEM && !isOpaqueSquare(source)
        }

        if (spec.depth && (plateNeeded || adaptive != null || spec.shape != IconShape.SYSTEM)) {
            drawShadow(canvas, mask, size)
        }

        when (spec.style) {
            IconStyle.NATURAL -> when {
                fromIconPack -> drawFull(canvas, source, rect)
                adaptive != null -> drawAdaptive(canvas, adaptive, rect, mask, null)
                spec.shape == IconShape.SYSTEM -> drawFull(canvas, source, rect)
                plateNeeded -> {
                    fill(canvas, mask, spec.plateColor)
                    drawInset(canvas, source, rect, mask, 0.72f, null)
                }
                else -> drawClipped(canvas, source, rect, mask)
            }
            IconStyle.PLATE -> {
                fill(canvas, mask, spec.plateColor)
                if (adaptive != null && !fromIconPack) {
                    val inner = inset(rect, 0.80f)
                    val innerMask = maskPath(spec.shape, adaptive, inner)
                    drawAdaptive(canvas, adaptive, inner, innerMask, null)
                } else {
                    drawInset(canvas, source, rect, mask, 0.70f, null)
                }
            }
            IconStyle.TINTED -> {
                fill(canvas, mask, spec.tintPlateColor)
                val mono = if (Build.VERSION.SDK_INT >= 33 && !fromIconPack) adaptive?.monochrome else null
                if (mono != null) {
                    drawLayer(canvas, mono, rect, mask, PorterDuffColorFilter(spec.tintColor, PorterDuff.Mode.SRC_IN))
                } else {
                    drawInset(canvas, source, rect, mask, if (adaptive != null) 1f else 0.70f, toneFilter(spec.tintColor))
                }
            }
        }

        // Fine rim: defines the edge on busy wallpapers and gives plates a
        // machined finish. Natural icons with the system mask keep their edge.
        if (plateNeeded || spec.style != IconStyle.NATURAL) stroke(canvas, mask, spec.rimColor, size * 0.012f)
        if (spec.depth && (plateNeeded || adaptive != null)) rimLight(canvas, mask, rect, size)
        return bitmap
    }

    // ------------------------------------------------------------------ paths

    private fun maskPath(shape: IconShape, adaptive: AdaptiveIconDrawable?, rect: RectF): Path {
        val outline = IconShapes.outline(shape)
        if (outline == null) {
            if (adaptive != null) {
                // The device's own mask, defined in a 100x100 box.
                val path = Path(adaptive.iconMask)
                val m = Matrix()
                m.setScale(rect.width() / MASK_SIZE, rect.height() / MASK_SIZE)
                m.postTranslate(rect.left, rect.top)
                path.transform(m)
                return path
            }
            return polygon(IconShapes.outline(IconShape.SQUIRCLE)!!, rect)
        }
        return polygon(outline, rect)
    }

    private fun polygon(points: FloatArray, rect: RectF): Path {
        val p = Path()
        for (i in points.indices step 2) {
            val x = rect.left + points[i] * rect.width()
            val y = rect.top + points[i + 1] * rect.height()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    private fun inset(rect: RectF, scale: Float): RectF {
        val dx = rect.width() * (1 - scale) / 2
        val dy = rect.height() * (1 - scale) / 2
        return RectF(rect.left + dx, rect.top + dy, rect.right - dx, rect.bottom - dy)
    }

    // --------------------------------------------------------------- drawing

    private fun drawAdaptive(canvas: Canvas, icon: AdaptiveIconDrawable, rect: RectF, mask: Path, filter: android.graphics.ColorFilter?) {
        val save = canvas.save()
        canvas.clipPath(mask)
        icon.background?.let { drawLayer(canvas, it, rect, null, filter) }
        icon.foreground?.let { drawLayer(canvas, it, rect, null, filter) }
        canvas.restoreToCount(save)
    }

    /** Adaptive layers are 1.5x the visible icon, centred (25% extra on each side). */
    private fun drawLayer(canvas: Canvas, layer: Drawable, rect: RectF, mask: Path?, filter: android.graphics.ColorFilter?) {
        val save = canvas.save()
        if (mask != null) canvas.clipPath(mask)
        val ex = rect.width() * 0.25f
        val ey = rect.height() * 0.25f
        layer.setBounds((rect.left - ex).toInt(), (rect.top - ey).toInt(), (rect.right + ex).toInt(), (rect.bottom + ey).toInt())
        val previous = layer.colorFilter
        if (filter != null) layer.colorFilter = filter
        layer.draw(canvas)
        if (filter != null) layer.colorFilter = previous
        canvas.restoreToCount(save)
    }

    private fun drawFull(canvas: Canvas, d: Drawable, rect: RectF) {
        d.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        d.draw(canvas)
    }

    private fun drawClipped(canvas: Canvas, d: Drawable, rect: RectF, mask: Path) {
        val save = canvas.save()
        canvas.clipPath(mask)
        drawFull(canvas, d, rect)
        canvas.restoreToCount(save)
    }

    private fun drawInset(canvas: Canvas, d: Drawable, rect: RectF, mask: Path, scale: Float, filter: android.graphics.ColorFilter?) {
        val save = canvas.save()
        canvas.clipPath(mask)
        val inner = inset(rect, scale)
        if (d is AdaptiveIconDrawable && scale >= 0.99f) {
            d.background?.let { drawLayer(canvas, it, inner, null, filter) }
            d.foreground?.let { drawLayer(canvas, it, inner, null, filter) }
        } else {
            val previous = d.colorFilter
            if (filter != null) d.colorFilter = filter
            drawFull(canvas, d, inner)
            if (filter != null) d.colorFilter = previous
        }
        canvas.restoreToCount(save)
    }

    private fun fill(canvas: Canvas, path: Path, color: Int) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun stroke(canvas: Canvas, path: Path, color: Int, width: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width.coerceAtLeast(1f)
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun drawShadow(canvas: Canvas, path: Path, size: Int) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = ColorMath.withAlpha(ColorMath.BLACK, 0.28f)
        paint.maskFilter = BlurMaskFilter(size * 0.035f, BlurMaskFilter.Blur.NORMAL)
        val save = canvas.save()
        canvas.translate(0f, size * 0.025f)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
        paint.maskFilter = null
    }

    /** A thin specular highlight along the upper edge. */
    private fun rimLight(canvas: Canvas, path: Path, rect: RectF, size: Int) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (size * 0.018f).coerceAtLeast(1f)
        paint.shader = LinearGradient(
            0f, rect.top, 0f, rect.centerY(),
            ColorMath.withAlpha(ColorMath.WHITE, 0.42f), ColorMath.withAlpha(ColorMath.WHITE, 0f),
            Shader.TileMode.CLAMP
        )
        val save = canvas.save()
        canvas.clipPath(path)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
        paint.shader = null
    }

    /** Desaturates and maps luminance onto [tint]: monochrome look for icons without a monochrome layer. */
    private fun toneFilter(tint: Int): ColorMatrixColorFilter {
        val gray = ColorMatrix().apply { setSaturation(0f) }
        val r = ColorMath.red(tint) / 255f
        val g = ColorMath.green(tint) / 255f
        val b = ColorMath.blue(tint) / 255f
        val tone = ColorMatrix(floatArrayOf(
            r, 0f, 0f, 0f, 0f,
            0f, g, 0f, 0f, 0f,
            0f, 0f, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        gray.postConcat(tone)
        return ColorMatrixColorFilter(gray)
    }

    private fun renderLivingIcon(
        canvas: Canvas,
        living: LivingIconDrawable,
        rect: RectF,
        size: Int,
        spec: IconRenderSpec
    ) {
        if (living.bitmap.isRecycled) return
        val targetRect = if (spec.depth) rect else inset(rect, 0.94f)
        if (spec.depth) {
            val shadowFootprint = inset(targetRect, 0.93f)
            val shadowPath = polygon(IconShapes.outline(IconShape.SQUIRCLE)!!, shadowFootprint)
            drawShadow(canvas, shadowPath, size)
        }
        val bounds = computeLivingBounds(
            iconId = living.iconId,
            targetLeft = targetRect.left,
            targetTop = targetRect.top,
            targetRight = targetRect.right,
            targetBottom = targetRect.bottom
        )
        val dst = RectF(bounds[0], bounds[1], bounds[2], bounds[3])
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.colorFilter = if (spec.style == IconStyle.TINTED && spec.explicitTint) {
            toneFilter(spec.tintColor)
        } else {
            null
        }
        canvas.drawBitmap(living.bitmap, null, dst, paint)
        paint.colorFilter = null
    }

    /** True when a legacy icon already fills its square (full-bleed artwork). */
    private fun isOpaqueSquare(d: Drawable): Boolean {
        val probe = Bitmap.createBitmap(PROBE, PROBE, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, PROBE, PROBE)
        d.draw(Canvas(probe))
        var opaque = 0
        for (y in 0 until PROBE) for (x in 0 until PROBE) {
            if ((probe.getPixel(x, y) ushr 24) > 200) opaque++
        }
        probe.recycle()
        return opaque >= PROBE * PROBE * 0.88f
    }

    companion object {
        private const val MASK_SIZE = 100f
        private const val PROBE = 24

        /**
         * Maps [iconId]'s measured optical-glass silhouette (`visibleSpanFraction`)
         * onto `[targetLeft..targetRight, targetTop..targetBottom]` without
         * distorting aspect ratio. Pure float math so unit tests verify alignment.
         */
        internal fun computeLivingBounds(
            iconId: LivingIconId,
            targetLeft: Float,
            targetTop: Float,
            targetRight: Float,
            targetBottom: Float
        ): FloatArray {
            val w = (targetRight - targetLeft).coerceAtLeast(1f)
            val h = (targetBottom - targetTop).coerceAtLeast(1f)
            val targetSpan = minOf(w, h)
            val fullSpan = targetSpan / iconId.visibleSpanFraction.coerceAtLeast(0.5f)
            val targetCx = (targetLeft + targetRight) * 0.5f
            val targetCy = (targetTop + targetBottom) * 0.5f
            val cx = targetCx - (iconId.centerXFraction - 0.5f) * fullSpan
            val cy = targetCy - (iconId.centerYFraction - 0.5f) * fullSpan
            return floatArrayOf(
                cx - fullSpan * 0.5f,
                cy - fullSpan * 0.5f,
                cx + fullSpan * 0.5f,
                cy + fullSpan * 0.5f
            )
        }
    }
}
