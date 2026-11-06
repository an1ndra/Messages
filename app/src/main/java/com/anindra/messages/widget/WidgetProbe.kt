package com.anindra.messages.widget

import com.anindra.messages.data.Conversation

/**
 * One-line summary of what the widgets would render, logged under the
 * `WidgetProbe` tag so a regression script can assert on it from logcat
 * without reading a pixel.
 */
object WidgetProbe {

    fun line(
        conversations: List<Conversation>,
        unreadAtTop: Boolean,
        appLock: Boolean,
        privacy: Boolean
    ): String {
        val locked = WidgetData.isLocked(appLock, privacy)
        val inbox = if (locked) emptyList() else WidgetData.inbox(conversations, unreadAtTop)
        val list = if (locked) emptyList() else WidgetData.listRows(inbox, WidgetData.MAX_LIST_ROWS)
        val unread = if (locked) emptyList() else WidgetData.unreadRows(inbox, WidgetData.MAX_UNREAD_ROWS)
        val total = if (locked) 0 else WidgetData.unreadTotal(inbox)
        return "locked=$locked list=[${rows(list)}] unread=[${rows(unread)}] total=$total"
    }

    private fun rows(list: List<Conversation>): String =
        list.joinToString(",") { "${it.id}|${it.address}|${it.name}|${it.unreadCount}" }
}
