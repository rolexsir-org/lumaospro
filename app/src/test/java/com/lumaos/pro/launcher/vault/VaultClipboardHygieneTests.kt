package com.lumaos.pro.launcher.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static checks on Vault's *sensitive-value* hygiene: the clipboard, and what the
 * code is allowed to write to a log.
 *
 * These are plain JVM tests that read the module's own sources, in the same style
 * as [VaultPlatformIntegrationTests]. They cannot prove that a running app never
 * logs anything - nothing static can - but they do pin the two decisions that are
 * easy to undo by accident in a later edit: note copy is always marked sensitive,
 * and no Vault source logs a PIN, a plaintext body or a key.
 */
class VaultClipboardHygieneTests {

    private val moduleDir: File by lazy { resolveModuleDir() }
    private val vaultSources: File by lazy {
        moduleDir.resolve("src/main/java/com/lumaos/pro/launcher/vault")
    }

    /**
     * Note copy must go through the sensitivity-marked path.
     *
     * The platform's own Copy action writes an ordinary clip, so the note editor
     * intercepts it. This test pins both halves of that decision: the intercept
     * exists, and it never falls back to an unmarked copy on failure.
     */
    @Test
    fun noteCopyIsAlwaysMarkedSensitive() {
        val notes = vaultSources.resolve("ui/VaultNotesActivity.kt")
        assertTrue("the note editor must exist", notes.isFile)
        val text = notes.readText()
        assertTrue("copy and cut are intercepted", text.contains("onTextContextMenuItem"))
        assertTrue("the marked path is used", text.contains("VaultClipboard.copySensitive"))
        assertTrue("copying is not removed from the UI", text.contains("android.R.id.copy"))
        assertFalse("no unmarked ClipData is built in the editor", text.contains("newPlainText"))
        assertFalse(
            "the editor must not write a note body to a log",
            text.contains("VaultLog.body")
        )
    }

    @Test
    fun clipboardHelperSetsThePlatformSensitivityExtra() {
        val helper = vaultSources.resolve("ui/VaultClipboard.kt")
        assertTrue("the clipboard helper must exist", helper.isFile)
        val text = helper.readText()
        // ClipDescription.EXTRA_IS_SENSITIVE is an API 33 constant; the literal is
        // used so the minSdk 26 build compiles, and older releases ignore it.
        assertTrue(
            "the platform extra must be set",
            text.contains("android.content.extra.IS_SENSITIVE")
        )
        assertTrue("it must be set on the description extras", text.contains("PersistableBundle"))
        assertTrue("a failure must be reported", text.contains("getOrDefault(false)"))
        assertFalse("nothing may log clipboard content", text.contains("LumaLog"))
        assertFalse("nothing may print clipboard content", text.contains("println"))
    }

    /** No Vault source logs a PIN, a plaintext body, a secret or a key. */
    @Test
    fun noVaultSourceLogsASensitiveValue() {
        val offenders = ArrayList<String>()
        val sensitive = Regex("(pin|body|plaintext|secret|wrappedDek|masterKey|clipboard)", RegexOption.IGNORE_CASE)
        vaultSources.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if ((line.contains("LumaLog.") || line.contains("Log.d(") || line.contains("Log.e(")) &&
                    sensitive.containsMatchIn(line)
                ) {
                    offenders += "${file.name}:${index + 1}: ${line.trim()}"
                }
            }
        }
        assertTrue(offenders.joinToString("\n") { "logged: $it" }, offenders.isEmpty())
    }

    /** Notes still take text from the user: copy and paste are not disabled. */
    @Test
    fun theNoteEditorStillAllowsPasteAndSelection() {
        val text = vaultSources.resolve("ui/VaultNotesActivity.kt").readText()
        assertFalse("the field must not be made non-editable", text.contains("isFocusable = false"))
        assertFalse("the field must not disable long press", text.contains("isLongClickable = false"))
        assertTrue("the editor still builds a text field", text.contains("SensitiveField"))
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
