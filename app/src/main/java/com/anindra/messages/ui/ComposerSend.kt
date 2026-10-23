package com.anindra.messages.ui

/**
 * Whether the composer's send button has anything to do. The button itself is
 * drawn unconditionally, as it was in 1.0.30; this only decides whether a tap
 * is live, so an empty field cannot fire a send.
 */
internal fun canSend(draft: String, hasAttachments: Boolean): Boolean =
    draft.isNotBlank() || hasAttachments
