package com.anindra.messages.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The `:mms` stack reports what it did through `MmsDiagnostics`, and every
 * callback on that interface is a no-op by default. A stack built without a
 * recorder is therefore silent, and "nothing arrived" and "nothing was
 * reported" are indistinguishable from the outside.
 *
 * These pin the wiring: the recorder the app hands the stack is the same object
 * Diagnostics reads, it survives across reads, and the download path — which
 * sits outside the `:mms` stack — reports through it too.
 */
class MmsDiagnosticsWiringTest {

    private fun sourceOf(relative: String): String {
        val tried = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relative) }
            .toList()
        return tried.firstOrNull { it.isFile }?.readText()
            ?: error("$relative not found from ${File("").absolutePath}; tried $tried")
    }

    @Test
    fun whatTheStackReportsIsWhatDiagnosticsPrints() {
        MmsFacade.diagnostics.sendBuilt(pduSize = 4_242, recipientCount = 2)

        val text = MmsFacade.trace().joinToString("\n")
        assertTrue(
            "a send event must be visible to the Diagnostics report",
            text.contains("[send]built pduSize=4242 recipients=2")
        )
    }

    @Test
    fun everyReadSeesTheSameRecorder() {
        MmsFacade.diagnostics.receiveStarted("notification-ind", 3)

        // Diagnostics and the send path have to agree; two recorders would each
        // hold half the history and the report would read empty.
        assertEquals(MmsFacade.diagnostics, MmsFacade.diagnostics)
        assertTrue(MmsFacade.trace().any { it.toString().contains("messageType=notification-ind") })
    }

    @Test
    fun theStackIsBuiltWithTheRecorder() {
        val source = sourceOf("app/src/main/java/com/anindra/messages/sms/MmsFacade.kt")
        assertTrue(
            "Mms.send must get the diagnostics, or nothing it does is recorded",
            Regex("""Mms\([\s\S]*?diagnostics = diagnostics""").containsMatchIn(source)
        )
    }

    @Test
    fun downloadsAreRecordedFromOutsideTheMmsStack() {
        // Receiving is not wired to Mms.receive yet, so the app's own download
        // path is the only thing reporting an incoming transfer.
        val source = sourceOf("app/src/main/java/com/anindra/messages/sms/MmsDownloader.kt")
        assertTrue(source.contains("MmsFacade.diagnostics.downloadRequested("))
        assertTrue(source.contains("MmsFacade.diagnostics.downloadCompleted("))
    }

    @Test
    fun aFailedDownloadReportsItsHttpStatus() {
        val source = sourceOf("app/src/main/java/com/anindra/messages/sms/MmsDownloadReceiver.kt")
        // Without the status the 404 branch of MmsRetry is unreachable, and an
        // expired message is re-requested on every launch forever.
        assertTrue(source.contains("EXTRA_MMS_HTTP_STATUS"))
    }

    @Test
    fun aLoggedMmsLineIsReadableInDiagnostics() {
        MmsTrace.w("MmsComposer", "MMS not sent: TOO_LARGE")

        // The whole reason MmsTrace exists: a line that used to exist only in
        // logcat is now text the report prints and a test can assert on.
        assertTrue(
            MmsFacade.trace().joinToString("\n")
                .contains("[log]w tag=MmsComposer line=MMS not sent: TOO_LARGE")
        )
    }

    @Test
    fun aThrowableKeepsItsTypeInTheRecordedLine() {
        MmsTrace.w("MmsDownload", "pending query failed", IllegalStateException("cursor closed"))

        assertTrue(
            MmsFacade.trace().joinToString("\n")
                .contains("line=pending query failed: IllegalStateException: cursor closed")
        )
    }

    @Test
    fun aLoggedLineFromEveryMmsLayerIsRecorded() {
        // One line per layer, because each of these files used to keep its own
        // copy of what it knew in logcat where nothing could read it back.
        MmsTrace.i("MmsComposer", "composed 90000B PDU")
        MmsTrace.w("MmsDownload", "pending query failed")
        MmsTrace.w("MmsSendDiag", "sendMms handing off")
        MmsTrace.i("MmsImport", "MMS import done: 3 offered, 2 imported, 1 already present")

        val text = MmsFacade.trace().joinToString("\n")
        assertTrue(text.contains("tag=MmsComposer line=composed 90000B PDU"))
        assertTrue(text.contains("tag=MmsDownload line=pending query failed"))
        assertTrue(text.contains("tag=MmsSendDiag line=sendMms handing off"))
        assertTrue(text.contains("tag=MmsImport line=MMS import done: 3 offered, 2 imported"))
    }
}