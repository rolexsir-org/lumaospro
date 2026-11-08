package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultCrypto
import com.lumaos.pro.launcher.vault.core.VaultCryptoException
import com.lumaos.pro.launcher.vault.core.VaultEncoding
import com.lumaos.pro.launcher.vault.core.VaultKeyring
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Primitive-level tests: HKDF and PBKDF2 are checked against the published test
 * vectors of their RFCs, and AES-GCM against the properties Vault relies on
 * (integrity, wrong-key rejection, IV uniqueness).
 */
class VaultCryptoTests {

    @Test
    fun hkdfMatchesRfc5869Case1() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expected = hex(
            "3cb25f25faacd57a90434f64d0362f2a" +
                "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b887185865"
        )
        assertArrayEquals(expected, VaultCrypto.hkdf(ikm, salt, info, 42))
    }

    @Test
    fun hkdfMatchesRfc5869Case3WithEmptySaltAndInfo() {
        val ikm = ByteArray(22) { 0x0b }
        val expected = hex(
            "8da4e775a563c18f715f802a063c5a31" +
                "b8a11f5c5ee1879ec3454e5f3c738d2d" +
                "9d201395faa4b61a96c8"
        )
        assertArrayEquals(expected, VaultCrypto.hkdf(ikm, ByteArray(0), ByteArray(0), 42))
    }

    @Test
    fun hkdfSeparatesPurposes() {
        val ikm = VaultCrypto.newKey()
        val salt = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES)
        val metadata = VaultCrypto.hkdf(ikm, salt, "lumaos/vault/metadata/v1")
        val objectKey = VaultCrypto.hkdf(ikm, salt, "lumaos/vault/object/v1")
        assertFalse(metadata.contentEquals(objectKey))
        assertEquals(32, metadata.size)
    }

    @Test
    fun pbkdf2MatchesPublishedVector() {
        val derived = VaultCrypto.pbkdf2Sha256(
            "password".toCharArray(),
            "salt".toByteArray(Charsets.US_ASCII),
            1,
            32
        )
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b", VaultEncoding.hex(derived))
    }

    @Test
    fun aesGcmRoundTripWithAssociatedData() {
        val key = VaultCrypto.newKey()
        val aad = "context".toByteArray()
        val plaintext = "private".toByteArray()
        val sealed = VaultCrypto.seal(key, plaintext, aad)
        assertEquals(12, sealed.iv.size)
        assertEquals(plaintext.size + 16, sealed.ciphertext.size)
        assertArrayEquals(plaintext, VaultCrypto.open(key, sealed.iv, sealed.ciphertext, aad))
    }

    @Test
    fun aesGcmRejectsTamperedCiphertext() {
        val key = VaultCrypto.newKey()
        val sealed = VaultCrypto.seal(key, "private".toByteArray())
        val tampered = sealed.ciphertext.copyOf()
        tampered[0] = (tampered[0].toInt() xor 0x01).toByte()
        assertThrows(VaultCryptoException::class.java) {
            VaultCrypto.open(key, sealed.iv, tampered)
        }
    }

    @Test
    fun aesGcmRejectsWrongKeyAndWrongAad() {
        val key = VaultCrypto.newKey()
        val sealed = VaultCrypto.seal(key, "private".toByteArray(), "a".toByteArray())
        assertThrows(VaultCryptoException::class.java) {
            VaultCrypto.open(VaultCrypto.newKey(), sealed.iv, sealed.ciphertext, "a".toByteArray())
        }
        assertThrows(VaultCryptoException::class.java) {
            VaultCrypto.open(key, sealed.iv, sealed.ciphertext, "b".toByteArray())
        }
    }

    @Test
    fun aesGcmRejectsCiphertextShorterThanTag() {
        val key = VaultCrypto.newKey()
        assertThrows(VaultCryptoException::class.java) {
            VaultCrypto.open(key, ByteArray(12), ByteArray(8))
        }
    }

    @Test
    fun everyIvmIsUniqueAcrossManySeals() {
        val key = VaultCrypto.newKey()
        val seen = HashSet<String>()
        repeat(500) {
            val sealed = VaultCrypto.seal(key, "x".toByteArray())
            assertTrue("IV reused", seen.add(sealed.ivHex()))
        }
    }

    @Test
    fun everyGeneratedKeyIsIndependent() {
        val seen = HashSet<String>()
        repeat(500) {
            assertTrue(seen.add(VaultEncoding.hex(VaultCrypto.newKey())))
        }
        assertEquals(32, VaultCrypto.newKey().size)
    }

    @Test
    fun vaultMasterKeysDifferPerVault() {
        // Two vaults created by the same app on the same device must not share a key.
        val first = VaultKeyring.newMasterKey()
        val second = VaultKeyring.newMasterKey()
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun zeroizeClearsKeyMaterial() {
        val key = VaultCrypto.newKey()
        VaultCrypto.zeroize(key)
        assertTrue(key.all { it == 0.toByte() })
    }

    @Test
    fun constantTimeEqualsBehavesLikeEquality() {
        assertTrue(VaultCrypto.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertFalse(VaultCrypto.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(VaultCrypto.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2)))
    }

    @Test
    fun hexAndBase64RoundTrip() {
        val bytes = VaultCrypto.randomBytes(48)
        assertArrayEquals(bytes, VaultEncoding.fromHex(VaultEncoding.hex(bytes)))
        assertArrayEquals(bytes, VaultEncoding.fromBase64(VaultEncoding.base64(bytes)))
        assertThrows(VaultCryptoException::class.java) { VaultEncoding.fromBase64("not base64!!") }
    }

    @Test
    fun pbkdf2CalibrationStaysInDocumentedRange() {
        val iterations = VaultCrypto.calibratePbkdf2Iterations(targetMillis = 20, minimum = 100_000, maximum = 150_000)
        assertTrue("calibration must respect its floor", iterations in 100_000..150_000)
    }

    private fun hex(value: String) = VaultEncoding.fromHex(value)
}
