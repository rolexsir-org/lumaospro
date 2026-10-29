package com.lumaos.pro.launcher.launcher

import com.lumaos.pro.launcher.ui.theme.LumaTokens
import com.lumaos.pro.launcher.ui.theme.LumaTypeScale
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Responsive geometry for home-grid and dock cells backed by [LumaTokens].
 *
 * Uses the universal 24dp horizontal screen margins (`48dp` total gutter),
 * `56dp` default app icons (`LumaTokens.iconApp`), `6dp` icon-to-label gap,
 * `11sp` single-line app labels, and full `56dp` unlabelled dock icons while
 * shrinking gracefully on cramped viewports or up to 2.0x system font scales.
 */
data class HomeCellMetrics(
    val iconDp: Int,
    val dockIconDp: Int,
    val cellHeightDp: Int,
    val labelSp: Float
)

object HomeCellSizing {

    const val MIN_ICON_DP = 32
    const val MAX_ICON_DP = 72
    private const val HORIZONTAL_CHROME_DP = 14
    private const val VERTICAL_CHROME_DP = 12
    private const val DOCK_SLOT_CHROME_DP = 8

    fun compute(
        widthDp: Int,
        gridAreaHeightDp: Int,
        columns: Int,
        rows: Int,
        iconPrefDp: Int,
        showLabels: Boolean,
        textScale: Float
    ): HomeCellMetrics {
        val cols = columns.coerceAtLeast(1)
        val r = rows.coerceAtLeast(1)
        val scale = textScale.coerceIn(0.85f, 2.0f)
        val pref = iconPrefDp.coerceIn(MIN_ICON_DP, MAX_ICON_DP)

        val gutterDp = (LumaTokens.screenMarginHorizontal * 2f).roundToInt()
        val cellWidthDp = ((widthDp - gutterDp).coerceAtLeast(160)) / cols
        val maxIconByWidth = (cellWidthDp - HORIZONTAL_CHROME_DP).coerceAtLeast(MIN_ICON_DP)

        val labelSp = LumaTypeScale.appLabelSp * scale
        val labelBlockDp = if (showLabels) {
            ceil(labelSp * 1.35f).toInt() + LumaTokens.gridIconLabelGap.roundToInt()
        } else {
            0
        }

        val rowBudgetDp = (gridAreaHeightDp.coerceAtLeast(r * 52)) / r
        val maxIconByHeight = (rowBudgetDp - VERTICAL_CHROME_DP - labelBlockDp).coerceAtLeast(MIN_ICON_DP)

        val iconDp = min(pref, min(maxIconByWidth, maxIconByHeight))
            .coerceIn(MIN_ICON_DP, MAX_ICON_DP)

        val naturalHeight = iconDp + labelBlockDp + VERTICAL_CHROME_DP
        val cellHeightDp = naturalHeight.coerceAtMost(rowBudgetDp.coerceAtLeast(naturalHeight))

        val dockInnerWidthDp = (widthDp - gutterDp - (LumaTokens.dockInternalPaddingH * 2f).roundToInt())
            .coerceAtLeast(140)
        val maxDockByWidth = (dockInnerWidthDp / LumaTokens.dockIconCount - DOCK_SLOT_CHROME_DP)
            .coerceAtLeast(MIN_ICON_DP)
        val dockIconDp = min(iconDp, maxDockByWidth).coerceIn(MIN_ICON_DP, LumaTokens.iconAppInt.coerceAtLeast(iconDp))

        return HomeCellMetrics(
            iconDp = iconDp,
            dockIconDp = dockIconDp,
            cellHeightDp = cellHeightDp,
            labelSp = labelSp
        )
    }
}

/**
 * Gives the configured number of home-grid rows the available viewport height,
 * respecting [LumaTokens.gridRowMinHeight] (`>= 84dp` at baseline).
 */
internal fun homeCellHeightPx(availableHeightPx: Int, rows: Int, minimumHeightPx: Int): Int {
    val safeRows = rows.coerceIn(1, 8)
    return (availableHeightPx.coerceAtLeast(0) / safeRows).coerceAtLeast(minimumHeightPx.coerceAtLeast(0))
}

/** Scales an icon to its grid cell when a large icon/column combination cannot fit. */
internal fun responsiveIconSizePx(
    preferredPx: Int,
    gridWidthPx: Int,
    horizontalInsetsPx: Int,
    columns: Int,
    cellGutterPx: Int,
    minimumPx: Int
): Int {
    if (gridWidthPx <= 0) return preferredPx.coerceAtLeast(0)
    val cellWidth = (gridWidthPx - horizontalInsetsPx).coerceAtLeast(0) / columns.coerceAtLeast(1)
    val available = (cellWidth - cellGutterPx).coerceAtLeast(minimumPx.coerceAtLeast(0))
    return preferredPx.coerceAtLeast(0).coerceAtMost(available)
}
