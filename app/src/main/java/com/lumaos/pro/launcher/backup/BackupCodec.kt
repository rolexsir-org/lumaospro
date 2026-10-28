package com.lumaos.pro.launcher.backup

import com.lumaos.pro.launcher.launcher.model.HomeLayoutCodec
import com.lumaos.pro.launcher.launcher.model.HomeLayoutOps

/**
 * LumaOS Pro backup format: a small, human-readable JSON document containing the
 * user's settings, themes, gestures, context schedules, hidden-app lists and
 * home layouts for every context profile.
 *
 * Deliberately excluded:
 *  - app widgets: widget ids belong to this install's AppWidgetHost and cannot
 *    be re-bound elsewhere, so they are stripped from layouts on export;
 *  - device state (Do Not Disturb bookkeeping, optimizer timestamps);
 *  - LumaOS Pro's launch history, which stays on the device it was recorded on.
 *
 * Restores are whitelist-based and type-checked: a key is written only if
 * LumaOS Pro knows it and the stored type matches, so a damaged or hand-edited
 * file can never put a wrongly typed value into DataStore.
 */
object BackupCodec {

    const val FORMAT = "lumaos-backup"
    private const val LEGACY_FORMAT = "rolexm-backup"
    private val ACCEPTED_FORMATS = setOf(FORMAT, LEGACY_FORMAT)
    const val VERSION = 1

    enum class ValueType(val tag: String) {
        STRING("s"), INT("i"), LONG("l"), BOOLEAN("b"), FLOAT("f");

        companion object {
            fun fromTag(tag: Any?): ValueType? = entries.firstOrNull { it.tag == tag }
        }
    }

    private val EXACT: Map<String, ValueType> = mapOf(
        // Appearance (legacy + theme engine)
        "theme_mode" to ValueType.STRING,
        "theme_id" to ValueType.STRING,
        "theme_switching" to ValueType.STRING,
        "theme_light_id" to ValueType.STRING,
        "theme_dark_id" to ValueType.STRING,
        "theme_dark_window" to ValueType.STRING,
        "accent_override" to ValueType.INT,
        "background_override" to ValueType.INT,
        "typography" to ValueType.STRING,
        "icon_shape" to ValueType.STRING,
        "icon_style" to ValueType.STRING,
        "icon_pack" to ValueType.STRING,
        "backdrop" to ValueType.STRING,
        "dynamic_wallpaper_colors" to ValueType.BOOLEAN,
        "active_wallpaper_id" to ValueType.STRING,
        "effects_pref" to ValueType.STRING,
        "motion_pref" to ValueType.STRING,
        "home_preset" to ValueType.STRING,
        "dock_style" to ValueType.STRING,
        "grid_columns" to ValueType.INT,
        "grid_rows" to ValueType.INT,
        "drawer_columns" to ValueType.INT,
        "icon_size_dp" to ValueType.INT,
        "show_labels" to ValueType.BOOLEAN,
        "text_scale" to ValueType.FLOAT,
        // Drawer & privacy
        "drawer_sort" to ValueType.STRING,
        "drawer_presentation" to ValueType.STRING,
        "drawer_categories" to ValueType.BOOLEAN,
        "suggestions_enabled" to ValueType.BOOLEAN,
        "hidden_search" to ValueType.BOOLEAN,
        "hidden_lock" to ValueType.BOOLEAN,
        "widgets_enabled" to ValueType.BOOLEAN,
        // Performance
        "profile_override" to ValueType.STRING,
        "auto_optimize" to ValueType.BOOLEAN,
        // Layout & context
        "home_layout" to ValueType.STRING,
        "layout_seeded" to ValueType.BOOLEAN,
        "active_context_profile" to ValueType.STRING,
        "context_notifications_enabled" to ValueType.BOOLEAN,
        "context_notification_profiles" to ValueType.STRING,
        "context_dnd_profiles" to ValueType.STRING,
        "onboarding_done" to ValueType.BOOLEAN
    )

