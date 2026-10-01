package com.anindra.messages.ui

import com.anindra.messages.data.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardTargetsTest {

    private fun convo(address: String, name: String, timestamp: Long = 0L) = Conversation(
        id = timestamp,
        address = address,
        name = name,
        snippet = "",
        timestamp = timestamp,
        unreadCount = 0,
        isMe = false
    )

    private val contacts = listOf(
        Contact("Alex", "+1555771010"),
        Contact("Bank", "+1555775020")
    )

    @Test
    fun everyContactIsOfferedNotJustTheAlphabeticallyFirstFew() {
        // The picker used to stop at 60 targets. Because the list is ordered by
        // name, that silently hid everyone past the cut-off: on a phone with
        // many contacts you could scroll no further than the names starting
        // with A, and had to know to search instead.
        val many = (1..250).map { Contact("Person $it", "+1555%07d".format(it)) }
        val rows = ForwardTargets.build(many, emptyList(), "")
        assertEquals(250, rows.size)
        assertEquals("Person 250", rows.last().name)
    }

    @Test
    fun aLongListStillNarrowsWithTheSearchBox() {
        val many = (1..250).map { Contact("Person $it", "+1555%07d".format(it)) }
        val rows = ForwardTargets.build(many, emptyList(), "Person 199")
        assertEquals(listOf("Person 199"), rows.map { it.name })
    }

    @Test
    fun contactsComeFirstAndConversationsFillInTheRest() {
        val rows = ForwardTargets.build(contacts, listOf(convo("+16505551212", "Carla", 5L)), "")
        assertEquals(listOf("+1555771010", "+1555775020", "+16505551212"), rows.map { it.number })
    }

    @Test
    fun aConversationAlreadyInContactsIsNotListedTwice() {
        val rows = ForwardTargets.build(contacts, listOf(convo("+1555771010", "Alex", 5L)), "")
        assertEquals(1, rows.count { ForwardTargets.digits(it.number) == "1555771010" })
        assertEquals("Alex", rows.first { it.number == "+1555771010" }.name)
    }

    @Test
    fun conversationsAreOfferedMostRecentFirst() {
        val convos = listOf(convo("+15551110001", "Old", 1L), convo("+15551110002", "New", 9L))
        val rows = ForwardTargets.build(emptyList(), convos, "")
        assertEquals(listOf("New", "Old"), rows.map { it.name })
    }

    @Test
    fun aTypedNumberNobodyMatchesIsStillForwardable() {
        val rows = ForwardTargets.build(contacts, emptyList(), "+15559998888")
        assertEquals("+15559998888", rows.first().number)
        assertNull(rows.first().name)
    }

    @Test
    fun aTypedNumberThatMatchesAContactDoesNotDuplicateIt() {
        val rows = ForwardTargets.build(contacts, emptyList(), "1555771010")
        assertEquals(1, rows.size)
        assertEquals("+1555771010", rows.first().number)
    }

    @Test
    fun tooFewDigitsIsNotTreatedAsANumber() {
        val rows = ForwardTargets.build(emptyList(), emptyList(), "12")
        assertTrue(rows.isEmpty())
    }

    @Test
    fun searchMatchesOnNameOrDigits() {
        assertEquals(listOf("Bank"), ForwardTargets.build(contacts, emptyList(), "ban").map { it.name })
        assertEquals(
            listOf("+1555771010"),
            ForwardTargets.build(contacts, emptyList(), "577 1010").map { it.number }
        )
    }

    @Test
    fun formattingOfANumberDoesNotChangeItsIdentity() {
        val rows = ForwardTargets.build(listOf(Contact("Ann", "+1 (555) 777-1010")), emptyList(), "")
        assertEquals("15557771010", ForwardTargets.digits(rows.single().number))
    }

    @Test
    fun theListStaysBoundedOnAPhoneWithThousandsOfContacts() {
        // This used to assert a hard cap of 60. Because targets are ordered by
        // name, the cap hid everyone past the cut-off and left a phone with many
        // contacts able to scroll no further than the names starting with A.
        // The picker is a LazyColumn, so bounding the list bought nothing; what
        // matters now is that a big address book stays correct.
        val many = (1..500).map { Contact("Person $it", "+1555000%04d".format(it)) }
        val rows = ForwardTargets.build(many, emptyList(), "")
        assertEquals(500, rows.size)
        assertEquals(500, rows.map { it.number }.distinct().size)
        assertTrue(ForwardTargets.build(many, emptyList(), "Person 499").isNotEmpty())
    }
}
