package com.lumaos.pro.launcher.vault.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The primitive layer of LumaOS Vault.
 *
 * Every algorithm used here is provided by the platform's JCA/JCE provider
 * (`javax.crypto`, `java.security`) that ships with Android and with the desktop
 * JDK. Vault deliberately contains **no hand-written cryptographic primitive**:
 * no S-boxes, no block functions, no custom MACs. What Vault does implement is
 * the *composition* of those vetted primitives - a key-wrapping convention
 * (AES-256-GCM over a randomly generated data key) and HKDF-SHA-256
 * (RFC 5869) for domain-separated key derivation, which is a documented
 * standard construction built on `HmacSHA256`.
 *
 * Everything in this file is plain JVM code: it imports nothing from `android.*`
 * so that the whole key hierarchy can be exercised by fast JVM unit tests. The
 * Android-only pieces (Keystore, biometrics, activities) live in
 * `com.lumaos.pro.launcher.vault.platform`.
 *
 * Algorithm inventory (all NIST/RFC standard, all platform-provided):
 *  - AES-256-GCM (AEAD, 96-bit IV, 128-bit tag) for objects, keys and metadata.
 *  - HKDF-SHA-256 (RFC 5869) for per-purpose key derivation.
 *  - PBKDF2-HMAC-SHA-256 (RFC 8018) to stretch the low-entropy vault PIN.
 *  - SHA-256 for integrity digests of backup containers.
 *  - `SecureRandom` for every key, salt and nonce.
 */
object VaultCrypto {

    /** 256-bit keys everywhere: the VMK, every DEK, and every wrapping key. */
    const val KEY_BYTES = 32

    /** AES-GCM with a 96-bit IV is the only configuration we ever select. */
    const val GCM_IV_BYTES = 12

    /** 128-bit authentication tag. */
    const val GCM_TAG_BYTES = 16

    /** Salt length for HKDF / PBKDF2. */
    const val SALT_BYTES = 32

    val EMPTY: ByteArray = ByteArray(0)

    const val TRANSFORM = "AES/GCM/NoPadding"
    const val MAC_TRANSFORM = "HmacSHA256"
    const val PBKDF2_TRANSFORM = "PBKDF2WithHmacSHA256"

    /** One shared CSPRNG; `SecureRandom` is thread-safe and expensive to seed. */
    private val random: SecureRandom by lazy { SecureRandom() }

    // ---------------------------------------------------------------------
    // Randomness
    // ---------------------------------------------------------------------

    /** Cryptographically secure random bytes from the platform CSPRNG. */
    fun randomBytes(count: Int): ByteArray {
        require(count > 0) { "count must be positive" }
        val out = ByteArray(count)
        random.nextBytes(out)
        return out
    }

    /**
     * A fresh 256-bit data/master key.
     *
     * Every vault and every stored object gets its own independent value from
     * this function. Nothing personal is ever fed into it: no phone number,
     * account, device id, IMEI, Android ID or PIN.
     */
    fun newKey(): ByteArray = randomBytes(KEY_BYTES)

    // ---------------------------------------------------------------------
    // AEAD
    // ---------------------------------------------------------------------

    /** Ciphertext plus the IV it was produced under. */
    class Sealed(val iv: ByteArray, val ciphertext: ByteArray) {
        fun ivHex(): String = VaultEncoding.hex(iv)
        fun ciphertextHex(): String = VaultEncoding.hex(ciphertext)
    }

