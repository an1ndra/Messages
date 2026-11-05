package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** #345/#405: an RTL window can host English copy (a locale the app does not
 *  ship falls back to `values/`, or the window predates `values-fa`; he, ur,
 *  ckb and ps still fall back today). An English sentence in an RTL paragraph
 *  parks its trailing "?" or "." at the visual start, so dialog copy must paint
 *  in its own strong direction and report it as DialogBidi for the regression
 *  scripts. */
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
            "directionalStyle must derive from BidiText.firstStrongDirection — the " +
                "same first-strong rule the message bubbles paint by",
            Regex("""internal fun directionalStyle\(base: TextStyle, text: String\): TextStyle""")
                .containsMatchIn(components) &&
                components.contains("BidiText.firstStrongDirection(text)")
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

    /** The default-SMS prompt is the screen a new Persian-locale user meets
     *  first, and it is the one that parked "?" in front of "app" (#362
     *  follow-up, seen in the field). It lives in MainActivity, not in a
     *  settings screen, so the sweep above never covered it. */
    @Test
    fun theDefaultSmsPromptPaintsItsCopyDirectionally() {
        val src = File(main, "java/com/anindra/messages/MainActivity.kt").readText()
        val at = src.indexOf("R.string.default_sms_title")
        assertTrue("the default-SMS dialog is gone from MainActivity", at > 0)
        val start = src.lastIndexOf("if (showDefaultSmsDialog)", at)
        val dialog = balancedBlock(src, src.indexOf('{', start))
        assertEquals(
            "both the prompt's title and its body must paint in their own direction",
            2,
            Regex("""directionalStyle\(\s*androidx\.compose\.material3\.LocalTextStyle\.current""")
                .findAll(dialog).count()
        )
    }

    /** #405's general guard: any AlertDialog title or body whose English copy
     *  ends in "?", "." or ":" must paint through [DialogText] (or
     *  directionalStyle). A new dialog with punctuated copy therefore fails
     *  here instead of parking its punctuation at the visual start on an RTL
     *  phone. Slots that only compose dynamic values have no resource to read,
     *  so they are left to their renderer. */
    @Test
    fun everyPunctuatedAlertDialogSlotPaintsDirectionally() {
        val strings = englishStrings()
        val offenders = mutableListOf<String>()
        File(main, "java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val src = file.readText()
                var at = src.indexOf("AlertDialog(")
                while (at >= 0) {
                    val dialog = balancedBlock(src, src.indexOf('{', at))
                    for (slot in listOf("title", "text")) {
                        val marker = Regex("""\b$slot\s*=\s*\{""").find(dialog) ?: continue
                        val block = balancedBlock(dialog, marker.range.last)
                        val punctuated = Regex("""R\.string\.(\w+)""")
                            .findAll(block)
                            .map { it.groupValues[1] }
                            .filter { endsWithPunctuation(strings[it]) }
                            .distinct()
                            .toList()
                        if (punctuated.isNotEmpty() &&
                            !block.contains("DialogText(") &&
                            !block.contains("directionalStyle(")
                        ) {
                            offenders += "${file.name}: $slot -> ${punctuated.joinToString()}"
                        }
                    }
                    at = src.indexOf("AlertDialog(", at + 1)
                }
            }
        assertEquals(
            "punctuated dialog copy must paint in its own strong direction",
            emptyList<String>(),
            offenders
        )
    }

    /** About's two dialogs are full-window Dialog + Surface (three buttons do
     *  not fit an AlertDialog row), so the sweep above cannot see them. */
    @Test
    fun aboutDialogsPaintTheirBodiesDirectionally() {
        val src = File(main, "java/com/anindra/messages/ui/AboutScreen.kt").readText()
        assertEquals(
            "the diagnostics and licences bodies must both use DialogText",
            2,
            Regex("""DialogText\(""").findAll(src).count()
        )
    }

    private fun englishStrings(): Map<String, String> {
        val out = HashMap<String, String>()
        val xml = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        File(main, "res/values").listFiles { f ->
            f.isFile && f.name.startsWith("strings") && f.extension == "xml"
        }?.forEach { file ->
            xml.findAll(file.readText()).forEach { out[it.groupValues[1]] = it.groupValues[2] }
        }
        return out
    }

    private fun endsWithPunctuation(value: String?): Boolean =
        value?.trim()?.lastOrNull() in setOf('?', '.', ':', '؟')

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
