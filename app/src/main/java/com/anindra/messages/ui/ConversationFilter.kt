package com.anindra.messages.ui

import com.anindra.messages.data.Conversation

/** Which folder a conversation is shown in on the home screen. */
enum class ConversationView { INBOX, ARCHIVED, SPAM_BLOCKED }

/** Mirrors Google Messages: blocked conversations leave the inbox and archives,
 *  and live only in the "Spam & blocked" folder. */
fun matchesView(convo: Conversation, view: ConversationView): Boolean = when (view) {
    ConversationView.INBOX -> !convo.blocked && !convo.archived
    ConversationView.ARCHIVED -> !convo.blocked && convo.archived
    ConversationView.SPAM_BLOCKED -> convo.blocked
}
