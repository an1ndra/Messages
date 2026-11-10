package com.anindra.messages.widget

import com.anindra.messages.data.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDataTest {

    private fun convo(
        id: Long,
        unread: Int = 0,
        archived: Boolean = false,
        blocked: Boolean = false,
        pinned: Boolean = false,
        timestamp: Long = id
    ) = Conversation(
        id = id, address = "+1555000$id", name = "Name $id", snippet = "s$id",
        timestamp = timestamp, unreadCount = unread, isMe = false,
        archived = archived, blocked = blocked, pinned = pinned
    )

    private fun ids(list: List<Conversation>) = list.map { it.id }

    @Test
    fun inboxDropsArchivedAndBlocked() {
        val list = listOf(convo(1), convo(2, archived = true), convo(3, blocked = true))
        assertEquals(listOf(1L), ids(WidgetData.inbox(list, unreadAtTop = false)))
    }

    @Test
    fun unreadAtTopKeepsPinnedFirst() {
        val list = listOf(convo(1, unread = 0, timestamp = 30), convo(2, unread = 2, timestamp = 10))
        assertEquals(
            listOf(2L, 1L),
            ids(WidgetData.inbox(list, unreadAtTop = true))
        )
        assertEquals(listOf(1L, 2L), ids(WidgetData.inbox(list, unreadAtTop = false)))
    }

    @Test
    fun listRowsRespectTheCap() {
        val list = (1L..6L).map { convo(it) }
        assertEquals(4, WidgetData.listRows(list, 4).size)
        assertEquals(2, WidgetData.listRows(list, 2).size)
        assertEquals(0, WidgetData.listRows(list, 0).size)
    }

    @Test
    fun unreadRowsOnlyUnreadAndCappedAtThree() {
        val list = (1L..6L).map { convo(it, unread = if (it % 2 == 0L) 1 else 0) }
        assertEquals(listOf(2L, 4L, 6L), ids(WidgetData.unreadRows(list, 3)))
    }

    @Test
    fun unreadTotalSumsMessagesNotConversations() {
        assertEquals(5, WidgetData.unreadTotal(listOf(convo(1, unread = 2), convo(2, unread = 3), convo(3))))
    }

    @Test
    fun rowCountUsesFloorAndClamps() {
        assertEquals(1, WidgetData.rowCount(110))
        assertEquals(1, WidgetData.rowCount(127))
        assertEquals(1, WidgetData.rowCount(40))
        assertEquals(4, WidgetData.rowCount(400))
        assertEquals(1, WidgetData.rowCount(127, rowDp = 46, max = 3))
    }

    /** The card pads 6 dp top and bottom, so a row that ignores both is clipped. */
    @Test
    fun rowCountLeavesRoomForBothPaddings() {
        assertEquals(2, WidgetData.rowCount(138))
        assertEquals(1, WidgetData.rowCount(137))
    }

    /** The unread card keeps the New message pill at every size, so it must reserve it. */
    @Test
    fun unreadRowCountReservesTheNewMessagePillAndItsMargins() {
        assertEquals(1, WidgetData.unreadRowCount(127))
        assertEquals(2, WidgetData.unreadRowCount(255))
    }

    /** A bubble is exactly its label row plus its snippet, or one of them is clipped. */
    @Test
    fun unreadBubbleFitsItsLabelAndSnippet() {
        assertEquals(
            WidgetData.UNREAD_LABEL_ROW_DP + WidgetData.UNREAD_SNIPPET_DP,
            WidgetData.UNREAD_BUBBLE_HEIGHT_DP
        )
    }

    @Test
    fun lockedWhenEitherGateIsOn() {
        assertFalse(WidgetData.isLocked(false, false))
        assertTrue(WidgetData.isLocked(true, false))
        assertTrue(WidgetData.isLocked(false, true))
        assertTrue(WidgetData.isLocked(true, true))
    }

    /**
     * The inbox draws `PersonAvatar(convo.groupTitle.ifBlank { convo.address })`, so
     * the widget must key its avatar on exactly that: a group names, a 1:1 numbers.
     */
    @Test
    fun avatarKeyMatchesTheInboxAvatarKey() {
        assertEquals("Weekend plans", WidgetData.avatarKey("Weekend plans", "+15550001"))
        assertEquals("+15550001", WidgetData.avatarKey("", "+15550001"))
    }
}
