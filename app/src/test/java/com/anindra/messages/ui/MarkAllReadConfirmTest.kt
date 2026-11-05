package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Mark all as read" is irreversible and sits one tap away from "Notifications"
 * in the settings list, so a stray tap used to wipe every unread badge with no
 * way back. Both settings UIs must ask first and only act on the confirm
 * branch; cancelling has to leave the badges alone.
 */
class MarkAllReadConfirmTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val screens = linkedMapOf(
        "new UI" to File(main, "java/com/anindra/messages/ui/SettingsScreen.kt"),
        "legacy UI" to File(main, "java/com/anindra/messages/ui/legacy/LegacySettingsScreen.kt")
    )

    /** Source text of the `SettingsRow(` call that carries the "Mark all as read" title. */
    private fun rowBlock(src: String): String {
        val at = src.indexOf("R.string.settings_mark_read_title")
        assertTrue("the Mark all as read row is missing", at > 0)
        val start = src.lastIndexOf("SettingsRow(", at)
        return balancedBlock(src, start, '(', ')')
    }

    /** Source text of the `if (markReadDialog) { ... }` confirmation. */
    private fun dialogBlock(src: String): String {
        val at = src.indexOf("if (markReadDialog)")
        assertTrue("no markReadDialog confirmation in this screen", at > 0)
        val start = src.indexOf('{', at)
        return balancedBlock(src, start, '{', '}')
    }

    private fun balancedBlock(src: String, start: Int, open: Char, close: Char): String {
        var depth = 0
        var i = start
        while (i < src.length) {
            when (src[i]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return src.substring(start, i + 1)
                }
            }
            i++
        }
        throw AssertionError("unterminated block at $start")
    }

    @Test
    fun theRowOnlyOpensTheConfirmation() {
        screens.forEach { (label, file) ->
            val row = rowBlock(file.readText())
            assertTrue(
                "$label: tapping the row marks everything read without asking",
                !row.contains("markAllRead()")
            )
            assertTrue(
                "$label: tapping the row must open the confirmation dialog",
                row.contains("markReadDialog = true")
            )
        }
    }

    @Test
    fun onlyTheConfirmButtonMarksEverythingRead() {
        screens.forEach { (label, file) ->
            val src = file.readText()
            assertEquals(
                "$label: markAllRead() must be called exactly once, from the dialog",
                1,
                Regex("""vm\.markAllRead\(\)""").findAll(src).count()
            )
            val dialog = dialogBlock(src)
            val confirm = dialog.indexOf("confirmButton")
            assertTrue("$label: the dialog has no confirm button", confirm > 0)
            val call = dialog.indexOf("vm.markAllRead()")
            assertTrue("$label: the confirmation never marks anything read", call > confirm)
        }
    }

    @Test
    fun cancellingClosesWithoutActing() {
        screens.forEach { (label, file) ->
            val dialog = dialogBlock(file.readText())
            assertTrue(
                "$label: the dialog needs a way out that marks nothing read",
                dialog.contains("dismissButton")
            )
            assertTrue(
                "$label: backing out of the dialog must not act on the confirm branch",
                dialog.contains("onDismissRequest = { markReadDialog = false }")
            )
            assertTrue(
                "$label: the success toast belongs to the confirm branch, not the row",
                !rowBlock(file.readText()).contains("settings_mark_read)")
            )
        }
    }

    @Test
    fun theConfirmationExplainsWhatItDoes() {
        screens.forEach { (label, file) ->
            val dialog = dialogBlock(file.readText())
            assertTrue(
                "$label: the dialog needs a title and a body",
                dialog.contains("R.string.settings_mark_read_confirm_title") &&
                    dialog.contains("R.string.settings_mark_read_confirm_message")
            )
        }
    }
}
