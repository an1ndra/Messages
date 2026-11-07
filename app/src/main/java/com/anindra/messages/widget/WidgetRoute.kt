package com.anindra.messages.widget

/**
 * Which screen a widget tap asks for. MainActivity is `singleTop`, so a tap on
 * a running app arrives as an extra rather than a new activity; this is the
 * one place that turns those extras back into a route.
 */
object WidgetRoute {
    fun forExtras(openInbox: Boolean, openNewMessage: Boolean): String? = when {
        openNewMessage -> "new"
        openInbox -> "list"
        else -> null
    }
}
