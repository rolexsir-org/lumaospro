package com.lumaos.pro.launcher.launcher.model

/** Presentation-only state; it does not change the source list or persisted settings. */
enum class ObsidianPage { OVERVIEW, LIBRARY }

/**
 * Splits the existing drawer output without recomputing discovery, filtering,
 * ranking or permissions. Search and collection filters always show their real
 * complete results. Bucket offsets are rebased for the library projection.
 */
object ObsidianProjection {
    fun project(output: DrawerModel.Output, page: ObsidianPage): DrawerModel.Output {
        if (output.query.isNotBlank() || output.filter != DrawerFilter.All) return output
        val boundary = output.rows.indexOfFirst { row ->
            (row is DrawerRow.Header && row.id == DrawerModel.SECTION_ALL) ||
                (row is DrawerRow.App && row.section == DrawerModel.SECTION_ALL)
        }.let { if (it < 0) output.rows.size else it }
        return when (page) {
            ObsidianPage.OVERVIEW -> output.copy(rows = output.rows.take(boundary), buckets = emptyList())
            ObsidianPage.LIBRARY -> output.copy(
                rows = output.rows.drop(boundary),
                buckets = output.buckets.mapNotNull { (letter, position) ->
                    if (position >= boundary) letter to position - boundary else null
                }
            )
        }
    }
}
