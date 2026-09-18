package com.anindra.messages.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPolicyTest {
    @Test
    fun backupAllowedNormally() {
        assertTrue(BackupPolicy.isBackupAllowed(privacyMode = false))
    }

    @Test
    fun backupDisabledInPrivacyMode() {
        assertFalse(BackupPolicy.isBackupAllowed(privacyMode = true))
    }
}
