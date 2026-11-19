package com.anindra.messages.ui

import android.content.Intent
import android.provider.ContactsContract

/**
 * What a contact card in the conversation can do.
 *
 * A card is only worth sending if the person who receives it can keep it, so a
 * tap offers the two things a phone does with a vCard: add it to the contacts
 * app, or write the file somewhere it can be opened later.
 */
object ContactActions {

    /** The name and number the insert intent carries, or null when the card has
     *  no number — there is nothing a contact could be created from. */
    fun insertValues(name: String?, number: String?): Pair<String, String>? {
        val phone = number?.trim().orEmpty()
        if (phone.isEmpty()) return null
        return name?.trim().orEmpty() to phone
    }

    /** The contacts app's "new contact" intent, prefilled from the card. */
    fun addIntent(name: String?, number: String?): Intent? {
        val (safeName, phone) = insertValues(name, number) ?: return null
        return Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, safeName)
            putExtra(ContactsContract.Intents.Insert.PHONE, phone)
        }
    }
}
