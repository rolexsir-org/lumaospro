package com.lumaos.pro.launcher.launcher.model

/** A single app grid over the existing discovery, visibility and sort model. */
object ObsidianProjection {
    fun project(output: DrawerModel.Output): DrawerModel.Output {
        val section = if (output.query.isBlank()) DrawerModel.SECTION_ALL else DrawerModel.SECTION_RESULTS
        val apps = output.rows.filterIsInstance<DrawerRow.App>().filter { it.section == section }
        return output.copy(rows = apps, buckets = emptyList(), appCount = apps.size)
    }
}
