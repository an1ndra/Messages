package com.anindra.messages.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class TransferOperation { EXPORT, IMPORT }

/**
 * One attempt at moving messages in or out of the app.
 *
 * Failures and conflicts are recorded rather than swallowed. A backup that
 * silently drops half its records, or an export that fails on a revoked SD-card
 * permission, is indistinguishable from success once the toast is gone — so the
 * outcome, the reason, and the per-category conflict tallies all persist.
 */
data class TransferEntry(
    val timestamp: Long,
    val operation: TransferOperation,
    /** [BackupFormat] name for an own-backup import, `sms-ie`, or `none`. */
    val format: String,
    /** `merge`, `replace`, or `none` for an export. */
    val mode: String,
    val succeeded: Boolean,
    /** Failure reason, or a one-line summary of what the run did. */
    val detail: String,
    val added: Int = 0,
    val seen: Int = 0,
    val skipped: Int = 0,
    /** Attempts made; >1 means a retry is what produced this outcome. */
    val attempts: Int = 1,
    /** True when startup self-healing, not a fresh run, produced this entry. */
    val recovered: Boolean = false,
    /** Reason -> how many, e.g. `already present` to 12. */
    val conflicts: Map<String, Int> = emptyMap()
)

object TransferLog {
    const val MAX_ENTRIES = 20

    /** Newest last, so the screen renders the file in order without reversing. */
    fun append(existing: List<TransferEntry>, entry: TransferEntry): List<TransferEntry> =
        (existing + entry).takeLast(MAX_ENTRIES)

    fun encode(entries: List<TransferEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put(KEY_TIME, entry.timestamp)
                    put(KEY_OP, entry.operation.name)
                    put(KEY_FORMAT, entry.format)
                    put(KEY_MODE, entry.mode)
                    put(KEY_OK, entry.succeeded)
                    put(KEY_DETAIL, entry.detail)
                    put(KEY_ADDED, entry.added)
                    put(KEY_SEEN, entry.seen)
                    put(KEY_SKIPPED, entry.skipped)
                    put(KEY_ATTEMPTS, entry.attempts)
                    put(KEY_RECOVERED, entry.recovered)
                    put(KEY_CONFLICTS, JSONObject().apply {
                        entry.conflicts.forEach { (reason, count) -> put(reason, count) }
                    })
                }
            )
        }
        return array.toString()
    }

    fun decode(text: String): List<TransferEntry> {
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val out = ArrayList<TransferEntry>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val conflicts = obj.optJSONObject(KEY_CONFLICTS)
            out += TransferEntry(
                timestamp = obj.optLong(KEY_TIME),
                operation = runCatching {
                    TransferOperation.valueOf(obj.optString(KEY_OP))
                }.getOrDefault(TransferOperation.IMPORT),
                format = obj.optString(KEY_FORMAT),
                mode = obj.optString(KEY_MODE),
                succeeded = obj.optBoolean(KEY_OK),
                detail = obj.optString(KEY_DETAIL),
                added = obj.optInt(KEY_ADDED),
                seen = obj.optInt(KEY_SEEN),
                skipped = obj.optInt(KEY_SKIPPED),
                attempts = obj.optInt(KEY_ATTEMPTS, 1).coerceAtLeast(1),
                recovered = obj.optBoolean(KEY_RECOVERED, false),
                conflicts = buildMap {
                    conflicts?.keys()?.forEach { key -> put(key, conflicts.optInt(key)) }
                }
            )
        }
        return out
    }

    private const val KEY_TIME = "t"
    private const val KEY_OP = "op"
    private const val KEY_FORMAT = "fmt"
    private const val KEY_MODE = "mode"
    private const val KEY_OK = "ok"
    private const val KEY_DETAIL = "detail"
    private const val KEY_ADDED = "added"
    private const val KEY_SEEN = "seen"
    private const val KEY_SKIPPED = "skipped"
    private const val KEY_ATTEMPTS = "attempts"
    private const val KEY_RECOVERED = "recovered"
    private const val KEY_CONFLICTS = "conflicts"
}

object TransferLogStore {
    const val FILE_NAME = "transfer-log.json"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun append(context: Context, entry: TransferEntry) {
        runCatching {
            val file = file(context)
            val existing = if (file.exists()) {
                TransferLog.decode(file.readText())
            } else {
                emptyList()
            }
            file.writeText(TransferLog.encode(TransferLog.append(existing, entry)))
        }
    }

    fun read(context: Context): List<TransferEntry> = runCatching {
        val file = file(context)
        if (file.exists()) TransferLog.decode(file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}