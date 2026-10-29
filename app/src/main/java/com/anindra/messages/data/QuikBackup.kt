package com.anindra.messages.data

import org.json.JSONObject

/**
 * Reader for the backup QUIK (formerly QKSMS) writes from *Backup and restore*.
 *
 * The file is a single JSON object, not an array:
 *
 * ```json
 * { "messageCount": 101,
 *   "messages": [ { "type": 1, "address": "+1555444329", "date": 1790279506001,
 *                   "dateSent": 0, "read": true, "status": 0, "body": "…",
 *                   "protocol": 0, "locked": false, "subId": -1 } ] }
 * ```
 *
 * `type` is `Telephony.Sms.TYPE` (1 inbox, 2 sent, …), `read` is a **boolean**
 * where the sms-ie records our importer reads use 0/1, and `serviceCenter` is
 * omitted entirely rather than written as null. Everything else maps straight
 * onto the sms-ie record shape, so the import is a conversion into that
 * format and then the existing import path — one importer, not two.
 *
 * QUIK itself says "Currently, only SMS is supported by Backup and Restore",
 * so there is nothing else in the file to carry over: no MMS, no attachments,
 * no settings, blocked numbers or scheduled messages. This app imports
 * everything the file actually holds, including the flags sms-ie would
 * otherwise lose (read state, sent timestamps, locked).
 */
object QuikBackup {

    /** Format tag recorded in the transfer log. */
    const val FORMAT = "quik"

    /** Cheap shape test: a `messages` array plus the counter QUIK always writes. */
    fun looksLikeQuik(text: String): Boolean {
        val head = text.take(4096)
        return head.contains("\"messages\"") && head.contains("\"messageCount\"")
    }

    /**
     * Converts a QUIK backup into sms-ie v2 records, one JSON object per line.
     *
     * Returns the number of records written. Records that cannot be read are
     * skipped rather than aborting the file, the way every other importer here
     * behaves: one damaged message should cost that message.
     */
    fun toRecords(objectJson: JSONObject, write: (String) -> Unit): Int {
        val messages = objectJson.optJSONArray("messages") ?: return 0
        var written = 0
        for (i in 0 until messages.length()) {
            val message = messages.optJSONObject(i) ?: continue
            val record = toRecord(message) ?: continue
            write(record.toString())
            written++
        }
        return written
    }

    /** One QUIK message as an sms-ie-shaped record, or null when unusable. */
    fun toRecord(message: JSONObject): JSONObject? {
        val address = message.optString("address").trim()
        if (address.isEmpty()) return null
        val date = message.optLong("date", 0L)
        if (date <= 0L) return null
        return JSONObject().apply {
            put("address", address)
            put("body", message.optString("body"))
            put("date", date)
            // QUIK stores read as a boolean; sms-ie uses 0/1.
            put("read", if (message.optBoolean("read", false)) 1 else 0)
            put("type", message.optInt("type", Type.INBOX))
            message.optLong("dateSent", 0L).takeIf { it > 0L }?.let { put("date_sent", it) }
            if (message.optBoolean("locked", false)) put("locked", 1)
        }
    }

    /** `Telephony.Sms.TYPE` values QUIK writes. */
    object Type {
        const val INBOX = 1
        const val SENT = 2
    }
}
