/*
 * Standalone contrast audit against the REAL ThemeEngine / SemanticPalette.
 *
 * Run with toolchain/harness/run_contrast_audit.sh. It resolves every ThemeId in
 * both polarities, with the built-in accent and with a stress set of user accent
 * overrides and wallpaper seeds, then measures every pairing the theme engine
 * documents. Nothing is re-implemented: contrast comes from ColorMath.contrast and
 * every colour comes from ThemeEngine.resolve.
 *
 * Thresholds are WCAG 2.1 SC 1.4.3 (text) and SC 1.4.11 (non-text):
 *   4.5:1  normal text
 *   3.0:1  large text, and non-text UI components
 *
 * Pairings are tiered, because a single flat threshold misreports both ways:
 *
 *   TEXT        content the user reads -> 4.5:1.
 *   UI_REQUIRED non-text information needed to identify a component or its state
 *               (focus indicator, status colours, functional icons, input borders)
 *               -> 3.0:1.
 *   DECORATIVE  separators and rims on surfaces that are already distinguishable by
 *               their own fill. SC 1.4.11 does not set a floor for these; they are
 *               measured and reported, never failed.
 *   EXEMPT      SC 1.4.3 explicitly exempts "an inactive user interface component",
 *               so disabled content is measured and reported, never failed.
 *   CONTEXTUAL  home-screen text, which is composited over the wallpaper plus a
 *               scrim -- not over ThemeColors.background -- so it is measured against
 *               the wallpaper seed that is actually behind it.
 *
 * Each pairing is flagged `live` only when BOTH of its roles are read outside
 * ui/theme, i.e. the pairing can actually be rendered by shipped UI. A violation on a
 * pairing nothing renders is a design-system defect, not a shipped accessibility
 * defect, and the report says which of the two it is.
 *
 * Measured read counts outside ui/theme (grep, this tree) that decide those flags:
 *   ThemeColors  background 82, accent 49, onSurface 35, isDark 34, surface 23,
 *                ripple 22, homeForeground 19, outline 18, onAccent 13, secondary 13,
 *                focusRing 11, onSurfaceMuted 10, scrim 8, homeMuted 8, homeShadow 5,
 *                accentContainer 4, danger 4, outlineStrong 3, onAccentContainer 3,
 *                onBackground 2 -- and 0 for onSurfaceFaint, glow, pressedOverlay,
 *                selectedFill, successSeed, warningSeed.
 *   SemanticColors  focusRing 11, onSuccess 6, info 6, danger 4, disabledSurface 3,
 *                inverseOnSurface/inverseSurface/overlay/pressed 2 each,
 *                onDanger/onDangerContainer/dangerContainer 2 each,
 *                dockBorder/dockSurface 1 each -- and 0 for the other 30 roles,
 *                which includes the whole widget*, search*, selected*, chip*,
 *                track* and divider* families.
 */
@file:Suppress("PackageDirectoryMismatch")

package com.lumaos.pro.launcher.ui.theme

import com.lumaos.pro.launcher.context.ContextProfile

private const val TEXT = 4.5
private const val UI = 3.0

private enum class Tier { TEXT, UI_REQUIRED, DECORATIVE, EXEMPT, CONTEXTUAL }

private data class Pair(
    val name: String,
    val fg: (ThemeColors, SemanticColors) -> Int,
    val bg: (ThemeColors, SemanticColors) -> Int,
    val tier: Tier,
    val live: Boolean
)

private data class Violation(
    val combo: String,
    val pair: String,
    val tier: Tier,
    val live: Boolean,
    val ratio: Double,
    val required: Double,
    val fg: Int,
    val bg: Int,
    val accentOverridden: Boolean
)

private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)

/*
 * Liveness evidence: roles read outside app/src/main/java/com/lumaos/pro/launcher/ui/theme
 * (grep, this tree): focusRing 11, onSuccess 6, info 6, danger 4, disabledSurface 3,
 * inverseOnSurface/inverseSurface/overlay/pressed 2 each, onDanger/onDangerContainer/
 * dangerContainer 2 each, dockBorder/dockSurface 1 each. Every other SemanticColors
 * role reads 0 times outside ui/theme. ThemeColors roles are read throughout.
 */
