package com.lumaos.pro.launcher.vault.core

import com.lumaos.pro.launcher.backup.MiniJson

/**
 * The vault key hierarchy.
 *
 * ```
 *  Vault Master Key (VMK)          32 random bytes, never leaves the device
 *      |                             never derived from the PIN or from anything personal
 *      +-- pin wrapper       AES-256-GCM under PBKDF2(PIN, salt, iterations)
 *      +-- recovery wrapper  AES-256-GCM under HKDF(recovery secret, salt)
 *      +-- biometric wrapper AES-256-GCM under a device-bound Keystore key
 *      |
 *      +-- HKDF("lumaos/vault/metadata/v1")     -> metadata encryption key
 *      +-- HKDF("lumaos/vault/object/v1", objId) -> per-object DEK protection
 * ```
 *
 * Each stored object additionally gets its own independent random Data
 * Encryption Key. The VMK never encrypts bulk data directly: it wraps object
 * keys, so a single object can be deleted, rotated or re-encrypted without
 * touching the rest of the vault, and changing the PIN only rewrites one 32-byte
 * wrapper instead of re-encrypting gigabytes of media.
 *
 * All three wrappers protect *the same* VMK. That is what makes the three
 * authentication paths interchangeable: PIN, biometrics and recovery key all end
 * at the same master key, so content stays readable no matter which path is used
 * - and a lost PIN or a wiped Keystore never orphans the data while the recovery
 * key still exists.
 *
 * Serialised form ("keyring.json") contains only ciphertext, salts, IVs and
 * iteration counts. It is not secret, but it lives in the vault's private,
 * no-backup directory all the same.
 */
object VaultKeyring {

    /** Bumped only when the on-disk key ring shape changes incompatibly. */
    const val FORMAT = 1

    /** File name inside the vault directory. */
    const val FILE_NAME = "keyring.json"

    const val PIN_SCHEME = "pin-pbkdf2-sha256-aesgcm-v1"
    const val RECOVERY_SCHEME = "recovery-hkdf-sha256-aesgcm-v1"
    const val BIOMETRIC_SCHEME = "biometric-keystore-aesgcm-v1"

    /**
     * PBKDF2-HMAC-SHA-256 iterations used when a new vault is created on a device
     * where calibration could not run. New vaults normally use
     * [VaultCrypto.calibratePbkdf2Iterations], whose floor is this value.
     */
    const val DEFAULT_PBKDF2_ITERATIONS = 210_000

    /** Refuse to open a key ring that claims fewer iterations than this. */
    const val MIN_PBKDF2_ITERATIONS = 100_000

    /** HKDF domain separation for the recovery wrapper. */
    private const val RECOVERY_INFO = "lumaos/vault/recovery-wrap/v1"

    /** A fresh, independent 256-bit vault master key. */
    fun newMasterKey(): ByteArray = VaultCrypto.newKey()

    // ------------------------------------------------------------------
    // PIN wrapper
    // ------------------------------------------------------------------

