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
        val content: MmsSupport.Content,
        /**
         * The provider thread this MMS belongs to, or 0 when the provider does
         * not say. The platform groups a group MMS on it — the `:mms` store
         * writes the same participants for a send and a receive — so it is the
         * conversation's identity (#448).
         */
        val threadId: Long = 0,
        /** Everyone in [threadId], the sender included. Empty when the provider's
         *  address table cannot be read, which keeps the old sender-only routing. */
        val participants: List<String> = emptyList()
    )

    fun read(skip: Set<Long>, consume: (Message) -> Unit) {
        val threads = threadParticipants()
        resolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf("_id", "thread_id", "date", "msg_box", "m_type", "read", "sub_id"),
            MmsSupport.PROVIDER_SELECTION, null, "date ASC, _id ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                if (id <= 0 || id in skip) continue
                val message = try {
                    val box = cursor.getInt(3)
                    if (!MmsSupport.isImportable(box, cursor.getInt(4))) continue
                    val timestamp = MmsSupport.milliseconds(cursor.getLong(2)) ?: continue
                    val threadId = cursor.getLong(1)
                    val participants = threads[threadId].orEmpty()
                    val address = MmsSupport.peer(box, addresses(id), participants.size)
                    if (address == null) {
                        android.util.Log.i("RepoSync", "MMS skipped: group or unverified phone participants")
                        continue
                    }
                    val content = MmsSupport.content(parts(id))
                    if (content.mediaRows().isEmpty() && content.body.isBlank()) continue
                    Message(
                        id, address, timestamp, box == 2, cursor.getInt(5) != 0,
                        if (cursor.isNull(6)) -1 else cursor.getInt(6), content,
                        threadId, participants
                    )
                } catch (e: SecurityException) {
                    // A revoked READ_SMS must abort the pass, not look like one
                    // unreadable row: the next sync retries the import in full.
                    throw e
                } catch (_: Exception) {
                    android.util.Log.w("RepoSync", "MMS skipped: provider content unavailable or unsupported")
                    continue
                }
                consume(message)
            }
        }
    }

    /**
     * Every thread and the people in it.
     *
     * The provider splits a thread's members into `recipient_ids`, canonical
     * address ids; the addresses themselves live in one table. Reading both in
     * two cursors is what Fossify's `getThreadParticipants` does, and it is the
     * only way to know a group MMS's members without opening each message's
     * address rows.
     *
     * A provider that will not hand either table over yields an empty map: the
     * import then routes by sender exactly as before, instead of failing the
     * whole pass.
     */
    private fun threadParticipants(): Map<Long, List<String>> {
        val addresses = runCatching { canonicalAddresses() }.getOrNull().orEmpty()
        if (addresses.isEmpty()) return emptyMap()
        val out = mutableMapOf<Long, List<String>>()
        runCatching {
            resolver.query(
                Uri.parse("content://mms-sms/conversations?simple=true"),
                arrayOf("_id", "recipient_ids"), null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(0)
                    val members = cursor.getString(1).orEmpty()
                        .split(Regex("\\s+"))
                        .mapNotNull { it.trim().toLongOrNull() }
                        .mapNotNull { addresses[it] }
                        .filter { it.isNotBlank() }
                        .distinct()
                    if (members.isNotEmpty()) out[threadId] = members
                }
            }
        }.onFailure {
            android.util.Log.w("RepoSync", "MMS thread recipients unavailable: ${it.message}")
        }
        return out
    }

    private fun canonicalAddresses(): Map<Long, String> {
        val out = mutableMapOf<Long, String>()
        resolver.query(
            Uri.parse("content://mms-sms/canonical-addresses"),
            arrayOf("_id", "address"), null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val address = cursor.getString(1).orEmpty().trim()
                if (id > 0 && address.isNotEmpty()) out[id] = address
            }
        }
        return out
    }

    /** A provider thread and everyone in it. */
    data class ProviderThread(val threadId: Long, val participants: List<String>)

    /**
     * Every provider MMS that belongs to a group thread, keyed by MMS id.
     *
     * The one-shot repair uses this to re-file messages imported before #448;
     * only multi-participant threads are returned, because a 1:1 needs no move.
     */
    fun threadsById(): Map<Long, ProviderThread> {
        val threads = threadParticipants()
        if (threads.isEmpty()) return emptyMap()
        val out = mutableMapOf<Long, ProviderThread>()
        runCatching {
            resolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, "thread_id"), null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val threadId = cursor.getLong(1)
                    val people = threads[threadId].orEmpty()
                    if (id > 0 && people.size > 1) {
                        out[id] = ProviderThread(threadId, people)
                    }
                }
            }
        }.onFailure {
            android.util.Log.w("RepoSync", "MMS thread map unavailable: ${it.message}")
        }
        return out
    }

    data class PartLine(val mime: String, val seq: Int)

    /**
     * Every media part the provider holds, grouped by MMS id, in one cursor.
     *
     * This is what lets the import skip a provider row without asking for its
     * addresses and parts one by one: knowing the part ids is enough to tell
     * whether the message is fully stored. Null when the parts table cannot be
     * read at all, so the caller can fall back to "skip everything already
     * linked" instead of re-reading the whole provider.
     */
    fun mediaPartsById(): Map<Long, List<Long>>? {
        val out = mutableMapOf<Long, MutableList<Long>>()
        val cursor = resolver.query(
            Uri.parse("content://mms/part"),
            arrayOf("_id", "mid", "ct"), null, null, null
        ) ?: return null
        cursor.use {
            while (it.moveToNext()) {
                val partId = it.getLong(0)
                val mid = it.getLong(1)
                val mime = it.getString(2).orEmpty()
                if (partId > 0 && mid > 0 &&
                    (MmsSupport.isImage(mime) || MmsSupport.isVideo(mime))
                ) {
                    out.getOrPut(mid) { mutableListOf() }.add(partId)
                }
            }
        }
        return out
    }

    /** One provider MMS and the parts it actually carries. Diagnostics prints
     *  this so a "only one picture showed" report can be separated from "the
     *  provider only had one picture" (#398). */
    data class MultiPart(val id: Long, val scanned: Int, val parts: List<PartLine>)

    /**
     * The newest provider MMS carrying two or more parts, if any.
     *
     * [scan] bounds the work: only the newest rows are looked at, because the
     * report is answering "what does a multi-part MMS look like here", not
     * counting the whole provider.
     */
    fun newestMultiPart(scan: Int = 20): MultiPart? {
        val ids = mutableListOf<Long>()
        runCatching {
            resolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID),
                null, null, "date DESC, _id DESC LIMIT $scan"
            )?.use { cursor ->
                while (cursor.moveToNext()) ids.add(cursor.getLong(0))
            }
        }
        for (id in ids) {
            val parts = runCatching { partLines(id) }.getOrNull() ?: continue
            if (parts.size >= 2) return MultiPart(id, ids.size, parts)
        }
        return null
    }

    private fun partLines(id: Long): List<PartLine> {
        val result = mutableListOf<PartLine>()
        val cursor = resolver.query(
            Uri.parse("content://mms/$id/part"),
            arrayOf("ct", "seq"), null, null, "seq ASC, _id ASC"
        ) ?: return result
        cursor.use {
            while (it.moveToNext()) result.add(PartLine(it.getString(0).orEmpty(), it.getInt(1)))
        }
        return result
    }

    /** Incoming MMS the carrier has announced but that nobody has downloaded
     *  yet; the default SMS app has to fetch it before it can be imported.     *  The Content-Location (ct_l) is where the platform expects it to be
     *  fetched from — a row without one cannot be requested at all. */
    fun pendingDownloads(): List<MmsSupport.PendingDownload> {
        val columns = arrayOf(
            Telephony.Mms._ID, Telephony.Mms.CONTENT_LOCATION, MmsSupport.PROVIDER_SUBSCRIPTION_ID,
        )
        val rows = runCatching { queryPending(columns, withSubscription = true) }
            .getOrElse {
                // A single-SIM provider has no sub_id column at all, and naming
                // it fails the whole query rather than returning a null column.
                android.util.Log.w("MmsDownload", "provider has no sub_id; pending rows carry no line")
                queryPending(columns.take(2).toTypedArray(), withSubscription = false)
            }
        return rows
    }

    private fun queryPending(columns: Array<String>, withSubscription: Boolean): List<MmsSupport.PendingDownload> {
        val result = mutableListOf<MmsSupport.PendingDownload>()
        resolver.query(
            Telephony.Mms.CONTENT_URI,
            columns,
            MmsSupport.PENDING_DOWNLOAD_SELECTION, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                if (id <= 0) continue
                result.add(
                    MmsSupport.PendingDownload(
                        id, cursor.getString(1), if (withSubscription) cursor.getInt(2) else -1,
                    )
                )
            }
        }
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
