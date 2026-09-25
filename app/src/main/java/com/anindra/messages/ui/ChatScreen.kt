package com.anindra.messages.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import android.widget.Toast
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.activity.compose.BackHandler
import androidx.core.app.NotificationManagerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.anindra.messages.AppViewModel
import com.anindra.messages.R
import com.anindra.messages.hideUrls
import com.anindra.messages.data.Message
import com.anindra.messages.data.SimCard
import com.anindra.messages.data.SimCards
import com.anindra.messages.data.SimIcon
import com.anindra.messages.data.SimSwitcher
import com.anindra.messages.data.MessageLockCrypto
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.anindra.messages.ui.theme.LocalReduceMotion
import com.anindra.messages.ui.theme.Motion
import com.anindra.messages.ui.theme.motionSpring
import com.anindra.messages.ui.theme.motionTween
import com.anindra.messages.ui.theme.chatBar
import com.anindra.messages.ui.theme.ChatMetaWeight
import com.anindra.messages.ui.theme.incomingBubble
import com.anindra.messages.ui.theme.inputPill
import com.anindra.messages.ui.theme.onSelectedBubble
import com.anindra.messages.ui.theme.outgoingBubble
import com.anindra.messages.ui.theme.selectedBubble
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.runtime.mutableStateListOf


private val EMOJIS = listOf("👍", "😂", "❤️", "🔥", "😢", "😮", "🙏", "🎉")

private const val INITIAL_CHUNK = 40
private const val AUTO_CHUNK = 40
private const val AUTO_CAP = 400
private const val LOAD_EARLIER_STEP = 200

/** Partial-text copy (#231). The message text is shown in a dialog inside a
 *  SelectionContainer: the system handles and the Copy/Share toolbar then work
 *  normally, and the chat keeps its own long-press behaviour instead of losing
 *  every contextual option. */
