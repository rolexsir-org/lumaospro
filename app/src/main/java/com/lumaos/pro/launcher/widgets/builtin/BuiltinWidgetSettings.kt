package com.lumaos.pro.launcher.widgets.builtin

import android.content.Context

internal data class WeatherPlace(val latitude: Double, val longitude: Double) {
    init { require(latitude in -90.0..90.0 && longitude in -180.0..180.0) }

    companion object {
        fun parse(latitude: String, longitude: String): WeatherPlace? {
            val lat = latitude.trim().replace(',', '.').toDoubleOrNull() ?: return null
            val lon = longitude.trim().replace(',', '.').toDoubleOrNull() ?: return null
            return if (lat in -90.0..90.0 && lon in -180.0..180.0) WeatherPlace(lat, lon) else null
        }
    }
}

internal object BuiltinWidgetSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("builtin_widgets", Context.MODE_PRIVATE)

    fun radius(context: Context, id: Int): Float? = prefs(context).getInt("$id.radius", -1)
        .takeIf { it in 2..38 }?.toFloat()

    fun place(context: Context, id: Int): WeatherPlace? = prefs(context).let {
        WeatherPlace.parse(it.getString("$id.lat", "").orEmpty(), it.getString("$id.lon", "").orEmpty())
    }

    fun save(context: Context, id: Int, radius: Int, place: WeatherPlace?) {
        prefs(context).edit().apply {
            putInt("$id.radius", radius)
            if (place != null) {
                putString("$id.lat", place.latitude.toString())
                putString("$id.lon", place.longitude.toString())
            }
        }.apply()
    }

    fun delete(context: Context, ids: IntArray) {
        prefs(context).edit().apply {
            for (id in ids) for (key in keys) remove("$id.$key")
        }.apply()
    }

    fun restore(context: Context, old: IntArray, new: IntArray) {
        val prior = prefs(context).all
        prefs(context).edit().apply {
            for (id in old) for (key in keys) remove("$id.$key")
            for ((from, to) in old.zip(new)) for (key in keys) {
                when (val value = prior["$from.$key"]) {
                    is Int -> putInt("$to.$key", value)
                    is String -> putString("$to.$key", value)
                }
            }
        }.apply()
    }

    private val keys = listOf("radius", "lat", "lon")
}
