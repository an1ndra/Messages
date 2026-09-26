package com.anindra.messages.data

object KeywordFilter {
    /** Where an incoming message goes: delivered to the inbox or moved to Trash. */
    enum class Route { INBOX, TRASH }

    /** True when [body] contains any non-blank keyword, case-insensitively. */
    fun isBlocked(body: String, keywords: Set<String>): Boolean {
        if (keywords.isEmpty()) return false
        val lower = body.lowercase()
        return keywords.any { it.isNotBlank() && lower.contains(it.lowercase()) }
    }

    /** Blocked messages are routed to Trash so they stay recoverable. */
    fun route(body: String, keywords: Set<String>): Route =
        if (isBlocked(body, keywords)) Route.TRASH else Route.INBOX
}