@Composable
private fun TextCopyDialog(
    body: String,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_select_text)) },
        text = {
            // Plain selectable text, no outlined-field chrome: the platform
            // still raises its own handles/Copy toolbar inside the dialog.
            SelectionContainer {
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_close)) }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    msg: Message,
    showTime: Boolean,
    onTap: () -> Unit,
    deliveryReports: Boolean,
    highlightLinks: Boolean,
    linkWarningEnabled: Boolean,
    hideLinks: Boolean,
    isUnlocked: Boolean,
    showSimIndicator: Boolean,
    isSelected: Boolean = false,
    animateIn: Boolean = false,
    position: BubblePosition = BubblePosition.SINGLE,
    onEntranceStart: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    val isLockedAndHidden = msg.locked && !isUnlocked
    val displayBody = if (isLockedAndHidden) "@Lock" else msg.body
    val slidePx = with(LocalDensity.current) { BubbleEntrance.SLIDE_DP.dp.toPx() }
    val bubbleReduceMotion = LocalReduceMotion.current
    val entrance = remember(msg.id) { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(msg.id) {
        if (animateIn) {
            onEntranceStart()
            android.util.Log.d("BubbleAnim", "entrance id=${msg.id} mine=${msg.isMe}")
            entrance.animateTo(
                1f,
                tween(BubbleEntrance.DURATION_MS, easing = Motion.emphasized(bubbleReduceMotion))
            )
        }
    }
    val bodyText = rememberLinkedText(
        displayBody,
        highlightLinks && !isLockedAndHidden,
        hideLinks && !isLockedAndHidden,
        onLinkClick = { pendingUrl = it },
        textColor = if (isSelected) cs.onSelectedBubble else if (msg.isMe) cs.onPrimaryContainer else cs.onSurface
    )

    val corners = bubbleCorners(position, msg.isMe)
    LaunchedEffect(msg.id, position) {
        android.util.Log.d(
            "BubbleShape",
            "id=${msg.id} position=$position mine=${msg.isMe} " +
                "topStart=${corners.topStart} topEnd=${corners.topEnd} " +
                "bottomStart=${corners.bottomStart} bottomEnd=${corners.bottomEnd}"
        )
    }

    pendingUrl?.let { url ->
        if (linkWarningEnabled) {
            LinkWarningDialog(
                url = url,
                onDismiss = { pendingUrl = null },
                onOpen = { pendingUrl = null; openUrl(context, url) }
            )
        } else {
            pendingUrl = null
            openUrl(context, url)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = entrance.value
                alpha = BubbleEntrance.alpha(p)
                scaleX = BubbleEntrance.scale(p)
                scaleY = BubbleEntrance.scale(p)
                translationX = BubbleEntrance.translationX(p, msg.isMe, slidePx)
                transformOrigin = TransformOrigin(
                    pivotFractionX = if (msg.isMe) 1f else 0f,
                    pivotFractionY = 1f
                )
            },
        horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start) {
            Box {
                if (msg.mediaType == "image" && msg.mediaUri.isNotBlank()) {
                    Column(
                        horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start,
                        modifier = Modifier.combinedClickable(
                            onClick = { onTap() },
                            onLongClick = { onLongPress() }
                        )
                    ) {
                        ImageBubble(uri = msg.mediaUri, isMe = msg.isMe)
                        if (msg.body.isNotBlank()) {
                            Surface(
                                color = if (isSelected) cs.selectedBubble else if (msg.isMe) cs.outgoingBubble else cs.incomingBubble,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.widthIn(max = 260.dp).padding(top = 2.dp)
                                    .combinedClickable(
                                        onClick = { onTap() },
                                        onLongClick = { onLongPress() }
                                    )
                            ) {
                                Text(
                                    text = bodyText,
                                    style = MaterialTheme.typography.bodyLarge.merge(
                                        TextStyle(color = if (isSelected) cs.onSelectedBubble else if (msg.isMe) cs.onPrimaryContainer else cs.onSurface)
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = if (isSelected) cs.selectedBubble else if (msg.isMe) cs.outgoingBubble else cs.incomingBubble,
                        shape = corners.toShape(),
                        modifier = Modifier.widthIn(max = 300.dp).combinedClickable(
                            onClick = { onTap() },
                            onLongClick = { onLongPress() }
                        )
                    ) {
                        Text(
                            text = bodyText,
                            style = MaterialTheme.typography.bodyLarge.merge(
                                TextStyle(color = if (isSelected) cs.onSelectedBubble else if (msg.isMe) cs.onPrimaryContainer else cs.onSurface)
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (msg.status == "failed" && msg.isMe) {
                Row(
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.chat_status_not_sent),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = ChatMetaWeight),
                        color = cs.error
                    )
                    Text(
                        " · ",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = ChatMetaWeight),
                        color = cs.error
                    )
                    Text(
                        stringResource(R.string.chat_status_retry),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = ChatMetaWeight),
                        color = cs.error,
                        modifier = Modifier.clickable { onRetry() }
                    )
                }
            } else if (showTime) {
                // Google shows the type only on your own messages.
                val statusText = if (msg.isMe) {
                    when {
                        deliveryReports && msg.status == "delivered" -> stringResource(R.string.status_delivered)
                        msg.status == "sending" -> "Sending…"
                        else -> "SMS"
                    }
                } else ""
                val simLabel = if (showSimIndicator && msg.subId > 0) {
                    try {
                        val slotIndex = SubscriptionManager.getSlotIndex(msg.subId)
                        if (slotIndex >= 0) String.format(context.getString(R.string.sim_slot_suffix), slotIndex + 1) else ""
                    } catch (_: Exception) { "" }
                } else ""
                val time = formatTimeOnly(msg.timestamp, is24HourFormat(context))
                val label = if (msg.isMe) {
                    "$time \u2022 $statusText$simLabel"
                } else {
                    "$time$simLabel"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = ChatMetaWeight
                    ),
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(
                        top = 6.dp,
                        bottom = 2.dp,
                        start = if (msg.isMe) 0.dp else 4.dp,
                        end = if (msg.isMe) 4.dp else 0.dp
                    )
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: AppViewModel,
    conversationId: Long,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit = {}
) {
    val context = LocalContext.current
    val reduceMotion = LocalReduceMotion.current
    val toolbarFade = motionTween<Float>(reduceMotion, Motion.DURATION_SHORT4)
    val toolbarSlide = motionSpring<IntOffset>(reduceMotion)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val convo by remember(conversationId) { vm.conversationById(conversationId) }.collectAsState(initial = null)
    val vmContacts by remember(vm) { vm.contacts }.collectAsState(initial = emptyList())
    val workNums = remember(vmContacts) {
        vmContacts.filter { it.workProfile }.map { phoneKey(it.number) }.toSet()
    }
    val listState = rememberLazyListState()
    // Progressive loading: latest chunk first, shimmer while older messages queue
    var pageLimit by remember(conversationId) { mutableIntStateOf(INITIAL_CHUNK) }
    var messagesLoaded by remember(conversationId) { mutableStateOf(false) }
    val messagesFlow = remember(conversationId, pageLimit) { vm.messages(conversationId, limit = pageLimit) }
    val messages by messagesFlow
        .onEach { messagesLoaded = true }
        .collectAsState(initial = emptyList())
    val totalCount by remember(conversationId) { vm.messageCountFlow(conversationId) }.collectAsState(initial = 0)
    // Highest id already on screen when the chat was opened (or first loaded);
    // only newer arrivals play the entrance animation, so scrolling back and
    // forward never replays it.
    var entranceBaseline by remember(conversationId) { mutableStateOf(-1L) }
    val animatedIds = remember(conversationId) { mutableStateListOf<Long>() }
    LaunchedEffect(messages, messagesLoaded) {
        if (entranceBaseline < 0 && messagesLoaded) {
            entranceBaseline = messages.maxOfOrNull { it.id } ?: 0L
        }
    }
    val pendingEarlier = messagesLoaded && totalCount > pageLimit && pageLimit < AUTO_CAP
    val deliveryReports = remember { vm.deliveryReportsEnabled() }
    var draft by remember { mutableStateOf("") }
    var draftLoaded by remember { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var attachSheet by remember { mutableStateOf(false) }

    val selectedMessageIds = remember { mutableStateListOf<Long>() }
    var textCopyMessage by remember { mutableStateOf<Message?>(null) }
    val selectionActive = selectedMessageIds.isNotEmpty()

    var cameraFileUri by remember { mutableStateOf<Uri?>(null) }

    var currentSimId by remember { mutableIntStateOf(vm.settings.simSubscriptionId) }
    var sims by remember { mutableStateOf(emptyList<SimCard>()) }

    var numberIsBlocked by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showPermanentDeleteDialog by remember { mutableStateOf(false) }

    var forwardingMessageIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var showForwardPicker by remember { mutableStateOf(false) }

    var detailsMessage by remember { mutableStateOf<Message?>(null) }

    var pendingSendText by remember { mutableStateOf("") }
    var sendCountdown by remember { mutableIntStateOf(0) }

    var showSchedulePicker by remember { mutableStateOf(false) }
    var scheduleStep by remember { mutableStateOf("date") }
    var scheduledDateMillis by remember { mutableStateOf<Long?>(null) }
    var scheduledHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var scheduledMinute by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }

    // sendAttempt (re)arms the countdown effect; Compose cancels the old one (#93)
    var sendAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(sendAttempt) {
        if (sendAttempt == 0 || pendingSendText.isEmpty() || sendCountdown <= 0) return@LaunchedEffect
        while (sendCountdown > 0) {
            delay(1000L)
            sendCountdown--
        }
        val toSend = pendingSendText
        pendingSendText = ""
        vm.send(conversationId, toSend, currentSimId)
        vm.saveDraft(conversationId, "")
        draft = ""
        showEmoji = false
    }

    // SIM management
    var retryingMessageId by remember { mutableStateOf(-1L) }
    var showRetrySimPicker by remember { mutableStateOf(false) }

    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sims = SimCards.load(context).filter { it.slotIndex >= 0 }.sortedBy { it.slotIndex }
            if (sims.isNotEmpty() && currentSimId == -1) {
                currentSimId = sims.first().subscriptionId
                vm.settings.simSubscriptionId = currentSimId
            }
        }
    }

    // Message locking
    var unlockedIds by remember { mutableStateOf(setOf<Long>()) }
    val activity = context as? androidx.fragment.app.FragmentActivity
    val biometricExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            biometricExecutor.shutdown()
            com.anindra.messages.sms.ForegroundTracker.setOpenConversation(null)
        }
    }

    fun cycleSim() {
        val next = SimSwitcher.next(currentSimId, sims) ?: return
        currentSimId = next.subscriptionId
        vm.settings.simSubscriptionId = next.subscriptionId
        val carrier = next.carrierName?.ifBlank { null }
        val label = if (carrier != null) String.format("%s · SIM %s", carrier, next.slotIndex + 1) else String.format(context.getString(R.string.settings_sim_label), next.slotIndex + 1)
        Toast.makeText(context, context.getString(R.string.chat_sending_via, label), Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        sims = SimCards.load(context).filter { it.slotIndex >= 0 }.sortedBy { it.slotIndex }
        if (sims.isNotEmpty() && currentSimId == -1) {
            currentSimId = sims.first().subscriptionId
            vm.settings.simSubscriptionId = currentSimId
        }
        if (sims.isEmpty() &&
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraFileUri?.let { vm.sendMediaMessage(conversationId, it) }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { vm.sendMediaMessage(conversationId, it) }
    }

    fun leaveChat() {
        vm.saveDraftAndMaybeTrash(conversationId, draft.trim(), vm.settings.draftsEnabled)
        onBack()
    }

    fun toggleSelection(id: Long) {
        if (id in selectedMessageIds) selectedMessageIds.remove(id) else selectedMessageIds.add(id)
    }

    fun clearSelection() = selectedMessageIds.clear()

    /** Select every unlocked message so bulk actions cannot trash locked ones. */
    fun selectAllMessages() {
        val locked = messages.filter { it.locked }.map { it.id }.toSet()
        selectedMessageIds.clear()
        selectedMessageIds.addAll(SelectionToolbar.selectAllCandidates(messages.map { it.id }, locked))
    }

    /** Partial-text copy (#231). A dialog is used instead of turning the bubble
     *  into a selection surface: that mode swallowed the long-press and left the
     *  user with no visible way back. Opening is deferred a frame so the
     *  dropdown's dismiss click cannot land on the new dialog's scrim. */
    fun startTextSelection(id: Long) {
        val msg = messages.firstOrNull { it.id == id } ?: return
        clearSelection()
        scope.launch {
            delay(150)
            textCopyMessage = msg
        }
    }

    fun saveMessageImage(id: Long) {
        vm.saveMessageImage(id)
        clearSelection()
    }

    fun selectedText(): String {
        val byId = messages.associateBy { it.id }
        return selectedMessageIds
            .mapNotNull { byId[it] }
            .joinToString("\n") { m ->
                val hidden = m.locked && !unlockedIds.contains(m.id)
                when {
                    hidden -> "@Lock"
                    vm.settings.hideLinks -> hideUrls(m.body)
                    else -> m.body
                }
            }
    }

    fun copySelection() {
        val text = selectedText()
        if (text.isNotBlank()) {
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText(context.getString(R.string.chat_messages_label), text))
            Toast.makeText(context, context.getString(R.string.chat_copied), Toast.LENGTH_SHORT).show()
        }
    }

    fun forwardSelection() {
        val ids = messages.filter { it.id in selectedMessageIds }.map { it.id }
        if (ids.isNotEmpty() && vm.settings.forwardingEnabled) {
            forwardingMessageIds = ids
            showForwardPicker = true
        }
    }

    fun shareSelection() {
        val text = selectedText()
        if (text.isNotBlank()) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, context.getString(R.string.chat_share)))
        }
    }

    fun deleteSelection() {
        val ids = selectedMessageIds.toList()
        ids.forEach { vm.deleteMessage(it) }
        clearSelection()
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = if (ids.size == 1) context.getString(R.string.chat_message_deleted) else String.format(context.getString(R.string.chat_messages_deleted), ids.size),
                actionLabel = context.getString(R.string.action_undo),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                ids.forEach { vm.restoreMessage(it) }
            }
        }
    }

    fun lockUnlockSelection() {
        val targets = messages.filter { it.id in selectedMessageIds }
        if (targets.isEmpty()) return
        if (targets.any { !it.locked }) {
            targets.forEach { vm.setLocked(it.id, true) }
            Toast.makeText(context, context.getString(R.string.chat_locked), Toast.LENGTH_SHORT).show()
            clearSelection()
        } else if (activity != null) {
            val biometricManager = BiometricManager.from(activity)
            val canAuth = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                val authCipher = MessageLockCrypto.newAuthCipher()
                val prompt = BiometricPrompt(activity, biometricExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            // Tie the unlock to the authenticated Keystore key: a
                            // crypto operation with the CryptoObject cipher proves the
                            // user actually authenticated, instead of trusting a
                            // boolean that UI-hooking tools can flip.
                            val cipher = result.cryptoObject?.cipher
                            val authenticated = cipher == null || MessageLockCrypto.proveAuth(cipher)
                            activity.runOnUiThread {
                                if (authenticated) {
                                    targets.forEach { vm.setLocked(it.id, false) }
                                    unlockedIds = unlockedIds + targets.map { it.id }
                                }
                            }
                        }
                    })
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(context.getString(R.string.lock_unlock_title))
                    .setSubtitle(context.getString(R.string.lock_auth_subtitle))
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()
                if (authCipher != null) {
                    prompt.authenticate(info, BiometricPrompt.CryptoObject(authCipher))
                } else {
                    prompt.authenticate(info)
                }
            } else {
                targets.forEach { vm.setLocked(it.id, false) }
                unlockedIds = unlockedIds + targets.map { it.id }
            }
            clearSelection()
        }
    }

    BackHandler(onBack = ::leaveChat)
    BackHandler(enabled = selectionActive) { clearSelection() }

    // Bottom on load + new messages. Int.MAX_VALUE clamps to the last row, so the newest
    // message is brought into view even before layout has counted the freshly added row.
    var hasScrolledToBottom by remember(conversationId) { mutableStateOf(false) }
    val newestId = messages.lastOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null) {
            listState.scrollToItem(Int.MAX_VALUE)
            hasScrolledToBottom = true
        }
    }
    // Retry once if the first scroll raced the layout pass.
    LaunchedEffect(messages.size) {
        if (!hasScrolledToBottom && messages.isNotEmpty()) {
            delay(80)
            listState.scrollToItem(Int.MAX_VALUE)
            hasScrolledToBottom = true
        }
    }
    // Load chunks while pinned near the bottom so inserting rows doesn't jump the view
    LaunchedEffect(pageLimit, totalCount) {
        if (!pendingEarlier) return@LaunchedEffect
        val nearBottom: () -> Boolean = {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
        }
        while (!nearBottom()) delay(80)
        delay(240)
        pageLimit = (pageLimit + AUTO_CHUNK).coerceAtMost(AUTO_CAP)
    }
    LaunchedEffect(conversationId) {
        vm.markRead(conversationId)
        // Dismiss all app notifications when user opens a chat
        NotificationManagerCompat.from(context).cancelAll()
        draftLoaded = false
    }
    LaunchedEffect(convo?.address) {
        com.anindra.messages.sms.ForegroundTracker.setOpenConversation(convo?.address)
    }
    LaunchedEffect(convo) {
        if (convo != null && !draftLoaded) {
            if (vm.settings.draftsEnabled) {
                val savedDraft = convo!!.draft
                if (savedDraft.isNotBlank()) {
                    draft = savedDraft
                }
            }
            draftLoaded = true
            numberIsBlocked = vm.isNumberBlocked(convo!!.address)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedContent(
                targetState = selectionActive,
                transitionSpec = {
                    (fadeIn(toolbarFade) +
                        slideInVertically(
                            animationSpec = toolbarSlide,
                            initialOffsetY = { -it / 4 }
                        )) togetherWith
                        (fadeOut(toolbarFade) +
                            slideOutVertically(
                                animationSpec = toolbarSlide,
                                targetOffsetY = { it / 4 }
                            ))
                },
                label = stringResource(R.string.access_chat_top_bar)
            ) { selecting ->
            if (selecting) {
                MessageSelectionToolbar(
                    count = selectedMessageIds.size,
                    allLocked = messages.filter { it.id in selectedMessageIds }.all { it.locked },
                    onSelectAll = { selectAllMessages() },
                    onSelectText = messages.firstOrNull { it.id in selectedMessageIds }
                        ?.takeIf { it.body.isNotBlank() }
                        ?.let { m -> { startTextSelection(m.id) } },
                    onSaveImage = messages.firstOrNull { it.id in selectedMessageIds }
                        ?.takeIf { it.mediaUri.isNotBlank() }
                        ?.let { m -> { saveMessageImage(m.id) } },
                    onClose = { clearSelection() },
                    onCopy = { copySelection() },
                    onForward = { forwardSelection() },
                    onShare = { shareSelection() },
                    onViewDetails = {
                        detailsMessage = messages.firstOrNull { it.id in selectedMessageIds }
                        clearSelection()
                    },
                    onDelete = { deleteSelection() },
                    onLockUnlock = { lockUnlockSelection() }
                )
            } else {
    val workProfile = convo?.let { c ->
        val key = phoneKey(c.address)
        key.isNotEmpty() && key in workNums
    } ?: false

            ChatTopBar(
                convo = convo,
                workProfile = workProfile,
                sims = sims,
                currentSimId = currentSimId,
                menuOpen = menuOpen,
                numberIsBlocked = numberIsBlocked,
                blockingEnabled = vm.settings.blockingEnabled,
                sendCountdown = sendCountdown,
                draftsEnabled = vm.settings.draftsEnabled,
                onBack = ::leaveChat,
                onOpenDetails = onOpenDetails,
                onMenuToggle = { menuOpen = true },
                onMenuDismiss = { menuOpen = false },
                onSimSelect = { simId, label ->
                    currentSimId = simId
                    vm.settings.simSubscriptionId = simId
                    Toast.makeText(context, context.getString(R.string.chat_sending_via, label), Toast.LENGTH_SHORT).show()
                },
                onArchive = {
                    vm.setArchived(conversationId, true)
                    Toast.makeText(context, context.getString(R.string.chat_archived), Toast.LENGTH_SHORT).show()
                    onBack()
                },
                onDelete = {
                    if (vm.settings.permanentDeleteEnabled) {
                        if (vm.settings.permanentDeleteWarn) {
                            showPermanentDeleteDialog = true
                        } else {
                            vm.deleteConversation(conversationId)
                            onBack()
                        }
                    } else {
                        vm.deleteConversation(conversationId)
                        Toast.makeText(context, context.getString(R.string.chat_moved_to_trash), Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                },
                onBlock = {
                    convo?.address?.let { addr ->
                        vm.blockNumber(addr)
                        Toast.makeText(context, context.getString(R.string.chat_moved_to_spam), Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                },
                onUnblock = {
                    convo?.address?.let { addr ->
                        vm.unblockNumber(addr)
                        numberIsBlocked = false
                        Toast.makeText(context, context.getString(R.string.chat_number_unblocked), Toast.LENGTH_SHORT).show()
                    }
                },
                onAddPeople = { /* no-op: MMS group chat, stub */ }
            )
            }
            }
        },
        bottomBar = {
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.chatBar)
                    .navigationBarsPadding()
            ) {
                AnimatedVisibility(
                    visible = showEmoji && !selectionActive,
                    enter = if (reduceMotion) fadeIn(tween(0))
                    else fadeIn(motionTween(false, Motion.DURATION_SHORT4)) +
                        expandVertically(
                            animationSpec = motionSpring(false),
                            expandFrom = Alignment.Top
                        ),
                    exit = if (reduceMotion) fadeOut(tween(0))
                    else fadeOut(motionTween(false, Motion.DURATION_SHORT4)) +
                        shrinkVertically(
                            animationSpec = motionSpring(false),
                            shrinkTowards = Alignment.Top
                        )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        EMOJIS.forEach { e ->
                            Text(
                                e,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier
                                    .clickable { draft += e }
                                    .padding(6.dp)
                            )
                        }
                    }
                }
                val chatAddress = convo?.address ?: ""
                if (chatAddress.isBlank() || isPhoneNumber(chatAddress)) {
                    InputBar(
                        draft = draft,
                        placeholder = stringResource(R.string.text_placeholder),
                        sims = sims,
                        currentSimId = currentSimId,
                        onCycleSim = { cycleSim() },
                        onDraftChange = { draft = it },
                        onSend = {
                            val text = draft.trim()
                            if (text.isNotEmpty()) {
                                val addr = convo?.address ?: ""
                                if (vm.isNumberBlocked(addr)) {
                                    showBlockedDialog = true
                                    return@InputBar
                                }
                                if (addr.isBlank()) return@InputBar
                                if (vm.settings.delayedSendingEnabled) {
                                    pendingSendText = text
                                    sendCountdown = vm.settings.delaySeconds
                                    sendAttempt++
                                } else {
                                    vm.send(conversationId, text, currentSimId)
                                    vm.saveDraft(conversationId, "")
                                    draft = ""
                                    showEmoji = false
                                }
                            }
                        },
                        onSchedule = {
                            val text = draft.trim()
                            if (text.isNotEmpty()) {
                                val addr = convo?.address ?: ""
                                if (vm.isNumberBlocked(addr)) {
                                    showBlockedDialog = true
                                    return@InputBar
                                }
                                if (addr.isBlank()) return@InputBar
                                scheduleStep = "date"
                                showSchedulePicker = true
                            }
                        },
                        onEmojiToggle = { showEmoji = !showEmoji },
                        onAttach = { attachSheet = true },
                        showEmojiButton = vm.settings.emojiButtonEnabled
                    )
                } else {
                    AlphanumericNotice(address = chatAddress)
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val revealed = remember { mutableStateListOf<Long>() }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(messages, key = { _, m -> m.id }) { idx, msg ->
                    val prev = messages.getOrNull(idx - 1)
                    val groupStart = prev == null || startsNewGroup(prev, msg)
                    val isLastMessage = idx == messages.lastIndex
                    val isRevealed = msg.id in revealed

                    Column(Modifier.fillMaxWidth()) {
                        if (groupStart) {
                            Box(
                                Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    formatGroupLabel(msg.timestamp, is24HourFormat(context)),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = ChatMetaWeight
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        ChatBubble(
                            msg = msg,
                            showTime = isLastMessage || isRevealed,
                            onTap = {
                                if (selectionActive) toggleSelection(msg.id)
                                else if (isRevealed) revealed.remove(msg.id) else revealed.add(msg.id)
                            },
                            deliveryReports = deliveryReports,
                            highlightLinks = vm.settings.highlightLinks,
                            linkWarningEnabled = vm.settings.linkOpenWarningEnabled,
                            hideLinks = vm.settings.hideLinks,
                            isUnlocked = unlockedIds.contains(msg.id),
                            showSimIndicator = vm.settings.showSimIndicator,
                            isSelected = msg.id in selectedMessageIds,
                            position = bubblePosition(messages, idx),
                            animateIn = BubbleEntrance.shouldAnimate(
                                msg.id, entranceBaseline, msg.id in animatedIds,
                                reduceMotion = LocalReduceMotion.current
                            ),
                            onEntranceStart = { if (msg.id !in animatedIds) animatedIds.add(msg.id) },
                            onLongPress = { toggleSelection(msg.id) },
                            onRetry = { vm.retryMessage(msg.id) }
                        )
                    }
                }
            }
            if (sendCountdown > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            String.format(context.getString(R.string.chat_sending_countdown), sendCountdown),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        TextButton(onClick = {
                            sendAttempt++          // cancels the ticking effect
                            sendCountdown = 0
                            pendingSendText = ""
                        }) {
                            Text(stringResource(R.string.chat_cancel))
                        }
                    }
                }
            }

        }
    }

    if (attachSheet) {
        AttachSheet(
            onGallery = {
                attachSheet = false
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCamera = {
                attachSheet = false
                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
                cameraFileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraLauncher.launch(cameraFileUri!!)
            },
            onDismiss = { attachSheet = false }
        )
    }

    if (showRetrySimPicker && retryingMessageId > 0) {
        SimPickerDialog(
            sims = sims,
            currentSimId = currentSimId,
            onSelect = { simId ->
                showRetrySimPicker = false
                vm.retryMessage(retryingMessageId, simId)
                retryingMessageId = -1
            },
            onDismiss = {
                showRetrySimPicker = false
                retryingMessageId = -1
            }
        )
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text(stringResource(R.string.chat_number_blocked)) },
            text = { Text(stringResource(R.string.chat_blocked_send)) },
            confirmButton = {
                TextButton(onClick = {
                    showBlockedDialog = false
                    convo?.address?.let { addr ->
                        vm.unblockNumber(addr)
                        numberIsBlocked = false
                    }
                }) { Text(stringResource(R.string.chat_unblock)) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockedDialog = false }) { Text(stringResource(R.string.chat_cancel)) }
            }
        )
    }

    if (showPermanentDeleteDialog) {
        PermanentDeleteConfirmDialog(
            onConfirm = { dontShowAgain ->
                if (dontShowAgain) vm.settings.permanentDeleteWarn = false
                showPermanentDeleteDialog = false
                vm.deleteConversation(conversationId)
                onBack()
            },
            onDismiss = { showPermanentDeleteDialog = false }
        )
    }

    detailsMessage?.let { detail ->
        MessageDetailsDialog(
            message = detail,
            address = convo?.address ?: "",
            onDismiss = { detailsMessage = null }
        )
    }

    if (showForwardPicker) {
        val vmContacts by vm.contacts.collectAsState()
        ForwardPicker(
            contacts = vmContacts,
            onPick = { address, name ->
                showForwardPicker = false
                val targets = forwardingMessageIds
                vm.openOrCreate(address, name) { targetId ->
                    targets.forEach { vm.forwardMessage(it, targetId) }
                    forwardingMessageIds = emptyList()
                    Toast.makeText(context, context.getString(R.string.chat_forwarded), Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = {
                showForwardPicker = false
                forwardingMessageIds = emptyList()
            }
        )
    }

    if (showSchedulePicker) {
        ChatSchedulePicker(
            visible = showSchedulePicker,
            step = scheduleStep,
            conversationId = conversationId,
            address = convo?.address ?: "",
            draft = draft,
            scheduledDateMillis = scheduledDateMillis,
            scheduledHour = scheduledHour,
            scheduledMinute = scheduledMinute,
            onDateSelected = { millis ->
                scheduledDateMillis = millis
                scheduleStep = "time"
            },
            onTimeSelected = { hour, minute ->
                scheduledHour = hour
                scheduledMinute = minute
            },
            onSchedule = { ts ->
                val text = draft.trim()
                val addr = convo?.address ?: return@ChatSchedulePicker
                vm.scheduleMessage(addr, text, ts, conversationId)
                val timeText = formatDateTime(ts, "MMM d,", is24HourFormat(context))
                Toast.makeText(context, context.getString(R.string.chat_scheduled_for, timeText), Toast.LENGTH_SHORT).show()
                vm.saveDraft(conversationId, "")
                draft = ""
                showEmoji = false
                showSchedulePicker = false
            },
            onDismiss = { showSchedulePicker = false }
        )
    }
    textCopyMessage?.let { msg ->
        TextCopyDialog(
            body = msg.body,
            onDismiss = { textCopyMessage = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageSelectionToolbar(
    count: Int,
    allLocked: Boolean,
    onSelectAll: () -> Unit,
    onSelectText: (() -> Unit)?,
    onSaveImage: (() -> Unit)?,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onViewDetails: () -> Unit,
    onDelete: () -> Unit,
    onLockUnlock: () -> Unit
) {
    var overflow by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.chatBar,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.primary,
            actionIconContentColor = MaterialTheme.colorScheme.primary
        ),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, stringResource(R.string.icon_cancel_selection), tint = MaterialTheme.colorScheme.primary)
            }
        },
        title = {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        },
        actions = {
            if (SelectionToolbar.showCopy(count)) {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, stringResource(R.string.icon_copy), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (SelectionToolbar.showForward(count)) {
                IconButton(onClick = onForward) {
                    Icon(
                        painterResource(R.drawable.ic_forward),
                        stringResource(R.string.chat_forward),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (SelectionToolbar.showTrash(count)) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, stringResource(R.string.chat_trash), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (SelectionToolbar.showMore(count)) {
                Box {
                    IconButton(onClick = { overflow = true }) {
                        Icon(Icons.Outlined.MoreVert, stringResource(R.string.icon_more_options), tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(
                        expanded = overflow,
                        onDismissRequest = { overflow = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_select_all)) },
                            onClick = { overflow = false; onSelectAll() }
                        )
                        if (onSelectText != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_select_text)) },
                                onClick = { overflow = false; onSelectText() }
                            )
                        }
                        if (onSaveImage != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_save_image)) },
                                onClick = { overflow = false; onSaveImage() }
                            )
                        }
                        if (SelectionToolbar.showSingleMessageActions(count)) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_share)) },
                                onClick = { overflow = false; onShare() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_view_details)) },
                                onClick = { overflow = false; onViewDetails() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(SelectionToolbar.lockLabelRes(allLocked))) },
                                onClick = { overflow = false; onLockUnlock() }
                            )
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    convo: com.anindra.messages.data.Conversation?,
    workProfile: Boolean,
    sims: List<SimCard>,
    currentSimId: Int,
    menuOpen: Boolean,
    numberIsBlocked: Boolean,
    blockingEnabled: Boolean,
    sendCountdown: Int,
    draftsEnabled: Boolean,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onSimSelect: (subscriptionId: Int, label: String) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onAddPeople: () -> Unit
) {
    val context = LocalContext.current
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.chatBar
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.icon_back))
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onOpenDetails() }
            ) {
                PersonAvatar(convo?.address ?: "?", size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            convo?.let {
                                if (it.name != it.address) it.name
                                else BidiText.ltr(it.display)
                            } ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        if (workProfile) {
                            Spacer(Modifier.width(6.dp))
                            WorkProfileBadge()
                        }
                    }
                    if (draftsEnabled && convo?.draft?.isNotBlank() == true && sendCountdown == 0) {
                        Text(
                            stringResource(R.string.chat_draft_prefix),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = {
                convo?.address?.let {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it")))
                }
            }) { Icon(Icons.Rounded.Call, stringResource(R.string.icon_call)) }

            Box {
                IconButton(onClick = onMenuToggle) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.icon_more_options))
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = onMenuDismiss,
                    containerColor = MaterialTheme.colorScheme.chatBar
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_add_people)) },
                        onClick = { onMenuDismiss(); onAddPeople() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_details)) },
                        onClick = { onMenuDismiss(); onOpenDetails() }
                    )
                    if (sims.size > 1) {
                        sims.sortedBy { it.slotIndex }.forEach { sub ->
                            val carrier = sub.carrierName?.ifBlank { null }
                            val simLabel = buildString {
                                append(String.format(context.getString(R.string.settings_sim_label), sub.slotIndex + 1))
                                if (carrier != null) append(String.format(" · %s", carrier))
                            }
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(simLabel, modifier = Modifier.weight(1f))
                                        RadioButton(
                                            selected = sub.subscriptionId == currentSimId,
                                            onClick = null
                                        )
                                    }
                                },
                                onClick = {
                                    onMenuDismiss()
                                    onSimSelect(sub.subscriptionId, simLabel)
                                }
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_archive)) },
                        onClick = { onMenuDismiss(); onArchive() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_delete)) },
                        onClick = { onMenuDismiss(); onDelete() }
                    )
                    if (blockingEnabled) {
                        if (numberIsBlocked) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_unblock_number)) },
                                onClick = { onMenuDismiss(); onUnblock() }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_block_number)) },
                                onClick = { onMenuDismiss(); onBlock() }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun ChatMessageList(
    messages: List<com.anindra.messages.data.Message>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    deliveryReports: Boolean,
    sims: List<SimCard>,
    highlightLinks: Boolean,
    linkWarningEnabled: Boolean,
    hideLinks: Boolean,
    forwardingEnabled: Boolean,
    unlockedIds: Set<Long>,
    showEntrySkeleton: Boolean,
    pendingEarlier: Boolean,
    hasEarlierButton: Boolean,
    onLoadEarlier: () -> Unit,
    onRetry: (msgId: Long) -> Unit,
    onRetryWithPicker: (msgId: Long) -> Unit,
    onLongPress: (msgId: Long) -> Unit,
    onLockUnlock: (msgId: Long, wantLock: Boolean) -> Unit,
    onDeleteMessage: (msgId: Long) -> Unit,
    showSimIndicator: Boolean = true
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showEntrySkeleton) {
            items(3, key = { "entry_skeleton_$it" }) { SkeletonMessageRow() }
        } else {
            if (pendingEarlier) {
                items(3, key = { "skeleton_$it" }) { SkeletonMessageRow() }
            }
            if (hasEarlierButton) {
                item(key = "load_earlier") {
                    TextButton(
                        onClick = onLoadEarlier,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.chat_loading_earlier))
                    }
                }
            }
        }
        itemsIndexed(messages, key = { _, msg -> msg.id }) { idx, msg ->
            // per-row unlock bit bounds recomposition to that single item
            val rowIsUnlocked = remember(msg.id, unlockedIds) { msg.id in unlockedIds }
            MessageRow(
                msg = msg,
                showDividerBefore = idx == 0 || !sameDay(messages[idx - 1].timestamp, msg.timestamp),
                showStatus = idx == messages.lastIndex && msg.isMe,
                deliveryReports = deliveryReports,
                forwardingEnabled = forwardingEnabled,
                onRetry = {
                    if (sims.size > 1) onRetryWithPicker(msg.id) else onRetry(msg.id)
                },
                onLongPress = { onLongPress(msg.id) },
                highlightLinks = highlightLinks,
                linkWarningEnabled = linkWarningEnabled,
                hideLinks = hideLinks,
                onLockUnlock = { wantLock -> onLockUnlock(msg.id, wantLock) },
                onDelete = { onDeleteMessage(msg.id) },
                isUnlocked = rowIsUnlocked,
                showSimIndicator = showSimIndicator
            )
        }
    }
}

