package com.anindra.messages.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony

internal class MmsProviderReader(private val resolver: ContentResolver) {
    data class Message(
        val id: Long,
        val address: String,
        val timestamp: Long,
        val isMe: Boolean,
        val read: Boolean,
        val subId: Int,
        val content: MmsSupport.Content
    )

    fun read(existing: Set<Long>, consume: (Message) -> Unit) {
        val recipients = mutableMapOf<Long, Int>()
        resolver.query(
            Uri.parse("content://mms-sms/conversations?simple=true"),
            arrayOf("_id", "recipient_ids"), null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                recipients[cursor.getLong(0)] = cursor.getString(1).orEmpty().trim()
                    .split(Regex("\\s+")).filter { it.isNotBlank() }.distinct().size
            }
        }
        resolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf("_id", "thread_id", "date", "msg_box", "m_type", "read", "sub_id"),
            MmsSupport.PROVIDER_SELECTION, null, "date ASC, _id ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                if (id <= 0 || id in existing) continue
                val message = try {
                    val box = cursor.getInt(3)
                    if (!MmsSupport.isImportable(box, cursor.getInt(4))) continue
                    val timestamp = MmsSupport.milliseconds(cursor.getLong(2)) ?: continue
                    val address = MmsSupport.peer(box, addresses(id), recipients[cursor.getLong(1)])
                    if (address == null) {
                        android.util.Log.i("RepoSync", "MMS skipped: group or unverified phone participants")
                        continue
                    }
                    val content = MmsSupport.content(parts(id))
                    if (content.imageId == null && content.body.isBlank()) continue
                    Message(id, address, timestamp, box == 2, cursor.getInt(5) != 0,
                        if (cursor.isNull(6)) -1 else cursor.getInt(6), content)
                } catch (_: Exception) {
                    android.util.Log.w("RepoSync", "MMS skipped: provider content unavailable or unsupported")
                    continue
                }
                consume(message)
            }
        }
    }

    /** Incoming MMS the carrier has announced but that nobody has downloaded
     *  yet; the default SMS app has to fetch it before it can be imported. */
    fun pendingDownloadIds(): List<Long> {
        val result = mutableListOf<Long>()
        resolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf("_id"),
            MmsSupport.PENDING_DOWNLOAD_SELECTION, null, null
        )?.use { cursor -> while (cursor.moveToNext()) result.add(cursor.getLong(0)) }
        return result
    }

    private fun addresses(id: Long): List<MmsSupport.Address> {
        val result = mutableListOf<MmsSupport.Address>()
        val cursor = resolver.query(Uri.parse("content://mms/$id/addr"),
            arrayOf("type", "address"), null, null, null) ?: error("MMS addresses unavailable")
        cursor.use {
            while (it.moveToNext()) result.add(MmsSupport.Address(it.getInt(0), it.getString(1).orEmpty()))
        }
        return result
    }

    private fun parts(id: Long): List<MmsSupport.Part> {
        val result = mutableListOf<MmsSupport.Part>()
        val cursor = resolver.query(Uri.parse("content://mms/$id/part"),
            arrayOf("_id", "ct", "text", "_data", "chset"), null, null, "seq ASC, _id ASC")
            ?: error("MMS parts unavailable")
        cursor.use {
            while (it.moveToNext()) {
                val partId = it.getLong(0)
                val mime = it.getString(1).orEmpty()
                val text = if (MmsSupport.mime(mime) == "text/plain") {
                    if (it.isNull(3)) it.getString(2).orEmpty()
                    else resolver.openInputStream(Uri.parse("content://mms/part/$partId"))?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(MmsSupport.acceptsTextChunk(output.size(), count))
                            output.write(buffer, 0, count)
                        }
                        MmsSupport.decodeText(output.toByteArray(), if (it.isNull(4)) 106 else it.getInt(4))
                    } ?: error("MMS text unavailable")
                } else null
                result.add(MmsSupport.Part(partId, mime, text))
            }
        }
        return result
    }
}
