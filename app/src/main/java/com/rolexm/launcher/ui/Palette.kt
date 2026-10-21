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

    private const val ACCENT = 0xFF0066CC.toInt()
    private const val ACCENT_DARK = 0xFF64D2FF.toInt()

    val LIGHT = Palette(
        isDark = false,
        background = 0xFFFFFFFF.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        surfaceVariant = 0xFFF5F5F7.toInt(),
        onSurface = 0xFF1C1C1E.toInt(),
        onSurfaceVariant = 0xFF6E6E73.toInt(),
        outline = 0xFFE5E5EA.toInt(),
        accent = ACCENT,
        onAccent = 0xFFFFFFFF.toInt(),
        scrim = 0x52000000.toInt(),
        drawerBackground = 0xFFFFFFFF.toInt(),
        fieldBackground = 0xFFF2F2F7.toInt()
    )

    val DARK = Palette(
        isDark = true,
        background = 0x00000000,
        surface = 0xFF1C1C1E.toInt(),
        surfaceVariant = 0xFF2C2C2E.toInt(),
        onSurface = 0xFFF5F5F7.toInt(),
        onSurfaceVariant = 0xFFAEAEB2.toInt(),
        outline = 0xFF3A3A3C.toInt(),
        accent = ACCENT_DARK,
        onAccent = 0xFF001018.toInt(),
        scrim = 0x99000000.toInt(),
        drawerBackground = 0xFF1C1C1E.toInt(),
        fieldBackground = 0xFF2C2C2E.toInt()
    )

    fun forMode(mode: ThemeMode, systemIsDark: Boolean): Palette =
        if (mode.resolveDark(systemIsDark)) DARK else LIGHT
}
