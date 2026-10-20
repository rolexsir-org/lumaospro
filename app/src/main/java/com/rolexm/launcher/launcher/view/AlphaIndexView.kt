package com.rolexm.launcher.launcher.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.rolexm.launcher.R
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
            invalidate()
        }

    /** Called continuously while dragging, so the grid tracks the finger. */
    var onLetterTouched: ((Char) -> Unit)? = null

    fun setAccentColor(color: Int) {
        paint.color = color
        invalidate()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.rolexm_accent)
        textAlign = Paint.Align.CENTER
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val items = letters
        if (items.isEmpty() || height <= 0) return

        paint.textSize = textSizePx()
        val slot = height.toFloat() / items.size
        val fontMetrics = paint.fontMetrics
        val baselineOffset = (slot - (fontMetrics.descent - fontMetrics.ascent)) / 2f - fontMetrics.ascent

        items.forEachIndexed { index, letter ->
            canvas.drawText(letter.toString(), width / 2f, index * slot + baselineOffset, paint)
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
        val desiredWidth = (26 * resources.displayMetrics.density).toInt()
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
        onLetterTouched?.invoke(items[index])
    }

    /** The view is touch-interactive, so it must report clickability. */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        private const val LETTER_SP = 11f
    }
}