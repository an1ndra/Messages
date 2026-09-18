package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupLocationTest {
    @Test
    fun defaultWhenNoTreeUri() {
        assertEquals("Documents/Messages", BackupLocation.label(""))
        assertEquals("Documents/Messages", BackupLocation.label("   "))
        assertFalse(BackupLocation.isCustom(""))
    }

    @Test
    fun usesTheTreeFolderName() {
        val tree = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FMessages"
        assertEquals("Messages", BackupLocation.label(tree))
        assertTrue(BackupLocation.isCustom(tree))
    }

    @Test
    fun handlesTreeAtStorageRoot() {
        val tree = "content://com.android.externalstorage.documents/tree/primary%3A"
        assertEquals("primary", BackupLocation.label(tree))
    }

    @Test
    fun fallsBackToRawUriWhenUnparseable() {
        assertEquals("not-a-uri", BackupLocation.label("not-a-uri"))
    }
}
