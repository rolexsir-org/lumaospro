package com.lumaos.pro.launcher.ui.theme

/** Opaque preview-card gradient and readable text for an authored theme. */
data class ThemeCardPalette(
    val background: Int,
    val middle: Int,
    val end: Int,
    val content: Int,
    /** Semi-transparent text, safe against all three gradient stops. */
    val muted: Int
) {
    companion object {
        fun forSpec(spec: ThemeSpec): ThemeCardPalette {
            val background = ColorMath.opaque(spec.background)
            val middle = ColorMath.composite(ColorMath.withAlpha(spec.secondary, 0.10f), background)
            val end = ColorMath.composite(ColorMath.withAlpha(spec.accent, 0.08f), background)
            val stops = listOf(background, middle, end)
            fun minimumContrast(color: Int): Double = stops.minOf { ColorMath.contrast(color, it) }
            val content = if (minimumContrast(ColorMath.WHITE) >= minimumContrast(ColorMath.BLACK)) {
                ColorMath.WHITE
            } else ColorMath.BLACK
            val subdued = ColorMath.withAlpha(content, 0.82f)
            val muted = if (stops.all { ColorMath.contrast(ColorMath.composite(subdued, it), it) >= 4.5 }) {
                subdued
            } else content
            return ThemeCardPalette(background, middle, end, content, muted)
        }
    }
}
