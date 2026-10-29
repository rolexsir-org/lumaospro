package com.lumaos.pro.launcher.ui.glass

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.effects.EffectsDecision
import com.lumaos.pro.launcher.ui.theme.GlassBackdrop
import com.lumaos.pro.launcher.ui.theme.GlassCapabilities
import com.lumaos.pro.launcher.ui.theme.GlassMaterials
import com.lumaos.pro.launcher.ui.theme.GlassPalette
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.GlassSpec
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme

/**
 * View-side adapter for the unified 6-layer Luma Glass material.
 *
 * Automatically samples the cached 1/8 downsampled wallpaper luminance behind
 * each View's window bounds (+8% over >0.7 bright regions, -5% over <0.2 dark
 * regions) and aligns API 29–30 pre-blurred wallpaper coordinates with zero
 * main-thread bitmap allocations.
 */
object GlassViews {

    fun capabilities(effects: EffectsDecision): GlassCapabilities = GlassCapabilities(
        translucency = effects.translucency,
        blur = effects.blur,
        depth = effects.iconDepth
    )

    fun palette(theme: ResolvedTheme, effects: EffectsDecision): GlassPalette =
        GlassMaterials.palette(theme, capabilities(effects))

    fun spec(theme: ResolvedTheme, effects: EffectsDecision, role: GlassRole): GlassSpec =
        palette(theme, effects)[role]

    fun drawable(
        spec: GlassSpec,
        density: Float,
        ripple: Boolean = false,
        depth: Float = GlassDrawable.defaultDepthForRole(spec.role)
    ): Drawable {
        val glass = GlassDrawable(spec, density, depth)
        if (!ripple) return glass
        return RippleDrawable(
            ColorStateList.valueOf(spec.rest.ripple),
            glass,
            mask(spec, density)
        )
    }

    fun apply(
        view: View,
        spec: GlassSpec,
        density: Float,
        ripple: Boolean = false,
        clipToOutline: Boolean = !spec.topOnly,
        depth: Float = GlassDrawable.defaultDepthForRole(spec.role),
        radiusOverrideDp: Float? = null
    ) {
        val effectiveSpec = if (radiusOverrideDp != null) spec.copy(radiusDp = radiusOverrideDp) else spec
        val bg = drawable(effectiveSpec, density, ripple, depth)
        view.background = bg
        view.elevation = effectiveSpec.elevationDp * density
        view.outlineProvider = ViewOutlineProvider.BACKGROUND
        view.clipToOutline = clipToOutline
        unwrapGlassDrawable(bg)?.let { attachRegionSampler(view, it) }
    }

    /** A quiet vertical veil that keeps text readable over light or dark artwork. */
    fun scrim(theme: ResolvedTheme): Drawable {
        val s = GlassBackdrop.scrim(theme)
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(s.top, s.center, s.bottom)
        )
    }

    private fun mask(spec: GlassSpec, density: Float): Drawable = GradientDrawable().apply {
        setColor(Color.WHITE)
        val r = spec.radiusDp * density
        cornerRadii = if (spec.topOnly) {
            floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        } else {
            FloatArray(8) { r }
        }
    }

    private fun unwrapGlassDrawable(drawable: Drawable?): GlassDrawable? = when (drawable) {
        is GlassDrawable -> drawable
        is RippleDrawable -> (0 until drawable.numberOfLayers)
            .firstNotNullOfOrNull { drawable.getDrawable(it) as? GlassDrawable }
        else -> null
    }

    private fun attachRegionSampler(view: View, glassDrawable: GlassDrawable) {
        val loc = IntArray(2)
        fun update() {
            val dm = view.resources.displayMetrics
            val sw = dm.widthPixels.coerceAtLeast(1)
            val sh = dm.heightPixels.coerceAtLeast(1)
            if (view.width <= 0 || view.height <= 0) return
            view.getLocationInWindow(loc)
            val leftNorm = (loc[0].toFloat() / sw).coerceIn(0f, 1f)
            val topNorm = (loc[1].toFloat() / sh).coerceIn(0f, 1f)
            val rightNorm = ((loc[0] + view.width).toFloat() / sw).coerceIn(0f, 1f)
            val bottomNorm = ((loc[1] + view.height).toFloat() / sh).coerceIn(0f, 1f)

            val app = view.context.applicationContext as? LumaApp
            val palette = app?.core?.themeManager?.palette?.value
            val lumDelta = if (palette != null) {
                val regionLum = palette.sampleRegionLuminance(leftNorm, topNorm, rightNorm, bottomNorm)
                when {
                    regionLum > 0.70f -> 0.08f
                    regionLum > 0.55f -> ((regionLum - 0.55f) / 0.15f) * 0.08f
                    regionLum < 0.20f -> -0.05f
                    regionLum < 0.30f -> -((0.30f - regionLum) / 0.10f) * 0.05f
                    else -> 0f
                }
            } else 0f

            glassDrawable.setRegionAdaptation(lumDelta, loc[0], loc[1], sw, sh)
        }

        val existing = view.getTag(R.id.home_root) as? View.OnLayoutChangeListener
        if (existing != null) view.removeOnLayoutChangeListener(existing)
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
        view.setTag(R.id.home_root, listener)
        view.addOnLayoutChangeListener(listener)
        if (view.isLaidOut) update()
    }
}

/**
 * Reusable View-system unified glass container (`FrameLayout` subclass).
 */
class LumaGlassSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var glassDepth: Float = LumaTokens.depthHome
    var cornerRadiusDp: Float = LumaTokens.radiusLarge

    fun bindGlass(
        spec: GlassSpec,
        ripple: Boolean = false,
        depth: Float = glassDepth,
        radiusOverrideDp: Float? = null
    ) {
        glassDepth = depth
        if (radiusOverrideDp != null) cornerRadiusDp = radiusOverrideDp
        GlassViews.apply(
            view = this,
            spec = spec,
            density = resources.displayMetrics.density,
            ripple = ripple,
            clipToOutline = !spec.topOnly,
            depth = depth,
            radiusOverrideDp = radiusOverrideDp
        )
    }

    fun applyBackdropBlurTo(target: View?, blurDp: Float, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < 31 || target == null) return
        if (!enabled || blurDp <= 0.5f) {
            target.setRenderEffect(null)
            return
        }
        val px = (blurDp * resources.displayMetrics.density).coerceAtLeast(1f)
        target.setRenderEffect(RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP))
    }
}
