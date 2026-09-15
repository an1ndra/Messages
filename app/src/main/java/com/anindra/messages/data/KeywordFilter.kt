package com.anindra.messages.data

object KeywordFilter {
    /** True when [body] contains any non-blank keyword, case-insensitively. */
    fun isBlocked(body: String, keywords: Set<String>): Boolean {
        if (keywords.isEmpty()) return false
        val lower = body.lowercase()
        return keywords.any { it.isNotBlank() && lower.contains(it.lowercase()) }
    }
}
