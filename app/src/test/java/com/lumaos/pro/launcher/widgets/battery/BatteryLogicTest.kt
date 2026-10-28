package com.lumaos.pro.launcher.widgets.battery

import org.junit.Assert.*
import org.junit.Test

class BatteryLogicTest {
    @Test fun `percentage reflects real level and scale without overflow`() {
        assertEquals(47, BatteryLogic.percent(47, 100))
        assertEquals(50, BatteryLogic.percent(5, 10))
        assertEquals(100, BatteryLogic.percent(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(0, BatteryLogic.percent(0, 100))
    }
    @Test fun `invalid battery values remain unavailable`() {
        assertNull(BatteryLogic.percent(-1, 100))
        assertNull(BatteryLogic.percent(1, 0))
        assertNull(BatteryLogic.percent(101, 100))
    }
    @Test fun `charging and full are distinct from unknown`() {
        assertEquals(BatteryStatus.CHARGING, BatteryLogic.status(2))
        assertEquals(BatteryStatus.FULL, BatteryLogic.status(5))
        assertEquals(BatteryStatus.NOT_CHARGING, BatteryLogic.status(3))
        assertEquals(BatteryStatus.NOT_CHARGING, BatteryLogic.status(4))
        assertEquals(BatteryStatus.UNKNOWN, BatteryLogic.status(-1))
    }
}
