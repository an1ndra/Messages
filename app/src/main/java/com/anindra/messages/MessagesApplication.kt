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

    /** Whether this build can open the database on the phone. */
    var database: com.anindra.messages.data.DatabaseCompatibility.State =
        com.anindra.messages.data.DatabaseCompatibility.State(0, 0)
        private set

    override fun onCreate() {
        super.onCreate()
        com.anindra.messages.crash.CrashReporter.install(this)
        PhoneNumberUtils.init(this)
        // Nothing may touch the database before this: a database written by a
        // newer build throws "Can't downgrade" from this very worker, which took
        // the whole process down before any screen could explain it. The
        // activity shows the explanation instead (IncompatibleDatabaseScreen).
        database = com.anindra.messages.data.DatabaseCompatibility.check(this)
        if (database.tooNew) {
            // A database written by a newer build cannot be downgraded: opening
            // it throws and takes the process down before any screen exists.
            // Park it beside the live file and start this build on a fresh one,
            // so the app opens and can say what happened.
            com.anindra.messages.data.DatabaseCompatibility.setAside(this, database)
            database = com.anindra.messages.data.DatabaseCompatibility.State(
                0,
                com.anindra.messages.data.DB_VERSION
            )
        }
        repository = Repository(this)
        // Widgets render outside the app, so nothing there subscribes to the
        // repository's flows: a change has to be pushed at them.
        repository.onChanged = { com.anindra.messages.widget.WidgetRefresh.request(this) }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            repository.settings.revision.collect {
                com.anindra.messages.widget.WidgetRefresh.request(this@MessagesApplication)
            }
        }
        // An unlocked app-lock session ends with the screen (#354 follow-up):
        // the phone is out of the owner's hands again, which is the same moment
        // the device lock re-arms.
        registerReceiver(
            object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                    if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                        LockSession.clear()
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
            // The three category channels exist from the first launch, so the
            // app's notification settings are never empty on a fresh install.
            // No per-chat channel is created here — those are opt-in.
            com.anindra.messages.sms.NotificationHelper
                .ensureChannels(this@MessagesApplication, recoverDemoted = false)
            // Chats no longer get a channel just for existing (#339 follow-up).
            // This only re-titles the ones that have one — after a restore or an
            // import the names may have changed — and drops those of chats that
            // are gone.
            com.anindra.messages.sms.NotificationHelper.syncNotificationChannels(this@MessagesApplication)
            // The nudge's schedule has to follow its switch, and an app start is
            // also the moment a nudge that became due while the app was closed
            // gets posted (#484).
            com.anindra.messages.data.ReplyNudgeScheduler.apply(
                this@MessagesApplication, s.replyNudgeEnabled
            )
            if (s.replyNudgeEnabled) {
                com.anindra.messages.data.ReplyNudgeScheduler.checkNow(this@MessagesApplication)
            }
            // A queued message's countdown card outlives neither a restart of
            // the app nor a killed shade entry, so the pending rows are
            // reconciled on every start (#475).
            com.anindra.messages.sms.ScheduledNotifications.sync(this@MessagesApplication)
        }
    }
}
