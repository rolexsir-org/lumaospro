package com.lumaos.pro.launcher.vault.ui

import android.content.Context
import android.graphics.drawable.Drawable
import com.lumaos.pro.launcher.R

/**
 * Vault's icons.
 *
 * The launcher already ships a hand-drawn "living icons" set
 * (the PNGs under `assets/living_icons`), and Vault reuses those drawings rather than
 * introducing a second icon language: the private camera looks like the camera the
 * user already recognises, the private gallery like Photos, and so on. Anything
 * without an equivalent in that set (the security centre, the scanner) borrows the
 * closest drawing rather than inventing a new visual vocabulary.
 */
object VaultIcons {

    private val cache = HashMap<String, Drawable?>()

    fun of(context: Context, name: String): Drawable? = cache.getOrPut(name) {
        runCatching {
            context.assets.open("living_icons/$name.png").use { stream ->
                Drawable.createFromStream(stream, null)
            }
        }.getOrNull()
    }

    fun camera(context: Context) = of(context, "camera")
    fun gallery(context: Context) = of(context, "photos")
    fun files(context: Context) = of(context, "files")
    fun notes(context: Context) = of(context, "notes")
    fun recorder(context: Context) = of(context, "recorder")
    fun scanner(context: Context) = of(context, "notes")
    fun security(context: Context) = of(context, "settings")

    /**
     * The eye-with-a-slash vector from the launcher's icon set, tinted to match the
     * text colour so it sits beside the other Vault tiles. This one is a vector
     * (there is no living_icons drawing for it), which is why it is tinted here
     * rather than loaded as a finished PNG.
     */
    fun hiddenApps(context: Context): Drawable? = runCatching {
        context.getDrawable(R.drawable.ic_rx_hide)
            ?.mutate()
            ?.apply { setTint(VaultDesign.colorTextPrimary) }
    }.getOrNull()
    fun video(context: Context) = of(context, "music")
    fun document(context: Context) = of(context, "downloads")

    /** The share of the icon cache that Vault actually needs. */
    fun clear() = cache.clear()
}
