package com.anindra.messages.ui.previews

import com.anindra.messages.data.BlockedMessage
import com.anindra.messages.data.Conversation
import com.anindra.messages.data.Message
import com.anindra.messages.data.ScheduledMessage
import com.anindra.messages.data.SimCard
import com.anindra.messages.data.TrashedMessage
import com.anindra.messages.ui.Contact
import com.anindra.messages.ui.ExpressiveTab
import com.anindra.messages.ui.RowSettings

/**
 * Fixed sample data for the previews.
 *
 * Timestamps are constants rather than `System.currentTimeMillis()` so a
 * rendered preview is stable between reloads; a preview that re-orders itself
 * on every keystroke is hard to read a change against.
 */
internal const val SAMPLE_TS = 1_757_000_000_000L
internal const val SAMPLE_TS_OLD = 1_756_900_000_000L

internal fun sampleMessage(
    id: Long,
    body: String,
    isMe: Boolean,
    status: String = if (isMe) "sent" else "received",
    timestamp: Long = SAMPLE_TS,
    subId: Int = -1,
    mediaType: String = "text",
    mediaUri: String = "",
    reactions: Map<String, Int> = emptyMap(),
    locked: Boolean = false,
    address: String = "",
    transport: String = "sms",
    deliveredAt: Long = 0
) = Message(
    id = id,
    conversationId = 1L,
    body = body,
    timestamp = timestamp,
    isMe = isMe,
    status = status,
    mediaType = mediaType,
    mediaUri = mediaUri,
    reactions = reactions,
    locked = locked,
    subId = subId,
    transport = transport,
    deliveredAt = deliveredAt,
    address = address
)

internal fun sampleConversation(
    id: Long,
    name: String,
    snippet: String,
    unreadCount: Int = 0,
    isMe: Boolean = false,
    timestamp: Long = SAMPLE_TS,
    pinned: Boolean = false,
    archived: Boolean = false,
    blocked: Boolean = false,
    draft: String = "",
    groupTitle: String = ""
) = Conversation(
    id = id,
    address = "+1555123000$id",
    name = name,
    snippet = snippet,
    timestamp = timestamp,
    unreadCount = unreadCount,
    isMe = isMe,
    archived = archived,
    blocked = blocked,
    pinned = pinned,
    draft = draft,
    groupTitle = groupTitle
)

internal fun sampleScheduled(
    id: Long,
    address: String,
    body: String,
    timestamp: Long = SAMPLE_TS,
    subId: Int = -1
) = ScheduledMessage(
    id = id,
    address = address,
    body = body,
    timestamp = timestamp,
    conversationId = 1L,
    subId = subId
)

internal fun sampleBlockedMessage(
    id: Long,
    name: String,
    body: String,
    reason: String = "Blocked keyword"
) = BlockedMessage(
    id = id,
    conversationId = 1L,
    address = "+1555123000$id",
    name = name,
    body = body,
    timestamp = SAMPLE_TS,
    blockedReason = reason
)

internal fun sampleTrashedMessage(
    id: Long,
    name: String,
    body: String
) = TrashedMessage(
    id = id,
    conversationId = 1L,
    address = "+1555123000$id",
    name = name,
    body = body,
    timestamp = SAMPLE_TS,
    deletedAt = SAMPLE_TS
)

internal fun sampleSim(
    subscriptionId: Int = 1,
    slotIndex: Int = 0,
    carrier: String = "Jio"
) = SimCard(
    subscriptionId = subscriptionId,
    slotIndex = slotIndex,
    carrierName = carrier,
    displayName = carrier,
    mccMnc = "40445",
    countryIso = "IN",
    embedded = false
)

internal fun sampleContact(
    name: String,
    number: String,
    workProfile: Boolean = false
) = Contact(name = name, number = number, workProfile = workProfile)

/** All the row-level features on, so a row renders every badge and affordance. */
internal val fullRowSettings = RowSettings(
    pinnedEnabled = true,
    draftsEnabled = true,
    reverseSwipe = false,
    archivingEnabled = true,
    blockingEnabled = true,
    swipeEnabled = true
)

/** Everything off, which is the minimal row a user can configure. */
internal val minimalRowSettings = RowSettings(
    pinnedEnabled = false,
    archivingEnabled = false,
    blockingEnabled = false,
    swipeEnabled = false
)

internal fun sampleTabs(vararg labels: Pair<Int, androidx.compose.ui.graphics.vector.ImageVector?>) =
    labels.map { ExpressiveTab(labelRes = it.first, icon = it.second) }
