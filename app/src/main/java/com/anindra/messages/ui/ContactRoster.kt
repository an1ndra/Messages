package com.anindra.messages.ui

/** One Phone row as Contacts hands it over: a name, a number, and its profile. */
data class ContactRow(val name: String?, val number: String?, val workProfile: Boolean = false)

/**
 * The contact list every picker offers.
 *
 * The personal and the work profile are queried separately but are one list to
 * the user, so a number saved in both is offered once, keeping the personal
 * spelling. A row without a name or a number cannot be offered at all.
 */
object ContactRoster {

    fun merge(rows: List<ContactRow>): List<Contact> {
        val out = mutableListOf<Contact>()
        val seen = HashSet<String>()
        for (row in rows) {
            val name = row.name ?: continue
            val number = row.number ?: continue
            if (seen.add(number.filter { it.isDigit() })) {
                out += Contact(name, number, row.workProfile)
            }
        }
        return out
    }
}
