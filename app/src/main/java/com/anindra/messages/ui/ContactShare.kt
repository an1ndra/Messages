package com.anindra.messages.ui

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/**
 * Reads the contact the picker handed back, so the tray can send it as a vCard.
 *
 * The picker returns a contact URI, not a number: the number lives in the
 * contact's data rows, and a contact can hold several, so the first one a
 * network can dial wins.
 */
internal object ContactShare {

    data class Picked(val name: String, val number: String)

    fun read(context: Context, uri: Uri): Picked? {
        val resolver = context.contentResolver
        val id: String
        var name: String? = null
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            id = cursor.getString(
                cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            ) ?: return null
            name = cursor.getString(
                cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
            )
        } ?: return null

        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(id),
            null
        )?.use { phones ->
            while (phones.moveToNext()) {
                val number = phones.getString(0)?.trim().orEmpty()
                if (ContactCard.isSendable(number)) {
                    return Picked(ContactCard.nameOf(name, number), number)
                }
            }
        }
        return null
    }
}
