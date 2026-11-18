package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #480: a contact leaves as a vCard body the receiving app can import. */
class ContactShareTest {

    @Test
    fun theCardIsVCard30() {
        val card = ContactShare.vCard("Dad", "+1555771010")
        assertTrue(card.startsWith("BEGIN:VCARD"))
        assertTrue(card.contains("VERSION:3.0"))
        assertTrue(card.contains("FN:Dad"))
        assertTrue(card.contains("TEL;TYPE=CELL:+1555771010"))
        assertTrue(card.endsWith("END:VCARD"))
    }

    @Test
    fun anUnnamedContactFallsBackToTheNumber() {
        val card = ContactShare.vCard("   ", "+1555771010")
        assertTrue(card.contains("FN:+1555771010"))
    }

    @Test
    fun numbersThatCannotBeSentAreRefused() {
        assertTrue(ContactShare.isSendable("+1555771010"))
        assertTrue(ContactShare.isSendable("555 7710"))
        assertFalse(ContactShare.isSendable(""))
        assertFalse(ContactShare.isSendable("12"))
        assertFalse(ContactShare.isSendable("555\n7710"))
    }

    @Test
    fun theCardHasNoStrayBlankLine() {
        // A blank line before END:VCARD makes some importers drop the phone row.
        val card = ContactShare.vCard("Dad", "+1555771010")
        assertEquals(0, Regex("\n\n").findAll(card).count())
    }
}
