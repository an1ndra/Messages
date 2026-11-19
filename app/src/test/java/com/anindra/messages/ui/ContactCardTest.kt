package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The two things an attached contact and an attached location turn into before
 * they reach the network: a vCard and a maps link.
 */
class ContactCardTest {

    @Test
    fun aShortOrEmptyNumberIsNotSendable() {
        assertFalse(ContactCard.isSendable(""))
        assertFalse(ContactCard.isSendable("12345"))
        assertFalse(ContactCard.isSendable("no digits here"))
    }

    @Test
    fun aDialableNumberIsSendable() {
        assertTrue(ContactCard.isSendable("+1 555 333 4444"))
        assertTrue(ContactCard.isSendable("5550100"))
    }

    @Test
    fun theCardIsVersionThreeWithOneCellNumber() {
        val card = ContactCard.vCard("Alex Rivera", "+15553334444")

        assertTrue(card.startsWith("BEGIN:VCARD\n"))
        assertTrue(card.trimEnd().endsWith("END:VCARD"))
        assertTrue(card.contains("VERSION:3.0"))
        assertTrue(card.contains("FN:Alex Rivera"))
        assertTrue(card.contains("TEL;TYPE=CELL:+15553334444"))
    }

    @Test
    fun aNamelessContactFallsBackToItsNumber() {
        val card = ContactCard.vCard("   ", "+15553334444")

        assertTrue(card.contains("FN:+15553334444"))
    }

    @Test
    fun aLineBreakInTheNameCannotBreakTheCard() {
        // A newline inside a property ends it, so a two-line name would leave a
        // malformed card behind.
        val card = ContactCard.vCard("Alex\nRivera", "+15553334444")

        assertEquals(5, card.trimEnd().lines().size)
        assertTrue(card.contains("FN:Alex Rivera"))
    }

    @Test
    fun theGeoLinkCarriesBothCoordinates() {
        assertEquals(
            "https://maps.google.com/?q=28.613939,77.209023",
            ContactCard.geoLink(28.613939, 77.209023)
        )
    }

    @Test
    fun negativeCoordinatesSurviveFormatting() {
        assertEquals(
            "https://maps.google.com/?q=-33.868820,151.209290",
            ContactCard.geoLink(-33.86882, 151.20929)
        )
    }

    @Test
    fun theLinkDoesNotFollowACommaDecimalLocale() {
        // A German phone must not produce "28,613939" — the link would land in
        // the wrong place, or nowhere.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(
                "https://maps.google.com/?q=28.613939,77.209023",
                ContactCard.geoLink(28.613939, 77.209023)
            )
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun aFixIsUsableOnlyOnEarthAndNotAtNullIsland() {
        assertTrue(ContactCard.isUsable(28.6, 77.2))
        assertTrue(ContactCard.isUsable(-33.8, 151.2))
        assertFalse("latitude out of range", ContactCard.isUsable(91.0, 0.0))
        assertFalse("longitude out of range", ContactCard.isUsable(0.0, 181.0))
        assertFalse("no fix at all", ContactCard.isUsable(0.0, 0.0))
    }
}
