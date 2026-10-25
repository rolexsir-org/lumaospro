package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.graphics.Typeface
import com.lumaos.pro.launcher.ui.theme.FontFace
import com.lumaos.pro.launcher.ui.theme.LumaFonts
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
    fun label(style: TypeStyle): Typeface = of(style.label)
    fun body(style: TypeStyle): Typeface = of(style.body)
    fun readout(style: TypeStyle): Typeface = of(style.readout)
    fun title(style: TypeStyle): Typeface = of(style.title)

    /** Used when the active asset set changes; not needed for normal theming (faces are immutable). */
    fun clear() = cache.clear()
}
