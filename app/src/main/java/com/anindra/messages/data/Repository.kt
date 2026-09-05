package com.anindra.messages.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.RandomAccessFile
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream

private const val DB_NAME = "messages.db"
enum class BackupFormat { PIN, LEGACY }

private const val DB_VERSION = 13
private const val PREFS_NAME = "messages_schema"
private const val PREF_HEAL_APPLIED = "heal_v1_applied"

class Db(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val schemaPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
                sys_id INTEGER NOT NULL DEFAULT 0,
                locked INTEGER NOT NULL DEFAULT 0,
                sub_id INTEGER NOT NULL DEFAULT -1)"""
        )
        db.execSQL("CREATE INDEX idx_messages_conversation ON messages(conversation_id)")
        db.execSQL("CREATE INDEX idx_messages_conv_ts ON messages(conversation_id, timestamp)")
        db.execSQL("CREATE UNIQUE INDEX idx_messages_sys_id ON messages(sys_id) WHERE sys_id>0")
        db.execSQL("CREATE INDEX idx_conversations_list ON conversations(deleted_at, pinned, timestamp)")
        db.execSQL("CREATE INDEX idx_conversations_archived ON conversations(archived) WHERE deleted_at=0")
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
        db.execSQL("CREATE INDEX idx_scheduled_timestamp ON scheduled_messages(timestamp)")
        db.execSQL(
            """CREATE TABLE conversation_notifications(
                conversation_id INTEGER PRIMARY KEY REFERENCES conversations(id) ON DELETE CASCADE,
                notifications_enabled INTEGER NOT NULL DEFAULT 1)"""
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
        if (oldVersion < 9) {
            db.execSQL(
                """CREATE TABLE conversation_notifications(
                    conversation_id INTEGER PRIMARY KEY REFERENCES conversations(id) ON DELETE CASCADE,
                    notifications_enabled INTEGER NOT NULL DEFAULT 1)"""
            )
        }
        if (oldVersion < 10) {
            db.execSQL("ALTER TABLE messages ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 11) {
            dedupeSysIds(db)
        }
        if (oldVersion < 12) {
            db.execSQL("ALTER TABLE messages ADD COLUMN sub_id INTEGER NOT NULL DEFAULT -1")
        }
        if (oldVersion < 13) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_conv_ts ON messages(conversation_id, timestamp)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_sys_id " +
                    "ON messages(sys_id) WHERE sys_id>0"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_list ON conversations(deleted_at, pinned, timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_archived ON conversations(archived) WHERE deleted_at=0")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_scheduled_timestamp ON scheduled_messages(timestamp)")
        }
    }

    /** Collapses rows that share a system-provider id (legacy double-imports,
     *  e.g. duplicated OTP texts), drops the unlinked local twin of any already
     *  linked message, then enforces uniqueness going forward. */
    private fun dedupeSysIds(db: SQLiteDatabase) {
        db.execSQL(
            """DELETE FROM messages WHERE sys_id>0 AND id NOT IN
               (SELECT MIN(id) FROM messages WHERE sys_id>0 GROUP BY sys_id)"""
        )
        db.execSQL(
            """DELETE FROM messages WHERE sys_id=0 AND EXISTS(
               SELECT 1 FROM messages m WHERE m.sys_id>0
                 AND m.conversation_id=messages.conversation_id
                 AND m.body=messages.body AND m.is_me=messages.is_me
                 AND ABS(m.timestamp-messages.timestamp)<86400000)"""
        )
        try {
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_sys_id " +
                    "ON messages(sys_id) WHERE sys_id>0"
            )
        } catch (_: android.database.sqlite.SQLiteException) {
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS conversation_notifications(
                conversation_id INTEGER PRIMARY KEY REFERENCES conversations(id) ON DELETE CASCADE,
                notifications_enabled INTEGER NOT NULL DEFAULT 1)"""
        )
        // The ALTER/INDEX healing below only needs to run once per install; running
        // it on every DB open rebuilds the unique index over the whole messages
        // table (and re-throws exceptions) on the main thread at app start.
        if (schemaPrefs.getBoolean(PREF_HEAL_APPLIED, false)) return
        try {
            db.execSQL("ALTER TABLE messages ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
        } catch (_: android.database.sqlite.SQLiteException) {
        }
        try {
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_sys_id " +
                    "ON messages(sys_id) WHERE sys_id>0"
            )
        } catch (_: android.database.sqlite.SQLiteException) {
        }
        try {
            db.execSQL("ALTER TABLE messages ADD COLUMN sub_id INTEGER NOT NULL DEFAULT -1")
        } catch (_: android.database.sqlite.SQLiteException) {
        }
        schemaPrefs.edit().putBoolean(PREF_HEAL_APPLIED, true).apply()
    }
}

