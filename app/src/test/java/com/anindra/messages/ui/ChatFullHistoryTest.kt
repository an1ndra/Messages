package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Issue #406 follow-up: the chat used to page its history in 40/120-row chunks,
 * so scrolling up stopped at every page and search walked toward a hit in
 * fixed steps. The whole thread is now loaded on open — one query, no pager —
 * which removes the chunk stops, the prepend anchor and the open-flicker burst
 * in one go.
 *
 * These assertions scan the source because the behaviour is a composition-level
 * decision: there is no pure function left to call.
 */
class ChatFullHistoryTest {

    private fun source(path: String): String {
        val resource = javaClass.classLoader.getResource("com/anindra/messages/$path")
        if (resource != null) return File(resource.file).readText()
        val candidates = listOf(
            "app/src/main/java/com/anindra/messages/$path",
            "../app/src/main/java/com/anindra/messages/$path",
            "../../app/src/main/java/com/anindra/messages/$path"
        )
        return candidates.firstNotNullOfOrNull { candidate ->
            File(candidate).takeIf { it.exists() }?.readText()
        } ?: error("$path not found")
    }

    private val chatScreen by lazy { source("ui/ChatScreen.kt") }

    @Test
    fun theChatAsksForTheWholeThread() {
        assertTrue(
            "messages must be requested without a window limit",
            Regex("""vm\.messages\(\s*conversationId\s*\)""").containsMatchIn(chatScreen)
        )
        assertFalse(
            "a page limit in the message request reintroduces chunked history",
            Regex("""vm\.messages\([^)]*limit\s*=""").containsMatchIn(chatScreen)
        )
    }

    @Test
    fun noPagingStateIsLeftBehind() {
        listOf("pageLimit", "pendingEarlier", "loadingEarlier", "grownPageLimit").forEach { name ->
            assertFalse(
                "$name is paging machinery; the whole thread is loaded on open",
                Regex("""\b$name\b""").containsMatchIn(chatScreen)
            )
        }
    }

    @Test
    fun theRepositoryDefaultsToEverything() {
        val repository = source("data/Repository.kt")
        assertTrue(
            "messages() must default to an unbounded window",
            Regex("""fun messages\([^)]*limit:\s*Int\s*=\s*Int\.MAX_VALUE""")
                .containsMatchIn(repository)
        )
    }
}
