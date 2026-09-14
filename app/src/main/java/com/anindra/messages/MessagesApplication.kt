package com.anindra.messages

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import com.anindra.messages.data.PhoneNumberUtils
import com.anindra.messages.data.Repository
import com.anindra.messages.sms.ForegroundTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MessagesApplication : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        PhoneNumberUtils.init(this)
        ForegroundTracker.init(this)
        repository = Repository(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.migrateParticipants()
            repository.purgeOldTrashSuspend()
            // skip until SMS access is granted; MainActivity re-imports then
            if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                repository.syncFromSystem()
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        ForegroundTracker.setAppForeground(false)
        ForegroundTracker.setOpenConversation(null)
    }
}
