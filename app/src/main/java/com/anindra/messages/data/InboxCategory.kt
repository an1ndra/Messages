package com.anindra.messages.data

/** Which tab of the inbox a thread belongs to.
 *
 *  Google Messages sorts the inbox the same way and for the same reason: a bank
 *  alert and a friend's text are not the same kind of thing, and the one you
 *  only need to check should not sit between the ones you need to read. */
enum class InboxCategory(val key: String) {
    PERSONAL("personal"),
    TRANSACTIONS("transactions"),
    OTP("otp"),
    /** Not a block: a thread the user's own rules pushed here still sits in the
     *  inbox, it is only filed away from the rest. */
    SPAM("spam");

    companion object {
        /** The category stored under [key], or null for a blank/unknown value —
         *  an unclassified thread shows under "All" and nowhere else. */
        fun fromKey(key: String): InboxCategory? =
            entries.firstOrNull { it.key == key }
    }
}

/** What a user rule matches on. */
enum class RuleKind(val key: String) {
    /** A word or phrase inside the message. */
    KEYWORD("keyword"),

    /** Who sent it — a number in any spelling, or an alphanumeric sender ID. */
    SENDER("sender");

    companion object {
        fun fromKey(key: String): RuleKind? = entries.firstOrNull { it.key == key }
    }
}

/** One line of the user's own filing rules. */
data class CategoryRule(
    val id: Long,
    val kind: RuleKind,
    val pattern: String,
    val category: InboxCategory
)

/**
 * How a thread gets its category.
 *
 * The decision is made once, when the inbox first sees the conversation, and
 * stored on the row — so a reply never moves a thread, and the same message is
 * never classified differently on two phones.
 *
 * The user's own rules are consulted first and win outright: someone who says
 * "anything from AD-AMAZON is Spam" means it. With no rule in play, only two
 * signals are trustworthy enough to use: a code the app already recognises as
 * an OTP, and who sent the message. The rest is a judgement about the sender's
 * shape, and a wrong guess costs only a chip the reader ignores.
 */
object InboxCategoryRules {

    /** A shortcode or an alphabetic sender ID is how businesses send: banks,
     *  deliveries, airlines. A person typing on a phone is neither. */
    private const val SHORTCODE_MAX_DIGITS = 6

    /** Words that make a long-code sender a bill rather than a person when the
     *  address itself is unremarkable. Kept short on purpose: every extra word
     *  is another way to file a friend's text under Transactions. */
    private val BILL_WORDS = Regex(
        "(?i)\\b(bill|invoice|statement|due|payment received|debited|credited|" +
            "order|delivery|recharged|balance|emi|policy)\\b"
    )

    /** The same wording in the languages a bank here actually writes in. A
     *  Persian transfer is "برداشت مبلغ ۵۰۰٬۰۰۰ ریال" and carries none of the
     *  English words above, so without this it was filed as Personal. */
    private val BILL_WORDS_LOCAL = Regex(
        "برداشت|واریز|مبلغ|مانده|خرید|تراکنش|ریال|تومان|رصيد|خصم|ايداع|ريال|درهم"
    )

    /** The first rule that matches, in the order the user saved them. */
    fun applyRules(
        address: String,
        body: String,
        rules: List<CategoryRule>
    ): InboxCategory? {
        for (rule in rules) {
            val hit = when (rule.kind) {
                RuleKind.KEYWORD -> rule.pattern.isNotBlank() &&
                    body.contains(rule.pattern, ignoreCase = true)
                RuleKind.SENDER -> sameSender(address, rule.pattern)
            }
            if (hit) return rule.category
        }
        return null
    }

    /**
     * The rules bucketed by the tab they file into, in tab order, with an empty
     * list for a tab nobody has written a rule for yet.
     *
     * The page shows every tab whether or not it has rules — a destination you
     * cannot see is one you would not think to file into — so this always
     * returns all of them, and never re-orders the user's own rules.
     */
    fun group(rules: List<CategoryRule>): Map<InboxCategory, List<CategoryRule>> =
        InboxCategory.entries.associateWith { category ->
            rules.filter { it.category == category }
        }

    /**
     * What a thread's category becomes when a code arrives in it.
     *
     * A thread is filed once, from the message that created it, so a bank that
     * opened with "debited Rs 500" stayed under Transactions and every OTP that
     * followed was filed there too. A code is the one signal strong enough to
     * move a thread — but a thread the user filed by their own rule stays where
     * that rule put it, and Spam never moves.
     *
     * @return the category to store, or null to leave the thread alone.
     */
    fun afterOtp(current: String, ruleMatched: Boolean): String? = when {
        ruleMatched -> null
        current == InboxCategory.OTP.key || current == InboxCategory.SPAM.key -> null
        else -> InboxCategory.OTP.key
    }

    /** The text a thread's tab is decided from. The list preview is the newest
     *  message: a later "Thanks" must not hide the code or debit that opened
     *  the thread. A thread with no messages yet falls back to the preview. */
    fun openingBody(firstMessage: String, preview: String): String =
        firstMessage.ifBlank { preview }

    /** A number matches however either side is written; an alphanumeric sender
     *  ID can only match itself, case aside. */
    private fun sameSender(address: String, pattern: String): Boolean {
        val p = pattern.trim()
        if (p.isEmpty()) return false
        if (p.any { it.isLetter() } || address.any { it.isLetter() }) {
            return address.trim().equals(p, ignoreCase = true)
        }
        return AddressIdentity.samePerson(address, p)
    }

    /**
     * @param address the sender the thread is addressed to
     * @param body the message text that arrived
     * @param hasOtpCode true when [com.anindra.messages.ui.OtpDetector] found a
     *   code in the message that arrived
     * @param savedContact true when the address belongs to someone in the
     *   phone book — the strongest signal that a message is personal
     * @param rules the user's own filing rules, consulted first
     */
    fun classify(
        address: String,
        body: String,
        hasOtpCode: Boolean,
        savedContact: Boolean,
        rules: List<CategoryRule> = emptyList()
    ): InboxCategory {
        applyRules(address, body, rules)?.let { return it }
        // A code the app is willing to offer to autofill is the one category
        // that can be certain, so it wins over everything else.
        if (hasOtpCode) return InboxCategory.OTP
        if (savedContact) return InboxCategory.PERSONAL
        val trimmed = address.trim()
        // A sender ID with letters in it cannot be a person's phone number.
        if (trimmed.any { it.isLetter() }) return InboxCategory.TRANSACTIONS
        val digits = trimmed.count { it.isDigit() }
        if (digits in 1..SHORTCODE_MAX_DIGITS) return InboxCategory.TRANSACTIONS
        if (BILL_WORDS.containsMatchIn(body) || BILL_WORDS_LOCAL.containsMatchIn(body)) {
            return InboxCategory.TRANSACTIONS
        }
        // A full-length number that is not a saved contact is still a person:
        // filing a stranger's text under Bills would be worse than leaving it
        // next to them.
        return InboxCategory.PERSONAL
    }
}
