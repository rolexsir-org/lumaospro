package com.lumaos.pro.launcher.wallpaper

import com.lumaos.pro.launcher.ui.theme.WallpaperArt

/** Stable IDs shared by the gallery and its persisted favourites. */
data class WallpaperEntry(
    val id: String,
    val title: String,
    val description: String,
    val art: WallpaperArt? = null
) {
    val isCustom: Boolean get() = art == null
}

enum class WallpaperCategory { ALL, LUMA, PHOTOS, FAVORITES }

object WallpaperCatalog {
    private val customName = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    fun bundled(): List<WallpaperEntry> = WallpaperArt.entries.map {
        WallpaperEntry("art:${it.storageKey}", it.displayName, it.character, it)
    }
    fun customId(name: String): String? = name.takeIf(customName::matches)?.let { "photo:$it" }
    fun customName(id: String): String? = id.removePrefix("photo:")
        .takeIf { id.startsWith("photo:") && customName.matches(it) }

    fun filter(entries: List<WallpaperEntry>, category: WallpaperCategory, favorites: Set<String>): List<WallpaperEntry> =
        entries.filter {
            when (category) {
                WallpaperCategory.ALL -> true
                WallpaperCategory.LUMA -> !it.isCustom
                WallpaperCategory.PHOTOS -> it.isCustom
                WallpaperCategory.FAVORITES -> it.id in favorites
            }
        }
}

/** Conservative bounds for untrusted image files and decoded bitmap memory. */
object WallpaperLimits {
    const val MAX_IMPORTS = 12
    const val MAX_FILE_BYTES = 12L * 1024 * 1024
    const val MAX_SOURCE_EDGE = 20_000
    const val MAX_SOURCE_PIXELS = 100_000_000L
    const val MAX_APPLY_EDGE = 2560
    const val MAX_APPLY_PIXELS = 5_000_000L

    fun validSource(width: Int, height: Int): Boolean = width in 1..MAX_SOURCE_EDGE &&
        height in 1..MAX_SOURCE_EDGE && width.toLong() * height <= MAX_SOURCE_PIXELS

    /** Power of two accepted by BitmapFactory; rounded up dimensions stay in budget. */
    fun sampleSize(width: Int, height: Int, maxEdge: Int, maxPixels: Long): Int {
        require(validSource(width, height) && maxEdge > 0 && maxPixels > 0)
        var sample = 1
        fun dimension(n: Int) = (n.toLong() + sample - 1) / sample
        while (dimension(width) > maxEdge || dimension(height) > maxEdge ||
            dimension(width) * dimension(height) > maxPixels) sample *= 2
        return sample
    }
}
