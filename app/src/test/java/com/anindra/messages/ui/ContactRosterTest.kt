package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contact list every picker offers: the personal and the work profile are
 * one list to the user, and a row the provider cannot name or number is not a
 * destination. Before this was one function, the same number saved in both
 * profiles was listed twice (#209 regression territory).
 */
class ContactRosterTest {

    @Test
    fun aNumberSavedInBothProfilesIsListedOnce() {
        val merged = ContactRoster.merge(
            listOf(
                ContactRow("Anindra", "9876543210"),
                ContactRow("Anindra (work)", "9876543210", workProfile = true)
            )
        )
        assertEquals(1, merged.size)
        assertEquals("Anindra", merged.single().name)
        assertTrue("the personal spelling wins", !merged.single().workProfile)
    }

    @Test
    fun separatorsDoNotMakeTwoPeople() {
        val merged = ContactRoster.merge(
            listOf(
                ContactRow("Anindra", "98765 43210"),
                ContactRow("Anindra", "9876543210")
            )
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun aRowWithoutANameOrNumberIsDropped() {
        val merged = ContactRoster.merge(
            listOf(
                ContactRow(null, "9876543210"),
                ContactRow("Nameless number", null),
                ContactRow("Real", "9000000001")
            )
        )
        assertEquals(listOf("Real"), merged.map { it.name })
    }

    @Test
    fun contactsKeepTheOrderTheProviderGave() {
        // Contacts sorts by DISPLAY_NAME, and the picker is a scrolling list, so
        // re-sorting here would silently disagree with the phone book.
        val merged = ContactRoster.merge(
            listOf(
                ContactRow("A", "9000000001"),
                ContactRow("B", "9000000002"),
                ContactRow("C", "9000000003", workProfile = true)
            )
        )
        assertEquals(listOf("A", "B", "C"), merged.map { it.name })
        assertTrue(merged.last().workProfile)
    }
}
