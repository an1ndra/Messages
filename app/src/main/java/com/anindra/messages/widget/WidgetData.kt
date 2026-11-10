package com.anindra.messages.widget

import com.anindra.messages.data.Conversation
import com.anindra.messages.data.ConversationList

/**
 * What each home-screen widget shows, kept pure so the selection rules are
 * unit testable. Delegates to [ConversationList] for ordering and filtering so
 * the widget and the inbox cannot disagree about either.
 */
object WidgetData {

    const val MAX_LIST_ROWS = 4
    const val MAX_UNREAD_ROWS = 3
    const val HEADER_HEIGHT_DP = 34
    const val ROW_HEIGHT_DP = 44
    const val VERTICAL_PADDING_DP = 8
    const val HORIZONTAL_PADDING_DP = 14
    const val AVATAR_DP = 30
    const val COMPOSE_BUTTON_DP = 28
    const val UNREAD_LABEL_ROW_DP = 36
    const val UNREAD_SNIPPET_DP = 32
    const val UNREAD_BUBBLE_HEIGHT_DP = UNREAD_LABEL_ROW_DP + UNREAD_SNIPPET_DP
    const val NEW_MESSAGE_PILL_HEIGHT_DP = 34
    const val NEW_MESSAGE_PILL_MARGIN_DP = 6

    /**
     * Rows that fit in [heightDp], after the header, both paddings and anything
     * else the card keeps visible ([reserveDp]). Never 0.
     */
    fun rowCount(
        heightDp: Int,
        rowDp: Int = ROW_HEIGHT_DP,
        max: Int = MAX_LIST_ROWS,
        reserveDp: Int = 0
    ): Int = (
        (heightDp - HEADER_HEIGHT_DP - 2 * VERTICAL_PADDING_DP - reserveDp) / rowDp
        ).coerceIn(1, max)

    /** The conversations a widget may show: blocked and archived dropped, then ordered. */
    fun inbox(conversations: List<Conversation>, unreadAtTop: Boolean): List<Conversation> =
        ConversationList.sort(
            ConversationList.filter(conversations, showArchived = false, query = "") { it.snippet },
            unreadAtTop,
            showArchived = false
        )

    fun listRows(inbox: List<Conversation>, limit: Int): List<Conversation> =
        inbox.take(limit.coerceAtLeast(0))

    /** Bubbles that fit above the New message pill and its margins. */
    fun unreadRowCount(heightDp: Int): Int = rowCount(
        heightDp,
        rowDp = UNREAD_BUBBLE_HEIGHT_DP,
        max = MAX_UNREAD_ROWS,
        reserveDp = NEW_MESSAGE_PILL_HEIGHT_DP + 2 * NEW_MESSAGE_PILL_MARGIN_DP
    )

    fun unreadRows(inbox: List<Conversation>, limit: Int): List<Conversation> =
        inbox.filter { it.unreadCount > 0 }.take(limit.coerceAtLeast(0))

    /** Messages waiting, not conversations holding them. */
    fun unreadTotal(inbox: List<Conversation>): Int = inbox.sumOf { it.unreadCount }

    fun isLocked(appLock: Boolean, privacy: Boolean): Boolean = appLock || privacy

    /**
     * The key the inbox draws an avatar for: `PersonAvatar(convo.groupTitle.ifBlank { convo.address })`.
     * A group is keyed by its title, a one-to-one by the number.
     */
    fun avatarKey(groupTitle: String, address: String): String =
        groupTitle.ifBlank { address }
}
