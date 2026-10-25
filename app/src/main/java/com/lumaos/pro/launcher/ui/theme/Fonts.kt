package com.lumaos.pro.launcher.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import com.lumaos.pro.launcher.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves [FontFace]s to bundled assets, with a cached platform fallback.
 *
 * The four Inter weights and three Space Grotesk weights are static instances
 * (not a variable font) so rendering is identical on every supported API from
 * 26 upward. Loading is synchronous and cached: the first home frame may load
 * at most a few faces, and every later surface reuses them.
 */
object LumaFonts {

    private val cache = ConcurrentHashMap<FontFace, Typeface>()

    @FontRes
    fun resId(face: FontFace): Int? = when (face.family) {
        FontFamilyId.INTER -> when (face.weight) {
            FontWeightToken.LIGHT -> R.font.inter_light
            FontWeightToken.REGULAR -> R.font.inter_regular
            FontWeightToken.MEDIUM -> R.font.inter_medium
            FontWeightToken.SEMIBOLD -> R.font.inter_semibold
        }
        FontFamilyId.SPACE_GROTESK -> when (face.weight) {
            FontWeightToken.LIGHT, FontWeightToken.REGULAR -> R.font.space_grotesk_regular
            FontWeightToken.MEDIUM -> R.font.space_grotesk_medium
            FontWeightToken.SEMIBOLD -> R.font.space_grotesk_semibold
        }
        // Platform families have no bundled asset; [fallback] handles them.
        FontFamilyId.SYSTEM_SANS, FontFamilyId.SYSTEM_SERIF, FontFamilyId.SYSTEM_MONO -> null
    }

    fun typeface(context: Context, face: FontFace): Typeface = cache.getOrPut(face) {
        val bundled = resId(face)?.let { id ->
            runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()
        }
        bundled ?: fallback(face)
    }

    /**
     * Platform fallback with a matching temperament. Only reached if a bundled
     * asset is missing from a build; the launcher stays legible either way.
     */
    fun fallback(face: FontFace): Typeface {
        val base = Typeface.create(face.family.platformFallback, Typeface.NORMAL)
        val style = when (face.weight) {
            FontWeightToken.LIGHT, FontWeightToken.REGULAR -> Typeface.NORMAL
            FontWeightToken.MEDIUM -> Typeface.NORMAL
            FontWeightToken.SEMIBOLD -> Typeface.BOLD
        }
        return Typeface.create(base, style)
    }

    /** Clear the cache (used only by tests and on a configuration change). */
    fun clear() = cache.clear()
}
