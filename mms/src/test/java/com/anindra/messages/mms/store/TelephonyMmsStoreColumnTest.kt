package com.anindra.messages.mms.store

import android.provider.Telephony
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the provider column mapping against the two places a name can drift:
 * our own constants, and the platform's public constants. The hidden columns are
 * asserted against the real AOSP schema strings, because a wrong name here makes
 * the provider reject the whole insert on a device.
 */
class TelephonyMmsStoreColumnTest {

    @Test
    fun publicColumnsMatchThePlatformConstants() {
        assertEquals(Telephony.Mms.TRANSACTION_ID, TelephonyMmsStore.COLUMN_TRANSACTION_ID)
        assertEquals(Telephony.Mms.MMS_VERSION, TelephonyMmsStore.COLUMN_MMS_VERSION)
        assertEquals(Telephony.Mms.MESSAGE_SIZE, TelephonyMmsStore.COLUMN_MESSAGE_SIZE)
        assertEquals(Telephony.Mms.STATUS, TelephonyMmsStore.COLUMN_STATUS)
        assertEquals(Telephony.Mms.EXPIRY, TelephonyMmsStore.COLUMN_EXPIRY)
        assertEquals(Telephony.Mms.CONTENT_TYPE, TelephonyMmsStore.COLUMN_CONTENT_TYPE)
        assertEquals(Telephony.Mms.CONTENT_LOCATION, TelephonyMmsStore.COLUMN_CONTENT_LOCATION)
        assertEquals(Telephony.Mms.MESSAGE_CLASS, TelephonyMmsStore.COLUMN_MESSAGE_CLASS)
        assertEquals(Telephony.Mms.MESSAGE_ID, TelephonyMmsStore.COLUMN_MESSAGE_ID)
        assertEquals(Telephony.Mms.PRIORITY, TelephonyMmsStore.COLUMN_PRIORITY)
        assertEquals(Telephony.Mms.READ_STATUS, TelephonyMmsStore.COLUMN_READ_STATUS)
        assertEquals(Telephony.Mms.SUBJECT, TelephonyMmsStore.COLUMN_SUBJECT)
        assertEquals(Telephony.Mms.SUBJECT_CHARSET, TelephonyMmsStore.COLUMN_SUBJECT_CHARSET)
        assertEquals(Telephony.Mms.DATE, TelephonyMmsStore.COLUMN_DATE)
        assertEquals(Telephony.Mms.DELIVERY_TIME, TelephonyMmsStore.COLUMN_DELIVERY_TIME)
    }

    @Test
    fun hiddenColumnsMatchTheAospSchema() {
        // The remaining columns have no public Telephony.Mms constant but are
        // real columns in the AOSP pdu table; the values here are taken from
        // MmsSmsDatabaseHelper.CREATE_PDU_TABLE_STR and the Telephony.java source.
        assertEquals("resp_st", TelephonyMmsStore.COLUMN_RESPONSE_STATUS)
        assertEquals("resp_txt", TelephonyMmsStore.COLUMN_RESPONSE_TEXT)
        assertEquals("ct_cls", TelephonyMmsStore.COLUMN_CONTENT_CLASS)
        assertEquals("d_rpt", TelephonyMmsStore.COLUMN_DELIVERY_REPORT)
        assertEquals("rr", TelephonyMmsStore.COLUMN_READ_REPORT)
        assertEquals("rpt_a", TelephonyMmsStore.COLUMN_REPORT_ALLOWED)
        assertEquals("retr_st", TelephonyMmsStore.COLUMN_RETRIEVE_STATUS)
        assertEquals("retr_txt", TelephonyMmsStore.COLUMN_RETRIEVE_TEXT)
        assertEquals("retr_txt_cs", TelephonyMmsStore.COLUMN_RETRIEVE_TEXT_CHARSET)
    }
}
