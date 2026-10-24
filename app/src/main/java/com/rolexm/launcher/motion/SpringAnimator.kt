package com.rolexm.launcher.motion

import android.view.Choreographer

/**
 * Drives one float with [SpringPhysics] on the display's frame clock.
 *
 * Interruption-safe by construction: [animateTo] while running only changes
 * the target, keeping the current value and velocity, so a reversed gesture or
 * a second tap continues smoothly from wherever the motion is. [snapTo] stops
 * immediately; [setVelocity] hands a finger's release speed to the spring.
 *
 * All callbacks run on the main thread. No allocation per frame.
 */
class SpringAnimator(
    initial: Float,
    private val valueThreshold: Float = 0.001f,
    private val onUpdate: (Float) -> Unit
) : Choreographer.FrameCallback {

    var value: Float = initial
        private set
    var velocity: Float = 0f
        private set
    var target: Float = initial
        private set
    var isRunning: Boolean = false
        private set

    private var spec: SpringSpec = MotionTokens.STANDARD
    private var policy: MotionPolicy = MotionPolicy.DEFAULT
    private var lastFrameNanos = 0L
    private var onEnd: (() -> Unit)? = null

    /** Velocity threshold scales with the value threshold. */
    private val velocityThreshold get() = valueThreshold * 60f

    fun animateTo(
        newTarget: Float,
        springSpec: SpringSpec,
        motion: MotionPolicy,
        endAction: (() -> Unit)? = null
    ) {
        target = newTarget
        spec = motion.adapt(springSpec)
        policy = motion
        onEnd = endAction
        // Reduced motion keeps the same state and callbacks but removes large
        // travel. Gesture tracking still works; only the decorative settle is
        // skipped. OFF and reduced therefore cannot strand a surface mid-flight.
        if (!motion.enabled || motion.reduced) {
            snapTo(newTarget)
            return
        }
        if (!isRunning) {
            isRunning = true
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** Hands over a gesture's release velocity (units per second). */
    fun setVelocity(unitsPerSecond: Float) {
        velocity = unitsPerSecond
    }

    /** Jumps to [newValue] without motion and stops; the end action (if any) runs. */
    fun snapTo(newValue: Float) {
        val wasRunning = isRunning
        if (wasRunning) Choreographer.getInstance().removeFrameCallback(this)
        isRunning = false
        value = newValue
        target = newValue
        velocity = 0f
        onUpdate(newValue)
        val end = onEnd
        onEnd = null
        end?.invoke()
    }

    /** Stops where it is (e.g. when a finger catches a moving surface). */
    fun cancel() {
        if (isRunning) Choreographer.getInstance().removeFrameCallback(this)
        isRunning = false
        onEnd = null
    }

    /** Sets the value directly while a gesture is tracking (no motion, no end action). */
    fun track(newValue: Float) {
        cancel()
        value = newValue
        target = newValue
        onUpdate(newValue)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return
        if (lastFrameNanos == 0L) {
            lastFrameNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
            return
        }
        // Cap dt so a stalled frame does not teleport the surface.
        val rawDt = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
        lastFrameNanos = frameTimeNanos
        val dt = if (policy.timeScale > 0f) rawDt / policy.timeScale else rawDt
        val next = SpringPhysics.step(SpringState(value, velocity), target, spec, dt)
        value = next.value
        velocity = next.velocity
        if (SpringPhysics.isAtRest(next, target, valueThreshold, velocityThreshold)) {
            isRunning = false
            value = target
            velocity = 0f
            onUpdate(target)
            val end = onEnd
            onEnd = null
            end?.invoke()
            return
        }
        onUpdate(value)
        Choreographer.getInstance().postFrameCallback(this)
    }
}
