package com.anindra.messages.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anindra.messages.R
import com.anindra.messages.data.Message
import com.anindra.messages.ui.theme.outgoingBubble
import com.anindra.messages.data.ScheduledCountdown
import com.anindra.messages.data.ScheduledMessage
import kotlinx.coroutines.delay

/**
 * One row of a conversation: a stored message, or one still waiting on the
 * scheduler. Merged by [timestamp] so a scheduled message sits where it will
 * actually appear, and keys are namespaced because both tables number rows
 * from 1.
 *
 * Shared by both UI generations so the redesigned and legacy chat cannot drift
 * on ordering or keying.
 */
sealed interface ChatRow {
    val timestamp: Long
    val key: String

    data class Sent(val message: Message) : ChatRow {
        override val timestamp get() = message.timestamp
        override val key get() = "sent_${message.id}"
    }

    data class Pending(val scheduled: ScheduledMessage) : ChatRow {
        override val timestamp get() = scheduled.timestamp
        override val key get() = "pending_${scheduled.id}"
    }
}

/** Chat rows for [conversationId], scheduled ones folded in by send time. */
@Composable
fun rememberChatRows(
    messages: List<Message>,
    allScheduled: List<ScheduledMessage>,
    conversationId: Long
): List<ChatRow> {
    val scheduledHere = remember(allScheduled, conversationId) {
        allScheduled.filter { it.conversationId == conversationId }
    }
    return remember(messages, scheduledHere) {
        (messages.map { ChatRow.Sent(it) } + scheduledHere.map { ChatRow.Pending(it) })
            .sortedBy { it.timestamp }
    }
}

/**
 * Row index -> its position in `messages`, or -1 for a pending row. Row indices
 * and message indices stop agreeing once scheduled rows interleave, so without
 * this a pending row above a message shifts every bubble tail below it.
 */
@Composable
fun rememberSentIndexAt(rows: List<ChatRow>): List<Int> = remember(rows) {
    var n = 0
    rows.map { if (it is ChatRow.Sent) n++ else -1 }
}

/**
 * A message waiting on the scheduler, drawn in the conversation it belongs to.
 * Carries the same time + SIM meta line a sent bubble does, plus a live
 * countdown. Long-press cancels after a confirmation.
 */
@Composable
fun ScheduledBubble(
    scheduled: ScheduledMessage,
    showSimIndicator: Boolean,
    onCancel: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var confirmCancel by remember { mutableStateOf(false) }
    var now by remember(scheduled.timestamp) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(scheduled.timestamp) {
        while (true) {
            delay(ScheduledCountdown.refreshIntervalMillis(now, scheduled.timestamp))
            now = System.currentTimeMillis()
        }
    }

    val countdown = remember(now, scheduled.timestamp) {
        ScheduledCountdown.format(now, scheduled.timestamp)
    }
    val timeText = remember(scheduled.timestamp) {
        formatTimeOnly(scheduled.timestamp, is24HourFormat(context))
    }
    val simLabel = remember(scheduled.subId, showSimIndicator) {
        if (showSimIndicator && scheduled.subId > 0) {
            try {
                val slot = android.telephony.SubscriptionManager.getSlotIndex(scheduled.subId)
                if (slot >= 0) String.format(context.getString(R.string.sim_slot_suffix), slot + 1) else ""
            } catch (_: Exception) {
                ""
            }
        } else ""
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            color = cs.outgoingBubble,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .widthIn(max = 260.dp)
                .combinedClickable(onClick = {}, onLongClick = { confirmCancel = true })
        ) {
            Text(
                scheduled.body,
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        Text(
            buildString {
                append(timeText)
                if (countdown.isNotEmpty()) append(" • in ").append(countdown)
                append(simLabel)
            },
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, end = 4.dp)
        )
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.settings_scheduled_title)) },
            text = { Text(scheduled.body, maxLines = 3) },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; onCancel() }) {
                    Text(stringResource(R.string.icon_cancel))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) {
                    Text(stringResource(R.string.chat_close))
                }
            }
        )
    }
}