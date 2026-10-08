package com.anindra.messages.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
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
import java.io.IOException

private const val DB_NAME = "messages.db"
private const val PRE_IMPORT_BACKUP_NAME = "pre_import_backup.db"
private const val IMPORT_TEMP_NAME = "import_temp.db"

/** How long a backup snapshot may sit in the cache before it is swept. */
private const val SNAPSHOT_TTL_MS = 60L * 60L * 1000L

/** Format label for an export, which always writes the PIN container. */
private const val PIN_FORMAT = "PIN"
private const val EXPORT_MODE = "none"

/** Format label for an SMS Import / Export archive, which has no BackupFormat. */
private const val SMS_IE_FORMAT = "sms-ie"
/**
 * How a backup file is encoded.
 *
 * [RAW] is a plain SQLite database, which is what an unencrypted backup is, and
 * what most of them are. It has to be told apart from [LEGACY] before any
 * decryption is attempted, because feeding a plaintext database to the keystore
 * cipher produces garbage rather than a clean failure.
 */
enum class BackupFormat { PIN, LEGACY, RAW }
enum class ImportMode { REPLACE, MERGE }

/** Rows written per transaction while streaming a large backup. */
private const val TAG_IMPORT = "BackupImport"

/** Rows written per transaction while streaming a large backup. */
private const val IMPORT_BATCH = 500

/** How often the importer reports progress, in records. */
private const val IMPORT_PROGRESS_EVERY = 250

