package com.rolexm.launcher.launcher.model

import com.rolexm.launcher.core.model.ComponentKey

/**
 * Persistence for [HomeLayout].
 *
 * Format: newline-delimited records, versioned on the first line.
 *
 *   v1
 *   D|<dock rec>,<dock rec>,...
 *   P|<page rec>,<page rec>,...
 *
 * Record grammar:
 *   A|<componentKey>              an app shortcut
 *   F|<id>|<name>|<key>;<key>     a folder
 *
 * Component keys never contain '|' or ';', and folder names are escaped, so the
 * grammar is unambiguous. A hand-rolled format is used instead of JSON to avoid
 * pulling org.json into code that must be unit-testable on the JVM (org.json is
 * Android framework and is stubbed out in local unit tests).
 *
 * Parsing is defensive: any malformed record is skipped rather than throwing,
 * because a layout file that fails to parse would otherwise leave the user with
 * a blank home screen and no way back.
 */
object HomeLayoutCodec {

    private const val VERSION = "v1"
    private const val SEP = "|"
    private const val ITEM_SEP = ","

    fun encode(layout: HomeLayout): String = buildString {
        appendLine(VERSION)
        append("D").append(SEP).appendLine(layout.dock.joinToString(ITEM_SEP, transform = ::encodeItem))
        layout.pages.forEach { page ->
            append("P").append(SEP).appendLine(page.joinToString(ITEM_SEP, transform = ::encodeItem))
        }
    }

    fun decode(raw: String?): HomeLayout {
        if (raw.isNullOrBlank()) return HomeLayout.EMPTY
        val lines = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return HomeLayout.EMPTY
        if (lines.first() != VERSION) return HomeLayout.EMPTY

        var dock: List<HomeItem> = emptyList()
        val pages = ArrayList<List<HomeItem>>()
        for (line in lines.drop(1)) {
            when {
                line.startsWith("D$SEP") -> dock = decodeList(line.removePrefix("D$SEP"))
                line.startsWith("P$SEP") -> pages += decodeList(line.removePrefix("P$SEP"))
            }
        }
        return HomeLayout(dock = dock, pages = if (pages.isEmpty()) listOf(emptyList()) else pages)
    }

    private fun encodeItem(item: HomeItem): String = when (item) {
        is HomeItem.App -> "A${SEP}${item.key.serialize()}"
        is HomeItem.Folder -> buildString {
            append("F").append(SEP)
            append(escape(item.id)).append(SEP)
            append(escape(item.name)).append(SEP)
            append(item.children.joinToString(";") { it.serialize() })
        }
    }

    private fun decodeList(payload: String): List<HomeItem> {
        if (payload.isBlank()) return emptyList()
        return payload.split(ITEM_SEP).mapNotNull { decodeItem(it) }
    }

    private fun decodeItem(token: String): HomeItem? {
        val parts = token.split(SEP)
        return when (parts.firstOrNull()) {
            "A" -> {
                val key = ComponentKey.parse(parts.getOrElse(1) { "" }) ?: return null
                HomeItem.App(key)
            }
            "F" -> {
                if (parts.size < 4) return null
                val id = unescape(parts[1])
                val name = unescape(parts[2])
                val children = parts[3].split(";").mapNotNull { ComponentKey.parse(it) }
                if (id.isBlank()) return null
                HomeItem.Folder(id, name, children)
            }
            else -> null
        }
    }

    /** Escapes the three characters that are structurally meaningful. */
    internal fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(SEP, "\\p")
        .replace(";", "\\s")
        .replace("\n", "\\n")
        .replace(ITEM_SEP, "\\c")

    internal fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> append('\\')
                    'p' -> append(SEP)
                    's' -> append(';')
                    'n' -> append('\n')
                    'c' -> append(ITEM_SEP)
                    else -> append(value[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
