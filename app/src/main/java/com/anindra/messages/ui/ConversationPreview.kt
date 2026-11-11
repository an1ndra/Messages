package com.anindra.messages.ui

import com.anindra.messages.data.Conversation
import com.anindra.messages.hideUrls

/**
 * The conversation-list preview line. The inbox row and both home-screen
 * widgets call this, so the draft / scheduled / "You:" rules and link hiding
 * cannot drift apart.
 *
 * [redact] is injectable because [hideUrls] relies on android.text.Linkify,
 * which does nothing under local unit tests.
 */
object ConversationPreview {
    fun text(
        conversation: Conversation,
        hideLinks: Boolean,
        scheduled: String?,
        draftPrefix: String,
        scheduledPrefix: String,
        yourPrefix: String,
        redact: (String) -> String = ::hideUrls
    ): String {
        val snippet = if (hideLinks) redact(conversation.snippet) else conversation.snippet
        val preview = when {
            conversation.draft.isNotBlank() ->
                draftPrefix + if (hideLinks) redact(conversation.draft) else conversation.draft
            !scheduled.isNullOrBlank() -> scheduledPrefix + scheduled
            conversation.isMe && snippet.isNotEmpty() -> yourPrefix + snippet
            else -> snippet
        }
        return BidiText.isolateNumberRuns(preview).text
    }
}
