package com.lumaos.pro.launcher.widgets

import java.util.Locale

/** Cached labels for one real provider; [provider] remains the platform object. */
data class WidgetPickerEntry<T>(
    val provider: T,
    val packageName: String,
    val appLabel: String,
    val widgetLabel: String
)

data class WidgetPickerGroup<T>(val packageName: String, val appLabel: String, val entries: List<WidgetPickerEntry<T>>)

/** Search and grouping are independent of Android's provider and preview APIs. */
object WidgetPickerModel {
    fun <T> groups(entries: List<WidgetPickerEntry<T>>, query: String): List<WidgetPickerGroup<T>> {
        val locale = Locale.getDefault()
        val needle = query.trim().lowercase(locale)
        return entries.asSequence()
            .filter {
                needle.isEmpty() || it.widgetLabel.lowercase(locale).contains(needle) ||
                    it.appLabel.lowercase(locale).contains(needle)
            }
            .groupBy { it.packageName }
            .map { (pkg, items) -> WidgetPickerGroup(pkg, items.first().appLabel, items) }
            // Package is a tie-breaker: two different apps may have the same label.
            .sortedWith(compareBy<WidgetPickerGroup<T>>({ it.appLabel.lowercase(locale) }, { it.packageName }))
    }
}
