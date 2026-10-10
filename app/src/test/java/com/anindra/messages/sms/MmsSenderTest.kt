package com.anindra.messages.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Reads a source file from the repository, wherever the test is run from. */
internal fun sourceOf(relative: String): String =
    generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, relative) }
        .firstOrNull { it.isFile }
        ?.readText()
        ?: error("$relative not found from ${File("").absolutePath}")

/**
 * Sending now goes through the `:mms` stack instead of the removed composer, so
 * the app's own job is much smaller: read the attachment, hand it over, and
 * remember which message the outbox row belongs to.
 */
class MmsSenderTest {

    @Test
    fun anImageIsReadWholeBecauseItIsShrunkToFit() {
        // The source of a photo is routinely larger than the carrier cap; it is
        // the encoded result that has to fit, not the file being read.
        assertEquals(
            MmsSender.IMAGE_READ_CAP_BYTES,
            MmsSender.readLimit("image/jpeg", 300_000)
        )
    }

    @Test
    fun anythingElseIsBoundedByTheCarrierCap() {
        // A video is sent as it is, so a source over the cap can never succeed
        // and reading it whole would only waste memory.
        assertEquals(300_000L, MmsSender.readLimit("video/mp4", 300_000))
        assertEquals(0L, MmsSender.readLimit("application/pdf", 0))
    }

    @Test
    fun theReadCapIsLargeEnoughForAPhonePhoto() {
        assertTrue(MmsSender.IMAGE_READ_CAP_BYTES >= 10_000_000L)
    }

    @Test
    fun theSendHookIsNotSwallowedByTheStackWiring() {
        // of(context, diagnostics) has to hand the caller's hook to the stack.
        // Leaving the facade's own recorder in place compiles fine, records
        // normally, and silently disables everything the caller asked for -
        // which is how the outbox link stopped firing once already.
        val facade = sourceOf("app/src/main/java/com/anindra/messages/sms/MmsFacade.kt")

        assertTrue(
            "Mms.send must receive the caller's diagnostics",
            Regex("""Mms\([\s\S]*?diagnostics = diagnostics""").containsMatchIn(facade)
        )
        assertTrue(
            "the transport must receive the caller's diagnostics too",
            Regex("""SystemMmsTransport\([\s\S]*?diagnostics = diagnostics""").containsMatchIn(facade)
        )
    }

    @Test
    fun everyStackCallbackReachesBothHooks() {
        // A forwarding hook that forgets one callback loses it silently: the
        // interface's methods all have no-op defaults.
        val sender = sourceOf("app/src/main/java/com/anindra/messages/sms/MmsSender.kt")
        val body = sender.substring(sender.indexOf("private class ChainedDiagnostics"))

        for (callback in listOf(
            "sendStarted", "sendBuilt", "sendCompleted", "receiveStarted", "receiveCompleted",
            "transportSelected", "apnResolved", "networkResolved", "attachmentFitted",
            "attachmentRejected", "downloadRequested", "pendingSwept", "downloadCompleted", "noted",
        )) {
            assertTrue(
                "ChainedDiagnostics does not forward $callback, so it is dropped",
                body.contains("override fun $callback(")
            )
        }
    }

    @Test
    fun sendMmsDelegatesToTheMmsStack() {
        val source = sourceOf("app/src/main/java/com/anindra/messages/sms/SmsSupport.kt")

        assertTrue(
            "sendMms must hand the send to the :mms stack",
            source.contains("MmsSender.send(")
        )
        assertTrue(
            "the removed composer must not be built anywhere",
            !source.contains("MmsComposer")
        )
    }
}

/**
 * The result is matched back to the app's message through the MMS transaction
 * id, and the provider row is linked at the same moment.
 */
class MmsSendResultWiringTest {

    @Test
    fun theReceiverListensForTheStacksAction() {
        val manifest = sourceOf("app/src/main/AndroidManifest.xml")

        assertTrue(
            "the receiver must answer the action the transport sends",
            manifest.contains("com.anindra.messages.mms.action.SEND_SENT")
        )
    }

    @Test
    fun theOutboxRowIsLinkedBeforeThePduIsHandedOver() {
        // Mms.send persists the row, then calls sendStarted, then hands over.
        // Linking inside sendStarted is the only point where the row exists and
        // the platform has not been told anything yet.
        val sender = sourceOf("app/src/main/java/com/anindra/messages/sms/MmsSender.kt")
        val mms = sourceOf("mms/src/main/java/com/anindra/messages/mms/Mms.kt")

        assertTrue(sender.contains("override fun sendStarted("))
        assertTrue(sender.contains("repository.linkMmsRow(messageId, rowId)"))
        assertTrue(
            "the link must happen before the transport is handed the message",
            mms.indexOf("diagnostics.sendStarted(") < mms.indexOf("binding.transport.send(")
        )
    }

    @Test
    fun aLinkedRowIsRecordedInTheMappingTableToo() {
        // This app reads message_provider_ids for chat deletion and for import
        // dedupe. A link that only filled messages.sys_id would fix the
        // duplicate on screen and then let the provider row come back as a
        // fresh 1:1 once the chat was deleted.
        val repository = sourceOf("app/src/main/java/com/anindra/messages/data/Repository.kt")
        val start = repository.indexOf("fun linkMmsRow(")

        assertTrue("linkMmsRow must exist", start > 0)
        val body = repository.substring(start, start + 900)
        assertTrue(body.contains("UPDATE messages SET sys_id="))
        assertTrue(body.contains("message_provider_ids"))
    }

}