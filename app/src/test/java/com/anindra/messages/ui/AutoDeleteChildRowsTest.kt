package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Auto-delete's child rows must be hidden while auto-delete is off.
 *
 * Both UIs used to render the bucket rows greyed out (`enabled = retentionOn`)
 * instead of hiding them. A disabled row that has no effect is a dead control,
 * and it left the user reading options that could not do anything — the footer
 * even states that nothing is erased while auto-delete is off.
 *
 * This is checked in both implementations, not just the redesigned one: the old
 * UI is the default, and it carries its own copy of these rows inline in
 * Advanced settings rather than in a sub-screen.
 */
class AutoDeleteChildRowsTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    /** The redesigned UI's Auto-delete sub-screen. */
    private val newScreen: String by lazy {
        val src = File(main, "java/com/anindra/messages/ui/AdvancedSubScreens.kt").readText()
        val start = Regex("fun AutoDeleteSettingsScreen\\(").find(src)?.range?.first
            ?: error("AutoDeleteSettingsScreen not found")
        val end = Regex("fun LinkSettingsScreen\\(").find(src)?.range?.first
            ?: error("LinkSettingsScreen not found")
        assertTrue("AutoDeleteSettingsScreen must come before LinkSettingsScreen", start < end)
        src.substring(start, end)
    }

    /** The old UI's inline block inside Advanced settings (card 13). */
    private val legacyScreen: String by lazy {
        val src = File(main, "java/com/anindra/messages/ui/legacy/LegacyAdvancedSettingsScreen.kt").readText()
        val start = src.indexOf("SettingsCard(13) {")
        assertTrue("card 13 (Auto-delete) not found in the legacy screen", start > 0)
        val end = src.indexOf("Spacer(Modifier.height(8.dp))", start)
        assertTrue("could not find the end of card 13", end > start)
        src.substring(start, end)
    }

    private val childRows = listOf(
        "settings_retention_trash",
        "settings_retention_keep_trash",
        "settings_retention_keyword",
        "settings_retention_blocked",
        "settings_retention_keep_spam"
    )

    /** Index just past the `}` that closes the block opened at [openIdx]. */
    private fun blockEnd(src: String, openIdx: Int): Int {
        var depth = 0
        var i = openIdx
        while (i < src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun assertGated(screen: String, label: String) {
        val gate = screen.indexOf("if (retentionOn) {")
        assertTrue("$label: no `if (retentionOn)` gate", gate > 0)
        // Brace matching, not indexOf("}"): the block contains the nested
        // `if (retentionTrash)` and `if (retentionBlocked)` blocks, so the
        // first closing brace belongs to one of those.
        val gateEnd = blockEnd(screen, screen.indexOf("{", gate))
        assertTrue("$label: could not find the end of the retentionOn block", gateEnd > gate)
        val inside = screen.substring(gate, gateEnd)

        for (row in childRows) {
            assertTrue(
                "$label: $row is not inside the `if (retentionOn)` gate, so it shows while auto-delete is off",
                Regex("R\\.string\\.$row\\b").containsMatchIn(inside)
            )
        }
        assertTrue(
            "$label: child rows should no longer be `enabled = retentionOn`; they are hidden instead",
            !inside.contains("enabled = retentionOn")
        )
    }

    @Test
    fun theRedesignedScreenHidesItsChildRows() = assertGated(newScreen, "new UI")

    @Test
    fun theOldScreenHidesItsChildRows() = assertGated(legacyScreen, "legacy UI")

    @Test
    fun noUiRendersTheChildRowsGreyedOut() {
        // Belt and braces: whichever implementation someone edits, the old
        // inert pattern must not come back.
        listOf("AdvancedSubScreens.kt" to newScreen, "LegacyAdvancedSettingsScreen.kt" to legacyScreen)
            .forEach { (file, screen) ->
                assertTrue(
                    "$file still disables child rows on `retentionOn` instead of hiding them",
                    !Regex("enabled\\s*=\\s*retentionOn").containsMatchIn(screen)
                )
            }
    }

    @Test
    fun permanentDeleteStaysVisibleWhenAutoDeleteIsOff() {
        // It is not a child: it is about where a delete lands, not when it runs.
        val gate = newScreen.indexOf("if (retentionOn) {")
        val at = newScreen.indexOf("R.string.settings_advanced_permanent_delete")
        assertTrue("Permanent delete row is missing from the redesigned screen", at > 0)
        assertTrue(
            "Permanent delete is not a child of auto-delete and must stay outside the gate",
            at < gate
        )
    }

    @Test
    fun theDayRowsRemainGatedOnTheirOwnToggle() {
        // "Keep deleted chats for" belongs to the Deleted chats toggle, not
        // directly to auto-delete, so it needs both conditions.
        assertTrue(
            "new UI: keep_trash should require retentionTrash as well",
            newScreen.contains("if (retentionTrash) {")
        )
        assertTrue(
            "legacy UI: keep_trash should require retentionTrash as well",
            legacyScreen.contains("if (retentionTrash) {")
        )
        assertTrue(
            "legacy UI: keep_spam should require a keyword or blocked-sender bucket as well",
            legacyScreen.contains("if (retentionKeyword || retentionBlocked) {")
        )
    }
}
