package com.lumaos.pro.launcher.context

import com.lumaos.pro.launcher.core.apps.AppCategories
import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.core.apps.DrawerCategory
import com.lumaos.pro.launcher.core.model.ComponentKey

/**
 * Sensible, review-before-applying defaults for per-context app visibility and
 * focus restrictions.
 *
 * Suggestions are computed from real data only: the category each app declares
 * in its own manifest plus conservative name matching for well-known app
 * families. They are never applied automatically — Settings offers them as a
 * one-tap starting point that the user can adjust app by app.
 *
 * Pure JVM logic: no Android imports, covered by unit tests.
 */
object ContextAppSuggestions {

    /**
     * Apps that should stay visible in [profile]. Personal is the catch-all:
     * everything stays visible.
     */
    fun suggestedVisible(profile: ContextProfile, apps: List<AppEntry>): Set<ComponentKey> {
        if (apps.isEmpty()) return emptySet()
        if (profile == ContextProfile.PERSONAL) return apps.mapTo(HashSet()) { it.key }
        return apps.filterTo(HashSet()) { visibleIn(profile, it) }
    }

    /**
     * Apps worth restricting while [profile] runs a focus session. Only Work
     * and Study support focus; every other profile suggests nothing.
     */
    fun suggestedRestricted(profile: ContextProfile, apps: List<AppEntry>): Set<ComponentKey> {
        if (profile !in ContextEngine.FOCUS_PROFILES || apps.isEmpty()) return emptySet()
        return apps.filterTo(HashSet()) { distracting(it) }
    }

    private fun visibleIn(profile: ContextProfile, app: AppEntry): Boolean {
        // Essentials stay reachable everywhere: communication, time, and the
        // system surfaces a user needs to run the device.
        if (app.isEssential) return true
        val category = AppCategories.of(app.category)
        return when (profile) {
            ContextProfile.WORK -> category == DrawerCategory.PRODUCTIVITY ||
                app.matches(WORK_TOKENS)
            ContextProfile.STUDY -> app.matches(STUDY_TOKENS)
            ContextProfile.SLEEP -> app.matches(SLEEP_TOKENS)
            ContextProfile.PERSONAL -> true
        }
    }

    private fun distracting(app: AppEntry): Boolean {
        if (app.isEssential) return false
        val category = AppCategories.of(app.category)
        if (category == DrawerCategory.SOCIAL || category == DrawerCategory.GAMES ||
            category == DrawerCategory.VIDEO
        ) return true
        return app.matches(DISTRACTOR_TOKENS)
    }

    private val AppEntry.isEssential: Boolean
        get() = matches(ESSENTIAL_TOKENS)

    private fun AppEntry.matches(tokens: List<String>): Boolean {
        val haystack = "${label.lowercase()} ${key.packageName.lowercase()}"
        return tokens.any { it in haystack }
    }

    private val ESSENTIAL_TOKENS = listOf(
        "phone", "dialer", "call", "message", "sms", "contact",
        "clock", "alarm", "setting", "camera", "file", "browser",
        "chrome", "firefox", "edge", "webview"
    )

    private val WORK_TOKENS = listOf(
        "mail", "gmail", "outlook", "calendar", "meet", "zoom", "teams",
        "slack", "docs", "sheet", "slide", "drive", "notion", "todo",
        "task", "office", "pdf", "print", "scan", "vpn", "auth",
        "password", "bank", "pay", "spreadsheet", "document"
    )

    private val STUDY_TOKENS = listOf(
        "note", "book", "read", "kindle", "study", "learn", "school",
        "class", "course", "calc", "dictionary", "translate", "drive",
        "docs", "pdf", "wiki", "duolingo", "quiz", "flashcard", "tutor"
    )

    private val SLEEP_TOKENS = listOf(
        "clock", "alarm", "sleep", "bedtime", "night", "white noise",
        "meditation", "calm", "weather"
    )

    private val DISTRACTOR_TOKENS = listOf(
        "youtube", "instagram", "tiktok", "facebook", "twitter",
        "reddit", "netflix", "twitch", "snapchat", "discord",
        "pinterest", "shorts", "reels"
    )
}
