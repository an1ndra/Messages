package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a provider MMS thread belongs (#448).
 *
 * The provider groups a group MMS on its participants, and the same rule this
 * replaces routed a group by its sender — which put every group a person was in
 * into that person's private chat. These pin the replacement: the thread id
 * first, the member set second, and a single-recipient chat is never a group.
 */
class ThreadRoutingTest {

    private fun candidate(id: Long, threadId: Long, members: List<String>) =
        ThreadRouting.Candidate(id, threadId, members)

    private val alex = "+15551110001"
    private val carlos = "+15551110002"
    private val dana = "+15551110003"

    @Test
    fun theThreadIdWinsOverEverythingElse() {
        val route = ThreadRouting.route(
            threadId = 42,
            participants = listOf(alex, carlos, dana),
            candidates = listOf(
                candidate(7, 41, listOf(alex, carlos)),
                candidate(9, 42, listOf(alex, carlos, dana))
            )
        )
        assertEquals(ThreadRouting.Route.Adopt(9, emptyList()), route)
    }

    @Test
    fun twoGroupsThatShareASenderStayApart() {
        // The reported failure: Alex is in two groups, so the second one used to
        // be filed into Alex's chat with the first. Thread ids separate them.
        val first = ThreadRouting.route(
            threadId = 100, participants = listOf(alex, carlos),
            candidates = listOf(candidate(1, 100, listOf(alex, carlos)))
        )
        val second = ThreadRouting.route(
            threadId = 200, participants = listOf(alex, dana),
            candidates = listOf(candidate(1, 100, listOf(alex, carlos)))
        )
        assertEquals(ThreadRouting.Route.Adopt(1, emptyList()), first)
        assertTrue(second is ThreadRouting.Route.Create)
        assertEquals(listOf(alex, dana), (second as ThreadRouting.Route.Create).members)
    }

    @Test
    fun aGroupStartedHereIsAdoptedByItsMemberSet() {
        // A group created in this app before the platform wrote a thread: the
        // same people is the same group, and its history must survive.
        val route = ThreadRouting.route(
            threadId = 77, participants = listOf(alex, carlos),
            candidates = listOf(candidate(5, 0, listOf(carlos, alex)))
        )
        assertEquals(ThreadRouting.Route.Adopt(5, emptyList()), route)
    }

    @Test
    fun aMemberSetThatDiffersIsNotTheSameGroup() {
        val route = ThreadRouting.route(
            threadId = 77, participants = listOf(alex, carlos),
            candidates = listOf(candidate(5, 0, listOf(alex, dana)))
        )
        assertEquals(ThreadRouting.Route.Create(listOf(alex, carlos)), route)
    }

    @Test
    fun aThreadWithSomeoneNewAppendsThemInsteadOfStartingOver() {
        val route = ThreadRouting.route(
            threadId = 42, participants = listOf(alex, carlos, dana),
            candidates = listOf(candidate(9, 42, listOf(alex, carlos)))
        )
        assertEquals(ThreadRouting.Route.Adopt(9, listOf(dana)), route)
    }

    @Test
    fun aSingleRecipientChatIsNeverAdoptedByMemberSet() {
        // One person is not a group, even when the thread names only them.
        val route = ThreadRouting.route(
            threadId = 0, participants = listOf(alex),
            candidates = listOf(candidate(3, 0, listOf(alex)))
        )
        assertEquals(ThreadRouting.Route.Create(listOf(alex)), route)
    }

    @Test
    fun aGroupConversationIsNeverAdoptedByASingleRecipientThread() {
        val route = ThreadRouting.route(
            threadId = 0, participants = listOf(alex, carlos),
            candidates = listOf(candidate(3, 0, listOf(alex)))
        )
        assertEquals(ThreadRouting.Route.Create(listOf(alex, carlos)), route)
    }

    @Test
    fun formattingAndOrderDoNotMatter() {
        // Addresses are canonicalised by the caller, so only order is left here.
        assertTrue(ThreadRouting.sameMembers(listOf(alex, carlos), listOf(carlos, alex)))
        assertTrue(ThreadRouting.sameMembers(listOf(" $alex ", carlos), listOf(carlos, alex)))
    }

    @Test
    fun duplicatesAndBlanksCollapseToThePeopleInTheThread() {
        val route = ThreadRouting.route(
            threadId = 0,
            participants = listOf(alex, alex, "", carlos),
            candidates = emptyList()
        )
        assertEquals(ThreadRouting.Route.Create(listOf(alex, carlos)), route)
    }

    @Test
    fun anExistingThreadIsPreferredOverAnEarlierDuplicate() {
        val route = ThreadRouting.route(
            threadId = 42, participants = listOf(alex, carlos),
            candidates = listOf(
                candidate(2, 42, listOf(alex, carlos)),
                candidate(1, 42, listOf(alex, carlos))
            )
        )
        assertEquals(ThreadRouting.Route.Adopt(2, emptyList()), route)
    }

    @Test
    fun withoutAThreadTheOnlyMatchingGroupIsTaken() {
        val route = ThreadRouting.route(
            threadId = 0, participants = listOf(alex, carlos),
            candidates = emptyList()
        )
        assertEquals(ThreadRouting.Route.Create(listOf(alex, carlos)), route)
    }
}
