package com.lumaos.pro.launcher.ui.theme

/**
 * The complete semantic palette every LumaOS Pro surface draws from.
 *
 * Components never reach for a raw hex value: they ask for a role such as
 * `semantics.dialogSurface` or `semantics.dangerContainer`. That is what keeps
 * Aurora, Zen and Void consistent across the View-based home screen and the
 * Compose settings surfaces, including their status, disabled and overlay
 * states - the roles the base [ThemeColors] hierarchy does not spell out.
 *
 * Derived from [ThemeColors] rather than stored separately, so an animated
 * theme change (which interpolates the base roles) cannot leave a stale status
 * colour behind, and a custom accent/background still yields a coherent set.
 *
 * Contrast is enforced where the role carries meaning:
 *  - status colours and their on-colours: >= 4.5:1 (WCAG AA text);
 *  - disabled content: >= 3.0:1 (perceivable, deliberately quieter);
 *  - focus rings and dividers: >= 3.0:1 on the surface they sit on.
 */
data class SemanticColors(
    // Status
    val success: Int,
    val onSuccess: Int,
    val successContainer: Int,
    val onSuccessContainer: Int,
    val warning: Int,
    val onWarning: Int,
    val warningContainer: Int,
    val onWarningContainer: Int,
    val danger: Int,
    val onDanger: Int,
    val dangerContainer: Int,
    val onDangerContainer: Int,
    val info: Int,
    val onInfo: Int,

    // Structure
    val divider: Int,
    val dividerStrong: Int,
    val focusRing: Int,
    val pressed: Int,
    val selectedSurface: Int,
    val selectedContent: Int,
    val unselectedSurface: Int,
    val unselectedContent: Int,
    val disabledSurface: Int,
    val disabledContent: Int,
    val trackOn: Int,
    val trackOff: Int,

    // Elevated and overlay surfaces
    val dialogSurface: Int,
    val dialogBorder: Int,
    val dialogScrim: Int,
    val overlay: Int,
    val widgetSurface: Int,
    val widgetBorder: Int,
    val widgetForeground: Int,
    val widgetMuted: Int,
    val searchSurface: Int,
    val searchBorder: Int,
    val searchHint: Int,
    val searchIcon: Int,
    val dockSurface: Int,
    val dockBorder: Int,
    val chipSurface: Int,
    val chipBorder: Int,
    val inverseSurface: Int,
    val inverseOnSurface: Int
)

object SemanticPalette {

    private const val GREEN = 2.6180 // 150 deg
    private const val AMBER = 1.2915 // 74 deg
    private const val RED = 0.4363 // 25 deg

    fun of(c: ThemeColors): SemanticColors {
        fun status(hue: Double, darkC: Double, lightC: Double): Int {
            val seed = if (c.isDark) Oklch(0.82, darkC, hue).toArgb()
            else Oklch(0.45, lightC, hue).toArgb()
            return ColorMath.ensureContrast(seed, c.surface1, 4.5)
        }

        fun container(hue: Double, chroma: Double): Int =
            if (c.isDark) Oklch(0.31, chroma, hue).toArgb()
            else Oklch(0.93, chroma * 0.55, hue).toArgb()

        fun onContainer(base: Int, hue: Double, chroma: Double): Int = ColorMath.ensureContrast(
            if (c.isDark) Oklch(0.93, chroma * 0.6, hue).toArgb() else Oklch(0.28, chroma, hue).toArgb(),
            base,
            4.5
        )

        fun on(base: Int): Int = ColorMath.ensureContrast(ThemeEngine.bestTextOn(base), base, 4.5)

        val success = status(GREEN, 0.13, 0.13)
        val warning = status(AMBER, 0.14, 0.13)
        val danger = ColorMath.ensureContrast(c.danger, c.surface1, 4.5)
        val info = ColorMath.ensureContrast(c.accent, c.surface1, 4.5)

        val successContainer = container(GREEN, 0.085)
        val warningContainer = container(AMBER, 0.090)
        val dangerContainer = container(RED, 0.095)

        // The disabled pair is checked against the *composited* plate, not against
        // surface1: the plate itself moves towards onSurface, so testing the text
        // against the unmodified surface would over-state the contrast.
        val disabledSurface = ColorMath.composite(
            ColorMath.withAlpha(c.onSurface, if (c.isDark) 0.10f else 0.08f), c.surface1
        )
        val disabledContent = ColorMath.ensureContrast(
            ColorMath.composite(ColorMath.withAlpha(c.onSurface, if (c.isDark) 0.42f else 0.38f), disabledSurface),
            disabledSurface,
            3.0
        )

        return SemanticColors(
            success = success,
            onSuccess = on(success),
            successContainer = successContainer,
            onSuccessContainer = onContainer(successContainer, GREEN, 0.10),
            warning = warning,
            onWarning = on(warning),
            warningContainer = warningContainer,
            onWarningContainer = onContainer(warningContainer, AMBER, 0.11),
            danger = danger,
            onDanger = on(danger),
            dangerContainer = dangerContainer,
            onDangerContainer = onContainer(dangerContainer, RED, 0.11),
            info = info,
            onInfo = on(info),
            divider = c.outline,
            dividerStrong = c.outlineStrong,
            focusRing = ColorMath.ensureContrast(c.focusRing, c.surface1, 3.0),
            pressed = c.pressedOverlay,
            selectedSurface = c.accentContainer,
            selectedContent = c.onAccentContainer,
            unselectedSurface = c.surface2,
            unselectedContent = c.onSurfaceMuted,
            disabledSurface = disabledSurface,
            disabledContent = disabledContent,
            trackOn = ColorMath.ensureContrast(c.accent, c.surface1, 3.0),
            trackOff = c.outlineStrong,
            dialogSurface = c.surface2,
            dialogBorder = c.outlineStrong,
            dialogScrim = c.scrim,
            overlay = c.scrim,
            widgetSurface = c.surface1,
            widgetBorder = c.outline,
            widgetForeground = c.onSurface,
            widgetMuted = c.onSurfaceMuted,
            searchSurface = c.surface1,
            searchBorder = c.outline,
            searchHint = c.onSurfaceMuted,
            searchIcon = c.onSurfaceMuted,
            dockSurface = c.surface1,
            dockBorder = c.outlineStrong,
            chipSurface = c.surface2,
            chipBorder = c.outline,
            inverseSurface = c.onSurface,
            inverseOnSurface = c.surface0
        )
    }

    /** Every status-on-colour pair that must meet AA text contrast. */
    fun statusPairs(c: SemanticColors): List<Triple<String, Int, Int>> = listOf(
        Triple("onSuccess/success", c.onSuccess, c.success),
        Triple("onSuccessContainer", c.onSuccessContainer, c.successContainer),
        Triple("onWarning/warning", c.onWarning, c.warning),
        Triple("onWarningContainer", c.onWarningContainer, c.warningContainer),
        Triple("onDanger/danger", c.onDanger, c.danger),
        Triple("onDangerContainer", c.onDangerContainer, c.dangerContainer),
        Triple("onInfo/info", c.onInfo, c.info)
    )
}
