package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reported: "in old UI General settings and Advanced setting background colour is
 * different". Only General painted the settings sheet; Advanced and Accessibility
 * fell back to the theme background, so opening them changed the page colour
 * under the rows.
 *
 * The colour now lives in one shared scaffold, which also reports it. Per-screen
 * reports were tried first and were useless: each read
 * `MaterialTheme.colorScheme.surfaceContainer` itself, so removing the Scaffold's
 * colour left the report green — the same duplication trap that once had
 * Diagnostics agree with a broken theme.
 */
class LegacySettingsSheetTest {

    private val main: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main") }
        .firstOrNull { it.isDirectory }
        ?: error("app/src/main not found")

    private fun legacy(name: String) =
        File(main, "java/com/anindra/messages/ui/legacy/$name.kt").readText()

    private val screens = mapOf(
        "general" to "LegacySettingsScreen",
        "advanced" to "LegacyAdvancedSettingsScreen",
        "accessibility" to "LegacyAccessibilityScreen"
    )

    @Test
    fun everyLegacySettingsScreenUsesTheSharedSheet() {
        for ((screen, file) in screens) {
            val src = legacy(file)
            assertTrue(
                "$file must paint the page through LegacySettingsScaffold",
                src.contains("LegacySettingsScaffold(screen = \"$screen\"")
            )
            assertTrue(
                "$file's app bar must match the sheet, not the page behind it",
                src.contains("containerColor = MaterialTheme.colorScheme.surfaceContainer")
            )
            assertTrue(
                "$file still creates its own Scaffold",
                !src.contains("Scaffold(\n")
            )
        }
    }

    @Test
    fun theSheetColourIsDecidedInOnePlace() {
        val src = legacy("LegacySheetColors")
        assertTrue(
            "the shared scaffold paints the page",
            src.contains("Scaffold(containerColor = sheet")
        )
        assertTrue(
            "and the colour comes from the theme role, not a literal",
            src.contains("val sheet = MaterialTheme.colorScheme.surfaceContainer")
        )
        assertEquals(
            "the report must describe the painted page, not re-read the role elsewhere",
            0,
            Regex("page=\\$\\{hexColor\\(sheet\\)\\}").findAll(src).count() - 1
        )
        assertTrue(
            "reported under the same tag the redesigned settings use",
            src.contains("\"SettingsColors\"")
        )
    }

    @Test
    fun theReportedScreenNamesMatchTheScript() {
        // scripts/test-legacy-settings-colours.sh looks these two up by name.
        for (screen in listOf("general", "advanced")) {
            assertTrue(
                "no screen reports \"$screen\"",
                legacy(screens.getValue(screen)).contains("screen = \"$screen\"")
            )
        }
    }
}
