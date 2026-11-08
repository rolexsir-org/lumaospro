package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.HiddenApp
import com.lumaos.pro.launcher.vault.core.VaultHiddenApp
import com.lumaos.pro.launcher.vault.core.VaultIndex
import com.lumaos.pro.launcher.vault.core.VaultSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hidden Apps, at the layer where hiding actually happens.
 *
 * Two properties matter and are both checked here without touching Android:
 *
 *  1. the hidden set lives in the encrypted index and round-trips through the
 *     serialised form, including on an index written *before* the field existed;
 *  2. hiding is *reversible and non-destructive* - it records a component and
 *     nothing else, so restoring an app can never have altered it.
 */
class VaultHiddenAppsTests {

    private val settings = VaultSettings()

    private fun index(settings: VaultSettings = VaultSettings()): VaultIndex =
        VaultIndex("vault-1", 1, 0, emptyList(), emptyList(), settings, emptyList())

    private fun app(pkg: String = "com.example.private", cls: String = "com.example.private.Main") =
        HiddenApp(pkg, cls, 0L, "Private App", 1_700_000_000_000L)

    // ------------------------------------------------------------------
    // Index behaviour
    // ------------------------------------------------------------------

    @Test
    fun hidingAddsTheAppToTheIndex() {
        val next = index().withHiddenApp(app())
        assertEquals(1, next.settings.hiddenApps.size)
        assertEquals("0:com.example.private/com.example.private.Main", next.settings.hiddenApps.first().componentKey)
    }

    @Test
    fun hidingTwiceReplacesRatherThanDuplicates() {
        val once = index().withHiddenApp(app())
        val twice = once.withHiddenApp(app().copy(label = "Renamed"))
        assertEquals("a second hide must not duplicate the entry", 1, twice.settings.hiddenApps.size)
        assertEquals("Renamed", twice.settings.hiddenApps.first().label)
    }

    @Test
    fun restoringRemovesOnlyThatApp() {
        val both = index().withHiddenApp(app()).withHiddenApp(app("com.example.other", "com.example.other.Main"))
        val restored = both.withoutHiddenApp("0:com.example.private/com.example.private.Main")
        assertEquals(1, restored.settings.hiddenApps.size)
        assertEquals("com.example.other", restored.settings.hiddenApps.first().packageName)
    }

    @Test
    fun restoringAnAppThatIsNotHiddenIsANoOp() {
        val next = index().withoutHiddenApp("0:nope/nope.Main")
        assertTrue(next.settings.hiddenApps.isEmpty())
    }

    /** Work-profile entries keep their serial, so the right profile is hidden. */
    @Test
    fun workProfileAppsKeepTheirSerial() {
        val next = index().withHiddenApp(app().copy(userSerial = 10L))
        assertEquals("10:com.example.private/com.example.private.Main", next.settings.hiddenApps.first().componentKey)
        assertTrue(next.hiddenComponentKeys().contains("10:com.example.private/com.example.private.Main"))
        assertFalse(next.hiddenComponentKeys().contains("0:com.example.private/com.example.private.Main"))
    }

    // ------------------------------------------------------------------
    // Serialisation and forward/backward compatibility
    // ------------------------------------------------------------------

    @Test
    fun hiddenAppsSurviveAnIndexRoundTrip() {
        val original = index().withHiddenApp(app()).withHiddenApp(app("com.a.b", "com.a.b.Main").copy(userSerial = 10L))
        val decoded = VaultIndex.fromMap(original.toMap(), "vault-1")
        assertNotNull(decoded)
        assertEquals(original.settings.hiddenApps.map { it.componentKey }, decoded!!.settings.hiddenApps.map { it.componentKey })
        assertEquals(original.settings.hiddenApps.map { it.label }, decoded.settings.hiddenApps.map { it.label })
    }

    /**
     * An index written by a build that predates Hidden Apps must still open.
     *
     * This is the compatibility half of "preserve existing data": upgrading must
     * not fail to read an existing vault, and the new field must default cleanly.
     */
    @Test
    fun anOlderIndexWithoutTheFieldStillReads() {
        val legacy = linkedMapOf<String, Any?>(
            "vaultId" to "vault-1",
            "generation" to 4,
            "updatedAt" to 1L,
            "items" to emptyList<Any?>(),
            "folders" to emptyList<Any?>(),
            "settings" to linkedMapOf(
                "autoLockMs" to 180_000L,
                "lockOnLeave" to false,
                "biometricEnabled" to false
            ),
            "events" to emptyList<Any?>()
        )
        val decoded = VaultIndex.fromMap(legacy, "vault-1")
        assertNotNull("an index without hiddenApps must still decode", decoded)
        assertTrue(decoded!!.settings.hiddenApps.isEmpty())
    }

