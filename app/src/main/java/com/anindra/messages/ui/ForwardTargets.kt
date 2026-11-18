package com.anindra.messages.ui

import com.anindra.messages.data.AddressIdentity
import com.anindra.messages.data.Conversation
import com.anindra.messages.data.PhoneNumberUtils

/** One row in the forward picker: a person, or a bare number the user typed. */
data class ForwardTarget(val name: String?, val number: String)

/**
 * Who a message can be forwarded to.
 *
 * Contacts alone are not enough: the person you most want to forward to is
 * usually someone you have already messaged but never saved, so recent
 * conversations are offered too, and a number typed into the search box becomes
 * a target of its own. Without those two the picker looks broken on a device
 * whose contacts are sparse.
 */
object ForwardTargets {

    /** Long enough to be a real number, short enough to allow an extension. */
    private const val MIN_TYPED_DIGITS = 5

    fun digits(value: String): String = value.filter { it.isDigit() }

    /** The identity two spellings of one number share: E.164 where the region
     *  can parse it, the digits otherwise. The address book holds "0912 123
     *  4567" while the thread is addressed "+989121234567", so keying the rows
     *  on the raw digits listed the same person twice. */
    private fun keyOf(number: String, region: String): String {
        val canonical = AddressIdentity.canonical(number, region)
        return if (canonical.startsWith("+")) canonical else digits(canonical)
    }

    fun build(
        contacts: List<Contact>,
        conversations: List<Conversation>,
        query: String,
        region: String = PhoneNumberUtils.region()
    ): List<ForwardTarget> {
        val queryDigits = digits(query)
        val rows = LinkedHashMap<String, ForwardTarget>()

        contacts.forEach { contact ->
            val key = keyOf(contact.number, region)
            if (key.isNotEmpty() && key !in rows) {
                rows[key] = ForwardTarget(contact.name.ifBlank { null }, contact.number)
            }
        }
        conversations.sortedByDescending { it.timestamp }.forEach { conversation ->
            val key = keyOf(conversation.address, region)
            val name = conversation.name.ifBlank { null }
            // A sender ID has no number to dial, so an SMS to it never leaves
            // the phone; offering it looked like a target and failed on send.
            val replyable = AddressIdentity.isReplyable(conversation.address)
            if (key.isNotEmpty() && replyable && key !in rows &&
                (name != null || key != keyOf(query, region))
            ) {
                rows[key] = ForwardTarget(name, conversation.address)
            }
        }

        val matched = rows.values.filter { matches(it, query, queryDigits) }

        // A number the user typed that matched nobody is still a valid
        // destination. Offered only when the search came up empty: inventing a
        // "forward to 577 1010" row above a contact the same digits already found
        // is worse than useless.
        if (matched.isEmpty() && queryDigits.length >= MIN_TYPED_DIGITS) {
            return listOf(ForwardTarget(null, query.trim()))
        }
        // No row cap: targets are ordered by contact name, so truncating hid
        // everyone past the cut-off and left a phone with many contacts able to
        // scroll no further than the names beginning with A. The picker is a
        // LazyColumn, so a long list costs nothing to scroll.
        return matched
    }

    private fun matches(target: ForwardTarget, query: String, queryDigits: String): Boolean {
        if (query.isBlank()) return true
        if (target.name?.contains(query, ignoreCase = true) == true) return true
        val number = digits(target.number)
        return queryDigits.isNotEmpty() && (number.contains(queryDigits) || queryDigits.contains(number))
    }
}
