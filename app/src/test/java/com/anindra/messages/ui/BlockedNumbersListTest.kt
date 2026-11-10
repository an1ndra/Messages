package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The blocked-numbers dialog must reach every number it counts (#405 follow-up).
 *
 * The reporter had 164 blocked senders: Inbox settings said "164 blocked", but
 * the dialog showed 14 and offered no next page. Both numbers come from the
 * same list — the subtitle uses `blockedNumbers.size` and the dialog iterates
 * `blockedNumbers` — so 164 was the truth and the other 150 rows were laid out
 * below the dialog's finite height with no way to scroll to them.
 *
 * A plain `Column` in a dialog is not scrollable. The list needs a scroll
 * container with a finite ceiling, or the rows past the first screenful exist
 * only to be clipped.
 */
class BlockedNumbersListTest {
    private fun locate(path: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, path)
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("could not locate $path")
    }

    private val settingsScreen: String by lazy {
        locate("app/src/main/java/com/anindra/messages/ui/InboxSettingsScreen.kt")
    }

    private val dialog: String by lazy {
        locate("app/src/main/java/com/anindra/messages/ui/BlockedNumbersDialog.kt")
    }

    @Test
    fun theCountAndTheRowsComeFromTheSameList() {
        val row = settingsScreen.substringAfter("R.string.settings_blocking_title")
            .substringBefore("settings_delayed_title")
        assertTrue(
            "the subtitle must count the blocked list itself",
            row.contains("settings_blocked_numbers_count, blockedNumbers.size"),
        )
        assertTrue(
            "the screen must hand the dialog the same list it counted",
            Regex("""BlockedNumbersDialog\(\s*blockedNumbers = blockedNumbers""")
                .containsMatchIn(settingsScreen),
        )
        assertTrue(
            "the dialog must iterate that same list",
            dialog.contains("blockedNumbers.forEach"),
        )
        assertEquals(
            "no slice or page size may hide entries the count includes",
            0,
            Regex("""blockedNumbers\.(take|drop|subList)\(""").findAll(dialog).count(),
        )
    }

    @Test
    fun theRowsLiveInABoundedScrollContainer() {
        val container = dialog.substringBefore("blockedNumbers.forEach")
        assertTrue(
            "the blocked-number rows must scroll, or rows past the first screenful are unreachable",
            container.contains("verticalScroll("),
        )
        assertTrue(
            "the scroll container needs a finite height inside a dialog",
            Regex("""heightIn\(max = \d+\.dp\)""").containsMatchIn(container),
        )
    }
}
