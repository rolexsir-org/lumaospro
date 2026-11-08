package com.lumaos.pro.launcher.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Static checks on the parts of Vault's privacy story that live in configuration
 * rather than in code: Android backup rules and the manifest.
 *
 * They are plain JVM tests (no Robolectric) that read the module's own resource
 * files, so a future edit that starts backing up the vault directory - or that
 * accidentally ships an exported Vault activity - fails the build here.
 */
class VaultPlatformIntegrationTests {

    private val moduleDir: File by lazy { resolveModuleDir() }

    @Test
    fun cloudBackupNeverIncludesTheVaultDirectory() {
        val rules = moduleDir.resolve("src/main/res/xml/data_extraction_rules.xml")
        assertTrue("data extraction rules must exist", rules.isFile)
        val text = rules.readText()
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(rules)
        val cloud = document.getElementsByTagName("cloud-backup")
        assertTrue("cloud-backup section is required", cloud.length == 1)

        // Vault data is stored in noBackupFilesDir, which Android never backs up.
        // These assertions make that the only possibility: no rule may name the
        // vault directory or include the whole filesystem.
        assertFalse(text.contains("vault"))
        assertFalse(text.contains("no_backup"))
        val includes = document.getElementsByTagName("include")
        for (index in 0 until includes.length) {
            val node = includes.item(index)
            val path = node.attributes?.getNamedItem("path")?.nodeValue ?: ""
            val domain = node.attributes?.getNamedItem("domain")?.nodeValue ?: ""
            assertFalse("unexpected domain $domain", domain == "root" || domain == "no_backup")
            assertFalse("unexpected path $path", path == "." && domain == "file")
        }
    }

    @Test
    fun legacyBackupRulesNeverIncludeTheVaultDirectory() {
        val rules = moduleDir.resolve("src/main/res/xml/backup_rules.xml")
        assertTrue("backup rules must exist", rules.isFile)
        assertFalse(rules.readText().contains("vault"))
    }

    @Test
    fun vaultActivitiesAreNotExported() {
        val manifest = moduleDir.resolve("src/main/AndroidManifest.xml").readText()
        val vaultActivities = Regex("<activity[^>]*\\.vault\\.[^>]*>").findAll(manifest).toList()
        assertTrue("vault activities must be declared in the manifest", vaultActivities.isNotEmpty())
        for (activity in vaultActivities) {
            assertTrue(
                "vault activity is exported: ${activity.value}",
                activity.value.contains("android:exported=\"false\"")
            )
        }
    }

    @Test
    fun vaultBringsNoExportedComponentsAndNoProviderOrNetworkSurface() {
        val manifest = moduleDir.resolve("src/main/AndroidManifest.xml").readText()
        // Vault is reachable only through the launcher itself. It must not add a
        // service, a receiver, a content provider or a broadcast that another app
        // could bind to or query, and none of its activities may be exported.
        assertFalse("Vault must not add a content provider", manifest.contains("android/provider"))
        for (line in manifest.lines()) {
            if (!line.contains("vault", ignoreCase = true)) continue
            assertFalse("Vault must not export anything: $line", line.contains("android:exported=\"true\""))
            assertFalse("Vault must not declare an intent-filter: $line", line.contains("intent-filter"))
        }
        // No networking permission is requested anywhere by this app.
        assertFalse(manifest.contains("android.permission.INTERNET"))
        // FLAG_SECURE is applied by the one base class every Vault screen extends.
        val base = moduleDir.resolve(
            "src/main/java/com/lumaos/pro/launcher/vault/ui/VaultScreenActivity.kt"
        ).readText()
        assertTrue("every Vault screen must set FLAG_SECURE", base.contains("FLAG_SECURE"))
    }

    @Test
    fun vaultSourcesDoNotLogFileNamesOrContent() {
        val root = moduleDir.resolve("src/main/java/com/lumaos/pro/launcher/vault")
        assertTrue(root.isDirectory)
        val offenders = ArrayList<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                val logs = trimmed.contains("LumaLog.")
                if (!logs) return@forEachIndexed
                // Only the fact that something happened may be logged: no item
                // names, no URIs, no exception messages that could embed a path.
                if (trimmed.contains("item.name") || trimmed.contains("uri") || trimmed.contains("displayName")) {
                    offenders.add("${file.name}:${index + 1}: $trimmed")
                }
            }
        }
        assertTrue("sensitive logging: $offenders", offenders.isEmpty())
    }

    private fun resolveModuleDir(): File {
        // Gradle runs unit tests with the module as the working directory; the
        // sandbox harness passes the repository root through an environment
        // variable, and the walk-up below covers every other layout.
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
