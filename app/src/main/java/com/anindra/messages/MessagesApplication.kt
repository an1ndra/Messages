package com.anindra.messages

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import com.anindra.messages.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MessagesApplication : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.purgeOldTrashSuspend()
            // skip until SMS access is granted; MainActivity re-imports then
            if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                repository.syncFromSystem()
            }
        }
    }
}
