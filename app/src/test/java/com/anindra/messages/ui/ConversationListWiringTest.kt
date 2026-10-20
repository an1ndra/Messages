package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #340/#347 wiring: long-press keeps opening a conversation's action sheet (the
 * familiar entry point), the sheet's "Multi select" row is what switches the
 * inbox into a selection, and the "Start chat" pill paints with the role pair
 * that Diagnostics reports.
 */
class ConversationListWiringTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private fun read(rel: String) = File(main, rel).readText()

    private val screens = listOf(
        "java/com/anindra/messages/ui/ConversationsScreen.kt",
        "java/com/anindra/messages/ui/legacy/LegacyConversationsScreen.kt"
    )

    @Test
    fun aLongPressStillOpensTheActionSheet() {
        screens.forEach { path ->
            val src = read(path)
            assertTrue(
                "$path: long-press must open the per-conversation sheet, not a selection",
                src.contains("onLongClick = { sheetConvoId = convo.id }")
            )
        }
    }

    @Test
    fun theSheetOffersMultiSelect() {
        screens.forEach { path ->
            val src = read(path)
            assertTrue(
                "$path: the sheet has no Multi select row",
                src.contains("stringResource(R.string.sheet_multi_select)")
            )
            assertTrue(
                "$path: Multi select must seed the selection with that conversation",
                src.contains("selectedIds = setOf(sheetConvo.id)")
            )
        }
    }

    @Test
    fun everyLocaleTranslatesTheMultiSelectRow() {
        val res = File(main, "res")
        res.listFiles { f: File -> f.isDirectory && (f.name == "values" || f.name.startsWith("values-")) }
            .orEmpty()
            .forEach { dir ->
                val file = File(dir, "strings_main.xml")
                if (!file.isFile) return@forEach
                val value = Regex("""<string name="sheet_multi_select">([^<]*)</string>""")
                    .find(file.readText())?.groupValues?.get(1)
                assertTrue("${dir.name} has no sheet_multi_select", value != null)
                assertTrue("${dir.name} ships a blank sheet_multi_select", !value.isNullOrBlank())
            }
    }

    @Test
    fun theStartChatPillAndDiagnosticsShareOnePair() {
        val fab = read("java/com/anindra/messages/ui/ConversationsScreen.kt")
        val theme = read("java/com/anindra/messages/ui/theme/Theme.kt")
        val report = read("java/com/anindra/messages/diagnostics/DiagnosticsReport.kt")
        assertTrue(
            "the pill must paint with the shared pair, not a mismatched role",
            fab.contains("background(cs.startChatContainer)") &&
                fab.contains("tint = cs.startChatContent") &&
                fab.contains("color = cs.startChatContent")
        )
        assertTrue(
            "the pair is defined once, in the theme",
            theme.contains("val ColorScheme.startChatContainer: Color get() = secondaryContainer") &&
                theme.contains("val ColorScheme.startChatContent: Color get() = onSecondaryContainer")
        )
        assertTrue(
            "Diagnostics must report the pair the pill paints with",
            report.contains("resolvedStartChatHex(")
        )
    }
}
