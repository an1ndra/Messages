package com.anindra.messages.ui

/**
 * LIFO stack behind the inbox's single undo snackbar (#374).
 *
 * `SnackbarHostState.showSnackbar` queues its callers first-in-first-out, so six
 * quick swipes used to show the *oldest* action's undo and restore that chat
 * first. The inbox shows only the newest entry and pops it on undo, so undo
 * walks back the way the user came. Cleared when the snackbar's window closes.
 */
class UndoStack<T> {

    private val entries = ArrayDeque<T>()

    fun push(entry: T) {
        entries.addLast(entry)
    }

    /** The action the snackbar currently offers, without consuming it. */
    fun peek(): T? = entries.lastOrNull()

    fun pop(): T? = entries.removeLastOrNull()

    fun clear() {
        entries.clear()
    }
}
