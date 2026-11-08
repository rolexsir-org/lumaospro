package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultCrypto
import com.lumaos.pro.launcher.vault.core.VaultCryptoException
import com.lumaos.pro.launcher.vault.core.VaultEncoding
import com.lumaos.pro.launcher.vault.core.VaultKeyFile
import com.lumaos.pro.launcher.vault.core.VaultKeyring
import com.lumaos.pro.launcher.vault.core.VaultPinPolicy
import com.lumaos.pro.launcher.vault.core.VaultRecoveryCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Key-hierarchy tests: the three wrappers must all open the same master key, must
 * not open each other's vaults, and a credential change must never require
 * re-encrypting data.
 */
class VaultKeyringTests {

    private val vaultId = VaultEncoding.newId()
    private val pin = "48193027"

    @Test
    fun pinWrapperOpensTheMasterKey() {
        val vmk = VaultKeyring.newMasterKey()
        val wrapper = VaultKeyring.wrapWithPin(vmk, pin.toCharArray(), vaultId, iterations = 120_000)
        assertArrayEquals(vmk, VaultKeyring.unwrapWithPin(wrapper, pin.toCharArray(), vaultId))
        assertEquals(VaultKeyring.PIN_SCHEME, wrapper.scheme)
        assertEquals(120_000, wrapper.iterations)
        assertEquals(32, wrapper.salt.size)
    }

    @Test
    fun wrongPinIsRejected() {
        val vmk = VaultKeyring.newMasterKey()
        val wrapper = VaultKeyring.wrapWithPin(vmk, pin.toCharArray(), vaultId, iterations = 120_000)
        assertThrows(VaultCryptoException::class.java) {
            VaultKeyring.unwrapWithPin(wrapper, "48193028".toCharArray(), vaultId)
        }
    }

    @Test
    fun wrapperIsBoundToItsVault() {
        val vmk = VaultKeyring.newMasterKey()
        val wrapper = VaultKeyring.wrapWithPin(vmk, pin.toCharArray(), vaultId, iterations = 120_000)
        // Same PIN, different vault id: the associated data no longer matches.
        assertThrows(VaultCryptoException::class.java) {
            VaultKeyring.unwrapWithPin(wrapper, pin.toCharArray(), VaultEncoding.newId())
        }
    }

    @Test
    fun pinChangeRewrapsWithoutChangingTheMasterKey() {
        val vmk = VaultKeyring.newMasterKey()
        val original = VaultKeyring.wrapWithPin(vmk, pin.toCharArray(), vaultId, iterations = 120_000)
        val replacement = VaultKeyring.wrapWithPin(vmk, "90218763".toCharArray(), vaultId, iterations = 120_000)

        assertFalse("a rewrap must use a fresh salt", original.salt.contentEquals(replacement.salt))
        assertFalse("ciphertexts must differ", original.wrapped.contentEquals(replacement.wrapped))
        assertArrayEquals(vmk, VaultKeyring.unwrapWithPin(replacement, "90218763".toCharArray(), vaultId))
        // The old wrapper is gone from the key ring in practice, but the point of
        // the test is that the master key - and therefore every stored object -
        // is unchanged by a PIN change.
        assertArrayEquals(vmk, VaultKeyring.unwrapWithPin(original, pin.toCharArray(), vaultId))
    }

    @Test
    fun recoveryWrapperIsIndependentOfThePin() {
        val vmk = VaultKeyring.newMasterKey()
        val secret = VaultRecoveryCode.generate()
        val recovery = VaultKeyring.wrapWithRecovery(vmk, secret, vaultId)
        val pinWrapper = VaultKeyring.wrapWithPin(vmk, pin.toCharArray(), vaultId, iterations = 120_000)

        assertArrayEquals(vmk, VaultKeyring.unwrapWithRecovery(recovery, secret, vaultId))
        assertFalse(
            "recovery and PIN wrappers must not share key material",
            recovery.wrapped.contentEquals(pinWrapper.wrapped)
        )
    }

