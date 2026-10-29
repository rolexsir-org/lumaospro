package com.lumaos.pro.launcher.motion

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.animation.core.SpringSpec as ComposeSpringSpec
import androidx.compose.animation.core.spring
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import java.util.EnumMap
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * Single Unified Spring Animation Engine for LumaOS Pro.
 *
 * Every transition, press state, drag release, stagger reveal, and layer shift
 * runs on the one analytical damped harmonic oscillator (`damping = 0.78f`,
 * `stiffness = 280f`) driven by `Choreographer` frame callbacks.
 *
 * Zero `ObjectAnimator`, `ValueAnimator`, `ViewPropertyAnimator`, or polynomial
 * easing interpolators are used anywhere in the launcher.
 */
object LumaSpring {

    val SPEC: SpringSpec = MotionTokens.LUMA

    private val mainHandler by lazy(LazyThreadSafetyMode.NONE) { Handler(Looper.getMainLooper()) }
    private val activeViewSprings = WeakHashMap<View, EnumMap<Property, SpringAnimator>>()

    enum class Property(val tolerance: Float) {
        TRANSLATION_X(0.5f),
        TRANSLATION_Y(0.5f),
        SCALE_X(0.002f),
        SCALE_Y(0.002f),
        SCALE(0.002f),
        ALPHA(0.005f),
        ROTATION(0.1f);

        fun get(view: View): Float = when (this) {
            TRANSLATION_X -> view.translationX
            TRANSLATION_Y -> view.translationY
            SCALE_X -> view.scaleX
            SCALE_Y -> view.scaleY
            SCALE -> view.scaleX
            ALPHA -> view.alpha
            ROTATION -> view.rotation
        }

        fun set(view: View, value: Float) {
            when (this) {
                TRANSLATION_X -> view.translationX = value
                TRANSLATION_Y -> view.translationY = value
                SCALE_X -> view.scaleX = value
                SCALE_Y -> view.scaleY = value
                SCALE -> {
                    view.scaleX = value
                    view.scaleY = value
                }
                ALPHA -> view.alpha = value.coerceIn(0f, 1f)
                ROTATION -> view.rotation = value
            }
        }
    }

    /**
     * Animates [property] on [view] toward [targetValue] using the unified
     * `0.78f / 280f` spring. Calling again mid-flight seamlessly retargets
     * without resetting instantaneous velocity.
     */
    fun animate(
        view: View,
        property: Property,
        targetValue: Float,
        startVelocity: Float = 0f,
        motion: MotionPolicy = MotionPolicy.DEFAULT,
        onUpdate: ((Float) -> Unit)? = null,
        onEnd: (() -> Unit)? = null
    ): SpringAnimator {
        if (!motion.enabled || (motion.reduced && property != Property.ALPHA)) {
            cancel(view, property)
            property.set(view, targetValue)
            onUpdate?.invoke(targetValue)
            onEnd?.invoke()
            return SpringAnimator(targetValue, property.tolerance) {}
        }

        val map = activeViewSprings.getOrPut(view) { EnumMap(Property::class.java) }
        val animator = map[property] ?: SpringAnimator(
            initial = property.get(view),
            valueThreshold = property.tolerance
        ) { v ->
            property.set(view, v)
            onUpdate?.invoke(v)
        }.also { map[property] = it }

        if (!animator.isRunning) {
            animator.snapTo(property.get(view))
        }
        if (startVelocity != 0f) {
            animator.setVelocity(startVelocity)
        }

        val effectiveSpec = if (motion.reduced) {
            // Critically damped ~200ms gentle alpha crossfade for accessibility reduce-motion.
            SpringSpec(stiffness = 320f, dampingRatio = 1.0f)
        } else {
            SPEC
        }
        val effectivePolicy = if (motion.reduced) MotionPolicy.DEFAULT else motion

        animator.animateTo(
            newTarget = targetValue,
            springSpec = effectiveSpec,
            motion = effectivePolicy,
            endAction = onEnd
        )
        return animator
    }

    /** Springs [view] to `(targetX, targetY)` simultaneously with optional release velocities. */
    fun animateToPosition(
        view: View,
        targetX: Float,
        targetY: Float,
        velocityX: Float = 0f,
        velocityY: Float = 0f,
        motion: MotionPolicy = MotionPolicy.DEFAULT,
        onEnd: (() -> Unit)? = null
    ) {
        var remaining = 2
        val checkDone = {
            remaining--
            if (remaining == 0) onEnd?.invoke()
        }
        animate(view, Property.TRANSLATION_X, targetX, velocityX, motion, onEnd = checkDone)
        animate(view, Property.TRANSLATION_Y, targetY, velocityY, motion, onEnd = checkDone)
    }

