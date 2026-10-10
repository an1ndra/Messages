package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * App lock may only change after the user proves who they are, in both
 * directions and in both UIs.
 *
 * Disabling used to need no check at all: only enabling verified anything, and
 * even then it merely asked whether a credential existed rather than actually
 * asking for one. Since the lock is what stands between a borrowed unlocked
 * phone and the user's messages, turning it *off* is the sensitive direction.
 *
 * Checked in both UIs because each carries its own copy of the row, and the
 * legacy UI is the default.
 */
class AppLockVerificationWiringTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val newUi: String by lazy {
        File(main, "java/com/anindra/messages/ui/SettingsScreen.kt").readText()
    }

    private val legacyUi: String by lazy {
        File(main, "java/com/anindra/messages/ui/legacy/LegacyAdvancedSettingsScreen.kt").readText()
    }

    /** The body of the App lock row in one of the two screens. */
    private fun appLockRow(src: String): String {
        val at = src.indexOf("R.string.settings_applock_title")
        assertTrue("App lock row not found", at > 0)
        val start = src.lastIndexOf("SettingsRow(", at)
        // Walk the argument list to its close rather than a fixed offset.
        var depth = 0
        var i = src.indexOf("(", start)
        val open = i
        while (i < src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return src.substring(start, i + 1)
                }
            }
            i++
        }
        throw AssertionError("unterminated SettingsRow( at $open")
    }

    @Test
    fun bothUilsRouteTheAppLockRowThroughTheSharedCheck() {
        listOf("new UI" to newUi, "legacy UI" to legacyUi).forEach { (label, src) ->
            val row = appLockRow(src)
            assertTrue(
                "$label: the App lock row must call verifyForAppLockChange()",
                row.contains("verifyForAppLockChange(context)")
            )
        }
    }

    @Test
    fun theSettingIsOnlyWrittenOnTheVerifiedBranch() {
        listOf("new UI" to newUi, "legacy UI" to legacyUi).forEach { (label, src) ->
            val row = appLockRow(src)
            val verified = row.indexOf("AppLockVerdict.Verified")
            assertTrue("$label: no Verified branch in the App lock row", verified > 0)
            val write = row.indexOf("vm.settings.appLockEnabled = enable")
            assertTrue("$label: the row never writes appLockEnabled", write > 0)
            assertTrue(
                "$label: appLockEnabled is written outside the Verified branch, so a rejected or " +
                    "unavailable check would still change the setting",
                write > verified
            )
        }
    }

    @Test
    fun noUiKeepsTheOldCapabilityCheck() {
        // The old code asked only "does a credential exist" and applied the
        // change without ever prompting.
        listOf("new UI" to newUi, "legacy UI" to legacyUi).forEach { (label, src) ->
            assertTrue(
                "$label still calls canAuthenticate() directly in the App lock row",
                !appLockRow(src).contains("canAuthenticate")
            )
        }
    }

    @Test
    fun bothUilsShareOneHelper() {
        // Two copies of the rule is how one of them ends up skipping it.
        val helper = File(main, "java/com/anindra/messages/ui/AppLockVerification.kt")
        assertTrue("AppLockVerification.kt is missing", helper.isFile)
        val text = helper.readText()
        assertTrue(
            "the helper must actually show a prompt, not just check capability",
            text.contains("prompt.authenticate(")
        )
        assertTrue(
            "the helper must report NoScreenLock when there is no credential",
            text.contains("AppLockVerdict.NoScreenLock")
        )
    }

    @Test
    fun theCheckCoversDisablingAsWellAsEnabling() {
        val helper = File(main, "java/com/anindra/messages/ui/AppLockVerification.kt").readText()
        // The helper is direction-agnostic: the caller passes the new value and
        // the helper asks first. If a future caller only asks when enabling, this
        // documents that the helper itself does not care.
        assertTrue(
            "the helper should not branch on the direction of the change",
            !Regex("enable\\b").containsMatchIn(helper)
        )
    }

    @Test
    fun bothUlsHandleEveryVerdict() {
        listOf("new UI" to newUi, "legacy UI" to legacyUi).forEach { (label, src) ->
            val row = appLockRow(src)
            listOf("Verified", "NoScreenLock", "Rejected").forEach { v ->
                assertTrue("$label: the App lock row ignores $v", row.contains("AppLockVerdict.$v"))
            }
        }
    }
}
