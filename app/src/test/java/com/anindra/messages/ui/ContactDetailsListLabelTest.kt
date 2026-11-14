package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        // The label is bidi-isolated so a number inside it cannot be reordered
        // by an RTL paragraph; the words are unchanged.
        assertEquals(
            BidiText.ltr("Sarah +Dad"),
            ContactDetails.listLabel(
                groupTitle = "Sarah +Dad",
                name = "Sarah",
                address = sarah,
                display = formatted,
            ),
        )
    }

    @Test
    fun `a group of numbers keeps every number intact and in order`() {
        // The reported bug: in RTL "+12345678, +12345789" painted as
        // "8765431+, 98754321+". Each member is isolated, and an all-LTR list
        // is pinned as a whole so its order survives too.
        val title = "+12345678, +12345789"
        val label = ContactDetails.listLabel(title, "+12345678", "+12345678", "12345678")
        assertEquals(BidiText.groupTitle(title), label)
        assertTrue("the first number must be isolated", label.contains(BidiText.ltr("+12345678")))
        assertTrue("the second number must be isolated", label.contains(BidiText.ltr("+12345789")))
        assertTrue("the list order must be pinned", label.startsWith(BidiText.LRI.toString()))
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