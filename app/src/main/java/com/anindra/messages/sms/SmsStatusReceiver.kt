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

        val repo = (context.applicationContext as MessagesApplication).repository
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when {
                    resultCodeSnapshot != Activity.RESULT_OK -> fail(repo, context, messageId)
                    action == ACTION_SMS_DELIVERED ->
                        repo.markMessageStatusSuspend(messageId, "delivered")
                    else -> sent(repo, messageId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun sent(repo: Repository, messageId: Long) {
        repo.markMessageStatusSuspend(messageId, "sent")
        val msg = repo.messageByIdSuspend(messageId) ?: return
        if (msg.mediaType == "text") {
            val convo = repo.conversationByIdSuspend(msg.conversationId) ?: return
            repo.writeSentToSystem(convo.address, msg.body)
        }
    }

    private fun fail(repo: Repository, context: Context, messageId: Long) {
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
