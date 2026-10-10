package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A group is listed and announced by its own name, not by whoever it started
 * with.
 *
 * A group's `address` is its primary contact's, so the usual "known contact
 * shows their name" rule files "Sarah + Dad" under "Sarah". That reached the
 * visible row title and the label a screen reader announced, in the redesigned
 * list, which never had the group branch -- the legacy list did.
 *
 * [ContactDetails.listLabel] is the single copy both lists call, so the two
 * cannot drift apart again.
 */
class ContactDetailsListLabelTest {
    private val sarah = "+15551230010"
    private val formatted = "(555) 123-0010"

    @Test
    fun `a group's own name wins over its primary contact`() {
        assertEquals(
            "Sarah +Dad",
            ContactDetails.listLabel(
                groupTitle = "Sarah +Dad",
                name = "Sarah",
                address = sarah,
                display = formatted,
            ),
        )
    }

    @Test
    fun `a saved contact is listed by their name`() {
        assertEquals(
            "Sarah",
            ContactDetails.listLabel("", "Sarah", sarah, formatted),
        )
    }

    @Test
    fun `an unknown sender is listed by their number`() {
        // name == address means the row has no real name, so the formatted
        // number stands in for one.
        assertEquals(
            formatted,
            ContactDetails.listLabel("", sarah, sarah, formatted),
        )
    }

    @Test
    fun `a group with no title falls back like a private chat`() {
        // groupTitle is only populated once a second person is added; before
        // that the conversation is still a private one and must read normally.
        assertEquals(
            "Sarah",
            ContactDetails.listLabel("", "Sarah", sarah, formatted),
        )
        assertEquals(
            formatted,
            ContactDetails.listLabel("   ", sarah, sarah, formatted),
        )
    }

    @Test
    fun `a blank title is treated as no title`() {
        // group_title defaults to '' and is cleared back to '' by editing the
        // name to blank, so whitespace must not resurrect a title.
        assertEquals(
            "Sarah",
            ContactDetails.listLabel(" ", "Sarah", sarah, formatted),
        )
    }
}