/** Shimmer placeholder bubbles shown while earlier messages are still loading. */
@Composable
private fun SkeletonMessageRow() {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .width(210.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                    .shimmer()
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Box(
                Modifier
                    .width(160.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp))
                    .shimmer()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSchedulePicker(
    visible: Boolean,
    step: String,
    conversationId: Long,
    address: String,
    draft: String,
    scheduledDateMillis: Long?,
    scheduledHour: Int,
    scheduledMinute: Int,
    onDateSelected: (millis: Long) -> Unit,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onSchedule: (millis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val context = LocalContext.current
    if (step == "date") {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = scheduledDateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { onDateSelected(datePickerState.selectedDateMillis ?: System.currentTimeMillis()) }) {
                    Text(stringResource(R.string.chat_next))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    } else if (step == "time") {
        val timePickerState = rememberTimePickerState(
            initialHour = scheduledHour,
            initialMinute = scheduledMinute,
            is24Hour = is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.chat_select_time)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeSelected(timePickerState.hour, timePickerState.minute)
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = scheduledDateMillis ?: System.currentTimeMillis()
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onSchedule(cal.timeInMillis)
                }) { Text(stringResource(R.string.chat_schedule)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
            }
        )
    }
}

/** Locale-formatted number, LTR-isolated so RTL layouts don't reorder its groups. */
fun formatPhoneNumber(raw: String): String =
    BidiText.ltr(
        com.anindra.messages.data.PhoneNumberUtils.displayFor(
            raw, com.anindra.messages.data.PhoneNumberUtils.region()
        )
    )

/** Stable identity for cross-referencing a stored address against a contact
 *  number: E.164 when valid, else the address unchanged (alphanumeric IDs). */
fun phoneKey(address: String): String =
    com.anindra.messages.data.AddressIdentity.canonical(
        address, com.anindra.messages.data.PhoneNumberUtils.region()
    )

/** True for actual phone/short-code numbers; false for alphanumeric sender IDs (DK-AIRCEL, VM-HDFCBK…). */
fun isPhoneNumber(address: String): Boolean =
    com.anindra.messages.data.AddressIdentity.isReplyable(address)

fun openUrl(context: android.content.Context, url: String) {
    val target = if (url.startsWith("http://") || url.startsWith("https://")) url else return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
    } catch (_: Exception) {
    }
}

