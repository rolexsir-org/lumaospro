package com.lumaos.pro.launcher.icons

import com.lumaos.pro.launcher.ui.theme.IconShape
import com.lumaos.pro.launcher.ui.theme.IconStyle
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeEnvironment
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies optical alignment, non-clipping margins, aspect-ratio preservation,
 * and theme/tint invariants for Living Icons in [IconRenderer] and [IconRenderSpec].
 */
class IconRendererLivingBoundsTest {

    @Test
    fun `computeLivingBounds centers all 30 Living Icons without clipping or aspect distortion`() {
        val sizes = listOf(48, 96, 144, 192, 256)
        for (size in sizes) {
            val s = size.toFloat()
            val pad = s * 0.06f
            for (depth in listOf(true, false)) {
                val baseLeft = pad
                val baseRight = s - pad
                val targetLeft = if (depth) baseLeft else {
                    val half = (baseRight - baseLeft) * 0.94f * 0.5f
                    s * 0.5f - half
                }
                val targetRight = if (depth) baseRight else {
                    val half = (baseRight - baseLeft) * 0.94f * 0.5f
                    s * 0.5f + half
                }
                val expectedVisibleSpan = targetRight - targetLeft
                for (id in LivingIconId.entries) {
                    val b = IconRenderer.computeLivingBounds(
                        iconId = id,
                        targetLeft = targetLeft,
                        targetTop = targetLeft,
                        targetRight = targetRight,
                        targetBottom = targetRight
                    )
                    val bLeft = b[0]
                    val bTop = b[1]
                    val bRight = b[2]
                    val bBottom = b[3]
                    val drawnWidth = bRight - bLeft
                    val drawnHeight = bBottom - bTop
                    // 1:1 square master canvas preserved
                    assertTrue(
                        "Aspect distortion for ${id.name} at ${size}px: $drawnWidth vs $drawnHeight",
                        abs(drawnWidth - drawnHeight) <= 0.01f
                    )

                    // Project the asset's measured non-transparent pixels into output bitmap space
                    val scaleX = drawnWidth / LivingIconId.MASTER_CANVAS_PX
                    val scaleY = drawnHeight / LivingIconId.MASTER_CANVAS_PX
                    val visLeftPx = bLeft + id.visibleLeft * scaleX
                    val visTopPx = bTop + id.visibleTop * scaleY
                    val visRightPx = bLeft + id.visibleRight * scaleX
                    val visBottomPx = bTop + id.visibleBottom * scaleY

                    // Non-transparent silhouette must NEVER clip outside [0, size]
                    assertTrue("Left clipped for ${id.name} ($visLeftPx)", visLeftPx >= 1f)
                    assertTrue("Top clipped for ${id.name} ($visTopPx)", visTopPx >= 1f)
                    assertTrue("Right clipped for ${id.name} ($visRightPx > $size)", visRightPx <= s - 1f)
                    assertTrue("Bottom clipped for ${id.name} ($visBottomPx > $size)", visBottomPx <= s - 1f)

                    // Visible span matches standard LumaOS Pro icon silhouette within rounding tolerance
                    val actualVisSpan = maxOf(visRightPx - visLeftPx, visBottomPx - visTopPx)
                    assertEquals(expectedVisibleSpan, actualVisSpan, 0.25f)

                    // Optical center aligns to canvas midpoint within 0.5px
                    val visCenterX = (visLeftPx + visRightPx) * 0.5f
                    val visCenterY = (visTopPx + visBottomPx) * 0.5f
                    assertEquals(s * 0.5f, visCenterX, 0.5f)
                    assertEquals(s * 0.5f, visCenterY, 0.5f)
                }
            }
        }
    }

    @Test
    fun `IconRenderSpec preserves Living Icon colors across dark and light themes unless user explicitly selects TINTED`() {
        for (themeId in ThemeId.entries) {
            for (dark in listOf(true, false)) {
                val baseSettings = ThemeSettings(themeId = themeId, iconShape = IconShape.SQUIRCLE)
                val resolved = ThemeEngine.resolve(baseSettings, ThemeEnvironment(systemDark = dark, minuteOfDay = 720))
                val defaultSpec = IconRenderSpec.from(
                    theme = resolved,
                    sizePx = 144,
                    depth = true,
                    iconPack = null,
                    livingIcons = true,
                    livingOverridesHash = 0,
                    explicitTint = baseSettings.iconStyle == IconStyle.TINTED
                )
                assertFalse(
                    "Theme ${themeId.name} (dark=$dark) must not indiscriminately recolor Living Icons by default",
                    defaultSpec.explicitTint
                )

                val userTintedSettings = baseSettings.copy(iconStyle = IconStyle.TINTED)
                val userTintedResolved = ThemeEngine.resolve(userTintedSettings, ThemeEnvironment(systemDark = dark, minuteOfDay = 720))
                val explicitTintSpec = IconRenderSpec.from(
                    theme = userTintedResolved,
                    sizePx = 144,
                    depth = true,
                    iconPack = null,
                    livingIcons = true,
                    livingOverridesHash = 0,
                    explicitTint = userTintedSettings.iconStyle == IconStyle.TINTED
                )
                assertTrue(explicitTintSpec.explicitTint)
                assertNotEquals(defaultSpec, explicitTintSpec)
            }
        }
    }

    @Test
    fun `IconRenderSpec equality invalidates cache when Living Icons toggle or overrides hash changes`() {
        val resolved = ThemeEngine.resolve(ThemeSettings(), ThemeEnvironment(systemDark = true, minuteOfDay = 600))
        val enabledSpec = IconRenderSpec.from(resolved, 144, depth = true, iconPack = null, livingIcons = true, livingOverridesHash = 0)
        val disabledSpec = IconRenderSpec.from(resolved, 144, depth = true, iconPack = null, livingIcons = false, livingOverridesHash = 0)
        val overriddenSpec = IconRenderSpec.from(resolved, 144, depth = true, iconPack = null, livingIcons = true, livingOverridesHash = 42)

        assertNotEquals(enabledSpec, disabledSpec)
        assertNotEquals(enabledSpec, overriddenSpec)
    }
}
