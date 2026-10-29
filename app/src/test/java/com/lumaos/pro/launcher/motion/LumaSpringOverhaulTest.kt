package com.lumaos.pro.launcher.motion

import com.lumaos.pro.launcher.ui.theme.LumaTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class LumaSpringOverhaulTest {

    @Test
    fun unifiedSpringMatchesExactTokenParametersAndSettlesCleanly() {
        assertEquals(0.78f, LumaSpring.SPEC.dampingRatio, 0.0001f)
        assertEquals(280f, LumaSpring.SPEC.stiffness, 0.0001f)
        assertEquals(LumaTokens.springDamping, MotionTokens.LUMA.dampingRatio, 0.0001f)
        assertEquals(LumaTokens.springStiffness, MotionTokens.LUMA.stiffness, 0.0001f)

        var state = SpringState(0f, 0f)
        var peak = 0f
        repeat(180) {
            state = SpringPhysics.step(state, 1f, LumaSpring.SPEC, 1f / 60f)
            if (state.value > peak) peak = state.value
        }
        assertEquals(1f, state.value, 0.002f)
        assertTrue(abs(state.velocity) < 0.02f)
        // Slight organic overshoot (damping = 0.78 < 1.0), never bouncy (>1.08)
        assertTrue("Expected subtle organic settle, peak=$peak", peak > 1.0f && peak < 1.06f)
    }

    @Test
    fun midFlightInterruptionPreservesPositionAndVelocity() {
        var state = SpringState(0f, 0f)
        repeat(8) {
            state = SpringPhysics.step(state, 1f, LumaSpring.SPEC, 1f / 60f)
        }
        val midPos = state.value
        val midVel = state.velocity
        assertTrue(midPos in 0.1f..0.95f)
        assertTrue(midVel > 0f)

        // Retarget mid-flight back to 0f: instantaneous position and momentum carry smoothly.
        val next = SpringPhysics.step(state, 0f, LumaSpring.SPEC, 1f / 1000f)
        assertEquals(midPos, next.value, 0.01f)
        assertEquals(midVel, next.velocity, 0.5f)
    }

    @Test
    fun gestureBinarySnapRespectsMomentumAndDistance() {
        assertEquals(1f, LumaSpring.resolveBinarySnapTarget(progress = 0.15f, velocityPerSec = 2.4f), 0.001f)
        assertEquals(0f, LumaSpring.resolveBinarySnapTarget(progress = 0.85f, velocityPerSec = -2.4f), 0.001f)
        assertEquals(1f, LumaSpring.resolveBinarySnapTarget(progress = 0.55f, velocityPerSec = 0.1f), 0.001f)
        assertEquals(0f, LumaSpring.resolveBinarySnapTarget(progress = 0.25f, velocityPerSec = -0.1f), 0.001f)
    }

    @Test
    fun codebaseContainsZeroNonSpringAnimatorsOrInterpolators() {
        val srcRoot = File("src/main/java")
        assertTrue("Expected src/main/java at ${srcRoot.absolutePath}", srcRoot.isDirectory)
        val forbidden = listOf(
            Regex("\\bObjectAnimator\\b"),
            Regex("\\bValueAnimator\\b"),
            Regex("\\bAnimatorSet\\b"),
            Regex("\\.animate\\(\\)"),
            Regex("\\bLinearInterpolator\\b"),
            Regex("\\bAccelerateDecelerateInterpolator\\b"),
            Regex("\\bDecelerateInterpolator\\b"),
            Regex("\\bOvershootInterpolator\\b"),
            Regex("\\bTransitionManager\\b"),
            Regex("\\boverridePendingTransition\\b")
        )
        val violations = mutableListOf<String>()
        srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "LumaSpring.kt" }
            .forEach { file ->
                val codeLines = file.readLines()
                    .filterNot { line ->
                        val t = line.trimStart()
                        t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
                    }
                    .joinToString("\n")
                for (pattern in forbidden) {
                    if (pattern.containsMatchIn(codeLines)) {
                        violations += "${file.relativeTo(srcRoot)}: matched ${pattern.pattern}"
                    }
                }
            }
        assertTrue("Found non-spring animation APIs: $violations", violations.isEmpty())
    }
}
