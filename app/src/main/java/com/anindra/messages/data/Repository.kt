package com.anindra.messages.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream

private const val DB_NAME = "messages.db"
private const val DB_VERSION = 8

class Db(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE conversations(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                address TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                snippet TEXT NOT NULL DEFAULT '',
                timestamp INTEGER NOT NULL DEFAULT 0,
                unread_count INTEGER NOT NULL DEFAULT 0,
                last_is_me INTEGER NOT NULL DEFAULT 0,
                archived INTEGER NOT NULL DEFAULT 0,
                pinned INTEGER NOT NULL DEFAULT 0,
                draft TEXT NOT NULL DEFAULT '',
                draft_date INTEGER NOT NULL DEFAULT 0,
                deleted_at INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                body TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                is_me INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'sent',
                media_type TEXT NOT NULL DEFAULT 'text',
                media_uri TEXT NOT NULL DEFAULT '',
                reactions TEXT NOT NULL DEFAULT '',
                sys_id INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX idx_messages_conversation ON messages(conversation_id)")
        db.execSQL(
            """CREATE TABLE blocked_numbers(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number TEXT NOT NULL UNIQUE,
                timestamp INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE scheduled_messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                address TEXT NOT NULL,
                body TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                conversation_id INTEGER NOT NULL,
                sub_id INTEGER NOT NULL DEFAULT -1)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE messages ADD COLUMN reactions TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE conversations ADD COLUMN draft TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE conversations ADD COLUMN draft_date INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """CREATE TABLE blocked_numbers(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    number TEXT NOT NULL UNIQUE,
                    timestamp INTEGER NOT NULL)"""
            )
            db.execSQL(
                """CREATE TABLE scheduled_messages(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    address TEXT NOT NULL,
                    body TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    conversation_id INTEGER NOT NULL,
                    sub_id INTEGER NOT NULL DEFAULT -1)"""
            )
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE messages ADD COLUMN sys_id INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 7) {
            db.execSQL(
                "DELETE FROM messages WHERE conversation_id IN (SELECT id FROM conversations WHERE address LIKE '+1555123000_')"
            )
            db.execSQL("DELETE FROM conversations WHERE address LIKE '+1555123000_'")
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0")
        }
    }
}

class Repository(private val context: Context) {

    private val db = Db(context)
    val settings = SettingsStore(context)

    private val listeners = mutableListOf<() -> Unit>()

    fun notifyChanged() {
        listeners.forEach { it() }
    }

    private fun <T> observe(block: () -> T): Flow<T> = callbackFlow {
        val update: () -> Unit = { trySend(block()) }
        listeners += update
        trySend(block())
        awaitClose { listeners.remove(update) }
    }.flowOn(Dispatchers.IO)

    fun conversations(): Flow<List<Conversation>> = observe {
        val out = mutableListOf<Conversation>()
        db.readableDatabase.rawQuery(
            """SELECT id,address,name,snippet,timestamp,unread_count,last_is_me,archived,pinned,draft,draft_date,deleted_at
               FROM conversations WHERE deleted_at=0 ORDER BY pinned DESC, timestamp DESC""",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Conversation(
                        id = c.getLong(0),
                        address = c.getString(1),
                        name = c.getString(2),
                        snippet = c.getString(3),
                        timestamp = c.getLong(4),
                        unreadCount = c.getInt(5),
                        isMe = c.getInt(6) == 1,
                        archived = c.getInt(7) == 1,
                        pinned = c.getInt(8) == 1,
                        draft = c.getString(9),
                        draftDate = c.getLong(10),
                        deletedAt = c.getLong(11)
                    )
                )
            }
        }
        out
    }

    fun trashedConversations(): Flow<List<Conversation>> = observe {
        val out = mutableListOf<Conversation>()
        db.readableDatabase.rawQuery(
            """SELECT id,address,name,snippet,timestamp,unread_count,last_is_me,archived,pinned,draft,draft_date,deleted_at
               FROM conversations WHERE deleted_at>0 ORDER BY deleted_at DESC""",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Conversation(
                        id = c.getLong(0),
                        address = c.getString(1),
                        name = c.getString(2),
                        snippet = c.getString(3),
                        timestamp = c.getLong(4),
                        unreadCount = c.getInt(5),
                        isMe = c.getInt(6) == 1,
                        archived = c.getInt(7) == 1,
                        pinned = c.getInt(8) == 1,
                        draft = c.getString(9),
                        draftDate = c.getLong(10),
                        deletedAt = c.getLong(11)
                    )
                )
            }
        }
        out
    }

    fun messages(conversationId: Long): Flow<List<Message>> = observe {
        val out = mutableListOf<Message>()
        db.readableDatabase.rawQuery(
            """SELECT id,body,timestamp,is_me,status,media_type,media_uri,reactions FROM messages
               WHERE conversation_id=? ORDER BY timestamp ASC""",
            arrayOf(conversationId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Message(
                        id = c.getLong(0),
                        conversationId = conversationId,
                        body = c.getString(1),
                        timestamp = c.getLong(2),
                        isMe = c.getInt(3) == 1,
                        status = c.getString(4),
                        mediaType = c.getString(5),
                        mediaUri = c.getString(6),
                        reactions = parseReactions(c.getString(7))
                    )
                )
            }
        }
        out
    }

    fun getOrCreateConversation(address: String, displayName: String? = null): Long =
        getOrCreateConversationBlocking(address, displayName)

    fun getOrCreateConversationBlocking(address: String, displayName: String? = null): Long {
        var convoId = -1L
        db.writableDatabase.rawQuery(
            "SELECT id FROM conversations WHERE address=?",
            arrayOf(address)
        ).use { c -> if (c.moveToFirst()) convoId = c.getLong(0) }

        if (convoId == -1L) {
            val cv = ContentValues().apply {
                put("address", address)
                put("name", displayName ?: contactNameFor(address) ?: address)
            }
            convoId = db.writableDatabase.insert("conversations", null, cv)
            notifyChanged()
        }
        return convoId
    }

    fun conversationByIdSuspend(id: Long): Conversation? {
        var found: Conversation? = null
        db.readableDatabase.rawQuery(
            "SELECT id,address,name,snippet,timestamp,unread_count,last_is_me,archived,pinned,draft,draft_date FROM conversations WHERE id=?",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) found = Conversation(
                c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                c.getLong(4), c.getInt(5), c.getInt(6) == 1, c.getInt(7) == 1,
                c.getInt(8) == 1, c.getString(9), c.getLong(10)
            )
        }
        return found
    }

    /** Stores a text message as 'sending'; SmsStatusReceiver confirms the final state. */
    fun sendText(conversationId: Long, body: String): Message? {
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("conversation_id", conversationId)
            put("body", body)
            put("timestamp", now)
            put("is_me", 1)
            put("status", "sending")
            put("media_type", "text")
        }
        val id = db.writableDatabase.insertOrThrow("messages", null, cv)
        touchConversation(conversationId, body, now, isMe = true)
        return Message(id, conversationId, body, now, true, "sending")
    }

    fun sendMedia(conversationId: Long, mediaType: String, uri: String, caption: String = ""): Message? {
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("conversation_id", conversationId)
            put("body", caption)
            put("timestamp", now)
            put("is_me", 1)
            put("status", "sending")
            put("media_type", mediaType)
            put("media_uri", uri)
        }
        val id = db.writableDatabase.insertOrThrow("messages", null, cv)
        touchConversation(conversationId, if (mediaType == "image") "Photo" else "Voice message", now, isMe = true)
        return Message(id, conversationId, caption, now, true, "sending", mediaType, uri)
    }

    private fun touchConversation(conversationId: Long, snippet: String, ts: Long, isMe: Boolean) {
        db.writableDatabase.execSQL(
            "UPDATE conversations SET snippet=?,timestamp=?,unread_count=0,last_is_me=?,deleted_at=0 WHERE id=?",
            arrayOf(snippet, ts, if (isMe) 1 else 0, conversationId)
        )
        notifyChanged()
    }

    /** Store an incoming SMS. Returns conversation id. */
    fun receiveMessage(address: String, body: String): Long {
        val now = System.currentTimeMillis()
        val convoId = getOrCreateConversationBlocking(address)

        db.writableDatabase.execSQL(
            """INSERT INTO messages(conversation_id,body,timestamp,is_me,status)
               VALUES(?,?,?,?,?)""",
            arrayOf(convoId, body, now, 0, "received")
        )
        db.writableDatabase.execSQL(
            """UPDATE conversations SET snippet=?,timestamp=?,last_is_me=0,
               unread_count=unread_count+1 WHERE id=?""",
            arrayOf(body, now, convoId)
        )
        notifyChanged()
        return convoId
    }

    fun markReadSuspend(conversationId: Long) {
        db.writableDatabase.execSQL(
            "UPDATE conversations SET unread_count=0 WHERE id=?", arrayOf(conversationId)
        )
        notifyChanged()
    }

    fun setArchivedSuspend(conversationId: Long, archived: Boolean) {
        db.writableDatabase.execSQL(
            "UPDATE conversations SET archived=? WHERE id=?",
            arrayOf(if (archived) 1 else 0, conversationId)
        )
        notifyChanged()
    }

    fun markAllReadSuspend() {
        db.writableDatabase.execSQL("UPDATE conversations SET unread_count=0")
        notifyChanged()
    }

    /** Moves a conversation to trash (soft delete); messages are kept for restore. */
    fun trashConversationSuspend(conversationId: Long) {
        db.writableDatabase.execSQL(
            "UPDATE conversations SET deleted_at=? WHERE id=?",
            arrayOf(System.currentTimeMillis().toString(), conversationId.toString())
        )
        notifyChanged()
    }

    fun restoreFromTrashSuspend(conversationId: Long) {
        db.writableDatabase.execSQL(
            "UPDATE conversations SET deleted_at=0 WHERE id=?",
            arrayOf(conversationId.toString())
        )
        notifyChanged()
    }

    fun emptyTrashSuspend() {
        db.writableDatabase.execSQL(
            "DELETE FROM messages WHERE conversation_id IN (SELECT id FROM conversations WHERE deleted_at>0)"
        )
        db.writableDatabase.execSQL("DELETE FROM conversations WHERE deleted_at>0")
        notifyChanged()
    }

    /** Hard-deletes conversations trashed more than [days] ago. */
    fun purgeOldTrashSuspend(days: Int = 30) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        db.writableDatabase.execSQL(
            "DELETE FROM messages WHERE conversation_id IN " +
                "(SELECT id FROM conversations WHERE deleted_at>0 AND deleted_at<?)",
            arrayOf(cutoff.toString())
        )
        db.writableDatabase.execSQL(
            "DELETE FROM conversations WHERE deleted_at>0 AND deleted_at<?",
            arrayOf(cutoff.toString())
        )
    }

    /** Permanently deletes a conversation and its messages. */
    fun deleteConversationSuspend(conversationId: Long) {
        db.writableDatabase.execSQL(
            "DELETE FROM messages WHERE conversation_id=?", arrayOf(conversationId)
        )
        db.writableDatabase.execSQL(
            "DELETE FROM conversations WHERE id=?", arrayOf(conversationId)
        )
        notifyChanged()
    }

    fun setReactionsSuspend(messageId: Long, reactions: Map<String, Int>) {
        db.writableDatabase.execSQL(
            "UPDATE messages SET reactions=? WHERE id=?",
            arrayOf(serializeReactions(reactions), messageId)
        )
        notifyChanged()
    }

    fun markMessageStatusSuspend(messageId: Long, status: String) {
        db.writableDatabase.execSQL(
            "UPDATE messages SET status=? WHERE id=?", arrayOf(status, messageId)
        )
        notifyChanged()
    }

    fun pinnedConversations(): Flow<List<Conversation>> = observe {
        val out = mutableListOf<Conversation>()
        db.readableDatabase.rawQuery(
            """SELECT id,address,name,snippet,timestamp,unread_count,last_is_me,archived,pinned,draft,draft_date
               FROM conversations WHERE pinned=1 ORDER BY timestamp DESC""",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Conversation(
                        id = c.getLong(0),
                        address = c.getString(1),
                        name = c.getString(2),
                        snippet = c.getString(3),
                        timestamp = c.getLong(4),
                        unreadCount = c.getInt(5),
                        isMe = c.getInt(6) == 1,
                        archived = c.getInt(7) == 1,
                        pinned = c.getInt(8) == 1,
                        draft = c.getString(9),
                        draftDate = c.getLong(10)
                    )
                )
            }
        }
        out
    }

    fun setPinnedSuspend(id: Long, pinned: Boolean) {
        db.writableDatabase.execSQL(
            "UPDATE conversations SET pinned=? WHERE id=?",
            arrayOf(if (pinned) 1 else 0, id)
        )
        notifyChanged()
    }

    fun saveDraft(conversationId: Long, draft: String) {
        val now = System.currentTimeMillis()
        db.writableDatabase.execSQL(
            "UPDATE conversations SET draft=?,draft_date=? WHERE id=?",
            arrayOf(draft, now, conversationId)
        )
        notifyChanged()
    }

    fun isNumberBlocked(number: String): Boolean {
        var blocked = false
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM blocked_numbers WHERE number=?",
            arrayOf(number)
        ).use { c ->
            if (c.moveToFirst()) blocked = c.getInt(0) > 0
        }
        return blocked
    }

    fun blockedNumbers(): Flow<List<BlockedNumber>> = observe {
        val out = mutableListOf<BlockedNumber>()
        db.readableDatabase.rawQuery(
            "SELECT id,number,timestamp FROM blocked_numbers ORDER BY timestamp DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    BlockedNumber(
                        id = c.getLong(0),
                        number = c.getString(1),
                        timestamp = c.getLong(2)
                    )
                )
            }
        }
        out
    }

    fun blockNumber(number: String) {
        val cv = ContentValues().apply {
            put("number", number)
            put("timestamp", System.currentTimeMillis())
        }
        db.writableDatabase.insertWithOnConflict("blocked_numbers", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        notifyChanged()
    }

    fun unblockNumber(number: String) {
        db.writableDatabase.execSQL("DELETE FROM blocked_numbers WHERE number=?", arrayOf(number))
        notifyChanged()
    }

    fun scheduledMessages(): Flow<List<ScheduledMessage>> = observe {
        val out = mutableListOf<ScheduledMessage>()
        db.readableDatabase.rawQuery(
            "SELECT id,address,body,timestamp,conversation_id,sub_id FROM scheduled_messages ORDER BY timestamp ASC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    ScheduledMessage(
                        id = c.getLong(0),
                        address = c.getString(1),
                        body = c.getString(2),
                        timestamp = c.getLong(3),
                        conversationId = c.getLong(4),
                        subId = c.getInt(5)
                    )
                )
            }
        }
        out
    }

    fun addScheduledMessage(address: String, body: String, timestamp: Long, conversationId: Long, subId: Int): Long {
        val cv = ContentValues().apply {
            put("address", address)
            put("body", body)
            put("timestamp", timestamp)
            put("conversation_id", conversationId)
            put("sub_id", subId)
        }
        val id = db.writableDatabase.insertOrThrow("scheduled_messages", null, cv)
        notifyChanged()
        return id
    }

    fun deleteScheduledMessage(id: Long) {
        db.writableDatabase.execSQL("DELETE FROM scheduled_messages WHERE id=?", arrayOf(id))
        notifyChanged()
    }

    fun backupDatabase(context: Context): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return false
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "messages_backup_${System.currentTimeMillis()}.db")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Messages")
            }
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return false
            resolver.openOutputStream(uri)?.use { out -> dbFile.inputStream().use { it.copyTo(out) } }
            true
        } catch (_: Exception) { false }
    }

    fun importDatabase(context: Context, sourceUri: android.net.Uri): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            db.close()
            context.contentResolver.openInputStream(sourceUri)?.use { inp ->
                FileOutputStream(dbFile).use { out -> inp.copyTo(out) }
            }
            true
        } catch (_: Exception) { false }
    }

    fun messageByIdSuspend(messageId: Long): Message? {
        var found: Message? = null
        db.readableDatabase.rawQuery(
            """SELECT conversation_id,body,timestamp,is_me,status,media_type,media_uri,reactions
               FROM messages WHERE id=?""",
            arrayOf(messageId.toString())
        ).use { c ->
            if (c.moveToFirst()) found = Message(
                id = messageId,
                conversationId = c.getLong(0),
                body = c.getString(1),
                timestamp = c.getLong(2),
                isMe = c.getInt(3) == 1,
                status = c.getString(4),
                mediaType = c.getString(5),
                mediaUri = c.getString(6),
                reactions = parseReactions(c.getString(7))
            )
        }
        return found
    }

    fun contactNameFor(address: String): String? {
        val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val nameCol = android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        val lastDigits = address.filter { it.isDigit() }.takeLast(8)
        if (lastDigits.isBlank()) return null
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(nameCol),
                android.provider.ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER + " LIKE ?",
                arrayOf("%$lastDigits%"),
                null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: context.contentResolver.query(
                    uri,
                    arrayOf(nameCol),
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?",
                    arrayOf("%$lastDigits%"),
                    null
                )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: SecurityException) {
            null
        }
    }

    /** Re-resolves display names from ContactsContract for all conversations. */
    fun refreshContactNames() {
        Thread {
            try {
                val rows = mutableListOf<Pair<Long, String>>()
                db.readableDatabase.rawQuery(
                    "SELECT id,address FROM conversations", null
                ).use { c ->
                    while (c.moveToNext()) rows.add(c.getLong(0) to c.getString(1))
                }
                var changed = false
                rows.forEach { (id, address) ->
                    val resolved = contactNameFor(address) ?: return@forEach
                    var current: String? = null
                    db.readableDatabase.rawQuery(
                        "SELECT name FROM conversations WHERE id=?", arrayOf(id.toString())
                    ).use { c -> if (c.moveToFirst()) current = c.getString(0) }
                    if (current != resolved) {
                        db.writableDatabase.execSQL(
                            "UPDATE conversations SET name=? WHERE id=?",
                            arrayOf(resolved, id.toString())
                        )
                        changed = true
                    }
                }
                if (changed) notifyChanged()
            } catch (_: Exception) {
            }
        }.start()
    }

    /**
     * Imports all real SMS from the system Telephony provider into the local
     * DB, grouped by address into conversations. Deduped via sys_id.
     */
    fun syncFromSystem() {
        Thread {
            try {
                val resolver = context.contentResolver
                val cursor = resolver.query(
                    android.provider.Telephony.Sms.CONTENT_URI,
                    arrayOf(
                        android.provider.Telephony.Sms._ID,
                        android.provider.Telephony.Sms.ADDRESS,
                        android.provider.Telephony.Sms.BODY,
                        android.provider.Telephony.Sms.DATE,
                        android.provider.Telephony.Sms.TYPE
                    ),
                    null, null,
                    android.provider.Telephony.Sms.DATE + " ASC"
                ) ?: return@Thread

                data class SysSms(val sysId: Long, val body: String, val date: Long, val type: Int)
                val byAddress = LinkedHashMap<String, MutableList<SysSms>>()
                cursor.use { c ->
                    while (c.moveToNext()) {
                        val addr = c.getString(1) ?: continue
                        val body = c.getString(2) ?: continue
                        val date = c.getLong(3)
                        val type = c.getInt(4)
                        if (type == android.provider.Telephony.Sms.MESSAGE_TYPE_DRAFT ||
                            type == android.provider.Telephony.Sms.MESSAGE_TYPE_OUTBOX
                        ) continue
                        byAddress.getOrPut(addr) { mutableListOf() }.add(
                            SysSms(c.getLong(0), body, date, type)
                        )
                    }
                }

                val existing = HashSet<Long>()
                db.readableDatabase.rawQuery("SELECT sys_id FROM messages WHERE sys_id!=0", null).use { c ->
                    while (c.moveToNext()) existing.add(c.getLong(0))
                }

                var changed = false
                byAddress.forEach { (addr, msgs) ->
                    if (msgs.all { it.sysId in existing }) return@forEach
                    val cid = getOrCreateConversationBlocking(addr)
                    msgs.forEach { m ->
                        if (m.sysId in existing) return@forEach
                        // Link a locally-stored copy (same body within ±2 min) instead of duplicating.
                        var localId = -1L
                        db.readableDatabase.rawQuery(
                            """SELECT id FROM messages
                               WHERE conversation_id=? AND sys_id=0 AND body=?
                                 AND ABS(timestamp-?) < 120000
                               ORDER BY ABS(timestamp-?) LIMIT 1""",
                            arrayOf(cid.toString(), m.body, m.date.toString(), m.date.toString())
                        ).use { c -> if (c.moveToFirst()) localId = c.getLong(0) }

                        if (localId != -1L) {
                            db.writableDatabase.execSQL(
                                "UPDATE messages SET sys_id=? WHERE id=?",
                                arrayOf(m.sysId.toString(), localId.toString())
                            )
                        } else {
                            db.writableDatabase.execSQL(
                                """INSERT INTO messages(conversation_id,body,timestamp,is_me,status,sys_id)
                                   VALUES(?,?,?,?,?,?)""",
                                arrayOf(
                                    cid, m.body, m.date,
                                    if (m.type == android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX) 0 else 1,
                                    when (m.type) {
                                        android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX -> "received"
                                        android.provider.Telephony.Sms.MESSAGE_TYPE_FAILED -> "failed"
                                        else -> "sent"
                                    },
                                    m.sysId
                                )
                            )
                        }
                        existing.add(m.sysId)
                        changed = true
                    }
                }

                val stale = db.readableDatabase.rawQuery(
                    """SELECT COUNT(*) FROM conversations
                       WHERE timestamp=0 AND id IN (SELECT DISTINCT conversation_id FROM messages)""",
                    null
                ).use { c -> c.moveToFirst() && c.getLong(0) > 0 }

                if (changed || stale) {
                    refreshConversationSnippets()
                    notifyChanged()
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    /** Recomputes snippet/timestamp/last_is_me from each conversation's newest message. */
    private fun refreshConversationSnippets() {
        db.writableDatabase.execSQL(
            """UPDATE conversations SET
                 snippet=COALESCE((SELECT body FROM messages WHERE conversation_id=conversations.id ORDER BY timestamp DESC LIMIT 1),''),
                 timestamp=COALESCE((SELECT MAX(timestamp) FROM messages WHERE conversation_id=conversations.id),0),
                 last_is_me=COALESCE((SELECT is_me FROM messages WHERE conversation_id=conversations.id ORDER BY timestamp DESC LIMIT 1),0)
               WHERE id IN (SELECT DISTINCT conversation_id FROM messages)"""
        )
    }

    /** Writes an outgoing SMS into the system Sent box (required when default app). */
    fun writeSentToSystem(address: String, body: String) {
        try {
            val cv = ContentValues().apply {
                put(android.provider.Telephony.Sms.ADDRESS, address)
                put(android.provider.Telephony.Sms.BODY, body)
                put(android.provider.Telephony.Sms.DATE, System.currentTimeMillis())
                put(android.provider.Telephony.Sms.READ, 1)
                put(
                    android.provider.Telephony.Sms.TYPE,
                    android.provider.Telephony.Sms.MESSAGE_TYPE_SENT
                )
            }
            context.contentResolver.insert(
                android.provider.Telephony.Sms.Sent.CONTENT_URI, cv
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        fun serializeReactions(r: Map<String, Int>): String =
            r.entries.filter { it.value > 0 }
                .joinToString(",") { "${it.key}:${it.value}" }

        fun parseReactions(s: String): Map<String, Int> =
            if (s.isBlank()) emptyMap()
            else s.split(',').mapNotNull {
                val parts = it.split(':')
                if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
            }.filter { it.second > 0 }.toMap()
    }
}
