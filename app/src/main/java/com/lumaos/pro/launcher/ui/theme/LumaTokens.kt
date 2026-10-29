package com.lumaos.pro.launcher.ui.theme

import android.content.Context
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Single Unified Design Token Authority for LumaOS Pro.
 *
 * Every View, XML layout (mirrored in `res/values/dimens.xml`), Drawable,
 * Activity, Overlay, and Jetpack Compose surface reads its glass material,
 * corner radii, shadows, typography, 8dp-grid spacing, icon dimensions,
 * component heights, spring physics, and Z-depth coordinates from [LumaTokens].
 */
object LumaTokens {

    // ── Glass Material ──
    const val blurRadiusSurface: Float = 25f
    const val blurRadiusOverlay: Float = 40f
    const val blurRadiusBackground: Float = 15f
    const val glassDarkBgAlpha: Float = 0.22f
    const val glassDarkCardAlpha: Float = 0.28f
    const val glassLightBgAlpha: Float = 0.35f
    const val glassLightCardAlpha: Float = 0.42f
    const val glassBorderWidth: Float = 1f
    const val glassBorderTopAlpha: Float = 0.18f
    const val glassBorderBottomAlpha: Float = 0.06f
    const val glassNoiseOpacity: Float = 0.02f
    const val glassSheenAlpha: Float = 0.06f
    const val glassNoiseTileSizePx: Int = 256
    const val glassDepthStepAlpha: Float = 0.03f
    const val glassLuminanceShiftMax: Float = 0.06f

    // ── Corner Radii (dp) ──
    const val radiusSmall: Float = 12f      // pills, chips, buttons
    const val radiusMedium: Float = 16f     // notifications, small cards
    const val radiusLarge: Float = 22f      // widgets, tiles
    const val radiusFolder: Float = 24f     // centered folder popup
    const val radiusXLarge: Float = 28f     // dock, sheets, panels
    const val radiusFull: Float = 999f      // circular toggles, pills

    // ── Shadows ──
    const val shadowCardElevation: Float = 12f
    const val shadowCardAlpha: Float = 0.15f
    const val shadowFloatingElevation: Float = 24f
    const val shadowFloatingAlpha: Float = 0.25f

    // ── Typography (sp & alpha) ──
    const val typeDisplayClockSp: Float = 72f       // Thin (200), clock
    const val typeDisplayHeroSp: Float = 34f        // Bold (700), large headers
    const val typeTitleSp: Float = 20f              // SemiBold (600), sheet titles
    const val typeHeadingSp: Float = 17f            // SemiBold (600), card headers
    const val typeBodySp: Float = 15f               // Regular (400), primary text
    const val typeCaptionSp: Float = 13f            // Regular (400), secondary text
    const val typeCaptionAlpha: Float = 0.70f
    const val typeSectionHeaderSp: Float = 13f      // SemiBold (600), drawer section headers
    const val typeSectionHeaderAlpha: Float = 0.50f
    const val typeMicroSp: Float = 11f              // Medium (500), timestamps, badges
    const val typeMicroAlpha: Float = 0.60f
    const val typeAppLabelSp: Float = 11f           // Regular (400), icon labels
    const val typeAppLabelAlpha: Float = 0.85f

    // ── Spacing (strict 8dp grid, with 4dp half-step & 12/20dp optical steps) ──
    const val spaceXXS: Float = 4f
    const val spaceXS: Float = 8f
    const val spaceSM: Float = 12f
    const val spaceMD: Float = 16f
    const val spaceLG: Float = 20f
    const val spaceXL: Float = 24f
    const val spaceXXL: Float = 32f
    const val spaceHero: Float = 48f
    const val screenMarginHorizontal: Float = 24f   // UNIVERSAL left/right margin
    const val cardPadding: Float = 16f
    const val cardGap: Float = 12f

