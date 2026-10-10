package com.anindra.messages.mms.debug

import com.anindra.messages.mms.spi.FitOutcome
import com.anindra.messages.mms.spi.FitRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsDebugRecorderTest {

    @Test
    fun keepsTheMostRecentEventsUpToCapacity() {
        val recorder = MmsDebugRecorder(capacity = 3)
        recorder.sendStarted("system", 1, "content://mms/outbox/1")
        recorder.sendBuilt(1234, 2)
        recorder.sendCompleted("system", "sent", 128, 200)
        recorder.networkResolved(true)

        val events = recorder.snapshot()
        assertEquals(3, events.size)
        assertEquals("send", events[0].category)
        assertEquals("built", events[0].name)
        assertEquals("send", events[1].category)
        assertEquals("completed", events[1].name)
        assertEquals("network", events[2].category)
        assertEquals("resolved", events[2].name)
    }

    @Test
    fun formatsEventsSoDiagnosticsCanPrintThemAsText() {
        val recorder = MmsDebugRecorder()
        recorder.transportSelected("direct-mmsc", 7, true)
        recorder.apnResolved(7, "http://mmsc.test", "proxy.test:8080")

        val text = recorder.snapshot().joinToString("\n") { it.toString() }
        assertTrue(text.contains("[transport]selected sub=7 transport=direct-mmsc available=true"))
        assertTrue(text.contains("[apn]resolved sub=7 mmsc=http://mmsc.test proxy=proxy.test:8080"))
    }

    @Test
    fun exposesTheLastEvent() {
        val recorder = MmsDebugRecorder()
        recorder.receiveStarted("notification-ind", 1)
        recorder.receiveCompleted("retrieved", "content://mms/inbox/5")

        assertEquals("receive", recorder.last()?.category)
        assertEquals("completed", recorder.last()?.name)
    }

    @Test
    fun recordsTheNumbersAPictureWasReEncodedTo() {
        val recorder = MmsDebugRecorder()
        val request = FitRequest(
            mimeType = "image/jpeg",
            bytes = ByteArray(400_000),
            sourceWidth = 4_000,
            sourceHeight = 3_000,
            budgetBytes = 295_168,
            maxImageWidth = 640,
            maxImageHeight = 480,
        )
        recorder.attachmentFitted(request, FitOutcome.Fitted(ByteArray(98_000), "image/jpeg", 640, 480))

        val text = recorder.last().toString()
        assertTrue(text.contains("[fit]fitted"))
        // Source and sent dimensions together are the whole blurry-picture story:
        // 4000x3000 going out as 640x480 is visible in one line.
        assertTrue(text.contains("source=4000x3000"))
        assertTrue(text.contains("sent=640x480"))
        assertTrue(text.contains("budget=295168"))
        assertTrue(text.contains("carrierCap=640x480"))
    }

    @Test
    fun recordsWhyAnAttachmentWasNotSent() {
        val recorder = MmsDebugRecorder()
        recorder.attachmentRejected("video/mp4", 9_000_000, 300_000, "too_large")

        val text = recorder.last().toString()
        assertTrue(text.contains("[fit]rejected"))
        assertTrue(text.contains("reason=too_large"))
        assertTrue(text.contains("sourceBytes=9000000"))
    }

    @Test
    fun recordsBothSidesOfADownloadAttempt() {
        val recorder = MmsDebugRecorder()
        recorder.downloadRequested(42, "http://mmsc.test/mms?x=1", attempt = 2)
        recorder.downloadCompleted(42, resultCode = 1, httpStatus = 404)

        val events = recorder.snapshot()
        assertEquals(2, events.size)
        assertEquals("[download]requested row=42 location=http://mmsc.test/mms?x=1 attempt=2", events[0].toString())
        assertEquals("[download]completed row=42 resultCode=1 httpStatus=404", events[1].toString())
    }

    @Test
    fun aDownloadWithoutAContentLocationStillRecordsTheRow() {
        val recorder = MmsDebugRecorder()
        recorder.downloadRequested(7, null, attempt = 1)
        recorder.downloadRequested(8, "   ", attempt = 1)

        // Blank and null both mean "nothing to fetch", and both have to be
        // printable: this is the line that says the row was there but unusable.
        assertTrue(recorder.snapshot().all { it.details["location"] == "-" })
    }

    @Test
    fun anEmptySweepIsStillRecorded() {
        val recorder = MmsDebugRecorder()
        recorder.pendingSwept(0)

        // "The sweep ran and found nothing" is the only line that separates a
        // carrier that sent nothing from a receive path that never looked.
        assertEquals("[download]swept pending=0", recorder.last().toString())
    }

    @Test
    fun aLoggedLineKeepsItsTagSoTheReportReadsLikeALog() {
        val recorder = MmsDebugRecorder()
        recorder.noted("MmsComposer", "w", "MMS not sent: TOO_LARGE")

        assertEquals(
            "[log]w tag=MmsComposer line=MMS not sent: TOO_LARGE",
            recorder.last().toString()
        )
    }
}

/**
 * The logcat mirror exists because the recorder dies with the process, so it
 * must forward everything — a callback it silently dropped would leave an event
 * readable in Diagnostics and missing from a bug report, which is the opposite
 * of what it is for.
 */
class LogcatMmsDiagnosticsTest {

    @Test
    fun everyEventReachesTheDelegate() {
        val delegate = MmsDebugRecorder()
        val subject = LogcatMmsDiagnostics(delegate)

        subject.sendStarted("system", 1, "content://mms/outbox/9")
        subject.sendBuilt(2_048, 2)
        subject.sendCompleted("system", "sent", 128, 200)
        subject.receiveStarted("notification-ind", 1)
        subject.receiveCompleted("retrieved", "content://mms/inbox/3")
        subject.transportSelected("system", 1, true)
        subject.apnResolved(1, "http://mmsc.test", null)
        subject.networkResolved(true)
        subject.pendingSwept(0)
        subject.downloadRequested(4, "http://mmsc.test/a", 1)
        subject.downloadCompleted(4, 1, 404)
        subject.attachmentRejected("video/mp4", 900, 300, "too_large")
        subject.noted("MmsComposer", "e", "pdu compose failed")

        assertEquals(13, delegate.snapshot().size)
    }

    @Test
    fun anAttachmentDecisionIsRecordedWithItsNumbers() {
        val delegate = MmsDebugRecorder()
        val subject = LogcatMmsDiagnostics(delegate)
        subject.attachmentFitted(
            FitRequest(
                mimeType = "image/jpeg",
                bytes = ByteArray(10),
                sourceWidth = 1_600,
                sourceHeight = 1_200,
                budgetBytes = 300_000,
                maxImageWidth = 640,
                maxImageHeight = 480,
            ),
            FitOutcome.Fitted(ByteArray(5), "image/jpeg", 1_600, 1_200),
        )

        assertTrue(
            delegate.last().toString().let {
            it.contains("[fit]fitted") &&
                it.contains("source=1600x1200") &&
                it.contains("sent=1600x1200") &&
                it.contains("carrierCap=640x480")
        }
        )
    }
}
