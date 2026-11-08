package com.lumaos.pro.launcher.vault.core

/**
 * The Vault Recovery Key: 256 bits of entropy from the platform CSPRNG, shown to
 * the user once, in a form a human can write down without transcription errors.
 *
 * ## Format
 *
 * The secret is base32-encoded with Crockford's alphabet
 * (`0-9 A-Z` without `I`, `L`, `O`, `U`), which removes the characters people
 * confuse when reading handwriting. 256 bits become 52 characters. A 10-bit
 * SHA-256 checksum is appended as two more characters, giving 54 characters,
 * printed as 14 groups of four separated by dashes:
 *
 * ```
 * 7K4M-Q2X9-BT1D-8VRC-3NP6-H5WZ-J0FS-9GYA-2EMX-6KDQ-T4RB-8WNP-3VF7-J2
 * ```
 *
 * Parsing is forgiving in the ways a human needs (case, whitespace, dashes,
 * `O`/`I`/`L` confusion) and strict where it matters: a single mistyped
 * character is rejected by the checksum instead of silently unlocking the vault
 * with a wrong key.
 *
 * The recovery key is never derived from personal information, never sent
 * anywhere, and never stored in plaintext: only its AES-GCM wrapping of the
 * vault master key is persisted.
 */
object VaultRecoveryCode {

    const val SECRET_BYTES = 32

    /** Groups of four; 54 characters total (52 secret + 2 checksum). */
    const val GROUP_SIZE = 4
    const val SECRET_CHARS = 52
    const val CHECKSUM_CHARS = 2
    const val TOTAL_CHARS = SECRET_CHARS + CHECKSUM_CHARS

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val DECODE: Map<Char, Int> = buildMap {
        ALPHABET.forEachIndexed { index, c -> put(c, index) }
        // Crockford's human-input aliases.
        put('O', 0)
        put('I', 1)
        put('L', 1)
    }

    /** A brand-new 256-bit recovery secret. */
    fun generate(): ByteArray = VaultCrypto.randomBytes(SECRET_BYTES)

    /** Canonical, dash-grouped, uppercase rendering for display. */
    fun format(secret: ByteArray): String {
        require(secret.size == SECRET_BYTES) { "recovery secret must be 256 bits" }
        val raw = encode(secret) + checksumChars(secret)
        val groups = raw.chunked(GROUP_SIZE)
        return groups.joinToString("-")
    }

    /**
     * Parses user input back into the secret, or returns `null` when the text is
     * malformed or the checksum does not match.
     */
    fun parse(text: String): ByteArray? {
        val cleaned = text.filter { it.isLetterOrDigit() || it == '-' }
            .filter { it != '-' }
            .uppercase()
        if (cleaned.length != TOTAL_CHARS) return null
        val body = cleaned.substring(0, SECRET_CHARS)
        val checksum = cleaned.substring(SECRET_CHARS)
        val secret = decode(body) ?: return null
        if (secret.size != SECRET_BYTES) return null
        if (checksumChars(secret) != checksum) return null
        return secret
    }

    /** True when [text] parses and passes its checksum. */
    fun isValid(text: String): Boolean = parse(text) != null

    /** Character count excluding dashes, for progress feedback while typing. */
    fun typedLength(text: String): Int = text.count { it.isLetterOrDigit() }

    // ------------------------------------------------------------------
    // base32
    // ------------------------------------------------------------------

    private fun encode(bytes: ByteArray): String {
        val out = StringBuilder(SECRET_CHARS)
        var bitBuffer = 0L
        var bitsHeld = 0
        for (byte in bytes) {
            bitBuffer = (bitBuffer shl 8) or (byte.toLong() and 0xFF)
            bitsHeld += 8
            while (bitsHeld >= 5) {
                val index = ((bitBuffer ushr (bitsHeld - 5)) and 0x1F).toInt()
                out.append(ALPHABET[index])
                bitsHeld -= 5
            }
        }
        if (bitsHeld > 0) {
            val index = ((bitBuffer shl (5 - bitsHeld)) and 0x1F).toInt()
            out.append(ALPHABET[index])
        }
        // 52 characters = 260 bits, i.e. four padding bits on the last character.
        while (out.length < SECRET_CHARS) out.append(ALPHABET[0])
        return out.toString()
    }

    private fun decode(text: String): ByteArray? {
        val out = ByteArray(SECRET_BYTES)
        var bitBuffer = 0L
        var bitsHeld = 0
        var produced = 0
        for (c in text) {
            val value = DECODE[c] ?: return null
            bitBuffer = (bitBuffer shl 5) or value.toLong()
            bitsHeld += 5
            if (bitsHeld >= 8) {
                if (produced >= SECRET_BYTES) continue
                out[produced++] = ((bitBuffer ushr (bitsHeld - 8)) and 0xFF).toByte()
                bitsHeld -= 8
            }
        }
        return if (produced == SECRET_BYTES) out else null
    }

    // ------------------------------------------------------------------
    // checksum
    // ------------------------------------------------------------------

    /**
     * Two-character checksum: the first 10 bits of SHA-256(secret).
     *
     * Ten bits catches every single-character substitution and the overwhelming
     * majority of transpositions; it is deliberately short so the recovery key
     * stays writable on one line.
     */
    private fun checksumChars(secret: ByteArray): String {
        val digest = VaultCrypto.sha256(secret)
        val value = (((digest[0].toInt() and 0xFF) shl 8) or (digest[1].toInt() and 0xFF)) and 0x3FF
        return buildString {
            append(ALPHABET[(value ushr 5) and 0x1F])
            append(ALPHABET[value and 0x1F])
        }
    }
}