    // ── Icon Sizes (dp) ──
    const val iconInline: Float = 18f
    const val iconAction: Float = 24f
    const val iconTile: Float = 28f
    const val iconNotification: Float = 36f
    const val iconApp: Float = 56f                  // Home, Drawer, Dock, Folder app icons
    const val iconAppInt: Int = 56
    const val iconFolderPreview: Float = 56f        // 2x2 mini grid inside

    // ── Grid ──
    const val gridColumnsNarrow: Int = 4            // phones < 410dp
    const val gridColumnsWide: Int = 5              // phones >= 410dp
    const val gridWideBreakpointDp: Int = 410
    const val gridIconLabelGap: Float = 6f
    const val gridRowMinHeight: Float = 84f
    const val dockIconCount: Int = 4

    // ── Component Heights & Geometry (dp) ──
    const val dockHeight: Float = 88f
    const val dockBottomMargin: Float = 12f         // above gesture bar
    const val dockInternalPaddingH: Float = 16f
    const val dockInternalPaddingV: Float = 16f
    const val searchBarHeight: Float = 44f
    const val quickTileHeight: Float = 64f
    const val volumePillWidth: Float = 48f
    const val volumePillHeight: Float = 180f
    const val sliderTrackHeight: Float = 36f
    const val notificationCardMinHeight: Float = 68f
    const val alphaScrubberVisualWidth: Float = 24f
    const val alphaScrubberTouchWidth: Float = 48f  // >= 48dp accessible touch target
    const val minTouchTarget: Float = 48f
    const val folderWidth: Float = 240f
    const val folderMinHeight: Float = 280f
    const val folderColumns: Int = 3
    const val folderMaxPerPage: Int = 9
    const val folderScrimAlpha: Float = 0.40f

    // ── Spring Physics (ONE model for the entire OS) ──
    const val springDamping: Float = 0.78f
    const val springStiffness: Float = 280f
    const val staggerDelayMs: Long = 15L
    const val staggerInitialScale: Float = 0.90f
    const val pressScale: Float = 0.92f
    const val pressAlpha: Float = 0.85f
    const val reducedMotionCrossfadeMs: Long = 200L
    const val transitionBlurScale: Float = 0.50f
    const val wallpaperBlurOverscan: Float = 1.50f
    const val backgroundCacheReleaseDelayMs: Long = 30_000L
    const val wallpaperChangeDebounceMs: Long = 200L
    const val drawerItemViewCacheSize: Int = 20
    const val drawerRecycledPoolMax: Int = 32

    // ── Depth Layers ──
    const val depthWallpaper: Float = 0f
    const val depthHome: Float = 1f
    const val depthFolder: Float = 2f
    const val depthDrawer: Float = 2.5f
    const val depthShade: Float = 3f
    const val depthControlCenter: Float = 3f
    const val depthLock: Float = 4f
    const val depthModal: Float = 5f
    const val depthLaunchPortal: Float = 6f

    // ── Luminance Polarity Threshold ──
    const val darkModeLuminanceThreshold: Float = 0.40f

    // ── View Dimension Helpers ──
    fun dpPx(density: Float, dpValue: Float): Float = dpValue * density
    fun dpInt(density: Float, dpValue: Float): Int = (dpValue * density).roundToInt()
    fun dpInt(context: Context, dpValue: Float): Int =
        (dpValue * context.resources.displayMetrics.density).roundToInt()

    fun spPx(metrics: DisplayMetrics, spValue: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, spValue, metrics)

    /** Compose-typed token projections backed by the exact same constants. */
    object Compose {
        val blurRadiusSurface: Dp = LumaTokens.blurRadiusSurface.dp
        val blurRadiusOverlay: Dp = LumaTokens.blurRadiusOverlay.dp
        val blurRadiusBackground: Dp = LumaTokens.blurRadiusBackground.dp
        val glassBorderWidth: Dp = LumaTokens.glassBorderWidth.dp

        val radiusSmall: Dp = LumaTokens.radiusSmall.dp
        val radiusMedium: Dp = LumaTokens.radiusMedium.dp
        val radiusLarge: Dp = LumaTokens.radiusLarge.dp
        val radiusFolder: Dp = LumaTokens.radiusFolder.dp
        val radiusXLarge: Dp = LumaTokens.radiusXLarge.dp
        val radiusFull: Dp = LumaTokens.radiusFull.dp

