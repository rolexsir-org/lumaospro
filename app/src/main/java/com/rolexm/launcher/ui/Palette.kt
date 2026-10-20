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

    private const val ACCENT = 0xFF526B4F.toInt()
    private const val ACCENT_DARK = 0xFFB5C5A9.toInt()

    val LIGHT = Palette(
        isDark = false,
        background = 0x00000000, // transparent: the wallpaper is the background
        surface = 0xFFF6F5F1.toInt(),
        surfaceVariant = 0xFFE9E9E2.toInt(),
        onSurface = 0xFF20251F.toInt(),
        onSurfaceVariant = 0xFF62695F.toInt(),
        outline = 0x1F20251F,
        accent = ACCENT,
        onAccent = 0xFFFFFFFF.toInt(),
        scrim = 0x99000000.toInt(),
        drawerBackground = 0xF2F6F5F1.toInt(),
        fieldBackground = 0xFFE9E9E2.toInt()
    )

    val DARK = Palette(
        isDark = true,
        background = 0x00000000,
        surface = 0xFF171B17.toInt(),
        surfaceVariant = 0xFF242A23.toInt(),
        onSurface = 0xFFE8EBE4.toInt(),
        onSurfaceVariant = 0xFFB2BAAD.toInt(),
        outline = 0x33FFFFFF,
        accent = ACCENT_DARK,
        onAccent = 0xFF20271E.toInt(),
        scrim = 0xB3000000.toInt(),
        drawerBackground = 0xF2171B17.toInt(),
        fieldBackground = 0xFF242A23.toInt()
    )

    fun forMode(mode: ThemeMode, systemIsDark: Boolean): Palette =
        if (mode.resolveDark(systemIsDark)) DARK else LIGHT
}
