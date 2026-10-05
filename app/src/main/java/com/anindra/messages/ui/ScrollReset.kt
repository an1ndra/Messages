package com.anindra.messages.ui

/**
 * Whether arriving at a screen should put its scroll back at the top.
 *
 * Settings is reached two ways and they want opposite behaviour. Coming from
 * the conversation list it is a fresh destination, so it opens at the top —
 * otherwise re-entering it lands mid-list with no visible reason. Coming back
 * from a sub-screen (Accessibility, Advanced, Inbox) it should stay exactly
 * where it was, because the sub-screen was opened *from* a row further down
 * and returning to the top throws away the context the user was reading
 * (issue #265).
 */
object ScrollReset {

    /** Routes that are a parent of [ROUTE], i.e. entered from them deliberately. */
    private const val ROUTE = "settings"

    fun shouldResetToTop(fromRoute: String?, toRoute: String): Boolean =
        toRoute == ROUTE && fromRoute == "list"
}