package com.anindra.messages.ui

import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController

/** Drops the home search field's focus before a result opens the chat. */
internal class SearchFocusRelease(
    private val focusManager: FocusManager,
    private val keyboardController: SoftwareKeyboardController?,
    private val onOpenConversation: (Long, String) -> Unit
) {
    operator fun invoke(conversationId: Long, query: String) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onOpenConversation(conversationId, query)
    }
}
