package com.lumaos.pro.launcher.icons

import androidx.core.graphics.PathParser

/** Symbol artwork for the bundled app mappings, independent of the Signature bitmaps. */
internal object ThemeAppGlyphs {
    private val paths = LivingIconId.entries.associateWith { id ->
        requireNotNull(PathParser.createPathFromPathData(when (id) {
            LivingIconId.PHONE -> "M7,3 L10,8 L8,11 Q10,16 15,17 L18,14 L22,17 Q20,23 15,21 Q5,18 3,8 Q2,4 7,3 Z"
            LivingIconId.MESSAGES -> "M3,4 H21 V17 H12 L6,21 V17 H3 Z M7,9 H17 M7,13 H14"
            LivingIconId.CAMERA -> "M2,7 H7 L9,4 H15 L17,7 H22 V20 H2 Z M16,13 A4,4 0 1,1 8,13 A4,4 0 1,1 16,13 Z"
            LivingIconId.PHOTOS -> "M3,3 H21 V21 H3 Z M3,18 L9,12 L13,16 L17,11 L21,15 M15,7 H17"
            LivingIconId.BROWSER -> "M21,12 A9,9 0 1,1 3,12 A9,9 0 1,1 21,12 Z M15,8 L13,14 L8,16 L10,10 Z"
            LivingIconId.SETTINGS -> "M4,5 H20 M4,12 H20 M4,19 H20 M8,3 V7 M16,10 V14 M10,17 V21"
            LivingIconId.MUSIC -> "M9,18 V5 L20,3 V16 M9,9 L20,7 M9,18 C9,22 3,23 3,19 C3,16 9,15 9,18 Z M20,16 C20,20 14,21 14,17 C14,14 20,13 20,16 Z"
            LivingIconId.CALENDAR -> "M3,5 H21 V21 H3 Z M3,10 H21 M8,3 V7 M16,3 V7 M8,15 H10 M14,15 H16"
            LivingIconId.CLOCK -> "M21,12 A9,9 0 1,1 3,12 A9,9 0 1,1 21,12 Z M12,7 V12 L16,14"
            LivingIconId.FILES -> "M2,6 H9 L12,9 H22 V20 H2 Z"
            LivingIconId.CALCULATOR -> "M5,2 H19 V22 H5 Z M8,8 H16 M8,13 H10 M14,13 H16 M8,17 H10 M14,17 H16"
            LivingIconId.CONTACTS -> "M16,7 A4,4 0 1,1 8,7 A4,4 0 1,1 16,7 Z M4,21 V19 Q4,14 12,14 Q20,14 20,19 V21 Z"
            LivingIconId.MAPS -> "M12,22 Q4,13 4,10 A8,8 0 0,1 20,10 Q20,13 12,22 Z M15,10 A3,3 0 1,1 9,10 A3,3 0 1,1 15,10 Z"
            LivingIconId.WEATHER -> "M7,19 A4,4 0 1,1 8,11 A5,5 0 0,1 17,12 A3.5,3.5 0 1,1 18,19 Z M12,4 V2 M18,6 L20,4 M6,6 L4,4"
            LivingIconId.RECORDER -> "M9,6 Q9,3 12,3 Q15,3 15,6 V12 Q15,15 12,15 Q9,15 9,12 Z M5,11 V12 A7,7 0 0,0 19,12 V11 M12,19 V22 M8,22 H16"
            LivingIconId.NOTES -> "M5,3 H19 V16 L14,21 H5 Z M14,21 V16 H19 M8,8 H16 M8,12 H15"
            LivingIconId.MAIL -> "M2,5 H22 V19 H2 Z M2,6 L12,14 L22,6"
            LivingIconId.DOWNLOADS -> "M12,3 V15 M7,10 L12,15 L17,10 M4,16 V21 H20 V16"
            LivingIconId.APP_STORE -> "M4,8 H20 L21,21 H3 Z M8,8 V6 A4,4 0 0,1 16,6 V8"
            LivingIconId.BATTERY -> "M3,6 H19 V18 H3 Z M21,9 V15 M7,12 H15"
            LivingIconId.WHATSAPP -> "M3,21 L4,16 A9,9 0 1,1 8,20 Z M9,7 L8,9 Q9,15 15,16 L17,14 L14,12 L12,13 L10,11 L11,9 Z"
            LivingIconId.INSTAGRAM -> "M7,3 H17 Q21,3 21,7 V17 Q21,21 17,21 H7 Q3,21 3,17 V7 Q3,3 7,3 Z M16,12 A4,4 0 1,1 8,12 A4,4 0 1,1 16,12 Z M17,7 H18"
            LivingIconId.YOUTUBE -> "M2,6 H22 V18 H2 Z M10,9 L15,12 L10,15 Z"
            LivingIconId.SPOTIFY -> "M21,12 A9,9 0 1,1 3,12 A9,9 0 1,1 21,12 Z M7,9 Q12,7 17,10 M8,12 Q12,10 16,13 M9,15 Q12,13 15,16"
            LivingIconId.TELEGRAM -> "M2,11 L22,3 L18,21 L11,16 L8,19 L7,13 Z M7,13 L18,7 L11,16"
            LivingIconId.DISCORD -> "M4,8 L9,5 L10,7 H14 L15,5 L20,8 L22,17 L17,20 L15,17 H9 L7,20 L2,17 Z M8,12 V14 M16,12 V14"
            LivingIconId.GMAIL -> "M3,7 V19 M21,7 V19 M3,7 L12,14 L21,7"
            LivingIconId.GOOGLE_DRIVE -> "M9,3 H15 L22,16 L19,21 H5 L2,16 Z M9,3 L19,21 M15,3 L5,21 M2,16 H22"
            LivingIconId.GOOGLE_CHROME -> "M21,12 A9,9 0 1,1 3,12 A9,9 0 1,1 21,12 Z M16,12 A4,4 0 1,1 8,12 A4,4 0 1,1 16,12 Z M12,8 H20 M8.5,14 L5,7 M15,15 L11,21"
            LivingIconId.GOOGLE_PHOTOS -> "M12,12 V3 Q20,3 18,12 H21 Q21,20 12,18 V21 Q4,21 6,12 H3 Q3,4 12,6 Z"
        }))
    }

