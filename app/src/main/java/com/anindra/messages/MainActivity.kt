package com.anindra.messages

import android.app.Activity
import android.app.Application
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.app.role.RoleManager
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationManagerCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anindra.messages.data.Conversation
import com.anindra.messages.ui.theme.Motion
import com.anindra.messages.ui.theme.motionTween
import com.anindra.messages.R
import androidx.compose.ui.res.stringResource
import com.anindra.messages.data.BlockedMessage
import com.anindra.messages.data.DownloadsStore
import com.anindra.messages.data.MmsSupport
import com.anindra.messages.data.TrashedMessage
import com.anindra.messages.data.Message
import com.anindra.messages.data.Repository
import com.anindra.messages.sms.NotificationHelper
import com.anindra.messages.data.ForwardPlan
import com.anindra.messages.sms.SmsSender
import com.anindra.messages.ui.ChatScreen
import com.anindra.messages.ui.ConversationsScreen
import com.anindra.messages.ui.AddPeopleScreen
import com.anindra.messages.ui.ContactDetailsScreen
import com.anindra.messages.ui.NotificationSettingsScreen
import com.anindra.messages.ui.AutoDeleteSettingsScreen
import com.anindra.messages.ui.LinkSettingsScreen
import com.anindra.messages.ui.InboxSettingsScreen
import com.anindra.messages.ui.ScheduledMessagesScreen
import com.anindra.messages.ui.NewChatScreen
import com.anindra.messages.ui.SettingsScreen
import com.anindra.messages.ui.AdvancedSettingsScreen
import com.anindra.messages.ui.AccessibilityScreen
import com.anindra.messages.ui.MmsSupportScreen
import com.anindra.messages.ui.SpamBlockedScreen
import com.anindra.messages.ui.TrashScreen
import com.anindra.messages.ui.isPhoneNumber
import com.anindra.messages.ui.theme.A11yOptions
import com.anindra.messages.ui.theme.MessagesTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import com.anindra.messages.crash.CrashReportFormatter
import com.anindra.messages.crash.CrashReporter
import com.anindra.messages.crash.CrashReportStore
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: Repository = (app as MessagesApplication).repository
    val settings = repo.settings
    // A throw escaping a fire-and-forget launch on a bare SupervisorJob reaches
    // the default uncaught handler and kills the process. Record it as a crash
    // report instead, so a failed background write never closes the app. (#281)
    private val scope = CoroutineScope(
        SupervisorJob() +
            CoroutineExceptionHandler { _, throwable ->
                runCatching {
                    CrashReportStore.save(
                        getApplication(),
                        CrashReportFormatter.format(
                            throwable,
                            CrashReporter.deviceInfo(),
                            CrashReporter.appInfo(getApplication()),
                            System.currentTimeMillis()
                        ),
                        System.currentTimeMillis()
                    )
                }
            } +
            Dispatchers.Main
    )

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }

    val contacts = kotlinx.coroutines.flow.MutableStateFlow<List<com.anindra.messages.ui.Contact>>(emptyList())

    val pendingCrashReports =
        androidx.compose.runtime.mutableStateOf<List<java.io.File>>(emptyList())

    init {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ctx = app.applicationContext
            val out = mutableListOf<com.anindra.messages.ui.Contact>()
            val enterpriseBase = android.provider.ContactsContract.Directory.ENTERPRISE_DEFAULT

            fun load(uri: android.net.Uri, withContactId: Boolean) {
                val projection = if (withContactId) arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                    "contact_id"
                ) else arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                ctx.contentResolver.query(
                    uri,
                    projection,
                    null, null,
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )?.use { c ->
                    val seen = mutableSetOf<String>()
                    while (c.moveToNext()) {
                        val name = c.getString(0) ?: continue
                        val num = c.getString(1) ?: continue
                        val work = withContactId && c.getLong(2) >= enterpriseBase
                        if (seen.add(num.filter { it.isDigit() })) {
                            out.add(com.anindra.messages.ui.Contact(name, num, work))
                        }
                    }
                }
            }

            try {
                // ENTERPRISE_CONTENT_URI is API 34+; referencing it on older
                // devices throws NoSuchFieldError (an Error), so guard by SDK
                // and fall back to the plain personal-profile URI. (#209)
                if (com.anindra.messages.data.EnterpriseContacts.isSupported(Build.VERSION.SDK_INT)) {
                    load(com.anindra.messages.data.EnterpriseContacts.phoneUri(), true)
                } else {
                    load(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI, false)
                }
            } catch (_: Throwable) {
                out.clear()
                try {
                    load(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI, false)
                } catch (_: Throwable) {
                }
            }
            contacts.value = out
        }
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            pendingCrashReports.value =
                com.anindra.messages.crash.CrashReporter.pending(app.applicationContext)
        }
    }

    val conversations: Flow<List<Conversation>> = repo.conversations()

    /** Emits true after the first system-SMS import of this process completes. */
    val initialSyncDone = repo.initialSyncDone

    /** Null = idle; 0..1 = fraction of pending system SMS imported. */
    val initialSyncProgress = repo.initialSyncProgress

    /** True once this process fully showed the list; suppresses skeleton flash on back-nav. */
    var hasLoadedOnce: Boolean = false

    // Observable theme state; SettingsScreen updates it via setTheme()
    var themeMode: String
        get() = _themeState.value
        set(value) { settings.themeMode = value; _themeState.value = value }

    fun setTheme(mode: String) { themeMode = mode }

    private val _themeState = androidx.compose.runtime.mutableStateOf(settings.themeMode)

    var fontFamily: String
        get() = _fontState.value
        set(value) { settings.fontFamily = value; _fontState.value = value }

    private val _fontState = androidx.compose.runtime.mutableStateOf(settings.fontFamily)

    // Observable accessibility-mode state; AccessibilityScreen updates it.
    val a11y: A11yOptions get() = _a11yState.value

    private val _a11yState = androidx.compose.runtime.mutableStateOf(readA11y())

    private fun readA11y() = A11yOptions(
        enabled = settings.a11yEnabled,
        fontScalePercent = settings.a11yFontScalePercent,
        bold = settings.a11yBold,
        highContrast = settings.a11yHighContrast,
        reduceMotion = settings.a11yReduceMotion,
        largeTouchTargets = settings.a11yLargeTouch
    )

    private fun refreshA11y() { _a11yState.value = readA11y() }

    var a11yEnabled: Boolean
        get() = _a11yState.value.enabled
        set(value) { settings.a11yEnabled = value; refreshA11y() }

    var a11yFontScalePercent: Int
        get() = _a11yState.value.fontScalePercent
        set(value) { settings.a11yFontScalePercent = value; refreshA11y() }

    var a11yBold: Boolean
        get() = _a11yState.value.bold
        set(value) { settings.a11yBold = value; refreshA11y() }

    var a11yHighContrast: Boolean
        get() = _a11yState.value.highContrast
        set(value) { settings.a11yHighContrast = value; refreshA11y() }

    var a11yReduceMotion: Boolean
        get() = _a11yState.value.reduceMotion
        set(value) { settings.a11yReduceMotion = value; refreshA11y() }

    var a11yLargeTouch: Boolean
        get() = _a11yState.value.largeTouchTargets
        set(value) { settings.a11yLargeTouch = value; refreshA11y() }

    fun addBlockedKeyword(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        settings.blockedKeywords = settings.blockedKeywords + kw
    }

    fun removeBlockedKeyword(keyword: String) {
        settings.blockedKeywords = settings.blockedKeywords - keyword
    }

    fun messages(conversationId: Long, limit: Int = Int.MAX_VALUE, offset: Int = 0): Flow<List<Message>> =
        repo.messages(conversationId, limit, offset)

    fun messageCount(conversationId: Long): Int = repo.messageCount(conversationId)

    fun messageCountFlow(conversationId: Long): Flow<Int> = repo.messageCountFlow(conversationId)

    fun syncFromSystem() = repo.syncFromSystem()

    fun requeryFromSystem() = repo.requeryFromSystem()

    fun conversationById(id: Long): Flow<Conversation?> =
        repo.conversationByIdFlow(id)

    fun markRead(id: Long) = scope.launch { repo.markReadSuspend(id) }

    fun markUnread(id: Long) = scope.launch { repo.setReadSuspend(id, read = false) }

    /** Restores a read state exactly, which is what an undo needs. */
    fun setRead(id: Long, read: Boolean) = scope.launch { repo.setReadSuspend(id, read) }

    /** Clears unread state, returning how many messages were unread. */
    suspend fun consumeUnread(id: Long): Int = withContext(Dispatchers.IO) {
        val n = repo.conversationByIdSuspend(id)?.unreadCount ?: 0
        if (n > 0) repo.markReadSuspend(id)
        n
    }

    fun deleteConversation(id: Long) = scope.launch {
        if (settings.permanentDeleteEnabled) repo.deleteConversationSuspend(id)
        else repo.trashConversationSuspend(id)
    }

    fun restoreFromTrash(id: Long) = scope.launch { repo.restoreFromTrashSuspend(id) }

    fun deleteForever(id: Long) = scope.launch { repo.deleteConversationSuspend(id) }

    fun emptyTrash() = scope.launch { repo.emptyTrashSuspend() }

    fun trashedConversations(): Flow<List<Conversation>> = repo.trashedConversations()

    fun trashedMessages(): Flow<List<TrashedMessage>> = repo.trashedMessages()

    fun deleteMessageForever(messageId: Long) =
        scope.launch { repo.deleteMessageForeverSuspend(messageId) }

    fun emptyMessageTrash() = scope.launch { repo.emptyMessageTrashSuspend() }

    fun blockedMessages(): Flow<List<BlockedMessage>> = repo.blockedMessages()

    fun deleteBlockedMessage(messageId: Long) {
        scope.launch(Dispatchers.IO) { repo.deleteBlockedMessage(messageId) }
    }

    fun restoreBlockedMessage(
        conversationId: Long,
        body: String,
        timestamp: Long,
        blockedReason: String
    ) = scope.launch(Dispatchers.IO) {
        repo.restoreBlockedMessage(conversationId, body, timestamp, blockedReason)
    }

    fun deleteBlockedConversation(conversationId: Long, address: String) =
        scope.launch(Dispatchers.IO) { repo.deleteBlockedConversation(conversationId, address) }

    fun deleteAllBlockedMessages() = scope.launch(Dispatchers.IO) { repo.deleteAllBlockedMessages() }

    fun returnBlockedMessageToChat(messageId: Long) =
        scope.launch(Dispatchers.IO) { repo.returnBlockedMessageToChat(messageId) }

    fun unblockAllNumbers() = scope.launch(Dispatchers.IO) { repo.unblockAllNumbers() }

    fun setArchived(id: Long, archived: Boolean) =
        scope.launch { repo.setArchivedSuspend(id, archived) }

    fun setLocked(messageId: Long, locked: Boolean) =
        scope.launch { repo.setLockedSuspend(messageId, locked) }

    fun deleteMessage(messageId: Long) =
        scope.launch { repo.deleteMessageSuspend(messageId) }

    fun restoreMessage(messageId: Long) =
        scope.launch { repo.restoreMessageSuspend(messageId) }

    fun markAllRead() = scope.launch { repo.markAllReadSuspend() }

    /** Applies FLAG_SECURE immediately so privacy mode toggles without restart. */
    fun setPrivacyMode(activity: Activity, enabled: Boolean) {
        settings.privacyModeEnabled = enabled
        val window = activity.window
        if (enabled) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun setReactions(messageId: Long, reactions: Map<String, Int>) =
        scope.launch { repo.setReactionsSuspend(messageId, reactions) }

    /** Everyone a conversation goes to, primary recipient first. */
    suspend fun conversationRecipients(conversationId: Long): List<String> =
        repo.conversationRecipients(conversationId)

    suspend fun isGroup(conversationId: Long): Boolean = repo.isGroup(conversationId)

    /**
     * Bumped whenever a conversation's membership changes, so screens showing
     * the participant list reload instead of keeping a stale count.
     */
    val membershipRevision = kotlinx.coroutines.flow.MutableStateFlow(0)

    /**
     * Adds people to a conversation, turning it into a group. [onDone] runs once
     * the write has landed, so the caller can navigate to the new group.
     */
    fun addParticipants(
        conversationId: Long,
        addresses: List<String>,
        onDone: () -> Unit = {}
    ) = scope.launch {
        repo.addParticipants(conversationId, addresses)
        membershipRevision.value++
        onDone()
    }

    /** Removes one person. The last remaining recipient cannot be removed. */
    fun removeParticipant(conversationId: Long, address: String) =
        scope.launch {
            repo.removeParticipant(conversationId, address)
            membershipRevision.value++
        }

    /** Contacts name for an address, or null when it is not in Contacts. */
    fun contactNameFor(address: String): String? = repo.contactNameFor(address)

    /** A group's own name, or blank for a 1:1 conversation. */
    fun conversationGroupTitle(conversationId: Long): String =
        repo.groupTitleBlocking(conversationId)

    /** Renames a group. Blank restores the default name. */
    fun setGroupTitle(conversationId: Long, title: String) =
        scope.launch {
            repo.setGroupTitle(conversationId, title)
            membershipRevision.value++
        }

    fun send(conversationId: Long, body: String, subId: Int = settings.simSubscriptionId) {
        scope.launch { dispatchText(conversationId, body, subId) }
    }

    /**
     * Store a message and hand it to the radio. Everything that puts a row on the
     * wire goes through here: a forwarded message that only got stored stayed
     * "Sending…" forever, because nothing was ever sent.
     */
    private suspend fun dispatchText(conversationId: Long, body: String, subId: Int) {
        val convo = repo.conversationByIdSuspend(conversationId) ?: return
        if (!isPhoneNumber(convo.address)) return
        val stored = repo.sendText(conversationId, body, subId) ?: return
        if (settings.soundsEnabled) NotificationHelper.playSentSound(getApplication())
        val recipients = repo.conversationRecipients(conversationId)
            .filter { isPhoneNumber(it) }
            .ifEmpty { listOf(convo.address) }
        // A group is one stored message sent once per recipient. The row is
        // marked failed only when every hand-off failed, since a partial
        // send is still a send.
        val results = recipients.map { address ->
            SmsSender.send(
                getApplication(), stored.id, address, body,
                subId, settings.deliveryReportsEnabled
            )
        }
        // accepted by framework; SmsStatusReceiver confirms sent/failed.
        if (results.isNotEmpty() && results.none { it }) {
            repo.markMessageStatusSuspend(stored.id, "failed")
            NotificationHelper.showSendFailed(getApplication(), convo.address)
        }
    }

    fun sendMediaMessage(conversationId: Long, uri: Uri) {
        scope.launch {
            val convo = repo.conversationByIdSuspend(conversationId) ?: return@launch
            if (!isPhoneNumber(convo.address)) return@launch
            val stored = repo.sendMedia(conversationId, "image", uri.toString()) ?: return@launch
            // A group MMS addresses every member in one PDU, unlike SMS which
            // is sent once per person.
            val recipients = repo.conversationRecipients(conversationId)
                .filter { isPhoneNumber(it) }
                .ifEmpty { listOf(convo.address) }
            val handedOff = SmsSender.sendMms(
                getApplication(), stored.id, recipients, uri, settings.simSubscriptionId,
                stored.body
            )
            if (!handedOff) {
                repo.markMessageStatusSuspend(stored.id, "failed")
                NotificationHelper.showSendFailed(getApplication(), convo.address)
            }
        }
    }

    /** Saves a message attachment (e.g. an MMS picture) to the gallery (#235).
     *  Reads the bytes through the resolver so provider-backed
     *  `content://mms/part/...` URIs and our own cache URIs both work. */
    fun saveMessageImage(messageId: Long) {
        scope.launch {
            val app = getApplication<Application>()
            val resolver = app.contentResolver
            val msg = repo.messageByIdSuspend(messageId)
            if (msg == null || msg.mediaUri.isBlank()) {
                Toast.makeText(app, R.string.chat_image_save_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val uri = Uri.parse(msg.mediaUri)
            val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            val mime = MmsSupport.mimeForSavedAttachment(
                msg.mediaUri, runCatching { resolver.getType(uri) }.getOrNull()
            )
            val convo = repo.conversationByIdSuspend(msg.conversationId)
            val name = MmsSupport.savedAttachmentName(
                convo?.name ?: convo?.address ?: "message", msg.timestamp, mime
            )
            val ok = bytes != null && bytes.isNotEmpty() &&
                DownloadsStore.writeImage(app, name, mime, bytes)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    app,
                    if (ok) R.string.chat_image_saved else R.string.chat_image_save_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun retryMessage(messageId: Long, simId: Int = settings.simSubscriptionId) {
        scope.launch {
            val msg = repo.messageByIdSuspend(messageId) ?: return@launch
            val convo = repo.conversationByIdSuspend(msg.conversationId) ?: return@launch
            if (!isPhoneNumber(convo.address)) return@launch
            repo.markMessageStatusSuspend(messageId, "sending")
            val recipients = repo.conversationRecipients(msg.conversationId)
                .filter { isPhoneNumber(it) }
                .ifEmpty { listOf(convo.address) }
            val handedOff = if (msg.mediaType == "image" && msg.mediaUri.isNotBlank()) {
                SmsSender.sendMms(getApplication(), messageId, recipients, Uri.parse(msg.mediaUri), simId)
            } else {
                val results = recipients.map { address ->
                    SmsSender.send(
                        getApplication(), messageId, address, msg.body,
                        simId, settings.deliveryReportsEnabled
                    )
                }
                results.isNotEmpty() && results.any { it }
            }
            if (!handedOff) repo.markMessageStatusSuspend(messageId, "failed")
        }
    }

    fun deliveryReportsEnabled() = settings.deliveryReportsEnabled

    fun openOrCreate(address: String, name: String?, onReady: (Long) -> Unit) {
        scope.launch {
            onReady(repo.getOrCreateConversation(address, name))
        }
    }

    fun backupDatabase(pin: String, onResult: (Boolean) -> Unit) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                repo.backupDatabase(getApplication(), pin)
            }
            onResult(ok)
        }
    }

    /** Non-null while an import is running; value = messages processed/target.
     *  MERGE reports rows written so far, REPLACE reports the backup's total
     *  message count before the atomic file swap. */
    val importLoading = androidx.compose.runtime.mutableStateOf<Int?>(null)

    fun importDatabase(
        uri: Uri,
        pin: String?,
        mode: com.anindra.messages.data.ImportMode,
        onResult: (com.anindra.messages.data.Repository.ImportResult) -> Unit
    ) {
        importLoading.value = 0
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    repo.importDatabase(getApplication(), uri, pin, mode) { n ->
                        mainHandler.post { importLoading.value = n }
                    }
                }
            } catch (e: Exception) {
                com.anindra.messages.data.Repository.ImportResult.Error("Import error: ${e.message}")
            }
            importLoading.value = null
            onResult(result)
        }
    }

    /** Imports an SMS Import / Export (sms-ie) backup file. [mode] REPLACE wipes
     *  the current history first, matching the Restore option. */
    fun importSmsIe(
        uri: Uri,
        mode: com.anindra.messages.data.ImportMode = com.anindra.messages.data.ImportMode.MERGE,
        onResult: (Int) -> Unit
    ) {
        importLoading.value = 0
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repo.importSmsIeFrom(getApplication(), uri, mode)
                }.getOrNull()
            }
            importLoading.value = null
            when (result) {
                is com.anindra.messages.data.Repository.ImportResult.Success -> onResult(result.merged ?: 0)
                is com.anindra.messages.data.Repository.ImportResult.Error -> onResult(-1)
                null -> onResult(-1)
            }
        }
    }

    fun peekBackupFormat(uri: Uri, onResult: (com.anindra.messages.data.BackupFormat) -> Unit) {        scope.launch {
            val format = withContext(Dispatchers.IO) {
                repo.peekBackupFormat(getApplication(), uri)
            }
            onResult(format)
        }
    }

    fun togglePin(id: Long) = scope.launch {
        val convo = repo.conversationByIdSuspend(id) ?: return@launch
        repo.setPinnedSuspend(id, !convo.pinned)
    }

    fun unpinAll() = scope.launch { repo.unpinAll() }

    fun unpin(id: Long) = scope.launch { repo.setPinnedSuspend(id, false) }

    fun setPinned(id: Long, pinned: Boolean) = scope.launch { repo.setPinnedSuspend(id, pinned) }

    fun archiveConversation(id: Long) = scope.launch { repo.setArchivedSuspend(id, true) }

    fun unarchiveConversation(id: Long) = scope.launch { repo.setArchivedSuspend(id, false) }

    fun saveDraft(conversationId: Long, draft: String) {
        scope.launch(Dispatchers.IO) { repo.saveDraft(conversationId, draft) }
    }

    /** Leaving a chat: persist the draft first, then trash the conversation if it
     *  ended up with nothing to show. Sequential so the emptiness check sees the
     *  draft we just wrote (and keeps a chat that still has one). */
    fun saveDraftAndMaybeTrash(conversationId: Long, draft: String) {
        scope.launch(Dispatchers.IO) {
            repo.saveDraft(conversationId, draft)
            repo.trashConversationIfEmptySuspend(conversationId)
        }
    }

    fun blockNumber(number: String) {
        scope.launch(Dispatchers.IO) { repo.blockNumber(number) }
    }

    fun unblockNumber(number: String) {
        scope.launch(Dispatchers.IO) { repo.unblockNumber(number) }
    }

    fun isNumberBlocked(number: String): Boolean = repo.isNumberBlocked(number)

    fun blockedNumbers() = repo.blockedNumbers()

    fun conversationNotificationsEnabledFlow(conversationId: Long): Flow<Boolean> =
        repo.conversationNotificationsEnabledFlow(conversationId)

    fun setConversationNotificationsEnabled(conversationId: Long, enabled: Boolean) {
        scope.launch(Dispatchers.IO) { repo.setConversationNotificationsEnabled(conversationId, enabled) }
    }

    fun conversationIdForAddress(address: String): Long? = repo.conversationIdForAddress(address)

    /** Stores the forwarded row in the target conversation *and* hands it to
     *  the framework, so SmsStatusReceiver can flip it to sent/failed. Storing
     *  alone would leave it on "Sending…" forever. */
    fun forwardMessage(messageId: Long, targetConversationId: Long) {
        scope.launch {
            val msg = repo.messageByIdSuspend(messageId) ?: return@launch
            val target = repo.conversationByIdSuspend(targetConversationId) ?: return@launch
            if (!isPhoneNumber(target.address)) return@launch
            val subId = settings.simSubscriptionId
            val text = if (settings.hideLinks) hideUrls(msg.body) else msg.body
when (val plan = ForwardPlan.of(msg, text)) {
                is ForwardPlan.Sms -> {
                    // An attachment with no caption has no text to forward, but
                    // it now travels as MMS below. Anything else blank would put
                    // an empty SMS on the wire, so drop it.
                    if (plan.body.isBlank()) return@launch
                    val stored = repo.sendText(targetConversationId, plan.body, subId) ?: return@launch
                    handOff(stored.id, target.address) {
                        SmsSender.send(
                            getApplication(), stored.id, target.address, plan.body,
                            subId, settings.deliveryReportsEnabled
                        )
                    }
                }
                is ForwardPlan.Mms -> {
                    val uri = Uri.parse(plan.mediaUri)
                    val stored = repo.sendMedia(
                        targetConversationId, "image", plan.mediaUri, plan.caption
                    ) ?: return@launch
                    handOff(stored.id, target.address) {
                        SmsSender.sendMms(
                            getApplication(), stored.id, listOf(target.address), uri, subId, plan.caption
                        )
                    }
                }
            }
        }
    }

    /** Marks the row failed and notifies when the framework declines the
     *  handoff; on success SmsStatusReceiver confirms sent/failed. */
    private suspend fun handOff(rowId: Long, address: String, send: () -> Boolean) {
        if (send()) return
        repo.markMessageStatusSuspend(rowId, "failed")
        NotificationHelper.showSendFailed(getApplication(), address)
    }

    fun scheduledMessages() = repo.scheduledMessages()

    fun scheduleMessage(
        address: String,
        body: String,
        timestamp: Long,
        conversationId: Long,
        onResult: (Boolean) -> Unit = {}
    ) {
        scope.launch(Dispatchers.IO) {
            // The repository rejects a blank body or a non-future time by throwing;
            // an uncaught throw here would take the process down, so the caller is
            // told instead. (#281)
            val ok = runCatching {
                val subId = settings.simSubscriptionId
                val id = repo.addScheduledMessage(address, body, timestamp, conversationId, subId)
                com.anindra.messages.sms.ScheduledMessageSender.schedule(
                    getApplication(), id, address, body, subId, timestamp
                )
            }.isSuccess
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    fun cancelScheduledMessage(id: Long) {
        scope.launch(Dispatchers.IO) {
            com.anindra.messages.sms.ScheduledMessageSender.cancel(getApplication(), id)
            repo.deleteScheduledMessage(id)
        }
    }

    fun rescheduleMessage(id: Long, timestamp: Long, onResult: (Boolean) -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val row = repo.scheduledMessageById(id) ?: error("no scheduled message $id")
                repo.updateScheduledMessage(id, timestamp)
                // the old alarm is keyed by id, so re-arming replaces it
                com.anindra.messages.sms.ScheduledMessageSender.cancel(getApplication(), id)
                com.anindra.messages.sms.ScheduledMessageSender.schedule(
                    getApplication(), id, row.address, row.body, row.subId, timestamp
                )
            }.isSuccess
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    fun exportCrashReports(onReady: (Boolean) -> Unit) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                com.anindra.messages.crash.CrashReporter.exportZipToDownloads(getApplication())
            }
            onReady(ok)
        }
    }

    fun copyCrashReports() {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                com.anindra.messages.crash.CrashReporter.reportText(getApplication())
            }
            val cm = getApplication<Application>()
                .getSystemService(android.content.ClipboardManager::class.java)
            cm?.setPrimaryClip(
                android.content.ClipData.newPlainText(
                    getApplication<Application>().getString(R.string.crash_report_clip_label),
                    text
                )
            )
        }
    }

    fun clearCrashReports() {
        com.anindra.messages.crash.CrashReporter.clear(getApplication())
        pendingCrashReports.value = emptyList()
    }

    fun diagnosticsReport(onReady: (String) -> Unit) {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                com.anindra.messages.diagnostics.DiagnosticsReport.collect(
                    getApplication(),
                    settings.simSubscriptionId,
                    settings,
                    repo.totalConversationCount(),
                    repo.totalMessageCount()
                )
            }
            onReady(text)
        }
    }
}

