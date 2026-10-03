package com.anindra.messages.mms.store

import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.BaseColumns
import android.provider.Telephony.Mms.Addr
import android.provider.Telephony.Mms.Part
import android.provider.Telephony.MmsSms.PendingMessages
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/**
 * An in-memory stand-in for the telephony provider.
 *
 * It routes on the exact uri shapes the store uses — the bare `content://mms` a
 * column probe hits, a box path, `content://mms/<id>`, `/part`, `/addr`,
 * `content://mms/part/<id>`, `content://mms-sms/threadID` and
 * `content://mms-sms/pending` — so a wrong uri is a routing miss rather than a
 * silent success. A projection narrows the cursor's columns the way a real query
 * does, which is what makes the store's own projection load-bearing.
 *
 * [operations] is the call log the ordering assertions read.
 */
class FakeContentResolver(
    private val hasSubIdColumn: Boolean = true,
) : ContentResolver() {

    val operations = mutableListOf<String>()

    var nextMessageId = 1L
    var nextPartId = 100L
    var nextThreadId = 500L
    var nextPendingId = 900L

    private val messages = LinkedHashMap<Long, LinkedHashMap<String, Any?>>()
    private val parts = LinkedHashMap<Long, LinkedHashMap<String, Any?>>()
    private val blobs = HashMap<Long, ByteArray>()
    private val addresses = mutableListOf<LinkedHashMap<String, Any?>>()
    private val pending = mutableListOf<LinkedHashMap<String, Any?>>()
    private val threads = LinkedHashMap<String, Long>()

    private val selections = mutableListOf<Query>()

    /** Makes any call whose description it matches throw, the way the provider does. */
    var failMatching: (String) -> Boolean = { false }

    data class Query(val uri: String, val selection: String?, val selectionArgs: Array<String>?)

    fun message(id: Long): Map<String, Any?>? = messages[id]

    fun messages(): List<Map<String, Any?>> = messages.values.toList()

    /** The participants of every `content://mms-sms/threadID` query, in call order. */
    fun queriedThreadParticipants(): List<List<String>> = selections
        .filter { it.uri.startsWith(THREAD_ID) }
        .map { recipientsOf(it.uri) }

    fun partsOf(messageId: Long): List<Map<String, Any?>> =
        parts.values.filter { it[Part.MSG_ID] == messageId }

    fun blobOf(partId: Long): ByteArray? = blobs[partId]

    fun addressesOf(messageId: Long): List<Map<String, Any?>> =
        addresses.filter { it[Addr.MSG_ID] == messageId }

    fun pendingRows(): List<Map<String, Any?>> = pending.toList()

    fun queries(): List<Query> = selections.toList()

    fun seedMessage(values: Map<String, Any?>): Long {
        val id = nextMessageId++
        messages[id] = LinkedHashMap(values).apply { put(BaseColumns._ID, id) }
        return id
    }

    fun seedPendingRow(values: Map<String, Any?>) {
        val id = values[BaseColumns._ID] ?: nextPendingId++
        pending += LinkedHashMap(values).apply { put(BaseColumns._ID, id) }
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        val call = "query $uri"
        operations += call
        fail(call)
        selections += Query(uri.toString(), selection, selectionArgs)

        val segments = uri.pathSegments
        val row = when {
            uri.toString() == MESSAGES -> {
                if (!hasSubIdColumn) throw SQLiteException("no such column: sub_id")
                return FakeCursor(projection?.toList() ?: listOf(SUB_ID), listOf(mapOf(SUB_ID to 1)))
            }
            uri.toString().startsWith(THREAD_ID) -> threadRow(uri)
            uri.toString().startsWith(PENDING) -> return pendingCursor(selection, selectionArgs)
            segments.size == 1 -> return FakeCursor(
                projection?.toList() ?: listOf(BaseColumns._ID, "msg_box", "m_type"),
                messages.values.toList(),
            )
            segments.size == 2 && segments[1] == PART ->
                // The provider serves a message's parts in seq order, which is how a
                // SMIL part forced to -1 comes back ahead of the start part.
                partsOf(segments[0].toLong()).sortedBy {
                    (it["seq"] as? Number)?.toInt() ?: 0
                }
            segments.size == 2 && segments[1] == ADDR -> addressesOf(segments[0].toLong())
            segments.size == 2 -> listOfNotNull(messages[segments[0].toLong()])
            else -> throw IllegalArgumentException("unsupported query uri $uri")
        }
        return FakeCursor(projection?.toList() ?: row.flatMap { it.keys }.distinct(), row)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val call = "insert $uri"
        operations += call
        fail(call)
        val segments = uri.pathSegments
        val columns = values?.snapshot() ?: emptyMap()
        return when {
            segments.size == 1 -> insertMessage(segments[0], columns)
            segments.size == 2 && segments[1] == PART -> insertPart(segments[0].toLong(), columns)
            segments.size == 2 && segments[1] == ADDR -> {
                addresses += LinkedHashMap(columns).apply { put(Addr.MSG_ID, segments[0].toLong()) }
                uri
            }
            else -> throw IllegalArgumentException("unsupported insert uri $uri")
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int {
        val call = "update $uri"
        operations += call
        fail(call)
        val columns = values?.snapshot() ?: emptyMap()
        val segments = uri.pathSegments
        return when {
            segments.size == 2 && segments[1] == PART -> reparentParts(segments[0].toLong(), columns)
            segments.size == 2 && segments[1] == ADDR -> 0
            segments.size == 2 -> {
                val message = messages[segments[0].toLong()] ?: return 0
                message.putAll(columns)
                1
            }
            segments.size == 3 && segments[0] == "pending" -> {
                val row = pending.firstOrNull { it[BaseColumns._ID] == segments[1].toLong() }
                    ?: return 0
                row.putAll(columns)
                1
            }
            else -> throw IllegalArgumentException("unsupported update uri $uri")
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val call = "delete $uri"
        operations += call
        fail(call)
        val segments = uri.pathSegments
        return when {
            segments.size == 2 -> {
                val id = segments[0].toLong()
                val removed = messages.remove(id) != null
                partsOf(id).forEach { parts.remove(it[BaseColumns._ID] as Long) }
                addresses.removeAll { it[Addr.MSG_ID] == id }
                if (removed) 1 else 0
            }
            segments.size == 3 && segments[0] == "pending" ->
                if (pending.removeAll { it[BaseColumns._ID] == segments[1].toLong() }) 1 else 0
            else -> throw IllegalArgumentException("unsupported delete uri $uri")
        }
    }

    override fun openInputStream(uri: Uri): InputStream {
        val call = "openInputStream $uri"
        operations += call
        fail(call)
        val partId = partIdOf(uri)
        val data = blobs[partId] ?: throw FileNotFoundException(uri.toString())
        return ByteArrayInputStream(data)
    }

    override fun openOutputStream(uri: Uri): OutputStream {
        val call = "openOutputStream $uri"
        operations += call
        fail(call)
        val partId = partIdOf(uri)
        val buffer = ByteArrayOutputStream()
        return object : OutputStream() {
            override fun write(byte: Int) = buffer.write(byte)

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                buffer.write(bytes, offset, length)
            }

            override fun close() {
                blobs[partId] = buffer.toByteArray()
            }
        }
    }

    private fun partIdOf(uri: Uri): Long =
        uri.pathSegments.lastOrNull()?.toLongOrNull()
            ?: throw FileNotFoundException(uri.toString())

    private fun insertMessage(box: String, columns: Map<String, Any?>): Uri {
        if (box !in BOXES) throw IllegalArgumentException("no such message box: $box")
        val id = nextMessageId++
        messages[id] = LinkedHashMap(columns).apply { put(BaseColumns._ID, id) }
        return Uri.parse("$MESSAGES/$box/$id")
    }

    private fun insertPart(messageId: Long, columns: Map<String, Any?>): Uri {
        val id = nextPartId++
        parts[id] = LinkedHashMap(columns).apply {
            put(BaseColumns._ID, id)
            put(Part.MSG_ID, messageId)
        }
        return Uri.parse("$MESSAGES/$PART/$id")
    }

    /**
     * The provider re-points every part of one message at another message id. The
     * placeholder and the real id are rows of the same table, so a bare "set mid"
     * would move somebody else's parts.
     */
    private fun reparentParts(fromMessageId: Long, columns: Map<String, Any?>): Int {
        val to = columns[Part.MSG_ID] ?: return 0
        var moved = 0
        partsOf(fromMessageId).forEach { part ->
            val row = parts[part[BaseColumns._ID]] ?: return@forEach
            row[Part.MSG_ID] = to
            moved++
        }
        return moved
    }

    private fun threadRow(uri: Uri): List<Map<String, Any?>> {
        val participants = recipientsOf(uri.toString())
        val id = threads.getOrPut(participants.sorted().joinToString(",")) { nextThreadId++ }
        return listOf(mapOf(BaseColumns._ID to id))
    }

    /**
     * Read the participants straight out of the uri text. Kotlin resolves `Uri` to
     * the classpath one when compiling, so its query accessors are not on the
     * declared type here even though the shadow loaded at runtime has them.
     */
    private fun recipientsOf(uri: String): List<String> = uri
        .substringAfter('?', "")
        .split('&')
        .filter { it.startsWith("$RECIPIENT=") }
        .map { java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    private fun pendingCursor(selection: String?, selectionArgs: Array<String>?): Cursor {
        val matched = pending
            .filter { matches(selection, selectionArgs, it) }
            .sortedBy { (it[PendingMessages.DUE_TIME] as? Number)?.toLong() ?: 0L }
        return FakeCursor(matched.flatMap { it.keys }.distinct(), matched)
    }

    /** Understands the `a < ? AND b <= ?` conjunction the pending query uses. */
    private fun matches(selection: String?, selectionArgs: Array<String>?, row: Map<String, Any?>): Boolean {
        if (selection.isNullOrBlank()) return true
        var argument = 0
        return selection.split(" AND ").all { clause ->
            val parts = clause.trim().split(Regex("[<>]=?")).map { it.trim() }
            if (parts.size != 2) throw IllegalArgumentException("unsupported clause $clause")
            val bound = if (parts[1] == "?") {
                selectionArgs?.get(argument++)?.toLongOrNull() ?: 0L
            } else {
                parts[1].toLongOrNull() ?: 0L
            }
            val value = (row[parts[0]] as? Number)?.toLong() ?: 0L
            if (clause.contains("<=")) value <= bound else value < bound
        }
    }

    private fun fail(call: String) {
        if (failMatching(call)) throw SQLiteException("provider refused: $call")
    }

    private companion object {
        const val MESSAGES = "content://mms"
        const val THREAD_ID = "content://mms-sms/threadID"
        const val PENDING = "content://mms-sms/pending"
        const val PART = "part"
        const val ADDR = "addr"
        const val SUB_ID = "sub_id"
        const val RECIPIENT = "recipient"

        /** The collections the provider exposes. Failed and all have none. */
        val BOXES = setOf("inbox", "sent", "drafts", "outbox")
    }
}
