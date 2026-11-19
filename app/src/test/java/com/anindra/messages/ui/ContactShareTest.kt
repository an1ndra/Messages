package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A contact leaves as a vCard card, attached to an MMS.
 *
 * It used to leave as the card's text pasted into the message body, which the
 * receiving phone showed as a wall of `BEGIN:VCARD` properties: a text message,
 * not a contact. The card is now a file the send path reads, so its shape and
 * the name/number the bubble reads back out of it both matter.
 */
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
        assertFalse("fewer than seven digits", ContactShare.isSendable("12345"))
        assertFalse(ContactShare.isSendable(""))
        assertFalse(ContactShare.isSendable("12"))
        assertFalse("a line break would split the property", ContactShare.isSendable("555\n7710"))
    }

    @Test
    fun theCardHasNoStrayBlankLine() {
        // A blank line before END:VCARD makes some importers drop the phone row.
        val card = ContactShare.vCard("Dad", "+1555771010")
        assertEquals(0, Regex("\n\n").findAll(card).count())
    }

    @Test
    fun aLineBreakInTheNameCannotBreakTheCard() {
        // A newline inside a property ends it, so a two-line name would leave a
        // malformed card behind.
        val card = ContactShare.vCard("Alex\nRivera", "+15553334444")
        assertEquals(6, card.trimEnd().lines().size)
        assertTrue(card.contains("FN:Alex Rivera"))
    }

    @Test
    fun aSemicolonInTheNameCannotSplitTheProperty() {
        val card = ContactShare.vCard("Rivera; Alex", "+15553334444")
        assertTrue(card.contains("FN:Rivera  Alex"))
    }

    @Test
    fun theCardIsDeclaredAsAVCardOnTheWire() {
        assertEquals("text/x-vcard", ContactShare.MIME)
        assertEquals("contact.vcf", ContactShare.FILE_NAME)
    }

    @Test
    fun theCardReadsBackAsTheNameAndNumber() {
        val parsed = ContactShare.parse(ContactShare.vCard("Dad", "+1555771010"))
        assertEquals("Dad" to "+1555771010", parsed)
    }

    @Test
    fun aCardWithNoNumberReadsBackAsNothing() {
        assertNull(ContactShare.parse("BEGIN:VCARD\nVERSION:3.0\nFN:Dad\nEND:VCARD"))
        assertNull(ContactShare.parse(""))
    }

    @Test
    fun aNamelessCardIsNamedByItsNumber() {
        val card = "BEGIN:VCARD\nVERSION:3.0\nTEL;TYPE=CELL:+1555771010\nEND:VCARD"
        assertEquals("+1555771010" to "+1555771010", ContactShare.parse(card))
    }
}
