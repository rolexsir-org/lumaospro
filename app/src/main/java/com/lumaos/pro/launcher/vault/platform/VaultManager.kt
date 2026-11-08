package com.lumaos.pro.launcher.vault.platform

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.vault.core.VaultAutoLock
import com.lumaos.pro.launcher.vault.core.VaultBackup
import com.lumaos.pro.launcher.vault.core.VaultCrypto
import com.lumaos.pro.launcher.vault.core.VaultCryptoException
import com.lumaos.pro.launcher.vault.core.VaultEncoding
import com.lumaos.pro.launcher.vault.core.VaultEventType
import com.lumaos.pro.launcher.vault.core.VaultFiles
import com.lumaos.pro.launcher.vault.core.HiddenApp
import com.lumaos.pro.launcher.vault.core.VaultHiddenApp
import com.lumaos.pro.launcher.vault.core.VaultIndex
import com.lumaos.pro.launcher.vault.core.VaultItem
import com.lumaos.pro.launcher.vault.core.VaultItemKind
import com.lumaos.pro.launcher.vault.core.VaultKeyFile
import com.lumaos.pro.launcher.vault.core.VaultKeyring
import com.lumaos.pro.launcher.vault.core.VaultMetadataStore
import com.lumaos.pro.launcher.vault.core.VaultObjectReader
import com.lumaos.pro.launcher.vault.core.VaultObjectStore
import com.lumaos.pro.launcher.vault.core.VaultPaths
import com.lumaos.pro.launcher.vault.core.VaultPinPolicy
import com.lumaos.pro.launcher.vault.core.VaultRecoveryCode
import com.lumaos.pro.launcher.vault.core.VaultSession
import com.lumaos.pro.launcher.vault.core.VaultUnlockGuard
import com.lumaos.pro.launcher.vault.core.VaultWrappedKey
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.crypto.Cipher

/**
 * The single entry point to LumaOS Vault.
 *
 * Everything the UI can do goes through this class, which keeps three invariants
 * in one place instead of spread over seven screens:
 *
 *  1. **No key, no data.** Every read and write requires an unlocked
 *     [VaultSession]; locking drops the derived stores, so a stale screen cannot
 *     decrypt anything even if it is still on the back stack.
 *  2. **Commit order.** Object payloads are verified and synced before the index
 *     that references them is written; deletions remove the index entry first and
 *     the payload afterwards. A crash can leave an unreferenced encrypted file
 *     (swept later), never a reference to nothing.
 *  3. **Honest state.** Backup status, biometric status and recovery status are
 *     derived from what is actually on disk and in the Keystore, never cached as
 *     an optimistic flag.
 *
 * Long operations (importing, exporting, backing up) run on [ioExecutor] and are
 * exposed as blocking calls: Vault's activities own their lifecycle and post the
 * results back to the main thread explicitly, so no coroutine scope outlives a
 * screen that has been finished.
 */
class VaultManager(private val context: Context) {

    companion object {
        /** Everything private lives here; `noBackupFilesDir` is never backed up. */
        const val VAULT_DIR = "vault"

        const val PREFS = "lumaos_vault"

        /** Hard cap for in-memory note reading; keeps a huge body off the heap. */
        const val MAX_TEXT_BYTES = 2L * 1024 * 1024

        /** Best-effort guess for imported files, used for icons and viewers. */
        fun kindForMime(mimeType: String, name: String): VaultItemKind {
            val lower = mimeType.lowercase()
            val extension = name.substringAfterLast('.', "").lowercase()
            return when {
                lower.startsWith("image/") -> VaultItemKind.IMAGE
                lower.startsWith("video/") -> VaultItemKind.VIDEO
                lower.startsWith("audio/") -> VaultItemKind.AUDIO
                lower == "application/pdf" -> VaultItemKind.SCAN
                lower.startsWith("text/") -> VaultItemKind.DOCUMENT
                extension in setOf("jpg", "jpeg", "png", "webp", "heic", "gif") -> VaultItemKind.IMAGE
                extension in setOf("mp4", "webm", "mkv", "3gp", "mov") -> VaultItemKind.VIDEO
                extension in setOf("m4a", "mp3", "ogg", "opus", "wav", "aac") -> VaultItemKind.AUDIO
                extension == "pdf" -> VaultItemKind.SCAN
                else -> VaultItemKind.DOCUMENT
            }
        }

        /** MIME type for a captured object when the platform does not supply one. */
        fun mimeFor(kind: VaultItemKind, fallbackName: String): String = when (kind) {
            VaultItemKind.IMAGE -> "image/jpeg"
            VaultItemKind.VIDEO -> "video/mp4"
            VaultItemKind.AUDIO -> "audio/mp4"
            VaultItemKind.NOTE -> "text/plain"
            VaultItemKind.SCAN -> "application/pdf"
            else -> if (fallbackName.endsWith(".pdf")) "application/pdf" else "application/octet-stream"
        }
    }

    val paths = VaultPaths(File(context.noBackupFilesDir, VAULT_DIR))
    val session = VaultSession()
    private val keyGuard = VaultDeviceKeyGuard(context)
    private val guard = VaultUnlockGuard(paths.guard)

    /** Single background lane: vault writes are serialised by design. */
    val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lumaos-vault").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    private var unlocked: Unlocked? = null

    /**
     * Ticks whenever the set of hidden apps can have changed.
     *
     * The launcher's drawer is a ViewModel-scoped flow, so it cannot poll; it maps
     * this counter to [hiddenComponentKeys]. Emitting on unlock, lock, hide and
     * restore covers every transition, and the counter carries no content itself.
     */
    private val _hiddenAppsRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val hiddenAppsRevision: kotlinx.coroutines.flow.StateFlow<Long> = _hiddenAppsRevision

    private fun bumpHiddenApps() {
        _hiddenAppsRevision.value = _hiddenAppsRevision.value + 1
    }

    /** A lock happened while the index could still be written; record it next time. */
    private var pendingLockEvent = false

    /** Derived state that exists only while the vault is open. */
    private class Unlocked(
        val vaultId: String,
        val masterKey: ByteArray,
        val metadata: VaultMetadataStore,
        val objects: VaultObjectStore,
        var index: VaultIndex
    )

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    data class BackupState(
        val kind: Kind,
        val lastSuccessAtMs: Long,
        val pending: Boolean,
        val destinationLabel: String?
    ) {
        enum class Kind {
            /** No destination chosen yet. */
            NOT_CONFIGURED,
            /** Latest backup completed and verified; nothing changed since. */
            PROTECTED,
            /** Recent changes are not yet in a verified backup. */
            PENDING,
            /** The last attempt failed; the reason is in the security log. */
            FAILED,
            /** A destination was chosen but cannot be written to any more. */
            DESTINATION_LOST
        }
    }

    data class Status(
        val vaultCreated: Boolean,
        val unlocked: Boolean,
        val autoLock: VaultAutoLock,
        val lockOnLeave: Boolean,
        val biometricEnabled: Boolean,
        val biometricAvailability: VaultDeviceKeyGuard.Availability,
        /** A wrapper exists but the Keystore key behind it is gone. */
        val biometricNeedsSetup: Boolean,
        val recoveryConfigured: Boolean,
        val recoveryConfirmed: Boolean,
        val itemCount: Int,
        val storageBytes: Long
    )

