package com.anindra.messages.ui

import com.anindra.messages.R
import com.anindra.messages.data.Message

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

    /** Star is offered for every selection; several messages toggle together. */
    fun showStar(count: Int): Boolean = count >= 1

    /** Lock/Unlock toggles to "Unlock" only when every selected message is locked. */
    fun lockLabelRes(allLocked: Boolean): Int =
        if (allLocked) R.string.chat_unlock_dialog else R.string.chat_lock_dialog

    fun isSelectionActive(count: Int): Boolean = count > 0

    /** Every selected row that carries a file. A multi-picture album is several
     *  rows sharing one bubble, so Save has to cover all of them; taking the
     *  first row silently saved one picture and reported success (#364). */
    fun saveTargets(messages: List<Message>, selected: Collection<Long>): List<Message> =
        messages.filter { it.id in selected && it.mediaUri.isNotBlank() }

    /** The overflow is reachable for any non-empty selection: it carries
     *  "Select all" even once several messages are picked, so the user can still
     *  extend a multi-message selection in one tap (#232). */
    fun showMore(count: Int): Boolean = count >= 1

    /**
     * "Select all" takes every message that is not locked; a locked message
     * must never end up in a bulk delete/trash.
     *
     * [groups] holds the ids that stand for one message — a single id, or every
     * picture of one album. A group is taken whole or not at all: selecting two
     * pictures of a locked three-picture MMS left the album looking unselected
     * while a delete would take those two, which is the silent partial-loss
     * bug this signature exists to make impossible.
     */
    fun selectAllCandidates(groups: List<List<Long>>, lockedIds: Set<Long>): List<Long> =
        groups.filter { group -> group.isNotEmpty() && group.none { it in lockedIds } }.flatten()

    /** Save names the number of files it will write: selecting several pictures
     *  offered "Save image" and read as if only one would be saved (#398). */
    fun saveLabelRes(count: Int): Int =
        if (count > 1) R.string.chat_save_images else R.string.chat_save_image
    /** Whether an action dismisses the selection afterwards. Non-destructive
     *  actions (copy, share, forward) leave the selection in place so the user
     *  can chain actions; destructive/state-changing ones (trash, lock) clear it. */
    enum class Action { COPY, SHARE, FORWARD, TRASH, LOCK }
    fun clearsSelection(action: Action): Boolean = when (action) {
        Action.COPY, Action.SHARE, Action.FORWARD -> false
        Action.TRASH, Action.LOCK -> true
    }
}
