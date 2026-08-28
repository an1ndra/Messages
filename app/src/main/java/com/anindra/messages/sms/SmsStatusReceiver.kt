package com.anindra.messages.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anindra.messages.MessagesApplication
import com.anindra.messages.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Confirms SMS/MMS delivery results and updates the stored message row. */
class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (messageId <= 0) return
        // getResultCode() is only valid on the receiver thread; snapshot before goAsync().
        val resultCodeSnapshot = resultCode
        val action = intent.action

        val app = context.applicationContext as MessagesApplication
        val repo = app.repository
        val pending = goAsync()
        val wakeLock = ReceiverWakeLock.acquire(context, "sms-status")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when {
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
        const val ACTION_MMS_SENT = "com.anindra.messages.MMS_SENT"
        const val EXTRA_MESSAGE_ID = "mid"
    }
}
