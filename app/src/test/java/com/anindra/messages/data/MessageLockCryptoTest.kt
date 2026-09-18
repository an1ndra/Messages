package com.anindra.messages.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class MessageLockCryptoTest {

    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()

    @Test
    fun proveAuthTrueForUsableCipher() {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.ENCRYPT_MODE, newKey()) }
        assertTrue(MessageLockCrypto.proveAuth(cipher))
    }

    @Test
    fun proveAuthFalseWhenCipherCannotComplete() {
        val key = newKey()
        val enc = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        // Decrypt with too few bytes to contain a GCM tag -> doFinal throws.
        val dec = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, enc.iv))
        }
        assertFalse(MessageLockCrypto.proveAuth(dec))
    }
}
