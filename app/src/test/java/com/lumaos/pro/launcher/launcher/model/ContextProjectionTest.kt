package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.launcher.LauncherViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home projection filters the union of Vault-hidden and context-hidden
 * apps while the stored layout keeps every entry: switching contexts (or
 * unhiding) restores the exact saved arrangement.
 */
class ContextProjectionTest {

    private val mail = ComponentKey("com.example.mail", "com.example.mail.Main")
    private val game = ComponentKey("com.example.game", "com.example.game.Play")
    private val notes = ComponentKey("com.example.notes", "com.example.notes.Main")

    @Test fun `context-hidden apps vanish from the page but keep stored positions`() {
        val stored = HomeLayout(
            dock = listOf(HomeItem.App(mail)),
            pages = listOf(listOf(HomeItem.App(mail), HomeItem.App(game), HomeItem.App(notes)))
        )
        // Vault hides nothing here; the context hides the game.
        val display = LauncherViewModel.project(stored, setOf(game), 0)
        assertEquals(listOf(HomeItem.App(mail), HomeItem.App(notes)), display.layout.page(0))
        assertEquals(listOf(0, 2), display.pageMap)
        // The stored layout is untouched: the game is still at index 1.
        assertEquals(HomeItem.App(game), stored.page(0)[1])
    }

    @Test fun `a folder left with one visible child shows that app`() {
        val stored = HomeLayout(
            pages = listOf(
                listOf(HomeItem.Folder("f1", "Fun", listOf(game, notes)))
            )
        )
        val display = LauncherViewModel.project(stored, setOf(game), 0)
        assertEquals(listOf(HomeItem.App(notes)), display.layout.page(0))
        assertEquals(listOf(0), display.pageMap)
    }

    @Test fun `fully hidden folders and dock entries disappear`() {
        val stored = HomeLayout(
            dock = listOf(HomeItem.App(game)),
            pages = listOf(
                listOf(
                    HomeItem.Folder("f1", "Fun", listOf(game)),
                    HomeItem.App(notes)
                )
            )
        )
        val display = LauncherViewModel.project(stored, setOf(game), 0)
        assertEquals(listOf(HomeItem.App(notes)), display.layout.page(0))
        assertTrue(display.layout.dock.isEmpty())
        assertTrue(display.dockMap.isEmpty())
    }
}
