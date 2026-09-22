package com.anindra.messages.ui

import com.anindra.messages.R

/**
 * Presentation rules for the contextual message-selection toolbar. Kept out of
 * the composable so the visibility matrix is unit tested.
 */
object SelectionToolbar {
    /** More-actions menu (Share, Forward, View details, Lock/Unlock) is only
     *  meaningful for a single message. */
    fun showSingleMessageActions(count: Int): Boolean = count == 1

    /** Forward is offered for every selection; multiple messages are forwarded
     *  together. */
    fun showForward(count: Int): Boolean = count >= 1

    /** Copy is offered for every selection; multiple messages are copied as one
     *  newline-separated block. */
    fun showCopy(count: Int): Boolean = count >= 1

    /** Trash is offered for every selection and moves those messages to Trash. */
    fun showTrash(count: Int): Boolean = count >= 1

    fun showDelete(count: Int): Boolean = count >= 1

    /** Lock/Unlock toggles to "Unlock" only when every selected message is locked. */
    fun lockLabelRes(allLocked: Boolean): Int =
        if (allLocked) R.string.chat_unlock_dialog else R.string.chat_lock_dialog

    fun isSelectionActive(count: Int): Boolean = count > 0

    /** Whether an action dismisses the selection afterwards. Non-destructive
     *  actions (copy, share, forward) leave the selection in place so the user
     *  can chain actions; destructive/state-changing ones (trash, lock) clear it. */
    enum class Action { COPY, SHARE, FORWARD, TRASH, LOCK }
    fun clearsSelection(action: Action): Boolean = when (action) {
        Action.COPY, Action.SHARE, Action.FORWARD -> false
        Action.TRASH, Action.LOCK -> true
    }
}
