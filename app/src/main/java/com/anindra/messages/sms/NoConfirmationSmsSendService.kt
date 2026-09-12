package com.anindra.messages.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

class NoConfirmationSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
