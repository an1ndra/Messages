package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Searching the settings list has to hide rows the query cannot reach *and* the
 * groups those rows were the only content of, or the screen fills with empty
 * cards that look like bugs.
 */
class SettingsSearchTest {

    private val group = listOf(
        "Notifications" to "Sound and alerts",
        "Notification sound" to "Default (system)",
        "Delivery reports" to "Mark all as read",
        "Choose theme" to "System default"
    )

    @Test
    fun anEmptyQueryKeepsEverything() {
        assertEquals(setOf(0, 1, 2, 3), SettingsSearch.visibleIndices("", group))
        assertEquals(setOf(0, 1, 2, 3), SettingsSearch.visibleIndices("   ", group))
    }

    @Test
    fun aTitleMatchKeepsThatRow() {
        assertEquals(setOf(1), SettingsSearch.visibleIndices("Notification sound", group))
    }

    @Test
    fun aSubtitleMatchKeepsTheRowToo() {
        // Searching by what the row *does* is the point: "sound" only appears in
        // the subtitles of the first two rows here.
        assertEquals(setOf(0, 1), SettingsSearch.visibleIndices("sound", group))
    }

    @Test
    fun matchingIgnoresCase() {
        assertEquals(setOf(0, 1), SettingsSearch.visibleIndices("SOUND", group))
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertEquals(setOf(3), SettingsSearch.visibleIndices("  theme  ", group))
    }

    @Test
    fun aQueryThatMatchesNothingLeavesNoRows() {
        assertEquals(emptySet<Int>(), SettingsSearch.visibleIndices("bluetooth", group))
    }

    @Test
    fun aGroupWhoseRowsAllVanishedIsDropped() {
        val visible = SettingsSearch.visibleIndices("bluetooth", group)
        assertTrue("the group should be dropped entirely", visible.isEmpty())
    }

    @Test
    fun aBlankLabelNeverMatches() {
        // A row whose subtitle is empty must still be findable by its title, and
        // a blank label must not be matchable in its own right.
        val rows = listOf("Backup messages" to null, "Import messages" to "")
        assertEquals(setOf(0), SettingsSearch.visibleIndices("backup", rows))
        assertEquals(setOf(1), SettingsSearch.visibleIndices("import", rows))
    }

    @Test
    fun aWhitespaceOnlyQueryIsTreatedAsEmpty() {
        // Typing then deleting should restore the list rather than blank it.
        assertEquals(setOf(0, 1), SettingsSearch.visibleIndices(" ", listOf("A" to "a", "B" to "b")))
        assertTrue(SettingsSearch.matches("  ", "anything"))
    }

    @Test
    fun matchesAcceptsLabelsDirectly() {
        assertTrue(SettingsSearch.matches("alp", "Alpha"))
        assertTrue(SettingsSearch.matches("ALPHA", "alpha"))
        assertFalse(SettingsSearch.matches("beta", "alpha", null, ""))
    }
}