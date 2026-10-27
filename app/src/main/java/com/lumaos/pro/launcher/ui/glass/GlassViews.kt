package com.lumaos.pro.launcher.ui.glass

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewOutlineProvider
import com.lumaos.pro.launcher.effects.EffectsDecision
import com.lumaos.pro.launcher.ui.theme.GlassBackdrop
import com.lumaos.pro.launcher.ui.theme.GlassCapabilities
import com.lumaos.pro.launcher.ui.theme.GlassMaterials
import com.lumaos.pro.launcher.ui.theme.GlassPalette
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.GlassSpec
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme

/** View-side adapter for the shared Liquid Glass material. */
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

    /** One drawable, optionally wrapped in a state-aware bounded ripple. */
    fun drawable(spec: GlassSpec, density: Float, ripple: Boolean = false): Drawable {
        val glass = GlassDrawable(spec, density)
        if (!ripple) return glass
        return RippleDrawable(
            ColorStateList.valueOf(spec.rest.ripple),
            glass,
            mask(spec, density)
        )
    }

    /**
     * Applies a surface to a View. Elevation is a single compositor layer, not
     * a stack of translucent shadows. Clipping is avoided on top-rounded sheets
     * because their bottom corners are intentionally square.
     */
    fun apply(
        view: View,
        spec: GlassSpec,
        density: Float,
        ripple: Boolean = false,
        clipToOutline: Boolean = !spec.topOnly
    ) {
        view.background = drawable(spec, density, ripple)
        view.elevation = spec.elevationDp * density
        view.outlineProvider = ViewOutlineProvider.BACKGROUND
        view.clipToOutline = clipToOutline
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
}
