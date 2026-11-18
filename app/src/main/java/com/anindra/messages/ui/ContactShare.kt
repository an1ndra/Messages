package com.anindra.messages.ui

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import java.util.Locale

/**
 * A contact sent as a vCard.
 *
 * The card travels as the message body rather than as an MMS part: the SMS path
 * is what works on every carrier, and a `text/vcard` part that the recipient's
 * app ignores leaves them with an empty bubble. Most messaging apps detect the
 * BEGIN:VCARD block and offer to save it, and anything that does not still shows
 * a readable card with the name and number.
 */
object ContactShare {

    /** vCard 3.0, which is the version every Android contacts app imports. */
    fun vCard(name: String, number: String): String {
        val cleanName = name.trim().ifEmpty { number.trim() }
        val cleanNumber = number.trim()
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:$cleanName")
            appendLine("N:$cleanName;;;;")
            appendLine("TEL;TYPE=CELL:$cleanNumber")
            append("END:VCARD")
        }
    }

    /** The name and first phone number behind a contact uri, or null. */
    fun read(context: Context, uri: Uri): Pair<String, String>? = try {
        val name = context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
            null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

        val id = uri.lastPathSegment
        val number = if (id == null) null else context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
            arrayOf(id),
            null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

        if (number.isNullOrBlank()) null else (name.orEmpty() to number)
    } catch (_: Exception) {
        null
    }

    /** Whether a number looks like something a vCard can carry. */
    fun isSendable(number: String): Boolean =
        number.count { it.isDigit() } >= 3 && number.none { it.isISOControl() }

    @Suppress("unused")
    private fun Locale.isRightToLeft(): Boolean =
        java.text.DecimalFormatSymbols.getInstance(this).zeroDigit != '0'
}