class MainActivity : FragmentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.READ_SMS] == true) {
                val repo = (application as MessagesApplication).repository
                if (repo.needsInitialImport) repo.requeryFromSystem()
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                grants[Manifest.permission.POST_NOTIFICATIONS] == false
            ) {
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.notifications_disabled),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

    private var navRoute by androidx.compose.runtime.mutableStateOf("list")
    private var pendingOpenAddress by androidx.compose.runtime.mutableStateOf<String?>(null)

    private var lastResumeTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFakeDualSim(intent)
        applySmsIeProbe(intent)
        enableEdgeToEdge()
        requestSmsPermissions()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val display = window.context.display
            val current = display.mode.let {
                com.anindra.messages.diagnostics.DisplayModeInfo(
                    it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate
                )
            }
            val modes = display.supportedModes.map {
                com.anindra.messages.diagnostics.DisplayModeInfo(
                    it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate
                )
            }
            com.anindra.messages.diagnostics.DisplayModeSelector
                .bestModeId(current, modes)
                ?.let { window.attributes.preferredDisplayModeId = it }
        }

        val bootVm = androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class.java]
        if (bootVm.settings.privacyModeEnabled) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }

        val appLockEnabled = bootVm.settings.appLockEnabled

        // Script hooks: --es set_theme dark|light|system, --ez open_settings true
        when (intent.getStringExtra("set_theme")) {
            "dark", "light", "system" -> bootVm.themeMode = intent.getStringExtra("set_theme")!!
        }
        if (intent.getBooleanExtra("open_settings", false)) navRoute = "settings"
        intent.getStringExtra("open_conversation_address")?.let {
            pendingOpenAddress = it
            com.anindra.messages.sms.NotificationHelper.clearConversationNotification(
                this@MainActivity, null, it
            )
        }
        recipientFromIntent(intent)?.let { pendingOpenAddress = it }
        // Opening straight from an external sms:/smsto: launch: hold on a neutral
        // screen while the conversation resolves, so the list never flashes first.
        if (pendingOpenAddress != null && navRoute == "list") navRoute = "opening"

        /** Result of the last "Set as default SMS app" request, so a refusal is visible. */
