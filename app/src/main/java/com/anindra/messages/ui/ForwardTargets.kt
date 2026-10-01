package com.anindra.messages.ui

import com.anindra.messages.data.Conversation

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
    private const val MAX_ROWS = 60

    fun digits(value: String): String = value.filter { it.isDigit() }

    fun build(
        contacts: List<Contact>,
        conversations: List<Conversation>,
        query: String
    ): List<ForwardTarget> {
        val queryDigits = digits(query)
        val rows = LinkedHashMap<String, ForwardTarget>()

        contacts.forEach { contact ->
            val key = digits(contact.number)
            if (key.isNotEmpty() && key !in rows) {
                rows[key] = ForwardTarget(contact.name.ifBlank { null }, contact.number)
            }
        }
        conversations.sortedByDescending { it.timestamp }.forEach { conversation ->
            val key = digits(conversation.address)
            val name = conversation.name.ifBlank { null }
            if (key.isNotEmpty() && key !in rows && (name != null || key != digits(query))) {
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
        return matched.take(MAX_ROWS)
    }

    private fun matches(target: ForwardTarget, query: String, queryDigits: String): Boolean {
        if (query.isBlank()) return true
        if (target.name?.contains(query, ignoreCase = true) == true) return true
        val number = digits(target.number)
        return queryDigits.isNotEmpty() && (number.contains(queryDigits) || queryDigits.contains(number))
    }
}