@Composable
private fun rememberLinkedText(
    body: String,
    highlight: Boolean,
    hide: Boolean = false,
    onLinkClick: (String) -> Unit = {},
    textColor: Color = MaterialTheme.colorScheme.onSurface
): AnnotatedString {
    val linkColor = if (highlight) textColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
    // produceState remembers its value WITHOUT keys, so an async redaction would
    // keep painting the previous (unredacted) text and only swap it once the
    // coroutine lands — every link bubble flashes its URL when the option is
    // turned on. Hiding removes content, so resolve it before the first paint;
    // hideUrls memoizes, so this is a cache hit on recomposition.
    if (hide) {
        return remember(body) {
            styledBody(hideUrls(body), linkColor, emptyMap(), null)
        }
    }
    return produceState(AnnotatedString(body), body, highlight, textColor, linkColor) {
        value = withContext(Dispatchers.Default) {
            val urls = if (highlight) {
                val spanned = SpannableStringBuilder(body)
                Linkify.addLinks(spanned, Linkify.WEB_URLS)
                spanned.getSpans(0, spanned.length, URLSpan::class.java)
                    .associate {
                        (spanned.getSpanStart(it)..spanned.getSpanEnd(it) - 1) to it.url
                    }
            } else emptyMap()
            styledBody(body, linkColor, urls, onLinkClick)
        }
    }.value
}

