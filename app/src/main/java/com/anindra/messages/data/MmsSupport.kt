package com.anindra.messages.data

import java.nio.charset.Charset
import java.util.Locale

object MmsSupport {
    const val TRANSPORT_SMS = "sms"
    const val TRANSPORT_MMS = "mms"
    const val PROVIDER_SELECTION = "(msg_box=1 AND m_type=132) OR (msg_box=2 AND m_type=128)"
    const val MAX_TEXT_BYTES = 1_048_576

    fun acceptsTextChunk(currentBytes: Int, nextBytes: Int): Boolean =
        currentBytes in 0..MAX_TEXT_BYTES && nextBytes in 0..(MAX_TEXT_BYTES - currentBytes)

    data class Address(val type: Int, val value: String)
    data class Part(val id: Long, val mime: String, val text: String? = null)
    data class Content(val body: String, val imageId: Long?, val omittedParts: Int)

    fun isImportable(box: Int, pduType: Int): Boolean =
        (box == 1 && pduType == 132) || (box == 2 && pduType == 128)

    fun milliseconds(seconds: Long): Long? =
        seconds.takeIf { it in 0..Long.MAX_VALUE / 1000 }?.times(1000)

    fun phoneAddress(raw: String): String? {
        val value = raw.trim().replace(Regex("/TYPE=PLMN$", RegexOption.IGNORE_CASE), "")
        if (!value.matches(Regex("\\+?[0-9 ()\\-.]+"))) return null
        val digits = value.filter { it in '0'..'9' }
        return digits.takeIf { it.length in 3..15 }?.let { if (value.startsWith('+')) "+$it" else it }
    }

    fun peer(box: Int, addresses: List<Address>, threadRecipientCount: Int?): String? {
        if (threadRecipientCount != 1 || box !in 1..2) return null
        if (addresses.any { it.type !in setOf(137, 151) }) return null
        val from = addresses.filter { it.type == 137 && it.value != "insert-address-token" }
        val to = addresses.filter { it.type == 151 }
        if (to.size > 1 || from.size > 1) return null
        if (addresses.any { it.value != "insert-address-token" && phoneAddress(it.value) == null }) return null
        return if (box == 1) from.singleOrNull()?.value?.let(::phoneAddress)
        else to.singleOrNull()?.value?.let(::phoneAddress)
    }

    fun mime(raw: String): String = raw.substringBefore(';').trim().lowercase(Locale.ROOT)

    fun isImage(raw: String): Boolean = mime(raw) in setOf(
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "image/bmp", "image/heic", "image/heif"
    )

    fun content(parts: List<Part>): Content {
        val image = parts.firstOrNull { it.id > 0 && isImage(it.mime) }
        val text = parts.filter { mime(it.mime) == "text/plain" }.mapNotNull { it.text }
            .filter { it.isNotBlank() }.joinToString("\n")
        val omitted = parts.count {
            mime(it.mime) !in setOf("text/plain", "application/smil") && it !== image
        }
        val notice = if (omitted > 0) "[MMS: $omitted additional/unsupported attachment(s) not displayed]" else ""
        return Content(listOf(text, notice).filter { it.isNotEmpty() }.joinToString("\n"), image?.id, omitted)
    }

    fun decodeText(bytes: ByteArray, charset: Int): String {
        val name = when (charset) {
            0, 106 -> "UTF-8"
            3 -> "US-ASCII"
            4 -> "ISO-8859-1"
            17 -> "Shift_JIS"
            1000 -> "UTF-16BE"
            1013 -> "UTF-16BE"
            1014 -> "UTF-16LE"
            1015 -> "UTF-16"
            2026 -> "Big5"
            else -> throw IllegalArgumentException("Unsupported MMS text charset")
        }
        return String(bytes, Charset.forName(name))
    }

    fun providerUri(transport: String): String? = when (transport) {
        TRANSPORT_SMS -> "content://sms"
        TRANSPORT_MMS -> "content://mms"
        else -> null
    }
}
