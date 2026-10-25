package com.lumaos.pro.launcher.launcher.home

import android.graphics.Typeface
import com.lumaos.pro.launcher.ui.theme.TypeStyle

/** Platform typefaces for a [TypeStyle]; created once and cached. */
object Typefaces {
    private val cache = HashMap<String, Typeface>()

    private fun family(name: String): Typeface = synchronized(cache) {
        cache.getOrPut(name) { Typeface.create(name, Typeface.NORMAL) }
    }

    fun clock(style: TypeStyle): Typeface = family(style.clockFamily)
    fun label(style: TypeStyle): Typeface = family(style.labelFamily)
    fun readout(style: TypeStyle): Typeface = family(style.readoutFamily)
    fun title(style: TypeStyle): Typeface = family(style.titleFamily)
}
