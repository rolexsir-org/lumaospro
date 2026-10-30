package com.lumaos.pro.launcher.controls

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.LumaTokens

/**
 * One glass slider: brightness or media volume.
 *
 * A custom [View] rather than a themed `SeekBar` because the shade needs the
 * Luma Glass track (a translucent capsule with a specular rim) and a thumb that
 * matches the panel's radii - and because a `SeekBar` brings a progress drawable,
 * a split track and a ripple that all fight the material. The interaction model
 * is the platform one: drag anywhere on the track, tap to jump, arrow keys when
 * focused, and `RangeInfo` for TalkBack.
 *
 * It reports positions, never system values: [DeviceControls] owns the mapping to
 * a brightness level or a stream index, and the slider is reset from the device's
 * real reading whenever the panel refreshes. That ordering is what keeps a refused
 * write from leaving the UI claiming a value the device does not have.
 */
class GlassSlider(context: Context) : View(context) {

    /** Called continuously while the finger moves; `fromUser` distinguishes drags. */
    var onValueChanged: ((fraction: Float, fromUser: Boolean) -> Unit)? = null

    /** Called once when the finger lifts, so writes can be committed. */
    var onCommit: ((fraction: Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val trackHeight = dp(LumaTokens.sliderTrackHeight)
    private val trackRadius = trackHeight / 2f
    private val thumbRadius = dp(THUMB_DP) / 2f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    private var fraction = 0f
    private var dragging = false

    /** False while the platform refuses the write; the slider then only reports. */
    var interactive: Boolean = true

    init {
        isFocusable = true
        isClickable = true
        minimumHeight = dp(LumaTokens.minTouchTarget).toInt()
    }

    /** Colours from the active glass material; called on every theme change. */
    fun setMaterial(track: Int, fill: Int, rim: Int, thumb: Int, thumbRim: Int) {
        trackPaint.color = track
        fillPaint.color = fill
        rimPaint.color = rim
        rimPaint.strokeWidth = dp(LumaTokens.glassBorderWidth)
        thumbPaint.color = thumb
        thumbRimPaint.color = thumbRim
        thumbRimPaint.strokeWidth = dp(1f)
        invalidate()
    }

    /** Sets the position from the device's real value without notifying listeners. */
    fun setValueSilently(value: Float) {
        val next = value.coerceIn(0f, 1f)
        if (next == fraction) return
        fraction = next
        invalidate()
    }

    fun value(): Float = fraction

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = resolveSize(dp(LumaTokens.minTouchTarget).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val inset = thumbRadius
        rect.set(inset, (h - trackHeight) / 2f, w - inset, (h + trackHeight) / 2f)

        canvas.drawRoundRect(rect, trackRadius, trackRadius, trackPaint)
        val filled = RectF(rect)
        filled.right = (rect.left + rect.width() * fraction).coerceAtLeast(rect.left + trackHeight * 0.5f)
        canvas.drawRoundRect(filled, trackRadius, trackRadius, fillPaint)

        val rim = RectF(rect)
        val half = rimPaint.strokeWidth / 2f
        rim.inset(half, half)
        canvas.drawRoundRect(rim, trackRadius, trackRadius, rimPaint)

        val cx = rect.left + rect.width() * fraction
        val cy = rect.centerY()
        canvas.drawCircle(cx, cy, thumbRadius, thumbPaint)
        canvas.drawCircle(cx, cy, thumbRadius - thumbRimPaint.strokeWidth / 2f, thumbRimPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFrom(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                updateFrom(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) return false
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                updateFrom(event.x)
                onCommit?.invoke(fraction)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                onCommit?.invoke(fraction)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFrom(x: Float) {
        val inset = thumbRadius
        val usable = (width - inset * 2).coerceAtLeast(1f)
        val next = ((x - inset) / usable).coerceIn(0f, 1f)
        if (kotlin.math.abs(next - fraction) < 0.0005f) return
        fraction = next
        invalidate()
        onValueChanged?.invoke(fraction, true)
    }

    /** Keyboard and switch-access parity with the platform slider. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!interactive) return super.onKeyDown(keyCode, event)
        val step = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN -> -KEY_STEP
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP -> KEY_STEP
            else -> return super.onKeyDown(keyCode, event)
        }
        fraction = (fraction + step).coerceIn(0f, 1f)
        invalidate()
        onValueChanged?.invoke(fraction, true)
        onCommit?.invoke(fraction)
        LumaHaptics.tick(this)
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.SeekBar::class.java.name
        // RangeInfo is what makes TalkBack announce "50 percent" and expose the
        // adjust actions; a bare content description would not.
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
            0,
            100,
            (fraction * 100).toInt()
        )
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        if (!interactive) info.isImportantForAccessibility = true
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (!interactive) return super.performAccessibilityAction(action, arguments)
        when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> {
                fraction = (fraction + KEY_STEP * 2).coerceIn(0f, 1f)
                invalidate()
                onValueChanged?.invoke(fraction, true)
                onCommit?.invoke(fraction)
                return true
            }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> {
                fraction = (fraction - KEY_STEP * 2).coerceIn(0f, 1f)
                invalidate()
                onValueChanged?.invoke(fraction, true)
                onCommit?.invoke(fraction)
                return true
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    companion object {
        private const val THUMB_DP = 22f
        private const val KEY_STEP = 0.05f

        /** Track/fill/rim/thumb colours derived from one glass content colour. */
        fun material(content: Int, accent: Int, dark: Boolean): Material = Material(
            track = ColorMath.withAlpha(content, if (dark) 0.16f else 0.12f),
            fill = ColorMath.withAlpha(accent, 0.85f),
            rim = ColorMath.withAlpha(ColorMath.WHITE, if (dark) 0.14f else 0.22f),
            thumb = ColorMath.opaque(accent),
            thumbRim = ColorMath.withAlpha(ColorMath.WHITE, 0.35f)
        )

        data class Material(val track: Int, val fill: Int, val rim: Int, val thumb: Int, val thumbRim: Int)
    }
}
