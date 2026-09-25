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
    fun eachFolderKeepsItsOwnWindow() {
        assertEquals(7, RetentionPolicy.daysFor(RetentionBucket.TRASH, 7, 90))
        assertEquals(90, RetentionPolicy.daysFor(RetentionBucket.KEYWORD_MESSAGES, 7, 90))
        assertEquals(90, RetentionPolicy.daysFor(RetentionBucket.BLOCKED_SENDERS, 7, 90))
    }

    @Test
    fun bothSpamBucketsShareTheSpamWindow() {
        // They are one folder, so one number has to describe it.
        assertEquals(
            RetentionPolicy.daysFor(RetentionBucket.KEYWORD_MESSAGES, 7, 90),
            RetentionPolicy.daysFor(RetentionBucket.BLOCKED_SENDERS, 7, 90)
        )
    }

    @Test
    fun eachBucketCutsOffAtItsOwnWindow() {
        val now = 1_700_000_000_000L
        assertEquals(
            now - 7 * day,
            RetentionPolicy.cutoffFor(RetentionBucket.TRASH, now, 7, 90)
        )
        assertEquals(
            now - 90 * day,
            RetentionPolicy.cutoffFor(RetentionBucket.BLOCKED_SENDERS, now, 7, 90)
        )
    }

    @Test
    fun cutoffIgnoresAnUnsupportedWindow() {
        val now = 1_700_000_000_000L
        assertEquals(
            RetentionPolicy.cutoffFor(RetentionBucket.TRASH, now, 999, 30),
            RetentionPolicy.cutoffFor(RetentionBucket.TRASH, now, 30, 30)
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

    @Test
    fun masterSwitchOffPurgesNothing() {
        assertTrue(
            RetentionPolicy.activeBuckets(
                enabled = false, trash = true, keywordMessages = true, blockedSenders = true
            ).isEmpty()
        )
    }

    @Test
    fun everyBucketCanBeSelectedOnItsOwn() {
        val all = setOf(
            RetentionBucket.TRASH,
            RetentionBucket.KEYWORD_MESSAGES,
            RetentionBucket.BLOCKED_SENDERS
        )
        assertEquals(
            setOf(RetentionBucket.TRASH),
            RetentionPolicy.activeBuckets(true, trash = true, keywordMessages = false, blockedSenders = false)
        )
        assertEquals(
            setOf(RetentionBucket.KEYWORD_MESSAGES),
            RetentionPolicy.activeBuckets(true, trash = false, keywordMessages = true, blockedSenders = false)
        )
        assertEquals(
            setOf(RetentionBucket.BLOCKED_SENDERS),
            RetentionPolicy.activeBuckets(true, trash = false, keywordMessages = false, blockedSenders = true)
        )
        assertEquals(
            all,
            RetentionPolicy.activeBuckets(true, trash = true, keywordMessages = true, blockedSenders = true)
        )
    }

    @Test
    fun nothingSelectedPurgesNothing() {
        assertTrue(
            RetentionPolicy.activeBuckets(
                enabled = true, trash = false, keywordMessages = false, blockedSenders = false
            ).isEmpty()
        )
    }

    @Test
    fun eachBucketMapsToItsOwnPredicate() {
        assertEquals(RetentionPolicy.TRASHED_SQL, RetentionPolicy.sql(RetentionBucket.TRASH))
        assertEquals(
            RetentionPolicy.KEYWORD_BLOCKED_SQL,
            RetentionPolicy.sql(RetentionBucket.KEYWORD_MESSAGES)
        )
        assertEquals(
            RetentionPolicy.BLOCKED_CONVERSATION_SQL,
            RetentionPolicy.sql(RetentionBucket.BLOCKED_SENDERS)
        )
    }
}
