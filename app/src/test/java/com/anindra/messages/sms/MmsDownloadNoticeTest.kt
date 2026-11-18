package com.anindra.messages.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #477: one download notice per announced message, cancelled on result. */
class MmsDownloadNoticeTest {

    @Test
    fun eachRowGetsItsOwnNotice() {
        assertTrue(MmsDownloadNotice.idFor(1) != MmsDownloadNotice.idFor(2))
        assertTrue(MmsDownloadNotice.idFor(1) > 7_000)
    }

    @Test
    fun idsDoNotCollideWithTheMessageNotices() {
        // Message notifications are derived from a conversation id with no offset;
        // the download notice starts at 7100 so a big conversation id cannot land
        // on it and cancel the wrong notification.
        assertTrue(MmsDownloadNotice.idFor(0) >= 7_100)
    }

    @Test
    fun theRowIdIsReadBackFromTheContentUri() {
        assertEquals(42L, MmsDownloadNotice.rowIdFrom("content://mms/42"))
        assertEquals(-1L, MmsDownloadNotice.rowIdFrom("content://mms/"))
        assertEquals(-1L, MmsDownloadNotice.rowIdFrom(null))
        assertEquals(-1L, MmsDownloadNotice.rowIdFrom("content://mms/not-a-number"))
    }

    @Test
    fun aLargeRowIdStillFitsAnInt() {
        val id = MmsDownloadNotice.idFor(Long.MAX_VALUE)
        assertTrue(id > 0)
    }
}
