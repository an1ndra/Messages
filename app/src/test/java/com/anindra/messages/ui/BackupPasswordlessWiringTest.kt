package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #292: a passwordless backup writes the plain SQLite database and warns first,
 * in both UIs — the legacy UI is the default. The Android file I/O itself is
 * exercised by the on-device regression script; this pins the wiring a refactor
 * could silently drop.
 */
class BackupPasswordlessWiringTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val repository: String by lazy {
        File(main, "java/com/anindra/messages/data/Repository.kt").readText()
    }
    private val newUi: String by lazy {
        File(main, "java/com/anindra/messages/ui/SettingsScreen.kt").readText()
    }
    private val legacyUi: String by lazy {
        File(main, "java/com/anindra/messages/ui/legacy/LegacySettingsScreen.kt").readText()
    }
    private val baseStrings: String by lazy {
        File(main, "res/values/strings_settings.xml").readText()
    }

    /** Body of [functionName] in [src], by brace matching. */
    private fun bodyOf(functionName: String, src: String): String {
        val start = src.indexOf("fun $functionName(")
        assertTrue("$functionName not found", start > 0)
        var depth = 0
        var seen = false
        for (i in start until src.length) {
            when (src[i]) {
                '{' -> { depth++; seen = true }
                '}' -> { depth--; if (seen && depth == 0) return src.substring(start, i + 1) }
            }
        }
        error("unterminated body for $functionName")
    }

    @Test
    fun unencryptedExportWritesTheRawDatabaseWithoutEncrypting() {
        val body = bodyOf("backupDatabaseUnencrypted", repository)
        assertTrue(
            "the export must be recorded as the RAW format the importer already recognises",
            body.contains("BackupFormat.RAW.name")
        )
        assertTrue("expected a plain file copy, not an encrypting stream", body.contains("copyTo"))
        assertFalse(
            "a passwordless export must not go through BackupCrypto.encryptWithPin",
            body.contains("encryptWithPin")
        )
    }

    @Test
    fun theWarningStringsExist() {
        listOf(
            "settings_backup_unencrypted_title",
            "settings_backup_unencrypted_warning",
            "settings_backup_unencrypted_confirm",
            "settings_backup_saved_location_unencrypted"
        ).forEach { key ->
            assertTrue("base string $key is missing", baseStrings.contains("name=\"$key\""))
        }
    }

    @Test
    fun bothUisSaveWithoutAPinWhenThePinIsEmpty() {
        listOf("new UI" to newUi, "legacy UI" to legacyUi).forEach { (label, src) ->
            assertTrue(
                "$label: Save must treat an empty PIN as 'back up without one'",
                src.contains("mode == PinDialogMode.SET && pin.isEmpty()")
            )
            assertTrue(
                "$label: the empty-PIN branch never calls the unencrypted export",
                src.contains("vm.backupDatabaseUnencrypted")
            )
            assertFalse(
                "$label: still carries a separate 'back up without PIN' action",
                src.contains("settings_backup_without_pin")
            )
        }
    }
}
