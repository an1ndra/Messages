package com.anindra.messages.ui

/**
 * Whether the composer's send button has anything to do. The button itself is
 * drawn unconditionally, as it was in 1.0.30; this only decides whether a tap
 * is live, so an empty field cannot fire a send.
 */
internal fun canSend(draft: String, hasAttachments: Boolean): Boolean =
    draft.isNotBlank() || hasAttachments

/**
 * Whether the composer's circle records a voice note instead of sending.
 *
 * The same circle cannot mean both things at once, so an empty composer hands it
 * to the microphone — the swap Google Messages makes. A device without one keeps
 * the send arrow, dimmed, rather than showing a control that cannot work.
 */
internal fun showsVoiceButton(
    voiceEnabled: Boolean,
    draft: String,
    hasAttachments: Boolean
): Boolean = voiceEnabled && !canSend(draft, hasAttachments)
