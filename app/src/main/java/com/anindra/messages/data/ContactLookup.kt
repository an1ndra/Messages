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
        // Region-agnostic fallback (#444): a contact stored as "+46701234567"
        // must be found for a conversation addressed as "0701234567", which the
        // platform's provider cannot resolve when the country codes differ.
        return scanFor(context, number)
    }

    /** Contact name for [number], or null when no unique contact matches. */
    fun scanNameFor(context: Context, number: String): String? =
        scanFor(context, number)?.displayName

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

    /**
     * True when two digit strings plausibly identify the same subscriber even
     * when one side carries a country code and the other a trunk prefix the
     * assumed region did not strip. Tail equality (last [MIN_MATCH_DIGITS]
     * digits) is enforced by the caller; this only rules out unrelated
     * collisions.
     */
    fun numbersCompatible(a: String, b: String): Boolean {
        val x = a.filter { it.isDigit() }
        val y = b.filter { it.isDigit() }
        if (x.length < MIN_MATCH_DIGITS || y.length < MIN_MATCH_DIGITS) return false
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val (longer, shorter) = if (x.length >= y.length) x to y else y to x
        if (longer.endsWith(shorter)) return true
        val shorterNoZero = shorter.removePrefix("0")
        if (shorterNoZero.isNotEmpty() && shorterNoZero != shorter &&
            longer.endsWith(shorterNoZero)) return true
        for (drop in 1..3) {
            if (longer.length - drop >= shorter.length &&
                longer.drop(drop).endsWith(shorter)) return true
            if (shorterNoZero.isNotEmpty() && shorterNoZero != shorter &&
                longer.length - drop >= shorterNoZero.length &&
                longer.drop(drop).endsWith(shorterNoZero)) return true
        }
        return false
    }

    /** Drops the cached contact index so the next scan re-reads Contacts. */
    fun invalidateIndex() {
        synchronized(indexLock) { indexEntries = null }
    }

    private data class IndexEntry(val digits: String, val name: String?, val id: String)

    @Volatile private var indexEntries: List<IndexEntry>? = null
    private val indexLock = Any()

    private fun index(context: Context): List<IndexEntry> {
        synchronized(indexLock) {
            indexEntries?.let { return it }
            val out = mutableListOf<IndexEntry>()
            try {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(NUMBER, CONTACT_ID,
                        ContactsContract.Contacts.DISPLAY_NAME),
                    null, null, null
                )?.use { c ->
                    val numIdx = c.getColumnIndex(NUMBER)
                    // Phone._ID and Contacts._ID are both "_id"; on the phone table
                    // "_id" is the data row, so the contact id is CONTACT_ID.
                    val idIdx = c.getColumnIndex(CONTACT_ID)
                    val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    while (c.moveToNext()) {
                        val n = if (numIdx >= 0) c.getString(numIdx) else null
                        val digits = n?.filter { it.isDigit() } ?: ""
                        if (digits.length < MIN_MATCH_DIGITS) continue
                        out += IndexEntry(
                            digits,
                            if (nameIdx >= 0) c.getString(nameIdx) else null,
                            if (idIdx >= 0) (c.getString(idIdx) ?: "") else ""
                        )
                    }
                }
            } catch (_: Exception) {
            }
            indexEntries = out
            return out
        }
    }

    /**
     * Region-agnostic contact lookup: scans the whole Contacts phone table and
     * returns the contact whose number matches [number] by tail + compatibility,
     * but only when the match is unique.
     */
    fun scanFor(context: Context, number: String): SavedContact? {
        val wanted = number.filter { it.isDigit() }
        if (wanted.length < MIN_MATCH_DIGITS) return null
        val wantedTail = wanted.takeLast(MIN_MATCH_DIGITS)
        val matches = index(context).filter {
            it.digits.takeLast(MIN_MATCH_DIGITS) == wantedTail &&
                numbersCompatible(it.digits, wanted)
        }
        if (matches.size != 1) return null
        val e = matches.first()
        if (e.id.isBlank()) return null
        return SavedContact(contactId = e.id, displayName = e.name)
    }

    /** Shortest run of digits worth treating as a subscriber number. */
    const val MIN_MATCH_DIGITS = 7
}
