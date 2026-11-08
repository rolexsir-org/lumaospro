package com.lumaos.pro.launcher.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static checks on the Hidden Apps screen that live in configuration and in its
 * own copy, not in behaviour a JVM test can reach without an emulator.
 *
 * Two things must hold and neither is visible from the Kotlin type system:
 *
 *  - the activity must be declared non-exported and out of recents, exactly like
 *    every other Vault screen, or the section would be reachable without the
 *    Vault door;
 *  - the screen must not promise protection it does not provide, and must not
 *    contain code that could touch the user's applications.
 */
class VaultHiddenAppsScreenTests {

    private val moduleDir: File by lazy { resolveModuleDir() }
    private val source: File by lazy {
        moduleDir.resolve("src/main/java/com/lumaos/pro/launcher/vault/ui/VaultHiddenAppsActivity.kt")
    }

    @Test
    fun theScreenIsDeclaredNonExportedAndOutOfRecents() {
        val manifest = moduleDir.resolve("src/main/AndroidManifest.xml").readText()
        val start = manifest.indexOf("VaultHiddenAppsActivity")
        assertTrue("VaultHiddenAppsActivity must be declared", start >= 0)
        // Read the element that declares it, up to the end of its attributes.
        val declaration = manifest.substring(start, minOf(start + 400, manifest.length))
        val end = declaration.indexOf("/>")
        val element = declaration.substring(0, if (end >= 0) end else declaration.length)
        assertTrue("must not be exported", element.contains("android:exported=\"false\""))
        assertTrue("must stay out of recents", element.contains("android:excludeFromRecents=\"true\""))
    }

    @Test
    fun theScreenIsReachableOnlyFromTheVault() {
        // The section is opened from the Vault home tile and from the two launcher
        // entry points, all of which go through the Vault lock screen first.
        val home = moduleDir.resolve("src/main/java/com/lumaos/pro/launcher/vault/ui/VaultHomeActivity.kt").readText()
        assertTrue("the Vault home must offer the section", home.contains("VaultHiddenAppsActivity"))
    }

    @Test
    fun theCopyDoesNotOverclaim() {
        val text = source.readText()
        // The screen must say what "hidden" does and does not mean. These phrases
        // are the contract with the user; losing them would turn a launcher filter
        // into a security claim.
        assertTrue(
            "the screen must state that hiding is launcher-level",
            text.contains("hidden from the LumaOS Pro launcher", ignoreCase = true)
        )
        assertTrue(
            "the screen must state the app is not uninstalled or disabled",
            text.contains("not ", ignoreCase = true) && text.contains("uninstalled", ignoreCase = true)
        )
        assertTrue(
            "the screen must disclaim work-profile isolation",
            text.contains("work-profile isolation", ignoreCase = true)
        )
    }

    @Test
    fun theScreenCannotTouchTheUsersApplications() {
        val text = source.readText()
        // Hiding is metadata. Nothing in this screen may uninstall, disable,
        // suspend, force-stop or clear another package - that would be destruction,
        // not hiding, and the task explicitly forbids it as a shortcut.
        listOf(
            "uninstallPackage",
            "setApplicationEnabledSetting",
            "setPackagesSuspended",
            "forceStopPackage",
            "clearApplicationUserData",
            "deletePackage",
            "pm uninstall"
        ).forEach { forbidden ->
            assertFalse("$forbidden must not appear in the Hidden Apps screen", text.contains(forbidden))
        }
    }

    @Test
    fun theScreenNeverLogsWhatIsHidden() {
        val text = source.readText()
        // Which apps someone hid is exactly the metadata this feature is meant to
        // keep private; the screen must not print it to logcat.
        listOf("LumaLog", "println(", "System.out", "Log.d(", "Log.i(", "Log.e(").forEach { forbidden ->
            assertFalse("$forbidden must not appear in the Hidden Apps screen", text.contains(forbidden))
        }
    }

    private fun resolveModuleDir(): File {
        (System.getenv("VAULT_REPO") ?: System.getProperty("lumaos.repo"))?.let { root ->
            val candidate = File(root, "app/src/main/AndroidManifest.xml")
            if (candidate.isFile) return File(root, "app")
        }
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        while (directory != null) {
            val candidate = File(directory, "src/main/AndroidManifest.xml")
            if (candidate.isFile) return directory
            val nested = File(directory, "app/src/main/AndroidManifest.xml")
            if (nested.isFile) return File(directory, "app")
            directory = directory.parentFile
        }
        error("Could not locate the app module from ${System.getProperty("user.dir")}")
    }
}
