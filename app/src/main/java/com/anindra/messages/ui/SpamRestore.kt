package com.anindra.messages.ui

/** Whether a caught message can be put back into its conversation. */
object SpamRestore {
    /** True once the message would no longer be caught, i.e. none of the keywords
     *  currently on the block list still matches it. Restoring while the keyword is
     *  still blocked would just hide it again, so the control stays unavailable. */
    fun canReturnToChat(body: String, blockedKeywords: Set<String>): Boolean =
        blockedKeywords.none { it.isNotBlank() && body.contains(it, ignoreCase = true) }
}
