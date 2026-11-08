package com.lumaos.pro.launcher.vault.core

/**
 * The vault's encrypted metadata model.
 *
 * Everything in this file lives *inside* `meta.bin`, which is AES-256-GCM
 * encrypted under a key derived from the master key. That includes ordinary
 * looking things such as file names and MIME types: a name like
 * "passport-scan.pdf" is exactly the kind of information that must not leak
 * through a filesystem listing, a backup or a log line.
 */

/** What a stored object is, which decides the icon, viewer and capture origin. */
enum class VaultItemKind(val id: String) {
    IMAGE("image"),
    VIDEO("video"),
    AUDIO("audio"),
    DOCUMENT("document"),
    NOTE("note"),
    SCAN("scan"),
    OTHER("other");

    companion object {
        fun fromId(id: String?): VaultItemKind = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/** Every object is genuinely encrypted with its own data key. */
data class VaultItem(
    val id: String,
    val kind: VaultItemKind,
    /** Original display name, only ever visible inside the vault. */
    val name: String,
    val mimeType: String,
    /** Plaintext size in bytes. */
    val sizeBytes: Long,
    /** When the item was added to the vault. */
    val addedAtMs: Long,
    /** Capture or file timestamp; equals [addedAtMs] for imports. */
    val capturedAtMs: Long,
    val folderId: String?,
    /** IV and ciphertext of this object's DEK, wrapped by the master key. */
    val dekIv: String,
    val wrappedDek: String,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val pageCount: Int = 0,
    /** Where the object came from: import, camera, scanner, recorder, note, restore. */
    val origin: String = "import",
    val favorite: Boolean = false
) {

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "kind" to kind.id,
        "name" to name,
        "mime" to mimeType,
        "size" to sizeBytes,
        "addedAt" to addedAtMs,
        "capturedAt" to capturedAtMs,
        "folder" to folderId,
        "dekIv" to dekIv,
        "dek" to wrappedDek,
        "width" to width,
        "height" to height,
        "duration" to durationMs,
        "pages" to pageCount,
        "origin" to origin,
        "favorite" to favorite
    )

    companion object {
        fun fromMap(map: Map<*, *>): VaultItem? {
            val id = map["id"] as? String ?: return null
            val dekIv = map["dekIv"] as? String ?: return null
            val dek = map["dek"] as? String ?: return null
            return VaultItem(
                id = id,
                kind = VaultItemKind.fromId(map["kind"] as? String),
                name = map["name"] as? String ?: "Untitled",
                mimeType = map["mime"] as? String ?: "application/octet-stream",
                sizeBytes = (map["size"] as? Number)?.toLong() ?: 0L,
                addedAtMs = (map["addedAt"] as? Number)?.toLong() ?: 0L,
                capturedAtMs = (map["capturedAt"] as? Number)?.toLong() ?: 0L,
                folderId = map["folder"] as? String,
                dekIv = dekIv,
                wrappedDek = dek,
                width = (map["width"] as? Number)?.toInt() ?: 0,
                height = (map["height"] as? Number)?.toInt() ?: 0,
                durationMs = (map["duration"] as? Number)?.toLong() ?: 0L,
                pageCount = (map["pages"] as? Number)?.toInt() ?: 0,
                origin = map["origin"] as? String ?: "import",
                favorite = map["favorite"] as? Boolean ?: false
            )
        }
    }
}

data class VaultFolder(
    val id: String,
    val name: String,
    val createdAtMs: Long
) {
    fun toMap(): Map<String, Any?> = linkedMapOf("id" to id, "name" to name, "createdAt" to createdAtMs)

    companion object {
        fun fromMap(map: Map<*, *>): VaultFolder? {
            val id = map["id"] as? String ?: return null
            return VaultFolder(
                id = id,
                name = map["name"] as? String ?: "Folder",
                createdAtMs = (map["createdAt"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

/** Auto-lock choices offered in the Security Center. */
enum class VaultAutoLock(val millis: Long, val labelKey: String) {
    THIRTY_SECONDS(30_000L, "30s"),
    ONE_MINUTE(60_000L, "1m"),
    THREE_MINUTES(180_000L, "3m"),
    FIVE_MINUTES(300_000L, "5m"),
    TEN_MINUTES(600_000L, "10m");

    companion object {
        val DEFAULT = THREE_MINUTES
        fun fromMillis(millis: Long): VaultAutoLock = entries.firstOrNull { it.millis == millis } ?: DEFAULT
    }
}

/**
 * Backup bookkeeping.
 *
 * [state] is computed, never asserted: the UI only ever displays a "Protected"
 * badge when a verified backup exists and its destination is still writable.
 */
data class VaultBackupSettings(
    val destinationUri: String? = null,
    val destinationLabel: String? = null,
    val autoBackup: Boolean = false,
    val lastSuccessAtMs: Long = 0L,
    val lastAttemptAtMs: Long = 0L,
    val lastFailure: String? = null,
    val lastVerified: Boolean = false,
    val lastBytes: Long = 0L,
    /** Set when vault content changed after the last verified backup. */
    val pendingSinceMs: Long = 0L
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "destinationUri" to destinationUri,
        "destinationLabel" to destinationLabel,
        "autoBackup" to autoBackup,
        "lastSuccessAt" to lastSuccessAtMs,
        "lastAttemptAt" to lastAttemptAtMs,
        "lastFailure" to lastFailure,
        "lastVerified" to lastVerified,
        "lastBytes" to lastBytes,
        "pendingSince" to pendingSinceMs
    )

    companion object {
        fun fromMap(map: Map<*, *>?): VaultBackupSettings {
            if (map == null) return VaultBackupSettings()
            return VaultBackupSettings(
                destinationUri = map["destinationUri"] as? String,
                destinationLabel = map["destinationLabel"] as? String,
                autoBackup = map["autoBackup"] as? Boolean ?: false,
                lastSuccessAtMs = (map["lastSuccessAt"] as? Number)?.toLong() ?: 0L,
                lastAttemptAtMs = (map["lastAttemptAt"] as? Number)?.toLong() ?: 0L,
                lastFailure = map["lastFailure"] as? String,
                lastVerified = map["lastVerified"] as? Boolean ?: false,
                lastBytes = (map["lastBytes"] as? Number)?.toLong() ?: 0L,
                pendingSinceMs = (map["pendingSince"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

data class VaultSettings(
    val autoLock: VaultAutoLock = VaultAutoLock.DEFAULT,
    /** Lock as soon as the vault leaves the foreground, not just after the timeout. */
    val lockOnLeave: Boolean = false,
    val biometricEnabled: Boolean = false,
    /** Set only after the user confirms they stored the recovery key. */
    val recoveryConfirmedAtMs: Long = 0L,
    val recoveryRotatedAtMs: Long = 0L,
    val backup: VaultBackupSettings = VaultBackupSettings(),
    /**
     * Launcher entries the user has moved into the Vault's Hidden Apps section.
     *
     * Stored here, inside the encrypted index, rather than in plain preferences:
     * the list of apps someone chose to hide is itself private, and on the old
     * launcher-level implementation it sat in DataStore in the clear. The launcher
     * still reads it (it has to, to keep the drawer clean) but only through the
     * Vault's authenticated manager, and only as a set of component names.
     *
     * Additive: an index written before this field existed reads as an empty set,
     * and an older build ignores it, so the format stays compatible both ways.
     */
    val hiddenApps: List<HiddenApp> = emptyList()
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "autoLockMs" to autoLock.millis,
        "lockOnLeave" to lockOnLeave,
        "biometricEnabled" to biometricEnabled,
        "recoveryConfirmedAt" to recoveryConfirmedAtMs,
        "recoveryRotatedAt" to recoveryRotatedAtMs,
        "backup" to backup.toMap(),
        "hiddenApps" to hiddenApps.map { it.toMap() }
    )

    companion object {
        fun fromMap(map: Map<*, *>?): VaultSettings {
            if (map == null) return VaultSettings()
            return VaultSettings(
                autoLock = VaultAutoLock.fromMillis((map["autoLockMs"] as? Number)?.toLong() ?: VaultAutoLock.DEFAULT.millis),
                lockOnLeave = map["lockOnLeave"] as? Boolean ?: false,
                biometricEnabled = map["biometricEnabled"] as? Boolean ?: false,
                recoveryConfirmedAtMs = (map["recoveryConfirmedAt"] as? Number)?.toLong() ?: 0L,
                recoveryRotatedAtMs = (map["recoveryRotatedAt"] as? Number)?.toLong() ?: 0L,
                backup = VaultBackupSettings.fromMap(map["backup"] as? Map<*, *>),
                hiddenApps = (map["hiddenApps"] as? List<*>)
                    ?.mapNotNull { HiddenApp.fromMap(it as? Map<*, *>) }
                    .orEmpty()
            )
        }
    }
}

/**
 * A launcher entry the user wants removed from the drawer.
 *
 * Deliberately a plain data holder with no Android types: it is what the Vault UI
 * hands to [com.lumaos.pro.launcher.vault.platform.VaultManager] and what the
 * launcher needs back to filter its drawer, so the whole hide/restore path can be
 * unit-tested without a device.
 */
data class VaultHiddenApp(
    val packageName: String,
    val className: String,
    val userSerial: Long = 0L,
    val label: String = "",
    val hiddenAtMs: Long = 0L
) {
    /** Canonical text form, matching `ComponentKey.serialize()`. */
    val componentKey: String get() = "$userSerial:$packageName/$className"

    companion object {
        /** Parses the launcher's `serial:pkg/cls` form; null when malformed. */
        fun fromComponentKey(value: String, label: String = ""): VaultHiddenApp? {
            val colon = value.indexOf(':')
            if (colon <= 0) return null
            val serial = value.substring(0, colon).toLongOrNull() ?: return null
            val slash = value.indexOf('/', colon + 1)
            if (slash <= colon + 1 || slash == value.length - 1) return null
            val pkg = value.substring(colon + 1, slash)
            val cls = value.substring(slash + 1)
            if (pkg.isBlank() || cls.isBlank()) return null
            return VaultHiddenApp(pkg, cls, serial, label)
        }
    }
}

/**
 * One launcher entry hidden from the drawer.
 *
 * Only what is needed to restore it: the component, a label for the Vault UI, the
 * profile it belongs to, and when it was hidden. The label is the launcher's own
 * label, not anything the app supplies at runtime, and it is inside the encrypted
 * index like every other piece of Vault metadata.
 */
data class HiddenApp(
    val packageName: String,
    val className: String,
    val userSerial: Long = 0L,
    val label: String = "",
    val hiddenAtMs: Long = 0L
) {

    /** Canonical text form, matching `ComponentKey.serialize()`. */
    val componentKey: String get() = "$userSerial:$packageName/$className"

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "pkg" to packageName,
        "cls" to className,
        "user" to userSerial,
        "label" to label,
        "at" to hiddenAtMs
    )

    companion object {
        fun fromMap(map: Map<*, *>?): HiddenApp? {
            if (map == null) return null
            val pkg = map["pkg"] as? String ?: return null
            val cls = map["cls"] as? String ?: return null
            if (pkg.isBlank() || cls.isBlank()) return null
            return HiddenApp(
                packageName = pkg,
                className = cls,
                userSerial = (map["user"] as? Number)?.toLong() ?: 0L,
                label = map["label"] as? String ?: "",
                hiddenAtMs = (map["at"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

/** Security-relevant events. Never contains names, text, or file content. */
enum class VaultEventType(val id: String, val label: String) {
    CREATED("created", "Vault created"),
    UNLOCKED_PIN("unlock_pin", "Unlocked with PIN"),
    UNLOCKED_BIOMETRIC("unlock_bio", "Unlocked with biometrics"),
    UNLOCKED_RECOVERY("unlock_recovery", "Unlocked with recovery key"),
    PIN_CHANGED("pin_changed", "PIN changed"),
    BIOMETRIC_ENABLED("bio_on", "Biometric unlock enabled"),
    BIOMETRIC_DISABLED("bio_off", "Biometric unlock disabled"),
    BIOMETRIC_INVALIDATED("bio_invalid", "Biometric unlock reset (fingerprint change)"),
    RECOVERY_VIEWED("recovery_viewed", "Recovery key checked"),
    RECOVERY_ROTATED("recovery_rotated", "Recovery key rotated"),
    LOCKED("locked", "Vault locked"),
    EXPORTED("exported", "Item exported out of the vault"),
    BACKUP_OK("backup_ok", "Encrypted backup verified"),
    BACKUP_FAILED("backup_failed", "Encrypted backup failed"),
    RESTORED("restored", "Vault restored from backup"),
    FAILED_ATTEMPTS("failed_attempts", "Failed unlock attempts"),
    HIDDEN_APP_ADDED("hidden_added", "App hidden in the vault"),
    HIDDEN_APP_REMOVED("hidden_removed", "App restored to the launcher");

    companion object {
        fun fromId(id: String?): VaultEventType? = entries.firstOrNull { it.id == id }
    }
}

data class VaultEvent(val type: VaultEventType, val atMs: Long) {
    fun toMap(): Map<String, Any?> = linkedMapOf("type" to type.id, "at" to atMs)

    companion object {
        fun fromMap(map: Map<*, *>): VaultEvent? {
            val type = VaultEventType.fromId(map["type"] as? String) ?: return null
            return VaultEvent(type, (map["at"] as? Number)?.toLong() ?: 0L)
        }
    }
}

/**
 * The decrypted form of `meta.bin`.
 *
 * Immutable: every mutation produces a new copy, which the store serialises,
 * encrypts and commits atomically. That keeps "the index on disk is always a
 * complete, authenticated document" true no matter where a write is interrupted.
 */
data class VaultIndex(
    val vaultId: String,
    val generation: Long,
    val updatedAtMs: Long,
    val items: List<VaultItem>,
    val folders: List<VaultFolder>,
    val settings: VaultSettings,
    val events: List<VaultEvent>
) {

    fun item(id: String): VaultItem? = items.firstOrNull { it.id == id }

    fun itemsOf(vararg kinds: VaultItemKind): List<VaultItem> =
        items.filter { it.kind in kinds }.sortedByDescending { it.capturedAtMs }

    fun withItem(item: VaultItem): VaultIndex =
        copy(items = items.filterNot { it.id == item.id } + item, updatedAtMs = System.currentTimeMillis())

    fun withoutItem(id: String): VaultIndex =
        copy(items = items.filterNot { it.id == id }, updatedAtMs = System.currentTimeMillis())

    fun withItemReplaced(id: String, transform: (VaultItem) -> VaultItem): VaultIndex =
        copy(items = items.map { if (it.id == id) transform(it) else it }, updatedAtMs = System.currentTimeMillis())

    fun withSettings(newSettings: VaultSettings): VaultIndex =
        copy(settings = newSettings, updatedAtMs = System.currentTimeMillis())

    /** Adds [app] to the hidden set, replacing any stale entry for the same component. */
    fun withHiddenApp(app: HiddenApp): VaultIndex = copy(
        settings = settings.copy(
            hiddenApps = settings.hiddenApps.filterNot { it.componentKey == app.componentKey } + app
        ),
        updatedAtMs = System.currentTimeMillis()
    )

    /** Removes the hidden entry for [componentKey]; a no-op when it is not hidden. */
    fun withoutHiddenApp(componentKey: String): VaultIndex = copy(
        settings = settings.copy(
            hiddenApps = settings.hiddenApps.filterNot { it.componentKey == componentKey }
        ),
        updatedAtMs = System.currentTimeMillis()
    )

    /** The hidden component keys, as the launcher's drawer wants them. */
    fun hiddenComponentKeys(): Set<String> = settings.hiddenApps.mapTo(LinkedHashSet()) { it.componentKey }

    fun withEvent(type: VaultEventType, atMs: Long = System.currentTimeMillis()): VaultIndex =
        copy(events = (events + VaultEvent(type, atMs)).takeLast(MAX_EVENTS), updatedAtMs = atMs)

    fun withFolder(folder: VaultFolder): VaultIndex = copy(
        folders = folders.filterNot { it.id == folder.id } + folder,
        updatedAtMs = System.currentTimeMillis()
    )

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "format" to FORMAT,
        "vaultId" to vaultId,
        "generation" to generation,
        "updatedAt" to updatedAtMs,
        "items" to items.map { it.toMap() },
        "folders" to folders.map { it.toMap() },
        "settings" to settings.toMap(),
        "events" to events.map { it.toMap() }
    )

    /** Marks changed content so the backup status can honestly say "pending". */
    fun markBackupPending(nowMs: Long = System.currentTimeMillis()): VaultIndex =
        if (settings.backup.pendingSinceMs > 0L) this
        else withSettings(settings.copy(backup = settings.backup.copy(pendingSinceMs = nowMs)))

    companion object {
        const val FORMAT = 1
        const val MAX_EVENTS = 200

        fun empty(vaultId: String, nowMs: Long = System.currentTimeMillis()): VaultIndex = VaultIndex(
            vaultId = vaultId,
            generation = 0,
            updatedAtMs = nowMs,
            items = emptyList(),
            folders = emptyList(),
            settings = VaultSettings(),
            events = emptyList()
        )

        fun fromMap(map: Map<*, *>, expectedVaultId: String?): VaultIndex {
            val format = (map["format"] as? Number)?.toInt() ?: FORMAT
            require(format == FORMAT) { "Unsupported vault index format $format" }
            val vaultId = map["vaultId"] as? String ?: throw VaultCryptoException("Vault index has no id")
            if (expectedVaultId != null && vaultId != expectedVaultId) {
                throw VaultCryptoException("Vault index belongs to a different vault")
            }
            val items = (map["items"] as? List<*>).orEmpty().mapNotNull { entry ->
                (entry as? Map<*, *>)?.let(VaultItem::fromMap)
            }
            val folders = (map["folders"] as? List<*>).orEmpty().mapNotNull { entry ->
                (entry as? Map<*, *>)?.let(VaultFolder::fromMap)
            }
            val events = (map["events"] as? List<*>).orEmpty().mapNotNull { entry ->
                (entry as? Map<*, *>)?.let(VaultEvent::fromMap)
            }
            return VaultIndex(
                vaultId = vaultId,
                generation = (map["generation"] as? Number)?.toLong() ?: 0L,
                updatedAtMs = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                items = items,
                folders = folders,
                settings = VaultSettings.fromMap(map["settings"] as? Map<*, *>),
                events = events
            )
        }
    }
}
