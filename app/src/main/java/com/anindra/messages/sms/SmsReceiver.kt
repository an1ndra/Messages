package com.anindra.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.anindra.messages.data.Repository

/** Stores incoming SMS into the local database and posts a notification. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION &&
            intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val repo = (context.applicationContext as com.anindra.messages.MessagesApplication).repository

        for (msg in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            val address = msg.originatingAddress ?: continue
            val body = msg.messageBody ?: continue
            repo.receiveMessage(address, body)
            if (Telephony.Sms.getDefaultSmsPackage(context) == context.packageName) {
                try {
                    val values = android.content.ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address)
                        put(Telephony.Sms.BODY, body)
                        put(Telephony.Sms.DATE, msg.timestampMillis)
                        put(Telephony.Sms.READ, 0)
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    }
                    context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                } catch (_: Exception) {
                }
            }
            NotificationHelper.show(context, address, body)
        }
    }
}
