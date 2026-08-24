package com.anindra.messages

import android.app.Activity
import android.app.Application
import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.app.role.RoleManager
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import com.anindra.messages.data.BlockedNumber
import com.anindra.messages.data.Message
import com.anindra.messages.data.Repository
import com.anindra.messages.sms.NotificationHelper
import com.anindra.messages.sms.SmsSender
import com.anindra.messages.ui.ChatScreen
import com.anindra.messages.ui.ConversationsScreen
import com.anindra.messages.ui.ContactDetailsScreen
import com.anindra.messages.ui.NewChatScreen
import com.anindra.messages.ui.SettingsScreen
import com.anindra.messages.ui.TrashScreen
import com.anindra.messages.ui.isPhoneNumber
import com.anindra.messages.ui.theme.MessagesTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: Repository = (app as MessagesApplication).repository
    val settings = repo.settings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val conversations: Flow<List<Conversation>> = repo.conversations()

    /** Emits true after the first system-SMS import of this process completes. */
    val initialSyncDone = repo.initialSyncDone

    // Observable theme state; SettingsScreen updates it via setTheme()
    var themeMode: String
        get() = _themeState.value
        set(value) { settings.themeMode = value; _themeState.value = value }

    fun setTheme(mode: String) { themeMode = mode }

    private val _themeState = androidx.compose.runtime.mutableStateOf(settings.themeMode)

    fun messages(conversationId: Long): Flow<List<Message>> = repo.messages(conversationId)

    fun conversationById(id: Long): Flow<Conversation?> =
        conversations.map { list -> list.firstOrNull { it.id == id } }

    fun markRead(id: Long) = scope.launch { repo.markReadSuspend(id) }

    /** Clears unread state, returning how many messages were unread. */
    suspend fun consumeUnread(id: Long): Int = withContext(Dispatchers.IO) {
        val n = repo.conversationByIdSuspend(id)?.unreadCount ?: 0
        if (n > 0) repo.markReadSuspend(id)
        n
    }

    fun deleteConversation(id: Long) = scope.launch { repo.trashConversationSuspend(id) }

    fun restoreFromTrash(id: Long) = scope.launch { repo.restoreFromTrashSuspend(id) }

    fun deleteForever(id: Long) = scope.launch { repo.deleteConversationSuspend(id) }

    fun emptyTrash() = scope.launch { repo.emptyTrashSuspend() }

    fun trashedConversations(): Flow<List<Conversation>> = repo.trashedConversations()

    fun setArchived(id: Long, archived: Boolean) =
        scope.launch { repo.setArchivedSuspend(id, archived) }

    fun setLocked(messageId: Long, locked: Boolean) =
        scope.launch { repo.setLockedSuspend(messageId, locked) }

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

    fun send(conversationId: Long, body: String) {
        scope.launch {
            val convo = repo.conversationByIdSuspend(conversationId) ?: return@launch
            val stored = repo.sendText(conversationId, body) ?: return@launch
            if (settings.soundsEnabled) NotificationHelper.playSentSound(getApplication())
            val handedOff = SmsSender.send(
                getApplication(), stored.id, convo.address, body,
                settings.simSubscriptionId, settings.deliveryReportsEnabled
            )
            // ok == accepted by framework; SmsStatusReceiver confirms sent/failed.
            if (!handedOff) {
                repo.markMessageStatusSuspend(stored.id, "failed")
                NotificationHelper.showSendFailed(getApplication(), convo.address)
            }
        }
    }

    fun sendMediaMessage(conversationId: Long, uri: Uri) {
        scope.launch {
            val convo = repo.conversationByIdSuspend(conversationId) ?: return@launch
            if (!isPhoneNumber(convo.address)) return@launch
            val stored = repo.sendMedia(conversationId, "image", uri.toString()) ?: return@launch
            val handedOff = SmsSender.sendMms(
                getApplication(), stored.id, convo.address, uri, settings.simSubscriptionId
            )
            if (!handedOff) {
                repo.markMessageStatusSuspend(stored.id, "failed")
                NotificationHelper.showSendFailed(getApplication(), convo.address)
            }
        }
    }

    fun retryMessage(messageId: Long, simId: Int = settings.simSubscriptionId) {
        scope.launch {
            val msg = repo.messageByIdSuspend(messageId) ?: return@launch
            val convo = repo.conversationByIdSuspend(msg.conversationId) ?: return@launch
            repo.markMessageStatusSuspend(messageId, "sending")
            val handedOff = if (msg.mediaType == "image" && msg.mediaUri.isNotBlank()) {
                SmsSender.sendMms(getApplication(), messageId, convo.address, Uri.parse(msg.mediaUri), simId)
            } else {
                SmsSender.send(
                    getApplication(), messageId, convo.address, msg.body,
                    simId, settings.deliveryReportsEnabled
                )
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

    fun backupDatabase(onResult: (Boolean) -> Unit) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                repo.backupDatabase(getApplication())
            }
            onResult(ok)
        }
    }

    fun importDatabase(uri: Uri, onResult: (Boolean) -> Unit) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                repo.importDatabase(getApplication(), uri)
            }
            onResult(ok)
        }
    }

    fun togglePin(id: Long) = scope.launch {
        val convo = repo.conversationByIdSuspend(id) ?: return@launch
        repo.setPinnedSuspend(id, !convo.pinned)
    }

    fun archiveConversation(id: Long) = scope.launch { repo.setArchivedSuspend(id, true) }

    fun unarchiveConversation(id: Long) = scope.launch { repo.setArchivedSuspend(id, false) }

    fun saveDraft(conversationId: Long, draft: String) {
        scope.launch(Dispatchers.IO) { repo.saveDraft(conversationId, draft) }
    }

    fun blockNumber(number: String) {
        scope.launch(Dispatchers.IO) { repo.blockNumber(number) }
    }

    fun unblockNumber(number: String) {
        scope.launch(Dispatchers.IO) { repo.unblockNumber(number) }
    }

    fun isNumberBlocked(number: String): Boolean = repo.isNumberBlocked(number)

    fun getConversationNotificationsEnabled(conversationId: Long): Boolean =
        repo.getConversationNotificationsEnabled(conversationId)

    fun setConversationNotificationsEnabled(conversationId: Long, enabled: Boolean) {
        scope.launch(Dispatchers.IO) { repo.setConversationNotificationsEnabled(conversationId, enabled) }
    }

    fun conversationIdForAddress(address: String): Long? = repo.conversationIdForAddress(address)

    fun forwardMessage(messageId: Long, targetConversationId: Long) {
        scope.launch {
            val msg = repo.messageByIdSuspend(messageId) ?: return@launch
            repo.sendText(targetConversationId, msg.body)
        }
    }

    fun scheduledMessages() = repo.scheduledMessages()

    fun scheduleMessage(address: String, body: String, timestamp: Long, conversationId: Long) {
        scope.launch(Dispatchers.IO) {
            val subId = settings.simSubscriptionId
            val id = repo.addScheduledMessage(address, body, timestamp, conversationId, subId)
            com.anindra.messages.sms.ScheduledMessageSender.schedule(
                getApplication(), id, address, body, subId, timestamp
            )
        }
    }

    fun cancelScheduledMessage(id: Long) {
        scope.launch(Dispatchers.IO) {
            com.anindra.messages.sms.ScheduledMessageSender.cancel(getApplication(), id)
            repo.deleteScheduledMessage(id)
        }
    }
}