    @Test
    fun recoveryWrapperRejectsWrongSecret() {
        val secret = VaultRecoveryCode.generate()
        val wrapper = VaultKeyring.wrapWithRecovery(VaultKeyring.newMasterKey(), secret, vaultId)
        assertThrows(VaultCryptoException::class.java) {
            VaultKeyring.unwrapWithRecovery(wrapper, VaultRecoveryCode.generate(), vaultId)
        }
    }

    @Test
    fun rotatedRecoveryKeyReplacesTheOldOne() {
        val vmk = VaultKeyring.newMasterKey()
        val oldSecret = VaultRecoveryCode.generate()
        val oldWrapper = VaultKeyring.wrapWithRecovery(vmk, oldSecret, vaultId)
        val newSecret = VaultRecoveryCode.generate()
        val newWrapper = VaultKeyring.wrapWithRecovery(vmk, newSecret, vaultId)

        assertArrayEquals(vmk, VaultKeyring.unwrapWithRecovery(newWrapper, newSecret, vaultId))
        assertThrows(VaultCryptoException::class.java) {
            VaultKeyring.unwrapWithRecovery(newWrapper, oldSecret, vaultId)
        }
    }

    @Test
    fun derivedKeysAreDistinctPerObject() {
        val vmk = VaultKeyring.newMasterKey()
        val first = VaultKeyring.objectKeyWrapperKey(vmk, "aaaa")
        val second = VaultKeyring.objectKeyWrapperKey(vmk, "bbbb")
        assertFalse(first.contentEquals(second))
        assertFalse(VaultKeyring.metadataKey(vmk).contentEquals(first))
    }

    @Test
    fun keyRingSurvivesJsonRoundTrip() {
        val vmk = VaultKeyring.newMasterKey()
        val keyFile = VaultKeyFile(
            vaultId = vaultId,
            createdAtMs = 1_700_000_000_000L,
            pin = VaultKeyring.wrapWithPin(vmk, pin.toCharArray(), vaultId, iterations = 120_000),
            recovery = VaultKeyring.wrapWithRecovery(vmk, VaultRecoveryCode.generate(), vaultId),
            biometric = null
        )
        val restored = VaultKeyring.fromJson(VaultKeyring.toJson(keyFile))
        assertEquals(vaultId, restored.vaultId)
        assertEquals(1_700_000_000_000L, restored.createdAtMs)
        assertEquals(keyFile.pin, restored.pin)
        assertEquals(keyFile.recovery, restored.recovery)
        assertNull(restored.biometric)
        assertArrayEquals(vmk, VaultKeyring.unwrapWithPin(restored.pin!!, pin.toCharArray(), vaultId))
    }

    @Test
    fun keyRingRejectsUnknownFormatAndMissingVaultId() {
        assertThrows(VaultCryptoException::class.java) {
            VaultKeyring.fromJson("""{"format": 99, "vaultId": "abc"}""")
        }
        assertThrows(VaultCryptoException::class.java) {
            VaultKeyring.fromJson("""{"format": 1}""")
        }
    }

    @Test
    fun pinPolicyAcceptsStrongPins() {
        for (value in listOf("48193027", "137294", "90218763541")) {
            assertTrue(value, VaultPinPolicy.validate(value) is VaultPinPolicy.Result.Ok)
        }
    }

