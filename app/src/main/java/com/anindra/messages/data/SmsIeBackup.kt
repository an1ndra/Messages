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
        val messages = records.mapNotNull { record -> record(record, partBytes) }
        return Parsed(messages, version)
    }

    /**
     * One record, for the streaming importer.
     *
     * Kept separate from [parse] so a 50k backup can be read a record at a time
     * instead of being turned into a list of every message before the first row
     * is written. Returns null for anything unreadable, so one damaged record
     * costs that record rather than the import.
     */
    fun record(json: String, partBytes: (String) -> ByteArray? = { null }): Message? {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        return record(obj, partBytes)
    }

    private fun record(record: JSONObject, partBytes: (String) -> ByteArray?): Message? {
        val isMms = record.has("m_type") || record.has("__parts") ||
            record.has("__sender_address")
        return if (isMms) mms(record, partBytes) else sms(record)
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

/**
 * Copes with backups written by an older build, whose `messages` table is
 * missing columns this one expects.
 *
 * A backup is only rejected earlier for not being a SQLite file at all, so a
 * merge has to cope with an old schema rather than refusing the file the user
 * actually has. A real v8 backup fails with "no such column: locked" otherwise.
 */
object LegacyBackupSchema {

    /**
     * Absent in older backups; each falls back to a constant of the same shape.
     *
     * The order matters: the caller reads these positionally and the original
     * query put transport last, so the order here is locked, sub_id, transport.
     */
    private val OPTIONAL = listOf("locked" to "0", "sub_id" to "-1", "transport" to "'sms'")

    /** Always present, in the order the caller reads them. */
    private val REQUIRED =
        listOf("conversation_id", "body", "timestamp", "is_me", "status", "media_type", "media_uri", "reactions", "sys_id")

    /**
     * Builds the SELECT from the columns the file actually has.
     *
     * A missing optional column becomes a literal, so the result set keeps its
     * shape and the caller's positional reads stay correct. Substituting the
     * column *name* instead would shift every index after it and write the wrong
     * values into the wrong fields.
     */
    fun messagesQuery(available: Set<String>): String {
        val select = (REQUIRED + OPTIONAL.map { it.first })
            .joinToString(",") { column ->
                if (column in available) column
                else OPTIONAL.first { it.first == column }.second
            }
        // deleted_at arrived with soft delete; without it nothing is in the trash.
        val where = if ("deleted_at" in available) "WHERE deleted_at=0" else ""
        return "SELECT $select FROM messages $where ORDER BY timestamp"
    }

    /**
     * Provider ids are meaningful only on the device that issued them.
     *
     * A backup carries them, so restoring it on the *same* phone keeps working:
     * the rows are already mirrored and must not be inserted into the provider a
     * second time. Restoring on a *different* phone is the dangerous case -- every
     * carried id refers to a row that does not exist here, and a reconcile against
     * the provider then concludes the messages were deleted and removes them.
     *
     * So a carried id is kept only if the local provider actually has it;
     * otherwise it is cleared, which leaves the row local-only until it gets
     * mirrored with a fresh id. Returns the ids to keep.
     */
    fun adoptProviderIds(carried: Collection<Long>, liveProviderIds: Set<Long>): Set<Long> =
        carried.filterTo(HashSet()) { it > 0 && it in liveProviderIds }

    /** Positional indexes of the optional columns, in the order above. */
    const val LOCKED_INDEX = 9
    const val SUB_ID_INDEX = 10
    const val TRANSPORT_INDEX = 11
}