private val pairs = listOf(
    // ---- TEXT ---------------------------------------------------------------
    Pair("onSurface / surface0", { c, _ -> c.onSurface }, { c, _ -> c.surface0 }, Tier.TEXT, true),
    Pair("onSurface / surface1", { c, _ -> c.onSurface }, { c, _ -> c.surface1 }, Tier.TEXT, true),
    Pair("onSurface / surface2", { c, _ -> c.onSurface }, { c, _ -> c.surface2 }, Tier.TEXT, true),
    Pair("onSurfaceMuted / surface1", { c, _ -> c.onSurfaceMuted }, { c, _ -> c.surface1 }, Tier.TEXT, true),
    Pair("onSurfaceMuted / surface2", { c, _ -> c.onSurfaceMuted }, { c, _ -> c.surface2 }, Tier.TEXT, true),
    Pair("onBackground / background", { c, _ -> c.onBackground }, { c, _ -> c.background }, Tier.TEXT, true),
    Pair("onAccent / accent", { c, _ -> c.onAccent }, { c, _ -> c.accent }, Tier.TEXT, true),
    Pair("onAccentContainer / accentContainer",
        { c, _ -> c.onAccentContainer }, { c, _ -> c.accentContainer }, Tier.TEXT, true),
    Pair("onSuccess / success", { _, s -> s.onSuccess }, { _, s -> s.success }, Tier.TEXT, false),
    Pair("onDanger / danger", { _, s -> s.onDanger }, { _, s -> s.danger }, Tier.TEXT, true),
    Pair("onInfo / info", { _, s -> s.onInfo }, { _, s -> s.info }, Tier.TEXT, false),
    Pair("onDangerContainer / dangerContainer",
        { _, s -> s.onDangerContainer }, { _, s -> s.dangerContainer }, Tier.TEXT, true),
    Pair("inverseOnSurface / inverseSurface",
        { _, s -> s.inverseOnSurface }, { _, s -> s.inverseSurface }, Tier.TEXT, true),
    Pair("onWarning / warning", { _, s -> s.onWarning }, { _, s -> s.warning }, Tier.TEXT, false),
    Pair("onSuccessContainer / successContainer",
        { _, s -> s.onSuccessContainer }, { _, s -> s.successContainer }, Tier.TEXT, false),
    Pair("onWarningContainer / warningContainer",
        { _, s -> s.onWarningContainer }, { _, s -> s.warningContainer }, Tier.TEXT, false),
    Pair("widgetForeground / widgetSurface",
        { _, s -> s.widgetForeground }, { _, s -> s.widgetSurface }, Tier.TEXT, false),
    Pair("widgetMuted / widgetSurface", { _, s -> s.widgetMuted }, { _, s -> s.widgetSurface }, Tier.TEXT, false),
    Pair("searchHint / searchSurface", { _, s -> s.searchHint }, { _, s -> s.searchSurface }, Tier.TEXT, false),
    Pair("selectedContent / selectedSurface",
        { _, s -> s.selectedContent }, { _, s -> s.selectedSurface }, Tier.TEXT, false),
    Pair("unselectedContent / unselectedSurface",
        { _, s -> s.unselectedContent }, { _, s -> s.unselectedSurface }, Tier.TEXT, false),

    // ---- UI_REQUIRED: identifies a component or conveys state ---------------
    Pair("focusRing / surface1", { _, s -> s.focusRing }, { c, _ -> c.surface1 }, Tier.UI_REQUIRED, true),
    Pair("accent / surface1", { c, _ -> c.accent }, { c, _ -> c.surface1 }, Tier.UI_REQUIRED, true),
    Pair("accent / background", { c, _ -> c.accent }, { c, _ -> c.background }, Tier.UI_REQUIRED, true),
    Pair("secondary / surface1", { c, _ -> c.secondary }, { c, _ -> c.surface1 }, Tier.UI_REQUIRED, true),
    Pair("danger / surface1", { c, _ -> c.danger }, { c, _ -> c.surface1 }, Tier.UI_REQUIRED, true),
    Pair("onSurfaceFaint / surface1", { c, _ -> c.onSurfaceFaint }, { c, _ -> c.surface1 }, Tier.UI_REQUIRED, false),
    // outlineStrong is read in exactly two places outside ui/theme, both decorative:
    // the icon plate border (IconRenderer.kt:68-69) and a wallpaper thumbnail frame
    // (WallpaperActivity.kt:607). Neither is a boundary needed to identify a
    // component, so it is tiered DECORATIVE rather than UI_REQUIRED.
    Pair("outlineStrong / surface1 (icon plate + thumb frame)",
        { c, _ -> c.outlineStrong }, { c, _ -> c.surface1 }, Tier.DECORATIVE, true),
    Pair("searchIcon / searchSurface", { _, s -> s.searchIcon }, { _, s -> s.searchSurface }, Tier.UI_REQUIRED, false),
    Pair("success / surface1", { _, s -> s.success }, { c, _ -> c.surface1 }, Tier.UI_REQUIRED, false),
    Pair("warning / surface1", { _, s -> s.warning }, { c, _ -> c.surface1 }, Tier.UI_REQUIRED, false),
    Pair("info / surface1", { _, s -> s.info }, { c, _ -> c.surface1 }, Tier.UI_REQUIRED, true),
    Pair("trackOn / surface1", { _, s -> s.trackOn }, { c, _ -> c.surface1 }, Tier.UI_REQUIRED, false),

    // ---- DECORATIVE: separators and rims, no SC 1.4.11 floor ---------------
    Pair("outline / surface1", { c, _ -> c.outline }, { c, _ -> c.surface1 }, Tier.DECORATIVE, true),
    Pair("divider / surface1", { _, s -> s.divider }, { c, _ -> c.surface1 }, Tier.DECORATIVE, false),
    Pair("dividerStrong / surface1", { _, s -> s.dividerStrong }, { c, _ -> c.surface1 }, Tier.DECORATIVE, false),
    Pair("chipBorder / chipSurface", { _, s -> s.chipBorder }, { _, s -> s.chipSurface }, Tier.DECORATIVE, false),
    Pair("dialogBorder / dialogSurface", { _, s -> s.dialogBorder }, { _, s -> s.dialogSurface }, Tier.DECORATIVE, false),
    Pair("widgetBorder / widgetSurface", { _, s -> s.widgetBorder }, { _, s -> s.widgetSurface }, Tier.DECORATIVE, false),
    Pair("searchBorder / searchSurface", { _, s -> s.searchBorder }, { _, s -> s.searchSurface }, Tier.DECORATIVE, false),
    Pair("dockBorder / dockSurface", { _, s -> s.dockBorder }, { _, s -> s.dockSurface }, Tier.DECORATIVE, true),
    Pair("trackOff / surface1", { _, s -> s.trackOff }, { c, _ -> c.surface1 }, Tier.DECORATIVE, false),
    Pair("ripple / surface1", { c, _ -> c.ripple }, { c, _ -> c.surface1 }, Tier.DECORATIVE, true),
    Pair("pressedOverlay / surface1", { c, _ -> c.pressedOverlay }, { c, _ -> c.surface1 }, Tier.DECORATIVE, false),
    Pair("selectedFill / surface1", { c, _ -> c.selectedFill }, { c, _ -> c.surface1 }, Tier.DECORATIVE, false),
    Pair("glow / background", { c, _ -> c.glow }, { c, _ -> c.background }, Tier.DECORATIVE, false),

    // ---- EXEMPT: SC 1.4.3 exempts inactive UI ------------------------------
    Pair("disabledContent / disabledSurface (inactive UI)",
        { _, s -> s.disabledContent }, { _, s -> s.disabledSurface }, Tier.EXEMPT, false)
)

