package com.lumaos.pro.launcher

import com.lumaos.pro.launcher.vault.resolveModuleDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-contract test: every `import com.lumaos.pro.launcher.*` in the main
 * and test source sets must resolve to a top-level declaration that actually
 * exists in this project (or to the AGP-generated `R`/`BuildConfig`).
 *
 * This guards the class of defect where a file imports a symbol from a package
 * it does not live in (for example `ui.glass.GlassRole` for a type declared in
 * `ui.theme`): such an import compiles nowhere, fails the whole test source
 * set, and is invisible to resource and manifest checks.
 *
 * The check reads the shipped sources from disk, like the other source-contract
 * tests; it needs no Android runtime.
 */
class ProjectImportResolutionTest {

    private val generated = setOf(
        "com.lumaos.pro.launcher.R",
        "com.lumaos.pro.launcher.BuildConfig",
    )

    @Test
    fun everyProjectImportResolvesToADeclaration() {
        val module = resolveModuleDir()
        val declarations = indexTopLevelDeclarations(module)
        val unresolved = mutableListOf<String>()

        for (source in sourceFiles(module)) {
            val text = stripComments(source.readText())
            IMPORT_REGEX.findAll(text).forEach { match ->
                val fq = match.groupValues[1]
                if (fq in generated) return@forEach
                if (!resolves(fq, declarations)) {
                    unresolved += "${source.relativeTo(module)}: $fq"
                }
            }
        }

        assertTrue(
            "imports with no matching project declaration:\n" + unresolved.joinToString("\n"),
            unresolved.isEmpty(),
        )
    }

    @Test
    fun theIndexCoversTheSourceSetsItClaimsTo() {
        val module = resolveModuleDir()
        val sources = sourceFiles(module).toList()
        assertTrue("expected a non-trivial source tree", sources.size > 300)
        val packages = indexTopLevelDeclarations(module).keys
        assertTrue("expected the launcher package to be indexed", packages.any { it.endsWith(".launcher") })
        // Spot-check symbols that have moved packages before: a stale package
        // for these types is exactly the defect this test exists to catch.
        val theme = declarationsFor(module, "com.lumaos.pro.launcher.ui.theme")
        assertEquals("GlassRole lives in ui.theme", true, "GlassRole" in theme)
        assertEquals("GlassCapabilities lives in ui.theme", true, "GlassCapabilities" in theme)
        assertEquals("GlassMaterials lives in ui.theme", true, "GlassMaterials" in theme)
    }

    // ---------------------------------------------------------------------

    private fun sourceFiles(module: File): Sequence<File> =
        listOf(File(module, "src/main/java"), File(module, "src/test/java"))
            .filter { it.isDirectory }
            .asSequence()
            .flatMap { root -> root.walkTopDown().asSequence().filter { it.isFile && it.extension == "kt" } }

    private fun stripComments(text: String): String {
        val noBlock = BLOCK_COMMENT_REGEX.replace(text, "")
        return LINE_COMMENT_REGEX.replace(noBlock, "")
    }

    private fun indexTopLevelDeclarations(module: File): Map<String, Set<String>> {
        val index = HashMap<String, MutableSet<String>>()
        for (source in sourceFiles(module)) {
            val text = stripComments(source.readText())
            val pkg = PACKAGE_REGEX.find(text)?.groupValues?.get(1) ?: continue
            val bucket = index.getOrPut(pkg) { HashSet() }
            DECLARATION_REGEX.findAll(text).forEach { bucket += it.groupValues[1] }
        }
        return index
    }

    private fun declarationsFor(module: File, pkg: String): Set<String> =
        indexTopLevelDeclarations(module)[pkg].orEmpty()

    private fun resolves(fqName: String, declarations: Map<String, Set<String>>): Boolean {
        val parts = fqName.split(".")
        if (parts.size < 2) return false
        // Longest prefix that names a package; the next segment must be a
        // top-level declaration in that package (members after it are nested).
        for (cut in parts.size - 1 downTo 2) {
            val pkg = parts.subList(0, cut).joinToString(".")
            if (parts[cut] in declarations[pkg]) return true
        }
        return false
    }


    private companion object {
        val IMPORT_REGEX = Regex("""(?m)^import\s+(com\.lumaos\.pro\.launcher\.[\w.]+)$""")
        val PACKAGE_REGEX = Regex("""(?m)^package\s+([\w.]+)""")
        val BLOCK_COMMENT_REGEX = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT_REGEX = Regex("""//[^\n]*""")
        val DECLARATION_REGEX = Regex(
            """(?m)^(?:[ \t]*@\w+(?:\([^)]*\))?[ \t]*\n?[ \t]*)*""" +
                """(?:[ \t]*(?:public|internal|private|protected|expect|actual|abstract|open|sealed|""" +
                """final|data|enum|annotation|value|inner|fun|operator|infix|suspend|inline|""" +
                """tailrec|external|override)\s+)*""" +
                """(?:[ \t]*(?:class|interface|object|fun|val|var|typealias)\s+""" +
                """(?:<[^>]*>\s+)?(?:[\w.]+\.)?(\w+))""",
        )
    }
}
