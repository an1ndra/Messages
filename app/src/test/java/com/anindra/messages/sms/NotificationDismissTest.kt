package com.anindra.messages.sms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reported after installing a build: notifications the user had already cleared
 * came back, and "Mark all as read" left the shade untouched.
 *
 * Two independent causes:
 *  - the boot/app-update re-post had no idea a notification had been dismissed,
 *    so it resurrected it;
 *  - mark-all-read only zeroed the counter in the database and never cancelled
 *    the notifications it had just read.
 */
class NotificationDismissTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    private val support get() = read("src/main/java/com/anindra/messages/sms/SmsSupport.kt")
    private val repo get() = read("src/main/java/com/anindra/messages/data/Repository.kt")
    private val activity get() = read("src/main/java/com/anindra/messages/MainActivity.kt")
    private val store get() = read("src/main/java/com/anindra/messages/data/SettingsStore.kt")
    private val manifest get() = read("src/main/AndroidManifest.xml")

    @Test
    fun theRepostSkipsWhatTheUserCleared() {
        assertTrue(
            "a cleared conversation must not be re-posted",
            support.contains("if (settings.notifClearedUpTo(row.conversationId) >= row.newest) continue")
        )
        assertTrue(
            "and the query must carry the id and the newest timestamp",
            repo.contains("conversationId = c.getLong(2)") && repo.contains("newest = c.getLong(3)")
        )
    }

    @Test
    fun dismissingANotificationRecordsIt() {
        assertTrue(
            "the notification must carry a delete intent",
            support.contains("builder.setDeleteIntent(")
        )
        assertTrue(
            "pointing at the receiver, with the conversation",
            support.contains("NotificationDismissReceiver.EXTRA_CONVERSATION_ID")
        )
        assertTrue(
            "which is registered in the manifest",
            manifest.contains(".sms.NotificationDismissReceiver")
        )
        assertTrue(
            "and writes the marker",
            read("src/main/java/com/anindra/messages/sms/NotificationDismissReceiver.kt")
                .contains("markNotifCleared(conversationId, System.currentTimeMillis())")
        )
    }

    @Test
    fun markingEverythingReadClearsTheShade() {
        assertTrue(
            "mark-all-read must cancel the notifications it just read",
            activity.contains("NotificationHelper.cancelAll(ctx)")
        )
        assertTrue(
            "and the helper must exist",
            support.contains("fun cancelAll(context: Context)")
        )
    }

    @Test
    fun markAllReadLeavesALiveBubbleAlone() {
        // Cancelling a notification collapses its bubble, so the sweep must skip
        // the conversation the user has open in a bubble.
        assertTrue(
            "the sweep must not call the blanket cancelAll()",
            !support.contains("NotificationManagerCompat.from(context).cancelAll()")
        )
        assertTrue(
            "it must walk the active notifications and skip held bubbles",
            support.contains("nm.activeNotifications.forEach") &&
                support.contains("if (BubbleHost.holds(posted.id.toLong())) return@forEach")
        )
    }

    @Test
    fun theMarkerIsMonotonic() {
        assertTrue(
            "a later dismissal must not lower an earlier marker",
            store.contains("if (upTo <= notifClearedUpTo(conversationId)) return")
        )
    }
}