/**
 * Accent overrides spanning what a user or a wallpaper can actually produce: the
 * extremes, the pure primaries and secondaries, and mid-luminance tones where an
 * accent-on-surface pairing is hardest.
 */
private val accentStress = listOf(
    null,
    0xFF000000.toInt(), 0xFFFFFFFF.toInt(),
    0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
    0xFFFFFF00.toInt(), 0xFF00FFFF.toInt(), 0xFFFF00FF.toInt(),
    0xFF808080.toInt(), 0xFF4581FF.toInt(), 0xFF7C86FF.toInt(),
    0xFFB5C5A9.toInt(), 0xFFE2B66B.toInt()
)

private val wallpaperSeeds = listOf(
    null,
    WallpaperSeeds(primary = 0xFF1B2430.toInt(), accent = 0xFF69E2F2.toInt(), supportsDarkText = false),
    WallpaperSeeds(primary = 0xFFF3EFE6.toInt(), accent = 0xFFC9A227.toInt(), supportsDarkText = true),
    WallpaperSeeds(primary = 0xFF4A4A4A.toInt(), supportsDarkText = null),
    WallpaperSeeds(primary = 0xFF69E2F2.toInt(), accent = 0xFF1B2430.toInt(), supportsDarkText = false)
)

private data class PairRecord(
    val ratio: Double,
    val combo: String,
    val fg: String,
    val bg: String,
    val required: Double
)

