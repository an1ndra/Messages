package com.anindra.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anindra.messages.data.MmsSupport

/**
 * Receives the MMS notification-indication WAP push. The broadcast carries the
 * raw PDU in its "data" extra and no data URI, and the platform has already
 * filed an announced-but-empty provider row for it — so the pending-row sweep
 * in [MmsDownloader] covers the push, and every broadcast missed while the app
 * was not the default handler, without parsing anything.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!MmsSupport.isMmsWapPush(intent.action, intent.type)) return
        MmsDownloader.onWapPush(context)
    }
}
