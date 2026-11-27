package com.lumaos.pro.launcher.widgets.builtin

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lumaos.pro.launcher.R
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Android app-widget configure/reconfigure entry point, shared by all bundled widgets. */
class WidgetSettingsActivity : Activity() {
    private var refreshPermissions = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        if (info == null || info.provider.packageName != packageName || info.configure?.className != javaClass.name) {
            finish()
            return
        }
        title = info.loadLabel(packageManager)
        val kind = BuiltinWidgetKind.entries.firstOrNull { it.component(this) == info.provider }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).roundToInt()
            setPadding(padding, padding * 2, padding, padding)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15+ enforces edge-to-edge (the platform ignores
            // setDecorFitsSystemWindows(true) and the bar colours for
            // targetSdk 35+), so the form keeps its own distance from the
            // bars, the cutout and the keyboard. Below API 35 the fitted,
            // opaque-bar layout is unchanged - see VaultScreenActivity.
            ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
                val pad = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
                )
                view.setPadding(pad.left, pad.top, pad.right, pad.bottom)
                insets
            }
        }
        fun text(res: Int) = TextView(this).apply { setText(res); textSize = 16f; body.addView(this) }
        fun button(res: Int, action: () -> Unit) = Button(this).apply {
            setText(res)
            setOnClickListener { action() }
            body.addView(this)
        }
        text(R.string.widget_config_heading).apply { textSize = 24f; append("\n$title") }
        val label = text(R.string.widget_theme_radius)
        val slider = SeekBar(this).apply {
            this.id = R.id.widget_corner_slider
            max = 37
            progress = BuiltinWidgetSettings.radius(this@WidgetSettingsActivity, id)?.toInt()?.minus(1) ?: 0
            contentDescription = getString(R.string.widget_corner_radius)
            body.addView(this)
        }
        fun updateRadius() {
            label.text = if (slider.progress == 0) getString(R.string.widget_theme_radius)
                else getString(R.string.widget_custom_radius, slider.progress + 1)
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) = updateRadius()
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        updateRadius()
        val place = BuiltinWidgetSettings.place(this, id)
        fun coordinate(fieldId: Int, hintRes: Int, value: Double?) = EditText(this).apply {
            this.id = fieldId
            hint = getString(hintRes)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            val separator = java.text.DecimalFormatSymbols.getInstance().decimalSeparator
            setText(value?.toString()?.replace('.', separator).orEmpty())
            body.addView(this)
        }
        val lat: EditText?
        val lon: EditText?
        if (kind == BuiltinWidgetKind.WEATHER) {
            text(R.string.widget_weather_privacy)
            lat = coordinate(R.id.widget_latitude_input, R.string.widget_latitude, place?.latitude)
            lon = coordinate(R.id.widget_longitude_input, R.string.widget_longitude, place?.longitude)
        } else {
            lat = null
            lon = null
        }
        when (kind) {
            BuiltinWidgetKind.CALENDAR -> {
                text(R.string.widget_calendar_disclosure)
                button(R.string.widget_calendar_permission) {
                    if (BuiltinWidgetData.calendarAllowed(this)) launchSetting(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", packageName, null))
                    else requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), CALENDAR_REQUEST)
                }
            }
            BuiltinWidgetKind.USAGE -> {
                text(R.string.widget_usage_disclosure)
                button(R.string.widget_usage_permission) { launchSetting(Settings.ACTION_USAGE_ACCESS_SETTINGS) }
            }
            BuiltinWidgetKind.STORAGE -> button(R.string.widget_open_storage_settings) {
                launchSetting(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            }
            else -> Unit
        }
        button(R.string.widget_save_configuration) {
            val weather = if (lat != null && lon != null) {
                WeatherPlace.parse(lat.text.toString(), lon.text.toString()) ?: run {
                    lon.error = getString(R.string.widget_invalid_coordinates)
                    return@button
                }
            } else null
            BuiltinWidgetSettings.save(this, id, if (slider.progress == 0) -1 else slider.progress + 1, weather)
            BuiltinWidgets.configure(applicationContext, info.provider, id)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
    }

    private fun launchSetting(action: String, uri: android.net.Uri? = null) {
        runCatching { startActivity(Intent(action, uri)) }
            .onSuccess { refreshPermissions = true }
            .onFailure { Toast.makeText(this, R.string.widget_data_unavailable, Toast.LENGTH_SHORT).show() }
    }

    override fun onResume() {
        super.onResume()
        if (refreshPermissions) {
            refreshPermissions = false
            refreshReadings()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CALENDAR_REQUEST) refreshReadings()
    }

    private fun refreshReadings() = BuiltinWidgets.scope.launch { BuiltinWidgets.refreshAll(applicationContext) }

    companion object {
        private const val CALENDAR_REQUEST = 21

        fun intent(context: Context, id: Int) = Intent(context, WidgetSettingsActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    }
}
