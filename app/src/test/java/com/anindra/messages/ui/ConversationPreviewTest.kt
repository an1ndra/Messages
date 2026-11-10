package com.anindra.messages.ui

import com.anindra.messages.data.Conversation
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationPreviewTest {

    private fun convo(
        snippet: String = "hello",
        isMe: Boolean = false,
        draft: String = ""
    ) = Conversation(
        id = 1, address = "+15550001", name = "A", snippet = snippet,
        timestamp = 1, unreadCount = 0, isMe = isMe, draft = draft
    )

    private fun text(
        c: Conversation,
        hideLinks: Boolean = false,
        scheduled: String? = null
    ) = ConversationPreview.text(
        c, hideLinks, scheduled, "D: ", "S: ", "You: ",
        redact = { it.replace(Regex("https?://\\S+"), "[link]").trim() }
    )

    @Test
    fun draftWinsOverSnippet() {
        assertEquals("D: h", text(convo(draft = "h")))
    }

    @Test
    fun scheduledShowsWhenNoDraft() {
        assertEquals("S: trip", text(convo(), scheduled = "trip"))
    }

    @Test
    fun blankScheduledFallsThrough() {
        assertEquals("hello", text(convo(), scheduled = "  "))
    }

    @Test
    fun outgoingGetsYouPrefixOnly() {
        assertEquals("You: see you", text(convo(snippet = "see you", isMe = true)))
        assertEquals("see you", text(convo(snippet = "see you", isMe = false)))
    }

    @Test
    fun emptyOutgoingSnippetGetsNoPrefix() {
        assertEquals("", text(convo(snippet = "", isMe = true)))
    }

    @Test
    fun hideLinksStripsUrlsFromSnippetAndDraft() {
        assertEquals(
            "check [link]",
            text(convo(snippet = "check https://example.com"), hideLinks = true)
        )
        assertEquals(
            "D: go [link]",
            text(convo(draft = "go https://example.com"), hideLinks = true)
        )
    }
}