    fun isVaultCreated(): Boolean = paths.exists()

    /** Snapshot for screens that are already unlocked. */
    fun status(): Status {
        val keyFile = readKeyFile()
        val index = synchronized(this) { unlocked?.index }
        val biometricWrapper = keyFile?.biometric
        return Status(
            vaultCreated = keyFile != null,
            unlocked = session.isUnlocked,
            autoLock = index?.settings?.autoLock ?: VaultAutoLock.DEFAULT,
            lockOnLeave = index?.settings?.lockOnLeave ?: false,
            biometricEnabled = index?.settings?.biometricEnabled ?: false,
            biometricAvailability = keyGuard.availability(),
            biometricNeedsSetup = keyFile?.biometric != null && keyGuard.isInvalidated(biometricWrapper?.keystoreAlias),
            recoveryConfigured = keyFile?.recovery != null,
            recoveryConfirmed = (index?.settings?.recoveryConfirmedAtMs ?: 0L) > 0L,
            itemCount = index?.items?.size ?: 0,
            storageBytes = paths.objectBytes() + if (paths.metadata.isFile) paths.metadata.length() else 0L
        )
    }

    fun backupState(): BackupState {
        val settings = synchronized(this) { unlocked?.index?.settings }
        val backup = settings?.backup ?: run {
            // Locked: the destination choice is not secret but lives in the
            // encrypted index, so "not configured" is the honest answer.
            return BackupState(BackupState.Kind.NOT_CONFIGURED, 0L, false, null)
        }
        val destination = backup.destinationUri
        return when {
            destination == null -> BackupState(BackupState.Kind.NOT_CONFIGURED, 0L, false, null)
            !hasDestinationPermission(destination) ->
                BackupState(BackupState.Kind.DESTINATION_LOST, backup.lastSuccessAtMs, true, backup.destinationLabel)
            backup.lastFailure != null && backup.lastSuccessAtMs == 0L ->
                BackupState(BackupState.Kind.FAILED, 0L, true, backup.destinationLabel)
            backup.lastFailure != null && backup.pendingSinceMs > 0 ->
                BackupState(BackupState.Kind.FAILED, backup.lastSuccessAtMs, true, backup.destinationLabel)
            backup.pendingSinceMs > 0L || backup.lastSuccessAtMs == 0L ->
                BackupState(BackupState.Kind.PENDING, backup.lastSuccessAtMs, true, backup.destinationLabel)
            backup.lastVerified ->
                BackupState(BackupState.Kind.PROTECTED, backup.lastSuccessAtMs, false, backup.destinationLabel)
            else -> BackupState(BackupState.Kind.PENDING, backup.lastSuccessAtMs, true, backup.destinationLabel)
        }
    }

    // ------------------------------------------------------------------
    // Creation
    // ------------------------------------------------------------------

    sealed class CreateResult {
        data class Success(val recoveryCode: String, val vaultId: String) : CreateResult()
        data class InvalidPin(val reason: VaultPinPolicy.Reason) : CreateResult()
        data object AlreadyExists : CreateResult()
        data class Failed(val message: String) : CreateResult()
    }

    /**
     * Creates a brand-new vault with its own independent master key.
     *
     * The recovery key is generated here, shown once by the caller and never
     * stored in plaintext: only its wrapping of the master key is persisted. The
     * caller must make the user confirm they wrote it down (see
     * [confirmRecoverySaved]) before the vault is treated as safe to use.
     */
    /**
     * Creates a vault from PIN characters.
     *
     * The `CharArray` overload exists so the lock screen never has to build an
     * immutable `String` of the user's PIN: a `String` cannot be wiped, a character
     * array can. The caller keeps ownership and is expected to zero it.
     */
    @Synchronized
    fun createVault(pin: CharArray, biometricEnabled: Boolean): CreateResult {
        if (isVaultCreated()) return CreateResult.AlreadyExists
        when (val validation = VaultPinPolicy.validate(pin)) {
            is VaultPinPolicy.Result.Rejected -> return CreateResult.InvalidPin(validation.reason)
            VaultPinPolicy.Result.Ok -> Unit
        }
        return try {
            paths.ensureDirectories()
            paths.clearStaging()
            val vaultId = VaultEncoding.newId()
            val masterKey = VaultKeyring.newMasterKey()
            val recoverySecret = VaultRecoveryCode.generate()
            // Calibrated per device so a low-end phone does not spend two seconds
            // per unlock while a fast one is not left with a cheap KDF.
            val iterations = calibratedIterations()
            val keyFile = VaultKeyFile(
                vaultId = vaultId,
                createdAtMs = System.currentTimeMillis(),
                pin = VaultKeyring.wrapWithPin(masterKey, pin, vaultId, iterations = iterations),
                recovery = VaultKeyring.wrapWithRecovery(masterKey, recoverySecret, vaultId),
                biometric = null
            )
            writeKeyFile(keyFile)
            val metadata = VaultMetadataStore(paths, VaultMetadataStore.keyFor(masterKey))
            val seeded = metadata.commit(VaultIndex.empty(vaultId).withEvent(VaultEventType.CREATED))
            val objects = VaultObjectStore(paths, masterKey)
            unlocked = Unlocked(vaultId, masterKey, metadata, objects, seeded)
            session.configure(seeded.settings.autoLock, seeded.settings.lockOnLeave)
            session.unlock(VaultSession.Keys(masterKey, vaultId))
            bumpHiddenApps()
            LumaLog.i("Vault created") // never logs identifiers or content
            CreateResult.Success(VaultRecoveryCode.format(recoverySecret), vaultId).also {
                VaultCrypto.zeroize(recoverySecret)
            }
        } catch (e: Exception) {
            LumaLog.e("Vault creation failed: ${e.javaClass.simpleName}")
            CreateResult.Failed(userMessage(e))
        }
    }

    /** Convenience overload; the String's characters are copied and wiped again. */
    @Synchronized
    fun createVault(pin: String, biometricEnabled: Boolean): CreateResult {
        val chars = pin.toCharArray()
        return try {
            createVault(chars, biometricEnabled)
        } finally {
            chars.fill('\u0000')
        }
    }

    /** Records that the user confirmed they stored the recovery key. */
    @Synchronized
    fun confirmRecoverySaved() {
        val current = unlocked ?: return
        val index = current.index.withSettings(
            current.index.settings.copy(recoveryConfirmedAtMs = System.currentTimeMillis())
        )
        commit(current, index)
    }

    // ------------------------------------------------------------------
    // Unlock
    // ------------------------------------------------------------------

    sealed class UnlockResult {
        data object Success : UnlockResult()
        data class WrongPin(val failures: Int, val retryAfterMs: Long) : UnlockResult()
        data class Throttled(val retryAfterMs: Long) : UnlockResult()
        data object NoPinConfigured : UnlockResult()
        data object WrongRecoveryKey : UnlockResult()
        data object KeysInvalidated : UnlockResult()
        data class Failed(val message: String) : UnlockResult()
    }