        val shadowCardElevation: Dp = LumaTokens.shadowCardElevation.dp
        val shadowFloatingElevation: Dp = LumaTokens.shadowFloatingElevation.dp

        val typeDisplayClockSize: TextUnit = LumaTokens.typeDisplayClockSp.sp
        val typeDisplayClockWeight: FontWeight = FontWeight.Thin
        val typeDisplayHeroSize: TextUnit = LumaTokens.typeDisplayHeroSp.sp
        val typeDisplayHeroWeight: FontWeight = FontWeight.Bold
        val typeTitleSize: TextUnit = LumaTokens.typeTitleSp.sp
        val typeTitleWeight: FontWeight = FontWeight.SemiBold
        val typeHeadingSize: TextUnit = LumaTokens.typeHeadingSp.sp
        val typeHeadingWeight: FontWeight = FontWeight.SemiBold
        val typeBodySize: TextUnit = LumaTokens.typeBodySp.sp
        val typeBodyWeight: FontWeight = FontWeight.Normal
        val typeCaptionSize: TextUnit = LumaTokens.typeCaptionSp.sp
        val typeCaptionWeight: FontWeight = FontWeight.Normal
        val typeSectionHeaderSize: TextUnit = LumaTokens.typeSectionHeaderSp.sp
        val typeSectionHeaderWeight: FontWeight = FontWeight.SemiBold
        val typeMicroSize: TextUnit = LumaTokens.typeMicroSp.sp
        val typeMicroWeight: FontWeight = FontWeight.Medium
        val typeAppLabelSize: TextUnit = LumaTokens.typeAppLabelSp.sp
        val typeAppLabelWeight: FontWeight = FontWeight.Normal

        val spaceXXS: Dp = LumaTokens.spaceXXS.dp
        val spaceXS: Dp = LumaTokens.spaceXS.dp
        val spaceSM: Dp = LumaTokens.spaceSM.dp
        val spaceMD: Dp = LumaTokens.spaceMD.dp
        val spaceLG: Dp = LumaTokens.spaceLG.dp
        val spaceXL: Dp = LumaTokens.spaceXL.dp
        val spaceXXL: Dp = LumaTokens.spaceXXL.dp
        val spaceHero: Dp = LumaTokens.spaceHero.dp
        val screenMarginHorizontal: Dp = LumaTokens.screenMarginHorizontal.dp
        val cardPadding: Dp = LumaTokens.cardPadding.dp
        val cardGap: Dp = LumaTokens.cardGap.dp

        val iconInline: Dp = LumaTokens.iconInline.dp
        val iconAction: Dp = LumaTokens.iconAction.dp
        val iconTile: Dp = LumaTokens.iconTile.dp
        val iconNotification: Dp = LumaTokens.iconNotification.dp
        val iconApp: Dp = LumaTokens.iconApp.dp
        val iconFolderPreview: Dp = LumaTokens.iconFolderPreview.dp

        val gridIconLabelGap: Dp = LumaTokens.gridIconLabelGap.dp
        val gridRowMinHeight: Dp = LumaTokens.gridRowMinHeight.dp
        val dockHeight: Dp = LumaTokens.dockHeight.dp
        val dockBottomMargin: Dp = LumaTokens.dockBottomMargin.dp
        val searchBarHeight: Dp = LumaTokens.searchBarHeight.dp
        val quickTileHeight: Dp = LumaTokens.quickTileHeight.dp
        val volumePillWidth: Dp = LumaTokens.volumePillWidth.dp
        val volumePillHeight: Dp = LumaTokens.volumePillHeight.dp
        val sliderTrackHeight: Dp = LumaTokens.sliderTrackHeight.dp
        val notificationCardMinHeight: Dp = LumaTokens.notificationCardMinHeight.dp
        val minTouchTarget: Dp = LumaTokens.minTouchTarget.dp
    }
}
