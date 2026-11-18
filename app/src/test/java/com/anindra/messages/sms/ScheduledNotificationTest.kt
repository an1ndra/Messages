package com.anindra.messages.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #475: a queued message is invisible between scheduling and sending. One
 * ongoing card per pending row carries the countdown and the two actions the
 * row itself has — send now and cancel — and it is reconciled at boot and app
 * start because the shade does not survive either.
 *
 * The card is pure Android notification plumbing, so the behaviour that can be
 * asserted off-device is the arithmetic and the wiring; the live card is driven
 * by scripts/test-scheduled-notifications.sh.
 */
class ScheduledNotificationTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private fun src(path: String) = File(main, path).readText()

    private val poster by lazy {
        src("java/com/anindra/messages/sms/ScheduledNotifications.kt")
    }
    private val sender by lazy {
        src("java/com/anindra/messages/sms/ScheduledMessageSender.kt")
    }
    private val activity by lazy { src("java/com/anindra/messages/MainActivity.kt") }
    private val app by lazy { src("java/com/anindra/messages/MessagesApplication.kt") }
    private val boot by lazy { src("java/com/anindra/messages/sms/BootReceiver.kt") }
    private val repository by lazy { src("java/com/anindra/messages/data/Repository.kt") }

    @Test
    fun aCardIsKeyedByTheRowItBelongsTo() {
        assertEquals(1, ScheduledNotifications.idOf(1))
        assertEquals(42, ScheduledNotifications.idOf(42))
        // The mask keeps the id usable as a notification id whatever the row
        // counter grows to, and never flips the sign.
        assertEquals(Int.MAX_VALUE, ScheduledNotifications.idOf(Long.MAX_VALUE))
        assertTrue(ScheduledNotifications.idOf(-1) >= 1)
    }

    @Test
    fun theCountdownRoundsUpSoAFreshCardNeverSaysZero() {
        val now = 1_000_000L
        assertEquals(0, ScheduledNotifications.minutesLeft(now, now))
        assertEquals(1, ScheduledNotifications.minutesLeft(now, now + 1))
        assertEquals(1, ScheduledNotifications.minutesLeft(now, now + 60_000))
        assertEquals(2, ScheduledNotifications.minutesLeft(now, now + 60_001))
        // An alarm that fires a moment late must not report negative time.
        assertEquals(0, ScheduledNotifications.minutesLeft(now, now - 5_000))
    }

    @Test
    fun theCardIsOngoingAndTheSystemDrawsItsCountdown() {
        assertTrue("the card must not be swiped away", poster.contains(".setOngoing(true)"))
        assertTrue("dismissal is the cancel action's job", poster.contains(".setAutoCancel(false)"))
        assertTrue(
            "the countdown must be a chronometer, so it stays live without a ticker",
            poster.contains(".setUsesChronometer(true)")
        )
        assertTrue(
            "a chronometer counts up by default; a schedule needs it to count down",
            poster.contains(".setChronometerCountDown(true)")
        )
        assertTrue("the card is a reminder, not an arrival", poster.contains("CATEGORY_REMINDER"))
    }

    @Test
    fun theCardOffersExactlyTheRowsTwoActions() {
        assertTrue(
            "send now must route to the scheduled receiver",
            poster.contains("ScheduledMessageSender.ACTION_SEND_NOW")
        )
        assertTrue(
            "cancel must route to the scheduled receiver",
            poster.contains("ScheduledMessageSender.ACTION_CANCEL")
        )
        assertTrue(
            "the receiver must answer both actions, or the card's buttons do nothing",
            sender.contains("ACTION_CANCEL ->") && sender.contains("ACTION_SEND_NOW ->")
        )
    }

    @Test
    fun sendNowSendsTheRowAsItStandsRatherThanTheCardsCopy() {
        assertTrue(
            "a card outlives a reschedule, so send-now must read the row back",
            Regex("""ACTION_SEND_NOW ->[\s\S]{0,400}?repo\.scheduledMessageById\(id\)""")
                .containsMatchIn(sender)
        )
        assertTrue(
            "a cancel action must disarm the alarm as well as the card",
            Regex("""ACTION_CANCEL ->[\s\S]{0,300}?cancel\(context, id\)[\s\S]{0,300}?deleteScheduledMessage""")
                .containsMatchIn(sender)
        )
    }

    @Test
    fun theCardFollowsScheduleRescheduleAndCancel() {
        assertTrue(
            "scheduling must post the card",
            Regex("""fun scheduleMessage[\s\S]{0,1200}?ScheduledNotifications\.post""")
                .containsMatchIn(activity)
        )
        assertTrue(
            "rescheduling must move the card with the alarm",
            Regex("""fun rescheduleMessage[\s\S]{0,1200}?ScheduledNotifications\.post""")
                .containsMatchIn(activity)
        )
        assertTrue(
            "cancelling must drop the card",
            Regex("""fun cancelScheduledMessage[\s\S]{0,600}?ScheduledNotifications\.cancel""")
                .containsMatchIn(activity)
        )
    }

    @Test
    fun theCardIsDroppedWhenTheSendLandsOrFails() {
        assertTrue(
            "every send path must retire the card in a finally, so a failure is covered too",
            Regex("""private fun send\([\s\S]*?finally \{[\s\S]*?ScheduledNotifications\.cancel\(""")
                .containsMatchIn(sender)
        )
    }

    @Test
    fun aBootAndAnAppStartReconcileTheShade() {
        assertTrue(
            "a reboot wipes the shade while the alarms survive",
            boot.contains("ScheduledNotifications.sync(context)")
        )
        assertTrue(
            "an app start covers a post refused before the permission was given",
            app.contains("ScheduledNotifications.sync(this@MessagesApplication)")
        )
        assertTrue(
            "reconciling outside a flow needs a blocking read",
            repository.contains("fun scheduledMessagesBlocking()")
        )
    }

    @Test
    fun theSilentChannelNeverAlertsOrBadges() {
        assertTrue(
            "a countdown is state, not an event: it must not make a sound",
            poster.contains("NotificationManager.IMPORTANCE_LOW")
        )
        assertTrue(poster.contains("setSound(null, null)"))
        assertTrue(poster.contains("setShowBadge(false)"))
        assertTrue(
            "the channel is created once and then belongs to the user",
            poster.contains("if (nm.getNotificationChannel(CHANNEL_ID) != null) return")
        )
    }

    @Test
    fun theCardRespectsTheMasterSwitchAndPrivacyMode() {
        assertTrue(
            "posting goes through the same switch every other notification does",
            poster.contains("if (!NotificationHelper.canPost(context)) return")
        )
        assertTrue(
            "a private body must not appear on the lock screen's card",
            poster.contains("privacyModeEnabled") &&
                Regex("""if \(privacy\).*notif_scheduled_private""", RegexOption.DOT_MATCHES_ALL)
                    .containsMatchIn(poster)
        )
    }

    @Test
    fun promotionIsGuardedByThePlatformAndTheUser() {
        assertTrue(
            "below Android 16 there is no promoted surface to ask for",
            poster.contains("Build.VERSION.SDK_INT < 36")
        )
        assertTrue(
            "the user can switch promoted notifications off, and the card must honour that",
            poster.contains("canPostPromotedNotifications()")
        )
        assertTrue(
            "the flag is what marks the card promotable",
            poster.contains("Notification.FLAG_PROMOTED_ONGOING")
        )
        assertTrue(
            "the progress bar is decorative: the chronometer is the countdown",
            poster.contains(".setStyledByProgress(false)")
        )
    }
}