    /**
     * Seal under AES-256-GCM, generating a fresh random IV.
     *
     * The caller must never supply an IV in normal operation: an IV is generated
     * per call, and the key is never reused for an unrelated purpose (see
     * [hkdf]), so nonce uniqueness holds by construction. [sealWithIv] exists
     * only for the streaming format, which derives a unique key per chunk and
     * therefore uses a deterministic per-chunk IV - the same construction
     * Google Tink uses for its streaming AEADs.
     */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray = EMPTY): Sealed {
        val iv = randomBytes(GCM_IV_BYTES)
        return Sealed(iv, sealWithIv(key, iv, plaintext, aad))
    }

    fun sealWithIv(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray = EMPTY): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 requires a 32-byte key" }
        require(iv.size == GCM_IV_BYTES) { "AES-GCM IV must be 96 bits" }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BYTES * 8, iv))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    /**
     * Open AES-256-GCM ciphertext.
     *
     * Throws [VaultCryptoException] when the tag does not verify - a wrong key,
     * a modified ciphertext, a modified AAD/IV and a truncated blob are all
     * indistinguishable failures to the caller, which is intentional: Vault
     * never acts on unauthenticated plaintext.
     */
    fun open(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray = EMPTY): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 requires a 32-byte key" }
        require(iv.size == GCM_IV_BYTES) { "AES-GCM IV must be 96 bits" }
        if (ciphertext.size < GCM_TAG_BYTES) throw VaultCryptoException("Ciphertext shorter than the authentication tag")
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BYTES * 8, iv))
            if (aad.isNotEmpty()) cipher.updateAAD(aad)
            cipher.doFinal(ciphertext)
        } catch (e: VaultCryptoException) {
            throw e
        } catch (e: Exception) {
            // AEADBadTagException on JVM/Android; keep the cause for the debug log
            // but never surface it as plaintext.
            throw VaultCryptoException("Authentication failed", e)
        }
    }

    // ---------------------------------------------------------------------
    // Key derivation
    // ---------------------------------------------------------------------

    /**
     * HKDF-SHA-256 (RFC 5869) in one call: extract then expand.
     *
     * `info` is the domain-separation string. Vault derives a different key for
     * every purpose (object streaming, metadata, key wrapping, backup) from the
     * same master key, so no key is ever used for two different jobs.
     */
    fun hkdf(ikm: ByteArray, salt: ByteArray?, info: ByteArray, outBytes: Int = KEY_BYTES): ByteArray {
        require(outBytes in 1..(255 * 32)) { "HKDF output length out of range" }
        val prk = Mac.getInstance(MAC_TRANSFORM).run {
            init(SecretKeySpec(if (salt == null || salt.isEmpty()) ByteArray(32) else salt, MAC_TRANSFORM))
            doFinal(ikm)
        }
        val out = ByteArray(outBytes)
        val mac = Mac.getInstance(MAC_TRANSFORM)
        mac.init(SecretKeySpec(prk, MAC_TRANSFORM))
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < outBytes) {
            mac.reset()
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val take = minOf(previous.size, outBytes - offset)
            previous.copyInto(out, offset, 0, take)
            offset += take
            counter++
        }
        zeroize(prk)
        return out
    }

    /** HKDF with a string domain-separation label (encoded UTF-8). */
    fun hkdf(ikm: ByteArray, salt: ByteArray?, info: String, outBytes: Int = KEY_BYTES): ByteArray =
        hkdf(ikm, salt, info.toByteArray(Charsets.UTF_8), outBytes)

    /**
     * Stretch a low-entropy secret (the vault PIN) with PBKDF2-HMAC-SHA-256.
     *
     * The PIN is the only credential a human can exhaustively guess, so it gets
     * a deliberately slow KDF. Iteration counts are calibrated per device (see
     * [calibratePbkdf2Iterations]) and stored in the vault header, so params can
     * be raised later without orphaning existing vaults.
     */
    fun pbkdf2Sha256(secret: CharArray, salt: ByteArray, iterations: Int, outBytes: Int = KEY_BYTES): ByteArray {
        require(iterations >= 1) { "iterations must be positive" }
        // RFC 8018 allows any non-empty salt. Vault always generates a 256-bit
        // random salt (see VaultKeyring.wrapWithPin); the primitive itself stays
        // general so it can be checked against the published test vectors.
        require(salt.isNotEmpty()) { "PBKDF2 salt must not be empty" }
        val spec = PBEKeySpec(secret, salt, iterations, outBytes * 8)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_TRANSFORM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** PBKDF2 over raw bytes (one byte per char, i.e. an ASCII/latin-1 password). */
    fun pbkdf2Sha256(secret: ByteArray, salt: ByteArray, iterations: Int, outBytes: Int = KEY_BYTES): ByteArray {
        val chars = CharArray(secret.size) { (secret[it].toInt() and 0xFF).toChar() }
        return try {
            pbkdf2Sha256(chars, salt, iterations, outBytes)
        } finally {
            chars.fill('\u0000')
        }
    }

    /**
     * Measures how many PBKDF2 iterations this device performs in [targetMillis].
     *
     * The result is clamped to a sane range and used only when a *new* vault is
     * created; existing vaults keep the iteration count recorded in their
     * header. This is the "benchmark on real devices" step: the default constant
     * is only a floor for hardware where the benchmark cannot run.
     */
    fun calibratePbkdf2Iterations(targetMillis: Long = 350L, minimum: Int = 120_000, maximum: Int = 800_000): Int {
        val probeSalt = randomBytes(SALT_BYTES)
        val probe = charArrayOf('1', '2', '3', '4', '5', '6', '7', '8')
        var iterations = 20_000
        pbkdf2Sha256(probe, probeSalt, iterations) // warm up the provider
        val start = System.nanoTime()
        pbkdf2Sha256(probe, probeSalt, iterations)
        val baselineMillis = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
        if (baselineMillis < targetMillis) {
            iterations = (iterations.toDouble() * targetMillis / baselineMillis.toLong()).toInt()
        }
        probe.fill('\u0000')
        return iterations.coerceIn(minimum, maximum)
    }

    // ---------------------------------------------------------------------
    // Hash helpers
    // ---------------------------------------------------------------------

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    /**
     * Constant-time comparison. Used for checksum/gate comparisons so that a
     * timing side channel cannot help an attacker.
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    /**
     * Best-effort zeroization of key material.
     *
     * The JVM gives no guarantee that it holds the only copy of an array - the
     * GC may have moved it or a cipher implementation may have copied it - so
     * this is defence in depth, not a hard guarantee. Vault still does it
     * everywhere it can: on lock, after a wrap/unwrap, and after use.
     */
    fun zeroize(vararg arrays: ByteArray?) {
        for (array in arrays) {
            array ?: continue
            array.fill(0)
        }
    }
}

/** Raised when authenticated decryption fails or key material is malformed. */
class VaultCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Hex/base64 helpers.
 *
 * `java.util.Base64` (not `android.util.Base64`) keeps this layer runnable on a
 * plain JVM, and it is available on every supported API level (26+).
 */
object VaultEncoding {

    private val HEX = "0123456789abcdef".toCharArray()

    fun hex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }

    fun fromHex(text: String): ByteArray {
        require(text.length % 2 == 0) { "hex length must be even" }
        val out = ByteArray(text.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(text[i * 2], 16)
            val lo = Character.digit(text[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "invalid hex" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    fun base64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    fun fromBase64(text: String): ByteArray = try {
        java.util.Base64.getDecoder().decode(text)
    } catch (e: IllegalArgumentException) {
        throw VaultCryptoException("Malformed base64", e)
    }

    /** Opaque 128-bit identifier for objects, folders and vaults. */
    fun newId(): String = hex(VaultCrypto.randomBytes(16))
}