/** Builds the styled bubble body, isolating number runs so they keep LTR order
 *  inside RTL messages while links/OTP styling offsets are remapped. */
private fun styledBody(
    body: String,
    linkColor: Color,
    urls: Map<IntRange, String>,
    onLinkClick: ((String) -> Unit)?
): AnnotatedString {
    val iso = BidiText.isolateNumberRuns(body, urls.keys.toList())
    val out = AnnotatedString.Builder(iso.text)
    urls.forEach { (original, url) ->
        val range = iso.remap(original)
        out.addLink(
            LinkAnnotation.Url(
                url = url,
                linkInteractionListener = { link ->
                    onLinkClick?.invoke((link as LinkAnnotation.Url).url)
                }
            ),
            range.first, range.last + 1
        )
        out.addStyle(SpanStyle(color = linkColor), range.first, range.last + 1)
        out.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), range.first, range.last + 1)
    }
    OtpDetector.findRanges(body)
        .filter { r -> urls.keys.none { s -> r.first >= s.first && r.last <= s.last } }
        .forEach { r ->
            val range = iso.remap(r)
            out.addStyle(SpanStyle(color = linkColor), range.first, range.last + 1)
            out.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), range.first, range.last + 1)
        }
    return out.toAnnotatedString()
}


@Composable
private fun LinkWarningDialog(url: String, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
        title = { Text(stringResource(R.string.chat_link_caution)) },
        text = {
            Column {
                Text(stringResource(R.string.chat_link_warning))
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.chat_open)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText(context.getString(R.string.chat_link_label), url))
                    Toast.makeText(context, context.getString(R.string.chat_link_copied), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) { Text(stringResource(R.string.chat_copy_link)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
            }
        }
    )
}

