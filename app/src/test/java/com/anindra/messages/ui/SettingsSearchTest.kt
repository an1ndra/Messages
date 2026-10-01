package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search matches option names the way a dialler suggests contacts: by what the
 * option is called, not by where it sits on the screen.
 */
class SettingsSearchTest {

    @Test
    fun anExactSubstringMatches() {
        assertTrue(SettingsSearch.matches("Notification", "Notification sound"))
        assertTrue(SettingsSearch.matches("sound", "Notification sound"))
        assertTrue(SettingsSearch.matches("NOTIF", "notification sound"))
    }

    @Test
    fun aQueryThatIsNotContainedDoesNotMatch() {
        assertFalse(SettingsSearch.matches("bluetooth", "Notification sound"))
        assertFalse(SettingsSearch.matches("sound", "Notification", "Blocked senders"))
    }

    @Test
    fun anEmptyQueryMatchesEverything() {
        assertTrue(SettingsSearch.matches("", "anything"))
        assertTrue(SettingsSearch.matches("   ", "anything"))
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertTrue(SettingsSearch.matches("  sound  ", "Notification sound"))
    }

    @Test
    fun aBlankOrMissingLabelNeverMatches() {
        // A row with no subtitle must still be findable by its title, and a blank
        // label must not be matchable in its own right.
        assertFalse(SettingsSearch.matches("x", null))
        assertFalse(SettingsSearch.matches("x", ""))
        assertFalse(SettingsSearch.matches("x", null, ""))
        assertTrue(SettingsSearch.matches("Backup", "Backup messages", null))
    }

    @Test
    fun anyOneLabelIsEnough() {
        assertTrue(SettingsSearch.matches("trash", "Deleted conversations", "Trash"))
    }
}