package com.anindra.messages.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts/decrypts backup files using Android Keystore-backed AES-256-GCM.
 * Backups are device-bound — only this app installation can read them.
 */
object BackupCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "messages_backup_key"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getEntry(KEY_ALIAS, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }
        val kg = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        kg.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    fun encrypt(input: InputStream, output: OutputStream) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        output.write(iv)
        val buf = ByteArray(8192)
        var read: Int
        while (input.read(buf).also { read = it } != -1) {
            cipher.update(buf, 0, read)?.let { output.write(it) }
        }
        // GCM emits nothing on doFinal() when input was fully flushed by
        // update() (provider returns null instead of an empty array).
        cipher.doFinal()?.let { output.write(it) }
    }

    fun decrypt(input: InputStream, output: OutputStream): Boolean {
        return try {
            val iv = ByteArray(GCM_IV_LENGTH)
            if (input.read(iv) != GCM_IV_LENGTH) return false
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
            val buf = ByteArray(8192 + GCM_TAG_LENGTH)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                cipher.update(buf, 0, read)?.let { output.write(it) }
            }
            cipher.doFinal()?.let { output.write(it) }
            true
        } catch (_: Exception) {
            false
        }
    }
}
