package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import com.lumaos.pro.launcher.ui.theme.FontFace
import com.lumaos.pro.launcher.ui.theme.FontFamilyId
import com.lumaos.pro.launcher.ui.theme.FontWeightToken
import com.lumaos.pro.launcher.ui.theme.LumaFonts
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemePresets
import com.lumaos.pro.launcher.ui.theme.TypeStyle
import java.util.concurrent.ConcurrentHashMap

/**
 * View-system access to the bundled typefaces.
 *
 * [install] is called once from `LumaApp` with the application context; every
 * face is then resolved and cached by [LumaFonts]. If installation never
 * happened (a bare unit-test harness), the platform fallback of the same
 * temperament is returned instead of crashing.
 */
object Typefaces {

    @Volatile
    private var appContext: Context? = null

    private val cache = ConcurrentHashMap<FontFace, Typeface>()

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun of(face: FontFace): Typeface {
        val ctx = appContext
        if (ctx == null) return cache.getOrPut(face) { LumaFonts.fallback(face) }
        return cache.getOrPut(face) { LumaFonts.typeface(ctx, face) }
    }

    fun clock(style: TypeStyle): Typeface = of(style.clock)

    /**
     * The home-clock face for [theme]. While the theme speaks in its own default
     * voice, the approved Phase 2 numerals apply: Ivory "Porcelain" uses light
     * serif numerals and Obsidian "Onyx & Ember" uses hairline weight-200
     * numerals. An explicit typography choice by the user always wins.
     */
    fun clock(theme: ResolvedTheme): Typeface {
        val style = theme.typography
        if (style != ThemePresets.of(theme.id).typography) return clock(style)
        return when (theme.id) {
            ThemeId.IVORY -> of(FontFace(FontFamilyId.SYSTEM_SERIF, FontWeightToken.LIGHT))
            ThemeId.OBSIDIAN -> hairline
            else -> clock(style)
        }
    }

    /** Weight-200 sans; API 26-27 fall back to the platform thin face. */
    private val hairline: Typeface by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SANS_SERIF, 200, false)
        } else {
            Typeface.create("sans-serif-thin", Typeface.NORMAL)
        }
    }
    fun label(style: TypeStyle): Typeface = of(style.label)
    fun body(style: TypeStyle): Typeface = of(style.body)
    fun readout(style: TypeStyle): Typeface = of(style.readout)
    fun title(style: TypeStyle): Typeface = of(style.title)

    /** Used when the active asset set changes; not needed for normal theming (faces are immutable). */
    fun clear() = cache.clear()
}
