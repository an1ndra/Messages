package com.anindra.messages.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.anindra.messages.MessagesApplication
import com.anindra.messages.data.Repository
import com.anindra.messages.mms.transport.SystemMmsTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Confirms SMS/MMS delivery results and updates the stored message row. */
class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        val action = intent.action
        // getResultCode() is only valid on the receiver thread; snapshot before goAsync().
        val resultCodeSnapshot = resultCode
        MmsTrace.i(
            "MmsSend",
            "receiver fired action=$action messageId=$messageId code=$resultCodeSnapshot " +
                "trId=${intent.getStringExtra(SystemMmsTransport.EXTRA_TRANSACTION_ID)}"
        )

        val app = context.applicationContext as MessagesApplication
        val repo = app.repository
        val pending = goAsync()
        val wakeLock = ReceiverWakeLock.acquire(context, "sms-status")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when {
                    action == SystemMmsTransport.ACTION_SEND_SENT -> mms(
                        repo, context, intent, resultCodeSnapshot
                    )
                    resultCodeSnapshot != Activity.RESULT_OK -> fail(repo, context, messageId)
                    action == ACTION_SMS_DELIVERED ->
                        repo.markMessageStatusSuspend(messageId, "delivered")
                    else -> sent(repo, messageId)
                }
            } finally {
                wakeLock.safeRelease()
                pending.finish()
            }
        }
    }

    /**
     * MMS result: move the provider outbox row to Sent/Failed and drop the
     * composed PDU file. The app row is updated by status alone — unlike SMS an
     * MMS must never be mirrored into the SMS sent box (#91).
     */
    private suspend fun mms(
        repo: Repository,
        context: Context,
        intent: Intent,
        resultCode: Int
    ) {
        val ok = resultCode == Activity.RESULT_OK
        val transactionId = intent.getStringExtra(SystemMmsTransport.EXTRA_TRANSACTION_ID)
        val httpStatus = intent.getIntExtra(android.telephony.SmsManager.EXTRA_MMS_HTTP_STATUS, 0)
        MmsTrace.i("MmsSend", "MMS send finished: code=$resultCode httpStatus=$httpStatus trId=$transactionId")
        // The location is the PDU file's name inside the cache root; only the
        // name is used, so an extra cannot point outside it.
        intent.getStringExtra(SystemMmsTransport.EXTRA_LOCATION)
            ?.let { File(context.cacheDir, File(it).name).delete() }
        if (transactionId == null) {
            MmsTrace.w("MmsSend", "MMS result carried no transaction id; row not settled")
            return
        }
        MmsPendingSends.rowOf(context, transactionId)?.let { row ->
            runCatching {
                context.contentResolver.update(
                    row,
                    ContentValues().apply {
                        put(
                            Telephony.Mms.MESSAGE_BOX,
                            if (ok) Telephony.Mms.MESSAGE_BOX_SENT
                            else Telephony.Mms.MESSAGE_BOX_FAILED
                        )
                    }, null, null
                )
            }.onFailure { MmsTrace.w("MmsSend", "could not move provider row to its final box", it) }
        } ?: MmsTrace.w("MmsSend", "no provider row for trId=$transactionId")
        val messageId = MmsPendingSends.take(context, transactionId) ?: return
        MmsTrace.i("MmsSend", "MMS result applied id=$messageId ok=$ok")
        if (ok) repo.markMessageStatusSuspend(messageId, "sent")
        else fail(repo, context, messageId)
    }

    private suspend fun sent(repo: Repository, messageId: Long) {
        // Fetch the row + parent conversation once, then run the two writes and
        // (if applicable) the system provider write inside a single coroutine.
        // Previously each step was its own suspending hop, which could hold
        // the PendingResult across multiple executor round-trips and exceed
        // the ~10s goAsync() timeout, leaving rows stuck in "sending".
        repo.markMessageStatusSuspend(messageId, "sent")
        val msg = repo.messageByIdSuspend(messageId) ?: return
        if (msg.mediaType == "text") {
            val convo = repo.conversationByIdSuspend(msg.conversationId) ?: return
            repo.writeSentToSystem(convo.address, msg.body, msg.subId)
        }
    }

    private suspend fun fail(repo: Repository, context: Context, messageId: Long) {
        repo.markMessageStatusSuspend(messageId, "failed")
        val msg = repo.messageByIdSuspend(messageId) ?: return
        val convo = repo.conversationByIdSuspend(msg.conversationId) ?: return
        NotificationHelper.showSendFailed(context, convo.address)
    }

    companion object {
        const val ACTION_SMS_SENT = "com.anindra.messages.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.anindra.messages.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "mid"
    }
}
