package com.anindra.messages.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reader for backups produced by SMS Import / Export (tmo1/sms-ie).
 *
 * Two layouts are supported, matching that app's own history:
 *  - v2 (2.0.0+): a ZIP holding `messages.ndjson` — one JSON message per
 *    line — plus a `data/` directory with the MMS binary part files, which are
 *    referenced from each part's `_data` tag by filename only.
 *  - v1 (<2.0.0): a single pretty-printed JSON array of the same objects.
 *
 * Tags the exporting app adds are prefixed with `__`. Contact names
 * (`__display_name`) are deliberately ignored: this app resolves names from
 * the device contacts, so trusting a stale name would pin the wrong label.
 */
object SmsIeBackup {

    data class Message(
        val address: String,
        val body: String,
        val timestamp: Long,
        val isMe: Boolean,
        val isMms: Boolean,
        val read: Boolean,
        val status: String,
        val imageBytes: ByteArray? = null,
        val imageMime: String = "",
        val imageName: String = ""
    ) {
        override fun equals(other: Any?): Boolean =
            other is Message && address == other.address && body == other.body &&
                timestamp == other.timestamp && isMe == other.isMe && isMms == other.isMms &&
                read == other.read && status == other.status &&
                imageName == other.imageName && imageMime == other.imageMime &&
                (imageBytes?.size ?: -1) == (other.imageBytes?.size ?: -1)

        override fun hashCode(): Int {
            var result = address.hashCode()
            result = 31 * result + body.hashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + isMe.hashCode()
            result = 31 * result + isMms.hashCode()
            result = 31 * result + status.hashCode()
            result = 31 * result + imageName.hashCode()
            return result
        }
    }

    data class Parsed(val messages: List<Message>, val version: Int)

    /** SMS `type` values (android.provider.Telephony.Sms.MESSAGE_TYPE_*). */
    fun smsStatus(type: Int): Pair<Boolean, String> = when (type) {
        1 -> false to "received"
        2, 3 -> true to "sent"
        4, 6 -> true to "sending"
        5 -> true to "failed"
        else -> false to "received"
    }

    /** MMS `msg_box` values (android.provider.Telephony.Mms.MESSAGE_BOX_*). */
    fun mmsStatus(box: Int): Pair<Boolean, String> = when (box) {
        1 -> false to "received"
        2 -> true to "sent"
        4 -> true to "sending"
        else -> false to "received"
    }

    /** MMS timestamps are exported in seconds while SMS uses milliseconds. */
    fun normalizeTimestamp(raw: Long, isMms: Boolean): Long {
        if (raw <= 0L) return System.currentTimeMillis()
        return if (isMms && raw < 100_000_000_000L) raw * 1000L else raw
    }

    /** [partBytes] resolves an MMS part's `_data` filename to its content. */
    fun parse(json: String, partBytes: (String) -> ByteArray? = { null }): Parsed {
        val trimmed = json.trim()
        val version: Int
        val records: List<JSONObject> = if (trimmed.startsWith("[")) {
            version = 1
            val array = JSONArray(trimmed)
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        } else {
            version = 2
            trimmed.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("{") && it.endsWith("}") }
                .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
                .toList()
        }
        val messages = records.mapNotNull { record ->
            val isMms = record.has("m_type") || record.has("__parts") ||
                record.has("__sender_address")
            if (isMms) mms(record, partBytes) else sms(record)
        }
        return Parsed(messages, version)
    }

    private fun sms(record: JSONObject): Message? {
        val address = record.optString("address").trim()
        if (address.isEmpty()) return null
        val (isMe, status) = smsStatus(record.optInt("type", 1))
        return Message(
            address = address,
            body = record.optString("body"),
            timestamp = normalizeTimestamp(record.optLong("date"), isMms = false),
            isMe = isMe,
            isMms = false,
            read = record.optInt("read", 1) != 0,
            status = status
        )
    }

    private fun mms(record: JSONObject, partBytes: (String) -> ByteArray?): Message? {
        val (isMe, status) = mmsStatus(record.optInt("msg_box", 1))
        val address = mmsPeer(record, isMe)
        if (address.isEmpty()) return null
        val parts = record.optJSONArray("__parts") ?: JSONArray()

        val text = StringBuilder()
        var imageBytes: ByteArray? = null
        var imageMime = ""
        var imageName = ""
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val ct = MmsSupport.mime(part.optString("ct", ""))
            when {
                ct == "text/plain" -> {
                    val inline = part.optString("text")
                    val value = inline.ifBlank {
                        part.optString("_data").substringAfterLast('/')
                            .let { partBytes(it)?.toString(Charsets.UTF_8).orEmpty() }
                    }
                    if (value.isNotBlank()) {
                        if (text.isNotEmpty()) text.append('\n')
                        text.append(value)
                    }
                }
                imageBytes == null && MmsSupport.isImage(ct) -> {
                    val file = part.optString("_data").substringAfterLast('/')
                    imageBytes = partBytes(file)
                    if (imageBytes != null) {
                        imageMime = ct
                        imageName = file.ifBlank { "image.${extensionFor(ct)}" }
                    }
                }
            }
        }
        val body = text.toString().ifBlank { record.optString("sub") }
        return Message(
            address = address,
            body = body,
            timestamp = normalizeTimestamp(record.optLong("date"), isMms = true),
            isMe = isMe,
            isMms = true,
            read = record.optInt("read", 1) != 0,
            status = status,
            imageBytes = imageBytes,
            imageMime = imageMime,
            imageName = imageName
        )
    }

    /** Sent MMS carry the recipient; inbox MMS carry the sender. */
    fun mmsPeer(record: JSONObject, isMe: Boolean): String {
        val sender = record.optJSONObject("__sender_address")?.optString("address")
        if (!isMe && !sender.isNullOrBlank()) return sender.trim()
        val recipients = record.optJSONArray("__recipient_addresses")
        if (recipients != null) {
            for (i in 0 until recipients.length()) {
                val addr = recipients.optJSONObject(i)?.optString("address")
                if (!addr.isNullOrBlank()) return MmsSupport.phoneAddress(addr) ?: addr.trim()
            }
        }
        if (!sender.isNullOrBlank()) return sender.trim()
        return record.optString("address").trim()
    }

    fun extensionFor(mime: String): String = when (MmsSupport.mime(mime)) {
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/bmp" -> "bmp"
        "image/heic", "image/heif" -> "heic"
        "video/mp4" -> "mp4"
        else -> "jpg"
    }
}

/** Decides whether an sms-ie import replaces the existing history or adds to it. */
object SmsIeBackupPolicy {
    /** Restore (REPLACE) wipes the current messages first; Merge keeps them. */
    fun clearsExisting(mode: ImportMode): Boolean = mode == ImportMode.REPLACE
}
