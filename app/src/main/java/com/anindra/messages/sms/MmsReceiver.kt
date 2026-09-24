package com.anindra.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Receives the MMS notification-indication WAP push. The broadcast only says an
 * MMS exists; the actual transfer is [MmsDownloader]'s job.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        if (intent.type != MIME) return
        val uri = intent.data ?: return
        MmsDownloader.onWapPush(context, uri)
    }

    companion object {
        const val MIME = "application/vnd.wap.mms-message"
    }
}
