package com.lumaos.pro.launcher.launcher.model

/** Projects the existing model into one grid without losing curated ordering. */
object ObsidianProjection {
    fun project(output: DrawerModel.Output): DrawerModel.Output {
        if (output.query.isNotBlank()) {
            val rows = output.rows.filter { it is DrawerRow.App || it is DrawerRow.Shortcut }
                .distinctBy { it.stableKey }
            return output.copy(rows = rows, buckets = emptyList(),
                appCount = rows.count { it is DrawerRow.App })
        }

        // Only the all-apps section determines eligibility. Curated sections can
        // influence order, but never resurrect hidden, removed or filtered apps.
        val all = output.rows.filterIsInstance<DrawerRow.App>()
            .filter { it.section == DrawerModel.SECTION_ALL }
            .associateBy { it.entry.key }
        val seen = HashSet<com.lumaos.pro.launcher.core.model.ComponentKey>()
        val rows = output.rows.filterIsInstance<DrawerRow.App>().mapNotNull { row ->
            val eligible = all[row.entry.key] ?: return@mapNotNull null
            if (seen.add(row.entry.key)) eligible else null
        }
        return output.copy(rows = rows, buckets = emptyList(), appCount = rows.size)
    }
}
