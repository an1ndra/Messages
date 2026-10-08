package com.anindra.messages.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony

/**
 * Remembers which app message an outgoing MMS belongs to.
 *
 * The platform reports a send through a broadcast that carries the MMS
 * transaction id (`tr_id`) and nothing of the app's own. The `:mms` stack
 * persists the outbox row before it hands the PDU over, so the link is written
 * at that point and read back when the result arrives. It is kept on disk
 * because the process can be gone by the time the radio answers.
 */
internal object MmsPendingSends {
    private const val PREFS = "mms_pending_sends"

    fun remember(context: Context, transactionId: String, messageId: Long) {
        // commit(), not apply(): the entry has to be on disk before the PDU is
        // handed to the platform, or a fast result could arrive without it.
        prefs(context).edit().putLong(transactionId, messageId).commit()
    }

    /** The app message for [transactionId], removed so it is used once. */
    fun take(context: Context, transactionId: String): Long? {
        val prefs = prefs(context)
        if (!prefs.contains(transactionId)) return null
        val id = prefs.getLong(transactionId, -1L)
        prefs.edit().remove(transactionId).apply()
        return id.takeIf { it > 0 }
    }

    /** The `tr_id` the provider holds for the row at [row]. */
    fun transactionIdOf(context: Context, row: Uri): String? = runCatching {
        context.contentResolver.query(
            row, arrayOf(Telephony.Mms.TRANSACTION_ID), null, null, null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** The provider row carrying [transactionId], newest first. */
    fun rowOf(context: Context, transactionId: String): Uri? = runCatching {
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID),
            "${Telephony.Mms.TRANSACTION_ID} = ?",
            arrayOf(transactionId),
            "${Telephony.Mms.DATE} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, cursor.getLong(0).toString())
            } else {
                null
            }
        }
    }.getOrNull()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
