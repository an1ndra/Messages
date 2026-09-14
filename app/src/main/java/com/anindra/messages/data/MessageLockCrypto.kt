package com.anindra.messages.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Keystore-backed key for the per-message privacy lock.
 *
 * The key requires user authentication for every use, so a successful
 * `BiometricPrompt` can be tied to a real cryptographic operation (a
 * `CryptoObject` cipher) instead of a bare boolean. This defeats UI-only
 * bypasses (Frida/hooking) that flip the unlock flag without authenticating.
 */
object MessageLockCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "messages_message_lock_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val PROOF = byteArrayOf(0x4D, 0x53, 0x47) // "MSG"

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else {
            // Still the correct call on Android 10 (per-use biometric auth).
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    /** A fresh encrypt-mode cipher for `BiometricPrompt.CryptoObject`, or null
     *  when the device cannot create an auth-bound key. */
    fun newAuthCipher(): Cipher? = try {
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
    } catch (_: Exception) {
        null
    }

    /** Runs a real crypto operation with the authenticated cipher, proving the
     *  user actually unlocked the auth-bound key (not just returned from the
     *  prompt). */
    fun proveAuth(cipher: Cipher): Boolean = try {
        cipher.doFinal(PROOF)
        true
    } catch (_: Exception) {
        false
    }
}
