package com.rolexm.launcher.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCellSizingTest {
    @Test fun configuredRowsShareTheViewport() {
        assertEquals(120, homeCellHeightPx(600, rows = 5, minimumHeightPx = 90))
        assertEquals(200, homeCellHeightPx(600, rows = 3, minimumHeightPx = 90))
    }

    @Test fun textAndIconMinimumCanExceedSmallViewport() {
        assertEquals(110, homeCellHeightPx(300, rows = 5, minimumHeightPx = 110))
    }

    @Test fun zeroRowsAreSafelyClamped() {
        assertEquals(600, homeCellHeightPx(600, rows = 0, minimumHeightPx = 90))
    }

    @Test fun iconSizeShrinksForNarrowHighColumnGrids() {
        assertEquals(32, responsiveIconSizePx(72, 320, 40, 7, 8, 24))
        assertEquals(48, responsiveIconSizePx(48, 400, 0, 4, 8, 24))
    }

    @Test fun iconSizeIsUnchangedBeforeGridMeasurement() {
        assertEquals(72, responsiveIconSizePx(72, 0, 0, 7, 8, 24))
    }
}
