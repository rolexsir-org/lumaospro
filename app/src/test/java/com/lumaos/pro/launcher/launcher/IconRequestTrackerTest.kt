package com.lumaos.pro.launcher.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconRequestTrackerTest {
    @Test fun duplicateRequestsAndReplacementAreTrackedByIdentity() {
        val tracker = IconRequestTracker<String>()
        val old = tracker.start("app")!!
        assertNull(tracker.start("app"))
        tracker.invalidate("app")
        val replacement = tracker.start("app")!!
        assertFalse(tracker.finish("app", old))
        assertTrue(tracker.isCurrent("app", replacement))
        assertTrue(tracker.finish("app", replacement))
        assertNotNull(tracker.start("app"))
    }

    @Test fun packageAndConfigurationInvalidationCancelOnlyRelevantWork() {
        val tracker = IconRequestTracker<String>()
        val first = tracker.start("one/A")!!
        val second = tracker.start("one/B")!!
        val other = tracker.start("two/A")!!
        tracker.invalidateWhere { it.startsWith("one/") }
        assertFalse(tracker.isCurrent("one/A", first))
        assertFalse(tracker.isCurrent("one/B", second))
        assertTrue(tracker.isCurrent("two/A", other))
        tracker.clear()
        assertFalse(tracker.finish("two/A", other))
    }
}
