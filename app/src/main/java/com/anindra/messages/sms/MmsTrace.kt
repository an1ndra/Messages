package com.anindra.messages.sms

/**
 * The one logging call MMS code uses, so every line it writes is also readable
 * in Diagnostics.
 *
 * Writing straight to `android.util.Log` is what these files used to do, and it
 * made MMS problems the hardest to diagnose in the app: logcat is scrolled away,
 * needs a cable, and cannot be asserted on, while the receiver, the downloader
 * and the composer each held the only copy of what they knew. The recorder
 * behind this keeps the recent lines in memory, and Diagnostics prints them as
 * plain text.
 *
 * Nothing logged through here may carry a recipient number, a message body or a
 * caption: this text is what a user attaches to a bug report.
 */
internal object MmsTrace {

    fun i(tag: String, message: String) = record(tag, "i", message)

    fun w(tag: String, message: String) = record(tag, "w", message)

    fun w(tag: String, message: String, error: Throwable) =
        record(tag, "w", "$message: ${error.javaClass.simpleName}: ${error.message}")

    fun e(tag: String, message: String, error: Throwable? = null) =
        record(tag, "e", if (error == null) message else "$message: ${error.message}")

    private fun record(tag: String, level: String, message: String) {
        MmsFacade.diagnostics.noted(tag, level, message)
    }
}