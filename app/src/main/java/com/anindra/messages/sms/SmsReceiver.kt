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
            // As default handler the framework expects US to persist the SMS
            // into the system provider; check both sources of truth since
            // getDefaultSmsPackage can lag behind an updated role.
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            val isDefaultHandler = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName ||
                roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) == true
            if (isDefaultHandler) {
                try {
                    val values = android.content.ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address)
                        put(Telephony.Sms.BODY, body)
                        put(Telephony.Sms.DATE, msg.timestampMillis)
                        put(Telephony.Sms.READ, 0)
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    }
                    context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                } catch (e: Exception) {
                    android.util.Log.w("SmsReceiver", "system inbox write-back failed", e)
                }
            }
            NotificationHelper.show(context, address, body)
        }
    }
}
