package com.rolexm.launcher.core.apps

import com.rolexm.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppCategoriesTest {

    private fun entry(p: String, category: Int) = AppEntry(ComponentKey(p, "$p.Main"), p, category = category)

    @Test fun `declared categories map to drawer categories`() {
        assertEquals(DrawerCategory.GAMES, AppCategories.of(0))
        assertEquals(DrawerCategory.PRODUCTIVITY, AppCategories.of(7))
        assertEquals(DrawerCategory.OTHER, AppCategories.of(-1))
        assertEquals(DrawerCategory.OTHER, AppCategories.of(1234))
    }

    @Test fun `only categories that occur are offered, in display order`() {
        val apps = listOf(entry("a", 7), entry("b", 0), entry("c", -1))
        assertEquals(listOf(DrawerCategory.GAMES, DrawerCategory.PRODUCTIVITY, DrawerCategory.OTHER), AppCategories.present(apps))
    }

    @Test fun `shared category names a new folder`() {
        assertEquals("Games", AppCategories.sharedTitle(entry("a", 0), entry("b", 0)))
        assertNull(AppCategories.sharedTitle(entry("a", 0), entry("b", 4)))
        assertNull(AppCategories.sharedTitle(entry("a", -1), entry("b", -1)))
    }

    @Test fun `drawer sort parses with a fallback`() {
        assertEquals(DrawerSort.NEWEST, DrawerSort.fromStorageKey("newest"))
        assertEquals(DrawerSort.ALPHABETICAL, DrawerSort.fromStorageKey(null))
    }
}
