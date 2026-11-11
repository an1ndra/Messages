package com.anindra.messages.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetRouteTest {

    @Test
    fun nothingRequestedMeansNoRoute() {
        assertNull(WidgetRoute.forExtras(openInbox = false, openNewMessage = false))
    }

    @Test
    fun inboxRequestedOpensTheList() {
        assertEquals("list", WidgetRoute.forExtras(openInbox = true, openNewMessage = false))
    }

    @Test
    fun newMessageWinsWhenBothArrive() {
        assertEquals("new", WidgetRoute.forExtras(openInbox = true, openNewMessage = true))
    }
}