    /**
     * Wrap [vmk] under a PIN-derived key.
     *
     * The PIN itself is never stored, and neither is its derivation: only the
     * random salt, the iteration count and the AES-GCM ciphertext of the master
     * key are written to disk.
     */
    fun wrapWithPin(
        vmk: ByteArray,
        pin: CharArray,
        vaultId: String,
        salt: ByteArray = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES),
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
        nowMs: Long = System.currentTimeMillis()
    ): VaultWrappedKey {
        require(vmk.size == VaultCrypto.KEY_BYTES) { "master key must be 256 bits" }
        require(iterations >= MIN_PBKDF2_ITERATIONS) { "PIN KDF iterations below the floor" }
        val kek = VaultCrypto.pbkdf2Sha256(pin, salt, iterations)
        try {
            val sealed = VaultCrypto.seal(kek, vmk, aad(PIN_SCHEME, vaultId))
            return VaultWrappedKey(
                scheme = PIN_SCHEME,
                salt = salt,
                iterations = iterations,
                iv = sealed.iv,
                wrapped = sealed.ciphertext,
                keystoreAlias = null,
                createdAtMs = nowMs
            )
        } finally {
            VaultCrypto.zeroize(kek)
        }
    }

    /**
     * Unwrap the master key with a PIN.
     *
     * A wrong PIN fails AES-GCM authentication, which produces
     * [VaultCryptoException]; there is no way to distinguish "wrong PIN" from
     * "corrupted wrapper" cryptographically, and callers must treat both as a
     * failed attempt.
     */
    fun unwrapWithPin(wrapper: VaultWrappedKey, pin: CharArray, vaultId: String): ByteArray {
        require(wrapper.scheme == PIN_SCHEME) { "not a PIN wrapper" }
        require(wrapper.iterations >= MIN_PBKDF2_ITERATIONS) { "PIN KDF iterations below the floor" }
        val kek = VaultCrypto.pbkdf2Sha256(pin, wrapper.salt, wrapper.iterations)
        return try {
            VaultCrypto.open(kek, wrapper.iv, wrapper.wrapped, aad(PIN_SCHEME, vaultId))
        } finally {
            VaultCrypto.zeroize(kek)
        }
    }

    // ------------------------------------------------------------------
    // Recovery wrapper
    // ------------------------------------------------------------------

    /**
     * Wrap [vmk] under the recovery key.
     *
     * The recovery secret is 256 bits of CSPRNG output, so it needs no slow KDF -
     * there is nothing to brute-force. HKDF is used purely for domain separation,
     * with a fresh random salt, and the result is an AES-GCM wrapper independent
     * of the PIN and of the device's Keystore. That independence is the whole
     * point: it is the only wrapper that still works after a factory reset, a new
     * phone, or a wiped biometric enrolment.
     */
    fun wrapWithRecovery(
        vmk: ByteArray,
        recoverySecret: ByteArray,
        vaultId: String,
        salt: ByteArray = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES),
        nowMs: Long = System.currentTimeMillis()
    ): VaultWrappedKey {
        require(vmk.size == VaultCrypto.KEY_BYTES) { "master key must be 256 bits" }
        require(recoverySecret.size == VaultRecoveryCode.SECRET_BYTES) { "recovery secret must be 256 bits" }
        val kek = VaultCrypto.hkdf(recoverySecret, salt, RECOVERY_INFO)
        try {
            val sealed = VaultCrypto.seal(kek, vmk, aad(RECOVERY_SCHEME, vaultId))
            return VaultWrappedKey(
                scheme = RECOVERY_SCHEME,
                salt = salt,
                iterations = 0,
                iv = sealed.iv,
                wrapped = sealed.ciphertext,
                keystoreAlias = null,
                createdAtMs = nowMs
            )
        } finally {
            VaultCrypto.zeroize(kek)
        }
    }

    fun unwrapWithRecovery(wrapper: VaultWrappedKey, recoverySecret: ByteArray, vaultId: String): ByteArray {
        require(wrapper.scheme == RECOVERY_SCHEME) { "not a recovery wrapper" }
        require(recoverySecret.size == VaultRecoveryCode.SECRET_BYTES) { "recovery secret must be 256 bits" }
        val kek = VaultCrypto.hkdf(recoverySecret, wrapper.salt, RECOVERY_INFO)
        return try {
            VaultCrypto.open(kek, wrapper.iv, wrapper.wrapped, aad(RECOVERY_SCHEME, vaultId))
        } finally {
            VaultCrypto.zeroize(kek)
        }
    }

    // ------------------------------------------------------------------
    // Derived keys
    // ------------------------------------------------------------------

    /**
     * Key used to encrypt the vault's metadata index.
     *
     * Derived from the VMK with its own domain-separation label: the VMK is never
     * used directly as an encryption key for anything.
     */
    fun metadataKey(vmk: ByteArray): ByteArray = VaultCrypto.hkdf(vmk, null, "lumaos/vault/metadata/v1")

    /**
     * Key that wraps a single object's data key.
     *
     * Binding the object id into the derivation means an object's wrapped DEK
     * cannot be swapped onto a different object, even by someone who can rewrite
     * the metadata database.
     */
    fun objectKeyWrapperKey(vmk: ByteArray, objectId: String): ByteArray =
        VaultCrypto.hkdf(vmk, null, "lumaos/vault/object/v1|$objectId")

    // ------------------------------------------------------------------
    // Serialisation
    // ------------------------------------------------------------------

    fun toJson(keyFile: VaultKeyFile): String = MiniJson.write(
        linkedMapOf(
            "format" to FORMAT,
            "vaultId" to keyFile.vaultId,
            "createdAtMs" to keyFile.createdAtMs,
            "pin" to keyFile.pin?.toMap(),
            "recovery" to keyFile.recovery?.toMap(),
            "biometric" to keyFile.biometric?.toMap()
        )
    )

    fun fromJson(text: String): VaultKeyFile {
        val root = MiniJson.parse(text) as? Map<*, *> ?: throw VaultCryptoException("Key ring is not an object")
        val format = (root["format"] as? Number)?.toInt() ?: throw VaultCryptoException("Key ring has no format")
        if (format != FORMAT) throw VaultCryptoException("Unsupported key ring format $format")
        val vaultId = root["vaultId"] as? String ?: throw VaultCryptoException("Key ring has no vault id")
        return VaultKeyFile(
            vaultId = vaultId,
            createdAtMs = (root["createdAtMs"] as? Number)?.toLong() ?: 0L,
            pin = VaultWrappedKey.fromMap(root["pin"]),
            recovery = VaultWrappedKey.fromMap(root["recovery"]),
            biometric = VaultWrappedKey.fromMap(root["biometric"])
        )
    }

    /** Associated data binding a wrapper to its purpose and to this vault. */
    internal fun aad(scheme: String, vaultId: String): ByteArray = "$scheme|$vaultId".toByteArray(Charsets.UTF_8)
}

