package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A thread is filed once, from the message that opened it, so a bank that
 * started with a debit stayed under Transactions and its OTPs went there too.
 * A code moves the thread — unless the user's own rule says otherwise.
 */
class InboxCategoryOtpTest {

    @Test
    fun aCodeMovesATransactionThread() {
        assertEquals("otp", InboxCategoryRules.afterOtp("transactions", ruleMatched = false))
    }

    @Test
    fun aCodeMovesAPersonalThread() {
        assertEquals("otp", InboxCategoryRules.afterOtp("personal", ruleMatched = false))
    }

    @Test
    fun anUnclassifiedThreadBecomesOtp() {
        assertEquals("otp", InboxCategoryRules.afterOtp("", ruleMatched = false))
    }

    @Test
    fun aUserRuleWins() {
        // "Anything from AD-AMAZON is Transactions" means it, even when a code
        // arrives from that sender.
        assertNull(InboxCategoryRules.afterOtp("transactions", ruleMatched = true))
    }

    @Test
    fun spamNeverMoves() {
        assertNull(InboxCategoryRules.afterOtp("spam", ruleMatched = false))
    }

    @Test
    fun aThreadAlreadyInOtpIsLeftAlone() {
        assertNull(InboxCategoryRules.afterOtp("otp", ruleMatched = false))
    }

    @Test
    fun aCodeFromAShortcodeIsStillAnOtp() {
        // The classification a code overrides is the sender-shape guess, and a
        // shortcode is exactly what a bank sends from.
        val bySender = InboxCategoryRules.classify(
            address = "VM-HDFCBK", body = "Debited Rs.500 from A/c", hasOtpCode = false,
            savedContact = false
        )
        assertEquals(InboxCategory.TRANSACTIONS, bySender)
        assertEquals(
            InboxCategory.OTP,
            InboxCategoryRules.classify(
                address = "VM-HDFCBK", body = "Use 1234 to verify your login", hasOtpCode = true,
                savedContact = false
            )
        )
    }
}
