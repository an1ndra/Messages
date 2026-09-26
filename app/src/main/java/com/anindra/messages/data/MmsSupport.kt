package com.anindra.messages.data

import java.nio.charset.Charset
import java.util.Locale

object MmsSupport {
    const val TRANSPORT_SMS = "sms"
    const val TRANSPORT_MMS = "mms"
    const val PROVIDER_SELECTION = "(msg_box=1 AND m_type=132) OR (msg_box=2 AND m_type=128)"
    const val MAX_TEXT_BYTES = 1_048_576

    /** Inbox/outbox PDU types. 130 is an incoming MMS the carrier has only
     *  announced; it has no parts until the default SMS app downloads it. */
    const val PDU_SEND_REQ = 128
    const val PDU_NOTIFICATION_IND = 130
    const val PDU_RETRIEVE_CONF = 132
    const val PENDING_DOWNLOAD_SELECTION = "msg_box=1 AND m_type=$PDU_NOTIFICATION_IND"
    const val DOWNLOAD_RETRY_COOLDOWN_MS = 5 * 60_000L

    fun acceptsTextChunk(currentBytes: Int, nextBytes: Int): Boolean =
        currentBytes in 0..MAX_TEXT_BYTES && nextBytes in 0..(MAX_TEXT_BYTES - currentBytes)

    data class Address(val type: Int, val value: String)
    data class Part(val id: Long, val mime: String, val text: String? = null)
    data class Content(val body: String, val imageId: Long?, val omittedParts: Int)
    data class InboundMms(val address: String, val body: String, val timestamp: Long)

    fun isImportable(box: Int, pduType: Int): Boolean =
        (box == 1 && pduType == PDU_RETRIEVE_CONF) || (box == 2 && pduType == PDU_SEND_REQ)

    /** An announced-but-undownloaded incoming MMS: the platform hands the row
     *  to the default SMS app, which must download it before it can be read. */
    fun isPendingDownload(box: Int, pduType: Int): Boolean =
        box == 1 && pduType == PDU_NOTIFICATION_IND

    fun shouldRetryDownload(lastAttemptAt: Long, now: Long): Boolean =
        lastAttemptAt <= 0L || now - lastAttemptAt >= DOWNLOAD_RETRY_COOLDOWN_MS

    fun messageContentUri(id: Long): String = "content://mms/$id"

    /** ContentResolver often reports no type for FileProvider URIs; MMS still
     *  needs a concrete content type on the wire. */
    fun defaultAttachmentMime(contentType: String?, uri: String): String {
        if (!contentType.isNullOrBlank()) return contentType.substringBefore(';').trim()
        val lower = uri.lowercase()
        return when {
            lower.contains("video") || VIDEO_EXTENSIONS.any { lower.endsWith(it) } -> "video/mp4"
            lower.contains("audio") || AUDIO_EXTENSIONS.any { lower.endsWith(it) } -> "audio/mp4"
            else -> "image/jpeg"
        }
    }

    private val VIDEO_EXTENSIONS = listOf(".mp4", ".3gp", ".mkv", ".webm")
    private val AUDIO_EXTENSIONS = listOf(".m4a", ".mp3", ".amr", ".ogg", ".aac")

    data class OutgoingPart(val name: String, val mimeType: String, val isText: Boolean)
    /** Attachment first, then an optional text part. A SMIL part is prepended
     *  separately because several carriers reject an MMS without one. */
    fun outgoingParts(attachmentMime: String, caption: String): List<OutgoingPart> = buildList {
        add(OutgoingPart("image", attachmentMime, false))
        if (caption.isNotBlank()) add(OutgoingPart("text", "text/plain", true))
    }

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

    private val EXTENSIONS = mapOf(
        "image/jpeg" to "jpg", "image/jpg" to "jpg", "image/png" to "png",
        "image/gif" to "gif", "image/webp" to "webp", "image/bmp" to "bmp",
        "image/heic" to "heic", "image/heif" to "heif", "video/mp4" to "mp4"
    )

    /** Fallback mime for a saved attachment when the resolver reports none. */
    fun mimeForSavedAttachment(uri: String, resolved: String?): String {
        if (!resolved.isNullOrBlank()) return resolved.substringBefore(';').trim()
        val lower = uri.lowercase()
        return EXTENSIONS.entries.firstOrNull { lower.endsWith(".${it.value}") }?.key ?: "image/jpeg"
    }

    /** Filename for a saved attachment: "<contact>_<timestamp>.<ext>". Spaces are
     *  kept (the gallery shows them); only path-unsafe characters are dropped. */
    fun savedAttachmentName(contactName: String, timestamp: Long, mime: String): String {
        val safe = contactName.trim()
            .replace(Regex("[/\\\\:*?\"<>|\\x00-\\x1F]"), "_")
            .trim('_', ' ')
            .take(32)
            .ifBlank { "message" }
        val ext = EXTENSIONS[mime.substringBefore(';').trim()] ?: "jpg"
        return "${safe}_$timestamp.$ext"
    }
}
