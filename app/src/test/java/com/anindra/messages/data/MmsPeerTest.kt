package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A group MMS has several To/From entries. Attribution needs the *sender*, not
 * a single participant, or every group MMS used to be discarded unread.
 */
class MmsPeerTest {

    private fun from(value: String) = MmsSupport.Address(137, value)
    private fun to(value: String) = MmsSupport.Address(151, value)
    private val token = MmsSupport.Address(137, "insert-address-token")

    @Test
    fun oneToOneIncomingTakesTheSender() {
        val peer = MmsSupport.peer(1, listOf(from("+15558887777"), to("+15551112222")), 1)
        assertEquals("+15558887777", peer)
    }

    @Test
    fun groupIncomingTakesTheSenderNotARecipient() {
        // Three recipients, one sender: the sender is who the message is from,
        // and who a group reply must be attributed to.
        val peer = MmsSupport.peer(
            1,
            listOf(from("+15558887777"), to("+15551112222"), to("+15553334444")),
            3
        )
        assertEquals("+15558887777", peer)
    }

    @Test
    fun incomingIgnoresTheAddressTokenPlaceholder() {
        val peer = MmsSupport.peer(
            1,
            listOf(token, from("+15558887777"), to("+15551112222")),
            2
        )
        assertEquals("+15558887777", peer)
    }

    @Test
    fun oneToOneOutgoingTakesTheRecipient() {
        val peer = MmsSupport.peer(2, listOf(to("+15558887777")), 1)
        assertEquals("+15558887777", peer)
    }

    @Test
    fun groupOutgoingTakesTheFirstRecipient() {
        val peer = MmsSupport.peer(
            2,
            listOf(to("+15558887777"), to("+15551112222")),
            2
        )
        assertEquals("+15558887777", peer)
    }

    @Test
    fun aGroupWithNoSenderIsNotAttributed() {
        // Recipients but nobody to attribute it to: better dropped than filed
        // against the wrong person.
        assertNull(MmsSupport.peer(1, listOf(to("+15551112222"), to("+15553334444")), 2))
    }

    @Test
    fun unverifiedAddressTypesAreRejected() {
        // 140 is the BCC field, which must never be treated as a participant.
        val bcc = MmsSupport.Address(140, "+15559998888")
        assertNull(MmsSupport.peer(1, listOf(from("+15558887777"), bcc), 1))
    }

    @Test
    fun nonPhoneRecipientsAreRejected() {
        // An alphanumeric sender ID cannot be routed to a conversation.
        assertNull(MmsSupport.peer(1, listOf(from("A1 SRB"), to("+15551112222")), 1))
    }

    @Test
    fun participantsCollectsEveryDistinctRecipient() {
        val list = listOf(to("+15551112222"), to("+15553334444"), to("+15551112222"))
        assertEquals(listOf("+15551112222", "+15553334444"), MmsSupport.participants(list))
    }
}
