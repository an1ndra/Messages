package com.anindra.messages.ui

import androidx.compose.ui.unit.dp

/** The gesture that puts the inbox tabs away again. */
internal object CategoryRowDismiss {

    /** An upward swipe on the chip row, not on the list underneath it. */
    val THRESHOLD = 48.dp

    /** [draggedPx] is negative while the finger moves up. */
    fun reached(draggedPx: Float, thresholdPx: Float): Boolean =
        draggedPx <= -thresholdPx
}
