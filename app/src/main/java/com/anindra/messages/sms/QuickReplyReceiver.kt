package com.anindra.messages.sms

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.anindra.messages.MessagesApplication

class QuickReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        val from = intent.getStringExtra(EXTRA_FROM) ?: address
        val replyText = extractReplyText(intent) ?: return
        val notificationId = from.hashCode()

        val app = context.applicationContext as MessagesApplication
        val repo = app.repository

        val convoId = repo.getOrCreateConversationBlocking(address)
        val message = repo.sendText(convoId, replyText) ?: return

        Thread {
            val subId = repo.settings.simSubscriptionId
            SmsSender.send(context, message.id, address, replyText, subId)
            if (android.provider.Telephony.Sms.getDefaultSmsPackage(context) == context.packageName) {
                repo.writeSentToSystem(address, replyText)
            }
        }.start()

        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (_: SecurityException) {
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

        fun createReplyIntent(
            context: Context,
            address: String,
            from: String,
            requestCode: Int
        ): Intent {
            return Intent(ACTION_REPLY).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_FROM, from)
            }
        }
    }
}
