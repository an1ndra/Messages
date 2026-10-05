package com.anindra.messages.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #285: the incoming-message notification offers a Delete action that moves the
 * newest message of that conversation to Trash (so it stays recoverable) and
 * dismisses the notification. Delete means Trash, never a permanent purge.
 */
class NotificationDeleteActionTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val sms: String by lazy {
        File(main, "java/com/anindra/messages/sms/SmsSupport.kt").readText()
    }
    private val receiver: String by lazy {
        File(main, "java/com/anindra/messages/sms/DeleteMessageReceiver.kt").readText()
    }
    private val manifest: String by lazy { File(main, "AndroidManifest.xml").readText() }
    private val repository: String by lazy {
        File(main, "java/com/anindra/messages/data/Repository.kt").readText()
    }
    private val strings: String by lazy {
        File(main, "res/values/strings_main.xml").readText()
    }

    @Test
    fun theConversationNotificationOffersDelete() {
        assertTrue(sms.contains("DeleteMessageReceiver.ACTION_DELETE"))
        assertTrue(sms.contains("R.string.notif_action_delete"))
        assertTrue(sms.contains("builder.addAction(deleteAction)"))
    }

    @Test
    fun theReceiverTrashesInsteadOfPurgingAndDismisses() {
        assertTrue(
            "the notification delete must soft-delete (Trash), not purge",
            receiver.contains("deleteMessageSuspend")
        )
        assertFalse(
            "a permanent purge from a notification would be unrecoverable",
            receiver.contains("deleteMessageForeverSuspend")
        )
        assertTrue(
            "the notification must be dismissed after deleting",
            receiver.contains("NotificationManagerCompat.from(context).cancel")
        )
    }

    @Test
    fun theReceiverIsDeclaredAndNotExported() {
        val at = manifest.indexOf("DeleteMessageReceiver")
        assertTrue("DeleteMessageReceiver is not declared", at > 0)
        val block = manifest.substring(at, (at + 400).coerceAtMost(manifest.length))
        assertTrue(
            "the action receiver acts on the telephony DB and must not be exported",
            block.contains("android:exported=\"false\"")
        )
    }

    @Test
    fun theTargetIsTheNewestIncomingMessage() {
        assertTrue(repository.contains("fun latestReceivedMessageIdSuspend("))
        assertTrue(
            "the delete target must be an incoming message",
            repository.contains("is_me=0")
        )
    }

    @Test
    fun theActionLabelIsATranslatableString() {
        assertTrue(strings.contains("name=\"notif_action_delete\""))
    }
}
