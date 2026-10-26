package com.lumaos.pro.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Parity between the Design System v3.2 bundle in `docs/design/v3.2/tokens/`
 * and the Kotlin transcription in [LumaDesignTokens].
 *
 * This is the test that keeps the design source of truth and the shipped app from
 * drifting: if someone edits a hex value in `signature.json` and not in Kotlin (or
 * the reverse), this fails with the exact token name.
 *
 * No JSON library is added for this. The bundle is machine-exported with a fixed
 * shape, so a brace-aware field reader is enough, and it keeps the dependency
 * list of a launcher that promises no network and no analytics unchanged.
 */
class DesignTokensParityTest {

    private val dir: File = System.getProperty("lumaos.designTokensDir")?.let(::File)
        ?: File("../docs/design/v3.2/tokens")

    private fun themeFile(key: String): String {
        val f = File(dir, "$key.json")
        assertTrue("Missing design token file: ${f.absolutePath}", f.isFile)
        return f.readText()
    }

    // ------------------------------------------------------------- json helpers

    /** Body of `"name": { ... }`, with nested braces balanced. */
    private fun section(json: String, name: String): String {
        val key = "\"$name\""
        val at = json.indexOf(key)
        assertTrue("section $name not found", at >= 0)
        val open = json.indexOf('{', at)
        var depth = 0
        var i = open
        while (i < json.length) {
            when (json[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return json.substring(open + 1, i)
                }
            }
            i++
        }
        error("unbalanced JSON section $name")
    }

    /** Raw JSON value (quoted string or bare number) for [key] inside [block]. */
    private fun raw(block: String, key: String): String {
        val match = requireNotNull(
            Regex("\"${Regex.escape(key)}\"\\s*:\\s*(\"([^\"]*)\"|(-?[0-9.]+))").find(block)
        ) { "key $key not found in token file" }
        return match.groupValues[2].ifEmpty { match.groupValues[3] }
    }

    /** `"#RRGGBB"` or `"rgba(r,g,b,a)"` as packed ARGB, matching ColorMath's rounding. */
    private fun argb(value: String): Int = when {
        value.startsWith("#") -> (0xFF000000L or value.drop(1).toLong(16)).toInt()
        value.startsWith("rgba(") -> {
            val parts = value.removePrefix("rgba(").removeSuffix(")").split(",").map { it.trim() }
            ColorMath.withAlpha(
                ColorMath.argb(255, parts[0].toInt(), parts[1].toInt(), parts[2].toInt()),
                parts[3].toFloat()
            )
        }
        else -> error("Unsupported colour token: $value")
    }

    private fun px(value: String): Float = value.removeSuffix("px").toFloat()

    private fun percent(value: String): Float = value.removeSuffix("%").toFloat() / 100f

    // ------------------------------------------------------------------ colours

    @Test fun `colour roles match the bundle exactly`() {
        for (tokens in LumaDesignTokens.ALL) {
            val json = themeFile(tokens.key)
            val background = section(json, "background")
            val surface = section(json, "surface")
            val stroke = section(json, "stroke")
            val text = section(json, "text")
            val accent = section(json, "accent")
            val status = section(json, "status")
            val c = tokens.colors

            assertEquals("${tokens.key} bg0", argb(raw(background, "bg0")), c.background0)
            assertEquals("${tokens.key} bg1", argb(raw(background, "bg1")), c.background1)
            assertEquals("${tokens.key} bg2", argb(raw(background, "bg2")), c.background2)
            assertEquals("${tokens.key} surface-1", argb(raw(surface, "s1")), c.surface1)
            assertEquals("${tokens.key} surface-2", argb(raw(surface, "s2")), c.surface2)
            assertEquals("${tokens.key} surface-3", argb(raw(surface, "s3")), c.surface3)
            assertEquals("${tokens.key} surface-solid", argb(raw(surface, "solid")), c.surfaceSolid)
            assertEquals("${tokens.key} stroke-1", argb(raw(stroke, "s1")), c.stroke1)
            assertEquals("${tokens.key} stroke-2", argb(raw(stroke, "s2")), c.stroke2)
            assertEquals("${tokens.key} text-1", argb(raw(text, "t1")), c.text1)
            assertEquals("${tokens.key} text-2", argb(raw(text, "t2")), c.text2)
            assertEquals("${tokens.key} text-3", argb(raw(text, "t3")), c.text3)
            assertEquals("${tokens.key} accent", argb(raw(accent, "primary")), c.accent)
            assertEquals("${tokens.key} accent-2", argb(raw(accent, "secondary")), c.accent2)
            assertEquals("${tokens.key} accent-soft", argb(raw(accent, "soft")), c.accentSoft)
            assertEquals("${tokens.key} ok", argb(raw(status, "ok")), c.ok)
            assertEquals("${tokens.key} warn", argb(raw(status, "warn")), c.warn)
            assertEquals("${tokens.key} danger", argb(raw(status, "danger")), c.danger)
        }
    }

