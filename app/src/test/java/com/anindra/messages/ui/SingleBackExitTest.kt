package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * One Back from the inbox closes the app.
 *
 * The old gesture asked for a second press ("Press back again to exit") and the
 * first one looked like it did nothing. This anchors that in the source: the
 * inbox handler finishes the activity and the reminder string is gone from it.
 */
class SingleBackExitTest {

    private val source = File(
        "src/main/java/com/anindra/messages/ui/ConversationsScreen.kt"
    ).readText()

    @Test
    fun theInboxBackHandlerFinishesTheActivity() {
        val handler = source.substringAfter(
            "BackHandler(enabled = backHandled && !selectionActive && !searching && !showArchived)"
        ).substringBefore("BackHandler(")
        assertTrue(
            "the inbox Back handler should finish the activity:\n$handler",
            handler.contains("finish()")
        )
    }

    @Test
    fun thereIsNoPressBackAgainReminder() {
        assertFalse(
            "the inbox still asks for a second Back press",
            source.contains("conversations_press_back_exit")
        )
        assertFalse(
            "the inbox still tracks a double-press timestamp",
            source.contains("lastBackExitAt")
        )
    }
}
