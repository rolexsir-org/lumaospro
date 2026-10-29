package com.lumaos.pro.launcher.launcher.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import kotlin.math.max

/**
 * The A-Z strip down the side of the app drawer.
 *
 * A hand-drawn View rather than a RecyclerView of TextViews: it is ~30
 * characters, it never changes size, and it is touched during fling-scrolls, so
 * a real view hierarchy would cost far more than it saves.
 */
class AlphaIndexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        isClickable = true
        isFocusable = true
    }

    /** Bucket characters to draw, in order. */
    var letters: List<Char> = emptyList()
        set(value) {
            field = value
            selectedIndex = -1
            invalidate()
        }

    private var selectedIndex = -1

    /** Called continuously while dragging, so the grid tracks the finger. */
    var onLetterTouched: ((Char) -> Unit)? = null

    fun setAccentColor(color: Int) {
        paint.color = color
        invalidate()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lumaos_accent)
        textAlign = Paint.Align.CENTER
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val items = letters
        if (items.isEmpty() || height <= 0) return

        val slot = height.toFloat() / items.size
        paint.textSize = textSizePx()
        // On short screens or large font settings, keep adjacent buckets from
        // drawing on top of one another. The full letters remain accessible.
        val naturalHeight = paint.fontMetrics.descent - paint.fontMetrics.ascent
        if (naturalHeight > slot && naturalHeight > 0f) paint.textSize *= slot / naturalHeight
        val fontMetrics = paint.fontMetrics
        val baselineOffset = (slot - (fontMetrics.descent - fontMetrics.ascent)) / 2f - fontMetrics.ascent
        val visualStripPx = LumaTokens.dpPx(resources.displayMetrics.density, LumaTokens.alphaScrubberVisualWidth)
        val drawCenterX = (width - visualStripPx / 2f).coerceAtLeast(width / 2f)

        items.forEachIndexed { index, letter ->
            canvas.drawText(letter.toString(), drawCenterX, index * slot + baselineOffset, paint)
        }
    }

    /**
     * Resolved on every draw rather than cached: displayMetrics.scaledDensity
     * is deprecated, and reading it once at construction would freeze the text
     * size against whichever font scale was in force when the view was created.
     */
    private fun textSizePx(): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        LETTER_SP,
        resources.displayMetrics
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = LumaTokens.dpInt(resources.displayMetrics.density, LumaTokens.alphaScrubberTouchWidth)
        val width = resolveSize(max(desiredWidth, suggestedMinimumWidth), widthMeasureSpec)
        val height = resolveSize(suggestedMinimumHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val items = letters
        if (items.isEmpty()) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Claim the gesture so MOVE events keep arriving even once the
                // finger leaves this view's bounds.
                parent?.requestDisallowInterceptTouchEvent(true)
                emitLetterAt(event.y)
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                emitLetterAt(event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    private fun emitLetterAt(y: Float) {
        val items = letters
        if (items.isEmpty() || height <= 0) return
        val slot = height.toFloat() / items.size
        val index = (y / slot).toInt().coerceIn(0, items.lastIndex)
        if (index != selectedIndex) {
            selectedIndex = index
            LumaHaptics.tick(this)
            onLetterTouched?.invoke(items[index])
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (letters.isNotEmpty()) {
            info.isScrollable = true
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> moveSelection(1)
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> moveSelection(-1)
        else -> super.performAccessibilityAction(action, arguments)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_DOWN -> moveSelection(1)
        KeyEvent.KEYCODE_DPAD_UP -> moveSelection(-1)
        else -> super.onKeyDown(keyCode, event)
    }

    private fun moveSelection(delta: Int): Boolean {
        val items = letters
        if (items.isEmpty()) return false
        val start = if (selectedIndex < 0) {
            if (delta > 0) -1 else items.size
        } else selectedIndex
        val next = (start + delta).coerceIn(0, items.lastIndex)
        if (next == selectedIndex) return false
        selectedIndex = next
        val letter = items[next]
        onLetterTouched?.invoke(letter)
        announceForAccessibility(context.getString(R.string.drawer_alphabet_selected, letter.toString()))
        return true
    }

    /** The view is touch-interactive, so it must report clickability. */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        private const val LETTER_SP = LumaTokens.typeMicroSp
    }
}