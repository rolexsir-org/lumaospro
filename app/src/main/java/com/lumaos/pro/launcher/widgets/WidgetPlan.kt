package com.lumaos.pro.launcher.widgets

import com.lumaos.pro.launcher.launcher.model.HomeItem
import com.lumaos.pro.launcher.launcher.model.HomeLayout
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure-Kotlin widget planning: provider capability interpretation, row
 * constraints and home-screen insertion decisions.
 *
 * Everything here is deliberately free of Android imports so the rules are
 * exercised by JVM unit tests. The Android side ([WidgetHostController])
 * translates `AppWidgetProviderInfo` into [ProviderFacts] and consumes the
 * [WidgetCapabilities] produced here.
 *
 * Home-screen geometry in LumaOS Pro is a vertical feed in which every widget
 * spans the full grid width and occupies an integer number of grid rows, so
 * "collision" is expressed as widget count and vertical-row budgets rather
 * than 2-D cell overlap. The budgets keep a page usable and scrollable; a
 * placement that would exceed them is refused instead of silently overwriting
 * or displacing existing items.
 */

/** Platform resize flags, mirrored from `AppWidgetProviderInfo` so this file stays Android-free. */
object WidgetResizeFlags {
    const val NONE = 0
    const val HORIZONTAL = 1
    const val VERTICAL = 2
    const val BOTH = 3

    /** `AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE` (API 28). */
    const val FEATURE_RECONFIGURABLE = 1
}

/**
 * The provider facts a sizing decision needs. Pixel fields that do not exist
 * on the running API level are passed as 0.
 */
data class ProviderFacts(
    val resizeMode: Int,
    val minHeightPx: Int,
    /** API 29+; 0 when unavailable. */
    val minResizeHeightPx: Int,
    /** API 31+; 0 when unavailable. */
    val maxResizeHeightPx: Int,
    /** API 31+ target height in grid cells; 0 when unavailable. */
    val targetCellHeight: Int,
    /**
     * Optional launcher-side minimum height in grid cells; 0 when unavailable.
     * The public Android SDK has no `minCellHeight` platform field: providers
     * express their minimum through [minResizeHeightPx] and [targetCellHeight].
     */
    val minCellHeight: Int,
    val hasConfigure: Boolean,
    /** API 28+ feature bitfield; 0 below API 28. */
    val widgetFeatures: Int,
    /** Current home-grid row height in pixels. */
    val rowHeightPx: Int
)

/** What the launcher is allowed to do with one provider. */
data class WidgetCapabilities(
    val resizableVertical: Boolean,
    val resizableHorizontal: Boolean,
    /** The provider ships a configuration activity. */
    val configurable: Boolean,
    /** An already-bound instance can be re-configured from its menu (API 28+). */
    val reconfigurable: Boolean,
    /** Smallest legal height in home-grid rows, honouring the provider's minimum. */
    val minRows: Int,
    /** Largest legal height in home-grid rows, honouring the provider's maximum. */
    val maxRows: Int,
    /** Recommended first placement height, in rows. */
    val initialRows: Int
) {
    /** Fixed-height widgets expose no resize affordance at all. */
    val resizable: Boolean get() = resizableVertical || resizableHorizontal
}

sealed interface InsertionPlan {
    data class AtIndex(val index: Int) : InsertionPlan
    data object AlreadyPresent : InsertionPlan
    data object NoSpace : InsertionPlan
}

object WidgetPlan {

    /** Widgets per home page beyond which the feed becomes unmanageable. */
    const val MAX_WIDGETS_PER_PAGE = 6

    /** Combined widget row budget on one page, so app icons keep room above the fold. */
    const val MAX_WIDGET_ROWS_PER_PAGE = 12

    private fun rowsForHeightPx(heightPx: Int, rowHeightPx: Int): Int {
        if (heightPx <= 0) return HomeLayout.MIN_WIDGET_ROWS
        if (rowHeightPx <= 0) return HomeLayout.MIN_WIDGET_ROWS
        return max(1, ceil(heightPx / rowHeightPx.toDouble()).toInt())
    }

