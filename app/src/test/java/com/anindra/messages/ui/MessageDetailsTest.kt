package com.anindra.messages.ui

import com.anindra.messages.data.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageDetailsTest {
    private fun msg(
        status: String = "sent",
        isMe: Boolean = true,
        transport: String = "sms",
        deliveredAt: Long = 0
    ) = Message(
        id = 1, conversationId = 1, body = "hi", timestamp = 1_700_000_000_000,
        isMe = isMe, status = status, transport = transport, deliveredAt = deliveredAt
    )

    @Test
    fun classifiesTransport() {
        assertEquals(MessageDetails.Kind.SMS, MessageDetails.kind("sms"))
        assertEquals(MessageDetails.Kind.MMS, MessageDetails.kind("mms"))
        assertEquals(MessageDetails.Kind.MMS, MessageDetails.kind("MMS"))
        assertEquals(MessageDetails.Kind.SMS, MessageDetails.kind(""))
        assertEquals("SMS", MessageDetails.kindLabel(MessageDetails.Kind.SMS))
        assertEquals("MMS", MessageDetails.kindLabel(MessageDetails.Kind.MMS))
    }

    @Test
    fun classifiesDirection() {
        assertEquals(MessageDetails.Direction.TO, MessageDetails.direction(isMe = true))
        assertEquals(MessageDetails.Direction.FROM, MessageDetails.direction(isMe = false))
    }

    @Test
    fun mapsEveryStatus() {
        assertEquals(MessageDetails.Status.SENDING, MessageDetails.status("sending"))
        assertEquals(MessageDetails.Status.SENT, MessageDetails.status("sent"))
        assertEquals(MessageDetails.Status.DELIVERED, MessageDetails.status("delivered"))
        assertEquals(MessageDetails.Status.RECEIVED, MessageDetails.status("received"))
        assertEquals(MessageDetails.Status.FAILED, MessageDetails.status("failed"))
        assertEquals(MessageDetails.Status.SENT, MessageDetails.status("unknown"))
    }

    @Test
    fun showsDeliveredTimeOnlyWhenActuallyDelivered() {
        assertEquals(1_700_000_000_500L, MessageDetails.deliveredAt(msg("delivered", deliveredAt = 1_700_000_000_500L)))
        assertNull(MessageDetails.deliveredAt(msg("delivered", deliveredAt = 0)))
        assertNull(MessageDetails.deliveredAt(msg("sent", deliveredAt = 1_700_000_000_500L)))
        assertNull(MessageDetails.deliveredAt(msg("received", deliveredAt = 1_700_000_000_500L, isMe = false)))
        assertNull(MessageDetails.deliveredAt(msg("failed", deliveredAt = 1_700_000_000_500L)))
    }

    /**
     * The bubble's status word is the one place the delivery-reports switch is
     * visible, so its whole truth table is pinned here. A delivered row
     * with the switch off must read as the message kind, because the app never
     * asked the network and the user has no confirmation to show.
     */
    @Test
    fun metaWordNeedsBothTheSwitchAndAConfirmedDelivery() {
        assertEquals(MessageDetails.Meta.DELIVERED, MessageDetails.meta("delivered", deliveryReports = true))
        assertEquals(MessageDetails.Meta.KIND, MessageDetails.meta("delivered", deliveryReports = false))
        assertEquals(MessageDetails.Meta.SENDING, MessageDetails.meta("sending", deliveryReports = true))
        assertEquals(MessageDetails.Meta.SENDING, MessageDetails.meta("sending", deliveryReports = false))
        assertEquals(MessageDetails.Meta.KIND, MessageDetails.meta("sent", deliveryReports = true))
        assertEquals(MessageDetails.Meta.KIND, MessageDetails.meta("failed", deliveryReports = true))
        assertEquals(MessageDetails.Meta.KIND, MessageDetails.meta("received", deliveryReports = true))
        assertEquals(MessageDetails.Meta.KIND, MessageDetails.meta("", deliveryReports = true))
    }
}