class MainActivity : FragmentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private var navRoute by androidx.compose.runtime.mutableStateOf("list")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestSmsPermissions()

        val bootVm = androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class.java]
        if (bootVm.settings.privacyModeEnabled) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }

        val appLockEnabled = bootVm.settings.appLockEnabled

        // Script hooks:
        //   adb shell am start -n $PKG/.MainActivity --es set_theme dark|light|system
        //   adb shell am start -n $PKG/.MainActivity --ez open_settings true
        when (intent.getStringExtra("set_theme")) {
            "dark", "light", "system" -> bootVm.themeMode = intent.getStringExtra("set_theme")!!
        }
        if (intent.getBooleanExtra("open_settings", false)) navRoute = "settings"

        val defaultSmsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { }

        setContent {
            val vm: AppViewModel = viewModel()
            var appUnlocked by remember { mutableStateOf(!appLockEnabled) }

            if (appLockEnabled && !appUnlocked) {
                LaunchedEffect(Unit) {
                    val biometricManager = BiometricManager.from(this@MainActivity)
                    if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
                        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                        val prompt = BiometricPrompt(this@MainActivity, executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    runOnUiThread { appUnlocked = true }
                                }
                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    runOnUiThread { finish() }
                                }
                            })
                        prompt.authenticate(
                            BiometricPrompt.PromptInfo.Builder()
                                .setTitle("Unlock Messages")
                                .setSubtitle("Authenticate to access your messages")
                                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                .build()
                        )
                    } else {
                        appUnlocked = true
                    }
                }
            }

            if (!appUnlocked) return@setContent

            MessagesTheme(mode = vm.themeMode) {
                var chatId by remember { mutableStateOf(-1L) }
                var detailsId by remember { mutableStateOf(-1L) }
                var showDefaultSmsDialog by remember { mutableStateOf(false) }
                var defaultSmsChecked by remember { mutableStateOf(false) }

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

                if (showDefaultSmsDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showDefaultSmsDialog = false },
                        title = { androidx.compose.material3.Text("Set as default SMS app?") },
                        text = { androidx.compose.material3.Text("To send and receive messages, Messages needs to be your default SMS app.") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                showDefaultSmsDialog = false
                                val roleManager = getSystemService(RoleManager::class.java)
                                if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                                    defaultSmsLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                                }
                            }) { androidx.compose.material3.Text("Set default") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showDefaultSmsDialog = false }) {
                                androidx.compose.material3.Text("Not now")
                            }
                        }
                    )
                }

                // Single back dispatcher for all routes. Child screens may
                // register their own BackHandler first; last-registered wins.
                androidx.activity.compose.BackHandler(enabled = navRoute != "list") {
                    when (navRoute) {
                        "details" -> navRoute = "chat"
                        "trash" -> navRoute = "settings"
                        else -> navRoute = "list"
                    }
                }

                val routeDepth = mapOf("list" to 0, "chat" to 1, "details" to 2, "new" to 1, "settings" to 1, "trash" to 2)
                AnimatedContent(
                    targetState = navRoute,
                    transitionSpec = {
                        val from = routeDepth[initialState] ?: 0
                        val to = routeDepth[targetState] ?: 0
                        when {
                            to > from -> slideInHorizontally(tween(280)) { it } + fadeIn(tween(180)) togetherWith
                                slideOutHorizontally(tween(280)) { -it / 3 } + fadeOut(tween(150))
                            to < from -> slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(180)) togetherWith
                                slideOutHorizontally(tween(280)) { it } + fadeOut(tween(150))
                            else -> fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                        }
                    },
                    label = "nav"
                ) { target ->
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                    ) {
                        when (target) {
                            "list" -> ConversationsScreen(
                                vm = vm,
                                onOpenConversation = { id -> chatId = id; navRoute = "chat" },
                                onNewChat = { navRoute = "new" },
                                onOpenSettings = { navRoute = "settings" }
                            )
                            "new" -> NewChatScreen(
                                onBack = { navRoute = "list" },
                                onPick = { address, name ->
                                    vm.openOrCreate(address, name) { id ->
                                        chatId = id
                                        navRoute = "chat"
                                    }
                                }
                            )
                            "settings" -> SettingsScreen(
                                vm = vm,
                                onBack = { navRoute = "list" },
                                onOpenTrash = { navRoute = "trash" }
                            )
                            "trash" -> TrashScreen(vm = vm, onBack = { navRoute = "settings" })
                            "details" -> ContactDetailsScreen(
                                vm = vm,
                                conversationId = detailsId,
                                onBack = { navRoute = "chat" }
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
        val repo = (application as MessagesApplication).repository
        repo.syncFromSystem()
        repo.refreshContactNames()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val vm = androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class.java]
        when (intent.getStringExtra("set_theme")) {
            "dark", "light", "system" -> vm.themeMode = intent.getStringExtra("set_theme")!!
        }
        if (intent.getBooleanExtra("open_settings", false)) navRoute = "settings"
    }
}