var defaultSmsResult by mutableStateOf(Activity.RESULT_CANCELED)

    val defaultSmsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            defaultSmsResult = result.resultCode
        }

        setContent {
            val vm: AppViewModel = viewModel()
            var appUnlocked by remember { mutableStateOf(!appLockEnabled) }
            var lockNotAvailable by remember { mutableStateOf(false) }

            if (appLockEnabled && !appUnlocked) {
                LaunchedEffect(Unit) {
                    val biometricManager = BiometricManager.from(this@MainActivity)
                    val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                        val prompt = BiometricPrompt(this@MainActivity, executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    result.cryptoObject
                                    runOnUiThread { appUnlocked = true }
                                }
                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    runOnUiThread { finish() }
                                }
                            })
                        prompt.authenticate(
                            BiometricPrompt.PromptInfo.Builder()
                                .setTitle(getString(R.string.lock_unlock_title))
                                .setSubtitle(getString(R.string.lock_title))
                                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                .build()
                        )
                    } else {
                        // No credential to verify against (no fingerprint/PIN, or no
                        // auth hardware): the lock could not actually protect anything,
                        // so disable it instead of stranding the user behind an inert
                        // lock that "anyone could turn off" — there is no working lock
                        // to bypass, and never fake one.
                        bootVm.settings.appLockEnabled = false
                        lockNotAvailable = true
                    }
                }
            }

            if (!appUnlocked) {
                if (lockNotAvailable) {
                    MessagesTheme(mode = vm.themeMode, font = vm.fontFamily, a11y = vm.a11y) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    stringResource(R.string.lock_off),
                                    style = MaterialTheme.typography.headlineSmall,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.lock_error_no_screen_lock),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(32.dp))
                                Button(onClick = {
                                    val lockIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try {
                                        startActivity(lockIntent)
                                    } catch (_: android.content.ActivityNotFoundException) {
                                        startActivity(
                                            Intent(Settings.ACTION_SECURITY_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }) { Text(stringResource(R.string.lock_turn_on)) }
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { appUnlocked = true }) { Text(stringResource(R.string.lock_got_it)) }
                            }
                        }
                    }
                }
                return@setContent
            }

            MessagesTheme(mode = vm.themeMode, font = vm.fontFamily, a11y = vm.a11y) {
                var chatId by remember { mutableStateOf(-1L) }
                var detailsId by remember { mutableStateOf(-1L) }
                var showDefaultSmsDialog by remember { mutableStateOf(false) }
                var defaultSmsChecked by remember { mutableStateOf(false) }
                // Hoisted above the AnimatedContent so every settings screen keeps
                // its scroll position when navigating into a nested screen and
                // back; inline remember* state would be rebuilt at 0 on re-entry.
                val settingsScroll = rememberScrollState()
                val advancedScroll = rememberScrollState()
                val accessibilityScroll = rememberScrollState()
                val contactDetailsScroll = rememberScrollState()
                val inboxScroll = rememberScrollState()
                val settingsRevision by vm.settings.revision.collectAsState()
                val useNewUi = settingsRevision.let { vm.settings.useNewUi }
                val trashConversationList = rememberLazyListState()
                val trashMessageList = rememberLazyListState()
                val spamConversationList = rememberLazyListState()
                val spamMessageList = rememberLazyListState()

                // Entering Settings from the list starts at the top; returning from
                // a sub-screen keeps the position, so #265 stays fixed.
                var lastRoute by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf("list")
                }
                androidx.compose.runtime.LaunchedEffect(navRoute) {
                    if (com.anindra.messages.ui.ScrollReset.shouldResetToTop(lastRoute, navRoute)) {
                        settingsScroll.scrollTo(0)
                    }
                    lastRoute = navRoute
                }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (navRoute != "settings") {
                        kotlinx.coroutines.delay(1500)
                        val roleManager = getSystemService(RoleManager::class.java)
                        val isDefaultSms = roleManager.isRoleHeld(RoleManager.ROLE_SMS) ||
                            Telephony.Sms.getDefaultSmsPackage(this@MainActivity) == packageName
                        if (!isDefaultSms) {
                            showDefaultSmsDialog = true
                        }
                    }
                    defaultSmsChecked = true
                }

                androidx.compose.runtime.LaunchedEffect(pendingOpenAddress) {
                    val addr = pendingOpenAddress ?: return@LaunchedEffect
                    val repo = (application as com.anindra.messages.MessagesApplication).repository
                    val id = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        repo.conversationIdForAddress(addr) ?: repo.getOrCreateConversationBlocking(addr)
                    }
                    chatId = id
                    navRoute = if (id > 0) "chat" else "list"
                    pendingOpenAddress = null
                }

                if (showDefaultSmsDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showDefaultSmsDialog = false },
                        title = { androidx.compose.material3.Text(stringResource(R.string.default_sms_title)) },
                        text = { androidx.compose.material3.Text(stringResource(R.string.default_sms_message)) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                showDefaultSmsDialog = false
                                val roleManager = getSystemService(RoleManager::class.java)
                                if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                                    defaultSmsLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                                }
                            }) { androidx.compose.material3.Text(stringResource(R.string.default_sms_set)) }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showDefaultSmsDialog = false }) {
                                androidx.compose.material3.Text(stringResource(R.string.lock_not_now))
                            }
                        }
                    )
                }

                if (vm.pendingCrashReports.value.isNotEmpty()) {
                    com.anindra.messages.crash.CrashReportDialog(
                        reportCount = vm.pendingCrashReports.value.size,
                        onExportZip = {
                            vm.exportCrashReports { ok ->
                                if (ok) android.widget.Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.crash_report_saved),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        onCopy = { vm.copyCrashReports() },
                        onDelete = { vm.clearCrashReports() }
                    )
                }

                // Single back dispatcher for all routes; child screen BackHandlers win.
                androidx.activity.compose.BackHandler(enabled = navRoute != "list") {
                    when (navRoute) {
                        "details" -> navRoute = "chat"
                        "add-people" -> navRoute = "details"
                        "trash" -> navRoute = "settings"
                        "inbox" -> navRoute = "settings"
                        "notif-settings" -> navRoute = "advanced"
                        "auto-delete" -> navRoute = "advanced"
                        "links" -> navRoute = "advanced"
                        "advanced" -> navRoute = "settings"
                        "accessibility" -> navRoute = "advanced"
                        "spam" -> navRoute = "settings"
                        else -> navRoute = "list"
                    }
                }

                val routeDepth = mapOf("list" to 0, "opening" to 0, "chat" to 1, "details" to 2, "new" to 1, "settings" to 1, "trash" to 2, "spam" to 2, "inbox" to 2, "advanced" to 2, "accessibility" to 3,
                        "notif-settings" to 3, "auto-delete" to 3, "links" to 3,
                        "add-people" to 3, "mms-check" to 3, "scheduled" to 2)
                val reduceMotion = vm.a11y.reduceMotionEnabled
                val navSlide = motionTween<IntOffset>(reduceMotion, Motion.DURATION_MEDIUM2)
                val navFade = motionTween<Float>(reduceMotion, Motion.DURATION_SHORT4)

                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    if (useNewUi) ConversationsScreen(
                        vm = vm,
                        onOpenConversation = { id ->
                            com.anindra.messages.sms.NotificationHelper
                                .clearConversationNotification(this@MainActivity, id)
                            chatId = id
                            navRoute = "chat"
                        },
                        onNewChat = { navRoute = "new" },
                        onOpenSettings = { navRoute = "settings" }
                    ) else com.anindra.messages.ui.legacy.ConversationsScreen(
                        vm = vm,
                        onOpenConversation = { id ->
                            com.anindra.messages.sms.NotificationHelper
                                .clearConversationNotification(this@MainActivity, id)
                            chatId = id
                            navRoute = "chat"
                        },
                        onNewChat = { navRoute = "new" },
                        onOpenSettings = { navRoute = "settings" }
                    )

                    AnimatedContent(
                        targetState = navRoute,
                        transitionSpec = {
                            if (vm.a11y.reduceMotionEnabled) {
                                androidx.compose.animation.EnterTransition.None togetherWith
                                    androidx.compose.animation.ExitTransition.None
                            } else {
                                val from = routeDepth[initialState] ?: 0
                                val to = routeDepth[targetState] ?: 0
                                when {
                                    to > from ->
                                        slideInHorizontally(navSlide) { it } togetherWith
                                            slideOutHorizontally(navSlide) { -it }
                                    to < from ->
                                        slideInHorizontally(navSlide) { -it } togetherWith
                                            slideOutHorizontally(navSlide) { it }
                                    else -> fadeIn(navFade) togetherWith fadeOut(navFade)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = stringResource(R.string.access_nav)
                    ) { target ->
                        if (target == "list") {
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize())
                        } else {
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                                when (target) {
                                    "opening" -> androidx.compose.foundation.layout.Box(Modifier.fillMaxSize())
                                    "new" -> NewChatScreen(
                                        vm = vm,
                                        onBack = { navRoute = "list" },
                                        onPick = { address, name ->
                                            vm.openOrCreate(address, name) { id ->
                                                chatId = id
                                                navRoute = "chat"
                                            }
                                        }
                                    )
                                    "settings" -> if (useNewUi) SettingsScreen(
                                        vm = vm,
                                        onBack = { navRoute = "list" },
                                        onOpenTrash = { navRoute = "trash" },
                                        onOpenAdvanced = { navRoute = "advanced" },
                                        onOpenSpamBlocked = { navRoute = "spam" },
                                        onOpenInbox = { navRoute = "inbox" },
                                        onOpenScheduled = { navRoute = "scheduled" },
                                        scrollState = settingsScroll
                                    ) else com.anindra.messages.ui.legacy.SettingsScreen(
                                        vm = vm,
                                        onBack = { navRoute = "list" },
                                        onOpenTrash = { navRoute = "trash" },
                                        onOpenAdvanced = { navRoute = "advanced" },
                                        onOpenSpamBlocked = { navRoute = "spam" },
                                        onOpenScheduled = { navRoute = "scheduled" },
                                        scrollState = settingsScroll
                                    )
                                    "inbox" -> InboxSettingsScreen(
                                        vm = vm,
                                        onBack = { navRoute = "settings" },
                                        scrollState = inboxScroll
                                    )
                                    "advanced" -> if (useNewUi) AdvancedSettingsScreen(
                                        vm = vm,
                                        onBack = { navRoute = "settings" },
onOpenAccessibility = { navRoute = "accessibility" },
                                        onOpenNotifications = { navRoute = "notif-settings" },
                                        onOpenAutoDelete = { navRoute = "auto-delete" },
                                        onOpenLinks = { navRoute = "links" },
                                        onOpenMmsCheck = { navRoute = "mms-check" },
                                        scrollState = advancedScroll
                                    ) else com.anindra.messages.ui.legacy.AdvancedSettingsScreen(
                                        vm = vm,
                                        onBack = { navRoute = "settings" },
                                        onOpenAccessibility = { navRoute = "accessibility" },
                                    )
                                    "scheduled" -> ScheduledMessagesScreen(
                                        vm = vm,
                                        onBack = { navRoute = "settings" },
                                        onOpenChat = { id ->
                                            chatId = id
                                            navRoute = "chat"
                                        }
                                    )
                                    "mms-check" -> MmsSupportScreen(
                                        onBack = { navRoute = "advanced" }
                                    )
                                    "notif-settings" -> NotificationSettingsScreen(
                                        vm = vm,
                                        onBack = { navRoute = "advanced" }
                                    )
                                    "auto-delete" -> AutoDeleteSettingsScreen(
                                        vm = vm,
                                        onBack = { navRoute = "advanced" }
                                    )
                                    "links" -> LinkSettingsScreen(
                                        vm = vm,
                                        onBack = { navRoute = "advanced" }
                                    )
                                    "accessibility" -> if (useNewUi) AccessibilityScreen(
                                        vm = vm,
                                        onBack = { navRoute = "advanced" },
                                        scrollState = accessibilityScroll
                                    ) else com.anindra.messages.ui.legacy.AccessibilityScreen(
                                        vm = vm,
                                        onBack = { navRoute = "advanced" },
                                    )
                                    "trash" -> TrashScreen(
                                        vm = vm,
                                        onBack = { navRoute = "settings" },
                                        conversationListState = trashConversationList,
                                        messageListState = trashMessageList
                                    )
                                    "spam" -> SpamBlockedScreen(
                                        vm = vm,
                                        onBack = { navRoute = "settings" },
                                        onOpenConversation = { navRoute = "chat"; chatId = it },
                                        conversationListState = spamConversationList,
                                        messageListState = spamMessageList
                                    )
                                    "details" -> if (useNewUi) ContactDetailsScreen(
                                        vm = vm,
                                        conversationId = detailsId,
onBack = { navRoute = "chat" },
                                        onAddPeople = { navRoute = "add-people" },
                                        scrollState = contactDetailsScroll
                                    ) else com.anindra.messages.ui.legacy.ContactDetailsScreen(
                                        vm = vm,
                                        conversationId = detailsId,
                                        onBack = { navRoute = "chat" },
                                    )
                                    "add-people" -> AddPeopleScreen(
                                        vm = vm,
                                        conversationId = detailsId,
                                        onBack = { navRoute = "details" },
                                        onDone = { picked, groupName ->
                                            // Add them, name the group, then take
                                            // the user into the new group chat so
                                            // they can carry on writing.
                                            vm.addParticipants(
                                                detailsId, picked,
                                                onDone = {
                                                    if (groupName.isNotEmpty()) {
                                                        vm.setGroupTitle(detailsId, groupName)
                                                    }
                                                    chatId = detailsId
                                                    navRoute = "chat"
                                                }
                                            )
                                        }
                                    )
                                    else -> ChatScreen(
                                        vm = vm,
                                        conversationId = chatId,
                                        onBack = { navRoute = "list" },
                                        onOpenDetails = { detailsId = chatId; navRoute = "details" }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestSmsPermissions() {
        val perms = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        val needed = perms.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        com.anindra.messages.sms.ForegroundTracker.setAppForeground(true)
        val repo = (application as MessagesApplication).repository
        // import right away once SMS access appears (default-app role, dialog)
        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            repo.needsInitialImport
        ) {
            repo.requeryFromSystem()
        }
        val now = System.currentTimeMillis()
        if (now - lastResumeTime > 5 * 60_000L) {
            repo.syncFromSystem()
            repo.refreshContactNames()
            lastResumeTime = now
        }
        // A SIM swap or carrier change alters the MMS size and image limits, so the
        // cached carrier config is dropped rather than pinned to the old SIM.
        com.anindra.messages.sms.MmsCarrierConfig.invalidate()
        // Catch MMS whose WAP push was missed (e.g. the app was not the default
        // handler at the time); they stay announced in the provider until fetched.
        com.anindra.messages.sms.MmsDownloader.requestPending(this)
    }

    override fun onPause() {
        com.anindra.messages.sms.ForegroundTracker.setAppForeground(false)
        super.onPause()
    }

    /** Debug builds only: `--ez fake_dual_sim true` makes the app see two fake
     *  SIMs so the dual-SIM UI can be tested on the single-SIM emulator. */
    private fun applyFakeDualSim(intent: Intent) {
        val debuggable =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        com.anindra.messages.data.SimCards.setDebugOverride(
            intent.getBooleanExtra("fake_dual_sim", false),
            debuggable
        )
    }

    /** Debug builds only: `--es mms_probe <media uri> --es mms_probe_to <number>`
     *  sends a single MMS so the PDU/outbox hand-off can be asserted on an
     *  emulator, which has no MMSC to actually deliver to. */
    private fun applyMmsProbe(intent: Intent) {
        val debuggable =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        val media = intent.getStringExtra("mms_probe") ?: return
        val to = intent.getStringExtra("mms_probe_to") ?: return
        val vm = androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class.java]
        vm.openOrCreate(to, null) { id -> vm.sendMediaMessage(id, android.net.Uri.parse(media)) }
    }

    /** Debug builds only: `--es sms_ie_probe <uri>` runs the sms-ie import so
     *  the end-to-end path can be asserted on an emulator (the SAF picker is
     *  not scriptable). */
    private fun applySmsIeProbe(intent: Intent) {
        val debuggable =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        val uri = intent.getStringExtra("sms_ie_probe") ?: return
        val mode = if (intent.getStringExtra("sms_ie_probe_mode") == "replace") {
            com.anindra.messages.data.ImportMode.REPLACE
        } else {
            com.anindra.messages.data.ImportMode.MERGE
        }
        val vm = androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class]
        vm.importSmsIe(Uri.parse(uri), mode) { count ->
            android.util.Log.i("SmsIeImport", "probe imported count=$count")
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyFakeDualSim(intent)
        applyMmsProbe(intent)
        applySmsIeProbe(intent)
        val vm = androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class.java]
        when (intent.getStringExtra("set_theme")) {
            "dark", "light", "system" -> vm.themeMode = intent.getStringExtra("set_theme")!!
        }
        if (intent.getBooleanExtra("open_settings", false)) navRoute = "settings"
        intent.getStringExtra("open_conversation_address")?.let {
            pendingOpenAddress = it
            com.anindra.messages.sms.NotificationHelper.clearConversationNotification(
                this@MainActivity, null, it
            )
        }
        recipientFromIntent(intent)?.let { pendingOpenAddress = it }
        // Warm external launch: hide whatever is on screen (usually the list)
        // immediately so it never shows before the resolved chat.
        if (pendingOpenAddress != null && navRoute == "list") navRoute = "opening"
    }

    /** Recipient of an external `sms:`/`smsto:`/`mms:`/`mmsto:` launch (the
     *  Contacts "Text" button), or null when the intent carries no address.
     *  Strips the `?body=` query and takes the first of any `;`/`,`-separated
     *  recipients, so the chat opens on the dialed number instead of the list. */
    private fun recipientFromIntent(intent: Intent): String? {
        val data = intent.data ?: return null
        val scheme = data.scheme?.lowercase(java.util.Locale.ROOT) ?: return null
        if (scheme !in setOf("sms", "smsto", "mms", "mmsto")) return null
        val raw = data.schemeSpecificPart?.trimStart('/') ?: return null
        val first = raw.substringBefore('?').split(';', ',').firstOrNull()?.trim().orEmpty()
        val decoded = Uri.decode(first)
        return decoded.ifBlank { null }
    }
}
