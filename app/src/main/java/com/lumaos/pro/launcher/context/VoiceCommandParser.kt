package com.lumaos.pro.launcher.context

sealed interface VoiceCommand {
    data class SwitchProfile(val profile: ContextProfile) : VoiceCommand
    data class OpenApp(val query: String) : VoiceCommand
    data object Unrecognized : VoiceCommand
}

/** Small deterministic grammar; speech recognition itself is provided by Android. */
object VoiceCommandParser {
    fun parse(transcript: String): VoiceCommand {
        // Trim *after* stripping punctuation: "Switch to Work!" must become
        // "switch to work", not "switch to work " (which matched nothing).
        val normalized = transcript.lowercase().replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        for (profile in ContextProfile.entries) {
            val name = profile.title.lowercase()
            if (normalized == name || normalized == "$name mode" ||
                normalized == "switch to $name" || normalized == "go to $name" ||
                normalized == "activate $name") {
                return VoiceCommand.SwitchProfile(profile)
            }
        }
        val appQuery = listOf("open ", "launch ", "start ")
            .firstNotNullOfOrNull { prefix -> normalized.takeIf { it.startsWith(prefix) }?.removePrefix(prefix) }
            ?.trim().orEmpty()
        return if (appQuery.isNotBlank()) VoiceCommand.OpenApp(appQuery) else VoiceCommand.Unrecognized
    }
}