    /** Milliseconds the caller must wait before another PIN attempt is allowed. */
    fun pinRetryDelayMs(): Long = guard.state().remainingDelayMs(System.currentTimeMillis())

    /**
     * Verifies a PIN.
     *
     * The characters are used in place and not copied: the caller owns the array and
     * the UI wipes it as soon as this returns. Nothing derived from the PIN is
     * cached - the only value kept across calls is the calibrated iteration *count*.
     */
    @Synchronized
    fun unlockWithPin(pin: CharArray): UnlockResult {
        val now = System.currentTimeMillis()
        val state = guard.state(now)
        if (state.isThrottled(now)) return UnlockResult.Throttled(state.remainingDelayMs(now))
        val keyFile = readKeyFile() ?: return UnlockResult.Failed("Vault is not set up")
        val wrapper = keyFile.pin ?: return UnlockResult.NoPinConfigured
        val masterKey = try {
            VaultKeyring.unwrapWithPin(wrapper, pin, keyFile.vaultId)
        } catch (e: VaultCryptoException) {
            val failed = guard.recordFailure(now)
            return UnlockResult.WrongPin(failed.failures, failed.remainingDelayMs(now))
        } catch (e: Exception) {
            return UnlockResult.Failed(userMessage(e))
        }
        return completeUnlock(keyFile, masterKey, VaultEventType.UNLOCKED_PIN)
    }

    /** Convenience overload for callers that already hold a String. */
    @Synchronized
    fun unlockWithPin(pin: String): UnlockResult {
        val chars = pin.toCharArray()
        return try {
            unlockWithPin(chars)
        } finally {
            chars.fill('\u0000')
        }
    }

    /**
     * Unlocks with the recovery key.
     *
     * Works with no PIN, no biometrics and no Keystore state, which is why it is
     * the documented path after a forgotten PIN, a wiped biometric enrolment or a
     * restored backup on a new phone. A successful recovery unlock must be
     * followed by [setPinAfterRecovery] so the vault again has a device-local PIN.
     */
    @Synchronized
    fun unlockWithRecovery(code: String): UnlockResult {
        val keyFile = readKeyFile() ?: return UnlockResult.Failed("Vault is not set up")
        val wrapper = keyFile.recovery ?: return UnlockResult.WrongRecoveryKey
        val secret = VaultRecoveryCode.parse(code) ?: return UnlockResult.WrongRecoveryKey
        val masterKey = try {
            VaultKeyring.unwrapWithRecovery(wrapper, secret, keyFile.vaultId)
        } catch (e: VaultCryptoException) {
            return UnlockResult.WrongRecoveryKey
        } finally {
            VaultCrypto.zeroize(secret)
        }
        return completeUnlock(keyFile, masterKey, VaultEventType.UNLOCKED_RECOVERY)
    }

    /** After a recovery unlock the vault has no usable PIN; set one now. */
    @Synchronized
    fun setPinAfterRecovery(pin: CharArray): VaultPinPolicy.Result {
        val current = unlocked ?: return VaultPinPolicy.Result.Rejected(VaultPinPolicy.Reason.TOO_SHORT)
        val validation = VaultPinPolicy.validate(pin)
        if (validation is VaultPinPolicy.Result.Rejected) return validation
        val keyFile = readKeyFile() ?: return VaultPinPolicy.Result.Rejected(VaultPinPolicy.Reason.TOO_SHORT)
        writeKeyFile(
            keyFile.copy(
                pin = VaultKeyring.wrapWithPin(
                    current.masterKey,
                    pin,
                    current.vaultId,
                    iterations = calibratedIterations()
                )
            )
        )
        commit(current, current.index.withEvent(VaultEventType.PIN_CHANGED))
        return VaultPinPolicy.Result.Ok
    }

