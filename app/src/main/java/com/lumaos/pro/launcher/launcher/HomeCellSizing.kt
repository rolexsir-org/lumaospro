package com.lumaos.pro.launcher.launcher

/**
 * Gives the configured number of home-grid rows the available viewport height.
 * Content beyond the viewport remains reachable through RecyclerView scrolling.
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
