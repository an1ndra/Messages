package com.anindra.messages.data

sealed interface SimLabel {
    object Default : SimLabel
    /** A human label: the user's own SIM name when set, otherwise the carrier. */
    data class Carrier(val carrier: String, val slot: Int) : SimLabel
    data class Slot(val slot: Int) : SimLabel
    object Unknown : SimLabel
}

object SimLabels {
    /**
     * The name the user gave this SIM in system settings, or null when the
     * platform is only echoing the carrier name. Android's display name falls
     * back to the carrier for a SIM that was never renamed, so equality with
     * the carrier is what distinguishes a real choice from that fallback.
     */
    fun userName(displayName: String?, carrierName: String?): String? {
        val name = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val carrier = carrierName?.trim()?.takeIf { it.isNotEmpty() }
        return name.takeIf { it != carrier }
    }

    fun userName(card: SimCard): String? = userName(card.displayName, card.carrierName)

    fun resolve(
        selectedSubId: Int,
        slotIndex: Int?,
        carrier: String?,
        displayName: String? = null
    ): SimLabel = when {
        selectedSubId == -1 -> SimLabel.Default
        slotIndex == null -> SimLabel.Unknown
        else -> {
            val name = userName(displayName, carrier)
                ?: carrier?.trim()?.takeIf { it.isNotEmpty() }
            if (name != null) SimLabel.Carrier(name, slotIndex + 1) else SimLabel.Slot(slotIndex + 1)
        }
    }
}
