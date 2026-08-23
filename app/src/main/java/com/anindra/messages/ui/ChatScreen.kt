package com.anindra.messages.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.widget.Toast
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.ui.res.painterResource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.anindra.messages.AppViewModel
import com.anindra.messages.R
import com.anindra.messages.data.Message
import com.anindra.messages.ui.theme.chatBar
import com.anindra.messages.ui.theme.incomingBubble
import com.anindra.messages.ui.theme.inputPill
import com.anindra.messages.ui.theme.outgoingBubble
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val EMOJIS = listOf("👍", "😂", "❤️", "🔥", "😢", "😮", "🙏", "🎉")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: AppViewModel,
    conversationId: Long,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val convo by vm.conversationById(conversationId).collectAsState(initial = null)
    val messages by vm.messages(conversationId).collectAsState(initial = emptyList())
    var draft by remember { mutableStateOf("") }
    var draftLoaded by remember { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var bannerDismissed by remember { mutableStateOf(false) }
    var bannerVisible by remember { mutableStateOf(false) }
    var attachSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    var cameraFileUri by remember { mutableStateOf<Uri?>(null) }

    var numberIsBlocked by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showAlphanumericDialog by remember { mutableStateOf(false) }

    var forwardingMessageId by remember { mutableStateOf(-1L) }
    var showForwardPicker by remember { mutableStateOf(false) }

    var pendingSendText by remember { mutableStateOf("") }
    var sendCountdown by remember { mutableIntStateOf(0) }

    var showSchedulePicker by remember { mutableStateOf(false) }
    var scheduleStep by remember { mutableStateOf("date") }
    var scheduledDateMillis by remember { mutableStateOf<Long?>(null) }
    var scheduledHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var scheduledMinute by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        bannerVisible = true
    }
    var sendCountdownJob by remember { mutableStateOf<Job?>(null) }

    // SIM management
    var currentSimId by remember { mutableIntStateOf(vm.settings.simSubscriptionId) }
    var sims by remember { mutableStateOf(emptyList<SubscriptionInfo>()) }
    var retryingMessageId by remember { mutableStateOf(-1L) }
    var showRetrySimPicker by remember { mutableStateOf(false) }

    fun cycleSim() {
        if (sims.size < 2) return
        val idx = sims.indexOfFirst { it.subscriptionId == currentSimId }
        val next = sims[(idx + 1) % sims.size]
        currentSimId = next.subscriptionId
        vm.settings.simSubscriptionId = next.subscriptionId
        val carrier = next.carrierName?.toString()?.ifBlank { null }
        val label = if (carrier != null) "$carrier · SIM ${next.simSlotIndex + 1}" else "SIM ${next.simSlotIndex + 1}"
        Toast.makeText(context, "Sending via $label", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        try {
            val sm = context.getSystemService(SubscriptionManager::class.java)
            sims = sm?.activeSubscriptionInfoList?.filter {
                it.simSlotIndex >= 0
            }?.sortedBy { it.simSlotIndex } ?: emptyList()
        } catch (_: SecurityException) {
            sims = emptyList()
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
        vm.saveDraft(conversationId, draft.trim())
        onBack()
    }
    BackHandler(onBack = ::leaveChat)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }
    LaunchedEffect(conversationId) {
        vm.markRead(conversationId)
        draftLoaded = false
    }
    LaunchedEffect(convo) {
        if (convo != null && !draftLoaded) {
            val savedDraft = convo!!.draft
            if (savedDraft.isNotBlank()) {
                draft = savedDraft
            }
            draftLoaded = true
            numberIsBlocked = vm.isNumberBlocked(convo!!.address)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.chatBar
                ),
                navigationIcon = {
                    IconButton(onClick = ::leaveChat) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
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
                            Text(
                                convo?.let {
                                    if (it.name != it.address) it.name
                                    else formatPhoneNumber(it.address)
                                } ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                            if (convo?.draft?.isNotBlank() == true && sendCountdown == 0) {
                                Text(
                                    "Draft",
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
                    }) { Icon(Icons.Rounded.Call, "Call") }

                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, "More options")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            containerColor = MaterialTheme.colorScheme.chatBar
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add people") },
                                onClick = {
                                    menuOpen = false
                                    convo?.address?.let {
                                        context.startActivity(
                                            Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                                type = ContactsContract.RawContacts.CONTENT_TYPE
                                                putExtra(ContactsContract.Intents.Insert.PHONE, it)
                                            })
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Details") },
                                onClick = {
                                    menuOpen = false
                                    onOpenDetails()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                onClick = { menuOpen = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    menuOpen = false
                                    vm.deleteConversation(conversationId)
                                    Toast.makeText(
                                        context, "Conversation moved to trash",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onBack()
                                }
                            )
                            if (vm.settings.blockingEnabled) {
                                if (numberIsBlocked) {
                                    DropdownMenuItem(
                                        text = { Text("Unblock number") },
                                        onClick = {
                                            menuOpen = false
                                            convo?.address?.let { addr ->
                                                vm.unblockNumber(addr)
                                                numberIsBlocked = false
                                                Toast.makeText(context, "Number unblocked", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Block number") },
                                        onClick = {
                                            menuOpen = false
                                            convo?.address?.let { addr ->
                                                vm.blockNumber(addr)
                                                numberIsBlocked = true
                                                Toast.makeText(context, "Number blocked", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.chatBar)
                    .navigationBarsPadding()
            ) {
                AnimatedVisibility(showEmoji) {
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
                InputBar(
                    draft = draft,
                    placeholder = "Text message",
                    simTrailing = {
                        if (sims.size > 1 && draft.isEmpty()) {
                            val simIdx = sims.indexOfFirst { it.subscriptionId == currentSimId }
                            val iconRes = when {
                                sims.size >= 3 -> R.drawable.ic_dual_sim
                                simIdx == 1 -> R.drawable.ic_sim_2
                                else -> R.drawable.ic_sim_1
                            }
                            IconButton(onClick = { cycleSim() }) {
                                Icon(
                                    painterResource(iconRes),
                                    contentDescription = "Switch SIM",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onDraftChange = { draft = it },
                    onSend = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            val addr = convo?.address ?: ""
                            if (vm.isNumberBlocked(addr)) {
                                showBlockedDialog = true
                                return@InputBar
                            }
                            if (!isPhoneNumber(addr)) {
                                showAlphanumericDialog = true
                                return@InputBar
                            }
                            if (vm.settings.delayedSendingEnabled) {
                                pendingSendText = text
                                sendCountdown = vm.settings.delaySeconds
                                sendCountdownJob?.cancel()
                                sendCountdownJob = scope.launch {
                                    for (i in sendCountdown downTo 1) {
                                        sendCountdown = i
                                        delay(1000L)
                                    }
                                    sendCountdown = 0
                                    val toSend = pendingSendText
                                    pendingSendText = ""
                                    vm.send(conversationId, toSend)
                                    vm.saveDraft(conversationId, "")
                                    draft = ""
                                    showEmoji = false
                                }
                            } else {
                                vm.send(conversationId, text)
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
                            if (!isPhoneNumber(addr)) {
                                showAlphanumericDialog = true
                                return@InputBar
                            }
                            scheduleStep = "date"
                            showSchedulePicker = true
                        }
                    },
                    onEmojiToggle = { showEmoji = !showEmoji },
                                        onAttach = {
                        val addr = convo?.address ?: ""
                        if (addr.isNotEmpty() && !isPhoneNumber(addr)) {
                            showAlphanumericDialog = true
                        } else {
                            attachSheet = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val idx = messages.indexOfFirst { it.id == msg.id }
                    MessageRow(
                        msg = msg,
                        showDividerBefore = idx == 0 || !sameDay(messages[idx - 1].timestamp, msg.timestamp),
                        showStatus = idx == messages.lastIndex && msg.isMe,
                        deliveryReports = vm.deliveryReportsEnabled(),
                        onRetry = {
                            if (sims.size > 1) {
                                retryingMessageId = msg.id
                                showRetrySimPicker = true
                            } else {
                                vm.retryMessage(msg.id)
                            }
                        },
                        onLongPress = {
                            if (vm.settings.forwardingEnabled) {
                                forwardingMessageId = msg.id
                                showForwardPicker = true
                            }
                        },
                        highlightLinks = vm.settings.highlightLinks
                    )
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
                            "Sending in $sendCountdown seconds...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        TextButton(onClick = {
                            sendCountdownJob?.cancel()
                            sendCountdownJob = null
                            sendCountdown = 0
                            pendingSendText = ""
                        }) {
                            Text("Cancel")
                        }
                    }
                }
            }

            val c = convo
            AnimatedVisibility(
                visible = c != null && c.name == c.address && isPhoneNumber(c.address) &&
                    !bannerDismissed && bannerVisible,
                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp),
                enter = androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + androidx.compose.animation.slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = androidx.compose.animation.core.tween(300)
                )
            ) {
                SaveContactBanner(
                    address = c?.address ?: "",
                    onDismiss = { bannerDismissed = true },
                    onAddContact = {
                        bannerDismissed = true
                        c?.address?.let { addr ->
                            context.startActivity(Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                type = ContactsContract.RawContacts.CONTENT_TYPE
                                putExtra(ContactsContract.Intents.Insert.PHONE, addr)
                            })
                        }
                    }
                )
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
            title = { Text("Number blocked") },
            text = { Text("This number is blocked. Unblock to send?") },
            confirmButton = {
                TextButton(onClick = {
                    showBlockedDialog = false
                    convo?.address?.let { addr ->
                        vm.unblockNumber(addr)
                        numberIsBlocked = false
                    }
                }) { Text("Unblock") }
            },
            dismissButton = {
                TextButton(onClick = { showBlockedDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAlphanumericDialog) {
        AlertDialog(
            onDismissRequest = { showAlphanumericDialog = false },
            title = { Text("Can't send message") },
            text = {
                Text(
                    "You can't send messages to alphanumeric senders like " +
                        "\u201C${convo?.address ?: ""}\u201D. Only phone numbers are supported."
                )
            },
            confirmButton = {
                TextButton(onClick = { showAlphanumericDialog = false }) { Text("OK") }
            }
        )
    }

    if (showForwardPicker) {
        ForwardPicker(
            onPick = { address, name ->
                showForwardPicker = false
                vm.openOrCreate(address, name) { targetId ->
                    vm.forwardMessage(forwardingMessageId, targetId)
                    forwardingMessageId = -1
                    Toast.makeText(context, "Message forwarded", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = {
                showForwardPicker = false
                forwardingMessageId = -1
            }
        )
    }

    if (showSchedulePicker) {
        if (scheduleStep == "date") {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = scheduledDateMillis ?: System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showSchedulePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        scheduledDateMillis = datePickerState.selectedDateMillis
                        scheduleStep = "time"
                    }) { Text("Next") }
                },
                dismissButton = {
                    TextButton(onClick = { showSchedulePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        } else if (scheduleStep == "time") {
            val timePickerState = rememberTimePickerState(
                initialHour = scheduledHour,
                initialMinute = scheduledMinute
            )
            AlertDialog(
                onDismissRequest = { showSchedulePicker = false },
                title = { Text("Select time") },
                text = { TimePicker(state = timePickerState) },
                confirmButton = {
                    TextButton(onClick = {
                        scheduledHour = timePickerState.hour
                        scheduledMinute = timePickerState.minute
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = scheduledDateMillis ?: System.currentTimeMillis()
                            set(Calendar.HOUR_OF_DAY, scheduledHour)
                            set(Calendar.MINUTE, scheduledMinute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val ts = cal.timeInMillis
                        val text = draft.trim()
                        val addr = convo?.address ?: return@TextButton
                        vm.scheduleMessage(addr, text, ts, conversationId)
                        val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                        Toast.makeText(context, "Scheduled for ${fmt.format(Date(ts))}", Toast.LENGTH_SHORT).show()
                        vm.saveDraft(conversationId, "")
                        draft = ""
                        showEmoji = false
                        showSchedulePicker = false
                    }) { Text("Schedule") }
                },
                dismissButton = {
                    TextButton(onClick = { showSchedulePicker = false }) { Text("Cancel") }
                }
            )
        }
    }
}

fun formatPhoneNumber(raw: String): String = try {
    android.telephony.PhoneNumberUtils.formatNumber(raw) ?: raw
} catch (_: Exception) {
    raw
}

/** True for actual phone/short-code numbers; false for alphanumeric sender IDs (DK-AIRCEL, VM-HDFCBK…). */
fun isPhoneNumber(address: String): Boolean =
    address.count { it.isDigit() } >= 4 && address.all { it.isDigit() || it == '+' }

fun openUrl(context: android.content.Context, url: String) {
    val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "http://$url"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
    } catch (_: Exception) {
    }
}

@Composable
private fun rememberLinkedText(body: String, highlight: Boolean): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(body, highlight) {
        if (!highlight) {
            AnnotatedString(body)
        } else {
            val spanned = SpannableStringBuilder(body)
            val found = Linkify.addLinks(spanned, Linkify.WEB_URLS)
            if (!found) {
                AnnotatedString(body)
            } else {
                val builder = AnnotatedString.Builder(body)
                spanned.getSpans(0, spanned.length, URLSpan::class.java).forEach { span ->
                    val start = spanned.getSpanStart(span)
                    val end = spanned.getSpanEnd(span)
                    builder.addLink(LinkAnnotation.Url(span.url), start, end)
                    builder.addStyle(SpanStyle(color = linkColor), start, end)
                    builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                }
                builder.toAnnotatedString()
            }
        }
    }
}

@Composable
private fun SaveContactBanner(address: String, onDismiss: () -> Unit, onAddContact: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().alpha(0.78f).padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PersonAddAlt1,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Save ${formatPhoneNumber(address)}?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Saving this number will add a new contact",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Report spam") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onAddContact) { Text("Add contact") }
            }
        }
    }
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
        title = { Text("Conversation details") },
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
                DetailRow("Phone number", formatPhoneNumber(address))
                DetailRow("Messages", "$messageCount")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
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
    onRetry: () -> Unit = {},
    onLongPress: () -> Unit = {},
    highlightLinks: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }
    val bodyText = rememberLinkedText(msg.body, highlightLinks)

    if (showDividerBefore) {
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), Alignment.Center) {
            Text(
                formatDividerTime(msg.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
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
                        onLongClick = { showContextMenu = true }
                    )
                ) {
                    ImageBubble(uri = msg.mediaUri, isMe = msg.isMe)
                    if (msg.body.isNotBlank()) {
                        Surface(
                            color = if (msg.isMe) cs.outgoingBubble else cs.incomingBubble,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.widthIn(max = 260.dp).padding(top = 2.dp)
                        ) {
                            ClickableText(
                                text = bodyText,
                                style = MaterialTheme.typography.bodyLarge.merge(
                                    TextStyle(color = if (msg.isMe) cs.onPrimaryContainer else cs.onSurface)
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                onClick = { offset ->
                                    bodyText.getLinkAnnotations(offset, offset + 1).firstOrNull()
                                        ?.let { it.item as? LinkAnnotation.Url }
                                        ?.let { openUrl(context, it.url) }
                                }
                            )
                        }
                    }
                }
            } else {
                Surface(
                    color = if (msg.isMe) cs.outgoingBubble else cs.incomingBubble,
                    shape = RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (msg.isMe) 18.dp else 4.dp,
                        bottomEnd = if (msg.isMe) 4.dp else 18.dp
                    ),
                    modifier = Modifier.widthIn(max = 300.dp).combinedClickable(
                        onClick = {},
                        onLongClick = { showContextMenu = true }
                    )
                ) {
                    ClickableText(
                        text = bodyText,
                        style = MaterialTheme.typography.bodyLarge.merge(
                            TextStyle(color = if (msg.isMe) cs.onPrimaryContainer else cs.onSurface)
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        onClick = { offset ->
                            bodyText.getLinkAnnotations(offset, offset + 1).firstOrNull()
                                ?.let { it.item as? LinkAnnotation.Url }
                                ?.let { openUrl(context, it.url) }
                        }
                    )
                }
            }

            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
                        showContextMenu = false
                        val clipboard = android.content.Context.CLIPBOARD_SERVICE
                        val clip = android.content.ClipData.newPlainText("message", msg.body)
                        (context.getSystemService(clipboard) as android.content.ClipboardManager).setPrimaryClip(clip)
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Forward") },
                    onClick = {
                        showContextMenu = false
                        onLongPress()
                    }
                )
            }
        }

        if (msg.isMe && showStatus) {
            if (msg.status == "failed") {
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
                        "Tap to retry",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.error,
                        modifier = Modifier.clickable { onRetry() }
                    )
                }
            } else {
                Text(
                    text = formatTimeOnly(msg.timestamp) + " • " +
                            when {
                                deliveryReports && msg.status == "delivered" -> "Delivered"
                                msg.status == "sending" -> "Sending…"
                                else -> "SMS"
                            },
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ImageBubble(uri: String, isMe: Boolean) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        try {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (_: Exception) { null }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
        )
    }
}

/** Google-Messages-like input bar: pill field with emoji toggle + circular send. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InputBar(
    draft: String,
    placeholder: String,
    simTrailing: (@Composable () -> Unit)? = null,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onSchedule: () -> Unit = {},
    onEmojiToggle: () -> Unit,
    onAttach: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAttach) {
            Icon(
                Icons.Rounded.AddCircleOutline, "Attach",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.inputPill,
            modifier = Modifier.weight(1f)
        ) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text(placeholder) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Send
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = { onSend() }
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (simTrailing != null) simTrailing()
                        IconButton(onClick = onEmojiToggle) {
                            Icon(
                                Icons.Rounded.EmojiEmotions, "Emoji",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                Icons.AutoMirrored.Rounded.Send, "Send",
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
                "Share",
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
                Text("Gallery", style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                Modifier.fillMaxWidth().clickable { onCamera() }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CameraAlt, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text("Camera", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SimPickerDialog(
    sims: List<SubscriptionInfo>,
    currentSimId: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableIntStateOf(currentSimId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retry with SIM") },
        text = {
            Column {
                sims.forEach { sub ->
                    val carrier = sub.carrierName?.toString()?.ifBlank { null }
                    val label = if (carrier != null) "$carrier · SIM ${sub.simSlotIndex + 1}" else "SIM ${sub.simSlotIndex + 1}"
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
            TextButton(onClick = { onSelect(selected) }) { Text("Retry") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ForwardPicker(
    onPick: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val contacts = rememberContacts().filter {
        it.name.contains(query, ignoreCase = true) || it.number.contains(query)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward to") },
        text = {
            Column {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search contacts") },
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
                    items(contacts) { contact ->
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
                                Text(contact.name, fontWeight = FontWeight.Medium)
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
