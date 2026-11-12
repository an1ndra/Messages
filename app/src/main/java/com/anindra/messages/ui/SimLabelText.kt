package com.anindra.messages.ui

import android.content.Context
import android.telephony.SubscriptionManager
import com.anindra.messages.R
import com.anindra.messages.data.SimCard
import com.anindra.messages.data.SimLabels

/** The name a SIM goes by: the user's own name, else the carrier, else null. */
internal fun simName(card: SimCard?): String? = card?.let {
    SimLabels.userName(it) ?: it.carrierName?.trim()?.takeIf(String::isNotEmpty)
}

/** "SIM n · Name" (or just "SIM n") for the SIM pickers. */
internal fun simOptionLabel(context: Context, card: SimCard): String {
    val slot = String.format(context.getString(R.string.settings_sim_label), card.slotIndex + 1)
    return simName(card)?.let { "$slot · $it" } ?: slot
}

/** "Name (SIM n)" (or just "SIM n") for the Settings rows. */
internal fun simSettingsLabel(context: Context, card: SimCard): String {
    val name = simName(card)
    return if (name != null) {
        String.format(context.getString(R.string.settings_sim_label_format), name, card.slotIndex + 1)
    } else {
        String.format(context.getString(R.string.settings_sim_label), card.slotIndex + 1)
    }
}

/**
 * The suffix a message's meta line carries for the SIM that sent it. A
 * user-named SIM shows its name; otherwise the old " · SIM n" slot label is
 * kept, so a device whose SIMs were never renamed renders exactly as before.
 */
internal fun simMessageSuffix(
    context: Context,
    card: SimCard?,
    subId: Int,
    showIndicator: Boolean
): String {
    if (!showIndicator || subId <= 0) return ""
    card?.let { SimLabels.userName(it) }?.let { return " • $it" }
    val slot = card?.slotIndex?.takeIf { it >= 0 } ?: try {
        SubscriptionManager.getSlotIndex(subId)
    } catch (_: Exception) {
        -1
    }
    return if (slot >= 0) String.format(context.getString(R.string.sim_slot_suffix), slot + 1) else ""
}