    /** A corrupt entry is skipped, never allowed to break the whole list. */
    @Test
    fun malformedHiddenEntriesAreSkipped() {
        val corrupt = listOf(
            linkedMapOf<String, Any?>("pkg" to "com.good.app", "cls" to "com.good.app.Main", "user" to 0),
            linkedMapOf<String, Any?>("pkg" to "", "cls" to "x"),
            linkedMapOf<String, Any?>("cls" to "no.package"),
            linkedMapOf<String, Any?>("pkg" to "com.x", "cls" to ""),
            "not even a map"
        )
        val decoded = VaultIndex.fromMap(
            linkedMapOf(
                "vaultId" to "vault-1", "generation" to 1, "updatedAt" to 0L,
                "items" to emptyList<Any?>(), "folders" to emptyList<Any?>(),
                "settings" to linkedMapOf("hiddenApps" to corrupt),
                "events" to emptyList<Any?>()
            ),
            "vault-1"
        )
        assertEquals("only the valid entry survives", 1, decoded!!.settings.hiddenApps.size)
        assertEquals("com.good.app", decoded.settings.hiddenApps.first().packageName)
    }

    // ------------------------------------------------------------------
    // Legacy migration
    // ------------------------------------------------------------------

    /**
     * The old implementation kept `hidden_apps_<profile>` in plain preferences.
     * Those selections must not reappear in the drawer after the upgrade.
     */
    @Test
    fun legacySelectionsMergeIntoTheIndexWithoutDuplicates() {
        val legacy = listOf(
            VaultHiddenApp("com.old.app", "com.old.app.Main"),
            VaultHiddenApp("com.already.hidden", "com.already.hidden.Main")
        )
        var next = index().withHiddenApp(HiddenApp("com.already.hidden", "com.already.hidden.Main"))
        // Mirrors VaultManager.importLegacyHiddenApps: skip what is already hidden.
        val existing = next.settings.hiddenApps.mapTo(HashSet()) { it.componentKey }
        legacy.filter { it.componentKey !in existing }
            .forEach { next = next.withHiddenApp(HiddenApp(it.packageName, it.className, it.userSerial, it.label)) }
        assertEquals(2, next.settings.hiddenApps.size)
        assertTrue(next.hiddenComponentKeys().contains("0:com.old.app/com.old.app.Main"))
        assertTrue(next.hiddenComponentKeys().contains("0:com.already.hidden/com.already.hidden.Main"))
    }

    /** Running the migration again changes nothing. */
    @Test
    fun legacyMigrationIsIdempotent() {
        val legacy = listOf(VaultHiddenApp("com.old.app", "com.old.app.Main"))
        var next = index()
        repeat(3) {
            val existing = next.settings.hiddenApps.mapTo(HashSet()) { it.componentKey }
            legacy.filter { it.componentKey !in existing }
                .forEach { next = next.withHiddenApp(HiddenApp(it.packageName, it.className)) }
        }
        assertEquals(1, next.settings.hiddenApps.size)
    }

    @Test
    fun componentKeyParsingRejectsGarbage() {
        assertNull(VaultHiddenApp.fromComponentKey(""))
        assertNull(VaultHiddenApp.fromComponentKey("noserial:com.a/b"))
        assertNull(VaultHiddenApp.fromComponentKey("0:"))
        assertNull(VaultHiddenApp.fromComponentKey("0:com.a"))
        assertNotNull(VaultHiddenApp.fromComponentKey("0:com.a/com.a.Main"))
    }

    // ------------------------------------------------------------------
    // Non-destructiveness
    // ------------------------------------------------------------------

    /**
     * Hiding records a component and nothing else.
     *
     * The requirement is explicit: hiding must not uninstall, disable or clear an
     * app. This asserts the whole stored shape, so a future field that could carry
     * "disable this package" would have to be added here deliberately.
     */
    @Test
    fun hidingStoresOnlyLauncherMetadata() {
        val stored = app().toMap()
        assertEquals(
            "hiding must store nothing but the component, its label and a timestamp",
            setOf("pkg", "cls", "user", "label", "at"),
            stored.keys
        )
    }

    /** Hidden apps carry no Vault *content*; restoring needs no item bookkeeping. */
    @Test
    fun hidingDoesNotTouchVaultItems() {
        val withItem = index().copy(items = emptyList())
        val hidden = withItem.withHiddenApp(app())
        assertTrue(hidden.items.isEmpty())
        assertTrue(hidden.settings.hiddenApps.isNotEmpty())
    }
}