    @Test
    fun pinPolicyRejectsWeakPins() {
        fun reason(value: String): VaultPinPolicy.Reason =
            (VaultPinPolicy.validate(value) as VaultPinPolicy.Result.Rejected).reason

        assertEquals(VaultPinPolicy.Reason.TOO_SHORT, reason("1234"))
        assertEquals(VaultPinPolicy.Reason.TOO_LONG, reason("1234567890123"))
        assertEquals(VaultPinPolicy.Reason.NOT_DIGITS, reason("12ab56"))
        assertEquals(VaultPinPolicy.Reason.REPEATED_DIGIT, reason("11111111"))
        assertEquals(VaultPinPolicy.Reason.SEQUENTIAL, reason("123456"))
        assertEquals(VaultPinPolicy.Reason.SEQUENTIAL, reason("987654"))
        assertEquals(VaultPinPolicy.Reason.SEQUENTIAL, reason("112233"))
        assertEquals(VaultPinPolicy.Reason.ONLY_FEW_DIGITS, reason("12121212"))
    }

    // ------------------------------------------------------------------
    // PIN KDF parameters and migration
    // ------------------------------------------------------------------

    @Test
    fun pinWrapperRecordsItsIterationCountAndScheme() {
        val vmk = VaultKeyring.newMasterKey()
        val wrapper = VaultKeyring.wrapWithPin(vmk, "84920517".toCharArray(), "vault", iterations = 150_000)
        assertEquals(VaultKeyring.PIN_SCHEME, wrapper.scheme)
        assertEquals(150_000, wrapper.iterations)
        assertEquals(VaultCrypto.SALT_BYTES, wrapper.salt.size)
        // Only the salt, the iteration count and the ciphertext are persisted.
        val encoded = VaultEncoding.base64(wrapper.wrapped) + VaultEncoding.base64(wrapper.salt)
        assertFalse(encoded.contains("84920517"))
    }

    /** Two vaults created with the same PIN must not share a salt or a wrapper. */
    @Test
    fun everyPinWrapperGetsAFreshSalt() {
        val vmk = VaultKeyring.newMasterKey()
        val pin = "84920517".toCharArray()
        val first = VaultKeyring.wrapWithPin(vmk, pin, "vault-a", iterations = 120_000)
        val second = VaultKeyring.wrapWithPin(vmk, pin, "vault-a", iterations = 120_000)
        assertFalse("salts must differ", first.salt.contentEquals(second.salt))
        assertFalse("wrappers must differ", first.wrapped.contentEquals(second.wrapped))
        // Both still open with the PIN: the salt travels with the wrapper.
        assertArrayEquals(vmk, VaultKeyring.unwrapWithPin(second, pin, "vault-a"))
    }

    /**
     * An existing vault keeps opening under the parameters it was created with.
     *
     * This is the migration property: the iteration count is *stored per wrapper*,
     * so raising [VaultKeyring.DEFAULT_PBKDF2_ITERATIONS] for new vaults cannot
     * lock anyone out of an old one, and no existing data is re-derived.
     */
    @Test
    fun anOldWrapperStaysReadableAfterTheDefaultsChange() {
        val vmk = VaultKeyring.newMasterKey()
        val pin = "84920517".toCharArray()
        val old = VaultKeyring.wrapWithPin(vmk, pin, "vault", iterations = VaultKeyring.MIN_PBKDF2_ITERATIONS)
        // Rewrap the same master key with the current default, as a PIN change would.
        val migrated = VaultKeyring.wrapWithPin(vmk, pin, "vault", iterations = VaultKeyring.DEFAULT_PBKDF2_ITERATIONS)
        assertArrayEquals(vmk, VaultKeyring.unwrapWithPin(old, pin, "vault"))
        assertArrayEquals(vmk, VaultKeyring.unwrapWithPin(migrated, pin, "vault"))
        assertTrue("the old wrapper kept its own parameters", old.iterations < migrated.iterations)
        // And the old wrapper still refuses a wrong PIN after the change.
        assertThrows(VaultCryptoException::class.java) {
            VaultKeyring.unwrapWithPin(old, "11111111".toCharArray(), "vault")
        }
    }

