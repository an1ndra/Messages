package com.anindra.messages.sms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** #345: the launcher badge sums the numbers of the app's live notifications,
 *  and a boot or an app update wipes the shade — unread messages kept their
 *  count in the database but lost their badge ("the phone turned off and on
 *  again; the counter no longer shows any number"). The boot receiver must
 *  re-post them, silently, and never wake the screen. */
class BadgeRepostWiringTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val manifest: String by lazy { File(main, "AndroidManifest.xml").readText() }
    private val receiver: String by lazy {
        File(main, "java/com/anindra/messages/sms/BootReceiver.kt").readText()
    }
    private val sms: String by lazy {
        File(main, "java/com/anindra/messages/sms/SmsSupport.kt").readText()
    }
    private val repository: String by lazy {
        File(main, "java/com/anindra/messages/data/Repository.kt").readText()
    }

    @Test
    fun theManifestHearsBootAndUpdate() {
        assertTrue(
            "RECEIVE_BOOT_COMPLETED must be declared for the receiver",
            manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED")
        )
        assertTrue(
            "the BootReceiver must listen for both shade-wiping broadcasts",
            Regex(
                """<receiver\s+android:name="\.sms\.BootReceiver"[\s\S]*?""" +
                    """BOOT_COMPLETED[\s\S]*?MY_PACKAGE_REPLACED[\s\S]*?</receiver>"""
            ).containsMatchIn(manifest)
        )
    }

    @Test
    fun theReceiverRepostsUnreadNotifications() {
        assertTrue(
            "the receiver gates on both actions",
            receiver.contains("Intent.ACTION_BOOT_COMPLETED") &&
                receiver.contains("Intent.ACTION_MY_PACKAGE_REPLACED")
        )
        assertTrue(
            "the receiver must hand the work to the re-post, not rebuild notifications",
            receiver.contains("NotificationHelper.repostUnread(context)")
        )
    }

    @Test
    fun aRepostIsSilentAndNeverWakesTheScreen() {
        assertTrue(
            "a re-post must not alert: hours have passed since the message arrived",
            sms.contains(".setSilent(silentRepost)")
        )
        assertTrue(
            "a re-post must not attach the full-screen wake intent",
            sms.contains("if (!silentRepost && shouldWake(")
        )
        assertTrue(
            "the re-post feeds from the unread summaries, silently",
            Regex("""show\(context, address, body, silentRepost = true\)""").containsMatchIn(sms)
        )
    }

    @Test
    fun theRepostQueryCoversOnlyUnreadInboxRows() {
        assertTrue(
            "the re-post must read the same rows the receive path posts for: " +
                "not deleted, not blocked (a blocked sender never badges), still unread",
            repository.contains("deleted_at=0 AND blocked=0 AND unread_count>0")
        )
        assertTrue(
            "the query lives next to the badge count it mirrors",
            repository.contains("fun unreadForRepostBlocking")
        )
    }
}
