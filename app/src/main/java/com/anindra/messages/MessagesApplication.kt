package com.anindra.messages

import android.app.Application
import com.anindra.messages.data.Repository

class MessagesApplication : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
        repository.purgeOldTrashSuspend()
        repository.syncFromSystem()
    }
}
