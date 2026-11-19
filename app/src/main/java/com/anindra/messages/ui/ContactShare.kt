package com.anindra.messages.ui

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/**
 * A contact sent as a vCard.
 *
 * The card leaves as an MMS part — `text/x-vCard`, named `contact.vcf` — so the
 * receiving phone gets a contact it can save, which a vCard pasted into the text
 * of an SMS does not give it: most apps show that as a wall of `BEGIN:VCARD`
 * properties rather than as a card.
 */
object ContactShare {

    /** What the attachment is declared as on the wire. */
    const val MIME = com.anindra.messages.data.MmsSupport.VCARD_MIME

    /** The name the receiving app sees; its extension is what makes a contact
     *  app treat the part as a card rather than as an unnamed file. */
    const val FILE_NAME = "contact.vcf"

    /** Shortest thing a network will dial. */
    const val MIN_DIGITS = 7

    /**
     * vCard 3.0, which is the version every Android contacts app imports.
     *
     * A line break inside a property ends it, so a two-line contact name is
     * folded to one line and semicolons (the property separator) are dropped:
     * either would leave the receiving app a malformed card.
     */
    fun vCard(name: String, number: String): String {
        val cleanNumber = number.replace("\n", "").replace("\r", "").trim()
        val cleanName = name.trim()
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(";", " ")
            .ifEmpty { cleanNumber }
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:$cleanName")
            appendLine("N:$cleanName;;;;")
            appendLine("TEL;TYPE=CELL:$cleanNumber")
            append("END:VCARD")
        }
    }

    /**
     * Writes the card to a file and hands back the uri MMS reads it from.
     *
     * The picker's own uri cannot travel: its grant ends with the process, and
     * the send path reads the bytes after the composer has moved on. A file in
     * this app's cache is readable for as long as the message needs it.
     */
    fun writeCard(context: Context, name: String, number: String): Uri? = try {
        val dir = File(context.cacheDir, "vcard").apply { mkdirs() }
        val file = File(dir, "contact_${System.currentTimeMillis()}.vcf")
        file.writeText(vCard(name, number))
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: Exception) {
        null
    }

    /** The name and number a card carries, or null when it has no number. */
    fun parse(vCard: String): Pair<String, String>? {
        var name: String? = null
        var number: String? = null
        for (line in vCard.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("FN:", ignoreCase = true) && name == null ->
                    name = trimmed.substringAfter(':').trim()
                trimmed.startsWith("TEL", ignoreCase = true) && number == null ->
                    number = trimmed.substringAfter(':').trim()
            }
        }
        val cleanNumber = number?.takeIf { it.isNotBlank() } ?: return null
        val cleanName = name?.takeIf { it.isNotBlank() } ?: cleanNumber
        return cleanName to cleanNumber
    }

    /** The card behind an attachment uri, for drawing it as a card. */
    fun readCard(context: Context, uri: Uri): Pair<String, String>? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            parse(input.readBytes().toString(Charsets.UTF_8))
        }
    } catch (_: Exception) {
        null
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
        number.count { it.isDigit() } >= MIN_DIGITS && number.none { it.isISOControl() }

    @Suppress("unused")
    private fun Locale.isRightToLeft(): Boolean =
        java.text.DecimalFormatSymbols.getInstance(this).zeroDigit != '0'
}
