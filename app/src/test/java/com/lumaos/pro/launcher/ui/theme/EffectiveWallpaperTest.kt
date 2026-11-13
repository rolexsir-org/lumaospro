package com.lumaos.pro.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The per-context wallpaper fallback ladder: a null override follows the
 * shared selection, an empty override explicitly shows the system wallpaper,
 * otherwise the context's own Luma art wins. The recorded system id always
 * tracks whichever scope the id came from, so the external-change detector
 * compares against the right record.
 */
class EffectiveWallpaperTest {

    @Test fun `null override follows the shared selection`() {
        assertEquals("art:aurora", effectiveWallpaperId("art:aurora", null))
        assertEquals(42, effectiveWallpaperSystemId(42, 7, null))
    }

    @Test fun `empty override explicitly uses the system wallpaper`() {
        assertNull(effectiveWallpaperId("art:aurora", ""))
        assertNull(effectiveWallpaperSystemId(42, null, ""))
    }

    @Test fun `a studio override wins over shared`() {
        assertEquals("art:ember", effectiveWallpaperId("art:aurora", "art:ember"))
        // A Luma-home override carries no system id: there is no device image
        // to track, so the detector must not compare against the shared one.
        assertNull(effectiveWallpaperSystemId(42, null, "art:ember"))
    }

    @Test fun `no selection anywhere resolves to nothing`() {
        assertNull(effectiveWallpaperId(null, null))
        assertNull(effectiveWallpaperSystemId(null, null, null))
    }
}
