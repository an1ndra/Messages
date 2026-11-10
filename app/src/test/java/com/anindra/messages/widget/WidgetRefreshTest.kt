package com.anindra.messages.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefreshTest {

    @Test
    fun nothingInstalledMeansNoUpdate() {
        assertTrue(installedKinds(false, false).isEmpty())
    }

    @Test
    fun onlyPlacedWidgetsAreUpdated() {
        assertEquals(setOf(WidgetKind.LIST), installedKinds(true, false))
        assertEquals(setOf(WidgetKind.UNREAD), installedKinds(false, true))
        assertEquals(setOf(WidgetKind.LIST, WidgetKind.UNREAD), installedKinds(true, true))
    }
}