    /**
     * Carries gesture release [velocity] (px/s or normalized/s) smoothly into the
     * unified spring, settling onto the projected rest target within `[min, max]`
     * (or the closest entry in [snapTargets] when supplied).
     */
    fun flingToRest(
        view: View,
        property: Property,
        velocity: Float,
        min: Float,
        max: Float,
        snapTargets: FloatArray? = null,
        motion: MotionPolicy = MotionPolicy.DEFAULT,
        onUpdate: ((Float) -> Unit)? = null,
        onEnd: (() -> Unit)? = null
    ): Float {
        val current = property.get(view)
        val projected = (current + velocity * 0.18f).coerceIn(min, max)
        val destination = if (snapTargets != null && snapTargets.isNotEmpty()) {
            snapTargets.minByOrNull { abs(it - projected) }?.coerceIn(min, max) ?: projected
        } else {
            projected
        }
        animate(
            view = view,
            property = property,
            targetValue = destination,
            startVelocity = velocity,
            motion = motion,
            onUpdate = onUpdate,
            onEnd = onEnd
        )
        return destination
    }

    /** Pure scalar spring driver (`0.78f / 280f`) for sheets, overlays, and depth stacks. */
    fun scalar(
        initial: Float = 0f,
        tolerance: Float = 0.001f,
        onUpdate: (Float) -> Unit
    ): SpringAnimator = SpringAnimator(initial, tolerance, onUpdate)

    /** Pure rest-target resolver used by gesture controllers (`0f` closed vs `1f` open). */
    fun resolveBinarySnapTarget(
        progress: Float,
        velocityPerSec: Float,
        openThreshold: Float = 0.40f,
        flingVelocityThreshold: Float = 1.15f
    ): Float = when {
        velocityPerSec > flingVelocityThreshold -> 1f
        velocityPerSec < -flingVelocityThreshold -> 0f
        progress > openThreshold -> 1f
        else -> 0f
    }

    /** Stops an in-flight spring on `(view, property)` at its exact instantaneous position. */
    fun cancel(view: View, property: Property) {
        activeViewSprings[view]?.get(property)?.cancel()
    }

    /** Stops all in-flight springs on [view] so a mid-animation touch grab takes immediate control. */
    fun cancelAll(view: View) {
        activeViewSprings[view]?.values?.forEach { it.cancel() }
    }

    /**
     * Staggers a list of [views] from `90% -> 100%` scale and `0% -> 100%` alpha
     * with `15ms` (`LumaTokens.staggerDelayMs`) delay per item. Returns a cancel
     * handle that immediately restores clean resting state if interrupted.
     */
    fun staggerReveal(
        views: List<View>,
        motion: MotionPolicy = MotionPolicy.DEFAULT,
        delayStepMs: Long = LumaTokens.staggerDelayMs,
        maxStaggeredItems: Int = 24
    ): Runnable {
        if (views.isEmpty() || !motion.enabled) {
            views.forEach { v ->
                v.alpha = 1f
                v.scaleX = 1f
                v.scaleY = 1f
            }
            return Runnable {}
        }
        if (motion.reduced) {
            views.forEach { v ->
                v.scaleX = 1f
                v.scaleY = 1f
                v.alpha = 0f
                animate(v, Property.ALPHA, 1f, motion = motion)
            }
            return Runnable {
                views.forEach { it.alpha = 1f }
            }
        }

        val tasks = ArrayList<Runnable>(views.size)
        val capped = views.take(maxStaggeredItems)
        val remainder = views.drop(maxStaggeredItems)
        remainder.forEach { v ->
            v.alpha = 1f
            v.scaleX = 1f
            v.scaleY = 1f
        }
        capped.forEachIndexed { index, v ->
            cancelAll(v)
            v.scaleX = LumaTokens.staggerInitialScale
            v.scaleY = LumaTokens.staggerInitialScale
            v.alpha = 0f
            val task = Runnable {
                if (v.isAttachedToWindow) {
                    animate(v, Property.SCALE, 1f, motion = motion)
                    animate(v, Property.ALPHA, 1f, motion = motion)
                } else {
                    v.scaleX = 1f
                    v.scaleY = 1f
                    v.alpha = 1f
                }
            }
            tasks += task
            if (index == 0 || delayStepMs <= 0L) {
                task.run()
            } else {
                mainHandler.postDelayed(task, index * delayStepMs)
            }
        }

        return Runnable {
            tasks.forEach { mainHandler.removeCallbacks(it) }
            capped.forEach { v ->
                cancelAll(v)
                v.scaleX = 1f
                v.scaleY = 1f
                v.alpha = 1f
            }
        }
    }

    /** Convenience stagger reveal for visible children of a [RecyclerView]. */
    fun staggerRecyclerView(
        recyclerView: RecyclerView,
        motion: MotionPolicy = MotionPolicy.DEFAULT,
        delayStepMs: Long = LumaTokens.staggerDelayMs
    ): Runnable {
        val visible = ArrayList<View>(recyclerView.childCount)
        for (i in 0 until recyclerView.childCount) {
            recyclerView.getChildAt(i)?.let { visible += it }
        }
        return staggerReveal(visible, motion, delayStepMs)
    }

