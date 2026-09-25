package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDetailsTest {

    @Test
    fun savedContactShowsNameWithNumberBeneath() {
        assertTrue(ContactDetails.isKnown("Sarah", "+15551230010"))
        assertEquals("Sarah", ContactDetails.title("Sarah", "+15551230010", "(555) 123-0010"))
        assertEquals(
            "(555) 123-0010",
            ContactDetails.subtitle("Sarah", "+15551230010", "(555) 123-0010")
        )
    }

    @Test
    fun unknownSenderShowsNumberAsTitleAndNoSubtitle() {
        assertFalse(ContactDetails.isKnown("+15551230010", "+15551230010"))
        assertEquals(
            "(555) 123-0010",
            ContactDetails.title("+15551230010", "+15551230010", "(555) 123-0010")
        )
        assertNull(ContactDetails.subtitle("+15551230010", "+15551230010", "(555) 123-0010"))
    }

    @Test
    fun alphanumericSenderIdStaysAsTitle() {
        assertEquals("A1 SRB", ContactDetails.title("A1 SRB", "A1 SRB", "A1 SRB"))
        assertNull(ContactDetails.subtitle("A1 SRB", "A1 SRB", "A1 SRB"))
    }
}
