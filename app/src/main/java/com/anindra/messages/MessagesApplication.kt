package com.anindra.messages

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.content.Intent
import com.anindra.messages.data.BackupHealth
import com.anindra.messages.data.LockSession
import com.anindra.messages.data.PhoneNumberUtils
import com.anindra.messages.data.PeriodicBackupScheduler
import com.anindra.messages.data.Repository
import com.anindra.messages.data.RetentionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MessagesApplication : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        com.anindra.messages.crash.CrashReporter.install(this)
        PhoneNumberUtils.init(this)
        repository = Repository(this)
        // An unlocked app-lock session ends with the screen (#354 follow-up):
        // the phone is out of the owner's hands again, which is the same moment
        // the device lock re-arms.
        registerReceiver(
            object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                    if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                        LockSession.lock()
                        android.util.Log.d("LockSession", "unlocked=false reason=screen_off")
                    }
                }
            },
            android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)
        )
        // Repair a restore that died mid-swap before anything opens the database.
        // Synchronous and before the first Activity, so the recovery cannot race
        // a reader that is about to open (and recreate) the live file.
        repository.recoverInterruptedImport()
        val startupSettings = repository.settings
        PeriodicBackupScheduler.apply(
            this,
            startupSettings.periodicBackupEnabled,
            startupSettings.periodicBackupInterval
        )
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.migrateParticipants()
            val s = repository.settings
            if (BackupHealth.isRetryDue(s.periodicBackupEnabled, s.lastBackupAt, s.lastBackupOk)) {
                PeriodicBackupScheduler.enqueueRetry(this@MessagesApplication)
            }
            repository.purgeRetainedSuspend(
                buckets = RetentionPolicy.activeBuckets(
                    enabled = s.retentionEnabled,
                    trash = s.retentionTrash,
                    keywordMessages = s.retentionKeywordMessages,
                    blockedSenders = s.retentionBlockedSenders
                ),
                trashDays = s.retentionTrashDays,
                spamDays = s.retentionSpamDays
            )
            // skip until SMS access is granted; MainActivity re-imports then
            if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                repository.syncFromSystem()
                // Watch for changes made by other apps -- a wipe in SMS Import /
                // Export has to be noticed without waiting for a restart.
                repository.observeProviderChanges()
                // Contacts too, or a rename made in the phone book keeps the old
                // name until a resume clears the refresh gate (#317).
                repository.observeContactChanges()
            }
            // Every conversation owns a notification channel (#339), and the
            // import above is where a fresh install's conversations come from —
            // running this before it left the whole history without channels
            // until each chat's first new message (#339 follow-up).
            com.anindra.messages.sms.NotificationHelper.syncConversationChannels(this@MessagesApplication)
        }
    }
}