    /** Compose spring spec backed by the exact same `0.78f / 280f` LumaSpring constants. */
    fun <T> composeSpec(visibilityThreshold: T? = null): ComposeSpringSpec<T> = spring(
        dampingRatio = LumaTokens.springDamping,
        stiffness = LumaTokens.springStiffness,
        visibilityThreshold = visibilityThreshold
    )

    /**
     * Spring-only [RecyclerView.ItemAnimator] replacing Android's polynomial
     * `DefaultItemAnimator` so icon/widget reorders, additions, and removals
     * all run on the unified `0.78f / 280f` spring.
     */
    class ItemAnimator(
        private val motionProvider: () -> MotionPolicy = { MotionPolicy.DEFAULT }
    ) : SimpleItemAnimator() {

        private val trackedHolders = HashSet<RecyclerView.ViewHolder>()

        init {
            supportsChangeAnimations = false
        }

        override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
            val motion = motionProvider()
            if (!motion.enabled) {
                dispatchRemoveFinished(holder)
                return false
            }
            val view = holder.itemView
            trackedHolders += holder
            dispatchRemoveStarting(holder)
            animate(view, Property.SCALE, LumaTokens.staggerInitialScale, motion = motion)
            animate(view, Property.ALPHA, 0f, motion = motion) {
                view.scaleX = 1f
                view.scaleY = 1f
                view.alpha = 1f
                trackedHolders -= holder
                dispatchRemoveFinished(holder)
                dispatchFinishedWhenDone()
            }
            return true
        }

        override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
            val motion = motionProvider()
            if (!motion.enabled) {
                dispatchAddFinished(holder)
                return false
            }
            val view = holder.itemView
            view.scaleX = if (motion.reduced) 1f else LumaTokens.staggerInitialScale
            view.scaleY = if (motion.reduced) 1f else LumaTokens.staggerInitialScale
            view.alpha = 0f
            trackedHolders += holder
            dispatchAddStarting(holder)
            animate(view, Property.SCALE, 1f, motion = motion)
            animate(view, Property.ALPHA, 1f, motion = motion) {
                trackedHolders -= holder
                dispatchAddFinished(holder)
                dispatchFinishedWhenDone()
            }
            return true
        }

        override fun animateMove(
            holder: RecyclerView.ViewHolder,
            fromX: Int,
            fromY: Int,
            toX: Int,
            toY: Int
        ): Boolean {
            val view = holder.itemView
            val dx = (fromX + view.translationX.toInt()) - toX
            val dy = (fromY + view.translationY.toInt()) - toY
            if (dx == 0 && dy == 0) {
                dispatchMoveFinished(holder)
                return false
            }
            val motion = motionProvider()
            if (!motion.enabled || motion.reduced) {
                view.translationX = 0f
                view.translationY = 0f
                dispatchMoveFinished(holder)
                return false
            }
            cancel(view, Property.TRANSLATION_X)
            cancel(view, Property.TRANSLATION_Y)
            view.translationX = dx.toFloat()
            view.translationY = dy.toFloat()
            trackedHolders += holder
            dispatchMoveStarting(holder)
            animateToPosition(view, 0f, 0f, motion = motion) {
                trackedHolders -= holder
                dispatchMoveFinished(holder)
                dispatchFinishedWhenDone()
            }
            return true
        }

        override fun animateChange(
            oldHolder: RecyclerView.ViewHolder,
            newHolder: RecyclerView.ViewHolder,
            fromLeft: Int,
            fromTop: Int,
            toLeft: Int,
            toTop: Int
        ): Boolean {
            if (oldHolder === newHolder) {
                return animateMove(oldHolder, fromLeft, fromTop, toLeft, toTop)
            }
            dispatchChangeFinished(oldHolder, true)
            dispatchChangeFinished(newHolder, false)
            return false
        }

        override fun runPendingAnimations() = Unit

        override fun endAnimation(item: RecyclerView.ViewHolder) {
            val v = item.itemView
            cancelAll(v)
            v.translationX = 0f
            v.translationY = 0f
            v.scaleX = 1f
            v.scaleY = 1f
            v.alpha = 1f
            if (trackedHolders.remove(item)) {
                dispatchAnimationFinished(item)
                dispatchFinishedWhenDone()
            }
        }

        override fun endAnimations() {
            val copy = trackedHolders.toList()
            trackedHolders.clear()
            for (holder in copy) {
                val v = holder.itemView
                cancelAll(v)
                v.translationX = 0f
                v.translationY = 0f
                v.scaleX = 1f
                v.scaleY = 1f
                v.alpha = 1f
                dispatchAnimationFinished(holder)
            }
            dispatchAnimationsFinished()
        }

        override fun isRunning(): Boolean = trackedHolders.isNotEmpty()

        private fun dispatchFinishedWhenDone() {
            if (!isRunning) dispatchAnimationsFinished()
        }
    }
}
