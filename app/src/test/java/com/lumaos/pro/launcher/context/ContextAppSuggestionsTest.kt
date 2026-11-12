package com.lumaos.pro.launcher.context

import com.lumaos.pro.launcher.core.apps.AppCategories
import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.core.apps.DrawerCategory
import com.lumaos.pro.launcher.core.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextAppSuggestionsTest {

    private fun app(pkg: String, label: String, category: Int = AppCategories.UNDEFINED): AppEntry =
        AppEntry(ComponentKey(pkg, "$pkg.Main"), label, category = category)

    private val phone = app("com.example.phone", "Phone")
    private val gmail = app("com.google.android.gm", "Gmail", DrawerCategory.PRODUCTIVITY.categoryValue)
    private val game = app("com.example.game", "Galaxy Shooter", DrawerCategory.GAMES.categoryValue)
    private val social = app("com.example.social", "Chatter", DrawerCategory.SOCIAL.categoryValue)
    private val notes = app("com.example.notes", "Notes")
    private val calculator = app("com.example.calc", "Calculator")
    private val weather = app("com.example.weather", "Weather")
    private val clock = app("com.example.clock", "Clock")

    private val all = listOf(phone, gmail, game, social, notes, calculator, weather, clock)

    @Test fun `personal keeps everything visible`() {
        assertEquals(all.mapTo(HashSet()) { it.key }, ContextAppSuggestions.suggestedVisible(ContextProfile.PERSONAL, all))
    }

    @Test fun `work keeps mail and essentials but not games`() {
        val visible = ContextAppSuggestions.suggestedVisible(ContextProfile.WORK, all)
        assertTrue(gmail.key in visible)
        assertTrue(phone.key in visible)
        assertFalse(game.key in visible)
        assertFalse(social.key in visible)
    }

    @Test fun `study keeps notes and calculator`() {
        val visible = ContextAppSuggestions.suggestedVisible(ContextProfile.STUDY, all)
        assertTrue(notes.key in visible)
        assertTrue(calculator.key in visible)
        assertFalse(game.key in visible)
    }

    @Test fun `sleep keeps only essentials`() {
        val visible = ContextAppSuggestions.suggestedVisible(ContextProfile.SLEEP, all)
        assertTrue(clock.key in visible)
        assertTrue(phone.key in visible)
        assertTrue(weather.key in visible)
        assertFalse(gmail.key in visible)
        assertFalse(game.key in visible)
        assertFalse(notes.key in visible)
    }

    @Test fun `focus suggests social games and video but never essentials`() {
        val restricted = ContextAppSuggestions.suggestedRestricted(ContextProfile.WORK, all)
        assertTrue(game.key in restricted)
        assertTrue(social.key in restricted)
        assertFalse(phone.key in restricted)
        assertFalse(clock.key in restricted)
        assertFalse(gmail.key in restricted)
    }

    @Test fun `focus suggests nothing outside Work and Study`() {
        assertTrue(ContextAppSuggestions.suggestedRestricted(ContextProfile.PERSONAL, all).isEmpty())
        assertTrue(ContextAppSuggestions.suggestedRestricted(ContextProfile.SLEEP, all).isEmpty())
    }
}
