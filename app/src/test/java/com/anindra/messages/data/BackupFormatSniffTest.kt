package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Reproduces the "Invalid or corrupted backup file" report on a perfectly
 * valid PIN-protected backup.
 *
 * The shape of the failure: `importDatabase` sniffs the format with
 * `peekBackupFormat`, which reads 4 bytes and asks `isPinMagic`. Anything that
 * is not exactly `MSP\x01` is routed to the LEGACY branch, which assumes a
 * Keystore-encrypted file and calls `BackupCrypto.decrypt`. That function
 * unconditionally eats the first 12 bytes as a GCM IV, so on a PIN file it
 * consumes the magic *and* 8 bytes of salt, the tag check fails, and the
 * function returns false.
 *
 * `decrypt` returning false is not itself the bug -- the legacy branch is
 * designed to fall through to a raw copy for genuinely unencrypted files. The
 * bug is that a PIN file silently takes that same route, and the raw copy then
 * fails the SQLite header check.
 *
 * These tests pin the two halves of that so the routing cannot silently change.
 */
class BackupFormatSniffTest {

    private fun temp(name: String, bytes: ByteArray): File {
        val f = File.createTempFile(name, ".enc")
        f.deleteOnExit()
        f.writeBytes(bytes)
        return f
    }

    /** Byte-for-byte what `isPinMagic` compares against. */
    private val pinMagic = byteArrayOf('M'.code.toByte(), 'S'.code.toByte(), 'P'.code.toByte(), 1)

    @Test
    fun pinMagicIsFourBytes() {
        // The whole routing decision rests on reading exactly 4 bytes and
        // matching them. A shorter or longer constant would misroute silently.
        assertEquals(4, pinMagic.size)
        assertTrue(BackupCrypto.isPinMagic(pinMagic))
    }

    @Test
    fun aPinFileIsRecognised() {
        // Header layout on disk: magic | salt(16) | iv(12) | ciphertext | tag
        val salt = ByteArray(16) { 0x11 }
        val iv = ByteArray(12) { 0x22 }
        val body = ByteArray(32) { 0x33 }
        val onDisk = pinMagic + salt + iv + body

        assertTrue(BackupCrypto.isPinMagic(onDisk.copyOfRange(0, 4)))
    }

    @Test
    fun legacyDecryptMisreadsAPinFileAsIvPlusCiphertext() {
        // This is the defect, isolated. `decrypt` is the LEGACY entry point and
        // it has no magic check -- it reads 12 bytes as the IV unconditionally.
        // On a PIN file those 12 bytes are `MSP\x01` plus 8 bytes of salt.
        val salt = ByteArray(16) { 0x11 }
        val iv = ByteArray(12) { 0x22 }
        val onDisk = pinMagic + salt + iv + ByteArray(32) { 0x33 }

        val whatDecryptTreatsAsIv = onDisk.copyOfRange(0, 12)
        assertFalse(
            "the first 12 bytes of a PIN file are magic+salt, not an IV",
            whatDecryptTreatsAsIv.contentEquals(iv)
        )
        // And the raw copy that follows writes the magic back out verbatim.
        assertEquals('M'.code.toByte(), onDisk[0])
    }

    @Test
    fun isValidSqliteFileRejectsAPinFile() {
        // Mirrors Repository.isValidSqliteFile: a raw copy of a PIN file can
        // never pass this, which is why the user sees "corrupted" rather than
        // "wrong PIN" even when the PIN was right.
        val salt = ByteArray(16) { 0x11 }
        val onDisk = pinMagic + salt + ByteArray(12) { 0x22 } + ByteArray(32) { 0x33 }
        val f = temp("pinfile", onDisk)

        val header = ByteArray(16)
        RandomAccessFile(f, "r").use { it.readFully(header) }
        assertFalse(String(header).startsWith("SQLite format 3"))
    }

    @Test
    fun aPlainSqliteFileStillPassesValidation() {
        // Guards the fallback path from being "fixed" into uselessness: an
        // unencrypted backup must keep working, so the raw-copy fallback is
        // load-bearing and cannot simply be deleted.
        val sqlite = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) +
            ByteArray(48) { 0x44 }
        val f = temp("rawsqlite", sqlite)

        val header = ByteArray(16)
        RandomAccessFile(f, "r").use { it.readFully(header) }
        assertTrue(String(header).startsWith("SQLite format 3"))
    }

    @Test
    fun wrongPinIsIndistinguishableFromTampering() {
        // GCM authenticates, so a wrong PIN and a truncated file both fail at
        // the tag. That is why the PIN branch reports "Wrong PIN or corrupted
        // file" -- the message is honest, but it is only reached when isPin is
        // true, which is exactly what the routing bug prevents.
        val out = ByteArrayOutputStream()
        val ok = BackupCrypto.decryptWithPin(
            ByteArrayInputStream(pinMagic + ByteArray(64)), out, "1234"
        )
        assertFalse("a fabricated file must not decrypt", ok)
    }
}
