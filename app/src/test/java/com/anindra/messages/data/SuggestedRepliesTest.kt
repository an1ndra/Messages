package com.anindra.messages.data

import com.anindra.messages.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #483: the one-tap replies have to be obviously right or absent. A yes/no
 * question should offer Yes and No, a plan should offer the two answers people
 * actually give, and a plain statement should offer nothing at all.
 */
class SuggestedRepliesTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private fun src(path: String) = File(main, path).readText()

    private fun message(
        id: Long = 1,
        body: String = "Are we still on for dinner?",
        isMe: Boolean = false,
        mediaType: String = "text"
    ) = Message(
        id = id,
        conversationId = 1,
        body = body,
        timestamp = id,
        isMe = isMe,
        status = "received",
        mediaType = mediaType
    )

    @Test
    fun aYesNoQuestionOffersYesAndNo() {
        assertEquals(
            listOf(ReplySuggestion.YES, ReplySuggestion.NO),
            SuggestedReplies.forMessage("Are we still on for dinner?")
        )
        assertEquals(
            listOf(ReplySuggestion.YES, ReplySuggestion.NO),
            SuggestedReplies.forMessage("Can you send me the address?")
        )
        assertEquals(
            "a question mark alone is not a yes/no question",
            listOf(ReplySuggestion.SURE, ReplySuggestion.LET_ME_CHECK),
            SuggestedReplies.forMessage("What did the doctor say?")
        )
    }

    @Test
    fun theOtherEverydayShapesGetTheRightAnswers() {
        assertEquals(
            listOf(ReplySuggestion.YOURE_WELCOME, ReplySuggestion.NO_PROBLEM),
            SuggestedReplies.forMessage("Thanks for the help!")
        )
        assertEquals(
            listOf(ReplySuggestion.ON_MY_WAY, ReplySuggestion.ALMOST_THERE),
            SuggestedReplies.forMessage("I'm outside")
        )
        assertEquals(
            listOf(ReplySuggestion.CALLING_NOW, ReplySuggestion.CANT_TALK),
            SuggestedReplies.forMessage("Call me when you can")
        )
        assertEquals(
            listOf(ReplySuggestion.HELLO, ReplySuggestion.GOT_IT),
            SuggestedReplies.forMessage("Hey there")
        )
        assertEquals(
            listOf(ReplySuggestion.SOUNDS_GOOD, ReplySuggestion.CANT_MAKE_IT),
            SuggestedReplies.forMessage("Dinner tomorrow?")
        )
    }

    @Test
    fun aPlainStatementGetsNoSuggestionAtAll() {
        assertTrue(SuggestedReplies.forMessage("I'll be at the office until six").isEmpty())
        assertTrue(SuggestedReplies.forMessage("ok").isEmpty())
        assertTrue(SuggestedReplies.forMessage("").isEmpty())
        assertTrue(SuggestedReplies.forMessage(null).isEmpty())
    }

    @Test
    fun aThreadOnlySuggestsWhileItIsWaitingOnUs() {
        assertEquals(
            listOf(ReplySuggestion.YES, ReplySuggestion.NO),
            SuggestedReplies.forThread(listOf(message()), enabled = true)
        )
        assertTrue(
            "the newest message being ours means the thread is answered",
            SuggestedReplies.forThread(
                listOf(message(id = 2, body = "See you at 7", isMe = true), message()),
                enabled = true
            ).isEmpty()
        )
        assertTrue(
            "a photo is not a question",
            SuggestedReplies.forThread(
                listOf(message(mediaType = "image", body = "")), enabled = true
            ).isEmpty()
        )
        assertTrue(
            "the switch takes the whole thing away",
            SuggestedReplies.forThread(listOf(message()), enabled = false).isEmpty()
        )
        assertTrue(
            "an empty thread has nothing to answer",
            SuggestedReplies.forThread(emptyList(), enabled = true).isEmpty()
        )
    }

    @Test
    fun everySuggestionIsItsOwnTranslatedString() {
        val labels = ReplySuggestion.values().map { it.label }
        assertEquals("two suggestions sharing one string would read the same",
            labels.size, labels.toSet().size)
        assertEquals(R.string.suggestion_yes, ReplySuggestion.YES.label)
        assertTrue(ReplySuggestion.values().size <= 20)
        assertTrue(
            "the composer caps the row at three chips",
            SuggestedReplies.forMessage("Are we still on for dinner?").size <= SuggestedReplies.MAX
        )
    }

    @Test
    fun theComposerAndTheNotificationBothAskTheSameFunction() {
        val chat = src("java/com/anindra/messages/ui/ChatScreen.kt")
        assertTrue(
            "chips only while the field is empty, or a suggestion would replace a draft",
            Regex("""suggestions = if \(draft\.isBlank\(\)\) \{[\s\S]{0,200}?SuggestedReplies\.forThread\(""")
                .containsMatchIn(chat)
        )
        assertTrue(
            "picking a chip fills the composer instead of sending",
            chat.contains("onSuggestion = { draft = context.getString(it.label) }")
        )
        val sms = src("java/com/anindra/messages/sms/SmsSupport.kt")
        assertTrue(
            "the notification reply offers the same choices, and the switch gates them",
            Regex("""suggestedRepliesEnabled\) emptyArray\(\)[\s\S]{0,200}?SuggestedReplies\.forMessage\(body\)""")
                .containsMatchIn(sms)
        )
        assertTrue(sms.contains(".setChoices(replyChoices)"))
        val store = src("java/com/anindra/messages/data/SettingsStore.kt")
        assertTrue(
            "a wrong suggestion is worse than none, so the switch ships on but exists",
            Regex("""var suggestedRepliesEnabled: Boolean\s+get\(\) = prefs\.getBoolean\(KEY_SUGGESTED_REPLIES, true\)""")
                .containsMatchIn(store)
        )
    }
}
