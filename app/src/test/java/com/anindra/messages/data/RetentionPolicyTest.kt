package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionPolicyTest {

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun defaultsToThirtyDays() {
        assertEquals(30, RetentionPolicy.DEFAULT_DAYS)
        assertEquals(30, RetentionPolicy.normalizedDays(0))
        assertEquals(30, RetentionPolicy.normalizedDays(-5))
    }

    @Test
    fun onlyAdvertisedOptionsSurvive() {
        RetentionPolicy.DAY_OPTIONS.forEach {
            assertEquals(it, RetentionPolicy.normalizedDays(it))
        }
        assertEquals(30, RetentionPolicy.normalizedDays(45))
    }

    @Test
    fun cutoffIsNowMinusTheWindow() {
        val now = 1_700_000_000_000L
        assertEquals(now - 30 * day, RetentionPolicy.cutoff(now, 30))
        assertEquals(now - 7 * day, RetentionPolicy.cutoff(now, 7))
    }

    @Test
    fun cutoffIgnoresAnUnsupportedWindow() {
        val now = 1_700_000_000_000L
        assertEquals(
            RetentionPolicy.cutoff(now, 30),
            RetentionPolicy.cutoff(now, 999)
        )
    }

    @Test
    fun keywordBlockedMessagesAreAgedByTheirOwnDeletedAt() {
        val sql = RetentionPolicy.KEYWORD_BLOCKED_SQL
        assertTrue(sql.contains("deleted_at>0"))
        assertTrue(sql.contains("blocked_reason!=''"))
        assertTrue(sql.contains("deleted_at<?"))
    }

    @Test
    fun trashedThreadsAreAgedByWhenTheyWereTrashed() {
        val sql = RetentionPolicy.TRASHED_SQL
        assertTrue(sql.contains("deleted_at>0"))
        assertTrue(sql.contains("deleted_at<?"))
    }

    @Test
    fun blockedSendersAreAgedByLastActivityNotByTrash() {
        val sql = RetentionPolicy.BLOCKED_CONVERSATION_SQL
        assertTrue(sql.contains("blocked=1"))
        assertTrue(sql.contains("timestamp<?"))
        assertTrue("must not key off deleted_at, which is never set for a blocked sender",
            !sql.contains("deleted_at"))
    }

    @Test
    fun noPredicateUsesATableAlias() {
        listOf(
            RetentionPolicy.TRASHED_SQL,
            RetentionPolicy.KEYWORD_BLOCKED_SQL,
            RetentionPolicy.BLOCKED_CONVERSATION_SQL
        ).forEach { sql ->
            assertTrue("\"$sql\" must not qualify columns: Android's SQLite rejects " +
                "DELETE FROM <table> <alias>", !Regex("\\b\\w+\\.").containsMatchIn(sql))
        }
    }
}
