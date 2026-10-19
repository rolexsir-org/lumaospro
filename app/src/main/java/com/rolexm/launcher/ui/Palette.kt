package com.rolexm.launcher.ui

/**
 * ROLEXM's colour scheme.
 *
 * The launcher does not use AppCompat or MaterialComponents day/night themes:
 * it paints the home screen itself, so it needs concrete colours rather than
 * theme attributes. That also means the same palette object feeds both the
 * View-based home screen and the Compose settings screens, keeping them
 * visually identical.
 *
 * Pure Kotlin (colours are plain ARGB Ints) so theme resolution is unit tested
 * on the JVM.
 */

enum class ThemeMode(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    /** Whether this mode resolves to a dark palette. */
    fun resolveDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorageKey(key: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == key } ?: SYSTEM
    }
}

data class Palette(
    val isDark: Boolean,
    /** Home screen / settings background. */
    val background: Int,
    /** Elevated surfaces: drawer sheet, cards, dialogs. */
    val surface: Int,
    val surfaceVariant: Int,
    /** Primary text. */
    val onSurface: Int,
    /** Secondary text. */
    val onSurfaceVariant: Int,
    /** Divider / outline. */
    val outline: Int,
    /** Accent used for selection and active state. */
    val accent: Int,
    val onAccent: Int,
    /** Dim layer behind the drawer and behind folder popups. */
    val scrim: Int,
    /** Background of the app drawer sheet. */
    val drawerBackground: Int,
    /** Background of the search field. */
    val fieldBackground: Int
)

object Palettes {

    private const val ACCENT = 0xFF26A69A.toInt()
    private const val ACCENT_DARK = 0xFF4DD0E1.toInt()

    val LIGHT = Palette(
        isDark = false,
        background = 0x00000000, // transparent: the wallpaper is the background
        surface = 0xFFF5F6F7.toInt(),
        surfaceVariant = 0xFFE6E9EB.toInt(),
        onSurface = 0xFF10171A.toInt(),
        onSurfaceVariant = 0xFF546066.toInt(),
        outline = 0x1F000000,
        accent = ACCENT,
        onAccent = 0xFFFFFFFF.toInt(),
        scrim = 0x99000000.toInt(),
        drawerBackground = 0xE6FFFFFF.toInt(),
        fieldBackground = 0xFFE6E9EB.toInt()
    )

    val DARK = Palette(
        isDark = true,
        background = 0x00000000,
        surface = 0xFF121A1E.toInt(),
        surfaceVariant = 0xFF1E282D.toInt(),
        onSurface = 0xFFE8EEF0.toInt(),
        onSurfaceVariant = 0xFF9FAEB4.toInt(),
        outline = 0x33FFFFFF,
        accent = ACCENT_DARK,
        onAccent = 0xFF06212A.toInt(),
        scrim = 0xB3000000.toInt(),
        drawerBackground = 0xE60E1418.toInt(),
        fieldBackground = 0xFF1E282D.toInt()
    )

    fun forMode(mode: ThemeMode, systemIsDark: Boolean): Palette =
        if (mode.resolveDark(systemIsDark)) DARK else LIGHT
}