    /** Convenience overload for callers that already hold a String. */
    @Synchronized
    fun setPinAfterRecovery(pin: String): VaultPinPolicy.Result {
        val chars = pin.toCharArray()
        return try {
            setPinAfterRecovery(chars)
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun completeUnlock(keyFile: VaultKeyFile, masterKey: ByteArray, event: VaultEventType): UnlockResult {
        var metadataKey: ByteArray? = null
        return try {
            metadataKey = VaultMetadataStore.keyFor(masterKey)
            val metadata = VaultMetadataStore(paths, metadataKey)
            val stored = metadata.read(keyFile.vaultId)
                ?: run {
                    VaultCrypto.zeroize(masterKey)
                    VaultCrypto.zeroize(metadataKey)
                    return UnlockResult.Failed("Vault data is missing")
                }
            val objects = VaultObjectStore(paths, masterKey)
            var index = stored
            if (pendingLockEvent) {
                index = index.withEvent(VaultEventType.LOCKED)
                pendingLockEvent = false
            }
            val withEvent = metadata.commit(index.withEvent(event))
            unlocked = Unlocked(keyFile.vaultId, masterKey, metadata, objects, withEvent)
            session.configure(withEvent.settings.autoLock, withEvent.settings.lockOnLeave)
            session.unlock(VaultSession.Keys(masterKey, keyFile.vaultId))
            guard.recordSuccess()
            // The old launcher-level Hide Apps list becomes Vault data exactly once.
            // A failure here must not stop an otherwise valid unlock: the legacy
            // preference is still there and the migration retries next time.
            runCatching { completeLegacyHiddenAppMigration() }
                .onFailure { LumaLog.e("Legacy hidden-app import did not complete", it) }
            bumpHiddenApps()
            // Anything left in staging belongs to an operation that died with the
            // process; it is never the only copy of anything.
            paths.clearStaging()
            runCatching { objects.sweepOrphans(withEvent) }
            UnlockResult.Success
        } catch (e: VaultCryptoException) {
            VaultCrypto.zeroize(masterKey)
            metadataKey?.let { VaultCrypto.zeroize(it) }
            UnlockResult.Failed("Vault data could not be verified")
        } catch (e: Exception) {
            VaultCrypto.zeroize(masterKey)
            metadataKey?.let { VaultCrypto.zeroize(it) }
            UnlockResult.Failed(userMessage(e))
        }
    }

    // ------------------------------------------------------------------
    // Lock
    // ------------------------------------------------------------------

    /**
     * Locks the vault: logs the event, drops derived stores, zeroizes the master
     * key and hides all content.
     */
    @Synchronized
    fun lock(reason: VaultSession.LockReason = VaultSession.LockReason.USER) {
        val current = unlocked
        if (current != null) {
            // Locking happens on the main thread (screen-off, activity stop), so no
            // disk write is allowed here: the encrypted index cannot be written once
            // the key is gone. The event is queued and committed with the next
            // unlock instead, which costs nothing and cannot block the UI.
            pendingLockEvent = true
            // Destroy the derived index key now; the session destroys the master key
            // it owns a few lines below. Both must go, or key material would survive
            // the lock in the heap.
            runCatching { current.metadata.destroy() }
        }
        unlocked = null
        session.lock(reason)
        bumpHiddenApps()
        paths.clearStaging()
        VaultMediaCache.clear()
        VaultTransientFiles.purge(paths)
    }

    // ------------------------------------------------------------------
    // PIN and recovery management
    // ------------------------------------------------------------------

    sealed class PinChangeResult {
        data object Success : PinChangeResult()
        data object WrongCurrentPin : PinChangeResult()
        data class InvalidNewPin(val reason: VaultPinPolicy.Reason) : PinChangeResult()
        data class Failed(val message: String) : PinChangeResult()
    }

    /**
     * Changes the PIN by re-wrapping the master key.
     *
     * No stored object is touched: the master key is unchanged, so a PIN change is
     * a single 32-byte authenticated rewrite regardless of how many gigabytes the
     * vault holds.
     */
    @Synchronized
    fun changePin(currentPin: CharArray, newPin: CharArray): PinChangeResult {
        val current = unlocked ?: return PinChangeResult.Failed("Vault is locked")
        when (val validation = VaultPinPolicy.validate(newPin)) {
            is VaultPinPolicy.Result.Rejected -> return PinChangeResult.InvalidNewPin(validation.reason)
            VaultPinPolicy.Result.Ok -> Unit
        }
        val keyFile = readKeyFile() ?: return PinChangeResult.Failed("Vault is not set up")
        val existing = keyFile.pin
        if (existing != null) {
            // The unwrapped copy of the master key only proves the current PIN, so it
            // is destroyed immediately instead of being handed to a session.
            val verified = try {
                VaultKeyring.unwrapWithPin(existing, currentPin, keyFile.vaultId)
            } catch (e: Exception) {
                null
            }
            if (verified == null) return PinChangeResult.WrongCurrentPin
            VaultCrypto.zeroize(verified)
        }
        return try {
            writeKeyFile(
                keyFile.copy(
                    pin = VaultKeyring.wrapWithPin(
                        current.masterKey,
                        newPin,
                        current.vaultId,
                        iterations = calibratedIterations()
                    )
                )
            )
            commit(current, current.index.withEvent(VaultEventType.PIN_CHANGED))
            PinChangeResult.Success
        } catch (e: Exception) {
            PinChangeResult.Failed(userMessage(e))
        }
    }

    /** Convenience overload for callers that already hold Strings. */
    @Synchronized
    fun changePin(currentPin: String, newPin: String): PinChangeResult {
        val currentChars = currentPin.toCharArray()
        val newChars = newPin.toCharArray()
        return try {
            changePin(currentChars, newChars)
        } finally {
            currentChars.fill('\u0000')
            newChars.fill('\u0000')
        }
    }

    data class RecoveryStatus(
        val configured: Boolean,
        val confirmed: Boolean,
        val createdAtMs: Long,
        val rotatedAtMs: Long
    )

    fun recoveryStatus(): RecoveryStatus {
        val keyFile = readKeyFile()
        val settings = synchronized(this) { unlocked?.index?.settings }
        return RecoveryStatus(
            configured = keyFile?.recovery != null,
            confirmed = (settings?.recoveryConfirmedAtMs ?: 0L) > 0L,
            createdAtMs = keyFile?.recovery?.createdAtMs ?: 0L,
            rotatedAtMs = settings?.recoveryRotatedAtMs ?: 0L
        )
    }

    /**
     * Issues a new recovery key and re-wraps the same master key under it.
     *
     * The old recovery key stops opening *this* vault immediately. Backups made
     * before the rotation keep their own wrapped copy of the master key, so they
     * still need the *previous* recovery key - the UI says so explicitly rather
     * than implying that rotation retroactively protects old backups.
     */
    @Synchronized
    fun rotateRecoveryKey(): String? {
        val current = unlocked ?: return null
        val keyFile = readKeyFile() ?: return null
        val secret = VaultRecoveryCode.generate()
        try {
            writeKeyFile(keyFile.copy(recovery = VaultKeyring.wrapWithRecovery(current.masterKey, secret, current.vaultId)))
            commit(
                current,
                current.index.withSettings(
                    current.index.settings.copy(recoveryRotatedAtMs = System.currentTimeMillis())
                ).withEvent(VaultEventType.RECOVERY_ROTATED)
            )
            return VaultRecoveryCode.format(secret)
        } finally {
            VaultCrypto.zeroize(secret)
        }
    }

    // ------------------------------------------------------------------
    // Biometrics
    // ------------------------------------------------------------------

    fun biometricAvailability(): VaultDeviceKeyGuard.Availability = keyGuard.availability()

    fun isBiometricEnabled(): Boolean = readKeyFile()?.biometric != null &&
        synchronized(this) { unlocked?.index?.settings?.biometricEnabled != false }

    /** True when the wrapper survived but the Keystore key did not. */
    fun biometricNeedsSetup(): Boolean {
        val wrapper = readKeyFile()?.biometric ?: return false
        return keyGuard.isInvalidated(wrapper.keystoreAlias)
    }

    /**
     * Prepares biometric enrolment: creates a device-bound Keystore key and
     * returns the cipher the prompt must authorise.
     */
    @Synchronized
    fun prepareBiometricEnrollment(): Cipher? {
        val current = unlocked ?: return null
        if (keyGuard.availability() != VaultDeviceKeyGuard.Availability.AVAILABLE) return null
        val alias = VaultDeviceKeyGuard.aliasFor(current.vaultId)
        return runCatching { keyGuard.createKeyAndEncryptCipher(alias) }.getOrElse {
            LumaLog.e("Biometric key could not be created: ${it.javaClass.simpleName}")
            null
        }
    }

    /** Called with the cipher returned by [prepareBiometricEnrollment] after the prompt succeeded. */
    @Synchronized
    fun completeBiometricEnrollment(cipher: Cipher): Boolean {
        val current = unlocked ?: return false
        val keyFile = readKeyFile() ?: return false
        val alias = VaultDeviceKeyGuard.aliasFor(current.vaultId)
        return try {
            val wrapped = cipher.doFinal(current.masterKey)
            val iv = cipher.iv ?: throw VaultCryptoException("Keystore cipher has no IV")
            writeKeyFile(
                keyFile.copy(
                    biometric = VaultWrappedKey(
                        scheme = VaultKeyring.BIOMETRIC_SCHEME,
                        salt = VaultCrypto.EMPTY,
                        iterations = 0,
                        iv = iv,
                        wrapped = wrapped,
                        keystoreAlias = alias,
                        createdAtMs = System.currentTimeMillis()
                    )
                )
            )
            commit(
                current,
                current.index.withSettings(current.index.settings.copy(biometricEnabled = true))
                    .withEvent(VaultEventType.BIOMETRIC_ENABLED)
            )
            true
        } catch (e: Exception) {
            LumaLog.e("Biometric enrolment failed: ${e.javaClass.simpleName}")
            keyGuard.deleteKey(alias)
            false
        }
    }

    /**
     * Prepares a biometric unlock: returns the cipher to hand to the prompt.
     *
     * Returns null when there is no wrapper or the Keystore key has been
     * invalidated; the caller then offers the PIN instead and says why.
     */
    @Synchronized
    fun prepareBiometricUnlock(): Cipher? {
        val keyFile = readKeyFile() ?: return null
        val wrapper = keyFile.biometric ?: return null
        val alias = wrapper.keystoreAlias ?: return null
        if (keyGuard.isInvalidated(alias)) {
            markBiometricInvalidated()
            return null
        }
        return runCatching { keyGuard.decryptCipher(alias, wrapper.iv) }.getOrElse {
            markBiometricInvalidated()
            null
        }
    }

    /** Completes a biometric unlock with the cipher the prompt authorised. */
    @Synchronized
    fun completeBiometricUnlock(cipher: Cipher): UnlockResult {
        val keyFile = readKeyFile() ?: return UnlockResult.Failed("Vault is not set up")
        val wrapper = keyFile.biometric ?: return UnlockResult.Failed("Biometrics are not set up")
        val masterKey = try {
            cipher.doFinal(wrapper.wrapped)
        } catch (e: Exception) {
            // The Keystore refused: biometric enrolment changed, or the key was
            // invalidated. Drop the dead wrapper so the UI can be truthful.
            markBiometricInvalidated()
            return UnlockResult.KeysInvalidated
        }
        return completeUnlock(keyFile, masterKey, VaultEventType.UNLOCKED_BIOMETRIC)
    }

    @Synchronized
    fun disableBiometric() {
        val keyFile = readKeyFile() ?: return
        keyFile.biometric?.keystoreAlias?.let { keyGuard.deleteKey(it) }
        runCatching { writeKeyFile(keyFile.copy(biometric = null)) }
        synchronized(this) { unlocked }?.let { current ->
            commit(
                current,
                current.index.withSettings(current.index.settings.copy(biometricEnabled = false))
                    .withEvent(VaultEventType.BIOMETRIC_DISABLED)
            )
        }
    }

    /**
     * Replaces the dead wrapper with nothing and records why.
     *
     * A new biometric wrapper can only be created from an unlocked vault, because
     * the Keystore key that encrypts the master key requires an authenticated user
     * - so re-enrolment is always "unlock with PIN, then turn biometrics on".
     */
    @Synchronized
    private fun markBiometricInvalidated() {
        val keyFile = readKeyFile() ?: return
        val alias = keyFile.biometric?.keystoreAlias
        runCatching { if (alias != null) keyGuard.deleteKey(alias) }
        runCatching { writeKeyFile(keyFile.copy(biometric = null)) }
        synchronized(this) { unlocked }?.let { current ->
            runCatching {
                commit(
                    current,
                    current.index.withSettings(current.index.settings.copy(biometricEnabled = false))
                        .withEvent(VaultEventType.BIOMETRIC_INVALIDATED)
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Session settings
    // ------------------------------------------------------------------

    @Synchronized
    fun updateLockSettings(autoLock: VaultAutoLock, lockOnLeave: Boolean) {
        session.configure(autoLock, lockOnLeave)
        val current = unlocked ?: return
        commit(current, current.index.withSettings(current.index.settings.copy(autoLock = autoLock, lockOnLeave = lockOnLeave)))
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    fun index(): VaultIndex? = synchronized(this) { unlocked?.index }

    fun items(kinds: Set<VaultItemKind> = VaultItemKind.entries.toSet()): List<VaultItem> =
        index()?.items?.filter { it.kind in kinds }?.sortedByDescending { it.capturedAtMs } ?: emptyList()

    fun item(id: String): VaultItem? = index()?.item(id)

    /**
     * Encrypts a stream into the vault.
     *
     * The source can be a SAF descriptor, a camera scratch file or a note body;
     * it is consumed once and never copied in the clear.
     */
    @Synchronized
    fun addStream(
        source: InputStream,
        kind: VaultItemKind,
        name: String,
        mimeType: String,
        folderId: String? = null,
        origin: String = "import",
        capturedAtMs: Long = System.currentTimeMillis()
    ): VaultItem {
        val current = unlocked ?: throw IllegalStateException("Vault is locked")
        val stored = current.objects.store(source)
        var width = 0
        var height = 0
        var pages = 0
        if (kind == VaultItemKind.IMAGE) {
            val probe = VaultItem(
                id = stored.id,
                kind = kind,
                name = name,
                mimeType = mimeType,
                sizeBytes = stored.sizeBytes,
                addedAtMs = System.currentTimeMillis(),
                capturedAtMs = capturedAtMs,
                folderId = folderId,
                dekIv = stored.dekIv,
                wrappedDek = stored.wrappedDek
            )
            val bounds = VaultMediaCache.boundsOf(current.objects, probe)
            width = bounds.first
            height = bounds.second
        }
        val item = VaultItem(
            id = stored.id,
            kind = kind,
            name = name,
            mimeType = mimeType,
            sizeBytes = stored.sizeBytes,
            addedAtMs = System.currentTimeMillis(),
            capturedAtMs = capturedAtMs,
            folderId = folderId,
            dekIv = stored.dekIv,
            wrappedDek = stored.wrappedDek,
            width = width,
            height = height,
            pageCount = pages,
            origin = origin
        )
        commit(current, current.index.withItem(item).markBackupPending())
        return item
    }

    /** Imports a document chosen through the Storage Access Framework. */
    @Synchronized
    fun importUri(uri: Uri, name: String, mimeType: String, kind: VaultItemKind? = null): VaultItem {
        val resolvedKind = kind ?: kindForMime(mimeType, name)
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("The selected file could not be opened")
        return stream.use { addStream(it, resolvedKind, name, mimeType, origin = "import") }
    }

    /** Creates a note: the body is stored as an encrypted object like any other file. */
    @Synchronized
    fun addNote(title: String, body: String): VaultItem {
        val name = title.ifBlank { "Untitled note" }
        val bytes = body.toByteArray(Charsets.UTF_8)
        return try {
            addStream(bytes.inputStream(), VaultItemKind.NOTE, name, "text/plain", origin = "note")
        } finally {
            VaultCrypto.zeroize(bytes)
        }
    }

    /** Reads a note's body, decrypting it only into memory. */
    fun readNote(item: VaultItem): String {
        val current = unlocked ?: throw IllegalStateException("Vault is locked")
        if (item.sizeBytes > MAX_TEXT_BYTES) throw IllegalStateException("Note is too large to open")
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            current.objects.copyTo(item, out)
            out.toByteArray()
        }
        return try {
            String(bytes, Charsets.UTF_8)
        } finally {
            VaultCrypto.zeroize(bytes)
        }
    }

    /**
     * Rewrites a note.
     *
     * The new body is written as a new object with a new data key and committed
     * before the old payload is shredded, so an interrupted edit cannot destroy
     * the version the user started from.
     */
    @Synchronized
    fun updateNote(item: VaultItem, title: String, body: String): VaultItem {
        val current = unlocked ?: throw IllegalStateException("Vault is locked")
        val bytes = body.toByteArray(Charsets.UTF_8)
        val stored = try {
            current.objects.store(bytes.inputStream())
        } finally {
            VaultCrypto.zeroize(bytes)
        }
        val updated = item.copy(
            id = stored.id,
            name = title.ifBlank { item.name },
            sizeBytes = stored.sizeBytes,
            dekIv = stored.dekIv,
            wrappedDek = stored.wrappedDek,
            capturedAtMs = System.currentTimeMillis()
        )
        val next = current.index.withoutItem(item.id).withItem(updated).markBackupPending()
        // The old payload is replaced here, so the commit must not leave a
        // generation behind that still holds its wrapped key.
        current.index = current.metadata.commitAndScrubPrevious(next)
        VaultMediaCache.evict(item.id)
        VaultMediaCache.evict(updated.id)
        current.objects.delete(item.id)
        return updated
    }

    @Synchronized
    fun rename(item: VaultItem, newName: String): VaultItem {
        val current = unlocked ?: throw IllegalStateException("Vault is locked")
        val cleaned = newName.trim().ifBlank { item.name }
        val updated = item.copy(name = cleaned)
        commit(current, current.index.withItem(updated).markBackupPending())
        return updated
    }

    @Synchronized
    fun setFavorite(item: VaultItem, favorite: Boolean): VaultItem {
        val current = unlocked ?: throw IllegalStateException("Vault is locked")
        val updated = item.copy(favorite = favorite)
        commit(current, current.index.withItem(updated))
        return updated
    }

    // ------------------------------------------------------------------
    // Hidden apps
    // ------------------------------------------------------------------

    /**
     * The launcher entries the user has moved into the Vault.
     *
     * This is the *only* way the launcher learns what to hide, and it returns an
     * empty set while the vault is locked - which is exactly why the drawer of a
     * locked device shows every app, and why hiding state cannot be read without
     * authenticating. The list itself is encrypted at rest inside `meta.bin`.
     */
    @Synchronized
    fun hiddenApps(): List<VaultHiddenApp> =
        synchronized(this) { unlocked?.index }?.settings?.hiddenApps.orEmpty().map {
            VaultHiddenApp(it.packageName, it.className, it.userSerial, it.label, it.hiddenAtMs)
        }

    /** The hidden component keys in `serial:pkg/cls` form, or empty when locked. */
    fun hiddenComponentKeys(): Set<String> =
        synchronized(this) { unlocked?.index }?.hiddenComponentKeys() ?: emptySet()

    /**
     * Hides [app] from the launcher drawer.
     *
     * Nothing is done to the application itself: it is not uninstalled, disabled,
     * suspended or cleared. The only change is this encrypted list plus the
     * drawer's filter, so the app keeps running and keeps its data - and any
     * other launcher on the device still shows it. `VaultHiddenApps` in the UI
     * says so in plain words, because "hidden" is not "isolated".
     */
    @Synchronized
    fun hideApp(app: VaultHiddenApp): Boolean {
        val current = unlocked ?: throw IllegalStateException("Vault is locked")
        if (app.packageName.isBlank() || app.className.isBlank()) return false
        if (current.index.settings.hiddenApps.any { it.componentKey == app.componentKey }) return true
        commit(
            current,
            current.index.withHiddenApp(
                HiddenApp(app.packageName, app.className, app.userSerial, app.label, System.currentTimeMillis())
            ).withEvent(VaultEventType.HIDDEN_APP_ADDED)
        )
        bumpHiddenApps()
        return true
    }

    /**
     * Restores a hidden app to the drawer.
     *
     * Removing the entry is the whole operation: the app was never touched, so
     * there is nothing to undo beyond forgetting that it was hidden.
     */
    @Synchronized
    fun unhideApp(componentKey: String): Boolean {
        val current = unlocked ?: throw IllegalStateException("Vault is locked")
        if (current.index.settings.hiddenApps.none { it.componentKey == componentKey }) return false
        commit(current, current.index.withoutHiddenApp(componentKey).withEvent(VaultEventType.HIDDEN_APP_REMOVED))
        bumpHiddenApps()
        return true
    }

    /**
     * Moves hidden-app selections from the old plain-preferences list into the
     * encrypted index. Runs once per vault, and only ever *adds*.
     *
     * The previous implementation kept `hidden_apps_<profile>` in DataStore. Users
     * who already hid apps must not see them reappear on upgrade, so on the first
     * unlock after this change the entries are imported. The caller passes the
     * legacy keys; an entry already present (or written concurrently) is skipped,
     * so running this twice changes nothing. The legacy preference itself is left
     * in place - the migration is additive and reversible, and nothing is deleted
     * from the user's existing data.
     *
     * Returns the number of entries imported, for the security log and tests.
     */
    @Synchronized
    fun importLegacyHiddenApps(legacy: List<VaultHiddenApp>): Int {
        val current = unlocked ?: return 0
        if (legacy.isEmpty()) return 0
        val existing = current.index.settings.hiddenApps.mapTo(HashSet()) { it.componentKey }
        val additions = legacy
            .filter { it.packageName.isNotBlank() && it.className.isNotBlank() }
            .filter { it.componentKey !in existing }
            .distinctBy { it.componentKey }
            .map { HiddenApp(it.packageName, it.className, it.userSerial, it.label, System.currentTimeMillis()) }
        if (additions.isEmpty()) return 0
        var index = current.index
        additions.forEach { index = index.withHiddenApp(it) }
        commit(current, index.withEvent(VaultEventType.HIDDEN_APP_ADDED))
        bumpHiddenApps()
        return additions.size
    }

    /**
     * Runs [importLegacyHiddenApps] once per vault, on the first unlock.
     *
     * The marker is written only after a successful commit. A device that upgrades
     * from the old launcher-level Hide Apps therefore keeps every hidden app, and a
     * process that dies mid-import simply tries again next unlock - the import is
     * additive and idempotent, so re-running it cannot duplicate or resurrect
     * anything.
     *
     * If there is nothing to import, the marker is still written: the prefs are
     * empty, so there is nothing to return for, and leaving it unset would re-read
     * them on every unlock for the life of the install.
     */
    @Synchronized
    private fun completeLegacyHiddenAppMigration() {
        if (paths.launchMigration.isFile) return
        // Null means "not known yet". The old list lives in DataStore, which has no
        // synchronous read, so the caller caches it in the background at startup.
        // Unlocking before that first read must not be mistaken for "no legacy list":
        // it would mark the migration done and strand the selection in the old pref.
        val legacy = runCatching { legacyHiddenAppReader() }.getOrNull() ?: return
        val imported = importLegacyHiddenApps(legacy)
        if (legacy.isNotEmpty() && imported == 0 && !legacyEntriesPresent(legacy)) {
            // Nothing was written and nothing was already there: treat as unfinished
            // so a transient failure is retried rather than silently losing the list.
            return
        }
        runCatching {
            paths.ensureDirectories()
            paths.launchMigration.writeText(LAUNCH_MIGRATION_VERSION)
        }.onFailure { LumaLog.e("Could not record launch migration marker", it) }
    }

    /** True when every legacy entry is already in the index (a no-op import). */
    private fun legacyEntriesPresent(legacy: List<VaultHiddenApp>): Boolean {
        val existing = hiddenComponentKeys()
        return legacy.all { it.componentKey in existing }
    }

    /**
     * Supplies the old Hide Apps preference, or null while that value has not been
     * read yet.
     *
     * Set by [com.lumaos.pro.launcher.core.LumaCore] at startup, so the Vault does
     * not have to depend on the preference layer. The default returns an empty list,
     * which keeps unit tests and a fresh install trivial.
     */
    @Volatile
    var legacyHiddenAppReader: () -> List<VaultHiddenApp>? = { emptyList() }

    /**
     * Removes an item: index first, payload second, stale generations last.
     *
     * The order is what makes an interrupted deletion safe. The new index (without
     * the item) is committed and fsynced before anything is destroyed, so a crash
     * at any point leaves either "the item still exists" or "the item is gone and
     * its payload is an unreferenced file that [VaultObjectStore.sweepOrphans]
     * shreds at the next unlock". It can never leave an index entry pointing at a
     * half-destroyed payload.
     *
     * The commit also overwrites `meta.bin.bak`, and this is the part that makes
     * the deletion cryptographic rather than merely structural: the backup
     * generation still contained the object's *wrapped data key*. Removing the
     * payload while leaving that key behind would allow anyone who later recovered
     * both files to decrypt the content again. Once the key is gone from every
     * generation of the index, no remaining legitimate key can open that object -
     * not the master key, not an index restored from a backup, not an older PIN
     * wrapper (which protects the same master key, so it does not help either).
     *
     * Thumbnails live in an in-memory LRU only - nothing decrypted is ever written
     * to disk - so evicting the entry drops the last decoded copy of the pixels.
     */
    @Synchronized
    fun delete(item: VaultItem) {
        val current = unlocked ?: throw IllegalStateException("Vault is locked")
        val committed = current.metadata.commitAndScrubPrevious(
            current.index.withoutItem(item.id).markBackupPending()
        )
        current.index = committed
        VaultMediaCache.evict(item.id)
        current.objects.delete(item.id)
    }

    /** Opens an item for reading (image decoding, playback, viewer). */
    fun open(item: VaultItem): VaultObjectReader {
        val current = unlocked ?: throw IllegalStateException("Vault is locked")
        return current.objects.open(item)
    }

    /**
     * Decodes a thumbnail in memory (never to disk) for grids and recents.
     */
    fun thumbnail(item: VaultItem, maxPixels: Int = 384): android.graphics.Bitmap? {
        val current = unlocked ?: return null
        return VaultMediaCache.bitmap(current.objects, item, maxPixels)
    }

    /** Decodes a full-size view of an image, bounded by [maxPixels] on the long edge. */
    fun bitmap(item: VaultItem, maxPixels: Int = 2048): android.graphics.Bitmap? {
        val current = unlocked ?: return null
        return VaultMediaCache.bitmap(current.objects, item, maxPixels)
    }

    /**
     * Random-access decrypting source for `MediaPlayer`, so audio and video play
     * from ciphertext without a plaintext copy anywhere.
     */
    fun mediaSource(item: VaultItem): VaultMediaDataSource? {
        val current = unlocked ?: return null
        return runCatching { VaultMediaDataSource(current.objects.open(item)) }.getOrNull()
    }

    /** Streams an item's plaintext to a caller-supplied stream (export/view). */
    fun copyTo(item: VaultItem, sink: OutputStream): Long {
        val current = unlocked ?: throw IllegalStateException("Vault is locked")
        return current.objects.copyTo(item, sink)
    }

    /**
     * Exports one item to a user-chosen destination.
     *
     * Always an explicit user action: the moment bytes leave the vault they are
     * ordinary files, visible to galleries, other apps and cloud sync. The UI
     * says so before it opens the destination picker.
     */
    @Synchronized
    fun exportTo(item: VaultItem, destination: Uri): Long {
        val current = unlocked ?: throw IllegalStateException("Vault is locked")
        val stream: OutputStream = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IllegalStateException("The export destination could not be opened")
        val written = stream.use { current.objects.copyTo(item, it) }
        commit(current, current.index.withEvent(VaultEventType.EXPORTED))
        return written
    }

    // ------------------------------------------------------------------
    // Backup and restore
    // ------------------------------------------------------------------

    data class BackupResult(val bytes: Long, val verified: Boolean, val destinationLabel: String?)

    /**
     * Writes a verified encrypted backup to [destination].
     *
     * "Verified" means the container was read back and its integrity digest and
     * structure checked. Success is only recorded after that, so the security
     * centre can never claim protection that does not exist.
     */
    @Synchronized
    fun backupTo(destination: Uri, label: String?): BackupResult {
        val current = unlocked ?: throw IllegalStateException("Vault is locked")
        val keyFile = readKeyFile() ?: throw IllegalStateException("Vault is not set up")
        val recovery = keyFile.recovery ?: throw IllegalStateException("No recovery key is configured")
        if (!paths.metadata.isFile) throw IllegalStateException("Vault index is missing")

        val started = System.currentTimeMillis()
        // The container is finished and integrity-checked on local storage first.
        // The destination is touched only once a complete, verified backup exists,
        // so a provider that fails mid-upload cannot leave a truncated file behind
        // that still looks like a backup - and nothing is ever written over a good
        // backup that has not been replaced by a complete one.
        val staged = runCatching { paths.stagingFile(".lvbk") }.getOrNull()
            ?: throw IllegalStateException("The staging area is unavailable")
        val written = try {
            VaultBackup.writeToFile(
                paths = paths,
                vaultId = current.vaultId,
                recoveryWrapper = recovery,
                requiredObjectIds = current.index.items.map { item -> item.id }.toSet(),
                file = staged
            )
            val out = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IllegalStateException("The backup destination could not be opened")
            var copied = 0L
            out.use { sink ->
                FileInputStream(staged).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        copied += read
                    }
                }
                sink.flush()
            }
            if (copied != staged.length()) throw IllegalStateException("The backup was not written completely")
            copied
        } catch (e: Exception) {
            recordBackupFailure(current, userMessage(e), label, destination, started)
            VaultFiles.shred(staged)
            throw e
        }
        VaultFiles.shred(staged)
        val verified = verifyContainer(destination)
        if (!verified) {
            recordBackupFailure(current, "The written backup did not verify", label, destination, started)
            throw IllegalStateException("The backup did not verify")
        }
        val settings = current.index.settings.copy(
            backup = current.index.settings.backup.copy(
                destinationUri = destination.toString(),
                destinationLabel = label ?: current.index.settings.backup.destinationLabel,
                lastSuccessAtMs = System.currentTimeMillis(),
                lastAttemptAtMs = started,
                lastFailure = null,
                lastVerified = true,
                lastBytes = written,
                pendingSinceMs = 0L
            )
        )
        commit(current, current.index.withSettings(settings).withEvent(VaultEventType.BACKUP_OK))
        persistDestination(destination)
        return BackupResult(written, true, label)
    }

    private fun recordBackupFailure(
        current: Unlocked,
        message: String,
        label: String?,
        destination: Uri,
        startedAtMs: Long
    ) {
        val settings = current.index.settings.copy(
            backup = current.index.settings.backup.copy(
                destinationUri = destination.toString(),
                destinationLabel = label ?: current.index.settings.backup.destinationLabel,
                lastAttemptAtMs = startedAtMs,
                lastFailure = message,
                lastVerified = false
            )
        )
        runCatching { commit(current, current.index.withSettings(settings).withEvent(VaultEventType.BACKUP_FAILED)) }
    }

    /** Remembers a destination so the next backup can offer it again. */
    @Synchronized
    fun rememberDestination(destination: Uri, label: String?) {
        val current = unlocked ?: return
        val settings = current.index.settings.copy(
            backup = current.index.settings.backup.copy(
                destinationUri = destination.toString(),
                destinationLabel = label ?: destination.lastPathSegment
            )
        )
        commit(current, current.index.withSettings(settings))
        persistDestination(destination)
    }

    data class RestorePreview(
        val vaultId: String,
        val createdAtMs: Long,
        val itemCount: Int,
        val totalBytes: Long
    )

    /** Reads a backup's header so the UI can describe it before asking for the key. */
    fun inspectBackup(source: Uri): RestorePreview? {
        val stream = context.contentResolver.openInputStream(source) ?: return null
        return stream.use {
            val info = VaultBackup.inspect(it)
            RestorePreview(info.vaultId, info.createdAtMs, info.itemCount, info.totalBytes)
        }
    }

    sealed class RestoreResult {
        data class Success(val itemCount: Int) : RestoreResult()
        data object WrongRecoveryKey : RestoreResult()
        data object Corrupt : RestoreResult()
        data class Failed(val message: String) : RestoreResult()
    }

    /**
     * Restores a backup over the current vault.
     *
     * The restored bytes are written to a sibling directory and only swapped in
     * after the container's integrity digest and the recovered master key have
     * both checked out. The previous vault directory is kept until the new PIN
     * has been written, so a failure at any point leaves the old vault intact.
     */
    @Synchronized
    fun restoreFrom(source: Uri, recoveryCode: String, newPin: String): RestoreResult {
        when (val validation = VaultPinPolicy.validate(newPin)) {
            is VaultPinPolicy.Result.Rejected -> return RestoreResult.Failed("pin:" + validation.reason.name)
            VaultPinPolicy.Result.Ok -> Unit
        }
        val secret = VaultRecoveryCode.parse(recoveryCode) ?: return RestoreResult.WrongRecoveryKey
        val docId = context.contentResolver.getType(source) ?: "backup"
        val stagingRoot = File(paths.root.parentFile, VAULT_DIR + ".restore")
        val previousRoot = File(paths.root.parentFile, VAULT_DIR + ".previous")
        VaultFiles.deleteRecursively(stagingRoot)
        VaultFiles.deleteRecursively(previousRoot)
        val stagingPaths = VaultPaths(stagingRoot)
        lock(VaultSession.LockReason.RESTORE)
        return try {
            val stream = context.contentResolver.openInputStream(source)
                ?: return RestoreResult.Failed("The backup could not be opened")
            val restored = try {
                stream.use { VaultBackup.restore(it, stagingPaths, secret) }
            } finally {
                VaultCrypto.zeroize(secret)
            }
            if (!paths.keyRing.exists() || !paths.root.exists()) {
                // Nothing to displace: move straight into place.
                if (!stagingRoot.renameTo(paths.root)) return RestoreResult.Failed("The vault could not be moved into place")
            } else {
                if (!paths.root.renameTo(previousRoot)) return RestoreResult.Failed("The existing vault could not be set aside")
                if (!stagingRoot.renameTo(paths.root)) {
                    previousRoot.renameTo(paths.root)
                    return RestoreResult.Failed("The restored vault could not be moved into place")
                }
            }
            // From here on the restored directory is in place. Any failure must put
            // the previous vault back rather than leave a half-established one:
            // a restored vault that never got a PIN would otherwise be openable
            // only with the recovery key while the user's original data sits in a
            // directory they cannot reach.
            fun rollback(): RestoreResult {
                runCatching { lock(VaultSession.LockReason.RESTORE) }
                if (previousRoot.isDirectory) {
                    VaultFiles.deleteRecursively(paths.root)
                    previousRoot.renameTo(paths.root)
                } else {
                    VaultFiles.deleteRecursively(paths.root)
                }
                return RestoreResult.Failed("The restore could not be completed; the previous Vault was kept")
            }

            // Re-open through the normal unlock path so the master key is proven
            // against the restored index, then establish this device's PIN.
            when (unlockWithRecovery(recoveryCode)) {
                UnlockResult.Success -> Unit
                else -> return rollback()
            }
            if (setPinAfterRecovery(newPin) != VaultPinPolicy.Result.Ok) return rollback()
            try {
                val current = synchronized(this) { unlocked } ?: return rollback()
                commit(
                    current,
                    current.index.withSettings(
                        current.index.settings.copy(
                            backup = current.index.settings.backup.copy(
                                lastFailure = null,
                                pendingSinceMs = System.currentTimeMillis(),
                                destinationLabel = current.index.settings.backup.destinationLabel ?: docId
                            )
                        )
                    ).withEvent(VaultEventType.RESTORED)
                )
            } catch (e: Exception) {
                return rollback()
            }
            VaultFiles.deleteRecursively(previousRoot)
            RestoreResult.Success(restored.itemCount)
        } catch (e: VaultCryptoException) {
            VaultFiles.deleteRecursively(stagingRoot)
            RestoreResult.Corrupt
        } catch (e: Exception) {
            VaultFiles.deleteRecursively(stagingRoot)
            RestoreResult.Failed(userMessage(e))
        }
    }

    /**
     * Streams a container through SHA-256 and compares the trailer.
     *
     * This is the "verify" half of "never report success until the backup is
     * complete and verified": the destination is read back from the provider,
     * not assumed to match what we wrote.
     */
    fun verifyContainer(uri: Uri): Boolean {
        val stream = context.contentResolver.openInputStream(uri) ?: return false
        return stream.use { input -> VaultBackup.verify(input) }
    }

    /** Whether the persisted permission for a backup destination is still valid. */
    fun hasDestinationPermission(uriString: String): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
    }

    private fun persistDestination(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    // ------------------------------------------------------------------
    // Security log
    // ------------------------------------------------------------------

    fun events(): List<com.lumaos.pro.launcher.vault.core.VaultEvent> = index()?.events?.reversed() ?: emptyList()

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun commit(current: Unlocked, index: VaultIndex) {
        val stored = current.metadata.commit(index)
        current.index = stored
    }

    private fun readKeyFile(): VaultKeyFile? {
        if (!paths.keyRing.isFile) return null
        return try {
            VaultKeyring.fromJson(paths.keyRing.readText())
        } catch (e: Exception) {
            LumaLog.e("Vault key ring is unreadable: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun writeKeyFile(keyFile: VaultKeyFile) {
        VaultFiles.writeBytesAtomically(paths.keyRing, VaultKeyring.toJson(keyFile).toByteArray(Charsets.UTF_8))
    }

    private var iterationsCache: Int? = null

    private fun calibratedIterations(): Int = iterationsCache ?: VaultCrypto.calibratePbkdf2Iterations().also {
        iterationsCache = it
    }

    private fun userMessage(e: Exception): String = when (e) {
        is java.io.IOException -> "Storage is unavailable"
        is VaultCryptoException -> "Vault data could not be verified"
        else -> "Something went wrong"
    }

}

/** Process-wide accessor; the manager is owned by [com.lumaos.pro.launcher.core.LumaCore]. */
private const val LAUNCH_MIGRATION_VERSION = "1:legacy-hidden-apps"

internal fun android.content.Context.vault(): VaultManager =
    (applicationContext as com.lumaos.pro.launcher.core.LumaApp).core.vault
