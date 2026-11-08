package com.lumaos.pro.launcher.vault.core

/**
 * The launcher's concealment index: which app drawer entries are hidden.
 *
 * ## Why this exists separately from the Vault index
 *
 * The authoritative list lives inside the Vault's encrypted metadata (`meta.bin`),
 * which can only be read with the master key, which only exists after the PIN.
 * That is the right place for it, but it has one consequence the product cannot
 * accept: after a reboot - and any time the vault is locked - the launcher cannot
 * read it, so it cannot filter the drawer, so every hidden app is back in plain
 * sight. That is the bug this index fixes.
 *
 * So the *same* selection is mirrored into a small, device-bound, Keystore-sealed
 * blob that the launcher can read before authentication. The Vault index stays
 * authoritative; this mirror is derived from it and is repaired from it on every
 * unlock.
 *
 * ## What "device-bound" does and does not buy
 *
 * The mirror is sealed with an AES-256 key that lives in the Android Keystore and
 * cannot be exported. Therefore:
 *
 *  - copying the file to another phone or to a laptop yields nothing; the key is
 *    not in the file, and the Keystore will not release it;
 *  - an Android backup, a bug report, `adb pull` (non-root) or a file-manager
 *    browse does not reveal which apps are hidden;
 *  - **it is not a secret from the device.** Anyone who can run this app's code on
 *    the unlocked device can ask the same Keystore to open it. The contents are
 *    only "hidden" in the sense that the drawer does not show them before unlock.
 *    Stated plainly here and in `docs/VAULT.md` rather than implied otherwise.
 *
 * ## Format
 *
 * Versioned JSON so the mirror can be extended without breaking installed copies,
 * sealed together with the vault id as associated data (a mirror copied from
 * another vault fails to open rather than silently hiding the wrong apps).
 *
 * This file is pure Kotlin: encoding, decoding, validation and the "which state
 * wins" rules are unit-tested without an Android device.
 */
object VaultConcealment {

    /** Bumped only if the payload shape changes incompatibly. */
    const val FORMAT = 1

    /** File names inside the vault directory. Not secret; the payload is sealed. */
    const val FILE_NAME = "conceal.bin"

    /** Previous generation, used when a write was interrupted. */
    const val BACKUP_FILE_NAME = "conceal.bin.bak"

    /**
     * One hidden drawer entry.
     *
     * Deliberately identical to [VaultHiddenApp] minus the timestamp: the label is
     * kept so the pre-unlock UI can name an app without a package query, and the
     * profile serial is kept so a work-profile copy stays distinct.
     */
    data class Entry(
        val packageName: String,
        val className: String,
        val userSerial: Long = 0L,
        val label: String = ""
    ) {
        val componentKey: String get() = "$userSerial:$packageName/$className"

        fun isValid(): Boolean = packageName.isNotBlank() && className.isNotBlank()
    }

    /**
     * What the launcher is allowed to believe before authentication.
     *
     * [entries] is empty in [State.Fresh] *and* in [State.Unreadable] on purpose -
     * the two are distinguished by [State.degraded], never conflated, because
     * "nothing is hidden" and "we could not find out" must not lead to the same UI
     * silent behaviour.
     */
    data class State(
        val entries: List<Entry>,
        /** No mirror has ever been written (a vault that never hid anything). */
        val fresh: Boolean = false,
        /** The mirror existed but could not be opened or parsed. */
        val degraded: Boolean = false,
        /** The vault the mirror belongs to, when known. */
        val vaultId: String? = null
    ) {
        val keys: Set<String> get() = entries.mapTo(LinkedHashSet()) { it.componentKey }
        val isEmpty: Boolean get() = entries.isEmpty()

        companion object {
            val FRESH = State(entries = emptyList(), fresh = true)
            val DEGRADED = State(entries = emptyList(), degraded = true)
        }
    }

    /** Encodes [entries] into the plaintext payload that gets sealed. */
    fun encode(vaultId: String, entries: Collection<Entry>): ByteArray {
        val rows = entries
            .filter { it.isValid() }
            .distinctBy { it.componentKey }
            .sortedBy { it.componentKey }
            .map { entry ->
                linkedMapOf(
                    "pkg" to entry.packageName,
                    "cls" to entry.className,
                    "user" to entry.userSerial,
                    "label" to entry.label
                )
            }
        return com.lumaos.pro.launcher.backup.MiniJson.write(
            linkedMapOf(
                "format" to FORMAT,
                "vaultId" to vaultId,
                "entries" to rows
            )
        ).toByteArray(Charsets.UTF_8)
    }

    /**
     * Parses a payload, returning null when it is not a concealment record we
     * understand.
     *
     * Malformed *rows* are skipped rather than failing the whole record: one bad
     * entry must not hide every other app's hiding. A payload that is unparseable
     * or from another vault returns null, and the caller treats that as
     * [State.DEGRADED] rather than as "nothing is hidden".
     */
    fun decode(payload: ByteArray, expectedVaultId: String?): State? {
        val root = runCatching {
            com.lumaos.pro.launcher.backup.MiniJson.parse(String(payload, Charsets.UTF_8)) as? Map<*, *>
        }.getOrNull() ?: return null

        val format = (root["format"] as? Number)?.toInt() ?: return null
        if (format != FORMAT) return null
        val vaultId = root["vaultId"] as? String ?: return null
        // A mirror belonging to a different vault describes different apps; using it
        // would hide the wrong ones, so it is rejected outright.
        if (expectedVaultId != null && vaultId != expectedVaultId) return null

        val rows = root["entries"] as? List<*> ?: return null
        val entries = ArrayList<Entry>(rows.size)
        for (row in rows) {
            val map = row as? Map<*, *> ?: continue
            val entry = Entry(
                packageName = map["pkg"] as? String ?: continue,
                className = map["cls"] as? String ?: continue,
                userSerial = (map["user"] as? Number)?.toLong() ?: 0L,
                label = map["label"] as? String ?: ""
            )
            if (!entry.isValid() || entry.userSerial < 0L) continue
            entries += entry
        }
        return State(entries = entries.distinctBy { it.componentKey }, vaultId = vaultId)
    }

    /**
     * Chooses what the launcher should believe from the two sealed generations.
     *
     * The newer generation is written first and atomically, so a readable primary
     * always wins. The backup is used *only* when the primary cannot be opened -
     * that is the interrupted-write case - which keeps a torn write from showing
     * hidden apps.
     */
    fun reconcile(primary: State?, backup: State?): State = when {
        primary != null -> primary
        backup != null -> backup
        else -> State.DEGRADED
    }

    /**
     * True when the launcher must not trust an empty [state] as "nothing hidden".
     *
     * A degraded mirror means the list could not be read. The drawer has to keep
     * behaving as it did (it has nothing else to go on) but the Vault must say so,
     * and the next unlock must repair the mirror from the authoritative index.
     */
    fun needsRepair(state: State): Boolean = state.degraded

    /** The mirror contents in the form the launcher's drawer filter wants. */
    fun toHiddenApps(entries: Collection<Entry>): List<VaultHiddenApp> =
        entries.filter { it.isValid() }.map {
            VaultHiddenApp(it.packageName, it.className, it.userSerial, it.label)
        }
}
