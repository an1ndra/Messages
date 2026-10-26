package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** #345: on a Persian-locale device every window is RTL while this app's
 *  strings are English (no values-fa yet), so an English dialog sentence's
 *  trailing "?" parked at its visual start. Dialog copy must paint in its own
 *  strong direction, and report it as DialogBidi for the regression scripts. */
class DialogDirectionTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val components: String by lazy {
        File(main, "java/com/anindra/messages/ui/Components.kt").readText()
    }

    private val screens = linkedMapOf(
        "new UI" to File(main, "java/com/anindra/messages/ui/SettingsScreen.kt"),
        "legacy UI" to File(main, "java/com/anindra/messages/ui/legacy/LegacySettingsScreen.kt")
    )

    @Test
    fun dialogCopyCarriesItsOwnDirection() {
        assertTrue(
            "directionalStyle must derive from BidiText.direction — the same " +
                "rule the message bubbles paint by",
            Regex("""internal fun directionalStyle\(base: TextStyle, text: String\): TextStyle""")
                .containsMatchIn(components) &&
                components.contains("BidiText.direction(text)")
        )
    }

    @Test
    fun theDirectionIsAssertableFromADumpRun() {
        assertTrue(
            "the paint order is invisible to uiautomator; log it as DialogBidi",
            components.contains("\"DialogBidi\"")
        )
    }

    @Test
    fun bothMarkAllReadDialogsPaintTheirCopyDirectionally() {
        screens.forEach { (label, file) ->
            val src = file.readText()
            val at = src.indexOf("settings_mark_read_confirm_title")
            assertTrue("$label: no mark-read dialog", at > 0)
            val start = src.lastIndexOf("if (markReadDialog)", at)
            val dialog = balancedBlock(src, src.indexOf('{', start))
            assertEquals(
                "$label: both the title and the body must paint in their own direction",
                2,
                Regex("""directionalStyle\(LocalTextStyle\.current, """).findAll(dialog).count()
            )
        }
    }

    private fun balancedBlock(src: String, openBrace: Int): String {
        var depth = 0
        for (i in openBrace until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(openBrace, i + 1)
                }
            }
        }
        throw AssertionError("unterminated block at $openBrace")
    }
}
