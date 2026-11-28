package com.anindra.messages.ui

/** Copies a conversation number and confirms it on screen. */
internal class NumberCopy(
    private val write: (String) -> Unit,
    private val notify: () -> Unit
) {
    operator fun invoke(number: String) {
        write(number)
        notify()
    }
}
