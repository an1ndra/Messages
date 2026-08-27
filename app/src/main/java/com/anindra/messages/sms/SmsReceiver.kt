package com.anindra.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.anindra.messages.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Stores incoming SMS into the local database and posts a notification. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION &&
            intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                processIncoming(context, intent)
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun processIncoming(context: Context, intent: Intent) {
        val repo = (context.applicationContext as com.anindra.messages.MessagesApplication).repository

        // As default handler the framework expects US to persist the SMS into the
        // system provider; check both sources of truth since getDefaultSmsPackage
        // can lag behind an updated role.
        val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
        val isDefaultHandler = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName ||
            roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) == true

        // A long SMS arrives as one broadcast holding every multipart PDU;
        // segments of the same sender must be joined or each shows up as its
        // own message + notification.
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            .filter { it.originatingAddress != null && !it.messageBody.isNullOrEmpty() }
        for ((address, parts) in msgs.groupBy { it.originatingAddress!! }) {
            val body = parts.joinToString("") { it.messageBody!! }

            var sysId = 0L
            if (isDefaultHandler) {
                try {
                    val values = android.content.ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address)
                        put(Telephony.Sms.BODY, body)
                        put(Telephony.Sms.DATE, parts.first().timestampMillis)
                        put(Telephony.Sms.READ, 0)
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    }
                    sysId = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                        ?.lastPathSegment?.toLongOrNull() ?: 0L
                } catch (_: Exception) {
                }
            }

            repo.receiveMessage(address, body, sysId)
            NotificationHelper.show(context, address, body)
        }
    }
}
