package com.anindra.messages.data

/**
 * Message bodies must not carry trailing blank lines: SMS/MMS senders (and some
 * providers) append "\n" separators, which render as an empty gap at the end of
 * a bubble. Interior blank lines are preserved — only trailing newlines and
 * whitespace are removed. Kept pure so it is unit tested.
 */
object MessageBody {
    fun normalize(body: String): String = body.trimEnd()
}
