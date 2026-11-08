package com.lumaos.pro.launcher.vault.ui

import com.lumaos.pro.launcher.ui.theme.LumaTokens
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vault is part of LumaOS Pro, so its screens must not drift into a separate design
 * system. `VaultDesign` mirrors the launcher's token values because it is plain-View
 * code; this test fails the build the moment the two disagree, which makes the
 * duplication safe rather than sloppy.
 *
 * (This test needs the Compose-enabled main sources, so it runs in Gradle; the
 * sandbox harness skips it because Compose is not resolvable without Google Maven.)
 */
class VaultDesignParityTest {

    @Test
    fun radiiMatchTheLauncherTokens() {
        assertEquals(LumaTokens.radiusSmall, VaultDesign.radiusSmall, 0.001f)
        assertEquals(LumaTokens.radiusMedium, VaultDesign.radiusMedium, 0.001f)
        assertEquals(LumaTokens.radiusLarge, VaultDesign.radiusLarge, 0.001f)
        assertEquals(LumaTokens.radiusXLarge, VaultDesign.radiusXLarge, 0.001f)
        assertEquals(LumaTokens.radiusFull, VaultDesign.radiusFull, 0.001f)
    }

    @Test
    fun spacingAndTypeMatchTheLauncherTokens() {
        assertEquals(LumaTokens.spaceXXS, VaultDesign.spaceXXS, 0.001f)
        assertEquals(LumaTokens.spaceXS, VaultDesign.spaceXS, 0.001f)
        assertEquals(LumaTokens.spaceSM, VaultDesign.spaceSM, 0.001f)
        assertEquals(LumaTokens.spaceMD, VaultDesign.spaceMD, 0.001f)
        assertEquals(LumaTokens.spaceLG, VaultDesign.spaceLG, 0.001f)
        assertEquals(LumaTokens.spaceXL, VaultDesign.spaceXL, 0.001f)
        assertEquals(LumaTokens.spaceXXL, VaultDesign.spaceXXL, 0.001f)
        assertEquals(LumaTokens.screenMarginHorizontal, VaultDesign.screenMarginHorizontal, 0.001f)
        assertEquals(LumaTokens.minTouchTarget, VaultDesign.minTouchTarget, 0.001f)

        assertEquals(LumaTokens.typeDisplayHeroSp, VaultDesign.typeDisplayHeroSp, 0.001f)
        assertEquals(LumaTokens.typeTitleSp, VaultDesign.typeTitleSp, 0.001f)
        assertEquals(LumaTokens.typeHeadingSp, VaultDesign.typeHeadingSp, 0.001f)
        assertEquals(LumaTokens.typeBodySp, VaultDesign.typeBodySp, 0.001f)
        assertEquals(LumaTokens.typeCaptionSp, VaultDesign.typeCaptionSp, 0.001f)
        assertEquals(LumaTokens.typeSectionHeaderSp, VaultDesign.typeSectionHeaderSp, 0.001f)
        assertEquals(LumaTokens.typeMicroSp, VaultDesign.typeMicroSp, 0.001f)
    }

    @Test
    fun accentMatchesTheLauncherAccentColour() {
        // #9AD3FF is res/values/colors.xml lumaos_accent; Vault keeps the same accent
        // so the private space reads as part of the same product.
        assertEquals(0xFF9AD3FF.toInt(), VaultDesign.colorAccent)
    }
}
