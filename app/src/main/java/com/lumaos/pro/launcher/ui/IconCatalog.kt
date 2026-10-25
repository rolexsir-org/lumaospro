package com.lumaos.pro.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.lumaos.pro.launcher.R

/**
 * The interface icon set.
 *
 * Every glyph is original LumaOS artwork drawn on the same 24 dp grid with the
 * same 1.75 dp stroke, rounded caps and 3 dp optical inset. Controls, menus,
 * settings sections and status messages all come from here; no second icon pack
 * is mixed into the interface. Application icons are a separate system (see
 * `IconRenderer` and `MonogramDrawable`) because they carry third-party identity.
 *
 * Filled variants are used deliberately, never decoratively: `DONE_FILLED` and
 * `PIN_FILLED` mark a selected menu entry, `FOLDER_FILLED` an open folder.
 */
enum class LumaIcon(
    @DrawableRes val res: Int,
    val role: IconRole,
    val label: String,
    /** Optional filled counterpart for a selected state. */
    @DrawableRes val filledRes: Int? = null
) {
    // Navigation and controls
    SEARCH(R.drawable.ic_rx_search, IconRole.CONTROL, "Search"),
    SETTINGS(R.drawable.ic_rx_settings, IconRole.CONTROL, "Settings"),
    CLOSE(R.drawable.ic_rx_close, IconRole.CONTROL, "Close"),
    VOICE(R.drawable.ic_rx_voice, IconRole.CONTROL, "Voice"),
    SORT(R.drawable.ic_rx_sort, IconRole.CONTROL, "Sort"),
    DONE(R.drawable.ic_rx_done, IconRole.CONTROL, "Done", R.drawable.ic_rx_check_filled),
    CHEVRON_RIGHT(R.drawable.ic_rx_chevron_right, IconRole.CONTROL, "Open"),
    MORE(R.drawable.ic_rx_sort, IconRole.CONTROL, "More"),

    // Home and organisation
    HOME(R.drawable.ic_rx_home, IconRole.NAVIGATION, "Home"),
    HOME_ADD(R.drawable.ic_rx_home_add, IconRole.ACTION, "Add to home"),
    FOLDER(R.drawable.ic_rx_folder, IconRole.APP, "Folder", R.drawable.ic_rx_folder_filled),
    LAYOUT(R.drawable.ic_rx_layout, IconRole.ACTION, "Layout"),
    DRAWER(R.drawable.ic_rx_drawer, IconRole.NAVIGATION, "App drawer"),
    WIDGETS(R.drawable.ic_rx_widgets, IconRole.ACTION, "Widgets"),
    DOCK(R.drawable.ic_rx_dock, IconRole.ACTION, "Dock"),
    MOVE(R.drawable.ic_rx_move, IconRole.ACTION, "Move"),
    PIN(R.drawable.ic_rx_pin, IconRole.ACTION, "Pin", R.drawable.ic_rx_pin_filled),
    RENAME(R.drawable.ic_rx_rename, IconRole.ACTION, "Rename"),
    RESIZE(R.drawable.ic_rx_resize, IconRole.ACTION, "Resize"),
    REMOVE(R.drawable.ic_rx_remove, IconRole.ACTION, "Remove"),
    HIDE(R.drawable.ic_rx_hide, IconRole.ACTION, "Hide"),
    UNINSTALL(R.drawable.ic_rx_uninstall, IconRole.ACTION, "Uninstall"),
    APPS(R.drawable.ic_rx_apps, IconRole.APP, "Applications"),

    // Customisation
    APPEARANCE(R.drawable.ic_rx_palette, IconRole.CATEGORY, "Appearance"),
    PALETTE(R.drawable.ic_rx_palette, IconRole.CATEGORY, "Palette"),
    STUDIO(R.drawable.ic_rx_studio, IconRole.CATEGORY, "Studio"),
    THEME(R.drawable.ic_rx_contrast, IconRole.CATEGORY, "Theme"),
    WALLPAPER(R.drawable.ic_rx_wallpaper, IconRole.CATEGORY, "Wallpaper"),
    TYPOGRAPHY(R.drawable.ic_rx_typography, IconRole.CATEGORY, "Typography"),
    MOTION(R.drawable.ic_rx_motion, IconRole.CATEGORY, "Motion"),
    GRID(R.drawable.ic_rx_grid, IconRole.CATEGORY, "Grid"),

    // Settings categories
    CONTEXT(R.drawable.ic_rx_context, IconRole.CATEGORY, "Context profiles"),
    GESTURES(R.drawable.ic_rx_gestures, IconRole.CATEGORY, "Gestures"),
    PERFORMANCE(R.drawable.ic_rx_performance, IconRole.CATEGORY, "Performance"),
    PRIVACY(R.drawable.ic_rx_privacy, IconRole.CATEGORY, "Privacy"),
    BACKUP(R.drawable.ic_rx_backup, IconRole.CATEGORY, "Backup"),
    RESTORE(R.drawable.ic_rx_restore, IconRole.CATEGORY, "Restore"),
    ABOUT(R.drawable.ic_rx_info, IconRole.CATEGORY, "About"),
    INFO(R.drawable.ic_rx_info, IconRole.STATUS, "Information"),

    // Status and states
    SUCCESS(R.drawable.ic_rx_success, IconRole.STATUS, "Success"),
    WARNING(R.drawable.ic_rx_warning, IconRole.STATUS, "Warning"),
    ERROR(R.drawable.ic_rx_error, IconRole.STATUS, "Error");

    companion object {
        /** Shown when a requested glyph cannot be resolved. */
        val fallback: LumaIcon = APPS

        fun forDrawable(@DrawableRes id: Int): LumaIcon? = entries.firstOrNull { it.res == id }
    }
}

enum class IconRole {
    /** Toolbar and dialog controls inside a 48 dp target. */
    CONTROL,
    /** Primary navigation or launcher surfaces. */
    NAVIGATION,
    /** Actions on an object (rename, pin, remove). */
    ACTION,
    /** Settings categories and customisation. */
    CATEGORY,
    /** Empty, loading and status messages. */
    STATUS,
    /** An application or folder representation. */
    APP
}

object LumaIcons {

    /** Consistent optical size for interface glyphs. */
    const val interfaceSizeDp = 24f
    /** Glyph size inside compact fields and menus. */
    const val compactSizeDp = 20f

    /**
     * Resolves a glyph, falling back to [LumaIcon.fallback] rather than drawing
     * nothing if the resource is unavailable in a stripped build.
     */
    fun drawable(context: Context, icon: LumaIcon, selected: Boolean = false, tint: Int? = null): Drawable {
        val id = if (selected) icon.filledRes ?: icon.res else icon.res
        val d = ContextCompat.getDrawable(context, id)
            ?: ContextCompat.getDrawable(context, LumaIcon.fallback.res)!!
        return d.mutate().apply { tint?.let { setTint(it) } }
    }

    /** Applies a glyph and tint to an ImageView (used by themed View surfaces). */
    fun apply(view: ImageView, icon: LumaIcon, tint: Int, selected: Boolean = false) {
        view.setImageDrawable(drawable(view.context, icon, selected, tint))
        view.imageTintList = ColorStateList.valueOf(tint)
        view.contentDescription = view.contentDescription ?: icon.label
    }

    /** Selected-state glyph for menus that already show their own label. */
    fun checkDrawable(context: Context, tint: Int): Drawable =
        drawable(context, LumaIcon.DONE, selected = true, tint = tint)
}