/**
 * One wrapped copy of the vault master key.
 *
 * @param scheme identifies the wrapping construction, so a future scheme can be
 *        introduced without breaking existing vaults (old wrappers stay readable).
 * @param salt KDF salt; empty for the Keystore-backed biometric wrapper.
 * @param iterations PBKDF2 iteration count; 0 for non-PBKDF2 schemes.
 * @param keystoreAlias alias of the Android Keystore key for the biometric
 *        wrapper; null for PIN and recovery wrappers.
 */
data class VaultWrappedKey(
    val scheme: String,
    val salt: ByteArray,
    val iterations: Int,
    val iv: ByteArray,
    val wrapped: ByteArray,
    val keystoreAlias: String?,
    val createdAtMs: Long
) {

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "scheme" to scheme,
        "salt" to VaultEncoding.base64(salt),
        "iterations" to iterations,
        "iv" to VaultEncoding.base64(iv),
        "wrapped" to VaultEncoding.base64(wrapped),
        "keystoreAlias" to keystoreAlias,
        "createdAtMs" to createdAtMs
    )

    override fun equals(other: Any?): Boolean {
        if (other !is VaultWrappedKey) return false
        return scheme == other.scheme &&
            salt.contentEquals(other.salt) &&
            iterations == other.iterations &&
            iv.contentEquals(other.iv) &&
            wrapped.contentEquals(other.wrapped) &&
            keystoreAlias == other.keystoreAlias &&
            createdAtMs == other.createdAtMs
    }

    override fun hashCode(): Int {
        var result = scheme.hashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + iterations
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + wrapped.contentHashCode()
        result = 31 * result + (keystoreAlias?.hashCode() ?: 0)
        result = 31 * result + createdAtMs.hashCode()
        return result
    }

    companion object {
        fun fromMap(value: Any?): VaultWrappedKey? {
            val map = value as? Map<*, *> ?: return null
            val scheme = map["scheme"] as? String ?: return null
            val salt = (map["salt"] as? String)?.let(VaultEncoding::fromBase64) ?: VaultCrypto.EMPTY
            val iterations = (map["iterations"] as? Number)?.toInt() ?: 0
            val iv = (map["iv"] as? String)?.let(VaultEncoding::fromBase64) ?: return null
            val wrapped = (map["wrapped"] as? String)?.let(VaultEncoding::fromBase64) ?: return null
            return VaultWrappedKey(
                scheme = scheme,
                salt = salt,
                iterations = iterations,
                iv = iv,
                wrapped = wrapped,
                keystoreAlias = map["keystoreAlias"] as? String,
                createdAtMs = (map["createdAtMs"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

/**
 * The persisted key ring: one vault identity plus its wrappers.
 *
 * A vault is created with a PIN wrapper and a recovery wrapper; a biometric
 * wrapper is added later, once the vault is unlocked and the user opts in (a
 * Keystore key that requires authentication can only be created while the device
 * is unlocked and the user is present).
 */
data class VaultKeyFile(
    val vaultId: String,
    val createdAtMs: Long,
    val pin: VaultWrappedKey?,
    val recovery: VaultWrappedKey?,
    val biometric: VaultWrappedKey?
)

/**
 * PIN quality rules.
 *
 * Vault never silently accepts a weak PIN: it explains what is wrong and offers
 * a suggestion. The hard floor is six digits (an eight-digit PIN is recommended
 * in the UI), and trivially guessable patterns are refused even at eight digits.
 */
object VaultPinPolicy {

    const val MIN_LENGTH = 6
    const val RECOMMENDED_LENGTH = 8
    const val MAX_LENGTH = 12

    sealed class Result {
        object Ok : Result()
        data class Rejected(val reason: Reason) : Result()
    }

    enum class Reason { TOO_SHORT, TOO_LONG, NOT_DIGITS, REPEATED_DIGIT, SEQUENTIAL, ONLY_FEW_DIGITS }

    fun validate(pin: String): Result {
        if (pin.length < MIN_LENGTH) return Result.Rejected(Reason.TOO_SHORT)
        if (pin.length > MAX_LENGTH) return Result.Rejected(Reason.TOO_LONG)
        if (!pin.all { it.isDigit() }) return Result.Rejected(Reason.NOT_DIGITS)
        if (pin.all { it == pin[0] }) return Result.Rejected(Reason.REPEATED_DIGIT)
        if (isSequential(pin)) return Result.Rejected(Reason.SEQUENTIAL)
        if (pin.toSet().size < 3) return Result.Rejected(Reason.ONLY_FEW_DIGITS)
        return Result.Ok
    }

    /**
     * True for runs like 123456, 987654, 112233 and 246810 - patterns that make a
     * PIN guessable from a glance at the keypad.
     */
    private fun isSequential(pin: String): Boolean {
        val digits = pin.map { it - '0' }
        val ascending = digits.zipWithNext().all { (a, b) -> b == (a + 1) % 10 }
        val descending = digits.zipWithNext().all { (a, b) -> b == (a + 9) % 10 }
        if (ascending || descending) return true
        val step = digits[1] - digits[0]
        if (step != 0 && digits.zipWithNext().all { (a, b) -> b == (a + step + 10) % 10 }) return true
        // Repeated pairs such as 112233 or 123123.
        val pairExtend = digits.size % 2 == 0 &&
            digits.chunked(2).all { it[0] == it[1] }
        if (pairExtend) return true
        val third = digits.size % 3 == 0 &&
            digits.chunked(3).distinct().size == 1
        return third
    }
}
