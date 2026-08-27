package com.anindra.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.anindra.messages.MessagesApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuickReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        val from = intent.getStringExtra(EXTRA_FROM) ?: address
        val replyText = extractReplyText(intent) ?: return
        // NotificationHelper passes the exact id it posted under; the hashCode
        // fallback covers stale intents from before the id scheme changed
        val notificationId = intent.getIntExtra(EXTRA_NOTIF_ID, from.hashCode())

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MessagesApplication
                val repo = app.repository

                val convoId = repo.getOrCreateConversationBlocking(address)
                val subId = repo.settings.simSubscriptionId
                val message = repo.sendText(convoId, replyText, subId)

                if (message != null) {
                    try {
                        NotificationManagerCompat.from(context).cancel(notificationId)
                    } catch (_: SecurityException) {
                    }
                    SmsSender.send(context, message.id, address, replyText, subId)
                    if (android.provider.Telephony.Sms.getDefaultSmsPackage(context) == context.packageName) {
                        repo.writeSentToSystem(address, replyText, subId)
                    }
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun extractReplyText(intent: Intent): String? {
        val results = RemoteInput.getResultsFromIntent(intent)
        return results?.getCharSequence("quick_reply")?.toString()
    }

    companion object {
        const val ACTION_REPLY = "com.anindra.messages.QUICK_REPLY"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_FROM = "from"
        const val EXTRA_NOTIF_ID = "notif_id"

        fun createReplyIntent(
            context: Context,
            address: String,
            from: String
        ): Intent {
            return Intent(ACTION_REPLY).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_FROM, from)
            }
        }
    }
}
