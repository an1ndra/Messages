package com.anindra.messages.sms

import android.app.IntentService
import android.content.Intent
import android.os.IBinder

class NoConfirmationSmsSendService : IntentService("NoConfirmationSmsSendService") {
    override fun onHandleIntent(intent: Intent?) {}
    override fun onBind(intent: Intent?): IBinder? = null
}