@Composable
private fun MessageDetailsDialog(
    message: Message,
    address: String,
    onDismiss: () -> Unit
) {
    val kind = MessageDetails.kind(message.transport)
    val toSelf = MessageDetails.direction(message.isMe) == MessageDetails.Direction.TO
    val context = LocalContext.current
    val is24Hour = is24HourFormat(context)
    val statusLabel = when (MessageDetails.status(message.status)) {
        MessageDetails.Status.SENDING -> stringResource(R.string.message_status_sending)
        MessageDetails.Status.DELIVERED -> stringResource(R.string.message_status_delivered)
        MessageDetails.Status.RECEIVED -> stringResource(R.string.message_status_received)
        MessageDetails.Status.FAILED -> stringResource(R.string.message_status_failed)
        MessageDetails.Status.SENT -> stringResource(R.string.message_status_sent)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.message_details_title)) },
        text = {
            Column {
                DetailRow(stringResource(R.string.message_detail_type), MessageDetails.kindLabel(kind))
                DetailRow(
                    stringResource(if (toSelf) R.string.message_detail_to else R.string.message_detail_from),
                    formatPhoneNumber(address)
                )
                DetailRow(stringResource(R.string.message_detail_sent), formatDateTime(message.timestamp, "MMM d, yyyy,", is24Hour))
                MessageDetails.deliveredAt(message)?.let {
                    DetailRow(stringResource(R.string.message_detail_delivered), formatDateTime(it, "MMM d, yyyy,", is24Hour))
                }
                DetailRow(stringResource(R.string.message_detail_status), statusLabel)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_close)) }
        }
    )
}

