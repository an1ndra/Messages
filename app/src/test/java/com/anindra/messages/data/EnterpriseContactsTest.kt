package com.anindra.messages.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnterpriseContactsTest {

    @Test
    fun enterpriseUriOnlySupportedFromApi34() {
        assertFalse(EnterpriseContacts.isSupported(29))
        assertFalse(EnterpriseContacts.isSupported(31))
        assertFalse(EnterpriseContacts.isSupported(33))
        assertTrue(EnterpriseContacts.isSupported(34))
        assertTrue(EnterpriseContacts.isSupported(35))
    }
}
