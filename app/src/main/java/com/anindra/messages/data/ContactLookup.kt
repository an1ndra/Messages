package com.anindra.messages.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID
import android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
import android.provider.ContactsContract.PhoneLookup

/**
 * Looks up an already-saved contact by phone number, so the profile screen can
 * offer to open one instead of offering to add it again.
 */
data class SavedContact(
    val contactId: String,
    val displayName: String? = null
) {
    /** The row the Contacts app shows for this person. */
    fun viewUri(): Uri = ContentUris.withAppendedId(
        ContactsContract.Contacts.CONTENT_URI,
        contactId.toLongOrNull() ?: 0L
    )
}

object ContactLookup {

    /**
     * The saved contact owning [number], or null when it is not in Contacts.
     *
     * Matching goes through [PhoneNumberUtils.toE164] so a contact stored as
     * "+15558887777" is found for a conversation addressed as "+1 555 888 7777"
     * or "5558887777". Comparison falls back to a digits-only form for numbers
     * libphonenumber cannot parse, so a short or odd entry still matches itself.
     */
    fun find(context: Context, number: String): SavedContact? {
        val wanted = canonical(number) ?: return null
        if (wanted.isEmpty()) return null

        // The platform's phone_lookup is picky about the format it is handed:
        // it accepts E.164 and the national form, but rejects anything shorter
        // and rejects a leading '+' outright. Try each shape in turn.
        for (candidate in queryForms(number, wanted)) {
            val hit = queryLookup(context, candidate, wanted) ?: continue
            return hit
        }
        return null
    }

    /** The number shapes worth asking the provider about, best first. */
    fun queryForms(number: String, canonicalNumber: String): List<String> {
        val e164 = PhoneNumberUtils.toE164(number, PhoneNumberUtils.region())
        val digits = number.filter { it.isDigit() }
        val forms = linkedSetOf<String>()
        e164?.filter { it.isDigit() }?.let { forms += it }
        if (digits.isNotEmpty()) {
            forms += digits
            // Drop a leading country code: providers index the national form too.
            val regionDigits = PhoneNumberUtils.region()
                .let { PhoneNumberUtils.toE164(number, it) }
                ?.filter { it.isDigit() }
            if (regionDigits != null && digits.length > regionDigits.length) {
                forms += digits.takeLast(digits.length - 1)
            }
        }
        canonicalNumber.filter { it.isDigit() }
            .takeIf { it.isNotEmpty() }
            ?.let { forms += it }
        return forms.toList()
    }

    private fun queryLookup(context: Context, query: String, wanted: String): SavedContact? {
        if (query.isBlank()) return null
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, query)
        // Only columns the phone_lookup view really exposes. Its number column
        // is named "number", not Phone.NUMBER ("number2"), so it is fetched
        // separately below rather than projected here.
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME
        )
        val candidates: List<Pair<String, String?>> = try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                if (idIdx < 0) {
                    emptyList()
                } else {
                    val out = mutableListOf<Pair<String, String?>>()
                    while (c.moveToNext()) {
                        val id = c.getString(idIdx)
                        if (id != null) {
                            val name = if (nameIdx >= 0) c.getString(nameIdx) else null
                            out += id to name
                        }
                    }
                    out
                }
            } ?: emptyList()
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            // A malformed path segment: the provider rejects it outright.
            null
        } catch (_: android.database.sqlite.SQLiteException) {
            null
        } ?: return null

        for ((id, name) in candidates) {
            // The provider matches loosely, so confirm the contact really owns
            // this number before claiming it is already saved.
            val owns = contactNumbers(context, id).any { compareDigits(canonical(it), wanted) }
            if (owns || candidates.size == 1) {
                return SavedContact(contactId = id, displayName = name)
            }
        }
        return null
    }

    private fun contactNumbers(context: Context, contactId: String): List<String> = try {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(NUMBER),
            "${CONTACT_ID}=?",
            arrayOf(contactId),
            null
        )?.use { c ->
            val n = c.getColumnIndex(NUMBER)
            buildList { if (n >= 0) while (c.moveToNext()) add(c.getString(n) ?: "") }
        }.orEmpty()
    } catch (_: SecurityException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    } catch (_: android.database.sqlite.SQLiteException) {
        emptyList()
    }

    /** E.164 where possible, otherwise the last seven digits, which is what
     *  decides a match when a number is too short or malformed to parse. */
    fun canonical(number: String): String? {
        val e164 = PhoneNumberUtils.toE164(number, PhoneNumberUtils.region())
        if (e164 != null) return e164
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return if (digits.length > MIN_MATCH_DIGITS) digits.takeLast(MIN_MATCH_DIGITS) else digits
    }

    /**
     * Last-seven-digits comparison, so country-code differences do not matter.
     * Needs at least [MIN_MATCH_DIGITS] digits: alphanumeric sender IDs such as
     * "ABC123" share their digits with each other, and a short code must not be
     * matched to an unrelated contact.
     */
    fun compareDigits(a: String?, b: String?): Boolean {
        if (a.isNullOrEmpty() || b.isNullOrEmpty()) return false
        val x = a.filter { it.isDigit() }.takeLast(MIN_MATCH_DIGITS)
        val y = b.filter { it.isDigit() }.takeLast(MIN_MATCH_DIGITS)
        return x.length == MIN_MATCH_DIGITS && x == y
    }

    /** Shortest run of digits worth treating as a subscriber number. */
    const val MIN_MATCH_DIGITS = 7
}
