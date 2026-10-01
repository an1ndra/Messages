package com.anindra.messages.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anindra.messages.AppViewModel
import com.anindra.messages.R
import com.anindra.messages.data.MmsVerdict
import com.anindra.messages.data.SettingsStore
import com.anindra.messages.data.SimMmsCheck
import com.anindra.messages.sms.NotificationHelper
import com.anindra.messages.sms.SimMmsProbe

private const val DAYS_TRASH = "trash"
private const val DAYS_SPAM = "spam"

/**
 * Sound and delivery options. This is where the notification *sound* lives, so
 * the picker has to stay reachable; it used to sit on General settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val revision by vm.settings.revision.collectAsState()

    var sendSound by remember(revision) { mutableStateOf(vm.settings.sendSoundEnabled) }
    var receiveSound by remember(revision) { mutableStateOf(vm.settings.receiveSoundEnabled) }
    var sound by remember(revision) { mutableStateOf(vm.settings.notificationSound) }
    var soundDialog by remember { mutableStateOf(false) }

    val options = listOf(
        SettingsStore.NOTIFY_SOUND_DEFAULT to context.getString(R.string.settings_sound_default),
        SettingsStore.NOTIFY_SOUND_APP to context.getString(R.string.settings_sound_classic),
        SettingsStore.NOTIFY_SOUND_DRAGON to context.getString(R.string.settings_sound_dragon),
        SettingsStore.NOTIFY_SOUND_UNIVERSFIELD_09 to context.getString(R.string.settings_sound_chime),
        SettingsStore.NOTIFY_SOUND_UNIVERSFIELD_062 to context.getString(R.string.settings_sound_bubble)
    )

    SettingsScaffold(
        title = stringResource(R.string.settings_notif_title),
        onBack = onBack
    ) {
        SettingsGroup {
            SettingsRow(
                position = RowPosition.FIRST,
                title = stringResource(R.string.settings_send_sound_title),
                checked = sendSound,
                onChecked = { sendSound = it; vm.settings.sendSoundEnabled = it }
            )
            SettingsRow(
                position = RowPosition.MIDDLE,
                title = stringResource(R.string.settings_receive_sound_title),
                checked = receiveSound,
                onChecked = {
                    receiveSound = it
                    vm.settings.receiveSoundEnabled = it
                    NotificationHelper.ensureChannel(context)
                }
            )
            SettingsRow(
                position = RowPosition.LAST,
                title = stringResource(R.string.settings_pin_notification_sound),
                subtitle = options.firstOrNull { it.first == sound }?.second
                    ?: context.getString(R.string.settings_sound_default),
                enabled = receiveSound,
                onClick = { soundDialog = true }
            )
        }

        SettingsFooter(stringResource(R.string.settings_notif_footer))
    }

    if (soundDialog) {
        AlertDialog(
            onDismissRequest = { soundDialog = false },
            title = { Text(stringResource(R.string.settings_pin_notification_sound)) },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = sound == value,
                                onClick = {
                                    sound = value
                                    NotificationHelper.previewNotificationSound(context, value)
                                }
                            )
                            Spacer(Modifier.padding(start = 8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.settings.notificationSound = sound
                    NotificationHelper.ensureChannel(context)
                    soundDialog = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { soundDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** Auto-delete master switch, what it purges, and how long each bucket is kept. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDeleteSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val revision by vm.settings.revision.collectAsState()

    var retentionOn by remember(revision) { mutableStateOf(vm.settings.retentionEnabled) }
    var retentionTrash by remember(revision) { mutableStateOf(vm.settings.retentionTrash) }
    var retentionKeyword by remember(revision) { mutableStateOf(vm.settings.retentionKeywordMessages) }
    var retentionBlocked by remember(revision) { mutableStateOf(vm.settings.retentionBlockedSenders) }
    var trashDays by remember(revision) { mutableIntStateOf(vm.settings.retentionTrashDays) }
    var spamDays by remember(revision) { mutableIntStateOf(vm.settings.retentionSpamDays) }
    var permanentDelete by remember(revision) { mutableStateOf(vm.settings.permanentDeleteEnabled) }
    var daysDialog by remember { mutableStateOf(false) }
    var daysTarget by remember { mutableStateOf(DAYS_TRASH) }

    SettingsScaffold(
        title = stringResource(R.string.settings_retention_title),
        onBack = onBack
    ) {
        SettingsGroup {
            SettingsRow(
                position = RowPosition.FIRST,
                title = stringResource(R.string.settings_retention_title),
                checked = retentionOn,
                onChecked = { retentionOn = it; vm.settings.retentionEnabled = it }
            )
            // Permanent delete is not a child of auto-delete: it changes where a
            // delete goes, not whether anything is deleted on a schedule, so it
            // stays available either way.
            SettingsRow(
                position = if (retentionOn) RowPosition.MIDDLE else RowPosition.LAST,
                title = stringResource(R.string.settings_advanced_permanent_delete),
                checked = permanentDelete,
                onChecked = {
                    permanentDelete = it
                    vm.settings.permanentDeleteEnabled = it
                    if (!it) vm.settings.permanentDeleteWarn = true
                }
            )
            // Which buckets auto-delete covers is meaningless while it is off, so
            // the whole run is hidden rather than greyed out. The footer already
            // says nothing is erased while it is off.
            if (retentionOn) {
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.settings_retention_trash),
                    checked = retentionTrash,
                    onChecked = { retentionTrash = it; vm.settings.retentionTrash = it }
                )
                if (retentionTrash) {
                    SettingsRow(
                        position = RowPosition.MIDDLE,
                        title = stringResource(R.string.settings_retention_keep_trash),
                        subtitle = context.getString(R.string.settings_retention_days, trashDays),
                        onClick = { daysDialog = true; daysTarget = DAYS_TRASH }
                    )
                }
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.settings_retention_keyword),
                    checked = retentionKeyword,
                    onChecked = { retentionKeyword = it; vm.settings.retentionKeywordMessages = it }
                )
                SettingsRow(
                    position = if (retentionBlocked) RowPosition.MIDDLE else RowPosition.LAST,
                    title = stringResource(R.string.settings_retention_blocked),
                    checked = retentionBlocked,
                    onChecked = { retentionBlocked = it; vm.settings.retentionBlockedSenders = it }
                )
                if (retentionBlocked) {
                    SettingsRow(
                        position = RowPosition.LAST,
                        title = stringResource(R.string.settings_retention_keep_spam),
                        subtitle = context.getString(R.string.settings_retention_days, spamDays),
                        onClick = { daysDialog = true; daysTarget = DAYS_SPAM }
                    )
                }
            }
        }

        SettingsFooter(stringResource(R.string.settings_auto_delete_footer))
    }

    if (daysDialog) {
        val editingTrash = daysTarget == DAYS_TRASH
        val current = if (editingTrash) trashDays else spamDays
        AlertDialog(
            onDismissRequest = { daysDialog = false },
            title = {
                Text(
                    stringResource(
                        if (editingTrash) R.string.settings_retention_keep_trash
                        else R.string.settings_retention_keep_spam
                    )
                )
            },
            text = {
                Column {
                    listOf(7, 30, 90, 180, 365).forEach { days ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = current == days,
                                onClick = {
                                    if (editingTrash) {
                                        trashDays = days; vm.settings.retentionTrashDays = days
                                    } else {
                                        spamDays = days; vm.settings.retentionSpamDays = days
                                    }
                                    daysDialog = false
                                }
                            )
                            Spacer(Modifier.padding(start = 8.dp))
                            Text(context.getString(R.string.settings_retention_days, days))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { daysDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** How links behave when tapped, and whether delete is reversible. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val revision by vm.settings.revision.collectAsState()

    var hideLinks by remember(revision) { mutableStateOf(vm.settings.hideLinks) }
    var highlightLinks by remember(revision) { mutableStateOf(vm.settings.highlightLinks) }
    var linkWarning by remember(revision) { mutableStateOf(vm.settings.linkOpenWarningEnabled) }
    var confirmDialog by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.settings_link_behaviour_title),
        onBack = onBack
    ) {
        SettingsGroup {
            SettingsRow(
                position = RowPosition.FIRST,
                title = stringResource(R.string.settings_advanced_hide_links),
                checked = hideLinks,
                onChecked = { hideLinks = it; vm.settings.hideLinks = it }
            )
            SettingsRow(
                position = if (hideLinks) RowPosition.MIDDLE else RowPosition.LAST,
                title = stringResource(R.string.settings_advanced_highlight_links),
                subtitle = if (hideLinks) {
                    context.getString(R.string.settings_advanced_turn_off) +
                        context.getString(R.string.settings_advanced_hide_links) +
                        context.getString(R.string.settings_advanced_turn_off_suffix)
                } else {
                    context.getString(R.string.settings_link_tap_info)
                },
                checked = highlightLinks,
                enabled = !hideLinks,
                onChecked = { highlightLinks = it; vm.settings.highlightLinks = it }
            )
            SettingsRow(
                position = RowPosition.LAST,
                title = stringResource(R.string.settings_advanced_link_warning),
                subtitle = when {
                    hideLinks -> context.getString(R.string.settings_advanced_turn_off) +
                        context.getString(R.string.settings_advanced_hide_links) +
                        context.getString(R.string.settings_advanced_turn_off_suffix)
                    !highlightLinks -> context.getString(R.string.settings_link_needs_highlight)
                    else -> context.getString(R.string.link_warning_confirm)
                },
                checked = linkWarning,
                enabled = !hideLinks && highlightLinks,
                onChecked = { linkWarning = it; vm.settings.linkOpenWarningEnabled = it }
            )
        }

        SettingsFooter(stringResource(R.string.settings_link_behaviour_footer))
    }

    if (confirmDialog) {
        PermanentDeleteConfirmDialog(
            onConfirm = { vm.settings.permanentDeleteEnabled = it; confirmDialog = false },
            onDismiss = { confirmDialog = false }
        )
    }
}

/**
 * Per-SIM MMS capability. The row above it promises a check, so the rows here
 * carry the verdict plus the country the SIM belongs to, and the footer says
 * where the answer came from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MmsSupportScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val checks = remember { SimMmsProbe.run(context) }

    SettingsScaffold(
        title = stringResource(R.string.mms_check_title),
        onBack = onBack
    ) {
        if (checks.isEmpty()) {
            SettingsGroup {
                SettingsRow(
                    position = RowPosition.SINGLE,
                    title = stringResource(R.string.mms_check_no_sims)
                )
            }
        } else {
            SettingsGroup {
                checks.forEachIndexed { index, check ->
                    SettingsRow(
                        position = when {
                            checks.size == 1 -> RowPosition.SINGLE
                            index == 0 -> RowPosition.FIRST
                            index == checks.lastIndex -> RowPosition.LAST
                            else -> RowPosition.MIDDLE
                        },
                        title = check.carrierName?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.settings_sim_unknown),
                        subtitle = mmsCheckSubtitle(check)
                    )
                }
            }
            SettingsFooter(
                stringResource(
                    R.string.mms_check_summary,
                    checks.count { it.supported },
                    checks.size
                )
            )
        }

        SettingsFooter(stringResource(R.string.mms_check_footer))
    }
}

@Composable
private fun mmsCheckSubtitle(check: SimMmsCheck): String {
    val verdict = stringResource(
        when (check.verdict) {
            MmsVerdict.SUPPORTED -> R.string.mms_check_verdict_supported
            MmsVerdict.CARRIER_DISABLED -> R.string.mms_check_verdict_disabled
            MmsVerdict.NO_CARRIER -> R.string.mms_check_verdict_no_carrier
            MmsVerdict.UNKNOWN -> R.string.mms_check_verdict_unknown
        }
    )
    val country = listOfNotNull(check.callingCode, check.region)
        .takeIf { it.isNotEmpty() }
        ?.let { stringResource(R.string.mms_check_country, it.first(), it.last()) }
    val source = check.number ?: stringResource(R.string.mms_check_number_hidden)
    return listOfNotNull(verdict, country, source).joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            stringResource(R.string.icon_back)
                        )
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
                .padding(horizontal = SettingsLayout.SCREEN_PADDING)
        ) {
            Spacer(Modifier.height(SettingsLayout.TOP_GAP))
            content()
            Spacer(Modifier.height(SettingsLayout.GROUP_GAP))
        }
    }
}
