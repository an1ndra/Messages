package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InboxCategoryTest {

    private fun classify(
        address: String,
        body: String = "",
        otp: Boolean = false,
        saved: Boolean = false
    ) = InboxCategoryRules.classify(address, body, otp, saved)

    @Test
    fun aCodeTheAppWouldOfferToAutofillIsAnOtp() {
        // The one signal that can be certain wins over the sender's shape: a
        // bank sending a verification code is still an OTP first.
        assertEquals(InboxCategory.OTP, classify("AD-MELLAT", "Your code is 482913", otp = true))
        assertEquals(
            InboxCategory.OTP,
            classify("+1555771010", "کد ورود: ۴۸۲۹۱۳", otp = true, saved = true)
        )
    }

    @Test
    fun aSavedContactIsPersonalWhateverTheWording() {
        assertEquals(
            InboxCategory.PERSONAL,
            classify("+1555771010", "Your order has been delivered", saved = true)
        )
    }

    @Test
    fun anAlphabeticSenderIsABusiness() {
        assertEquals(InboxCategory.TRANSACTIONS, classify("AD-AMAZON", "Your package is out"))
        assertEquals(InboxCategory.TRANSACTIONS, classify("VM-HDFCBK", "Dear customer"))
    }

    @Test
    fun aShortcodeIsABusiness() {
        assertEquals(InboxCategory.TRANSACTIONS, classify("51234", "Balance: 1,200"))
        assertEquals(InboxCategory.TRANSACTIONS, classify("999", "Recharge done"))
    }

    @Test
    fun aFullNumberWithBillWordingIsTransactions() {
        assertEquals(
            InboxCategory.TRANSACTIONS,
            classify("+1555775020", "Rs 1,200 debited from your account")
        )
    }

    @Test
    fun aFullNumberFromAStrangerIsStillAPerson() {
        // Filing one person's text under Bills is worse than leaving them in
        // Personal: the tab is a hint, not a rule.
        assertEquals(InboxCategory.PERSONAL, classify("+1555771010", "see you at 8"))
    }

    @Test
    fun aPersianBankTransferIsNotFiledAsPersonal() {
        assertEquals(
            InboxCategory.TRANSACTIONS,
            classify("+989998620453", "برداشت مبلغ 500,000 ریال")
        )
    }

    @Test
    fun theUsersOwnRuleBeatsTheAppsGuess() {
        // "anything with gift card is Spam" has to mean it, even though the
        // sender is a saved contact the heuristics would call Personal.
        val spam = CategoryRule(1, RuleKind.KEYWORD, "gift card", InboxCategory.SPAM)
        assertEquals(
            InboxCategory.SPAM,
            InboxCategoryRules.classify(
                "+1555771050",
                "You won a gift card, claim it",
                hasOtpCode = false,
                savedContact = true,
                rules = listOf(spam)
            )
        )
    }

    @Test
    fun aSenderRuleMatchesAnySpellingOfTheNumber() {
        val rule = CategoryRule(1, RuleKind.SENDER, "+1555771010", InboxCategory.SPAM)
        listOf("+1555771010", "1555771010", "+1 555 771 010").forEach { spelling ->
            assertEquals(
                "rule did not match $spelling",
                InboxCategory.SPAM,
                InboxCategoryRules.classify(spelling, "hi", false, true, listOf(rule))
            )
        }
    }

    @Test
    fun anAlphanumericSenderRuleIgnoresCase() {
        val rule = CategoryRule(1, RuleKind.SENDER, "ad-amazon", InboxCategory.OTP)
        assertEquals(
            InboxCategory.OTP,
            InboxCategoryRules.classify("AD-AMAZON", "anything", false, false, listOf(rule))
        )
    }

    @Test
    fun theFirstMatchingRuleWins() {
        val rules = listOf(
            CategoryRule(1, RuleKind.KEYWORD, "invoice", InboxCategory.TRANSACTIONS),
            CategoryRule(2, RuleKind.KEYWORD, "invoice", InboxCategory.SPAM)
        )
        assertEquals(
            InboxCategory.TRANSACTIONS,
            InboxCategoryRules.classify("+1555771010", "invoice attached", false, true, rules)
        )
    }

    @Test
    fun noMatchingRuleLeavesTheHeuristicsAlone() {
        val rule = CategoryRule(1, RuleKind.KEYWORD, "unrelated", InboxCategory.SPAM)
        assertEquals(
            InboxCategory.PERSONAL,
            InboxCategoryRules.classify("+1555771010", "see you at 8", false, true, listOf(rule))
        )
    }

    @Test
    fun theStoredKeyRoundTrips() {
        InboxCategory.entries.forEach { category ->
            assertEquals(category, InboxCategory.fromKey(category.key))
        }
        assertNull(InboxCategory.fromKey(""))
        assertNull(InboxCategory.fromKey("promotions"))
        assertNull(InboxCategory.fromKey("otp-code"))
    }
}
