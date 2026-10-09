package com.anindra.messages.ui.legacy

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.anindra.messages.R
import androidx.compose.ui.res.painterResource
import com.anindra.messages.AppViewModel
import com.anindra.messages.ui.PersonAvatar
import com.anindra.messages.ui.BidiText
import com.anindra.messages.ui.formatPhoneNumber
import com.anindra.messages.ui.phoneKey
import com.anindra.messages.ui.WorkProfileBadge
import com.anindra.messages.ui.ContactDetails
import com.anindra.messages.data.ContactLookup
import com.anindra.messages.data.SavedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailsScreen(
    vm: AppViewModel,
    conversationId: Long,
    onBack: () -> Unit,
    onAddPeople: () -> Unit = {}
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

    // Already in Contacts? Then the action opens that person instead of
    // offering to add them a second time. The modern screen has had this since
    // it was written; the legacy one did not, so it always offered "Add".
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
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PersonAvatar(address, size = 96.dp)

                Spacer(Modifier.height(12.dp))

                Text(
                    text = ContactDetails.title(name, address, display),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically)
                )
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
                if (workProfile) {
                    Spacer(Modifier.height(4.dp))
                    WorkProfileBadge()
                }

                Spacer(Modifier.height(16.dp))

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
                            // Same plain profile icon as the saved case: only
                            // the label says whether this adds or opens them.
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

            Spacer(Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column {
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
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
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

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.contact_one_person),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            // Must add a member to *this* chat. This used to open
                            // the system "create new contact" intent — a different
                            // thing entirely — because the legacy screen was never
                            // given the callback the modern one uses.
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
                    // Commented out at the user's request, matching
                    // ContactDetailsScreen: the row just repeated the name and
                    // number already shown above.
                    // HorizontalDivider(
                    //     modifier = Modifier.padding(horizontal = 16.dp),
                    //     color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    // )
                    // Row(
                    //     Modifier
                    //         .fillMaxWidth()
                    //         .padding(horizontal = 16.dp, vertical = 12.dp),
                    //     verticalAlignment = Alignment.CenterVertically
                    // ) {
                    //     PersonAvatar(address, size = 40.dp)
                    //     Spacer(Modifier.width(16.dp))
                    //     Column(Modifier.weight(1f)) {
                    //         Row(verticalAlignment = Alignment.CenterVertically) {
                    //             Text(
                    //                 ContactDetails.title(name, address, display),
                    //                 style = MaterialTheme.typography.bodyLarge
                    //             )
                    //             if (workProfile) {
                    //                 Spacer(Modifier.width(6.dp))
                    //                 WorkProfileBadge()
                    //             }
                    //         }
                    //         ContactDetails.subtitle(name, address, display)?.let { number ->
                    //             Text(
                    //                 number,
                    //                 style = MaterialTheme.typography.bodyMedium,
                    //                 color = MaterialTheme.colorScheme.onSurfaceVariant
                    //             )
                    //         }
                    //     }
                    // }
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