    private val PREFIXES: Map<String, ValueType> = mapOf(
        "home_layout_" to ValueType.STRING,
        "schedule_" to ValueType.STRING,
        "hidden_apps_" to ValueType.STRING,
        "pinned_apps_" to ValueType.STRING,
        "gesture_" to ValueType.STRING,
        "profile_theme_" to ValueType.STRING
    )

    fun expectedType(key: String): ValueType? =
        EXACT[key] ?: PREFIXES.entries.firstOrNull { key.startsWith(it.key) && key.length > it.key.length }?.value

    fun isLayoutKey(key: String): Boolean = key == "home_layout" || key.startsWith("home_layout_")

    data class Contents(
        val values: Map<String, Any>,
        val skippedKeys: List<String>,
        val createdAtMillis: Long?,
        val appVersion: String?
    )

    class InvalidBackup(message: String) : Exception(message)

    /** Serialises [values] (DataStore key name -> value); unknown keys are dropped. */
    fun encode(values: Map<String, Any>, appVersion: String, createdAtMillis: Long): String {
        val settings = LinkedHashMap<String, Any?>()
        for ((key, raw) in values.toSortedMap()) {
            val type = expectedType(key) ?: continue
            val value = coerce(raw, type) ?: continue
            // Private imported photo IDs reference device-local files not carried in backups.
            if (key == "active_wallpaper_id" && !(value as String).startsWith("art:")) continue
            val stored = if (isLayoutKey(key)) sanitizeLayout(value as String) else value
            settings[key] = linkedMapOf("t" to type.tag, "v" to stored)
        }
        val doc = linkedMapOf<String, Any?>(
            "format" to FORMAT,
            "version" to VERSION,
            "app_version" to appVersion,
            "created_at" to createdAtMillis,
            "note" to "LumaOS Pro settings backup. Widgets and launch history are not included.",
            "settings" to settings
        )
        return MiniJson.write(doc)
    }

    fun decode(text: String): Contents {
        val root = try {
            MiniJson.parse(text)
        } catch (e: MiniJson.ParseException) {
            throw InvalidBackup("Not a valid backup file (${e.message})")
        } as? Map<*, *> ?: throw InvalidBackup("Not a LumaOS Pro backup")
        if (root["format"] !in ACCEPTED_FORMATS) throw InvalidBackup("Not a LumaOS Pro backup")
        val version = (root["version"] as? Number)?.toInt() ?: throw InvalidBackup("Missing version")
        if (version > VERSION) throw InvalidBackup("Backup was made by a newer LumaOS Pro (format $version)")
        val settings = root["settings"] as? Map<*, *> ?: throw InvalidBackup("Backup has no settings")

        val values = LinkedHashMap<String, Any>()
        val skipped = ArrayList<String>()
        for ((k, entry) in settings) {
            val key = k as? String ?: continue
            val expected = expectedType(key)
            val obj = entry as? Map<*, *>
            val tag = ValueType.fromTag(obj?.get("t"))
            if (expected == null || obj == null || tag != expected) {
                skipped += key
                continue
            }
            val value = coerce(obj["v"], expected)
            if (value == null) {
                skipped += key
                continue
            }
            values[key] = if (isLayoutKey(key)) sanitizeLayout(value as String) else value
        }
        return Contents(
            values = values,
            skippedKeys = skipped,
            createdAtMillis = (root["created_at"] as? Number)?.toLong(),
            appVersion = root["app_version"] as? String
        )
    }

    private fun coerce(raw: Any?, type: ValueType): Any? = when (type) {
        ValueType.STRING -> raw as? String
        ValueType.BOOLEAN -> raw as? Boolean
        ValueType.INT -> (raw as? Number)?.toLong()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        ValueType.LONG -> (raw as? Number)?.toLong()
        ValueType.FLOAT -> (raw as? Number)?.toFloat()?.takeIf { it.isFinite() }
    }

    /** Widgets cannot move between installs; everything else in a layout is kept. */
    fun sanitizeLayout(raw: String): String =
        HomeLayoutCodec.encode(HomeLayoutOps.stripWidgets(HomeLayoutCodec.decode(raw)))
}
