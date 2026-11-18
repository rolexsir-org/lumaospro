package com.lumaos.pro.launcher.icons

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.PathParser
import com.lumaos.pro.launcher.ui.theme.ThemeId

/** Authored silhouettes in a shared 24-unit grid. Signature keeps its original artwork. */
internal enum class ThemeIconPack(frame: String, val glyphInset: Int, val squareInk: Boolean = false) {
    ORBIT("M12,1 A11,11 0 1,1 11.99,1 Z M12,2.5 A9.5,9.5 0 1,1 11.99,2.5 Z", 5),
    BLOCK("M2,2 H22 V22 H2 Z M4,4 H20 V20 H4 Z", 5, true),
    GEM("M6,1 H18 L23,7 V17 L18,23 H6 L1,17 V7 Z M7,3 L3,8 V16 L7,21 H17 L21,16 V8 L17,3 Z", 5, true),
    BRACKETS("M2,8 V2 H8 V4 H4 V8 Z M16,2 H22 V8 H20 V4 H16 Z M22,16 V22 H16 V20 H20 V16 Z M8,22 H2 V16 H4 V20 H8 Z", 4, true),
    CUT("M1,7 L7,1 H23 V17 L17,23 H1 Z M3,8 V21 H16 L21,16 V3 H8 Z", 5, true),
    PETAL("M12,2 C17,-1 25,7 22,12 C25,17 17,25 12,22 C7,25 -1,17 2,12 C-1,7 7,-1 12,2 Z M12,4 C7,1 1,7 4,12 C1,17 7,23 12,20 C17,23 23,17 20,12 C23,7 17,1 12,4 Z", 5),
    MEDALLION("M12,1 C18,1 22,5 22,12 C22,19 18,23 12,23 C6,23 2,19 2,12 C2,5 6,1 12,1 Z M12,3 C7,3 4,6 4,12 C4,18 7,21 12,21 C17,21 20,18 20,12 C20,6 17,3 12,3 Z", 5),
    DIAMOND("M12,0.5 L23.5,12 L12,23.5 L0.5,12 Z M12,3 L3,12 L12,21 L21,12 Z", 6, true),
    LENS("M7,1 H17 Q23,1 23,7 V17 Q23,23 17,23 H7 Q1,23 1,17 V7 Q1,1 7,1 Z M7,3 Q3,3 3,7 V17 Q3,21 7,21 H17 Q21,21 21,17 V7 Q21,3 17,3 Z", 5),
    TAB("M2,3 H22 V18 H15 L12,22 L9,18 H2 Z M4,5 V16 H10 L12,19 L14,16 H20 V5 Z", 6, true),
    SEAL("M8,1 H16 V3 H21 V8 H23 V16 H21 V21 H16 V23 H8 V21 H3 V16 H1 V8 H3 V3 H8 Z M9,3 V5 H5 V9 H3 V15 H5 V19 H9 V21 H15 V19 H19 V15 H21 V9 H19 V5 H15 V3 Z", 5);

    private val outline = requireNotNull(PathParser.createPathFromPathData(frame)).apply {
        fillType = Path.FillType.EVEN_ODD
    }

    // The outer contour also serves as a plate; Graphite's open corner brackets
    // are intentionally separate, leaving its plate shape to the user setting.
    val plate = if (name == "BRACKETS") null else
        requireNotNull(PathParser.createPathFromPathData(frame.substringBefore(" Z M") + " Z"))

    fun drawFrame(canvas: Canvas, paint: Paint) = canvas.drawPath(outline, paint)

    companion object {
        fun forTheme(id: ThemeId): ThemeIconPack? = when (id) {
            ThemeId.SIGNATURE -> null
            ThemeId.ATMOSPHERE -> ORBIT
            ThemeId.MONOLITH -> BLOCK
            ThemeId.OBSIDIAN -> GEM
            ThemeId.GRAPHITE -> BRACKETS
            ThemeId.VECTOR -> CUT
            ThemeId.AURORA -> PETAL
            ThemeId.PORCELAIN -> MEDALLION
            ThemeId.CHROMA -> DIAMOND
            ThemeId.LUMA_GLASS -> LENS
            ThemeId.LUMA_MONO -> TAB
            ThemeId.IVORY -> SEAL
        }
    }
}
