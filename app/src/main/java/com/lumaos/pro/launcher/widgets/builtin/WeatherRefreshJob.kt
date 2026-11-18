package com.lumaos.pro.launcher.widgets.builtin

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.text.format.DateUtils
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.util.LumaLog
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Network work has a system-managed lifetime, not the app-widget broadcast deadline. */
class WeatherRefreshJob : JobService() {
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work = CoroutineScope(Dispatchers.Main).launch {
            val retry = try {
                withContext(Dispatchers.IO) {
                    val app = applicationContext
                    val manager = AppWidgetManager.getInstance(app)
                    val ids = manager.getAppWidgetIds(BuiltinWidgetKind.WEATHER.component(app))
                    val places = ids.asSequence().mapNotNull { BuiltinWidgetSettings.place(app, it) }.distinct()
                    var failed = false
                    for (place in places) {
                        ensureActive()
                        if (WeatherData.isFresh(app, place)) continue
                        try {
                            val sample = WeatherData.fetch(place)
                            ensureActive()
                            WeatherData.store(app, place, sample)
                        } catch (e: java.io.IOException) {
                            failed = true
                            LumaLog.w("Weather widget request unavailable")
                        } catch (e: org.json.JSONException) {
                            failed = true
                            LumaLog.w("Weather widget response unavailable")
                        }
                    }
                    BuiltinWidgets.render(app, BuiltinWidgetKind.WEATHER, ids)
                    failed
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                LumaLog.e("Weather job could not update widgets", e)
                true
            }
            jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }

    companion object {
        private const val JOB_ID = 62481

        internal fun enqueue(context: Context, ids: IntArray, reconfigure: Boolean) {
            if (ids.none { id -> BuiltinWidgetSettings.place(context, id)?.let { !WeatherData.isFresh(context, it) } == true }) return
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (!reconfigure && scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, WeatherRefreshJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(15 * DateUtils.MINUTE_IN_MILLIS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build())
        }

        internal fun cancel(context: Context) = context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
    }
}

internal data class WeatherSample(val celsius: Double, val code: Int, val fetchedAt: Long)

internal object WeatherData {
    private fun prefs(context: Context) = context.getSharedPreferences("widget_weather_cache", Context.MODE_PRIVATE)
    private fun key(place: WeatherPlace) = "${place.latitude},${place.longitude}"

    private fun sample(context: Context, place: WeatherPlace): WeatherSample? = runCatching {
        val data = JSONObject(prefs(context).getString(key(place), null) ?: return null)
        WeatherSample(data.getDouble("temperature"), data.getInt("code"), data.getLong("fetched"))
    }.getOrNull()

    fun clear(context: Context) { prefs(context).edit().clear().apply() }

    fun isFresh(context: Context, place: WeatherPlace) = sample(context, place)?.let {
        System.currentTimeMillis() - it.fetchedAt in 0 until 30 * DateUtils.MINUTE_IN_MILLIS
    } ?: false

    fun store(context: Context, place: WeatherPlace, sample: WeatherSample) {
        // Keep only a small cache. Nothing is uploaded except the coordinates in the forecast request.
        val prefs = prefs(context)
        prefs.edit().apply {
            if (prefs.all.size >= 32 && key(place) !in prefs.all) clear()
            putString(key(place), JSONObject().put("temperature", sample.celsius).put("code", sample.code)
                .put("fetched", sample.fetchedAt).toString())
        }.apply()
    }

    fun fetch(place: WeatherPlace): WeatherSample {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,weather_code&temperature_unit=celsius")
        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            if (connection.responseCode != 200) throw java.io.IOException("Forecast response error")
            val body = connection.inputStream.use { input ->
                val buffer = ByteArray(8_192)
                var length = 0
                while (length < buffer.size) {
                    val count = input.read(buffer, length, buffer.size - length)
                    if (count < 0) break
                    length += count
                }
                if (length == buffer.size) throw java.io.IOException("Forecast response too large")
                String(buffer, 0, length, Charsets.UTF_8)
            }
            return parse(body, System.currentTimeMillis())
        } finally {
            connection.disconnect()
        }
    }

    fun parse(body: String, now: Long): WeatherSample {
        val current = JSONObject(body).getJSONObject("current")
        val temperature = current.getDouble("temperature_2m")
        if (!temperature.isFinite()) throw org.json.JSONException("Invalid temperature")
        return WeatherSample(temperature, current.getInt("weather_code"), now)
    }

    fun reading(context: Context, place: WeatherPlace?): WidgetReading {
        if (place == null) return WidgetReading(context.getString(R.string.widget_weather_configure))
        val data = sample(context, place)
            ?: return WidgetReading(context.getString(R.string.widget_weather_waiting), context.getString(R.string.widget_weather_source))
        val age = System.currentTimeMillis() - data.fetchedAt
        if (age !in 0..DateUtils.DAY_IN_MILLIS) {
            return WidgetReading(context.getString(R.string.widget_data_unavailable), context.getString(R.string.widget_weather_source))
        }
        val condition = when (data.code) {
            0 -> R.string.widget_weather_clear
            in 1..3 -> R.string.widget_weather_cloud
            45, 48 -> R.string.widget_weather_fog
            in 51..67, in 80..82 -> R.string.widget_weather_rain
            in 71..77, 85, 86 -> R.string.widget_weather_snow
            in 95..99 -> R.string.widget_weather_storm
            else -> R.string.widget_weather_unknown_condition
        }
        val updated = DateUtils.getRelativeTimeSpanString(data.fetchedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        return WidgetReading(context.getString(R.string.widget_weather_temperature, data.celsius),
            context.getString(R.string.widget_weather_detail, context.getString(condition), updated), weatherCode = data.code)
    }
}