@Composable
private fun ConversationDetailsDialog(
    address: String,
    name: String?,
    messageCount: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_details_title)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(address, size = 48.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        if (!name.isNullOrBlank() && name != address) {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            formatPhoneNumber(address),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                DetailRow(stringResource(R.string.detail_phone_number), formatPhoneNumber(address))
                DetailRow(stringResource(R.string.detail_messages), "$messageCount")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_close)) }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
    msg: Message,
    showDividerBefore: Boolean,
    showStatus: Boolean,
    deliveryReports: Boolean,
    forwardingEnabled: Boolean = false,
    onRetry: () -> Unit = {},
    onLongPress: () -> Unit = {},
    highlightLinks: Boolean = false,
    linkWarningEnabled: Boolean = true,
    hideLinks: Boolean = false,
    onLockUnlock: (Boolean) -> Unit = {},
    onDelete: () -> Unit = {},
    isUnlocked: Boolean = false,
    showSimIndicator: Boolean = true,
    isSelected: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    val isLockedAndHidden = msg.locked && !isUnlocked
    val displayBody = if (isLockedAndHidden) "@Lock" else msg.body
    val bodyText = rememberLinkedText(
        displayBody,
        highlightLinks && !isLockedAndHidden,
        hideLinks && !isLockedAndHidden,
        onLinkClick = { pendingUrl = it },
        textColor = if (isSelected) cs.onSelectedBubble else if (msg.isMe) cs.onPrimaryContainer else cs.onSurface
    )

    // cache derived text/sim so an unlock doesn't recompute row allocations
    val dividerText = remember(msg.timestamp) { formatDividerTime(msg.timestamp, context) }
    val timeText = remember(msg.timestamp) { formatTimeOnly(msg.timestamp, is24HourFormat(context)) }
    val simLabel = remember(msg.subId, showSimIndicator) {
        if (showSimIndicator && msg.subId > 0) {
            try {
                val slotIndex = SubscriptionManager.getSlotIndex(msg.subId)
                if (slotIndex >= 0) String.format(context.getString(R.string.sim_slot_suffix), slotIndex + 1) else ""
            } catch (_: Exception) { "" }
        } else ""
    }

    if (showDividerBefore) {
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), Alignment.Center) {
            Text(
                dividerText,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
    }

    pendingUrl?.let { url ->
        if (linkWarningEnabled) {
            LinkWarningDialog(
                url = url,
                onDismiss = { pendingUrl = null },
                onOpen = {
                    pendingUrl = null
                    openUrl(context, url)
                }
            )
        } else {
            pendingUrl = null
            openUrl(context, url)
        }
    }

    Column(
        horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            if (msg.mediaType == "image" && msg.mediaUri.isNotBlank()) {
                Column(
                    horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { onLongPress() }
                    )
                ) {
                    ImageBubble(uri = msg.mediaUri, isMe = msg.isMe)
                    if (msg.body.isNotBlank()) {
                        Surface(
                            color = if (isSelected) cs.selectedBubble else if (msg.isMe) cs.outgoingBubble else cs.incomingBubble,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.widthIn(max = 260.dp).padding(top = 2.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { onLongPress() }
                                )
                        ) {
                            Text(
                                text = bodyText,
                                style = MaterialTheme.typography.bodyLarge.merge(
                                    TextStyle(color = if (isSelected) cs.onSelectedBubble else if (msg.isMe) cs.onPrimaryContainer else cs.onSurface)
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            } else {
                Surface(
                    color = if (isSelected) cs.selectedBubble else if (msg.isMe) cs.outgoingBubble else cs.incomingBubble,
                    shape = RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (msg.isMe) 18.dp else 4.dp,
                        bottomEnd = if (msg.isMe) 4.dp else 18.dp
                    ),
                    modifier = Modifier.widthIn(max = 300.dp).combinedClickable(
                        onClick = {},
                        onLongClick = { onLongPress() }
                    )
                ) {
                    Text(
                        text = bodyText,
                        style = MaterialTheme.typography.bodyLarge.merge(
                            TextStyle(color = if (isSelected) cs.onSelectedBubble else if (msg.isMe) cs.onPrimaryContainer else cs.onSurface)
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        val simLabelFinal = simLabel

        if (msg.status == "failed" && msg.isMe) {
            Row(
                modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Not sent",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.error
                )
                Text(
                    " · ",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.error
                )
                Text(
                    stringResource(R.string.chat_status_retry),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.error,
                    modifier = Modifier.clickable { onRetry() }
                )
            }
        } else {
            val statusText = if (msg.isMe) {
                when {
                    showStatus && deliveryReports && msg.status == "delivered" -> stringResource(R.string.status_delivered)
                    showStatus && msg.status == "sending" -> "Sending…"
                    else -> "SMS"
                }
            } else ""
            val prefix = if (statusText.isNotEmpty()) " • $statusText" else ""
            Text(
                text = timeText + prefix + simLabelFinal,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(
                    top = 2.dp,
                    start = if (!msg.isMe) 4.dp else 0.dp,
                    end = if (msg.isMe) 4.dp else 0.dp
                )
            )
        }
    }
}

@Composable
private fun ImageBubble(uri: String, isMe: Boolean) {
    coil3.compose.AsyncImage(
        model = uri,
        contentDescription = stringResource(R.string.access_photo),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .widthIn(max = 260.dp)
            .heightIn(max = 300.dp)
            .clip(RoundedCornerShape(16.dp))
    )
}

/** Google-Messages-like input bar: pill field with emoji toggle + circular send. */
@Composable
private fun AlphanumericNotice(address: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.chat_alphanumeric_notice, address),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InputBar(
    draft: String,
    placeholder: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onSchedule: () -> Unit = {},
    onEmojiToggle: () -> Unit,
    onAttach: () -> Unit,
    showEmojiButton: Boolean = false,
    sims: List<SimCard> = emptyList(),
    currentSimId: Int = -1,
    onCycleSim: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.inputPill,
            modifier = Modifier.weight(1f)
        ) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text(placeholder) },
                leadingIcon = {
                    IconButton(onClick = onAttach) {
                        Icon(
                            Icons.Rounded.AddCircleOutline, stringResource(R.string.icon_attach),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Default,
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sims.size > 1 && draft.isBlank()) {
                            val currentIndex = sims.indexOfFirst { it.subscriptionId == currentSimId }
                            val iconRes = when (SimSwitcher.iconFor(sims.size, currentIndex)) {
                                SimIcon.SIM_2 -> R.drawable.ic_sim_2
                                SimIcon.DUAL -> R.drawable.ic_dual_sim
                                SimIcon.SIM_1 -> R.drawable.ic_sim_1
                            }
                            IconButton(onClick = onCycleSim, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    painterResource(iconRes),
                                    stringResource(R.string.chat_switch_sim),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (showEmojiButton) {
                            IconButton(onClick = onEmojiToggle, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Rounded.EmojiEmotions, stringResource(R.string.icon_emoji),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(8.dp))
        val canSend = draft.isNotBlank()
        Box(
            Modifier
                .size(48.dp)
                .background(
                    if (canSend) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    CircleShape
                )
                .combinedClickable(
                    enabled = canSend,
                    onClick = onSend,
                    onLongClick = onSchedule
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Send, stringResource(R.string.icon_send),
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (canSend) 1f else 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachSheet(
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                stringResource(R.string.chat_share),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                Modifier.fillMaxWidth().clickable { onGallery() }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Image, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.chat_media_gallery), style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                Modifier.fillMaxWidth().clickable { onCamera() }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CameraAlt, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.chat_media_camera), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SimPickerDialog(
    sims: List<SimCard>,
    currentSimId: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableIntStateOf(currentSimId) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_retry_sim)) },
        text = {
            Column {
                sims.forEach { sub ->
                    val carrier = sub.carrierName?.ifBlank { null }
                    val label =
                        if (carrier != null) String.format("%s · SIM %s", carrier, sub.slotIndex + 1) else String.format(context.getString(R.string.settings_sim_label), sub.slotIndex + 1)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selected = sub.subscriptionId }
                    ) {
                        RadioButton(
                            selected = selected == sub.subscriptionId,
                            onClick = { selected = sub.subscriptionId }
                        )
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selected) }) { Text(stringResource(R.string.chat_retry)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
        }
    )
}

@Composable
private fun ForwardPicker(
    contacts: List<Contact>,
    onPick: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = contacts.filter {
        it.name.contains(query, ignoreCase = true) || it.number.contains(query)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_forward_to)) },
        text = {
            Column {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.chat_search_contacts)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(filtered, key = { it.number }) { contact ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(contact.number, contact.name) }
                                .padding(vertical = 8.dp)
                        ) {
                            PersonAvatar(contact.number, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(contact.name, fontWeight = FontWeight.Medium)
                                    if (contact.workProfile) {
                                        Spacer(Modifier.width(6.dp))
                                        WorkProfileBadge()
                                    }
                                }
                                Text(
                                    contact.number,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
        }
    )
}
