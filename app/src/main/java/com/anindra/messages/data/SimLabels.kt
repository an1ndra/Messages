package com.anindra.messages.data

sealed interface SimLabel {
    data object Default : SimLabel
    data class Carrier(val carrier: String, val slot: Int) : SimLabel
    data class Slot(val slot: Int) : SimLabel
    data object Unknown : SimLabel
}

object SimLabels {
    fun resolve(selectedSubId: Int, slotIndex: Int?, carrier: String?): SimLabel = when {
        selectedSubId == -1 -> SimLabel.Default
        slotIndex != null && !carrier.isNullOrBlank() -> SimLabel.Carrier(carrier, slotIndex + 1)
        slotIndex != null -> SimLabel.Slot(slotIndex + 1)
        else -> SimLabel.Unknown
    }
}
