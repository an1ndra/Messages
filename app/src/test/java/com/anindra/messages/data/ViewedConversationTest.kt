package com.anindra.messages.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ViewedConversationTest {

    private val repositorySrc: String = run {
        val resource = javaClass.classLoader.getResource("com/anindra/messages/data/Repository.kt")
        if (resource != null) {
            File(resource.file).readText()
        } else {
            val candidates = listOf(
                "app/src/main/java/com/anindra/messages/data/Repository.kt",
                "../app/src/main/java/com/anindra/messages/data/Repository.kt",
                "../../app/src/main/java/com/anindra/messages/data/Repository.kt"
            )
            candidates.firstNotNullOfOrNull { path ->
                File(path).takeIf { it.exists() }?.readText()
            } ?: error("Repository.kt not found")
        }
    }

    @Test
    fun receiveMessageGuardsUnreadWithViewedConversationId() {
        assertTrue(repositorySrc.contains("markUnread && convoId != viewedConversationId"))
    }

    @Test
    fun importProviderMmsGuardsUnreadWithViewedConversationId() {
        assertTrue(repositorySrc.contains("!message.isMe && !message.read && cid != viewedConversationId"))
    }

    @Test
    fun importProviderMmsRefreshesSnippetsAfterLoop() {
        assertTrue(repositorySrc.contains("refreshed.forEach { refreshConversationSnippetFor(it) }"))
    }

    @Test
    fun repositoryExposesViewedConversationId() {
        assertTrue(repositorySrc.contains("@Volatile var viewedConversationId: Long = 0L"))
    }
}
