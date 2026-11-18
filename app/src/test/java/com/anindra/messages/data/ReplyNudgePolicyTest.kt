package com.anindra.messages.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #484: a message that was read and then forgotten is the one that never comes
 * back — unread counts only cover messages nobody opened. A nudge is a reminder,
 * so it has to be narrow: only a thread whose newest message is inbound, already
 * read and old enough, and never one that asked not to be disturbed.
 */
class ReplyNudgePolicyTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private fun src(path: String) = File(main, path).readText()

    private val hour = 3_600_000L
    private val now = 1_000_000_000_000L

    private fun row(
        newest: Long = now - 30 * hour,
        lastIsMine: Boolean = false,
        unread: Int = 0,
        archived: Boolean = false,
        blocked: Boolean = false,
        deleted: Boolean = false
    ) = ReplyNudgeRow(
        conversationId = 7,
        address = "+15551230000",
        snippet = "Are we still on for dinner?",
        newest = newest,
        lastIsMine = lastIsMine,
        unread = unread,
        archived = archived,
        blocked = blocked,
        deleted = deleted
    )

    @Test
    fun aReadUnansweredMessageBecomesDueAfterTheInterval() {
        val day = ReplyNudgePolicy.intervalMillis(24)
        assertFalse(
            "a message from an hour ago is not forgotten yet",
            ReplyNudgePolicy.shouldNudge(row(newest = now - hour), false, 0, now, day)
        )
        assertTrue(
            "a day-old read message is exactly the case this exists for",
            ReplyNudgePolicy.shouldNudge(row(newest = now - 25 * hour), false, 0, now, day)
        )
    }

    @Test
    fun anAnsweredThreadIsNeverNudged() {
        assertFalse(
            "the newest message being ours is the definition of answered",
            ReplyNudgePolicy.shouldNudge(row(lastIsMine = true), false, 0, now, hour)
        )
    }

    @Test
    fun anUnreadThreadIsLeftToItsOwnNotification() {
        assertFalse(
            "unread already has a message notification; a second one would double up",
            ReplyNudgePolicy.shouldNudge(row(unread = 2), false, 0, now, hour)
        )
    }

    @Test
    fun everyQuietStateWinsOverTheReminder() {
        val day = ReplyNudgePolicy.intervalMillis(24)
        assertFalse("archived", ReplyNudgePolicy.shouldNudge(row(archived = true), false, 0, now, day))
        assertFalse("blocked", ReplyNudgePolicy.shouldNudge(row(blocked = true), false, 0, now, day))
        assertFalse("trashed", ReplyNudgePolicy.shouldNudge(row(deleted = true), false, 0, now, day))
        assertFalse(
            "a chat muted through its Android channel",
            ReplyNudgePolicy.shouldNudge(row(), muted = true, lastNudgedAt = 0, now = now, intervalMillis = day)
        )
    }

    @Test
    fun oneNudgePerConversationPerDay() {
        val day = ReplyNudgePolicy.intervalMillis(24)
        assertFalse(
            "nudged an hour ago",
            ReplyNudgePolicy.shouldNudge(row(), false, lastNudgedAt = now - hour, now = now, intervalMillis = day)
        )
        assertTrue(
            "a thread still unanswered the next day earns one more",
            ReplyNudgePolicy.shouldNudge(
                row(), false, lastNudgedAt = now - 25 * hour, now = now, intervalMillis = day
            )
        )
    }

    @Test
    fun theIntervalIsBoundedAndReadsAsHours() {
        assertTrue(ReplyNudgePolicy.intervalMillis(24) == 24 * hour)
        assertTrue(ReplyNudgePolicy.intervalMillis(0) == hour)
        assertTrue("a week is the cap", ReplyNudgePolicy.intervalMillis(1_000) == 7 * 24 * hour)
        assertTrue(ReplyNudgePolicy.INTERVAL_CHOICES.contains(ReplyNudgePolicy.DEFAULT_INTERVAL_HOURS))
    }

    @Test
    fun theSwitchOwnsTheScheduleAndTheAppStartReconciles() {
        val app = src("java/com/anindra/messages/MessagesApplication.kt")
        assertTrue(
            "the worker's schedule must follow its switch, or it outlives the preference",
            Regex("""ReplyNudgeScheduler\.apply\(\s*this@MessagesApplication,\s*s\.replyNudgeEnabled""")
                .containsMatchIn(app)
        )
        assertTrue(
            "an app start is when a nudge that came due while the app was closed is posted",
            app.contains("ReplyNudgeScheduler.checkNow(this@MessagesApplication)")
        )
        val ui = src("java/com/anindra/messages/ui/AdvancedSubScreens.kt")
        assertTrue(
            "turning the switch off has to disarm the worker too",
            Regex("""vm\.settings\.replyNudgeEnabled = it[\s\S]{0,200}?ReplyNudgeScheduler\.apply\(context, it\)""")
                .containsMatchIn(ui)
        )
        assertTrue(
            "the interval only matters while the switch is on",
            Regex("""if \(replyNudge\) \{[\s\S]{0,400}?settings_reply_nudge_after_title""")
                .containsMatchIn(ui)
        )
    }

    @Test
    fun replyingDropsTheCardOnEverySendPath() {
        val repo = src("java/com/anindra/messages/data/Repository.kt")
        assertTrue(
            "every send ends in touchConversation(isMe = true), so the cancel belongs there",
            Regex("""touchConversation\(conversationId, clean, now, isMe = true\)""").containsMatchIn(repo)
        )
        assertTrue(
            "a reply is the answer the nudge was waiting for",
            Regex("""if \(isMe\) com\.anindra\.messages\.sms\.ReplyNudge\.cancel\(context, conversationId\)""")
                .containsMatchIn(repo)
        )
    }

    @Test
    fun theCardIsQuietAndOpensTheChat() {
        val nudge = src("java/com/anindra/messages/sms/ReplyNudge.kt")
        assertTrue("a reminder must not buzz", nudge.contains("NotificationManager.IMPORTANCE_LOW"))
        assertTrue(nudge.contains("setSound(null, null)"))
        assertTrue(nudge.contains("setShowBadge(false)"))
        assertTrue("silent even where the channel is louder", nudge.contains(".setSilent(true)"))
        assertTrue("tapping it opens the conversation", nudge.contains("\"open_conversation_address\""))
        assertTrue("a nudge must not inflate the launcher badge", !nudge.contains(".setNumber("))
        assertTrue(
            "a private body must not reach the lock screen",
            nudge.contains("privacyModeEnabled") && nudge.contains("notif_nudge_private")
        )
        assertTrue(
            "gone the moment the row stops qualifying",
            Regex("""activeNotifications\?\.filter \{ it\.tag == TAG && it\.id !in live \}""")
                .containsMatchIn(nudge)
        )
    }

    @Test
    fun theWorkerAsksThePolicyAndTheMasterSwitch() {
        val scheduler = src("java/com/anindra/messages/data/ReplyNudgeScheduler.kt")
        assertTrue(
            "the period is WorkManager's floor: the policy decides what is due",
            scheduler.contains("PERIOD_MINUTES = 15L")
        )
        assertTrue(
            "a disabled switch disarms both the period and the one-shot check",
            Regex("""if \(!enabled\) \{[\s\S]{0,200}?cancelUniqueWork\(WORK_NAME\)""")
                .containsMatchIn(scheduler)
        )
        assertTrue(scheduler.contains("if (!app.repository.settings.replyNudgeEnabled) return Result.success()"))
        assertTrue(
            "everything that posts goes through the same master switch",
            src("java/com/anindra/messages/sms/ReplyNudge.kt")
                .contains("if (!settings.replyNudgeEnabled || !NotificationHelper.canPost(context))")
        )
    }
}
