package com.anindra.messages.widget

import com.anindra.messages.data.Conversation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetProbeTest {

    private fun convo(id: Long, unread: Int = 0) = Conversation(
        id = id, address = "+1555000$id", name = "Name $id", snippet = "s$id",
        timestamp = id, unreadCount = unread, isMe = false
    )

    private val list = listOf(convo(1, unread = 2), convo(2))

    @Test
    fun unlockedLineCarriesRowsAndTotal() {
        val line = WidgetProbe.line(list, unreadAtTop = false, appLock = false, privacy = false)
        assertTrue(line.contains("locked=false"))
        assertTrue(line.contains("+15550001|Name 1|2"))
        assertTrue(line.contains("total=2"))
    }

    @Test
    fun lockedLineCarriesNothing() {
        val line = WidgetProbe.line(list, unreadAtTop = false, appLock = true, privacy = false)
        assertTrue(line.contains("locked=true"))
        assertTrue(line.endsWith("list=[] unread=[] total=0"))
        assertFalse(line.contains("+15550001"))
        assertFalse(line.contains("Name 1"))
    }

    @Test
    fun privacyAloneLocksTheLine() {
        val line = WidgetProbe.line(list, unreadAtTop = false, appLock = false, privacy = true)
        assertTrue(line.contains("locked=true"))
        assertFalse(line.contains("+1555000"))
    }
}