    // ------------------------------------------------------------ shape/material

    @Test fun `radius and tile geometry match the bundle`() {
        for (tokens in LumaDesignTokens.ALL) {
            val shape = section(themeFile(tokens.key), "shape")
            val s = tokens.shape
            assertEquals("${tokens.key} r-xl", px(raw(shape, "r-xl")), s.extraLarge, 0f)
            assertEquals("${tokens.key} r-lg", px(raw(shape, "r-lg")), s.large, 0f)
            assertEquals("${tokens.key} r-md", px(raw(shape, "r-md")), s.medium, 0f)
            assertEquals("${tokens.key} r-sm", px(raw(shape, "r-sm")), s.small, 0f)
            assertEquals("${tokens.key} r-xs", px(raw(shape, "r-xs")), s.extraSmall, 0f)
            assertEquals("${tokens.key} dock", px(raw(shape, "dock")), s.dockRadius, 0f)
            val tile = section(shape, "iconTile")
            assertEquals("${tokens.key} tile size", px(raw(tile, "size")), s.tileSize, 0f)
            assertEquals("${tokens.key} tile radius", px(raw(tile, "radius")), s.tileRadius, 0f)
        }
    }

    @Test fun `material blur and saturation match the bundle`() {
        for (tokens in LumaDesignTokens.ALL) {
            val material = section(themeFile(tokens.key), "material")
            assertEquals("${tokens.key} blur", px(raw(material, "blur")), tokens.material.blurDp, 0f)
            assertEquals(
                "${tokens.key} saturation",
                percent(raw(material, "saturation")),
                tokens.material.saturation,
                1e-6f
            )
            assertEquals("${tokens.key} usesBlur", tokens.material.blurDp > 0f, tokens.material.usesBlur)
        }
    }

    // ---------------------------------------------------------------- type/spacing

    @Test fun `type scale matches the bundle`() {
        val scale = section(section(themeFile("signature"), "type"), "scale")
        val t = LumaDesignTokens.TYPE_SCALE
        assertEquals("clock", px(raw(scale, "clock")), t.clock, 0f)
        assertEquals("screenTitle", px(raw(scale, "screenTitle")), t.screenTitle, 0f)
        assertEquals("sectionHeading", px(raw(scale, "sectionHeading")), t.sectionHeading, 0f)
        assertEquals("widgetHeading", px(raw(scale, "widgetHeading")), t.widgetHeading, 0f)
        assertEquals("primary", px(raw(scale, "primary")), t.primary, 0f)
        assertEquals("appLabel", px(raw(scale, "appLabel")), t.appLabel, 0f)
        assertEquals("supporting", px(raw(scale, "supporting")), t.supporting, 0f)
        assertEquals("numeric", px(raw(scale, "numeric")), t.numeric, 0f)
        assertEquals("button", px(raw(scale, "button")), t.button, 0f)
        assertEquals("kicker", px(raw(scale, "kicker")), t.kicker, 0f)
    }

    @Test fun `spacing scale matches the bundle`() {
        val spacing = section(themeFile("signature"), "spacing")
        for (step in 1..11) {
            assertEquals(
                "sp-$step",
                px(raw(spacing, "sp-$step")),
                LumaDesignTokens.spacing(step),
                0f
            )
        }
    }

    @Test fun `motion tokens match the bundle`() {
        val motion = section(themeFile("signature"), "motion")
        assertEquals(px(raw(motion, "dur1")).toInt(), LumaDesignTokens.Motion.DUR_1)
        assertEquals(px(raw(motion, "dur2")).toInt(), LumaDesignTokens.Motion.DUR_2)
        assertEquals(px(raw(motion, "dur3")).toInt(), LumaDesignTokens.Motion.DUR_3)
        assertEquals(px(raw(motion, "dur4")).toInt(), LumaDesignTokens.Motion.DUR_4)
        assertEquals(px(raw(motion, "press")).toInt(), LumaDesignTokens.Motion.PRESS)
        assertEquals(px(raw(motion, "longPress")).toInt(), LumaDesignTokens.Motion.LONG_PRESS)
        // Easing control points are the authored cubic-beziers.
        assertTrue(LumaDesignTokens.Motion.STANDARD.contentEquals(floatArrayOf(0.22f, 0.61f, 0.36f, 1f)))
        assertTrue(LumaDesignTokens.Motion.EMPHASIS.contentEquals(floatArrayOf(0.16f, 0.84f, 0.28f, 1f)))
        assertTrue(LumaDesignTokens.Motion.EXIT.contentEquals(floatArrayOf(0.4f, 0f, 1f, 1f)))
    }

    @Test fun `bundle version matches`() {
        val meta = section(themeFile("signature"), "meta")
        assertEquals(LumaDesignTokens.VERSION, raw(meta, "version"))
        assertEquals("signature", raw(meta, "theme"))
    }
}