class Repository(private val context: Context) {

    private var db = Db(context)
    val settings = SettingsStore(context)
    private val dbExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val ioDispatcher = kotlinx.coroutines.Dispatchers.IO

    /** Runs the block on a single dedicated IO thread; the result is returned
     *  synchronously to the caller. Used by receiver/UI paths that need DB
     *  access but cannot suspend. The shared executor serializes writes so we
     *  never collide with concurrent [notifyChanged] listeners. */
    private fun <T> runOnIo(block: () -> T): T {
        val future = java.util.concurrent.CompletableFuture<T>()
        dbExecutor.execute {
            try { future.complete(block()) } catch (t: Throwable) { future.completeExceptionally(t) }
        }
        return future.get()
    }

    /** Same as [runOnIo] but coroutine-friendly: suspends on the same single
     *  executor. Preferred over [runOnIo] from `viewModelScope`/composables. */
    private suspend inline fun <T> runOnIoAsync(crossinline block: () -> T): T =
        kotlinx.coroutines.withContext(ioDispatcher) {
            kotlinx.coroutines.suspendCancellableCoroutine<T> { cont ->
                dbExecutor.execute {
                    if (cont.isActive) {
                        try { cont.resumeWith(kotlin.runCatching { block() }) }
                        catch (t: Throwable) { cont.resumeWith(kotlin.Result.failure(t)) }
                    }
                }
            }
        }

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    private val _initialSyncDone = MutableStateFlow(settings.firstImportDone)

    /** True when the UI may skip the loading state. */
    val initialSyncDone: StateFlow<Boolean> = _initialSyncDone.asStateFlow()

    private val _initialSyncProgress = MutableStateFlow<Float?>(null)

    /** Null = idle; 0..1 = fraction imported this pass. */
    val initialSyncProgress: StateFlow<Float?> = _initialSyncProgress.asStateFlow()

    // One IO thread — overlapping onResume calls can't double-import
    private val syncExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private val notifyThread = HandlerThread("repo-notify").apply { start() }
    private val notifyHandler = Handler(notifyThread.looper)
    private val notifyRunnable = Runnable { listeners.forEach { it() } }

    fun notifyChanged() {
        notifyHandler.removeCallbacks(notifyRunnable)
        notifyHandler.postDelayed(notifyRunnable, 100)
    }

    private fun <T> observe(block: () -> T): Flow<T> = callbackFlow {
        val update: () -> Unit = { trySend(block()) }
        listeners += update
        trySend(block())
        awaitClose { listeners.remove(update) }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

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

    fun conversationByIdFlow(id: Long): Flow<Conversation?> = observe {
        var out: Conversation? = null
        db.readableDatabase.rawQuery(
            """SELECT id,address,name,snippet,timestamp,unread_count,last_is_me,archived,pinned,draft,draft_date,deleted_at
               FROM conversations WHERE id=? AND deleted_at=0""",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) {
                out = Conversation(
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

    fun messages(conversationId: Long, limit: Int = Int.MAX_VALUE, offset: Int = 0): Flow<List<Message>> = observe {
        val out = mutableListOf<Message>()
        db.readableDatabase.rawQuery(
            """SELECT id,body,timestamp,is_me,status,media_type,media_uri,reactions,locked,sub_id FROM messages
               WHERE conversation_id=? ORDER BY timestamp DESC LIMIT ? OFFSET ?""",
            arrayOf(conversationId.toString(), limit.toString(), offset.toString())
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
                        reactions = parseReactions(c.getString(7)),
                        locked = c.getInt(8) == 1,
                        subId = c.getInt(9)
                    )
                )
            }
        }
        out.reversed()
    }

    fun messageCount(conversationId: Long): Int = runOnIo {
        var count = 0
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE conversation_id=?",
            arrayOf(conversationId.toString())
        ).use { if (it.moveToFirst()) count = it.getInt(0) }
        count
    }

