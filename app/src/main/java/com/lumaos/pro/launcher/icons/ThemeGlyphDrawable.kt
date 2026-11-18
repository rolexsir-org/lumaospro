package com.lumaos.pro.launcher.icons

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** A themed mark can be drawn as a small control or as the face of an app tile. */
internal class ThemeGlyphDrawable(
    val pack: ThemeIconPack,
    private val base: Drawable?,
    private val size: Int,
    private val app: LivingIconId? = null
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -1 }
    private var tint: ColorStateList? = null
    private var tintColor = -1
    private var filter: ColorFilter? = null

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        pack.drawFrame(canvas, paint)
        val inset = pack.glyphInset
        if (app != null) {
            canvas.translate(inset.toFloat(), inset.toFloat())
            val scale = (24 - 2 * inset) / 24f
            canvas.scale(scale, scale)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (pack.squareInk) 2.5f else 2.15f
            paint.strokeCap = if (pack.squareInk) Paint.Cap.SQUARE else Paint.Cap.ROUND
            paint.strokeJoin = if (pack.squareInk) Paint.Join.BEVEL else Paint.Join.ROUND
            canvas.drawPath(ThemeAppGlyphs.path(app, pack), paint)
            paint.style = Paint.Style.FILL
        } else {
            base?.setBounds(inset, inset, 24 - inset, 24 - inset)
            base?.draw(canvas)
        }
        canvas.restoreToCount(save)
    }

    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        base?.alpha = alpha
        invalidateSelf()
    }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        filter = colorFilter
        paint.colorFilter = colorFilter
        base?.colorFilter = colorFilter
        invalidateSelf()
    }
    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint
        base?.setTintList(tint)
        updateTint(state)
        invalidateSelf()
    }
    override fun isStateful() = tint?.isStateful == true || base?.isStateful == true
    override fun onStateChange(state: IntArray): Boolean {
        val baseChanged = base?.setState(state) == true
        return updateTint(state) || baseChanged
    }
    private fun updateTint(state: IntArray): Boolean {
        val color = tint?.getColorForState(state, tint?.defaultColor ?: -1) ?: -1
        if (color == tintColor) return false
        tintColor = color
        paint.color = color
        paint.colorFilter = filter
        return true
    }
}
