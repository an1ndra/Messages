package com.anindra.messages.sms

import com.anindra.messages.ui.OtpDetector

/**
 * Whether a message notification has to be treated as carrying a one-time code (#476).
 *
 * Android 16 stops other apps reading codes out of notifications, but only for
 * notifications the app marks. A code that arrives in a text is exactly the
 * case: it is useful on the lock screen and dangerous to a notification
 * listener, so the notification is marked sensitive and its public version is
 * redacted rather than dropped — the app itself still shows the full text.
 *
 * Detection is [OtpDetector]'s, not a second opinion: the app already finds
 * codes there for tap-to-copy, and two patterns that disagree would mark some
 * codes and not others.
 */
object OtpNotificationPolicy {

    /** True when [body] contains something the app would offer to copy as a code. */
    fun carriesCode(body: String): Boolean =
        body.isNotBlank() && OtpDetector.findRanges(body).isNotEmpty()

    /**
     * The body with every digit replaced, for the public (lock-screen) version.
     *
     * Only the code is redacted, not the sender's wording: "Your code is 482913,
     * do not share it" reads as "Your code is 000000, do not share it", which
     * still tells the reader who it is from and what it is for without giving
     * the code away.
     */
    fun redact(body: String): String {
        if (body.isEmpty()) return body
        return body.map { if (it.isDigit()) '0' else it }.joinToString("")
    }
}