    fun messageCountFlow(conversationId: Long): Flow<Int> = observe {
        var count = 0
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE conversation_id=?",
            arrayOf(conversationId.toString())
        ).use { if (it.moveToFirst()) count = it.getInt(0) }
        count
    }

    fun getOrCreateConversation(address: String, displayName: String? = null): Long =
        getOrCreateConversationBlocking(address, displayName)

    fun getOrCreateConversationBlocking(address: String, displayName: String? = null): Long = runOnIo {
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
        convoId
    }

    suspend fun conversationByIdSuspend(id: Long): Conversation? = runOnIoAsync {
        var found: Conversation? = null
        db.readableDatabase.rawQuery(
            "SELECT id,address,name,snippet,timestamp,unread_count,last_is_me,archived,pinned,draft,draft_date FROM conversations WHERE id=? AND deleted_at=0",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) found = Conversation(
                c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                c.getLong(4), c.getInt(5), c.getInt(6) == 1, c.getInt(7) == 1,
                c.getInt(8) == 1, c.getString(9), c.getLong(10)
            )
        }
        found
    }

    /** Stores a text message as 'sending'; SmsStatusReceiver confirms the final state. */
    fun sendText(conversationId: Long, body: String, subId: Int = -1): Message? {
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("conversation_id", conversationId)
            put("body", body)
            put("timestamp", now)
            put("is_me", 1)
            put("status", "sending")
            put("media_type", "text")
            put("sub_id", subId)
        }
        val id = db.writableDatabase.insertOrThrow("messages", null, cv)
        touchConversation(conversationId, body, now, isMe = true)
        return Message(id, conversationId, body, now, true, "sending", subId = subId)
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

    /** Store an incoming SMS. Returns conversation id. [sysId] links the row to
     *  its system-provider copy so the next sync skips it instead of duplicating.
     *  [subId] is the SIM subscription id for dual-SIM display. */
    fun receiveMessage(address: String, body: String, sysId: Long = 0L, subId: Int = -1): Long {
        val now = System.currentTimeMillis()
        val convoId = getOrCreateConversationBlocking(address)

        db.writableDatabase.execSQL(
            """INSERT INTO messages(conversation_id,body,timestamp,is_me,status,sys_id,sub_id)
               VALUES(?,?,?,?,?,?,?)""",
            arrayOf(convoId, body, now, 0, "received", sysId, subId)
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

    fun setLockedSuspend(messageId: Long, locked: Boolean) {
        db.writableDatabase.execSQL(
            "UPDATE messages SET locked=? WHERE id=?",
            arrayOf(if (locked) 1 else 0, messageId)
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
        val trashed = mutableListOf<Long>()
        db.readableDatabase.rawQuery("SELECT id FROM conversations WHERE deleted_at>0", null)
            .use { c -> while (c.moveToNext()) trashed.add(c.getLong(0)) }
        purgeProviderMessages(trashed)
        db.writableDatabase.execSQL(
            "DELETE FROM messages WHERE conversation_id IN (SELECT id FROM conversations WHERE deleted_at>0)"
        )
        db.writableDatabase.execSQL("DELETE FROM conversations WHERE deleted_at>0")
        notifyChanged()
    }

    /** Best-effort removal of permanently-deleted messages from the system
     *  SMS provider, so the periodic [syncFromSystem] doesn't resurrect them.
     *  Needs default-SMS-app (or WRITE_SMS); skipped silently otherwise. */
    private fun purgeProviderMessages(conversationIds: List<Long>) {
        try {
            if (conversationIds.isEmpty()) return
            val ph = conversationIds.joinToString(",") { "?" }
            val sysIds = mutableListOf<Long>()
            db.readableDatabase.rawQuery(
                "SELECT sys_id FROM messages WHERE conversation_id IN ($ph) AND sys_id>0",
                conversationIds.map { it.toString() }.toTypedArray()
            ).use { c -> while (c.moveToNext()) sysIds.add(c.getLong(0)) }
            if (sysIds.isEmpty()) return
            sysIds.chunked(200).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                context.contentResolver.delete(
                    android.provider.Telephony.Sms.CONTENT_URI,
                    android.provider.Telephony.Sms._ID + " IN ($placeholders)",
                    chunk.map { it.toString() }.toTypedArray()
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("RepoSync", "Provider purge skipped: ${e.message}")
        }
    }

    /** Hard-deletes conversations trashed more than [days] ago. */
    fun purgeOldTrashSuspend(days: Int = 30) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val stale = mutableListOf<Long>()
        db.readableDatabase.rawQuery(
            "SELECT id FROM conversations WHERE deleted_at>0 AND deleted_at<?",
            arrayOf(cutoff.toString())
        ).use { c -> while (c.moveToNext()) stale.add(c.getLong(0)) }
        purgeProviderMessages(stale)
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
        purgeProviderMessages(listOf(conversationId))
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

    private val blockedCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun isNumberBlocked(number: String): Boolean {
        blockedCache[number]?.let { return it }
        val blocked = runOnIo {
            var b = false
            db.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM blocked_numbers WHERE number=?",
                arrayOf(number)
            ).use { c ->
                if (c.moveToFirst()) b = c.getInt(0) > 0
            }
            b
        }
        blockedCache[number] = blocked
        return blocked
    }

    /** Invalidates the in-process block cache; call after [blockNumber]/[unblockNumber]. */
    private fun invalidateBlockCache(number: String) { blockedCache.remove(number) }

    fun conversationIdForAddress(address: String): Long? = runOnIo {
        var id: Long? = null
        db.readableDatabase.rawQuery(
            "SELECT id FROM conversations WHERE address=?", arrayOf(address)
        ).use { c -> if (c.moveToFirst()) id = c.getLong(0) }
        id
    }

    suspend fun getConversationNotificationsEnabled(conversationId: Long): Boolean = runOnIoAsync {
        var enabled = true
        db.readableDatabase.rawQuery(
            "SELECT notifications_enabled FROM conversation_notifications WHERE conversation_id=?",
            arrayOf(conversationId.toString())
        ).use { c ->
            if (c.moveToFirst()) enabled = c.getInt(0) == 1
        }
        enabled
    }

    fun conversationNotificationsEnabledFlow(conversationId: Long): Flow<Boolean> = observe {
        var enabled = true
        db.readableDatabase.rawQuery(
            "SELECT notifications_enabled FROM conversation_notifications WHERE conversation_id=?",
            arrayOf(conversationId.toString())
        ).use { c ->
            if (c.moveToFirst()) enabled = c.getInt(0) == 1
        }
        enabled
    }

    /** Synchronous variant for receivers already on a background thread. */
    fun getConversationNotificationsEnabledBlocking(conversationId: Long): Boolean = runOnIo {
        var enabled = true
        db.readableDatabase.rawQuery(
            "SELECT notifications_enabled FROM conversation_notifications WHERE conversation_id=?",
            arrayOf(conversationId.toString())
        ).use { c ->
            if (c.moveToFirst()) enabled = c.getInt(0) == 1
        }
        enabled
    }

    fun setConversationNotificationsEnabled(conversationId: Long, enabled: Boolean) {
        val cv = ContentValues().apply {
            put("conversation_id", conversationId)
            put("notifications_enabled", if (enabled) 1 else 0)
        }
        db.writableDatabase.insertWithOnConflict(
            "conversation_notifications", null, cv, SQLiteDatabase.CONFLICT_REPLACE
        )
        notifyChanged()
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
        invalidateBlockCache(number)
        notifyChanged()
    }

    fun unblockNumber(number: String) {
        db.writableDatabase.execSQL("DELETE FROM blocked_numbers WHERE number=?", arrayOf(number))
        invalidateBlockCache(number)
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
        require(address.isNotBlank()) { "Address must not be blank" }
        require(body.isNotBlank()) { "Message body must not be blank" }
        require(body.length <= 1600) { "Message body exceeds 1600 characters" }
        require(timestamp > System.currentTimeMillis()) { "Scheduled time must be in the future" }
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

    fun peekBackupFormat(context: Context, sourceUri: android.net.Uri): BackupFormat {
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { inp ->
                val magic = ByteArray(4)
                if (inp.read(magic) == 4 && BackupCrypto.isPinMagic(magic)) BackupFormat.PIN
                else BackupFormat.LEGACY
            } ?: BackupFormat.LEGACY
        } catch (_: Exception) {
            BackupFormat.LEGACY
        }
    }

    fun backupDatabase(context: Context, pin: String): Boolean {
        return try {
            if (!BackupCrypto.isValidPin(pin)) return false
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return false
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "messages_backup_${System.currentTimeMillis()}.enc")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Messages")
            }
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                dbFile.inputStream().use { inp -> BackupCrypto.encryptWithPin(inp, out, pin) }
            }
            true
        } catch (_: Exception) { false }
    }

    sealed class ImportResult {
        data object Success : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    fun importDatabase(context: Context, sourceUri: android.net.Uri, pin: String?): ImportResult {
        val dbFile = context.getDatabasePath(DB_NAME)
        val backupFile = File(dbFile.parent, "pre_import_backup.db")
        val tempFile = File(dbFile.parent, "import_temp.db")
        try {
            val isPin = peekBackupFormat(context, sourceUri) == BackupFormat.PIN
            if (isPin && pin == null) {
                return ImportResult.Error("Backup is PIN-protected. Enter the PIN to import.")
            }
            // Decrypt to temp file first (never touch the live DB until we have a valid file)
            val decrypted = context.contentResolver.openInputStream(sourceUri)?.use { inp ->
                FileOutputStream(tempFile).use { out ->
                    if (isPin) BackupCrypto.decryptWithPin(inp, out, pin!!)
                    else BackupCrypto.decrypt(inp, out)
                }
            } ?: return ImportResult.Error("Cannot open backup file")

            if (!decrypted) {
                if (isPin) {
                    // Wrong PIN (or tampered). A PIN file is never a raw sqlite dump.
                    tempFile.delete()
                    return ImportResult.Error("Wrong PIN or corrupted file")
                }
                // Legacy (or raw) backup that doesn't decrypt — try a raw import
                tempFile.delete()
                context.contentResolver.openInputStream(sourceUri)?.use { raw ->
                    FileOutputStream(tempFile).use { raw::copyTo }
                }
            }

            // Validate the temp file is a real SQLite database
            if (!isValidSqliteFile(tempFile)) {
                tempFile.delete()
                return ImportResult.Error("Invalid or corrupted backup file")
            }

            // Backup current DB in case swap fails
            dbFile.copyTo(backupFile, overwrite = true)

            // Close the shared DB, swap files, and reopen
            db.close()
            try {
                tempFile.renameTo(dbFile)
                if (!dbFile.exists()) {
                    // Restore from backup
                    backupFile.renameTo(dbFile)
                    db = Db(context)
                    return ImportResult.Error("Failed to replace database file")
                }
                db = Db(context)
                // A restored backup should be fully visible: lift any
                // conversations that were in the trash when the backup was made.
                db.writableDatabase.execSQL("UPDATE conversations SET deleted_at=0 WHERE deleted_at>0")
                // Clean up
                tempFile.delete()
                backupFile.delete()
                return ImportResult.Success
            } catch (e: Exception) {
                // Restore backup on failure
                if (!dbFile.exists()) backupFile.renameTo(dbFile)
                db = Db(context)
                tempFile.delete()
                return ImportResult.Error("Database swap failed: ${e.message ?: e.javaClass.simpleName}")
            }
        } catch (e: Exception) {
            tempFile.delete()
            return ImportResult.Error("Import failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun isValidSqliteFile(file: File): Boolean {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(16)
                raf.readFully(header)
                String(header).startsWith("SQLite format 3")
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun messageByIdSuspend(messageId: Long): Message? = runOnIoAsync {
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
        found
    }

    private val contactCache = HashMap<String, Pair<String?, Long>>()
    private val CONTACT_CACHE_TTL = 5 * 60 * 1000L

    fun contactNameFor(address: String): String? {
        val now = System.currentTimeMillis()
        contactCache[address]?.let { (name, ts) ->
            if (now - ts < CONTACT_CACHE_TTL) return name
        }
        val resolved = lookupContactName(address)
        contactCache[address] = resolved to now
        return resolved
    }

    private fun lookupContactName(address: String): String? {
        if (address.isBlank()) return null
        return try {
            val lookupUri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(address)
            )
            context.contentResolver.query(
                lookupUri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: SecurityException) {
            null
        }
    }

    /** Re-resolves display names from ContactsContract for all conversations. */
    fun refreshContactNames() {
        Thread {
            try {
                val ids = mutableListOf<Long>()
                val addresses = mutableListOf<String>()
                db.readableDatabase.rawQuery(
                    "SELECT id,address FROM conversations", null
                ).use { c ->
                    while (c.moveToNext()) {
                        ids.add(c.getLong(0))
                        addresses.add(c.getString(1))
                    }
                }
                // Resolve every contact (cached) up front, then issue a single
                // CASE-based UPDATE instead of N+1 SELECT/UPDATE round trips.
                val resolved = addresses.map { contactNameFor(it) ?: it }
                if (resolved.zip(addresses).all { (a, b) -> a == b }) {
                    // Names already match — skip the write entirely.
                    return@Thread
                }
                val cases = ids.zip(resolved)
                    .joinToString(" ") { (id, name) ->
                        "WHEN $id THEN ${android.database.DatabaseUtils.sqlEscapeString(name)}"
                    }
                val idsList = ids.joinToString(",")
                db.writableDatabase.execSQL(
                    "UPDATE conversations SET name = CASE id $cases END WHERE id IN ($idsList)"
                )
                notifyChanged()
            } catch (_: Exception) {
            }
        }.start()
    }

@Volatile private var syncRunning = false

    /** True until a sync that actually had SMS access completes. */
    val needsInitialImport: Boolean get() = !settings.firstImportDone

    /** Imports system SMS into the local DB, grouped by address, deduped by sys_id. */
    fun syncFromSystem() {
        if (syncRunning) return
        syncRunning = true
        syncExecutor.execute {
            try {
                val resolver = context.contentResolver
                android.util.Log.d("RepoSync", "Starting syncFromSystem")
                val cursor = resolver.query(
                    android.provider.Telephony.Sms.CONTENT_URI,
                    arrayOf(
                        android.provider.Telephony.Sms._ID,
                        android.provider.Telephony.Sms.ADDRESS,
                        android.provider.Telephony.Sms.BODY,
                        android.provider.Telephony.Sms.DATE,
                        android.provider.Telephony.Sms.TYPE,
                        android.provider.Telephony.Sms.SUBSCRIPTION_ID
                    ),
                    null, null,
                    android.provider.Telephony.Sms.DATE + " ASC"
                ) ?: run { android.util.Log.e("RepoSync", "Cursor is null — READ_SMS not granted?"); return@execute }

                data class SysSms(val sysId: Long, val body: String, val date: Long, val type: Int, val subId: Int)
                val byAddress = LinkedHashMap<String, MutableList<SysSms>>()
                cursor.use { c ->
                    while (c.moveToNext()) {
                        val addr = c.getString(1) ?: continue
                        val body = c.getString(2) ?: continue
                        val date = c.getLong(3)
                        val type = c.getInt(4)
                        val subId = c.getInt(5)
                        if (type == android.provider.Telephony.Sms.MESSAGE_TYPE_DRAFT ||
                            type == android.provider.Telephony.Sms.MESSAGE_TYPE_OUTBOX
                        ) continue
                        byAddress.getOrPut(addr) { mutableListOf() }.add(
                            SysSms(c.getLong(0), body, date, type, subId)
                        )
                    }
                }

                val incomingSysIds = byAddress.values.flatMapTo(mutableSetOf()) { list -> list.map { it.sysId } }
                val existing = mutableSetOf<Long>()
                incomingSysIds.chunked(500).forEach { chunk ->
                    val ph = chunk.joinToString(",") { "?" }
                    db.readableDatabase.rawQuery(
                        "SELECT sys_id FROM messages WHERE sys_id IN ($ph)",
                        chunk.map { it.toString() }.toTypedArray()
                    ).use { c -> while (c.moveToNext()) existing.add(c.getLong(0)) }
                }

                val pending = byAddress.values.sumOf { list -> list.count { it.sysId !in existing } }
                android.util.Log.d("RepoSync", "Loaded ${byAddress.size} addresses, $pending pending messages")
                if (pending > 0) _initialSyncProgress.value = 0f

                var changed = false
                var done = 0
                val batchSize = 50
                val pendingMessages = mutableListOf<Triple<String, Long, SysSms>>()
                byAddress.forEach { (addr, msgs) ->
                    if (msgs.all { it.sysId in existing }) return@forEach
                    val cid = getOrCreateConversationBlocking(addr)
                    msgs.filter { it.sysId !in existing }.forEach { m ->
                        pendingMessages.add(Triple(addr, cid, m))
                    }
                }
                pendingMessages.chunked(batchSize).forEach { batch ->
                    db.writableDatabase.beginTransaction()
                    try {
                        for ((addr, cid, m) in batch) {
                            val isMe = m.type != android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX
                            var localId = -1L
                            db.readableDatabase.rawQuery(
                                """SELECT id FROM messages
                                   WHERE conversation_id=? AND sys_id=0 AND body=? AND is_me=?
                                     AND ABS(timestamp-?) < 86400000
                                   ORDER BY ABS(timestamp-?) LIMIT 1""",
                                arrayOf(cid.toString(), m.body, if (isMe) "1" else "0",
                                    m.date.toString(), m.date.toString())
                            ).use { c -> if (c.moveToFirst()) localId = c.getLong(0) }

                            try {
                                if (localId != -1L) {
                                    db.writableDatabase.execSQL(
                                        "UPDATE messages SET sys_id=? WHERE id=?",
                                        arrayOf(m.sysId.toString(), localId.toString())
                                    )
                                } else {
                                    db.writableDatabase.execSQL(
                                        """INSERT INTO messages(conversation_id,body,timestamp,is_me,status,sys_id,sub_id)
                                           VALUES(?,?,?,?,?,?,?)""",
                                        arrayOf(cid, m.body, m.date, if (isMe) 1 else 0,
                                            when (m.type) {
                                                android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX -> "received"
                                                android.provider.Telephony.Sms.MESSAGE_TYPE_FAILED -> "failed"
                                                else -> "sent"
                                            },
                                            m.sysId, m.subId)
                                    )
                                }
                            } catch (e: android.database.sqlite.SQLiteException) {
                                android.util.Log.e("RepoSync", "INSERT failed: ${e.message}", e)
                            }
                            existing.add(m.sysId)
                            done++
                            _initialSyncProgress.value = done.toFloat() / pending
                            changed = true
                        }
                        db.writableDatabase.setTransactionSuccessful()
                    } finally {
                        db.writableDatabase.endTransaction()
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
                settings.firstImportDone = true
            } catch (e: SecurityException) {
                // no SMS access yet — keep firstImportDone=false so grant re-imports
                android.util.Log.e("RepoSync", "syncFromSystem skipped: ${e.message}", e)
            } catch (e: Exception) {
                android.util.Log.e("RepoSync", "syncFromSystem failed: ${e.message}", e)
                settings.firstImportDone = true
            } finally {
                _initialSyncProgress.value = null
                _initialSyncDone.value = true
                syncRunning = false
            }
        }
    }

    /** Re-runs [syncFromSystem] with the loading UI active. */
    fun requeryFromSystem() {
        if (syncRunning || !needsInitialImport) return
        _initialSyncDone.value = false
        syncFromSystem()
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

    /** Writes an outgoing SMS into the system Sent box (required when default app)
     *  and links the new provider id to our local row, preventing re-import dups. */
    fun writeSentToSystem(address: String, body: String, subId: Int = -1) {
        try {
            val now = System.currentTimeMillis()
            val cv = ContentValues().apply {
                put(android.provider.Telephony.Sms.ADDRESS, address)
                put(android.provider.Telephony.Sms.BODY, body)
                put(android.provider.Telephony.Sms.DATE, now)
                put(android.provider.Telephony.Sms.READ, 1)
                put(
                    android.provider.Telephony.Sms.TYPE,
                    android.provider.Telephony.Sms.MESSAGE_TYPE_SENT
                )
                if (subId > 0) put(android.provider.Telephony.Sms.SUBSCRIPTION_ID, subId)
            }
            val sysId = context.contentResolver.insert(
                android.provider.Telephony.Sms.Sent.CONTENT_URI, cv
            )?.lastPathSegment?.toLongOrNull() ?: -1L
            if (sysId > 0) {
                // Pick the message we just created: same body+is_me+sys_id=0, most recent
                // for that conversation. The (conversation_id, timestamp DESC) index
                // makes this O(log n) rather than a full messages scan with ABS().
                val convoId = conversationIdForAddress(address) ?: return
                db.writableDatabase.execSQL(
                    """UPDATE messages SET sys_id=? WHERE id=(
                       SELECT id FROM messages
                       WHERE conversation_id=? AND sys_id=0 AND body=? AND is_me=1
                       ORDER BY timestamp DESC LIMIT 1)""",
                    arrayOf(sysId.toString(), convoId.toString(), body)
                )
            }
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
