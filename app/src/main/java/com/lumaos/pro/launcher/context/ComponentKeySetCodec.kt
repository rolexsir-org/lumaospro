package com.lumaos.pro.launcher.context

import com.lumaos.pro.launcher.core.model.ComponentKey

/**
 * Text codec for persisted per-context app sets (drawer visibility, focus
 * restrictions).
 *
 * One [ComponentKey.serialize] entry per app, joined with `|`. Corrupt or
 * outdated entries decode to "missing" (skipped) instead of failing the whole
 * set, so one bad entry can never hide or restrict every app.
 *
 * Pure JVM logic: no Android imports, covered by unit tests.
 */
object ComponentKeySetCodec {
    fun encode(keys: Collection<ComponentKey>): String =
        keys.distinct().joinToString("|") { it.serialize() }

    fun decode(raw: String?): Set<ComponentKey> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split('|').mapNotNullTo(LinkedHashSet()) { ComponentKey.parse(it) }
    }
}