private const val DB_VERSION = 24
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
                blocked INTEGER NOT NULL DEFAULT 0,
                blocked_at INTEGER NOT NULL DEFAULT 0,
                pinned INTEGER NOT NULL DEFAULT 0,
                draft TEXT NOT NULL DEFAULT '',
                draft_date INTEGER NOT NULL DEFAULT 0,
                deleted_at INTEGER NOT NULL DEFAULT 0,
                deleted_reason TEXT NOT NULL DEFAULT 'manual')"""
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
                transport TEXT NOT NULL DEFAULT 'sms',
                delivered_at INTEGER NOT NULL DEFAULT 0,
                locked INTEGER NOT NULL DEFAULT 0,
                sub_id INTEGER NOT NULL DEFAULT -1,
                deleted_at INTEGER NOT NULL DEFAULT 0,
                blocked_reason TEXT NOT NULL DEFAULT '')"""
        )
        db.execSQL("CREATE INDEX idx_messages_conversation ON messages(conversation_id)")
        db.execSQL("CREATE INDEX idx_messages_conv_ts ON messages(conversation_id, timestamp)")
        db.execSQL("CREATE UNIQUE INDEX idx_messages_sys_id ON messages(transport, sys_id) WHERE sys_id>0")
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
        db.execSQL(
            """CREATE TABLE participants(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                normalized_destination TEXT NOT NULL UNIQUE,
                send_destination TEXT NOT NULL,
                display_destination TEXT NOT NULL,
                comparable_destination TEXT NOT NULL,
                country_code TEXT NOT NULL DEFAULT '',
                sub_id INTEGER NOT NULL DEFAULT -1)"""
        )
        db.execSQL("ALTER TABLE conversations ADD COLUMN group_title TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE messages ADD COLUMN address TEXT NOT NULL DEFAULT ''")
        createConversationRecipients(db)
    }

    /**
     * Who a conversation goes to. `conversations.address` stays as the primary
     * recipient so every existing 1:1 conversation keeps working untouched; a
     * conversation is a group once this table holds more than one row.
     */
    private fun createConversationRecipients(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS conversation_recipients(
                conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                address TEXT NOT NULL,
                PRIMARY KEY(conversation_id, address))"""
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_conv_recipients_address ON conversation_recipients(address)"
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
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_list ON conversations(deleted_at, pinned, timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_archived ON conversations(archived) WHERE deleted_at=0")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_scheduled_timestamp ON scheduled_messages(timestamp)")
        }
        if (oldVersion < 14) {
            db.execSQL("ALTER TABLE messages ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 15) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS participants(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    normalized_destination TEXT NOT NULL UNIQUE,
                    send_destination TEXT NOT NULL,
                    display_destination TEXT NOT NULL,
                    comparable_destination TEXT NOT NULL,
                    country_code TEXT NOT NULL DEFAULT '',
                    sub_id INTEGER NOT NULL DEFAULT -1)"""
            )
        }
        if (oldVersion < 16) {
            db.execSQL("ALTER TABLE messages ADD COLUMN transport TEXT NOT NULL DEFAULT 'sms'")
            db.execSQL("DROP INDEX IF EXISTS idx_messages_sys_id")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_sys_id " +
                    "ON messages(transport, sys_id) WHERE sys_id>0"
            )
        }
        if (oldVersion < 17) {
            db.execSQL("ALTER TABLE messages ADD COLUMN delivered_at INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 18) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN deleted_reason TEXT NOT NULL DEFAULT 'manual'")
        }
        if (oldVersion < 19) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN blocked INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 20) {
            db.execSQL("ALTER TABLE messages ADD COLUMN blocked_reason TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 21) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN blocked_at INTEGER NOT NULL DEFAULT 0")
            // Seed from the conversation's own activity so an already-blocked
            // sender gets a full window from the upgrade rather than being
            // purged the moment it is first seen.
            db.execSQL("UPDATE conversations SET blocked_at=timestamp WHERE blocked=1 AND blocked_at=0")
        }
        if (oldVersion < 22) {
            createConversationRecipients(db)
            // Every existing conversation starts as its own single recipient, so
            // 1:1 chats keep sending exactly where they did before.
            db.execSQL(
                """INSERT OR IGNORE INTO conversation_recipients(conversation_id, address)
                   SELECT id, address FROM conversations WHERE deleted_at=0"""
            )
        }
        if (oldVersion < 23) {
            // Every existing conversation is 1:1, so no group titles yet; they
            // are filled in the moment a second person is added.
            db.execSQL("ALTER TABLE conversations ADD COLUMN group_title TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 24) {
            // Who sent each message. Blank means the conversation's own address
            // (1:1 chats, and everything sent by the user).
            db.execSQL("ALTER TABLE messages ADD COLUMN address TEXT NOT NULL DEFAULT ''")
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
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        var exists = false
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (c.getString(nameIdx) == column) { exists = true; break }
            }
        }
        return exists
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
        if (!hasColumn(db, "messages", "locked")) {
            try {
                db.execSQL("ALTER TABLE messages ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
            } catch (_: android.database.sqlite.SQLiteException) {
            }
        }
        try {
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_sys_id " +
                    "ON messages(transport, sys_id) WHERE sys_id>0"
            )
        } catch (_: android.database.sqlite.SQLiteException) {
        }
        if (!hasColumn(db, "messages", "sub_id")) {
            try {
                db.execSQL("ALTER TABLE messages ADD COLUMN sub_id INTEGER NOT NULL DEFAULT -1")
            } catch (_: android.database.sqlite.SQLiteException) {
            }
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
            """SELECT c.id,c.address,c.name,c.snippet,c.timestamp,c.unread_count,c.last_is_me,
               c.archived,c.blocked,c.pinned,c.draft,c.draft_date,c.deleted_at,
               COALESCE(p.display_destination, c.address), c.group_title
               FROM conversations c
               LEFT JOIN participants p ON p.normalized_destination = c.address
               WHERE c.deleted_at=0 ORDER BY c.pinned DESC, c.timestamp DESC""",
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
                        blocked = c.getInt(8) == 1,
                        pinned = c.getInt(9) == 1,
                        draft = c.getString(10),
                        draftDate = c.getLong(11),
                        deletedAt = c.getLong(12),
                        display = c.getString(13),
                        groupTitle = c.getString(14).orEmpty()
                    )
                )
            }
        }
        out
    }

    fun conversationByIdFlow(id: Long): Flow<Conversation?> = observe {
        var out: Conversation? = null
        db.readableDatabase.rawQuery(
            """SELECT c.id,c.address,c.name,c.snippet,c.timestamp,c.unread_count,c.last_is_me,
               c.archived,c.pinned,c.draft,c.draft_date,c.deleted_at,
               COALESCE(p.display_destination, c.address), c.group_title
               FROM conversations c
               LEFT JOIN participants p ON p.normalized_destination = c.address
               WHERE c.id=? AND c.deleted_at=0""",
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
                    deletedAt = c.getLong(11),
                    display = c.getString(12),
                    groupTitle = c.getString(13).orEmpty()
                )
            }
        }
        out
    }

    fun trashedConversations(): Flow<List<Conversation>> = observe {
        val out = mutableListOf<Conversation>()
        db.readableDatabase.rawQuery(
            """SELECT c.id,c.address,c.name,c.snippet,c.timestamp,c.unread_count,c.last_is_me,
               c.archived,c.pinned,c.draft,c.draft_date,c.deleted_at,
               COALESCE(p.display_destination, c.address),c.deleted_reason
               FROM conversations c
               LEFT JOIN participants p ON p.normalized_destination = c.address
               WHERE c.deleted_at>0 ORDER BY c.deleted_at DESC""",
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
                        deletedAt = c.getLong(11),
                        display = c.getString(12),
                        deletedReason = c.getString(13) ?: TrashReason.MANUAL
                    )
                )
            }
        }
        out
    }

    fun messages(conversationId: Long, limit: Int = Int.MAX_VALUE, offset: Int = 0): Flow<List<Message>> = observe {
        val out = mutableListOf<Message>()
        db.readableDatabase.rawQuery(
            """SELECT id,body,timestamp,is_me,status,media_type,media_uri,reactions,locked,sub_id,
               transport,delivered_at,address FROM messages
               WHERE conversation_id=? AND deleted_at=0 ORDER BY timestamp DESC LIMIT ? OFFSET ?""",
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
                        subId = c.getInt(9),
                        transport = c.getString(10),
                        deliveredAt = c.getLong(11),
                        address = c.getString(12).orEmpty()
                    )
                )
            }
        }
        out.reversed()
    }

    /**
     * Conversations with at least one non-deleted message whose body contains
     * [query], case-insensitively — the home list's global message search, so a
     * word buried in an old message still surfaces its thread. Returns an empty
     * set for a blank query.
     */
    fun conversationIdsMatchingMessage(query: String, hideLinks: Boolean): Flow<Set<Long>> = observe {
        val out = mutableSetOf<Long>()
        if (query.isNotBlank()) {
            val like = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            // The LIKE is only a cheap prefilter: the visibility rule (a locked
            // body never surfaces, hidden links match only redacted text)
            // lives in MessageSearch, so SQL cannot quietly disagree with it.
            db.readableDatabase.rawQuery(
                "SELECT DISTINCT conversation_id, body, locked FROM messages WHERE deleted_at=0 " +
                    "AND body LIKE ? ESCAPE '\\'",
                arrayOf(like)
            ).use { c ->
                while (c.moveToNext()) {
                    if (MessageSearch.matchesVisible(
                            c.getString(1).orEmpty(), query, c.getInt(2) == 1, hideLinks
                        )
                    ) out += c.getLong(0)
                }
            }
        }
        out
    }

    /** Ids of [conversationId]'s messages whose *visible* body matches, oldest
     *  first. An in-chat search has to reach a hit older than the loaded
     *  window — the home list surfaces the thread, so the chat must be able to
     *  reach the message. Blank query matches nothing. */
    fun messageIdsMatching(conversationId: Long, query: String, hideLinks: Boolean): Flow<List<Long>> = observe {
        val out = mutableListOf<Long>()
        if (query.isNotBlank()) {
            val like = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            db.readableDatabase.rawQuery(
                "SELECT id, body, locked FROM messages WHERE conversation_id=? AND deleted_at=0 " +
                    "AND body LIKE ? ESCAPE '\\' ORDER BY id ASC",
                arrayOf(conversationId.toString(), like)
            ).use { c ->
                while (c.moveToNext()) {
                    if (MessageSearch.matchesVisible(
                            c.getString(1).orEmpty(), query, c.getInt(2) == 1, hideLinks
                        )
                    ) out += c.getLong(0)
                }
            }
        }
        out
    }

    fun messageCount(conversationId: Long): Int = runOnIo {
        var count = 0
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE conversation_id=? AND deleted_at=0",
            arrayOf(conversationId.toString())
        ).use { if (it.moveToFirst()) count = it.getInt(0) }
        count
    }

    fun totalConversationCount(): Int = runOnIo {
        var count = 0
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM conversations WHERE deleted_at=0", null
        ).use { if (it.moveToFirst()) count = it.getInt(0) }
        count
    }

    fun totalMessageCount(): Int = runOnIo {
        var count = 0
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE deleted_at=0", null
        ).use { if (it.moveToFirst()) count = it.getInt(0) }
        count
    }

    fun messageCountFlow(conversationId: Long): Flow<Int> = observe {
        var count = 0
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE conversation_id=? AND deleted_at=0",
            arrayOf(conversationId.toString())
        ).use { if (it.moveToFirst()) count = it.getInt(0) }
        count
    }

    /** True when two stored addresses identify the same person despite formatting
     *  differences (e.g. "+15551234567" vs "15551234567", the issue #183 case).
     *  Delegates to [AddressIdentity.samePerson]: digit-run comparison for
     *  numbers, exact (case-insensitive) match for alphanumeric sender IDs. */
    private fun samePerson(a: String, b: String): Boolean = AddressIdentity.samePerson(a, b)

    private fun matchConversationId(database: SQLiteDatabase, address: String, activeOnly: Boolean = false): Long? {
        if (address.isBlank()) return null
        val where = if (activeOnly) "WHERE deleted_at=0" else ""
        database.rawQuery("SELECT id, address FROM conversations $where", null).use { c ->
            while (c.moveToNext()) {
                if (samePerson(c.getString(1), address)) return c.getLong(0)
            }
        }
        return null
    }

    private fun findConversationForAddress(address: String): Long? =
        matchConversationId(db.readableDatabase, address)

    /** Canonical identity for same-person checks: E.164 when the address is a
     *  valid phone number, else the address unchanged (alphanumeric sender IDs
     *  such as "A1 SRB" must never be reduced to their digits — issue #207). */
    private fun canonical(address: String): String =
        AddressIdentity.canonical(address, PhoneNumberUtils.region())

    /** Inserts/refreshes the participant row (display form, country, SIM) for a
     *  canonical address. No-op for non-phone addresses (alphanumeric senders). */
    private fun upsertParticipant(database: SQLiteDatabase, address: String, subId: Int = -1) {
        if (!PhoneNumberUtils.isLikelyPhoneNumber(address)) return
        val region = PhoneNumberUtils.region()
        val e164 = PhoneNumberUtils.toE164(address, region) ?: return
        database.execSQL(
            """INSERT INTO participants(
                  normalized_destination,send_destination,display_destination,
                  comparable_destination,country_code,sub_id)
               VALUES(?,?,?,?,?,?)
               ON CONFLICT(normalized_destination) DO UPDATE SET
                  send_destination=excluded.send_destination,
                  display_destination=excluded.display_destination,
                  country_code=excluded.country_code,
                  sub_id=excluded.sub_id""",
            arrayOf<Any>(e164, e164, PhoneNumberUtils.displayFor(e164, region),
                e164.lowercase(), PhoneNumberUtils.regionFor(e164), subId)
        )
    }

    fun getOrCreateConversation(address: String, displayName: String? = null): Long =
        getOrCreateConversationBlocking(address, displayName)

    fun getOrCreateConversationBlocking(
        address: String,
        displayName: String? = null,
        subId: Int = -1,
        kind: InboundKind = InboundKind.NORMAL
    ): Long = runOnIo {
        // Store one canonical spelling per person: every incoming spelling
        // (E.164, national, formatted) maps to the same conversation row.
        val target = canonical(address).ifEmpty { address }
        var convoId = -1L
        db.writableDatabase.rawQuery(
            "SELECT id FROM conversations WHERE address=?",
            arrayOf(target)
        ).use { c -> if (c.moveToFirst()) convoId = c.getLong(0) }
        if (convoId == -1L) convoId = findConversationForAddress(target) ?: -1L

        if (convoId == -1L) {
            val cv = ContentValues().apply {
                put("address", target)
                put("name", displayName ?: contactNameFor(target) ?: target)
            }
            convoId = db.writableDatabase.insert("conversations", null, cv)
            upsertParticipant(db.writableDatabase, target, subId)
            // Every conversation needs its own address as a recipient, or adding
            // a second person would build a group that drops the original.
            if (convoId > 0) {
                db.writableDatabase.execSQL(
                    "INSERT OR IGNORE INTO conversation_recipients(conversation_id, address) VALUES(?,?)",
                    arrayOf(convoId, target)
                )
            }
            notifyChanged()
        } else if (InboundIngest.restoresTrashedConversation(kind)) {
            // A trashed thread addressed by a new chat / incoming message must be
            // restored, or ChatScreen (which filters deleted_at=0) shows a blank
            // header and sends are rejected.
            val restored = db.writableDatabase.update(
                "conversations",
                ContentValues().apply { put("deleted_at", 0) },
                "id=? AND deleted_at>0",
                arrayOf(convoId.toString())
            )
            if (restored > 0) notifyChanged()
        }
        convoId
    }

    suspend fun conversationByIdSuspend(id: Long): Conversation? = runOnIoAsync {
        var found: Conversation? = null
        db.readableDatabase.rawQuery(
            """SELECT c.id,c.address,c.name,c.snippet,c.timestamp,c.unread_count,c.last_is_me,
               c.archived,c.pinned,c.draft,c.draft_date,
               COALESCE(p.display_destination, c.address), c.group_title
               FROM conversations c
               LEFT JOIN participants p ON p.normalized_destination = c.address
               WHERE c.id=? AND c.deleted_at=0""",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) found = Conversation(
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
                display = c.getString(11),
                groupTitle = c.getString(12).orEmpty()
            )
        }
        found
    }

    /**
     * Who a conversation goes to, primary recipient first. Falls back to the
     * conversation's own `address` so a conversation with no rows here (a
     * brand-new one, or one predating the migration) still sends somewhere.
     */
    suspend fun conversationRecipients(conversationId: Long): List<String> =
        runOnIoAsync { recipientsBlocking(conversationId) }

    /** True once a conversation has more than one recipient. */
    suspend fun isGroup(conversationId: Long): Boolean =
        runOnIoAsync { recipientsBlocking(conversationId).size > 1 }

    private fun recipientsBlocking(conversationId: Long): List<String> {
        val out = mutableListOf<String>()
        db.readableDatabase.rawQuery(
            "SELECT address FROM conversation_recipients WHERE conversation_id=? ORDER BY rowid",
            arrayOf(conversationId.toString())
        ).use { c ->
            while (c.moveToNext()) out += c.getString(0)
        }
        if (out.isEmpty()) {
            db.readableDatabase.rawQuery(
                "SELECT address FROM conversations WHERE id=?", arrayOf(conversationId.toString())
            ).use { c -> if (c.moveToFirst()) out += c.getString(0) }
        }
        return out.filter { it.isNotBlank() }.distinct()
    }

    /**
     * Adds [addresses] to the conversation, keeping its primary recipient.
     * Returns the addresses that were not already on the conversation.
     */
    suspend fun addParticipants(conversationId: Long, addresses: List<String>): List<String> =
        runOnIoAsync {
            val existing = recipientsBlocking(conversationId).toMutableSet()
            val added = mutableListOf<String>()
            for (raw in addresses) {
                val address = raw.trim()
                if (address.isEmpty()) continue
                upsertParticipant(db.writableDatabase, address)
                if (!existing.add(address)) continue
                added += address
                db.writableDatabase.execSQL(
                    "INSERT OR IGNORE INTO conversation_recipients(conversation_id, address) VALUES(?,?)",
                    arrayOf(conversationId, address)
                )
            }
            // A conversation that somehow lost its own recipient row would
            // otherwise become a group that no longer sends to its original
            // address, so put it back before anything else.
            val primary = db.readableDatabase.rawQuery(
                "SELECT address FROM conversations WHERE id=?", arrayOf(conversationId.toString())
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            if (primary != null && primary !in existing && added.isNotEmpty()) {
                db.writableDatabase.execSQL(
                    "INSERT OR IGNORE INTO conversation_recipients(conversation_id, address) VALUES(?,?)",
                    arrayOf(conversationId, primary)
                )
            }
            if (added.isNotEmpty()) {
                ensureGroupTitle(conversationId)
                notifyChanged()
            }
            added
        }

    /**
     * Gives a group a default name from its members, once. A title the user set
     * is never overwritten, and a 1:1 conversation keeps its contact name.
     */
    private fun ensureGroupTitle(conversationId: Long) {
        val members = recipientsBlocking(conversationId)
        if (members.size <= 1) return
        val current = db.readableDatabase.rawQuery(
            "SELECT group_title FROM conversations WHERE id=?", arrayOf(conversationId.toString())
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        if (!current.isNullOrBlank()) return
        val names = members.map { contactNameFor(it) ?: it }.take(3)
        val title = when {
            names.size <= 1 -> names.first()
            else -> names.dropLast(1).joinToString(", ") + " +" + names.last()
        }
        db.writableDatabase.update(
            "conversations",
            ContentValues().apply { put("group_title", title) },
            "id=?", arrayOf(conversationId.toString())
        )
    }

    /**
     * Where an inbound SMS from [address] belongs, or null when the caller
     * should create a 1:1 thread.
     *
     * A 1:1 with this sender always wins: if the user has a private thread with
     * them, a text from them almost certainly means that thread, not a group.
     * Failing that, the message goes to their group, but only when exactly one
     * group is possible -- an SMS names a sender, never a conversation, so this
     * refuses to guess between two.
     */
    suspend fun conversationForInbound(address: String): Long? =
        runOnIoAsync { conversationForInboundBlocking(address) }

    fun conversationForInboundBlocking(address: String): Long? =
        conversationIdForAddressBlocking(address) ?: soleGroupBlocking(address)

    private fun conversationIdForAddressBlocking(address: String): Long? {
        var id: Long? = null
        db.readableDatabase.rawQuery(
            "SELECT id FROM conversations WHERE address=? AND deleted_at=0", arrayOf(address)
        ).use { c -> if (c.moveToFirst()) id = c.getLong(0) }
        return id ?: matchConversationId(db.readableDatabase, address, activeOnly = true)
    }

    /**
     * The one group conversation [address] belongs to, or null if ambiguous.
     *
     * Compared by canonical identity rather than by string: the network can
     * deliver an address with or without a leading '+', and an exact match would
     * silently fail to attribute those messages.
     */
    fun soleGroupBlocking(address: String): Long? {
        val groups = mutableMapOf<Long, MutableList<String>>()
        db.readableDatabase.rawQuery(
            """SELECT r.conversation_id, r.address FROM conversation_recipients r
               JOIN conversations c ON c.id = r.conversation_id
               WHERE c.deleted_at = 0""",
            null
        ).use { c ->
            val idIdx = c.getColumnIndex("conversation_id")
            val addrIdx = c.getColumnIndex("address")
            while (c.moveToNext()) {
                groups.getOrPut(c.getLong(idIdx)) { mutableListOf() } += c.getString(addrIdx)
            }
        }
        return groups
            .filter { (_, members) -> members.size > 1 }
            .filter { (_, members) -> members.any { samePerson(it, address) } }
            .keys
            .singleOrNull()
    }

    /** A group's own name, or blank when the conversation is 1:1. */
    fun groupTitleBlocking(conversationId: Long): String =
        db.readableDatabase.rawQuery(
            "SELECT group_title FROM conversations WHERE id=?", arrayOf(conversationId.toString())
        ).use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() else "" }

    /** Renames a group. Blank input clears it, restoring the default. */
    suspend fun setGroupTitle(conversationId: Long, title: String) = runOnIoAsync {
        val clean = title.trim()
        db.writableDatabase.update(
            "conversations",
            ContentValues().apply { put("group_title", clean) },
            "id=?", arrayOf(conversationId.toString())
        )
        if (clean.isEmpty()) ensureGroupTitle(conversationId)
        notifyChanged()
    }

    /**
     * Drops [address] from the conversation. The last remaining recipient
     * cannot be removed, so a conversation always has somewhere to send.
     * Returns true when a row was actually deleted.
     */
    suspend fun removeParticipant(conversationId: Long, address: String): Boolean =
        runOnIoAsync {
            if (recipientsBlocking(conversationId).size <= 1) return@runOnIoAsync false
            val removed = db.writableDatabase.delete(
                "conversation_recipients", "conversation_id=? AND address=?",
                arrayOf(conversationId.toString(), address)
            )
            if (removed > 0) notifyChanged()
            removed > 0
        }

    /** Stores a text message as 'sending'; SmsStatusReceiver confirms the final state. */
    fun sendText(conversationId: Long, body: String, subId: Int = -1): Message? {
        val now = System.currentTimeMillis()
        val clean = MessageBody.normalize(body)
        val cv = ContentValues().apply {
            put("conversation_id", conversationId)
            put("body", clean)
            put("timestamp", now)
            put("is_me", 1)
            put("status", "sending")
            put("media_type", "text")
            put("transport", MmsSupport.TRANSPORT_SMS)
            put("sub_id", subId)
        }
        val id = db.writableDatabase.insertOrThrow("messages", null, cv)
        touchConversation(conversationId, clean, now, isMe = true)
        return Message(id, conversationId, clean, now, true, "sending", subId = subId)
    }

    fun sendMedia(conversationId: Long, mediaType: String, uri: String, caption: String = ""): Message? {
        val now = System.currentTimeMillis()
        val clean = MessageBody.normalize(caption)
        val cv = ContentValues().apply {
            put("conversation_id", conversationId)
            put("body", clean)
            put("timestamp", now)
            put("is_me", 1)
            put("status", "sending")
            put("media_type", mediaType)
            put("media_uri", uri)
            put("transport", MmsSupport.TRANSPORT_SMS)
        }
        val id = db.writableDatabase.insertOrThrow("messages", null, cv)
        touchConversation(conversationId, if (mediaType == "image") "Photo" else "Voice message", now, isMe = true)
        return Message(id, conversationId, clean, now, true, "sending", mediaType, uri)
    }

    /**
     * Links an app message to the provider row it is being sent as.
     *
     * [sendMedia] stores the message with no `sys_id`, and a sent MMS reaches
     * the provider's sent box, where [importProviderMms] would import it as a
     * second message (the same picture twice in the chat). Recording the
     * provider row here makes the next sync recognise it as already present.
     */
    fun linkMmsRow(messageId: Long, sysId: Long) {
        if (messageId <= 0 || sysId <= 0) return
        db.writableDatabase.execSQL(
            "UPDATE messages SET sys_id=?, transport=? WHERE id=?",
            arrayOf<Any?>(sysId, MmsSupport.TRANSPORT_MMS, messageId)
        )
        notifyChanged()
    }

    private fun touchConversation(conversationId: Long, snippet: String, ts: Long, isMe: Boolean) {
        db.writableDatabase.execSQL(
            "UPDATE conversations SET snippet=?,timestamp=?,unread_count=0,last_is_me=?,deleted_at=0 WHERE id=?",
            arrayOf<Any?>(snippet, ts, if (isMe) 1 else 0, conversationId)
        )
        notifyChanged()
    }

    /** Store an incoming SMS. Returns conversation id. [sysId] links the row to
     *  its system-provider copy so the next sync skips it instead of duplicating.
     *  [subId] is the SIM subscription id for dual-SIM display. */
    fun receiveMessage(
        address: String,
        body: String,
        sysId: Long = 0L,
        subId: Int = -1,
        markUnread: Boolean = true
    ): Long {
        val now = System.currentTimeMillis()
        val clean = MessageBody.normalize(body)
        val convoId = conversationForInboundBlocking(address)
            ?: getOrCreateConversationBlocking(address, null, subId)

        db.writableDatabase.execSQL(
            """INSERT INTO messages(conversation_id,body,timestamp,is_me,status,sys_id,transport,sub_id,address)
               VALUES(?,?,?,?,?,?,?,?,?)""",
            arrayOf<Any?>(
                convoId, clean, now, 0, "received", sysId, MmsSupport.TRANSPORT_SMS,
                subId, address
            )
        )
        db.writableDatabase.execSQL(
            """UPDATE conversations SET snippet=?,timestamp=?,last_is_me=0,
               unread_count=unread_count+? WHERE id=?""",
            arrayOf<Any?>(clean, now, if (markUnread) 1 else 0, convoId)
        )
        notifyChanged()
        return convoId
    }

    /** Stores a keyword-blocked message soft-deleted, so it is listed under
     *  Spam & blocked → Messages rather than the conversation. No notification
     *  and no unread badge, and a trashed conversation stays trashed. */
    fun receiveBlockedMessage(address: String, body: String, sysId: Long = 0L, subId: Int = -1): Long {
        val now = System.currentTimeMillis()
        val clean = MessageBody.normalize(body)
        val convoId =
            getOrCreateConversationBlocking(address, null, subId, InboundKind.BLOCKED_KEYWORD)
        db.writableDatabase.execSQL(
            """INSERT INTO messages(conversation_id,body,timestamp,is_me,status,sys_id,transport,sub_id,
               deleted_at,blocked_reason) VALUES(?,?,?,?,?,?,?,?,?,?)""",
            arrayOf<Any?>(
                convoId, clean, now, 0, "received", sysId, MmsSupport.TRANSPORT_SMS, subId,
                now, TrashReason.BLOCKED_KEYWORD
            )
        )
        refreshConversationSnippetFor(convoId)
        notifyChanged()
        return convoId
    }

    /** Keyword-blocked messages shown under Spam & blocked → Messages. */
    fun blockedMessages(): Flow<List<BlockedMessage>> = observe {
        val out = mutableListOf<BlockedMessage>()
        db.readableDatabase.rawQuery(FolderRows.BLOCKED_SELECT, null).use { c ->
            while (c.moveToNext()) {
                out.add(
                    FolderRows.blockedMessage(
                        id = c.getLong(FolderRows.COL_ID),
                        conversationId = c.getLong(FolderRows.COL_CONVERSATION_ID),
                        address = c.getString(FolderRows.COL_ADDRESS),
                        name = c.getString(FolderRows.COL_NAME),
                        body = c.getString(FolderRows.COL_BODY),
                        timestamp = c.getLong(FolderRows.COL_TIMESTAMP),
                        blockedReason = c.getString(FolderRows.COL_BLOCKED_REASON)
                    )
                )
            }
        }
        out
    }

    /** Re-insert a blocked message the user undid a delete on. The row has to come
     *  back soft-deleted with its original reason, or it would surface in the
     *  normal conversation instead of the blocked folder. */
    fun restoreBlockedMessage(
        conversationId: Long,
        body: String,
        timestamp: Long,
        blockedReason: String
    ) {
        db.writableDatabase.execSQL(
            """INSERT INTO messages(conversation_id,body,timestamp,is_me,status,deleted_at,blocked_reason)
               VALUES(?,?,?,0,'received',?,?)""",
            arrayOf(conversationId, body, timestamp, System.currentTimeMillis(), blockedReason)
        )
        notifyChanged()
    }

    /** Delete a blocked conversation and the blocked messages in it, and drop the
     *  block so later mail from that sender is no longer diverted. */
    fun deleteBlockedConversation(conversationId: Long, address: String) {
        db.writableDatabase.execSQL(
            "DELETE FROM messages WHERE conversation_id=? AND blocked_reason!=''",
            arrayOf(conversationId)
        )
        db.writableDatabase.execSQL(
            "UPDATE conversations SET blocked=0,blocked_at=0,unread_count=0 WHERE id=?",
            arrayOf(conversationId)
        )
        if (isNumberBlocked(address)) unblockNumber(address)
        notifyChanged()
    }

    /** Put a caught message back into its conversation. Clearing blocked_reason is
     *  what makes it visible again, since the folder lists only rows that still
     *  carry one. */
    fun returnBlockedMessageToChat(messageId: Long) {
        db.writableDatabase.execSQL(
            "UPDATE messages SET deleted_at=0,blocked_reason='' WHERE id=? AND blocked_reason!=''",
            arrayOf(messageId)
        )
        notifyChanged()
    }

    fun deleteAllBlockedMessages() {
        db.writableDatabase.execSQL("DELETE FROM messages WHERE blocked_reason!=''")
        notifyChanged()
    }

    /** Drop every blocked sender, emptying the Conversations tab. */
    fun unblockAllNumbers() {
        db.writableDatabase.execSQL(
            "UPDATE conversations SET blocked=0,blocked_at=0,unread_count=0 WHERE blocked=1"
        )
        db.writableDatabase.execSQL("DELETE FROM blocked_numbers")
        notifyChanged()
    }

    fun deleteBlockedMessage(messageId: Long) {
        db.writableDatabase.execSQL(
            "DELETE FROM messages WHERE id=? AND blocked_reason!=''",
            arrayOf(messageId)
        )
        notifyChanged()
    }

    /** Manually deleted messages kept in Trash → Messages. Keyword-blocked rows
     *  are excluded: they live in Spam & blocked → Messages instead. */
    fun trashedMessages(): Flow<List<TrashedMessage>> = observe {
        val out = mutableListOf<TrashedMessage>()
        db.readableDatabase.rawQuery(FolderRows.TRASHED_SELECT, null).use { c ->
            while (c.moveToNext()) {
                out.add(
                    FolderRows.trashedMessage(
                        id = c.getLong(FolderRows.COL_ID),
                        conversationId = c.getLong(FolderRows.COL_CONVERSATION_ID),
                        address = c.getString(FolderRows.COL_ADDRESS),
                        name = c.getString(FolderRows.COL_NAME),
                        body = c.getString(FolderRows.COL_BODY),
                        timestamp = c.getLong(FolderRows.COL_TIMESTAMP),
                        deletedAt = c.getLong(FolderRows.COL_DELETED_AT)
                    )
                )
            }
        }
        out
    }

    /** Permanently removes a trashed message. */
    fun deleteMessageForeverSuspend(messageId: Long) = runOnIo {
        db.writableDatabase.execSQL("DELETE FROM messages WHERE id=?", arrayOf(messageId))
        notifyChanged()
    }

    /** Permanently removes every message-level trash entry. */
    fun emptyMessageTrashSuspend() = runOnIo {
        db.writableDatabase.execSQL("DELETE FROM messages WHERE deleted_at>0 AND blocked_reason=''")
        notifyChanged()
    }

    /** Stores an SMS from a blocked number in the "Spam & blocked" folder:
     *  the conversation is flagged blocked, unread stays 0 and no notification
     *  is posted. Unblocking returns the conversation to the inbox. A trashed
     *  conversation stays trashed, as for a blocked keyword. */
    fun receiveSpamMessage(address: String, body: String, sysId: Long = 0L, subId: Int = -1): Long {
        val now = System.currentTimeMillis()
        val clean = MessageBody.normalize(body)
        val convoId = getOrCreateConversationBlocking(address, null, subId, InboundKind.BLOCKED_NUMBER)
        db.writableDatabase.execSQL(
            """INSERT INTO messages(conversation_id,body,timestamp,is_me,status,sys_id,transport,sub_id)
               VALUES(?,?,?,?,?,?,?,?)""",
            arrayOf<Any?>(convoId, clean, now, 0, "received", sysId, MmsSupport.TRANSPORT_SMS, subId)
        )
        db.writableDatabase.execSQL(
            """UPDATE conversations SET snippet=?,timestamp=?,last_is_me=0,
               unread_count=0,blocked=1,
               blocked_at=CASE WHEN blocked_at>0 THEN blocked_at ELSE ? END WHERE id=?""",
            arrayOf<Any?>(clean, now, now, convoId)
        )
        notifyChanged()
        return convoId
    }

    fun markReadSuspend(conversationId: Long) {
        setReadSuspend(conversationId, read = true)
    }

    /** [read] false restores the unread badge, which a swipe undo needs. */
    fun setReadSuspend(conversationId: Long, read: Boolean) {
        db.writableDatabase.execSQL(
            "UPDATE conversations SET unread_count=? WHERE id=?",
            arrayOf<Any>(if (read) 0 else 1, conversationId)
        )
        notifyChanged()
    }

    fun setLockedSuspend(messageId: Long, locked: Boolean) {
        db.writableDatabase.execSQL(
            "UPDATE messages SET locked=? WHERE id=?",
            arrayOf(if (locked) 1 else 0, messageId)
        )
        refreshSnippetForLockToggle(messageId)
        notifyChanged()
    }

    /** Recomputes a conversation's snippet/timestamp/last_is_me from its newest
     *  non-deleted message, honouring the "@Lock" masking for a locked latest
     *  message. Falls back to an empty snippet when nothing is left. */
    private fun refreshConversationSnippetFor(conversationId: Long) {
        var body = ""
        var mediaType = "text"
        var locked = false
        var isMe = false
        var ts = 0L
        var found = false
        db.readableDatabase.rawQuery(
            """SELECT body,media_type,locked,is_me,timestamp FROM messages
               WHERE conversation_id=? AND deleted_at=0
               ORDER BY timestamp DESC, id DESC LIMIT 1""",
            arrayOf(conversationId.toString())
        ).use { c ->
            if (c.moveToFirst()) {
                body = c.getString(0)
                mediaType = c.getString(1)
                locked = c.getInt(2) == 1
                isMe = c.getInt(3) == 1
                ts = c.getLong(4)
                found = true
            }
        }
        val snippet = when {
            !found -> ""
            locked -> "@Lock"
            mediaType == "text" -> body
            mediaType == "image" -> "Photo"
            mediaType == "video" -> "Video"
            mediaType == "audio" -> "Voice message"
            else -> "Attachment"
        }
        db.writableDatabase.execSQL(
            "UPDATE conversations SET snippet=?, timestamp=?, last_is_me=? WHERE id=?",
            arrayOf<Any?>(snippet, if (found) ts else 0L, if (isMe) 1 else 0, conversationId.toString())
        )
    }

    /** Hides ([deleted]=true) or restores a single message. Soft delete keeps the
     *  row so the home-list preview can be recomputed and so a later system sync
     *  does not re-import it (sync dedupes on sys_id, which stays present). */
    private fun setMessageDeleted(messageId: Long, deleted: Boolean) {
        var convoId = -1L
        db.readableDatabase.rawQuery(
            "SELECT conversation_id FROM messages WHERE id=?",
            arrayOf(messageId.toString())
        ).use { c -> if (c.moveToFirst()) convoId = c.getLong(0) }
        if (convoId == -1L) return
        db.writableDatabase.execSQL(
            "UPDATE messages SET deleted_at=? WHERE id=?",
            arrayOf(if (deleted) System.currentTimeMillis() else 0L, messageId)
        )
        refreshConversationSnippetFor(convoId)
        notifyChanged()
    }

    fun deleteMessageSuspend(messageId: Long) = runOnIo { setMessageDeleted(messageId, true) }

    fun restoreMessageSuspend(messageId: Long) = runOnIo { setMessageDeleted(messageId, false) }

    /** If [messageId] is the newest message in its conversation, rewrite that
     *  row's snippet so a locked latest message doesn't leak on the home list
     *  (lock → "@Lock", unlock → the message content). */
    private fun refreshSnippetForLockToggle(messageId: Long) {
        var convoId = -1L
        db.readableDatabase.rawQuery(
            "SELECT conversation_id FROM messages WHERE id=?",
            arrayOf(messageId.toString())
        ).use { c -> if (c.moveToFirst()) convoId = c.getLong(0) }
        if (convoId == -1L) return
        val newestId = db.readableDatabase.rawQuery(
            """SELECT id FROM messages WHERE conversation_id=? AND deleted_at=0
               ORDER BY timestamp DESC, id DESC LIMIT 1""",
            arrayOf(convoId.toString())
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
        if (newestId != messageId) return
        refreshConversationSnippetFor(convoId)
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
            "UPDATE conversations SET deleted_at=?,deleted_reason=? WHERE id=?",
            arrayOf(System.currentTimeMillis().toString(), TrashReason.MANUAL, conversationId.toString())
        )
        notifyChanged()
    }

    /** Moves a conversation to trash once it has nothing left to show: no
     *  non-deleted messages and no draft worth keeping. Otherwise a no-op, so it
     *  is safe to call on every exit from a chat. */
    fun trashConversationIfEmptySuspend(conversationId: Long) {
        // Return the two values from the query lambda instead of capturing
        // mutable locals: the capture hides the assignment from static analysis
        // (CodeQL saw `remaining` as always 0 -> java/constant-comparison).
        val (remaining, pending, draft) = db.readableDatabase.rawQuery(
            """SELECT (SELECT COUNT(*) FROM messages WHERE conversation_id=? AND deleted_at=0),
                      (SELECT COUNT(*) FROM scheduled_messages WHERE conversation_id=?),
                      draft
               FROM conversations WHERE id=? AND deleted_at=0""",
            arrayOf(conversationId.toString(), conversationId.toString(), conversationId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return
            Triple(c.getInt(0), c.getInt(1), c.getString(2) ?: "")
        }
        // A draft only keeps the chat around while drafts are actually surfaced;
        // a leftover column value from when the feature was on must not.
        // A queued message is content the user is waiting on. It lives in its own
        // table, so without counting it the chat looks empty the moment the draft
        // is cleared on scheduling and the conversation vanishes from the list.
        if (ConversationLiveness.keepAlive(remaining, pending, draft)) return
        db.writableDatabase.execSQL(
            "UPDATE conversations SET deleted_at=?,deleted_reason=? WHERE id=?",
            arrayOf(System.currentTimeMillis().toString(), TrashReason.MANUAL, conversationId.toString())
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
     *  SMS/MMS provider, so the periodic [syncFromSystem] doesn't resurrect them.
     *  Needs default-SMS-app (or WRITE_SMS); skipped silently otherwise. */
    private fun purgeProviderMessages(conversationIds: List<Long>) {
        try {
            if (conversationIds.isEmpty()) return
            val ph = conversationIds.joinToString(",") { "?" }
            data class Purge(val transport: String, val sysId: Long)
            val ids = mutableListOf<Purge>()
            db.readableDatabase.rawQuery(
                "SELECT transport, sys_id FROM messages WHERE conversation_id IN ($ph) AND sys_id>0",
                conversationIds.map { it.toString() }.toTypedArray()
            ).use { c -> while (c.moveToNext()) ids.add(Purge(c.getString(0), c.getLong(1))) }
            ids.groupBy { it.transport }.forEach { (transport, messages) ->
                val uri = MmsSupport.providerUri(transport)?.let(android.net.Uri::parse) ?: return@forEach
                messages.map { it.sysId }.chunked(200).forEach { chunk ->
                    val placeholders = chunk.joinToString(",") { "?" }
                    context.contentResolver.delete(
                        uri,
                        "_id IN ($placeholders)",
                        chunk.map { it.toString() }.toTypedArray()
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("RepoSync", "Provider purge skipped: ${e.message}")
        }
    }
    /** Hard-deletes the selected [buckets] once they are older than their own
     *  window. Blocked senders and deleted chats are the only buckets that
     *  remove a conversation row; a keyword-blocked message is removed on its
     *  own, so a chat the user is not looking at is never taken away by the
     *  cleanup. */
    fun purgeRetainedSuspend(
        buckets: Set<RetentionBucket>,
        trashDays: Int,
        spamDays: Int
    ) {
        if (buckets.isEmpty()) return
        val now = System.currentTimeMillis()
        val stale = mutableListOf<Long>()

        if (RetentionBucket.TRASH in buckets) {
            val arg = argFor(RetentionBucket.TRASH, now, trashDays, spamDays)
            val sql = RetentionPolicy.sql(RetentionBucket.TRASH)
            db.readableDatabase.rawQuery(
                "SELECT id FROM conversations WHERE $sql", arg
            ).use { c -> while (c.moveToNext()) stale.add(c.getLong(0)) }
            db.writableDatabase.execSQL(
                "DELETE FROM messages WHERE conversation_id IN " +
                    "(SELECT id FROM conversations WHERE $sql)",
                arg
            )
            db.writableDatabase.execSQL("DELETE FROM conversations WHERE $sql", arg)
        }
        if (RetentionBucket.BLOCKED_SENDERS in buckets) {
            val arg = argFor(RetentionBucket.BLOCKED_SENDERS, now, trashDays, spamDays)
            val sql = RetentionPolicy.sql(RetentionBucket.BLOCKED_SENDERS)
            db.readableDatabase.rawQuery(
                "SELECT id FROM conversations WHERE $sql", arg
            ).use { c -> while (c.moveToNext()) stale.add(c.getLong(0)) }
            db.writableDatabase.execSQL(
                "DELETE FROM messages WHERE conversation_id IN " +
                    "(SELECT id FROM conversations WHERE $sql)",
                arg
            )
            db.writableDatabase.execSQL("DELETE FROM conversations WHERE $sql", arg)
        }
        if (RetentionBucket.KEYWORD_MESSAGES in buckets) {
            db.writableDatabase.execSQL(
                "DELETE FROM messages WHERE ${RetentionPolicy.KEYWORD_BLOCKED_SQL}",
                argFor(RetentionBucket.KEYWORD_MESSAGES, now, trashDays, spamDays)
            )
        }
        purgeProviderMessages(stale)
    }

    /** The lines a grouped notification should carry: only what the other person
     *  has sent and only what is still unread. A plain blocking read, because the
     *  notification is built on a receiver thread, not from a Flow. */
    fun notificationHistory(
        conversationId: Long,
        maxLines: Int
    ): List<com.anindra.messages.sms.NotificationLine> = runOnIo {
        var unread = 0
        db.readableDatabase.rawQuery(
            "SELECT unread_count FROM conversations WHERE id=?",
            arrayOf(conversationId.toString())
        ).use { c -> if (c.moveToFirst()) unread = c.getInt(0) }
        val out = mutableListOf<com.anindra.messages.sms.NotificationLine>()
        db.readableDatabase.rawQuery(
            "SELECT body,timestamp FROM messages WHERE conversation_id=? AND deleted_at=0" +
                " AND is_me=0 ORDER BY timestamp DESC, id DESC LIMIT ?",
            arrayOf(
                conversationId.toString(),
                com.anindra.messages.sms.NotificationHistory
                    .takeCount(unread, maxLines).toString()
            )
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    com.anindra.messages.sms.NotificationLine(
                        text = c.getString(0),
                        timestamp = c.getLong(1),
                        fromMe = false
                    )
                )
            }
        }
        out.reversed()
    }

    private fun argFor(
        bucket: RetentionBucket,
        now: Long,
        trashDays: Int,
        spamDays: Int
    ): Array<String> =
        arrayOf(RetentionPolicy.cutoffFor(bucket, now, trashDays, spamDays).toString())

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
            arrayOf<Any?>(serializeReactions(reactions), messageId)
        )
        notifyChanged()
    }

    /**
     * Applies a reaction that arrived as text from another of our devices
     * (#188). Finds the newest message in the conversation whose body matches
     * the quoted snippet and toggles the emoji on it. Returns false when nothing
     * matches, so the caller stores the text as an ordinary message instead.
     */
    suspend fun applyIncomingReaction(
        address: String,
        emoji: String,
        snippet: String,
        added: Boolean
    ): Boolean = runOnIoAsync {
        val convoId = conversationIdForAddressBlocking(address) ?: return@runOnIoAsync false
        val like = snippet.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        var targetId = -1L
        var current = ""
        db.readableDatabase.rawQuery(
            "SELECT id,reactions FROM messages WHERE conversation_id=? AND deleted_at=0 " +
                "AND body LIKE ? ESCAPE '\\' ORDER BY timestamp DESC, id DESC LIMIT 1",
            arrayOf(convoId.toString(), like)
        ).use { c ->
            if (c.moveToFirst()) {
                targetId = c.getLong(0)
                current = c.getString(1)
            }
        }
        if (targetId < 0) return@runOnIoAsync false
        val map = parseReactions(current).toMutableMap()
        if (added) map[emoji] = (map[emoji] ?: 0) + 1 else map.remove(emoji)
        db.writableDatabase.execSQL(
            "UPDATE messages SET reactions=? WHERE id=?",
            arrayOf<Any?>(serializeReactions(map), targetId)
        )
        notifyChanged()
        true
    }

    fun markMessageStatusSuspend(messageId: Long, status: String) {
        if (status == "delivered") {
            db.writableDatabase.execSQL(
                "UPDATE messages SET status=?, delivered_at=? WHERE id=?",
                arrayOf<Any?>(status, System.currentTimeMillis(), messageId)
            )
        } else {
            db.writableDatabase.execSQL(
                "UPDATE messages SET status=? WHERE id=?", arrayOf<Any?>(status, messageId)
            )
        }
        notifyChanged()
    }

    fun pinnedConversations(): Flow<List<Conversation>> = observe {
        val out = mutableListOf<Conversation>()
        db.readableDatabase.rawQuery(
            """SELECT c.id,c.address,c.name,c.snippet,c.timestamp,c.unread_count,c.last_is_me,
               c.archived,c.pinned,c.draft,c.draft_date,
               COALESCE(p.display_destination, c.address)
               FROM conversations c
               LEFT JOIN participants p ON p.normalized_destination = c.address
               WHERE c.pinned=1 ORDER BY c.timestamp DESC""",
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
                        display = c.getString(11)
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

    fun unpinAll() {
        db.writableDatabase.execSQL("UPDATE conversations SET pinned=0 WHERE pinned=1")
        notifyChanged()
    }

    fun saveDraft(conversationId: Long, draft: String) {
        val now = System.currentTimeMillis()
        db.writableDatabase.execSQL(
            "UPDATE conversations SET draft=?,draft_date=? WHERE id=?",
            arrayOf<Any?>(draft, now, conversationId)
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

    /** True when [address] is on the blocklist, matching canonical spellings. */
    fun isAddressBlocked(address: String): Boolean {
        if (isNumberBlocked(address)) return true
        val canon = canonical(address).ifEmpty { address }
        return canon != address && isNumberBlocked(canon)
    }

    fun conversationIdForAddress(address: String): Long? = runOnIo {
        var id: Long? = null
        db.readableDatabase.rawQuery(
            "SELECT id FROM conversations WHERE address=? AND deleted_at=0", arrayOf(address)
        ).use { c -> if (c.moveToFirst()) id = c.getLong(0) }
        id ?: matchConversationId(db.readableDatabase, address, activeOnly = true)
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
        setConversationBlockedForAddress(number, blocked = true)
        notifyChanged()
    }

    fun unblockNumber(number: String) {
        db.writableDatabase.execSQL("DELETE FROM blocked_numbers WHERE number=?", arrayOf(number))
        invalidateBlockCache(number)
        setConversationBlockedForAddress(number, blocked = false)
        notifyChanged()
    }

    private fun setConversationBlockedForAddress(number: String, blocked: Boolean) {
        val flag = if (blocked) 1 else 0
        // Ageing a blocked sender from when it was blocked, not from its last
        // message, is what lets auto-delete ever reach a sender that keeps texting.
        val blockedAt = if (blocked) System.currentTimeMillis() else 0
        db.writableDatabase.execSQL(
            "UPDATE conversations SET blocked=?,blocked_at=? WHERE address=?",
            arrayOf<Any?>(flag, blockedAt, canonical(number).ifEmpty { number })
        )
        db.writableDatabase.execSQL(
            "UPDATE conversations SET blocked=?,blocked_at=? WHERE address=?",
            arrayOf<Any?>(flag, blockedAt, number)
        )
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

    suspend fun scheduledMessageById(id: Long): ScheduledMessage? = runOnIoAsync {
        db.readableDatabase.rawQuery(
            "SELECT id,address,body,timestamp,conversation_id,sub_id FROM scheduled_messages WHERE id=?",
            arrayOf(id.toString())
        ).use { c ->
            if (!c.moveToFirst()) return@use null
            ScheduledMessage(
                id = c.getLong(0),
                address = c.getString(1),
                body = c.getString(2),
                timestamp = c.getLong(3),
                conversationId = c.getLong(4),
                subId = c.getInt(5)
            )
        }
    }

    fun updateScheduledMessage(id: Long, timestamp: Long) {
        require(timestamp > System.currentTimeMillis()) { "Scheduled time must be in the future" }
        val updated = db.writableDatabase.update(
            "scheduled_messages",
            ContentValues().apply { put("timestamp", timestamp) },
            "id=?",
            arrayOf(id.toString())
        )
        require(updated > 0) { "No scheduled message with id $id" }
        notifyChanged()
    }

    fun peekBackupFormat(context: Context, sourceUri: android.net.Uri): BackupFormat {
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { inp ->
                val magic = ByteArray(16)
                val read = inp.read(magic)
                when {
                    read >= 4 && BackupCrypto.isPinMagic(magic.copyOf(4)) -> BackupFormat.PIN
                    // A SQLite database names itself in its first 16 bytes, so an
                    // unencrypted backup is recognisable without trying to
                    // decrypt it.
                    read == 16 && String(magic).startsWith("SQLite format 3") ->
                        BackupFormat.RAW
                    else -> BackupFormat.LEGACY
                }
            } ?: BackupFormat.LEGACY
        } catch (_: Exception) {
            BackupFormat.LEGACY
        }
    }

    /**
     * Writes an encrypted copy of the database to the configured location.
     *
     * Every `return false` used to be the same `false`, so a revoked SD-card
     * permission and a rejected PIN both reached the user as one generic
     * failure. Each early exit now carries the reason it took.
     */
    sealed interface ExportResult {
        data class Success(
            val fileName: String,
            val bytes: Long,
            /** Attempts made; >1 means a retry produced this file. */
            val attempts: Int = 1
        ) : ExportResult

        data class Error(
            val message: String,
            val attempts: Int = 1,
            /** Whether another attempt could plausibly have helped. */
            val transient: Boolean = false
        ) : ExportResult
    }

    fun backupDatabase(context: Context, pin: String): ExportResult {
        if (!BackupPolicy.isBackupAllowed(settings.privacyModeEnabled)) {
            return exportFailed("Backups are turned off while privacy mode is on", PIN_FORMAT)
        }
        if (!BackupCrypto.isValidPin(pin)) return exportFailed("PIN must be 4 digits or more", PIN_FORMAT)
        return writeBackup(context, PIN_FORMAT, ".enc") { snapshot, out ->
            snapshot.inputStream().use { inp -> BackupCrypto.encryptWithPin(inp, out, pin) }
        }
    }

    /**
     * Plaintext SQLite copy, readable outside the app (issue #292). The caller
     * must have warned the user first: this file is not encrypted.
     */
    fun backupDatabaseUnencrypted(context: Context): ExportResult {
        if (!BackupPolicy.isBackupAllowed(settings.privacyModeEnabled)) {
            return exportFailed("Backups are turned off while privacy mode is on", BackupFormat.RAW.name)
        }
        return writeBackup(context, BackupFormat.RAW.name, ".db") { snapshot, out ->
            snapshot.inputStream().use { inp -> inp.copyTo(out) }
        }
    }

    /**
     * Writes a verified snapshot to the configured location, retrying the
     * destination write with backoff.
     *
     * The live database is snapshotted once and the retries re-stream that
     * stable file, so a transient failure re-reads neither the moving database
     * nor (for an encrypted export) the cipher. Only a complete, non-empty write
     * is recorded as success; a partial destination file is deleted.
     */
    private fun writeBackup(
        context: Context,
        format: String,
        extension: String,
        writeBody: (java.io.File, java.io.OutputStream) -> Long
    ): ExportResult {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) return exportFailed("No message database to back up yet", format)
        val snapshot = createSnapshot(dbFile)
            ?: return exportFailed("Could not snapshot the message database", format)
        try {
            if (!isValidSqliteFile(snapshot)) {
                return exportFailed("Backup snapshot is not a valid database", format)
            }
            val name = "messages_backup_${System.currentTimeMillis()}$extension"
            val target = createBackupTarget(context, name) ?: return exportFailed(
                if (settings.backupTreeUri.isNotEmpty()) "Cannot write to the chosen backup folder"
                else "Cannot write to Documents/Messages",
                format
            )
            val outcome = TransferRetry.run {
                // Debug-only: the regression script forces the first N attempts
                // to fail so the backoff path can be observed on a device.
                if (TransferRetry.consumeInjectedFailure()) {
                    throw IOException("injected backup failure on attempt $it")
                }
                context.contentResolver.openOutputStream(target)?.use { out ->
                    writeBody(snapshot, out)
                } ?: throw IOException("cannot open the backup file for writing")
            }
            return when (outcome) {
                is TransferRetry.Result.Success -> {
                    val written = outcome.value
                    if (written <= 0L) {
                        context.contentResolver.delete(target, null, null)
                        exportFailed("Backup file was written empty", format, outcome.attempts)
                    } else {
                        recordTransfer(
                            TransferOperation.EXPORT, format, EXPORT_MODE, true,
                            "Saved $name", attempts = outcome.attempts
                        )
                        ExportResult.Success(name, written, outcome.attempts)
                    }
                }
                is TransferRetry.Result.Failure -> {
                    // A half-written file must not masquerade as a backup.
                    context.contentResolver.delete(target, null, null)
                    exportFailed(
                        outcome.error.message ?: outcome.error.javaClass.simpleName,
                        format, outcome.attempts, outcome.transient
                    )
                }
            }
        } finally {
            snapshot.delete()
        }
    }

    /**
     * A stable copy of the live database to stream from, or null if it cannot be
     * made. Folds the journal into the main file first, matching the pre-prune
     * snapshot, so the copy is one coherent database rather than a file caught
     * mid-write.
     */
    private fun createSnapshot(dbFile: File): File? {
        val dir = File(context.cacheDir, "backup-snapshots")
        return try {
            dir.mkdirs()
            // Unique per run: a manual backup can overlap the periodic worker,
            // and two runs must never delete or stream each other's snapshot.
            val target = File.createTempFile("snapshot-", ".db", dir)
            // Sweep only snapshots old enough to be a previous crash's leftovers.
            val staleBefore = System.currentTimeMillis() - SNAPSHOT_TTL_MS
            dir.listFiles()?.forEach { if (it != target && it.lastModified() < staleBefore) it.delete() }
            runCatching { db.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close() }
            dbFile.copyTo(target, overwrite = true)
            target
        } catch (_: Exception) {
            null
        }
    }

    /** Opens the destination document in the configured location. */
    private fun createBackupTarget(context: Context, name: String): android.net.Uri? {
        val resolver = context.contentResolver
        val custom = settings.backupTreeUri.takeIf { it.isNotEmpty() }
        // A user-chosen location must never silently fall back to internal
        // storage: fail instead, so they know the backup did not go where
        // they asked (e.g. revoked SD-card access).
        return if (custom != null) {
            val treeUri = android.net.Uri.parse(custom)
            android.provider.DocumentsContract.createDocument(
                resolver,
                android.provider.DocumentsContract.buildDocumentUriUsingTree(
                    treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                ),
                "application/octet-stream",
                name
            )
        } else {
            resolver.insert(
                MediaStore.Files.getContentUri("external"),
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Messages")
                }
            )
        }
    }

    private fun exportFailed(
        reason: String,
        format: String = PIN_FORMAT,
        attempts: Int = 1,
        transient: Boolean = false
    ): ExportResult.Error {
        recordTransfer(
            TransferOperation.EXPORT, format, EXPORT_MODE, false, reason, attempts = attempts
        )
        return ExportResult.Error(reason, attempts, transient)
    }


    sealed interface ImportResult {
        /** [merged] is the number of messages added, non-null only for a merge import. */
        data class Success(
            val merged: Int? = null,
            val attempts: Int = 1
        ) : ImportResult

        data class Error(
            val message: String,
            val attempts: Int = 1,
            /** Whether another attempt could plausibly have helped. */
            val transient: Boolean = false
        ) : ImportResult
    }

    /**
     * Why a message in an incoming backup was not written.
     *
     * The counts are the only record that a merge left rows behind. Without
     * them "merged 4,000" reads as a clean run when 600 were already there.
     */
    object Conflict {
        const val ALREADY_PRESENT = "already present"
        const val PROVIDER_ID_TAKEN = "provider id already used"
        const val PROVIDER_ID_DROPPED = "provider id from another device"
        const val CONVERSATION_MERGED = "conversation matched an existing one"
        const val CONVERSATION_UNRESOLVED = "conversation could not be matched"
        const val RECORD_UNREADABLE = "record could not be read"
        const val PART_TOO_LARGE = "attachment too large"
    }

    /**
     * How a streaming import went.
     *
     * [skipped] and [truncated] are reported rather than swallowed: a 50k backup
     * that silently loses records to a damaged line or an oversized photo is
     * worse than one that says so.
     */
    data class ImportReport(
        val added: Int,
        val seen: Int,
        val skipped: Int,
        val truncated: Boolean = false,
        /** Reason -> count; see [Conflict]. Empty for a run with nothing lost. */
        val conflicts: Map<String, Int> = emptyMap()
    )

    /** Bumps [reason] in a running tally, so each site reports its own loss. */
    private fun MutableMap<String, Int>.count(reason: String, by: Int = 1) {
        if (by <= 0) return
        this[reason] = (this[reason] ?: 0) + by
    }

    /**
     * Writes one run to the transfer log.
     *
     * Recorded here rather than in the ViewModel so every path that reaches the
     * database is logged, including the debug probes the regression scripts
     * drive — a script asserting on a conflict count depends on the same call
     * the UI makes.
     */
    private fun recordTransfer(
        operation: TransferOperation,
        format: String,
        mode: String,
        succeeded: Boolean,
        detail: String,
        added: Int = 0,
        seen: Int = 0,
        skipped: Int = 0,
        attempts: Int = 1,
        recovered: Boolean = false,
        conflicts: Map<String, Int> = emptyMap()
    ) {
        TransferLogStore.append(
            context,
            TransferEntry(
                timestamp = System.currentTimeMillis(),
                operation = operation,
                format = format,
                mode = mode,
                succeeded = succeeded,
                detail = if (attempts > 1) "$detail (after $attempts attempts)" else detail,
                added = added,
                seen = seen,
                skipped = skipped,
                attempts = attempts,
                recovered = recovered,
                conflicts = conflicts.filterValues { it > 0 }
            )
        )
    }

    /** Copies an imported MMS attachment into app storage and returns its URI. */
    private fun storeImportedImage(bytes: ByteArray, msg: SmsIeBackup.Message): String {
        return try {
            val dir = File(context.filesDir, "mms-import").apply { mkdirs() }
            val safeName = msg.imageName.ifBlank { "image.${SmsIeBackup.extensionFor(msg.imageMime)}" }
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            val file = File(dir, "${msg.timestamp}_$safeName")
            file.writeBytes(bytes)
            "content://${context.packageName}.fileprovider/mms/${file.name}"
        } catch (_: Exception) {
            ""
        }
    }

    fun importSmsIeFrom(
        context: Context,
        uri: android.net.Uri,
        mode: ImportMode = ImportMode.MERGE,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): ImportResult {
        val staged = SmsIeReader.stage(context, uri, context.cacheDir)
            ?: run {
                recordTransfer(
                    TransferOperation.IMPORT, SMS_IE_FORMAT, mode.name, false,
                    "Cannot read that backup file"
                )
                return ImportResult.Error("Cannot read that backup file")
            }
        return staged.use {
            val report = importStaged(staged, mode, onProgress)
            when {
                report.seen == 0 -> {
                    recordTransfer(
                        TransferOperation.IMPORT, SMS_IE_FORMAT, mode.name, false,
                        "No messages found in that backup"
                    )
                    ImportResult.Error("No messages found in that backup")
                }
                // Partial success is still success: a backup with one unreadable
                // record should not read as a total failure to the user.
                else -> {
                    // Mirror into the system SMS store, so the phone's own
                    // messaging app shows the imported history too. Without this
                    // an sms-ie import lives only here, and the user finds their
                    // backup missing from the app they actually use day to day.
                    pushLocalMessagesToProvider()
                    recordTransfer(
                        TransferOperation.IMPORT, SMS_IE_FORMAT, mode.name, true,
                        "Imported ${report.added} of ${report.seen} message(s)",
                        added = report.added,
                        seen = report.seen,
                        skipped = report.skipped,
                        conflicts = report.conflicts
                    )
                    ImportResult.Success(report.added)
                }
            }
        }
    }

    /**
     * Streams a staged backup into the database.
     *
     * Three things make this survive a 50k-message backup with images:
     *
     *  - records are read and written one at a time, in batches, so neither the
     *    message list nor the MMS payload is ever fully resident;
     *  - conversations are looked up through a map built once, instead of
     *    re-scanning the whole table per message, which was quadratic;
     *  - a record that fails to insert costs that record, not the batch and not
     *    the import. One bad row used to discard everything with it.
     */
    fun importStaged(
        staged: SmsIeReader.Staged,
        mode: ImportMode = ImportMode.MERGE,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): ImportReport {
        if (SmsIeBackupPolicy.clearsExisting(mode)) clearAllMessages()

        val database = db.writableDatabase
        val byAddress = HashMap<String, Long>()
        database.rawQuery("SELECT id, address FROM conversations", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val address = c.getString(1) ?: continue
                byAddress[address] = id
                // Also key the canonical spelling, so a backup written with a
                // different national/E.164 formatting still finds the thread.
                val canon = canonical(address)
                if (canon != address) byAddress.putIfAbsent(canon, id)
            }
        }

        val batch = ArrayList<SmsIeBackup.Message>(IMPORT_BATCH)
        var added = 0
        var seen = 0
        var skipped = 0
        var truncated = staged.skippedParts.isNotEmpty()
        val conflicts = LinkedHashMap<String, Int>()
        conflicts.count(Conflict.PART_TOO_LARGE, staged.skippedParts.size)
        // Only meaningful when merging. After a Replace the table is empty, so
        // seeding from it would cost a full scan to find nothing.
        val known = if (SmsIeBackupPolicy.clearsExisting(mode)) HashSet()
        else existingMessageKeys()

        fun flush() {
            if (batch.isEmpty()) return
            val rows = batch.sortedBy { it.timestamp }
            batch.clear()
            // Snapshot the shared tally so a retried batch starts from the state
            // before its rolled-back attempt rather than double-counting it.
            val baseAdded = added
            val baseSkipped = skipped
            val baseConflicts = LinkedHashMap(conflicts)
            val baseKnown = HashSet(known)
            val baseByAddress = HashMap(byAddress)
            val outcome = TransferRetry.run {
                added = baseAdded
                skipped = baseSkipped
                conflicts.clear()
                conflicts.putAll(baseConflicts)
                known.clear()
                known.addAll(baseKnown)
                byAddress.clear()
                byAddress.putAll(baseByAddress)
                database.beginTransaction()
                try {
                    for (msg in rows) {
                        val address = canonical(msg.address).ifEmpty { msg.address }
                        var cid = byAddress[address] ?: byAddress[msg.address]
                        if (cid == null) {
                            cid = database.insert("conversations", null, ContentValues().apply {
                                put("address", address)
                                put("name", contactNameFor(address) ?: address)
                            })
                            if (cid <= 0) {
                                skipped++
                                conflicts.count(Conflict.CONVERSATION_UNRESOLVED)
                                continue
                            }
                            byAddress[address] = cid
                        }
                        val transport = if (msg.isMms) MmsSupport.TRANSPORT_MMS else MmsSupport.TRANSPORT_SMS
                        val key = messageKey(address, msg.timestamp, msg.isMe, msg.body, transport)
                        if (!known.add(key)) {
                            skipped++
                            conflicts.count(Conflict.ALREADY_PRESENT)
                            continue
                        }
                        val image = msg.imageBytes
                        val row = database.insert("messages", null, ContentValues().apply {
                            put("conversation_id", cid)
                            put("body", msg.body)
                            put("timestamp", msg.timestamp)
                            put("is_me", if (msg.isMe) 1 else 0)
                            put("status", msg.status)
                            put("transport", transport)
                            put("media_type", if (image != null) "image" else "text")
                            put("media_uri", if (image != null) storeImportedImage(image, msg) else "")
                        })
                        if (row <= 0) {
                            skipped++
                            conflicts.count(Conflict.RECORD_UNREADABLE)
                            continue
                        }
                        if (!msg.isMe && !msg.read) {
                            database.execSQL(
                                "UPDATE conversations SET unread_count=unread_count+1 WHERE id=?",
                                arrayOf(cid)
                            )
                        }
                        upsertParticipant(database, address)
                        added++
                    }
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
            }
            if (outcome is TransferRetry.Result.Failure) {
                // A batch that keeps failing must not take the rest of the
                // import with it; the rows already written in it are lost, and
                // that is reported rather than hidden.
                added = baseAdded
                skipped = baseSkipped + rows.size
                conflicts.clear()
                conflicts.putAll(baseConflicts)
                conflicts.count(Conflict.RECORD_UNREADABLE, rows.size)
                Log.w(
                    "SmsIeImport",
                    "batch failed after ${outcome.attempts} attempt(s): ${outcome.error.message}"
                )
            }
        }

        SmsIeReader.forEachRecord(staged) { json ->
            val msg = SmsIeBackup.record(json) { name ->
                if (staged.wasSkipped(name)) null else staged.partBytes(name)
            }
            seen++
            if (msg == null) {
                skipped++
                conflicts.count(Conflict.RECORD_UNREADABLE)
            } else {
                batch.add(msg)
                if (batch.size >= IMPORT_BATCH) flush()
            }
            if (seen % IMPORT_PROGRESS_EVERY == 0) onProgress?.invoke(seen, 0)
        }
        flush()

        refreshConversationSnippets()
        reMigrateParticipants()
        notifyChanged()
        onProgress?.invoke(seen, seen)
        return ImportReport(added, seen, skipped, truncated, conflicts)
    }

    /**
     * Identity of a message for duplicate detection: the same conversation, the
     * same instant, the same direction, transport and text. Deliberately not the
     * provider id — that belongs to whichever device issued it.
     */
    private fun messageKey(
        address: String,
        timestamp: Long,
        isMe: Boolean,
        body: String,
        transport: String
    ): String = "$address|$timestamp|${if (isMe) 1 else 0}|$transport|$body"

    /** Keys for every message already stored, so a merge can skip repeats. */
    private fun existingMessageKeys(): HashSet<String> {
        val keys = HashSet<String>()
        val addressByConvo = HashMap<Long, String>()
        db.readableDatabase.rawQuery("SELECT id, address FROM conversations", null).use { c ->
            while (c.moveToNext()) addressByConvo[c.getLong(0)] = c.getString(1) ?: ""
        }
        db.readableDatabase.rawQuery(
            "SELECT conversation_id, timestamp, is_me, body, transport FROM messages", null
        ).use { c ->
            while (c.moveToNext()) {
                val address = addressByConvo[c.getLong(0)] ?: continue
                keys += messageKey(address, c.getLong(1), c.getInt(2) == 1, c.getString(3) ?: "", c.getString(4) ?: "")
            }
        }
        return keys
    }

    /** Drops every stored message and conversation, leaving settings intact. */
    fun clearAllMessages() {
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            database.execSQL("DELETE FROM messages")
            database.execSQL("DELETE FROM conversations")
            database.execSQL("DELETE FROM participants")
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        notifyChanged()
    }

    fun importDatabase(
        context: Context,
        sourceUri: android.net.Uri,
        pin: String?,
        mode: ImportMode = ImportMode.REPLACE,
        onProgress: (Int) -> Unit = {}
    ): ImportResult {
        val dbFile = context.getDatabasePath(DB_NAME)
        val backupFile = File(dbFile.parent, PRE_IMPORT_BACKUP_NAME)
        val tempFile = File(dbFile.parent, IMPORT_TEMP_NAME)
        try {
            val isPin = peekBackupFormat(context, sourceUri) == BackupFormat.PIN
            if (isPin && pin == null) {
                return ImportResult.Error("Backup is PIN-protected. Enter the PIN to import.")
            }
            // Stage the file first, and never touch the live DB until it is
            // verified. A plain SQLite backup is copied as-is: attempting to
            // decrypt it and then re-opening the source to recover was how a
            // perfectly good backup came back as "corrupted", because the second
            // read produced nothing and the check then saw an empty file.
            val format = peekBackupFormat(context, sourceUri)
            if (format == BackupFormat.PIN && pin == null) {
                return ImportResult.Error("Backup is PIN-protected. Enter the PIN to import.")
            }

            // A transient read failure is retried with backoff; a rejected PIN
            // is not, because no number of retries can change it.
            val staging = stageBackup(context, sourceUri, format, pin, tempFile)
            val staged: Boolean
            val attempts: Int
            when (staging) {
                is TransferRetry.Result.Success -> {
                    staged = staging.value
                    attempts = staging.attempts
                }
                is TransferRetry.Result.Failure -> {
                    tempFile.delete()
                    val reason = "Cannot open backup file"
                    recordTransfer(
                        TransferOperation.IMPORT, format.name, mode.name, false, reason,
                        attempts = staging.attempts
                    )
                    return ImportResult.Error(reason, staging.attempts, staging.transient)
                }
            }

            if (!staged) {
                tempFile.delete()
                val reason =
                    if (format == BackupFormat.PIN) "Wrong PIN or corrupted file"
                    else "Cannot decrypt this backup on this device"
                recordTransfer(
                    TransferOperation.IMPORT, format.name, mode.name, false, reason,
                    attempts = attempts
                )
                return ImportResult.Error(reason, attempts)
            }
            Log.i(
                TAG_IMPORT,
                "staged ${tempFile.length()} bytes from ${sourceUri.lastPathSegment} ($format)"
            )

            // Validate the temp file is a real SQLite database
            if (!isValidSqliteFile(tempFile)) {
                Log.e(
                    TAG_IMPORT,
                    "temp file is not a SQLite database: ${tempFile.length()} bytes " +
                        "header=${runCatching {
                            RandomAccessFile(tempFile, "r").use { raf ->
                                ByteArray(16).also { raf.readFully(it) }
                            }.joinToString("") { b -> "%02x".format(b) }
                        }.getOrNull()}"
                )
                tempFile.delete()
                recordTransfer(
                    TransferOperation.IMPORT, format.name, mode.name, false,
                    "Invalid or corrupted backup file", attempts = attempts
                )
                return ImportResult.Error("Invalid or corrupted backup file", attempts)
            }

            if (mode == ImportMode.MERGE) {
                when (val merge = TransferRetry.run { mergeDatabase(tempFile, onProgress) }) {
                    is TransferRetry.Result.Failure -> {
                        tempFile.delete()
                        val reason =
                            "Merge failed: ${merge.error.message ?: merge.error.javaClass.simpleName}"
                        recordTransfer(
                            TransferOperation.IMPORT, format.name, mode.name, false, reason,
                            attempts = merge.attempts
                        )
                        return ImportResult.Error(reason, merge.attempts, merge.transient)
                    }
                    is TransferRetry.Result.Success -> {
                        val merged = merge.value
                        tempFile.delete()
                        pushLocalMessagesToProvider()
                        reMigrateParticipants()
                        notifyChanged()
                        recordTransfer(
                            TransferOperation.IMPORT, format.name, mode.name, true,
                            "Merged ${merged.added} message(s)", added = merged.added,
                            attempts = attempts, conflicts = merged.conflicts
                        )
                        return ImportResult.Success(merged.added, attempts)
                    }
                }
            }

            // Backup current DB in case swap fails
            onProgress(countMessages(tempFile))
            dbFile.copyTo(backupFile, overwrite = true)

            // Close the shared DB, swap files, and reopen
            db.close()
            try {
                tempFile.renameTo(dbFile)
                if (!dbFile.exists()) {
                    // Restore from backup
                    backupFile.renameTo(dbFile)
                    db = Db(context)
                    recordTransfer(
                        TransferOperation.IMPORT, format.name, mode.name, false,
                        "Failed to replace database file", attempts = attempts
                    )
                    return ImportResult.Error("Failed to replace database file", attempts)
                }
                db = Db(context)
                // A restored backup should be fully visible: lift any
                // conversations that were in the trash when the backup was made.
                db.writableDatabase.execSQL("UPDATE conversations SET deleted_at=0 WHERE deleted_at>0")
                db.writableDatabase.execSQL("UPDATE messages SET deleted_at=0")
                // Mirror the restored history into the system SMS store.
                pushLocalMessagesToProvider()
                reMigrateParticipants()
                // Clean up
                tempFile.delete()
                backupFile.delete()
                recordTransfer(
                    TransferOperation.IMPORT, format.name, mode.name, true,
                    "Restored backup", attempts = attempts
                )
                return ImportResult.Success(null, attempts)
            } catch (e: Exception) {
                // Restore backup on failure
                if (!dbFile.exists()) backupFile.renameTo(dbFile)
                db = Db(context)
                tempFile.delete()
                val reason = "Database swap failed: ${e.message ?: e.javaClass.simpleName}"
                recordTransfer(
                    TransferOperation.IMPORT, format.name, mode.name, false, reason,
                    attempts = attempts
                )
                return ImportResult.Error(reason, attempts, TransferRetry.isTransient(e))
            }
        } catch (e: Exception) {
            tempFile.delete()
            val reason = "Import failed: ${e.message ?: e.javaClass.simpleName}"
            recordTransfer(TransferOperation.IMPORT, BackupFormat.RAW.name, mode.name, false, reason)
            return ImportResult.Error(reason, transient = TransferRetry.isTransient(e))
        }
    }

    /**
     * Reads the source into [tempFile], retrying a transient read failure.
     *
     * Returns false rather than throwing when the stream opened but the
     * container did not decode — a wrong PIN or a corrupt file — because that is
     * permanent and a retry would only re-reject it.
     */
    private fun stageBackup(
        context: Context,
        sourceUri: android.net.Uri,
        format: BackupFormat,
        pin: String?,
        tempFile: File
    ): TransferRetry.Result<Boolean> = TransferRetry.run {
        var decrypted = false
        val opened = context.contentResolver.openInputStream(sourceUri)?.use { inp ->
            FileOutputStream(tempFile).use { out ->
                when (format) {
                    BackupFormat.PIN -> decrypted = BackupCrypto.decryptWithPin(inp, out, pin!!)
                    BackupFormat.RAW -> {
                        inp.copyTo(out)
                        decrypted = true
                    }
                    // Legacy keystore-bound. A wrong or absent key fails the
                    // GCM tag, which is the correct outcome rather than
                    // silently importing an empty database.
                    BackupFormat.LEGACY -> decrypted = BackupCrypto.decrypt(inp, out)
                }
            }
            true
        }
        if (opened == null) throw IOException("cannot open backup file")
        decrypted
    }

    /**
     * Repairs the database if a REPLACE import died mid-swap.
     *
     * Called once at startup before anything opens the database. The importer
     * keeps the pre-import copy and the verified temp file beside the live
     * database until the swap is complete; if the live file is gone, one of
     * those is the whole history and is promoted back. If the live file is
     * healthy, the leftovers are just scratch and are removed.
     */
    fun recoverInterruptedImport() {
        val dbFile = context.getDatabasePath(DB_NAME)
        val preImport = File(dbFile.parent, PRE_IMPORT_BACKUP_NAME)
        val temp = File(dbFile.parent, IMPORT_TEMP_NAME)
        val liveExists = dbFile.isFile && dbFile.length() > 0L
        val action = ImportRecovery.decide(
            liveDatabaseExists = liveExists,
            preImportExists = preImport.isFile,
            tempIsValid = temp.isFile && isValidSqliteFile(temp)
        )
        when (action) {
            ImportRecovery.Action.RESTORE_PRE_IMPORT -> {
                val restored = runCatching { preImport.copyTo(dbFile, overwrite = true) }.isSuccess &&
                    dbFile.isFile && dbFile.length() > 0L
                if (restored) {
                    Log.w(TAG_IMPORT, "recovered an interrupted restore from $PRE_IMPORT_BACKUP_NAME")
                    recordTransfer(
                        TransferOperation.IMPORT, BackupFormat.RAW.name, "replace", true,
                        "Recovered an interrupted restore", recovered = true
                    )
                }
            }
            ImportRecovery.Action.PROMOTE_TEMP -> {
                val promoted = runCatching { temp.copyTo(dbFile, overwrite = true) }.isSuccess &&
                    dbFile.isFile && dbFile.length() > 0L
                if (promoted) {
                    Log.w(TAG_IMPORT, "completed an interrupted restore from $IMPORT_TEMP_NAME")
                    recordTransfer(
                        TransferOperation.IMPORT, BackupFormat.RAW.name, "replace", true,
                        "Completed an interrupted restore", recovered = true
                    )
                }
            }
            ImportRecovery.Action.NONE -> Unit
        }
        // Keep the pre-import copy if the live database is still broken, so the
        // next launch can try again rather than deleting the only history.
        val healthy = dbFile.isFile && dbFile.length() > 0L
        if (healthy) {
            temp.delete()
            preImport.delete()
        } else if (temp.isFile && !isValidSqliteFile(temp)) {
            temp.delete()
        }
    }


    /** Total message rows in a decrypted backup—shown as the target count for a replace import. */
    private fun countMessages(file: File): Int = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM messages", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        }
    } catch (_: Exception) {
        0
    }

    /** Merges the backup DB's conversations and messages into the live DB, keeping
     *  existing rows and adding only backup rows not already present. Returns the
     *  number of messages written. */
    private fun mergeDatabase(backupFile: File, onProgress: (Int) -> Unit): MergeReport {
        val target = db.writableDatabase
        var added = 0
        val conflicts = LinkedHashMap<String, Int>()
        SQLiteDatabase.openDatabase(backupFile.path, null, SQLiteDatabase.OPEN_READONLY).use { backup ->
            // Ids this device's provider actually holds. A backup carries ids from
            // wherever it was made, and those mean nothing here.
            val liveProviderIds = HashSet<Long>()
            runCatching {
                context.contentResolver.query(
                    android.provider.Telephony.Sms.CONTENT_URI,
                    arrayOf(android.provider.Telephony.Sms._ID), null, null, null
                )?.use { c -> while (c.moveToNext()) liveProviderIds.add(c.getLong(0)) }
            }
            val carriedIds = ArrayList<Long>()
            runCatching {
                backup.rawQuery(
                    "SELECT sys_id FROM messages WHERE sys_id>0", null
                ).use { c -> while (c.moveToNext()) carriedIds.add(c.getLong(0)) }
            }
            val adopted = LegacyBackupSchema.adoptProviderIds(carriedIds, liveProviderIds)
            val droppedIds = carriedIds.distinct().size - adopted.size
            val conflicts = LinkedHashMap<String, Int>()
            if (droppedIds > 0) {
                conflicts[Conflict.PROVIDER_ID_DROPPED] = droppedIds
                Log.i(
                    TAG_IMPORT,
                    "dropped $droppedIds provider id(s) from another device; ${adopted.size} kept"
                )
            }

            val existing = HashSet<String>()
            target.rawQuery("SELECT address FROM conversations", null).use { c ->
                while (c.moveToNext()) existing.add(c.getString(0))
            }
            val convoMap = HashMap<Long, Long>()
            // Newest message per live conversation, refreshed only when merged rows are newer
            val newest = HashMap<Long, Triple<Long, String, Int>>()
            // Restored incoming messages make the conversation unread (like a fresh receive)
            val unreadBump = HashMap<Long, Int>()
            target.beginTransaction()
            try {
                backup.rawQuery(
                    """SELECT id,address,name,snippet,timestamp,unread_count,last_is_me,
                       archived,pinned,draft,draft_date,deleted_at FROM conversations""", null
                ).use { c ->
                    while (c.moveToNext()) {
                        val address = c.getString(1)
                        val tId: Long =
                            if (existing.contains(address)) {
                                conflicts.count(Conflict.CONVERSATION_MERGED)
                                target.rawQuery("SELECT id FROM conversations WHERE address=?", arrayOf(address))
                                    .use { q -> if (q.moveToFirst()) q.getLong(0) else -1L }
                            } else {
                                val mergedId = matchConversationId(target, address)
                                if (mergedId != null) {
                                    conflicts.count(Conflict.CONVERSATION_MERGED)
                                    existing.add(address)
                                    mergedId
                                } else {
                                    existing.add(address)
                                    target.insert(
                                        "conversations", null,
                                        ContentValues().apply {
                                            put("address", address)
                                            put("name", c.getString(2))
                                            put("snippet", c.getString(3))
                                            put("timestamp", c.getLong(4))
                                            put("unread_count", c.getInt(5))
                                            put("last_is_me", c.getInt(6))
                                            put("archived", c.getInt(7))
                                            put("pinned", c.getInt(8))
                                            put("draft", c.getString(9))
                                            put("draft_date", c.getLong(10))
                                            // 0: lift conversations trashed in the backup
                                            put("deleted_at", 0)
                                        }
                                    )
                                }
                            }
                        if (tId == -1L) continue
                        convoMap[c.getLong(0)] = tId
                    }
                }

                val backupColumns = mutableSetOf<String>()
                backup.rawQuery("PRAGMA table_info(messages)", null).use { columns ->
                    while (columns.moveToNext()) backupColumns.add(columns.getString(1))
                }
                val messageQuery = LegacyBackupSchema.messagesQuery(backupColumns)
                backup.rawQuery(messageQuery, null).use { m ->
                    while (m.moveToNext()) {
                        val tId = convoMap[m.getLong(0)] ?: run {
                            conflicts.count(Conflict.CONVERSATION_UNRESOLVED)
                            continue
                        }
                        val body = m.getString(1)
                        val ts = m.getLong(2)
                        val isMe = m.getInt(3)
                        val transport = m.getString(LegacyBackupSchema.TRANSPORT_INDEX)
                        val dup = target.rawQuery(
                            """SELECT 1 FROM messages WHERE conversation_id=? AND timestamp=?
                               AND is_me=? AND body=? AND transport=? AND media_type=? AND media_uri=?
                               AND (?='sms' OR sys_id=?) LIMIT 1""",
                            arrayOf(tId.toString(), ts.toString(), isMe.toString(), body, transport,
                                m.getString(5), m.getString(6), transport, m.getLong(8).toString())
                        ).use { q -> q.moveToFirst() }
                        if (dup) {
                            conflicts.count(Conflict.ALREADY_PRESENT)
                            continue
                        }
                        // A provider id only means something on the device that
                        // issued it. Carrying it from a backup made on another
                        // phone leaves rows whose ids match nothing here, and the
                        // next reconcile then deletes the import as though the
                        // messages had been removed. Kept only when this device's
                        // provider really has the row.
                        val carried = m.getLong(8)
                        val sysId = if (carried in liveProviderIds) carried else 0L
                        if (sysId > 0) {
                            val dupSys = target.rawQuery(
                                "SELECT 1 FROM messages WHERE transport=? AND sys_id=?",
                                arrayOf(transport, sysId.toString())
                            ).use { q -> q.moveToFirst() }
                            if (dupSys) {
                                conflicts.count(Conflict.PROVIDER_ID_TAKEN)
                                continue
                            }
                        }
                        val insertId = target.insert(
                            "messages", null,
                            ContentValues().apply {
                                put("conversation_id", tId)
                                put("body", body)
                                put("timestamp", ts)
                                put("is_me", isMe)
                                put("status", m.getString(4))
                                put("media_type", m.getString(5))
                                put("media_uri", m.getString(6))
                                put("reactions", m.getString(7))
                                put("sys_id", sysId)
                                put("transport", transport)
                                put("locked", m.getInt(9))
                                put("sub_id", m.getInt(10))
                            }
                        )
                        if (insertId <= 0) continue
                        added++
                        onProgress(added)
                        // A refused insert (constraint, disk) is a lost message
                        // and used to look exactly like a successful merge.
                        if (insertId <= 0) {
                            conflicts.count(Conflict.RECORD_UNREADABLE)
                        }
                        if (isMe == 0) {
                            unreadBump[tId] = (unreadBump[tId] ?: 0) + 1
                        }
                        val snippet = when (m.getString(5)) {
                            "text" -> body
                            "image" -> "Photo"
                            "video" -> "Video"
                            "audio" -> "Voice message"
                            else -> "Attachment"
                        }
                        val cur = newest[tId]
                        if (cur == null || ts > cur.first) newest[tId] = Triple(ts, snippet, isMe)
                    }
                }

                for ((tId, n) in newest) {
                    target.execSQL(
                        "UPDATE conversations SET snippet=?,timestamp=?,last_is_me=? WHERE id=? AND timestamp<?",
                        arrayOf<Any?>(n.second, n.first, n.third, tId, n.first)
                    )
                }

                for ((tId, n) in unreadBump) {
                    target.execSQL(
                        "UPDATE conversations SET unread_count=unread_count+? WHERE id=?",
                        arrayOf<Any?>(n, tId)
                    )
                }

                backup.rawQuery("SELECT number,timestamp FROM blocked_numbers", null).use { c ->
                    while (c.moveToNext()) {
                        target.execSQL(
                            "INSERT OR IGNORE INTO blocked_numbers(number,timestamp) VALUES(?,?)",
                            arrayOf(c.getString(0), c.getLong(1))
                        )
                    }
                }

                // conversation_notifications arrived after v8, so a backup from an
                // older build simply does not have the table. Per-conversation
                // notification settings are then left at their default, which is
                // what a fresh conversation gets anyway.
                if (backupHasTable(backup, "conversation_notifications")) {
                    backup.rawQuery(
                        "SELECT conversation_id,notifications_enabled FROM conversation_notifications", null
                    ).use { c ->
                        while (c.moveToNext()) {
                            val tId = convoMap[c.getLong(0)] ?: continue
                            target.execSQL(
                                "INSERT OR IGNORE INTO conversation_notifications(conversation_id,notifications_enabled) VALUES(?,?)",
                                arrayOf<Any?>(tId, c.getInt(1))
                            )
                        }
                    }
                } else {
                    Log.i(TAG_IMPORT, "backup predates conversation_notifications; using defaults")
                }

                target.setTransactionSuccessful()
            } finally {
                target.endTransaction()
            }
        }
        return MergeReport(added, conflicts)
    }

    /** What a merge wrote and what it had to leave alone. */
    data class MergeReport(val added: Int, val conflicts: Map<String, Int> = emptyMap())

    /** True when the incoming backup has the named table; older ones do not. */
    private fun backupHasTable(backup: SQLiteDatabase, name: String): Boolean =
        runCatching {
            backup.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(name)
            ).use { it.moveToFirst() }
        }.getOrDefault(false)

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

    /**
     * Newest incoming message in [conversationId], used by the notification
     * Delete action (#285) to trash the message the notification is showing.
     */
    suspend fun latestReceivedMessageIdSuspend(conversationId: Long): Long? = runOnIoAsync {
        var found: Long? = null
        db.readableDatabase.rawQuery(
            "SELECT id FROM messages WHERE conversation_id=? AND deleted_at=0 AND is_me=0 " +
                "ORDER BY timestamp DESC, id DESC LIMIT 1",
            arrayOf(conversationId.toString())
        ).use { c -> if (c.moveToFirst()) found = c.getLong(0) }
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
        queryPhoneLookup(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, address, null)
            ?.let { return it }
        return try {
            val dirs = context.contentResolver.query(
                android.provider.ContactsContract.Directory.ENTERPRISE_CONTENT_URI,
                arrayOf(android.provider.ContactsContract.Directory._ID),
                null, null, null
            )?.use { c ->
                val ids = mutableListOf<Long>()
                while (c.moveToNext()) ids.add(c.getLong(0))
                ids
            } ?: emptyList()
            dirs.filter { it != android.provider.ContactsContract.Directory.DEFAULT }
                .firstNotNullOfOrNull { dir ->
                    queryPhoneLookup(
                        android.provider.ContactsContract.PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI,
                        address, dir
                    )
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryPhoneLookup(base: android.net.Uri, address: String, directoryId: Long?): String? {
        return try {
            var uri = android.net.Uri.withAppendedPath(base, android.net.Uri.encode(address))
            if (directoryId != null) {
                uri = uri.buildUpon().appendQueryParameter("directory", directoryId.toString()).build()
            }
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) {
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
    /**
     * Deletes local message rows whose provider row no longer exists.
     *
     * The local database is the app's own store, not a view of the provider, so
     * a delete performed by another app -- SMS Import / Export's "Wipe messages"
     * being the one users hit -- left every message still on screen even though
     * the phone had none of them.
     *
     * Only rows that came *from* the provider are considered: `sys_id = 0` marks
     * a row that only ever existed locally (an import, or a message this app
     * wrote while not being the default handler), and those must survive a
     * provider that has never heard of them.
     *
     * Returns true when anything was removed.
     */
    fun pruneMessagesMissingFromProvider(): Boolean {
        val resolver = context.contentResolver
        val live = HashSet<Long>()
        for ((uri, idColumn, transport) in PROVIDER_MESSAGE_SOURCES) {
            runCatching {
                resolver.query(
                    uri, arrayOf(idColumn), null, null, null
                )?.use { c ->
                    val i = c.getColumnIndex(idColumn)
                    if (i >= 0) while (c.moveToNext()) live.add(
                        ProviderPresence.key(transport, c.getLong(i))
                    )
                }
            }.onFailure {
                // Without permission the provider cannot be read, so nothing can
                // be proven missing. Guessing here would delete real messages.
                Log.w("RepoSync", "provider read failed, not pruning: ${it.message}")
                return false
            }
        }

        val database = db.writableDatabase
        val doomed = ArrayList<Long>()
        database.rawQuery("SELECT id, transport, sys_id FROM messages WHERE sys_id>0", null)
            .use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val transport = c.getString(1) ?: "sms"
                    // SMS and MMS have independent id spaces, so a row only counts
                    // as present if its own transport's id is still there.
                    if (live.contains(ProviderPresence.key(transport, c.getLong(2)))) continue
                    doomed.add(id)
                }
            }
        if (doomed.isEmpty()) return false

        // This deletes user data on the strength of the provider's state, and
        // the local copy is the only one left if the provider was emptied by
        // something else. A copy is taken first so the decision is reversible.
        backUpBeforePrune(doomed.size)

        database.beginTransaction()
        try {
            doomed.forEach { database.delete("messages", "id=?", arrayOf(it.toString())) }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        removeEmptiedConversations()
        Log.i("RepoSync", "pruned ${doomed.size} message(s) deleted outside the app")
        return true
    }

    /**
     * Snapshots the database before a prune removes anything.
     *
     * The prune decides to delete on the strength of what the system provider
     * currently holds, and the local rows are frequently the only surviving copy
     * -- that is exactly the case that made this necessary. If the reconcile is
     * ever wrong, the messages have to be recoverable, so the file is copied
     * aside first and the name says how many rows it was taken for.
     */
    private fun backUpBeforePrune(doomed: Int) {
        runCatching {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.isFile) return
            // Fold the write-ahead log in first, or the copy is missing whatever
            // has not been checkpointed yet.
            runCatching { db.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close() }
            val dir = File(context.filesDir, "prune-backups").apply { mkdirs() }
            val stamp = java.text.SimpleDateFormat(
                "yyyyMMdd-HHmmss", java.util.Locale.US
            ).format(java.util.Date())
            val target = File(dir, "before-prune-$stamp-$doomed.db")
            dbFile.copyTo(target, overwrite = true)
            // Keep the three most recent; each is a full copy of the history.
            dir.listFiles()
                ?.filter { it.name.startsWith("before-prune-") }
                ?.sortedByDescending { it.name }
                ?.drop(3)
                ?.forEach { runCatching { it.delete() } }
            Log.i("RepoSync", "database copied to ${target.name} before pruning $doomed row(s)")
        }.onFailure {
            // Not fatal: the prune is still correct. But say so, because this is
            // the difference between a reversible decision and an irreversible one.
            Log.w("RepoSync", "could not back up before prune: ${it.message}")
        }
    }

    private var providerObserver: android.database.ContentObserver? = null
    private var providerHandler: android.os.Handler? = null
    private val resyncRunnable = Runnable { syncFromSystem() }

    private val PROVIDER_MESSAGE_SOURCES = listOf(
        Triple(
            android.provider.Telephony.Sms.CONTENT_URI,
            android.provider.Telephony.Sms._ID,
            MmsSupport.TRANSPORT_SMS
        ),
        Triple(
            android.provider.Telephony.Mms.CONTENT_URI,
            android.provider.Telephony.Mms._ID,
            MmsSupport.TRANSPORT_MMS
        )
    )

    /** Drops conversations left with no messages, so the list has no empty rows. */
    private fun removeEmptiedConversations() {
        db.writableDatabase.execSQL(
            "DELETE FROM conversations WHERE deleted_at=0 AND id NOT IN " +
                "(SELECT DISTINCT conversation_id FROM messages)"
        )
    }

    /**
     * Watches the system SMS/MMS provider and re-syncs when another app changes it.
     *
     * Without this, a delete performed elsewhere -- SMS Import / Export's "Wipe
     * messages" is the one users hit -- was invisible until the app was
     * restarted, because the local database is a store of its own rather than a
     * view of the provider.
     *
     * Notifications are coalesced with a short debounce, because wiping a phone
     * produces one notification per deleted row and re-syncing on each of those
     * would be a great deal of work for the same end state.
     */
    fun observeProviderChanges() {
        if (providerObserver != null) return
        if (providerHandler == null) {
            providerHandler = android.os.Handler(android.os.Looper.getMainLooper())
        }
        val resolver = context.contentResolver
        val observer = object : android.database.ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                scheduleProviderResync()
            }
        }
        runCatching {
            for ((uri, _, _) in PROVIDER_MESSAGE_SOURCES) {
                resolver.registerContentObserver(uri, true, observer)
            }
        }.onFailure {
            Log.w("RepoSync", "could not observe provider: ${it.message}")
            return
        }
        providerObserver = observer
    }

    private fun scheduleProviderResync() {
        val handler = providerHandler ?: return
        handler.removeCallbacks(resyncRunnable)
        handler.postDelayed(resyncRunnable, PROVIDER_RESYNC_DEBOUNCE_MS)
    }

    private fun releaseProviderObserver() {
        providerObserver?.let {
            runCatching { context.contentResolver.unregisterContentObserver(it) }
        }
        providerObserver = null
    }

    fun syncFromSystem() {
        if (syncRunning) return
        syncRunning = true
        syncExecutor.execute {
            try {
                val resolver = context.contentResolver
                val initial = needsInitialImport
                // Heal pre-fix alphanumeric sender rows before importing, so a
                // pending message from the same sender reuses the repaired thread
                // instead of creating a second one.
                var changed = repairAlphanumericSenders()
                android.util.Log.d("RepoSync", "Starting syncFromSystem")
                // Count first so the read phase (which can take a while on a phone
                // with a large provider) shows a moving bar instead of an apparently
                // frozen loading screen; the count is cheap and indexed.
                val total = try {
                    resolver.query(
                        android.provider.Telephony.Sms.CONTENT_URI,
                        arrayOf("COUNT(*)"), null, null, null
                    )?.use { if (it.moveToFirst()) it.getInt(0) else 0 } ?: 0
                } catch (_: Exception) { 0 }
                if (initial && total > 0) _initialSyncProgress.value = 0f

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
                var read = 0
                cursor.use { c ->
                    while (c.moveToNext()) {
                        val addr = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
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
                        read++
                        if (initial && total > 0) {
                            _initialSyncProgress.value = SyncProgress.read(read, total)
                        }
                    }
                }

                val incomingSysIds = byAddress.values.flatMapTo(mutableSetOf()) { list -> list.map { it.sysId } }
                val existing = mutableSetOf<Long>()
                incomingSysIds.chunked(500).forEach { chunk ->
                    val ph = chunk.joinToString(",") { "?" }
                    db.readableDatabase.rawQuery(
                        "SELECT sys_id FROM messages WHERE transport=? AND sys_id IN ($ph)",
                        arrayOf(MmsSupport.TRANSPORT_SMS) + chunk.map { it.toString() }
                    ).use { c -> while (c.moveToNext()) existing.add(c.getLong(0)) }
                }

                val pending = byAddress.values.sumOf { list -> list.count { it.sysId !in existing } }
                android.util.Log.d("RepoSync", "Loaded ${byAddress.size} addresses, $pending pending messages")
                if (pending > 0) _initialSyncProgress.value = if (initial) SyncProgress.READ_END else 0f

                var done = 0
                val batchSize = 50
                val pendingMessages = mutableListOf<Triple<String, Long, SysSms>>()
                val addrsWithPending = byAddress.entries.filter { (_, msgs) ->
                    msgs.any { it.sysId !in existing }
                }
                var resolved = 0
                addrsWithPending.forEach { (addr, msgs) ->
                    val cid = getOrCreateConversationBlocking(addr)
                    msgs.filter { it.sysId !in existing }.forEach { m ->
                        pendingMessages.add(Triple(addr, cid, m))
                    }
                    resolved++
                    if (initial) {
                        _initialSyncProgress.value = SyncProgress.resolve(resolved, addrsWithPending.size)
                    }
                }
                pendingMessages.chunked(batchSize).forEach { batch ->
                    db.writableDatabase.beginTransaction()
                    try {
                        for ((_, cid, m) in batch) {
                            val isMe = m.type != android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX
                            var localId = -1L
                            db.readableDatabase.rawQuery(
                                """SELECT id FROM messages
                                   WHERE conversation_id=? AND transport=? AND sys_id=0 AND body=? AND is_me=?
                                     AND ABS(timestamp-?) < 86400000
                                   ORDER BY ABS(timestamp-?) LIMIT 1""",
                                arrayOf(cid.toString(), MmsSupport.TRANSPORT_SMS, m.body, if (isMe) "1" else "0",
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
                                        """INSERT INTO messages(conversation_id,body,timestamp,is_me,status,sys_id,transport,sub_id)
                                           VALUES(?,?,?,?,?,?,?,?)""",
                                        arrayOf<Any?>(cid, m.body, m.date, if (isMe) 1 else 0,
                                            when (m.type) {
                                                android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX -> "received"
                                                android.provider.Telephony.Sms.MESSAGE_TYPE_FAILED -> "failed"
                                                else -> "sent"
                                            },
                                            m.sysId, MmsSupport.TRANSPORT_SMS, m.subId)
                                    )
                                }
                            } catch (e: android.database.sqlite.SQLiteException) {
                                android.util.Log.e("RepoSync", "INSERT failed: ${e.message}", e)
                            }
                            existing.add(m.sysId)
                            done++
                            _initialSyncProgress.value = SyncProgress.import(done, pending)
                            changed = true
                        }
                        db.writableDatabase.setTransactionSuccessful()
                    } finally {
                        db.writableDatabase.endTransaction()
                    }
                }

                if (importProviderMms().isNotEmpty()) changed = true

                val stale = db.readableDatabase.rawQuery(
                    """SELECT COUNT(*) FROM conversations
                       WHERE timestamp=0 AND id IN (SELECT DISTINCT conversation_id FROM messages)""",
                    null
                ).use { c -> c.moveToFirst() && c.getLong(0) > 0 }

                // Messages deleted in the system provider by another app -- most
                // often SMS Import / Export's "Wipe messages" -- have to come out
                // of the local database too. The sync only ever added rows, so
                // without this the app keeps showing messages that no longer
                // exist anywhere on the phone.
                if (pruneMessagesMissingFromProvider()) changed = true

                if (changed || stale) {
                    refreshConversationSnippets()
                    notifyChanged()
                }
                // Fold split threads off the sync thread: runOnIo would block the
                // single sync executor (future.get()) until the O(C²) heal finishes,
                // which on a phone with hundreds of threads delays the "Loading" UI.
                // Queued on the same executor so it stays serialized with imports.
                syncExecutor.execute { mergeSplitConversations() }
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

    /** One-shot heal for conversations whose stored address was reduced to bare
     *  digits before issue #207 ("A1 SRB" → "1"). The system provider still
     *  holds the original sender ID, so each affected conversation is
     *  re-addressed from the `sys_id`s of its messages. Idempotent; retries on
     *  the next sync when SMS access is still missing. Returns true when a row
     *  was rewritten. */
    private fun repairAlphanumericSenders(): Boolean {
        if (settings.alphanumericRepairDone) return false
        try {
            val byAddress = LinkedHashMap<String, MutableList<Long>>()
            context.contentResolver.query(
                android.provider.Telephony.Sms.CONTENT_URI,
                arrayOf(
                    android.provider.Telephony.Sms._ID,
                    android.provider.Telephony.Sms.ADDRESS
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val addr = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    if (addr.none { it.isLetter() }) continue
                    byAddress.getOrPut(addr) { mutableListOf() }.add(c.getLong(0))
                }
            }
            if (byAddress.isEmpty()) {
                settings.alphanumericRepairDone = true
                return false
            }
            var changed = false
            db.writableDatabase.beginTransaction()
            try {
                for ((addr, sysIds) in byAddress) {
                    val convoIds = mutableSetOf<Long>()
                    sysIds.chunked(500).forEach { chunk ->
                        val ph = chunk.joinToString(",") { "?" }
                        db.readableDatabase.rawQuery(
                            "SELECT DISTINCT conversation_id FROM messages WHERE transport=? AND sys_id IN ($ph)",
                            arrayOf(MmsSupport.TRANSPORT_SMS) + chunk.map { it.toString() }
                        ).use { q -> while (q.moveToNext()) convoIds.add(q.getLong(0)) }
                    }
                    for (cid in convoIds) {
                        val row = db.readableDatabase.rawQuery(
                            "SELECT address, name FROM conversations WHERE id=?",
                            arrayOf(cid.toString())
                        ).use { q ->
                            if (q.moveToFirst()) q.getString(0) to q.getString(1) else null
                        } ?: continue
                        val (oldAddr, oldName) = row
                        if (AddressIdentity.samePerson(oldAddr, addr)) continue
                        db.writableDatabase.execSQL(
                            """UPDATE conversations
                               SET address=?, name=CASE WHEN name=? THEN ? ELSE name END
                               WHERE id=?""",
                            arrayOf(addr, oldAddr, addr, cid.toString())
                        )
                        changed = true
                    }
                }
                db.writableDatabase.setTransactionSuccessful()
            } finally {
                db.writableDatabase.endTransaction()
            }
            settings.alphanumericRepairDone = true
            return changed
        } catch (e: SecurityException) {
            android.util.Log.e("RepoSync", "alphanumeric repair skipped: ${e.message}", e)
        } catch (e: Exception) {
            android.util.Log.e("RepoSync", "alphanumeric repair failed: ${e.message}", e)
        }
        return false
    }

    private fun importProviderMms(): List<MmsSupport.InboundMms> {
        val existing = mutableSetOf<Long>()
        db.readableDatabase.rawQuery("SELECT sys_id FROM messages WHERE transport='mms' AND sys_id>0", null)
            .use { cursor -> while (cursor.moveToNext()) existing.add(cursor.getLong(0)) }
        val imported = mutableListOf<MmsSupport.InboundMms>()
        try {
            MmsProviderReader(context.contentResolver).read(existing) { message ->
                runOnIo {
                    val database = db.writableDatabase
                    database.beginTransaction()
                    try {
                        val duplicate = database.rawQuery(
                            "SELECT 1 FROM messages WHERE transport='mms' AND sys_id=?",
                            arrayOf(message.id.toString())
                        ).use { it.moveToFirst() }
                        if (!duplicate) {
                            val address = canonical(message.address).ifEmpty { message.address }
                            // Same routing as an inbound SMS: a 1:1 with the
                            // sender wins, otherwise the group they are in, and
                            // only when exactly one is possible.
                            val cid = conversationForInboundBlocking(address)
                                ?: database.insertOrThrow(
                                    "conversations", null, ContentValues().apply {
                                        put("address", address)
                                        put("name", contactNameFor(address) ?: address)
                                    }
                                )
                            database.insertOrThrow("messages", null, ContentValues().apply {
                                put("conversation_id", cid)
                                put("body", message.content.body)
                                put("timestamp", message.timestamp)
                                put("is_me", if (message.isMe) 1 else 0)
                                put("status", if (message.isMe) "sent" else "received")
                                put("sys_id", message.id)
                                put("transport", MmsSupport.TRANSPORT_MMS)
                                put("sub_id", message.subId)
                                put("media_type", if (message.content.imageId == null) "text" else "image")
                                put("media_uri", message.content.imageId?.let { "content://mms/part/$it" } ?: "")
                                put("address", if (message.isMe) "" else address)
                            })
                            if (!message.isMe && !message.read) database.execSQL(
                                "UPDATE conversations SET unread_count=unread_count+1 WHERE id=?", arrayOf(cid)
                            )
                            upsertParticipant(database, address, message.subId)
                            if (!message.isMe) imported.add(
                                MmsSupport.InboundMms(address, message.content.body, message.timestamp)
                            )
                        }
                        database.setTransactionSuccessful()
                    } finally {
                        database.endTransaction()
                    }
                }
            }
        } catch (_: Exception) {
            android.util.Log.w("RepoSync", "MMS import incomplete; retry on next sync")
        }
        return imported
    }

    /** Imports MMS that finished downloading and returns the inbound messages
     *  that were new, so the caller can notify. Not wrapped in [runOnIo]:
     *  [importProviderMms] already serializes its writes on the same executor. */
    fun importDownloadedMms(): List<MmsSupport.InboundMms> = importProviderMms()

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
                 snippet=COALESCE((SELECT CASE WHEN locked=1 THEN '@Lock' WHEN media_type='image' THEN 'Photo' ELSE body END FROM messages WHERE conversation_id=conversations.id AND deleted_at=0 ORDER BY timestamp DESC, id DESC LIMIT 1),''),
                 timestamp=COALESCE((SELECT MAX(timestamp) FROM messages WHERE conversation_id=conversations.id AND deleted_at=0),0),
                 last_is_me=COALESCE((SELECT is_me FROM messages WHERE conversation_id=conversations.id AND deleted_at=0 ORDER BY timestamp DESC LIMIT 1),0)
               WHERE id IN (SELECT DISTINCT conversation_id FROM messages WHERE deleted_at=0)"""
        )
    }

    /** Folds conversations that [samePerson] considers the same contact into the
     *  earliest one. Heals threads that were already split before the lookup fix
     *  (issue #183). Idempotent; ignores trashed conversations. */
    private fun mergeSplitConversations() {
        val convos = ArrayList<Triple<Long, String, Int>>()
        db.readableDatabase.rawQuery(
            "SELECT id, address, deleted_at FROM conversations ORDER BY id", null
        ).use { c -> while (c.moveToNext()) convos.add(Triple(c.getLong(0), c.getString(1), c.getInt(2))) }
        val canon = convos.map { canonical(it.second) }
        val primary = HashMap<Long, Long>()
        for (i in convos.indices) {
            val a = convos[i]
            if (a.third != 0) { primary[a.first] = a.first; continue }
            var base = a.first
            for (j in 0 until i) {
                val b = convos[j]
                if (b.third != 0 || primary[b.first] != b.first) continue
                val ca = canon[i]
                val same = samePerson(b.second, a.second) ||
                    (ca.isNotEmpty() && ca == canon[j])
                if (same) { base = primary.getValue(b.first); break }
            }
            primary[a.first] = base
        }
        val toMerge = primary.mapNotNull { (id, p) -> if (id == p) null else id to p }
        if (toMerge.isEmpty()) return

        db.writableDatabase.beginTransaction()
        try {
            for ((id, p) in toMerge) {
                val pDraft: String
                val pArchived: Int
                db.readableDatabase.rawQuery(
                    "SELECT draft, archived FROM conversations WHERE id=?", arrayOf(p.toString())
                ).use { c ->
                    if (c.moveToFirst()) { pDraft = c.getString(0) ?: ""; pArchived = c.getInt(1) }
                    else continue
                }
                val sDraft: String
                val sArchived: Int
                db.readableDatabase.rawQuery(
                    "SELECT draft, archived FROM conversations WHERE id=?", arrayOf(id.toString())
                ).use { c ->
                    if (c.moveToFirst()) { sDraft = c.getString(0) ?: ""; sArchived = c.getInt(1) }
                    else continue
                }
                db.writableDatabase.execSQL(
                    "UPDATE conversations SET draft=?, archived=? WHERE id=?",
                    arrayOf<Any?>(if (pDraft.isEmpty()) sDraft else pDraft, if (pArchived == 1 || sArchived == 1) 1 else 0, p.toString())
                )
                db.writableDatabase.execSQL(
                    "UPDATE messages SET conversation_id=? WHERE conversation_id=?",
                    arrayOf(p.toString(), id.toString())
                )
                db.writableDatabase.execSQL(
                    """UPDATE conversation_notifications SET conversation_id=?
                       WHERE conversation_id=? AND NOT EXISTS(
                         SELECT 1 FROM conversation_notifications WHERE conversation_id=?)""",
                    arrayOf(p.toString(), id.toString(), p.toString())
                )
                db.writableDatabase.execSQL(
                    "DELETE FROM conversations WHERE id=?", arrayOf(id.toString())
                )
            }
            db.writableDatabase.setTransactionSuccessful()
        } finally {
            db.writableDatabase.endTransaction()
        }
        refreshConversationSnippets()
        notifyChanged()
    }

    @Volatile private var migrationStarted = false

    /** One-shot (per install) migration to canonical E.164 addresses:
     *  1. rewrites each phone-number conversation's address to its E.164 form,
     *  2. folds conversations whose spellings normalize to the same person,
     *  3. populates the participants table (display form, country, SIM),
     *  4. renames fallback names that still hold the old raw address.
     *  Re-runs its display pass when the device region changes (SIM/locale).
     *  Safe to call repeatedly; actual work happens once. */
    fun migrateParticipants() {
        if (migrationStarted) return
        migrationStarted = true
        runOnIo { runParticipantMigration() }
    }

    /** Imports bring in fresh (possibly raw) conversations; force a re-scan.
     *  Cheap in steady state: every address is already E.164, so the pass
     *  reduces to idempotent participant upserts. */
    fun reMigrateParticipants() {
        settings.participantsMigrated = false
        migrationStarted = true
        runOnIo { runParticipantMigration() }
    }

    private fun runParticipantMigration() {
        val region = PhoneNumberUtils.region()
        val firstRun = !settings.participantsMigrated
        val regionChanged = settings.phoneRegion.isNotBlank() && settings.phoneRegion != region

        if (!firstRun && !regionChanged) { settings.phoneRegion = region; return }

        if (regionChanged) {
            db.readableDatabase.rawQuery(
                "SELECT normalized_destination FROM participants", null
            ).use { c ->
                var updated = 0
                while (c.moveToNext()) {
                    val e164 = c.getString(0)
                    db.writableDatabase.execSQL(
                        "UPDATE participants SET display_destination=? WHERE normalized_destination=?",
                        arrayOf(PhoneNumberUtils.displayFor(e164, region), e164)
                    )
                    updated++
                }
            }
        }

        if (firstRun) {
            val convos = mutableListOf<Triple<Long, String, String>>()
            db.readableDatabase.rawQuery(
                "SELECT id, address, name FROM conversations", null
            ).use { c ->
                while (c.moveToNext()) {
                    convos.add(Triple(c.getLong(0), c.getString(1), c.getString(2)))
                }
            }
            // Group by canonical E.164; a group with >1 row means the same
            // person split across address spellings (issue #183, generalized).
            // Addresses that don't resolve to a number (alphanumeric IDs,
            // ambiguous locals) are left untouched — samePerson still
            // dedupes them at merge time.
            val byE164 = LinkedHashMap<String, MutableList<Triple<Long, String, String>>>()
            for (convo in convos) {
                val e164 = PhoneNumberUtils.toE164(convo.second, region) ?: continue
                byE164.getOrPut(e164) { mutableListOf() }.add(convo)
            }

            db.writableDatabase.beginTransaction()
            try {
                for ((e164, group) in byE164) {
                    // Primary: an already-canonical row, else the lowest id.
                    val primary = group.firstOrNull { it.second == e164 }
                        ?: group.minByOrNull { it.first }!!
                    for ((id, addr, _) in group) {
                        if (id == primary.first) {
                            if (addr != e164) {
                                db.writableDatabase.execSQL(
                                    """UPDATE conversations
                                       SET address=?, name=CASE WHEN name=? THEN ? ELSE name END
                                       WHERE id=?""",
                                    arrayOf(e164, addr, e164, id.toString())
                                )
                            }
                        } else {
                            db.writableDatabase.execSQL(
                                "UPDATE messages SET conversation_id=? WHERE conversation_id=?",
                                arrayOf(primary.first.toString(), id.toString())
                            )
                            db.writableDatabase.execSQL(
                                """UPDATE conversation_notifications SET conversation_id=?
                                   WHERE conversation_id=? AND NOT EXISTS(
                                     SELECT 1 FROM conversation_notifications WHERE conversation_id=?)""",
                                arrayOf(primary.first.toString(), id.toString(), primary.first.toString())
                            )
                            db.writableDatabase.execSQL(
                                "DELETE FROM conversations WHERE id=?", arrayOf(id.toString())
                            )
                        }
                    }
                    upsertParticipant(db.writableDatabase, e164)
                }
                // Blocked numbers keep their stored spelling; normalize + dedupe.
                db.readableDatabase.rawQuery(
                    "SELECT id, number FROM blocked_numbers", null
                ).use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val num = c.getString(1)
                        val e164 = PhoneNumberUtils.toE164(num, region) ?: continue
                        if (e164 != num) {
                            val other = db.writableDatabase.rawQuery(
                                "SELECT id FROM blocked_numbers WHERE number=?", arrayOf(e164)
                            ).use { q -> if (q.moveToFirst()) q.getLong(0) else -1L }
                            if (other != -1L && other != id) {
                                db.writableDatabase.execSQL(
                                    "DELETE FROM blocked_numbers WHERE id=?", arrayOf(id.toString())
                                )
                            } else {
                                db.writableDatabase.execSQL(
                                    "UPDATE blocked_numbers SET number=? WHERE id=?",
                                    arrayOf(e164, id.toString())
                                )
                            }
                        }
                    }
                }
                db.writableDatabase.setTransactionSuccessful()
            } catch (e: Exception) {
                throw e
            } finally {
                db.writableDatabase.endTransaction()
            }
            settings.participantsMigrated = true
            refreshConversationSnippets()
            notifyChanged()
        }
        settings.phoneRegion = region
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
                       WHERE conversation_id=? AND transport='sms' AND media_type='text' AND sys_id=0 AND body=? AND is_me=1
                       ORDER BY timestamp DESC LIMIT 1)""",
                    arrayOf(sysId.toString(), convoId.toString(), body)
                )
            }
        } catch (_: Exception) {
        }
    }

    /** After a backup import, re-populates the system SMS provider so the rest of
     *  the phone (the phone's own messaging app, other SMS tools) mirrors the
     *  restored history. Best-effort: only possible while this app is the default
     *  handler; rows whose sys_id still exists in the provider are skipped, and
     *  freshly inserted rows get their new provider id written back locally so the
     *  next system sync does not duplicate them.
     *
     *  Only plain SMS is mirrored. Writing MMS into `content://mms` means building
     *  a multipart, uploading each part, then setting the message box, and doing
     *  that wrong leaves malformed entries in the user's system store -- so an
     *  imported MMS stays in this app only, which is deliberate rather than
     *  forgotten.
     *
     *  The existence check for already-mirrored rows is one query per row, which
     *  is a binder round trip each. For a 50k import that dominates the run, so
     *  the set of live provider ids is read once and membership tested in memory.
     */
    fun pushLocalMessagesToProvider() {
        try {
            val resolver = context.contentResolver
            val providerUri = android.provider.Telephony.Sms.CONTENT_URI

            // One read of the live ids, instead of a per-row existence query.
            val live = HashSet<Long>()
            runCatching {
                resolver.query(
                    providerUri, arrayOf(android.provider.Telephony.Sms._ID),
                    null, null, null
                )?.use { c ->
                    while (c.moveToNext()) live.add(c.getLong(0))
                }
            }.onFailure {
                // Without a readable provider nothing can be written anyway, and
                // a half-mirrored history is worse than none.
                Log.w("RepoMirror", "provider not readable, not mirroring: ${it.message}")
                return
            }

            val linked = ArrayList<Pair<Long, Long>>()
            var attempted = 0
            db.readableDatabase.rawQuery(
                "SELECT m.id, c.address, m.body, m.timestamp, m.is_me, m.status, m.sub_id, m.sys_id " +
                    "FROM messages m JOIN conversations c ON c.id = m.conversation_id " +
                    "WHERE m.deleted_at=0 AND m.transport='sms' AND m.media_type='text' AND m.media_uri=''",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val localId = c.getLong(0)
                    val existingSysId = c.getLong(7)
                    if (existingSysId > 0 && live.contains(existingSysId)) continue
                    val address = c.getString(1) ?: continue
                    val body = c.getString(2) ?: continue
                    val isMe = c.getInt(4) == 1
                    attempted++
                    val cv = ContentValues().apply {
                        put(android.provider.Telephony.Sms.ADDRESS, address)
                        put(android.provider.Telephony.Sms.BODY, body)
                        put(android.provider.Telephony.Sms.DATE, c.getLong(3))
                        put(android.provider.Telephony.Sms.READ, 1)
                        put(android.provider.Telephony.Sms.SEEN, 1)
                        put(
                            android.provider.Telephony.Sms.TYPE,
                            if (isMe) android.provider.Telephony.Sms.MESSAGE_TYPE_SENT
                            else android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX
                        )
                        val pStatus = when (c.getString(5)) {
                            "failed" -> android.provider.Telephony.Sms.STATUS_FAILED
                            "sent", "delivered" -> android.provider.Telephony.Sms.STATUS_COMPLETE
                            else -> 0
                        }
                        put(android.provider.Telephony.Sms.STATUS, pStatus)
                        val subId = c.getInt(6)
                        if (subId > 0) put(android.provider.Telephony.Sms.SUBSCRIPTION_ID, subId)
                    }
                    val sysId = resolver.insert(providerUri, cv)?.lastPathSegment?.toLongOrNull() ?: -1L
                    if (sysId > 0) {
                        live.add(sysId)
                        linked.add(localId to sysId)
                    }
                    // Written back in batches: one transaction per few hundred
                    // rather than one per row, because a 50k import otherwise
                    // spends most of its time committing.
                    if (linked.size >= MIRROR_FLUSH_EVERY) {
                        writeBackSysIds(linked)
                        linked.clear()
                    }
                }
            }
            writeBackSysIds(linked)
            Log.i("RepoMirror", "mirrored $attempted message(s) into the system SMS store")
        } catch (e: Exception) {
            Log.w("RepoMirror", "mirror failed: ${e.message}")
        }
    }

    /** Writes the new provider ids back so a later sync does not re-add the rows. */
    private fun writeBackSysIds(linked: List<Pair<Long, Long>>) {
        if (linked.isEmpty()) return
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            for ((localId, sysId) in linked) {
                database.execSQL(
                    "UPDATE messages SET sys_id=? WHERE id=?",
                    arrayOf(sysId.toString(), localId.toString())
                )
            }
            database.setTransactionSuccessful()
        } catch (e: SecurityException) {
            // Not the default SMS handler: writing to the provider is not
            // permitted, and the local history is unaffected either way.
            Log.w("RepoMirror", "mirror skipped (not the default SMS app): ${e.message}")
        } catch (e: Exception) {
            Log.w("RepoMirror", "mirror write-back failed: ${e.message}")
        } finally {
            database.endTransaction()
        }
    }

    companion object {
        /**
         * A provider wipe emits one notification per deleted row, so the resync
         * is debounced rather than run per notification.
         */
        const val PROVIDER_RESYNC_DEBOUNCE_MS = 2_000L

        /** Provider ids written back per transaction while mirroring a backup. */
        const val MIRROR_FLUSH_EVERY = 500

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
