package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules page draws one section per tab, so the bucketing that feeds it is
 * what decides whether a rule shows up under the tab it files into.
 *
 * A rule listed under the wrong tab is worse than a missing one: it says the
 * chat goes somewhere it does not.
 */
class RuleGroupingTest {

    private fun rule(id: Long, pattern: String, category: InboxCategory) =
        CategoryRule(id, RuleKind.KEYWORD, pattern, category)

    @Test
    fun everyTabGetsASectionEvenWithNoRules() {
        val grouped = InboxCategoryRules.group(emptyList())
        assertEquals(InboxCategory.entries.toSet(), grouped.keys)
        assertTrue(grouped.values.all { it.isEmpty() })
        assertEquals(
            "sections should follow the chip order",
            InboxCategory.entries,
            grouped.keys.toList()
        )
    }

    @Test
    fun eachRuleLandsUnderTheTabItFilesInto() {
        val spam = rule(1, "gift card", InboxCategory.SPAM)
        val bills = rule(2, "HDFCBK", InboxCategory.TRANSACTIONS)
        val grouped = InboxCategoryRules.group(listOf(spam, bills))

        assertEquals(listOf(spam), grouped[InboxCategory.SPAM])
        assertEquals(listOf(bills), grouped[InboxCategory.TRANSACTIONS])
        assertTrue(grouped[InboxCategory.OTP]!!.isEmpty())
        assertTrue(grouped[InboxCategory.PERSONAL]!!.isEmpty())
    }

    @Test
    fun theUsersOwnOrderIsKept() {
        val first = rule(1, "urgent offer", InboxCategory.SPAM)
        val second = rule(2, "free entry", InboxCategory.SPAM)
        val grouped = InboxCategoryRules.group(listOf(first, second))
        assertEquals(listOf(first, second), grouped[InboxCategory.SPAM])
    }
}
