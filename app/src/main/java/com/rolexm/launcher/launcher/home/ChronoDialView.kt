package com.rolexm.launcher.launcher.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.rolexm.launcher.launcher.model.Chronometer
import com.rolexm.launcher.launcher.model.ReadoutSnapshot
import com.rolexm.launcher.ui.theme.ColorMath
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * ROLEXM's chronometer: the brand's gauge mark turned into an instrument.
 *
 *  - outer ring: the 24-hour day, midnight at the top, with hour ticks;
 *  - accent arc: how much of the day has passed, ending in a lit head;
 *  - inner arc: battery level from the system battery broadcast;
 *  - marker: the next alarm's position, when one is set for the next 24 h.
 *
 * Every mark is real data. The view redraws once a minute, aligned to the
 * minute boundary, and only while attached and shown.
 */
class ChronoDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var ringColor: Int = 0x66FFFFFF; set(v) { field = v; invalidate() }
    var progressColor: Int = 0xFFB5C5A9.toInt(); set(v) { field = v; invalidate() }
    var batteryColor: Int = 0xCCFFFFFF.toInt(); set(v) { field = v; invalidate() }
    var markerColor: Int = 0xFFFFFFFF.toInt(); set(v) { field = v; invalidate() }

    var snapshot: ReadoutSnapshot = ReadoutSnapshot()
        set(v) {
            field = v
            updateDescription()
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val arcRect = RectF()
    private val marker = Path()
    private val density = resources.displayMetrics.density

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            updateDescription()
            postDelayed(this, Chronometer.millisToNextMinute(System.currentTimeMillis()) + 50)
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(tick)
        postDelayed(tick, Chronometer.millisToNextMinute(System.currentTimeMillis()) + 50)
        updateDescription()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun updateDescription() {
        contentDescription = Chronometer.describeDial(Chronometer.dayFraction(System.currentTimeMillis()), snapshot)
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (size <= 0f) return
        val cx = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val outer = size / 2f - 3f * density
        val now = System.currentTimeMillis()
        val day = Chronometer.dayFraction(now)

        // Hour ticks; the four quarter-day ticks are longer.
        paint.style = Paint.Style.STROKE
        paint.color = ringColor
        for (h in 0 until 24) {
            val major = h % 6 == 0
            paint.strokeWidth = if (major) 1.6f * density else 1f * density
            val len = if (major) outer * 0.16f else outer * 0.08f
            val a = Math.toRadians(h * 15.0 - 90.0)
            val r0 = outer - len
            canvas.drawLine(
                cx + (r0 * cos(a)).toFloat(), cy + (r0 * sin(a)).toFloat(),
                cx + (outer * cos(a)).toFloat(), cy + (outer * sin(a)).toFloat(), paint
            )
        }

        // Day-progress arc and its lit head.
        val progressR = outer * 0.74f
        arcRect.set(cx - progressR, cy - progressR, cx + progressR, cy + progressR)
        paint.strokeWidth = 2.4f * density
        paint.color = ColorMath.withAlpha(ringColor, 0.35f)
        canvas.drawArc(arcRect, 0f, 360f, false, paint)
        paint.color = progressColor
        canvas.drawArc(arcRect, -90f, day * 360f, false, paint)
        val ha = Math.toRadians(day * 360.0 - 90.0)
        paint.style = Paint.Style.FILL
        paint.color = ColorMath.withAlpha(progressColor, 0.28f)
        val hx = cx + (progressR * cos(ha)).toFloat()
        val hy = cy + (progressR * sin(ha)).toFloat()
        canvas.drawCircle(hx, hy, 4.5f * density, paint)
        paint.color = progressColor
        canvas.drawCircle(hx, hy, 2.4f * density, paint)

        // Battery arc.
        snapshot.batteryPercent?.let { pct ->
            val br = outer * 0.50f
            arcRect.set(cx - br, cy - br, cx + br, cy + br)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f * density
            paint.color = ColorMath.withAlpha(batteryColor, 0.25f)
            canvas.drawArc(arcRect, 135f, 270f, false, paint)
            paint.color = batteryColor
            canvas.drawArc(arcRect, 135f, 270f * (pct.coerceIn(0, 100) / 100f), false, paint)
        }

        // Next-alarm marker just outside the ticks.
        Chronometer.alarmFraction(now, snapshot.nextAlarmAtMillis)?.let { f ->
            val a = Math.toRadians(f * 360.0 - 90.0)
            val tipR = outer - outer * 0.20f
            val baseR = outer + 1f * density
            val half = Math.toRadians(5.0)
            marker.reset()
            marker.moveTo(cx + (tipR * cos(a)).toFloat(), cy + (tipR * sin(a)).toFloat())
            marker.lineTo(cx + (baseR * cos(a - half)).toFloat(), cy + (baseR * sin(a - half)).toFloat())
            marker.lineTo(cx + (baseR * cos(a + half)).toFloat(), cy + (baseR * sin(a + half)).toFloat())
            marker.close()
            paint.style = Paint.Style.FILL
            paint.color = markerColor
            canvas.drawPath(marker, paint)
        }

        // Hub: the gauge mark's centre.
        paint.style = Paint.Style.FILL
        paint.color = progressColor
        canvas.drawCircle(cx, cy, 2.6f * density, paint)
    }
}
