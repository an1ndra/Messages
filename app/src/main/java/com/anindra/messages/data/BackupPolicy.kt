package com.anindra.messages.data

/** Whether message backup/export is permitted under the current settings. */
object BackupPolicy {
    /** Privacy mode keeps message content off disk/outside the app, so backups
     *  (which write the plaintext database) are disabled entirely. */
    fun isBackupAllowed(privacyMode: Boolean): Boolean = !privacyMode
}
