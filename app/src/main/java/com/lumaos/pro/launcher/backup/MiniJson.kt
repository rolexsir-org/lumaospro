package com.lumaos.pro.launcher.backup

/**
 * Minimal JSON reader/writer for backup files.
 *
 * org.json is part of the Android framework and is stubbed in local unit
 * tests, so the backup format would otherwise be untestable on the JVM. This
 * supports exactly what backups need: objects, arrays, strings, numbers,
 * booleans and null. Integral numbers decode as Long, others as Double.
 */
object MiniJson {

    fun write(value: Any?, indent: String = "  "): String = StringBuilder().also { writeValue(it, value, indent, 0) }.toString()

    private fun writeValue(sb: StringBuilder, value: Any?, indent: String, depth: Int) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(value)
            is Int, is Long, is Short, is Byte -> sb.append(value.toString())
            is Float -> sb.append(if (value.isFinite()) value.toString() else "null")
            is Double -> sb.append(if (value.isFinite()) value.toString() else "null")
            is Map<*, *> -> {
                if (value.isEmpty()) { sb.append("{}"); return }
                sb.append("{\n")
                val entries = value.entries.toList()
                entries.forEachIndexed { i, (k, v) ->
                    repeat(depth + 1) { sb.append(indent) }
                    writeString(sb, k.toString())
                    sb.append(": ")
                    writeValue(sb, v, indent, depth + 1)
                    if (i < entries.lastIndex) sb.append(',')
                    sb.append('\n')
                }
                repeat(depth) { sb.append(indent) }
                sb.append('}')
            }
            is Iterable<*> -> {
                val items = value.toList()
                if (items.isEmpty()) { sb.append("[]"); return }
                sb.append("[\n")
                items.forEachIndexed { i, v ->
                    repeat(depth + 1) { sb.append(indent) }
                    writeValue(sb, v, indent, depth + 1)
                    if (i < items.lastIndex) sb.append(',')
                    sb.append('\n')
                }
                repeat(depth) { sb.append(indent) }
                sb.append(']')
            }
            else -> writeString(sb, value.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
    }

    class ParseException(message: String) : Exception(message)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value(0)
        p.skipWs()
        if (!p.atEnd()) throw ParseException("Trailing characters at ${p.pos}")
        return v
    }

    private class Parser(val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(depth: Int): Any? {
            if (depth > 64) throw ParseException("Nesting too deep")
            skipWs()
            if (atEnd()) throw ParseException("Unexpected end")
            return when (val c = s[pos]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw ParseException("Unexpected '$c' at $pos")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) throw ParseException("Expected $word at $pos")
            pos += word.length
            return v
        }

        private fun obj(depth: Int): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWs()
            if (pos < s.length && s[pos] == '}') { pos++; return out }
            while (true) {
                skipWs()
                if (atEnd() || s[pos] != '"') throw ParseException("Expected key at $pos")
                val k = str()
                skipWs()
                if (atEnd() || s[pos] != ':') throw ParseException("Expected ':' at $pos")
                pos++
                out[k] = value(depth + 1)
                skipWs()
                if (atEnd()) throw ParseException("Unterminated object")
                when (s[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return out }
                    else -> throw ParseException("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            val out = ArrayList<Any?>()
            pos++ // [
            skipWs()
            if (pos < s.length && s[pos] == ']') { pos++; return out }
            while (true) {
                out += value(depth + 1)
                skipWs()
                if (atEnd()) throw ParseException("Unterminated array")
                when (s[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return out }
                    else -> throw ParseException("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun str(): String {
            val sb = StringBuilder()
            pos++ // opening quote
            while (true) {
                if (atEnd()) throw ParseException("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw ParseException("Bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw ParseException("Bad unicode escape")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw ParseException("Bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            val token = s.substring(start, pos)
            return token.toLongOrNull() ?: token.toDoubleOrNull() ?: throw ParseException("Bad number '$token'")
        }
    }
}