    // A few prominent marks are redrawn for the pack's visual language rather
    // than just moved into a different silhouette.
    private val alternatePaths = mapOf(
        (ThemeIconPack.ORBIT to LivingIconId.CAMERA) to
            "M22,12 A10,10 0 1,1 2,12 A10,10 0 1,1 22,12 Z M12,2 L16,11 L7,11 M22,12 L12,13 L16,21 M7,21 L9,13 L3,8",
        (ThemeIconPack.BLOCK to LivingIconId.CAMERA) to
            "M2,5 H22 V19 H2 Z M8,8 H16 V16 H8 Z M18,8 H19",
        (ThemeIconPack.LENS to LivingIconId.CAMERA) to
            "M5,4 H19 Q21,4 21,6 V18 Q21,20 19,20 H5 Q3,20 3,18 V6 Q3,4 5,4 Z M16,12 A4,4 0 1,1 8,12 A4,4 0 1,1 16,12 Z",
        (ThemeIconPack.BLOCK to LivingIconId.SETTINGS) to
            "M5,3 V21 M12,3 V21 M19,3 V21 M2,7 H8 M9,16 H15 M16,10 H22",
        (ThemeIconPack.ORBIT to LivingIconId.SETTINGS) to
            "M19,12 A7,7 0 1,1 5,12 A7,7 0 1,1 19,12 Z M12,12 L16,7 M12,2 V5 M12,19 V22 M2,12 H5 M19,12 H22"
    ).mapValues { (_, data) -> requireNotNull(PathParser.createPathFromPathData(data)) }

    fun path(id: LivingIconId, pack: ThemeIconPack) = alternatePaths[pack to id] ?: paths.getValue(id)
}
