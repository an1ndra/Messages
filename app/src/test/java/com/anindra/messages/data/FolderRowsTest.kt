package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderRowsTest {

    private fun projection(sql: String): List<String> =
        sql.substringBefore(" FROM ").removePrefix("SELECT").split(",").map { it.trim() }

    @Test
    fun blockedSelectResolvesNameFromContactsColumn() {
        val cols = projection(FolderRows.BLOCKED_SELECT)
        assertTrue(cols[FolderRows.COL_NAME].endsWith("c.name"))
        assertEquals("m.body", cols[FolderRows.COL_BODY])
    }

    @Test
    fun trashedSelectResolvesNameFromContactsColumn() {
        val cols = projection(FolderRows.TRASHED_SELECT)
        assertTrue(cols[FolderRows.COL_NAME].endsWith("c.name"))
        assertFalse(cols[FolderRows.COL_NAME].contains("display"))
    }

    @Test
    fun blockedSelectDoesNotLeakDisplayNumberIntoName() {
        assertFalse(FolderRows.BLOCKED_SELECT.contains("display_destination"))
        assertFalse(FolderRows.BLOCKED_SELECT.contains("participants"))
    }

    @Test
    fun trashedSelectDoesNotLeakDisplayNumberIntoName() {
        assertFalse(FolderRows.TRASHED_SELECT.contains("display_destination"))
        assertFalse(FolderRows.TRASHED_SELECT.contains("participants"))
    }

    @Test
    fun blockedMessageKeepsSavedContactName() {
        val msg = FolderRows.blockedMessage(
            id = 7, conversationId = 2, address = "+15551230010", name = "Sarah",
            body = "kw probe", timestamp = 1_700_000_000_000L
        )
        assertEquals("Sarah", msg.name)
    }

    @Test
    fun trashedMessageKeepsSavedContactName() {
        val msg = FolderRows.trashedMessage(
            id = 8, conversationId = 2, address = "+15551230010", name = "Sarah",
            body = "hello", timestamp = 1_700_000_000_000L, deletedAt = 1_700_000_000_100L
        )
        assertEquals("Sarah", msg.name)
    }
}