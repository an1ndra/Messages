package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tapping a contact card offers the two things a phone does with a vCard: put
 * it in the contacts app, or save the file.
 *
 * The insert is only worth offering when there is a number to create the
 * contact from; a card without one would open an empty editor.
 */
class ContactActionsTest {

    @Test
    fun aCardWithANumberFillsTheNewContact() {
        assertEquals(
            "Alex Rivera" to "+15553334444",
            ContactActions.insertValues("Alex Rivera", "+15553334444")
        )
    }

    @Test
    fun aNamelessCardStillCarriesItsNumber() {
        assertEquals("" to "+15553334444", ContactActions.insertValues(null, "  +15553334444  "))
    }

    @Test
    fun aCardWithNoNumberCannotBecomeAContact() {
        assertNull(ContactActions.insertValues("Alex", null))
        assertNull(ContactActions.insertValues("Alex", "   "))
    }
}
