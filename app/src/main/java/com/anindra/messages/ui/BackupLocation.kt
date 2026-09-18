package com.anindra.messages.ui

/** Presentation helpers for the backup-location setting. */
object BackupLocation {
    /** Human-readable location for a persisted SAF tree URI, or the default
     *  Documents/Messages path when none is set. */
    fun label(treeUri: String): String {
        if (treeUri.isBlank()) return "Documents/Messages"
        val decoded = decode(treeUri)
        val marker = "/tree/"
        val id = decoded.substringAfter(marker, decoded)
        val path = id.substringAfter(':', id).trim('/')
        val last = path.substringAfterLast('/').ifBlank { path }
        return last.ifBlank { id.trimEnd(':') }.ifBlank { decoded }
    }

    fun isCustom(treeUri: String): Boolean = treeUri.isNotBlank()

    private fun decode(value: String): String = try {
        java.net.URLDecoder.decode(value, Charsets.UTF_8.name())
    } catch (_: Exception) {
        value
    }
}
