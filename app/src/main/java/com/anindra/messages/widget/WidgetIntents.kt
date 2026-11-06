package com.anindra.messages.widget

import android.content.Context
import android.content.Intent
import com.anindra.messages.MainActivity

/**
 * Intents a widget tap launches. Every one targets MainActivity and reuses a
 * running instance, so a tap never stacks a second copy of the app.
 */
object WidgetIntents {

    const val EXTRA_OPEN_INBOX = "open_inbox"
    const val EXTRA_OPEN_NEW_MESSAGE = "open_new_message"
    const val EXTRA_OPEN_CONVERSATION_ADDRESS = "open_conversation_address"

    private fun base(context: Context) = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    fun conversation(context: Context, address: String): Intent =
        base(context).putExtra(EXTRA_OPEN_CONVERSATION_ADDRESS, address)

    fun inbox(context: Context): Intent = base(context).putExtra(EXTRA_OPEN_INBOX, true)

    fun newMessage(context: Context): Intent = base(context).putExtra(EXTRA_OPEN_NEW_MESSAGE, true)

    fun openApp(context: Context): Intent = base(context)
}
