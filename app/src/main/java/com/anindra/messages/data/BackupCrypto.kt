package com.anindra.messages.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Two formats, one file shape:
 *  - PIN-protected (new): `"MSP\x01" | salt | iv | AES-256-GCM ciphertext`
 *    Key = PBKDF2(pin, salt). Portable — survives reinstall / new device.
 *  - Legacy device-bound: `iv | AES-256-GCM ciphertext`, key = Android Keystore.
 *    Kept so backups made before the PIN feature still import.
 */
object BackupCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "messages_backup_key"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PIN_MAGIC_LENGTH = 4
    private const val PIN_SALT_LENGTH = 16
    private const val PIN_MAX_DIGITS = 16
    private const val PBKDF2_ITERATIONS = 120_000
    private val PIN_MAGIC = byteArrayOf('M'.code.toByte(), 'S'.code.toByte(), 'P'.code.toByte(), 1)

    fun isValidPin(pin: String): Boolean =
        pin.length in 4..PIN_MAX_DIGITS && pin.all { it.isDigit() }

    fun isPinMagic(magic: ByteArray): Boolean =
        magic.size == PIN_MAGIC_LENGTH && magic.contentEquals(PIN_MAGIC)

    fun encryptWithPin(input: InputStream, output: OutputStream, pin: String) {
        val salt = ByteArray(PIN_SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(pin, salt))
        output.write(PIN_MAGIC)
        output.write(salt)
        output.write(cipher.iv)
        val buf = ByteArray(8192)
        var read: Int
        while (input.read(buf).also { read = it } != -1) {
            cipher.update(buf, 0, read)?.let { output.write(it) }
        }
        // GCM emits nothing on doFinal() when input was fully flushed by
        // update() (provider returns null instead of an empty array).
        cipher.doFinal()?.let { output.write(it) }
    }

    /** False when the stream is not PIN-encrypted, is truncated, or the PIN is
     *  wrong (GCM auth rejects the tag). */
    fun decryptWithPin(input: InputStream, output: OutputStream, pin: String): Boolean {
        return try {
            val magic = ByteArray(PIN_MAGIC_LENGTH)
            if (input.read(magic) != PIN_MAGIC_LENGTH || !isPinMagic(magic)) return false
            val salt = ByteArray(PIN_SALT_LENGTH)
            if (input.read(salt) != PIN_SALT_LENGTH) return false
            val iv = ByteArray(GCM_IV_LENGTH)
            if (input.read(iv) != GCM_IV_LENGTH) return false
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(pin, salt),
                GCMParameterSpec(GCM_TAG_LENGTH, iv)
            )
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

    private fun deriveKey(pin: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val secret = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

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

    /** Legacy Keystore-bound decrypt for pre-PIN backups. */
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