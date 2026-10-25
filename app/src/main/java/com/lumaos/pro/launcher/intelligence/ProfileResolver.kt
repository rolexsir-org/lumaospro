package com.lumaos.pro.launcher.intelligence

/**
 * Decides which [Profile] is in force.
 *
 * Two inputs: an optional user override, and what the device looks like. An
 * explicit user choice always wins - LumaOS Pro never silently overrides the
 * person using it. Automatic selection only fills in the default.
 */
object ProfileResolver {

    /** What was resolved, and why - surfaced verbatim on the dashboard. */
    data class Resolution(
        val profile: Profile,
        val reason: String,
        val chosenByUser: Boolean
    )

    /**
     * @param override the profile the user picked in Settings, or null for
     *                 automatic selection.
     * @param capability measured device capability, used when [override] is null.
     * @param underMemoryPressure true when the last memory sample crossed the
     *                            low-memory threshold; only affects automatic
     *                            selection, so a manual choice is never undone
     *                            by a transient pressure spike.
     */
    fun resolve(
        override: ProfileId?,
        capability: DeviceCapability,
        underMemoryPressure: Boolean = false
    ): Resolution {
        if (override != null) {
            return Resolution(
                profile = Profiles.of(override),
                reason = "Chosen by you in Settings.",
                chosenByUser = true
            )
        }

        val base = when (capability.tier) {
            DeviceTier.LOW -> ProfileId.LIGHTWEIGHT
            DeviceTier.MEDIUM -> ProfileId.BALANCED
            DeviceTier.HIGH -> ProfileId.VISUAL_QUALITY
        }

        if (underMemoryPressure && base != ProfileId.LIGHTWEIGHT) {
            val stepped = Profiles.step(base, -1)
            return Resolution(
                profile = Profiles.of(stepped),
                reason = "Auto: device reports ${capability.tier.name} tier, stepped down to " +
                    "${stepped.name} because the system is under memory pressure.",
                chosenByUser = false
            )
        }

        return Resolution(
            profile = Profiles.of(base),
            reason = "Auto: device reports ${capability.tier.name} tier " +
                "(${capability.totalMemoryMb} MB RAM, ${capability.cpuCoreCount} cores).",
            chosenByUser = false
        )
    }
}
