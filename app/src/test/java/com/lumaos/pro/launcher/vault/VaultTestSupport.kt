package com.lumaos.pro.launcher.vault

import java.io.File

/**
 * Locates the `app` module from the working directory or an explicit override.
 *
 * Gradle runs unit tests with the module as the working directory; the sandbox
 * harness passes the repository root through `VAULT_REPO` (or the
 * `lumaos.repo` system property), and the walk-up below covers every other
 * layout. Shared by the source-contract tests (vault and project-wide) so the
 * resolution rule exists once.
 */
internal fun resolveModuleDir(): File {
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
