package com.anindra.messages.mms.debug

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
}
