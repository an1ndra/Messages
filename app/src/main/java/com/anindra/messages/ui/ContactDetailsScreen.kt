package com.anindra.messages.ui

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import com.anindra.messages.R
import com.anindra.messages.AppViewModel
import kotlinx.coroutines.withContext
import com.anindra.messages.data.SavedContact
import com.anindra.messages.data.ContactLookup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailsScreen(
    vm: AppViewModel,
    conversationId: Long,
onBack: () -> Unit,
    onAddPeople: () -> Unit = {},
    scrollState: ScrollState = rememberScrollState()
) {
    val context = LocalContext.current
    val convo by vm.conversationById(conversationId).collectAsState(initial = null)
    val vmContacts by remember(vm) { vm.contacts }.collectAsState(initial = emptyList())
    val workNums = remember(vmContacts) {
        vmContacts.filter { it.workProfile }.map { phoneKey(it.number) }.toSet()
    }
    if (convo == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        return
    }
    val address = convo!!.address
    val name = convo!!.name
    val display = BidiText.ltr(convo!!.display)
    val workProfile = phoneKey(address).let { it.isNotEmpty() && it in workNums }

    // flows (not sync SELECTs) for notify/block state; VM retains last value
    val notificationsEnabled by vm.conversationNotificationsEnabledFlow(conversationId)
        .collectAsState(initial = true)
    var notifState by remember(notificationsEnabled) { mutableStateOf(notificationsEnabled) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var numberIsBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(address) { numberIsBlocked = vm.isNumberBlocked(address) }

    val membershipRevision by vm.membershipRevision.collectAsState()
    var recipients by remember(address) { mutableStateOf(listOf(address)) }
    LaunchedEffect(conversationId, membershipRevision) {
        recipients = vm.conversationRecipients(conversationId).ifEmpty { listOf(address) }
    }
    val isGroup = recipients.size > 1
    val groupTitle = if (isGroup) {
        vm.conversationGroupTitle(conversationId)
            .ifBlank { ContactDetails.title(name, address, display) }
    } else {
        ContactDetails.title(name, address, display)
    }

    // Edited in place. The pencil sits below the name rather than beside it: a
    // long group name would read an icon next to it as part of the name.
    var renaming by remember { mutableStateOf(false) }
    // The caret lands at the end, not the start: the name here is a generated
    // default the user almost always replaces wholesale, and offset 0 means
    // typing inserts into the middle of it.
    var draft by remember { mutableStateOf(TextFieldValue("")) }

    // Already in Contacts? Then the action opens that person instead of
    // offering to add them a second time.
    var savedContact by remember(address) { mutableStateOf<SavedContact?>(null) }
    var contactsLoaded by remember(address) { mutableStateOf(false) }
    LaunchedEffect(address) {
        savedContact = withContext(Dispatchers.IO) { ContactLookup.find(context, address) }
        contactsLoaded = true
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.icon_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(8.dp))

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PersonAvatar(address, size = 96.dp)

                Spacer(Modifier.height(12.dp))

                // Inline editing: the name turns into a field in place rather than
                // opening a dialog over the screen.
                if (renaming) {
                    val focusRequester = remember { FocusRequester() }
                    // onFocusChanged emits an initial isFocused=false the moment
                    // the field composes, before requestFocus() has run. Committing
                    // on that would close the field the instant it opened, so only
                    // a blur after it has genuinely held focus counts.
                    var hadFocus by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    fun commit() {
                        if (!renaming) return
                        renaming = false
                        // Blank clears the override, restoring the derived name.
                        vm.setGroupTitle(conversationId, draft.text)
                    }
                    // Borderless and centred: an outlined box in the middle of the
                    // header reads as a separate dialog-ish thing, and a
                    // left-aligned name would jump when the field takes over.
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                if (it.isFocused) hadFocus = true else if (hadFocus) commit()
                            }
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = groupTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            textDecoration = if (isGroup) TextDecoration.Underline else null
                        )
                        // Only a group has an editable name; a 1:1's title is the
                        // contact's own name and is not ours to change.
                        if (isGroup) {
                            IconButton(onClick = {
                                draft = TextFieldValue(groupTitle, TextRange(groupTitle.length))
                                renaming = true
                            }) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    stringResource(R.string.contact_group_name),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                // A group has no single number to show. One member's number under a
                // group name reads as though the whole thread belongs to them.
                if (!isGroup) {
                    ContactDetails.subtitle(name, address, display)?.let { number ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = number,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (workProfile) {
                    Spacer(Modifier.height(4.dp))
                    WorkProfileBadge()
                }

                Spacer(Modifier.height(16.dp))

                // A group has no single person to call, view or block: the thread is
                // the unit, not any one member. Those actions belong to a 1:1.
                if (!isGroup) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DetailActionButton(
                            icon = Icons.Rounded.Call,
                            label = stringResource(R.string.action_call),
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$address"))
                                )
                            }
                        )
                        Spacer(Modifier.width(32.dp))
                        // Saved contacts get a plain profile icon and an "Info"
                        // label, matching Google Messages: the person already
                        // exists, so there is nothing to add.
                        if (contactsLoaded && savedContact != null) {
                            DetailActionButton(
                                icon = Icons.Rounded.Person,
                                label = stringResource(R.string.action_info),
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, savedContact!!.viewUri())
                                    )
                                }
                            )
                        } else {
                            DetailActionButton(
                                // Same plain profile icon as the saved case: only the
                                // label says whether this adds or opens the contact.
                                icon = Icons.Rounded.Person,
                                label = stringResource(R.string.action_contact),
                                onClick = {
                                    context.startActivity(
                                        Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                            type = ContactsContract.RawContacts.CONTENT_TYPE
                                            putExtra(ContactsContract.Intents.Insert.PHONE, address)
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // One card, not two: notifications and blocking are the same block of
            // per-conversation controls, split by a hairline like Google's.
            GroupedRowList(Modifier.padding(horizontal = SettingsLayout.SCREEN_PADDING)) {
                DetailCardRow(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(R.string.contact_notifications),
                    trailing = {
                        Switch(
                            checked = notifState,
                            onCheckedChange = {
                                notifState = it
                                vm.setConversationNotificationsEnabled(conversationId, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                )
                // Blocking one member would block the whole thread, so a group has
                // nothing here to block.
                if (!isGroup) {
                    DetailCardRow(
                        icon = Icons.Rounded.Block,
                        title = stringResource(R.string.contact_block_report),
                        titleColor = MaterialTheme.colorScheme.error,
                        iconColor = MaterialTheme.colorScheme.error,
                        onClick = { showBlockDialog = true }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // The participant count and the participant list are one block too.
            GroupedRowList(Modifier.padding(horizontal = SettingsLayout.SCREEN_PADDING)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = SettingsLayout.ROW_CONTENT_PADDING,
                            vertical = 12.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isGroup) {
                            context.getString(R.string.contact_people_count, recipients.size)
                        } else {
                            stringResource(R.string.contact_one_person)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onAddPeople() }
                    ) {
                        Icon(
                            Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.contact_add_people),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                    // A group lists who is in it. A 1:1 does not: the single member
                    // is already named and numbered above, so listing them again
                    // was pure duplication. This also carries the only UI for
                    // removeParticipant.
                    if (isGroup) {
                        recipients.forEachIndexed { index, member ->
                            val memberName = vm.contactNameFor(member)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = SettingsLayout.ROW_CONTENT_PADDING,
                                        vertical = 12.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PersonAvatar(member, size = 40.dp)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        memberName ?: formatPhoneNumber(member),
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (memberName != null) {
                                        Text(
                                            formatPhoneNumber(member),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
        }
        // The last recipient is the conversation itself, so
        // it cannot be removed.
        if (index > 0) {
            IconButton(onClick = { vm.removeParticipant(conversationId, member) }) {
                Icon(
                    Icons.Rounded.Close,
                    stringResource(R.string.contact_remove_people),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
                    }
                }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text(stringResource(R.string.contact_block_report)) },
            text = { Text(context.getString(R.string.contact_block_confirm, formatPhoneNumber(address))) },
            confirmButton = {
                TextButton(onClick = {
                    showBlockDialog = false
                    vm.blockNumber(address)
                    numberIsBlocked = true
                }) { Text(stringResource(R.string.contact_block)) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
internal fun DetailActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun DetailCardRow(
    icon: ImageVector,
    title: String,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    iconColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = iconColor
        )
        Spacer(Modifier.width(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}