    /** Interprets raw provider facts into the launcher's row-based capabilities. */
    fun capabilities(facts: ProviderFacts): WidgetCapabilities {
        val resizableVertical = (facts.resizeMode and WidgetResizeFlags.VERTICAL) != 0
        val resizableHorizontal = (facts.resizeMode and WidgetResizeFlags.HORIZONTAL) != 0

        // The provider's hard minimum always applies, resize support or not.
        val baselineRows = rowsForHeightPx(facts.minHeightPx, facts.rowHeightPx)
            .coerceIn(HomeLayout.MIN_WIDGET_ROWS, HomeLayout.MAX_WIDGET_ROWS)

        val minFromPixels = when {
            resizableVertical && facts.minResizeHeightPx > 0 ->
                rowsForHeightPx(facts.minResizeHeightPx, facts.rowHeightPx)
            else -> baselineRows
        }
        // API 31 providers state their minimum directly in cells, which wins
        // over a pixel conversion when both are available.
        val minRows = max(minFromPixels, facts.minCellHeight)
            .coerceIn(HomeLayout.MIN_WIDGET_ROWS, HomeLayout.MAX_WIDGET_ROWS)

        val maxRows = when {
            // A fixed-height widget has exactly one legal size: its own.
            !resizableVertical -> minRows
            facts.maxResizeHeightPx > 0 ->
                rowsForHeightPx(facts.maxResizeHeightPx, facts.rowHeightPx)
            else -> HomeLayout.MAX_WIDGET_ROWS
        }.coerceIn(minRows, HomeLayout.MAX_WIDGET_ROWS)

        val initialRows = when {
            facts.targetCellHeight > 0 -> facts.targetCellHeight
            facts.minCellHeight > 0 -> facts.minCellHeight
            else -> baselineRows
        }.coerceIn(minRows, maxRows)

        val reconfigurable = facts.hasConfigure &&
            (facts.widgetFeatures and WidgetResizeFlags.FEATURE_RECONFIGURABLE) != 0

        return WidgetCapabilities(
            resizableVertical = resizableVertical,
            resizableHorizontal = resizableHorizontal,
            configurable = facts.hasConfigure,
            reconfigurable = reconfigurable,
            minRows = minRows,
            maxRows = maxRows,
            initialRows = initialRows
        )
    }

    /** Clamps a requested row height into the provider's legal range. */
    fun clampRows(rows: Int, capabilities: WidgetCapabilities): Int =
        rows.coerceIn(capabilities.minRows, capabilities.maxRows)

    /** Total rows currently occupied by widgets on [page]. */
    fun widgetRows(page: List<HomeItem>): Int =
        page.filterIsInstance<HomeItem.Widget>().sumOf { it.rows }

    private fun widgetCount(page: List<HomeItem>): Int =
        page.count { it is HomeItem.Widget }

    /**
     * Decides where a new widget may be inserted.
     *
     * The linear feed cannot overlap existing items, so the only failure
     * modes are the duplicate-id guard and the page budgets. New widgets lead
     * the page (index 0) where the result is visible immediately and app icons
     * keep their relative order underneath.
     */
    fun planInsertion(page: List<HomeItem>, widget: HomeItem.Widget): InsertionPlan {
        if (page.any { it is HomeItem.Widget && it.appWidgetId == widget.appWidgetId }) {
            return InsertionPlan.AlreadyPresent
        }
        if (widgetCount(page) >= MAX_WIDGETS_PER_PAGE) return InsertionPlan.NoSpace
        if (widgetRows(page) + widget.rows > MAX_WIDGET_ROWS_PER_PAGE) return InsertionPlan.NoSpace
        return InsertionPlan.AtIndex(0)
    }

    /** True when [widget] fits the remaining page budget without reordering anything. */
    fun canFit(page: List<HomeItem>, widget: HomeItem.Widget): Boolean =
        planInsertion(page, widget) is InsertionPlan.AtIndex
}

/** Outcome of an edit-mode widget resize gesture. */
sealed interface WidgetResizeResult {
    data object OpenMenu : WidgetResizeResult
    data class Commit(val rows: Int) : WidgetResizeResult
    data class Cancel(val originalHeight: Int) : WidgetResizeResult
}

/** Tracks previews separately from committed rows, including cancellation. */
class WidgetResizeGesture(
    private val startY: Float,
    private val originalHeight: Int,
    private val rowHeight: Int,
    private val minRows: Int,
    private val maxRows: Int,
    private val touchSlop: Int
) {
    init {
        require(rowHeight > 0 && minRows > 0 && maxRows >= minRows)
    }

    var previewHeight: Int = originalHeight
        private set
    var dragged: Boolean = false
        private set

    fun move(y: Float): Int {
        val delta = y - startY
        if (kotlin.math.abs(delta) > touchSlop) dragged = true
        if (dragged) {
            previewHeight = (originalHeight + delta).toInt()
                .coerceIn(rowHeight * minRows, rowHeight * maxRows)
        }
        return previewHeight
    }

    fun finish(cancelled: Boolean): WidgetResizeResult = when {
        cancelled -> WidgetResizeResult.Cancel(originalHeight)
        !dragged -> WidgetResizeResult.OpenMenu
        else -> WidgetResizeResult.Commit(
            (previewHeight / rowHeight.toFloat()).roundToInt().coerceIn(minRows, maxRows)
        )
    }
}
