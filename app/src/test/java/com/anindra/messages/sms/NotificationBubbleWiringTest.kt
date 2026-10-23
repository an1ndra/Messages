package com.anindra.messages.sms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wiring for conversation bubbles (#354). Android only floats a notification as
 * a bubble when the launched activity is embeddable and resizable, the
 * notification references a published long-lived shortcut, and the bubble's
 * PendingIntent is mutable — an immutable one makes notify() throw and silently
 * loses the whole message notification. Each of those is pinned against the
 * source that owns it.
 */
class NotificationBubbleWiringTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private fun read(relative: String): String = File(main, relative).readText()

    private fun src(name: String) = read("java/com/anindra/messages/$name")

    private fun manifest(): String = read("AndroidManifest.xml")

    @Test
    fun theBubbleActivityIsEmbeddableAndResizable() {
        val activity = Regex("""<activity(?=[^>]*android:name="\.MainActivity")[^>]*>""")
            .find(manifest())
            ?.value
            ?: error("MainActivity is not declared")
        assertTrue("MainActivity must be embeddable for a bubble", activity.contains("android:allowEmbedded=\"true\""))
        assertTrue("MainActivity must be resizable for a bubble", activity.contains("android:resizeableActivity=\"true\""))
    }

    @Test
    fun thePosterAttachesBubbleMetadataBehindAPublishedShortcut() {
        val s = src("sms/SmsSupport.kt")
        assertTrue("the poster never publishes the conversation shortcut", s.contains("NotificationBubble.ensureShortcut"))
        assertTrue("the notification never references the shortcut", s.contains(".setShortcutId("))
        assertTrue("the notification is not a conversation without a person", s.contains(".addPerson("))
        assertTrue("the notification carries no bubble metadata", s.contains(".setBubbleMetadata("))
        assertTrue("bubbles are gated by the shared policy", s.contains("NotificationBubble.shouldAttach("))
    }

    @Test
    fun theBubbleLaunchIsMarkedAndCancellableOnlyByTheBubble() {
        val helper = src("sms/NotificationBubble.kt")
        assertTrue(
            "an immutable PendingIntent on bubble metadata makes notify() throw",
            helper.contains("PendingIntent.FLAG_MUTABLE")
        )
        assertTrue("the bubble launch must be recognisable", helper.contains("EXTRA_OPEN_IN_BUBBLE"))

        val poster = src("sms/SmsSupport.kt")
        assertTrue(
            "dismissal must wait for a live bubble host",
            poster.contains("if (BubbleHost.holds(conversationId)) return")
        )

        val activity = src("MainActivity.kt")
        assertTrue("the activity ignores the bubble launch", activity.contains("isBubbleLaunch"))
        assertTrue("the bubble never registers its conversation", activity.contains("BubbleHost.enter("))
        assertTrue("the bubble never releases its conversation", activity.contains("BubbleHost.leave("))
        assertTrue("the bubble's chat clears its notification anyway", activity.contains("if (bubbleMode) finish()"))
    }

    @Test
    fun diagnosticsReportBubbleReadinessFromTheSystem() {
        val report = src("diagnostics/DiagnosticsReport.kt")
        assertTrue(report.contains("bubblesEnabled = NotificationHelper.bubblesAllowed(context)"))
        assertTrue(report.contains("bubbleChannels = NotificationHelper.bubbleChannels(context)"))
        assertTrue(report.contains("NotificationBubble.publishedShortcutIds(context)"))
        assertTrue(report.contains("\"Bubbles enabled: "))
        assertTrue(report.contains("\"Bubbles: "))
        assertTrue(report.contains("\"Bubble shortcuts: "))
        assertTrue(
            "channel bubble state is read from the platform, not re-derived",
            src("sms/SmsSupport.kt").contains("internal fun bubbleChannels(")
        )
    }
}
