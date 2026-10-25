package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.core.model.ComponentKey

/**
 * Persistence for [HomeLayout].
 *
 * Format: newline-delimited records, versioned on the first line.
 *
 *   v1 | v2
 *   D|<dock rec>,<dock rec>,...
 *   P|<page rec>,<page rec>,...
 *
 * Record grammar:
 *   A|<componentKey>              an app shortcut                 (v1)
 *   F|<id>|<name>|<key>;<key>     a folder                        (v1)
 *   W|<widgetId>|<provider>|<rows> an app widget                  (v2)
 *   S|<id>|<kind>|<name>          a smart folder (live contents)  (v2)
 *
 * Layouts that only contain v1 records are still written as "v1", so a
 * downgrade keeps working; "v2" is written only when a widget or smart folder
 * is present. Both versions are read.
 *
 * Component keys never contain '|' or ';', and names are escaped, so the
 * grammar is unambiguous. A hand-rolled format is used instead of JSON to avoid
 * pulling org.json into code that must be unit-testable on the JVM (org.json is
 * Android framework and is stubbed out in local unit tests).
 *
 * Parsing is defensive: any malformed record is skipped rather than throwing,
 * because a layout file that fails to parse would otherwise leave the user with
 * a blank home screen and no way back.
 */
object HomeLayoutCodec {

    private const val V1 = "v1"
    private const val V2 = "v2"
    private const val SEP = "|"
    private const val ITEM_SEP = ","

    fun encode(layout: HomeLayout): String = buildString {
        val needsV2 = (layout.dock + layout.pages.flatten())
            .any { it is HomeItem.Widget || it is HomeItem.SmartFolder }
        appendLine(if (needsV2) V2 else V1)
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
        val version = lines.first()
        if (version != V1 && version != V2) return HomeLayout.EMPTY
        val allowV2 = version == V2

        var dock: List<HomeItem> = emptyList()
        val pages = ArrayList<List<HomeItem>>()
        for (line in lines.drop(1)) {
            when {
                line.startsWith("D$SEP") -> dock = decodeList(line.removePrefix("D$SEP"), allowV2)
                    .filterNot { it is HomeItem.Widget }
                line.startsWith("P$SEP") -> pages += decodeList(line.removePrefix("P$SEP"), allowV2)
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
        is HomeItem.Widget -> buildString {
            append("W").append(SEP)
            append(item.appWidgetId).append(SEP)
            append(escape(item.provider)).append(SEP)
            append(item.rows)
        }
        is HomeItem.SmartFolder -> buildString {
            append("S").append(SEP)
            append(escape(item.id)).append(SEP)
            append(item.kind.storageKey).append(SEP)
            append(escape(item.name))
        }
    }

    private fun decodeList(payload: String, allowV2: Boolean): List<HomeItem> {
        if (payload.isBlank()) return emptyList()
        return payload.split(ITEM_SEP).mapNotNull { decodeItem(it, allowV2) }
    }

    private fun decodeItem(token: String, allowV2: Boolean): HomeItem? {
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
            "W" -> {
                if (!allowV2 || parts.size < 4) return null
                val id = parts[1].toIntOrNull() ?: return null
                val rows = parts[3].toIntOrNull() ?: return null
                if (id <= 0) return null
                HomeItem.Widget(
                    appWidgetId = id,
                    provider = unescape(parts[2]),
                    rows = rows.coerceIn(HomeLayout.MIN_WIDGET_ROWS, HomeLayout.MAX_WIDGET_ROWS)
                )
            }
            "S" -> {
                if (!allowV2 || parts.size < 4) return null
                val id = unescape(parts[1])
                val kind = SmartKind.fromStorageKey(parts[2]) ?: return null
                if (id.isBlank()) return null
                HomeItem.SmartFolder(id, kind, unescape(parts[3]))
            }
            else -> null
        }
    }

    /** Escapes the characters that are structurally meaningful. */
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