fun main() {
    val enforced = pairs.filter { it.tier == Tier.TEXT || it.tier == Tier.UI_REQUIRED }
    val reported = pairs.filter { it.tier == Tier.DECORATIVE || it.tier == Tier.EXEMPT }
    val violations = mutableListOf<Violation>()
    val worstPerPair = LinkedHashMap<String, PairRecord>()
    var combos = 0
    var measurements = 0
    var homeChecked = 0
    var homeWorst = Double.MAX_VALUE
    var homeWorstWhere = ""

    for (id in ThemeId.entries) {
        for (systemDark in listOf(false, true)) {
            for (seeds in wallpaperSeeds) {
                for (accent in accentStress) {
                    val settings = ThemeSettings(
                        themeId = id,
                        switching = ThemeSwitching.FIXED,
                        accentOverride = accent
                    )
                    val env = ThemeEnvironment(
                        systemDark = systemDark,
                        minuteOfDay = 12 * 60,
                        profile = null as ContextProfile?,
                        wallpaper = seeds
                    )
                    val resolved = ThemeEngine.resolve(settings, env)
                    val c = resolved.colors
                    val s = SemanticPalette.of(c)
                    combos++
                    val label = buildString {
                        append(id.storageKey)
                        append(if (systemDark) "/dark" else "/light")
                        append(accent?.let { "/accent=${hex(it)}" } ?: "/built-in")
                        if (seeds != null) append("/wallpaper=${hex(seeds.primary)}")
                    }

                    for (p in pairs) {
                        val fg = ColorMath.opaque(p.fg(c, s))
                        val bg = ColorMath.opaque(p.bg(c, s))
                        val ratio = ColorMath.contrast(fg, bg)
                        measurements++
                        val rec = worstPerPair[p.name]
                        if (rec == null || ratio < rec.ratio) {
                            worstPerPair[p.name] = PairRecord(
                                ratio, label, hex(fg), hex(bg),
                                when (p.tier) { Tier.TEXT -> TEXT; Tier.UI_REQUIRED -> UI; else -> 0.0 }
                            )
                        }
                        val required = when (p.tier) {
                            Tier.TEXT -> TEXT
                            Tier.UI_REQUIRED -> UI
                            else -> 0.0
                        }
                        if (required > 0 && ratio < required) {
                            violations += Violation(
                                label, p.name, p.tier, p.live, ratio, required, fg, bg,
                                accentOverridden = accent != null
                            )
                        }
                    }

                    // CONTEXTUAL: home text sits on the wallpaper behind a scrim, so
                    // measuring it against ThemeColors.background would be meaningless.
                    val behind = seeds?.let { ColorMath.opaque(it.primary) } ?: c.background
                    for (name in listOf("homeForeground" to c.homeForeground, "homeMuted" to c.homeMuted)) {
                        val ratio = ColorMath.contrast(ColorMath.opaque(name.second), behind)
                        homeChecked++
                        if (ratio < homeWorst) {
                            homeWorst = ratio
                            homeWorstWhere = "$label :: ${name.first} ${hex(name.second)} on ${hex(behind)}"
                        }
                    }
                }
            }
        }
    }

    val liveViolations = violations.filter { it.live }
    val latentViolations = violations.filter { !it.live }
    val liveCurated = liveViolations.filter { !it.accentOverridden }
    val liveOverridden = liveViolations.filter { it.accentOverridden }

    println("Theme contrast audit -- real ThemeEngine.resolve + SemanticPalette.of")
    println("====================================================================")
    println("presets=${ThemeId.entries.size} polarities=2 accentOverrides=${accentStress.size} " +
        "wallpaperSeeds=${wallpaperSeeds.size}")
    println("combinations=$combos  pairings=${pairs.size}  measurements=$measurements  " +
        "home-text checks=$homeChecked")
    println("pairings enforced: ${enforced.size} " +
        "(${enforced.count { it.tier == Tier.TEXT }} at 4.5:1, " +
        "${enforced.count { it.tier == Tier.UI_REQUIRED }} at 3.0:1); " +
        "reported only: ${reported.size}")
    println()
    println("SHIPPED-UI violations (roles actually read outside ui/theme): ${liveViolations.size}")
    println("  with the theme's curated accent : ${liveCurated.size}")
    println("  with a user-supplied accent     : ${liveOverridden.size}")
    if (liveCurated.isNotEmpty()) {
        println("  curated-accent pairings failing:")
        liveCurated.groupBy { it.pair }.forEach { (name, vs) ->
            val w = vs.minByOrNull { it.ratio }!!
            println("    %-42s worst %.2f:1  fails %d  e.g. %s".format(name, w.ratio, vs.size, w.combo))
        }
    }
    val byPairLive = liveViolations.groupBy { it.pair }
    byPairLive.entries.sortedBy { it.value.minOf { v -> v.ratio } }.forEach { (name, vs) ->
        val w = vs.minByOrNull { it.ratio }!!
        println("  %-42s floor %.1f:1  worst %.2f:1  fails %d/%d  e.g. %s  %s on %s".format(
            name, w.required, w.ratio, vs.size, combos, w.combo, hex(w.fg), hex(w.bg)))
    }
    if (byPairLive.isEmpty()) println("  (none)")
    println()
    println("LATENT design-system violations (roles no shipped view reads yet): ${latentViolations.size}")
    val byPairLatent = latentViolations.groupBy { it.pair }
    byPairLatent.entries.sortedBy { it.value.minOf { v -> v.ratio } }.forEach { (name, vs) ->
        val w = vs.minByOrNull { it.ratio }!!
        println("  %-42s floor %.1f:1  worst %.2f:1  fails %d/%d".format(
            name, w.required, w.ratio, vs.size, combos))
    }
    if (byPairLatent.isEmpty()) println("  (none)")
    println()
    val tightest = enforced.minByOrNull { worstPerPair[it.name]!!.ratio }!!
    val tightestRec = worstPerPair[tightest.name]!!
    println("Minimum ratio on an enforced pairing: %.2f:1".format(tightestRec.ratio))
    println("  produced by %s  [%s  %s on %s]".format(
        tightest.name, tightestRec.combo, tightestRec.fg, tightestRec.bg))
    println()
    println("Tightest enforced pairing per combination class:")
    enforced.sortedBy { worstPerPair[it.name]!!.ratio }.take(14).forEach { p ->
        val r = worstPerPair[p.name]!!
        val floor = if (p.tier == Tier.TEXT) TEXT else UI
        println("  %.2f:1  %-42s floor %.1f  margin %+.2f  [%s %s on %s]".format(
            r.ratio, p.name, floor, r.ratio - floor, r.combo, r.fg, r.bg))
    }
    println()
    println("Decorative / WCAG-exempt pairings (measured, never failed):")
    reported.sortedBy { worstPerPair[it.name]!!.ratio }.forEach { p ->
        val r = worstPerPair[p.name]!!
        println("  %.2f:1  %-42s [%s %s on %s]".format(r.ratio, p.name, r.combo, r.fg, r.bg))
    }
    println()
    println("CONTEXTUAL home text, measured against the wallpaper actually behind it:")
    println("  worst %.2f:1  at %s".format(homeWorst, homeWorstWhere))
    println("  (home clock/date/context are composited over the wallpaper plus a legibility")
    println("   scrim; a flat-colour measurement would understate the real rendering)")

    println()
    if (liveViolations.isEmpty()) {
        println("RESULT: PASS -- no WCAG 1.4.3/1.4.11 violation on any pairing whose roles")
        println("are read by shipped UI, across all $combos combinations.")
        if (latentViolations.isNotEmpty()) {
            println("NOTE: ${latentViolations.size} latent violation(s) remain on design-system roles")
            println("no view reads yet. They are not shipped defects, but they will surface the")
            println("moment a widget/search/dialog surface is wired to SemanticColors.")
        }
    } else {
        println("RESULT: FAIL -- ${liveViolations.size} shipped-UI violation(s).")
        kotlin.system.exitProcess(1)
    }
}