    /**
     * The iteration count is read from the file before any PIN is tried, so a
     * downgraded key ring must be refused rather than silently used.
     */
    @Test
    fun aDowngradedIterationCountIsRefusedBeforeDeriving() {
        val vmk = VaultKeyring.newMasterKey()
        val wrapper = VaultKeyring.wrapWithPin(vmk, "84920517".toCharArray(), "vault", iterations = 120_000)
        val downgraded = wrapper.copy(iterations = VaultKeyring.MIN_PBKDF2_ITERATIONS - 1)
        assertThrows(IllegalArgumentException::class.java) {
            VaultKeyring.unwrapWithPin(downgraded, "84920517".toCharArray(), "vault")
        }
    }

    /** A wrapper cannot be moved to another vault: the vault id is authenticated. */
    @Test
    fun pinWrapperIsBoundToItsVaultId() {
        val vmk = VaultKeyring.newMasterKey()
        val wrapper = VaultKeyring.wrapWithPin(vmk, "84920517".toCharArray(), "vault-a", iterations = 120_000)
        assertThrows(VaultCryptoException::class.java) {
            VaultKeyring.unwrapWithPin(wrapper, "84920517".toCharArray(), "vault-b")
        }
    }

    /** The in-place validation path leaves the caller's buffer untouched. */
    @Test
    fun pinPolicyCharArrayValidationDoesNotMutateTheBuffer() {
        val pin = "84920517".toCharArray()
        assertEquals(VaultPinPolicy.Result.Ok, VaultPinPolicy.validate(pin))
        assertEquals("84920517", String(pin))
        val weak = "111111".toCharArray()
        assertTrue(VaultPinPolicy.validate(weak) is VaultPinPolicy.Result.Rejected)
        assertEquals("111111", String(weak))
        weak.fill('\u0000')
    }

    @Test
    fun recoveryCodeFormatHasEnoughEntropyAndChecksum() {
        val secret = VaultRecoveryCode.generate()
        val formatted = VaultRecoveryCode.format(secret)
        assertEquals(32, secret.size)
        assertEquals(
            "54 characters in groups of four",
            VaultRecoveryCode.TOTAL_CHARS + (VaultRecoveryCode.TOTAL_CHARS / VaultRecoveryCode.GROUP_SIZE),
            formatted.length
        )
        assertArrayEquals(secret, VaultRecoveryCode.parse(formatted)!!)
        assertNotNull(VaultRecoveryCode.parse(formatted.lowercase().replace("-", " ")))
        assertNull("a single wrong character must be caught", VaultRecoveryCode.parse(flipFirst(formatted)))
        assertNull(VaultRecoveryCode.parse("too short"))
        assertNull(VaultRecoveryCode.parse(""))
    }

    @Test
    fun recoveryCodeForgivesHumanTranscription() {
        val secret = VaultRecoveryCode.generate()
        val formatted = VaultRecoveryCode.format(secret)
        // Crockford aliases: O -> 0, I/L -> 1, and dashes/case/spacing are ignored.
        val sloppy = formatted.replace('0', 'O').replace('1', 'l').replace("-", "").lowercase()
        assertArrayEquals(secret, VaultRecoveryCode.parse(sloppy)!!)
    }

    @Test
    fun everyRecoveryCodeIsDistinct() {
        val seen = HashSet<String>()
        repeat(200) { assertTrue(seen.add(VaultRecoveryCode.format(VaultRecoveryCode.generate()))) }
    }

    @Test
    fun zeroizedMasterKeyCopyLeavesOriginalUsable() {
        val vmk = VaultKeyring.newMasterKey()
        val copy = vmk.copyOf()
        VaultCrypto.zeroize(copy)
        assertTrue(copy.all { it == 0.toByte() })
        assertFalse(vmk.all { it == 0.toByte() })
    }

    private fun flipFirst(formatted: String): String {
        val index = formatted.indexOfFirst { it.isLetterOrDigit() }
        val character = formatted[index]
        val replacement = if (character == '0') '1' else '0'
        return formatted.substring(0, index) + replacement + formatted.substring(index + 1)
    }
}
