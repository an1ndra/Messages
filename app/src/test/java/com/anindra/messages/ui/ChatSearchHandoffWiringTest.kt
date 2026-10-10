package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home-search handoff must be consumed when the chat is left for contact
 * details.
 *
 * `chatSearchQuery` lives in `MainActivity` and is passed to every
 * `ChatScreen` instance. `ChatScreen` sits inside `AnimatedContent`, so a route
 * change disposes it — coming back built a *fresh* chat that re-read the same
 * query and replayed the highlight on the message. The query was never
 * consumed, only re-read.
 *
 * `AnimatedContent` disposal is not reproducible off-device, so this pins the
 * wiring that causes it: every route out of a chat clears the handoff.
 */
class ChatSearchHandoffWiringTest {
    private val source: String by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/com/anindra/messages/MainActivity.kt")
            if (candidate.isFile) return@lazy candidate.readText()
            dir = dir.parentFile
        }
        error("could not locate MainActivity.kt")
    }

    @Test
    fun openingContactDetailsClearsTheSearchHandoff() {
        val onOpenDetails = source.substringAfter("onOpenDetails = {")
        val body = onOpenDetails.substringBefore("},")
        assertTrue(
            "onOpenDetails must clear chatSearchQuery, otherwise returning from " +
                "details replays the highlight; got: ${body.trim()}",
            body.contains("chatSearchQuery = null"),
        )
    }

    @Test
    fun theHandoffIsStillAppliedWhenTheChatOpens() {
        // The clear must happen on the way *out*, never on the way in — clearing
        // it at open time would silently disable the whole feature.
        val callSite = source.substringAfter("else -> ChatScreen(")
        assertTrue(
            "ChatScreen must still receive the query when opened from search",
            callSite.substringBefore(")").contains("searchQuery = chatSearchQuery"),
        )
    }
}