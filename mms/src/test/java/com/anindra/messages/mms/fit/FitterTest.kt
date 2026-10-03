package com.anindra.messages.mms.fit

import com.anindra.messages.mms.pdu.ContentTypes
import com.anindra.messages.mms.spi.BudgetPolicy
import com.anindra.messages.mms.spi.FitOutcome
import com.anindra.messages.mms.spi.FitRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetPolicyTest {

    @Test
    fun overheadIsDeductedBeforeAnythingIsShared() {
        val budget = BudgetPolicy.attachmentBudget(307_200, captionBytes = 0, partCount = 1)
        assertEquals(307_200L - BudgetPolicy.PDU_OVERHEAD_BYTES - 512L, budget)
    }

    @Test
    fun aLongCaptionEatsIntoTheAttachmentShare() {
        val short = BudgetPolicy.attachmentBudget(307_200, 100, 1)
        val long = BudgetPolicy.attachmentBudget(307_200, 5_000, 1)
        assertTrue(long < short)
    }

    @Test
    fun aCarrierCapSmallerThanTheOverheadYieldsNoBudget() {
        assertEquals(0L, BudgetPolicy.attachmentBudget(1_024, 0, 1))
        assertEquals(0L, BudgetPolicy.attachmentBudget(0, 0, 1))
    }

    @Test
    fun theBudgetNeverGoesNegative() {
        val budget = BudgetPolicy.attachmentBudget(20_000, 100_000, 8)
        assertEquals(0L, budget)
    }

    @Test
    fun morePartsCostMoreHeadroom() {
        val one = BudgetPolicy.attachmentBudget(307_200, 0, 1)
        val four = BudgetPolicy.attachmentBudget(307_200, 0, 4)
        assertTrue(four < one)
    }
}

class RawAttachmentFitterTest {

    private val fitter = RawAttachmentFitter()

    private fun request(bytes: ByteArray, budget: Long, mime: String = "video/mp4") =
        FitRequest(mimeType = mime, bytes = bytes, budgetBytes = budget, maxImageWidth = 0, maxImageHeight = 0)

    @Test
    fun bytesWithinBudgetPassThroughWithTheirType() {
        val outcome = fitter.fit(request(ByteArray(1000), 2_000))
        assertTrue(outcome is FitOutcome.Fitted)
        outcome as FitOutcome.Fitted
        assertEquals(1000, outcome.bytes.size)
        assertEquals("video/mp4", outcome.mimeType)
    }

    @Test
    fun theTypeIsNormalizedSoAParameterisedMimeStillMatches() {
        val outcome = fitter.fit(request(ByteArray(10), 100, "video/mp4; codecs=avc1")) as FitOutcome.Fitted
        assertEquals("video/mp4", outcome.mimeType)
    }

    @Test
    fun anOversizedAttachmentIsReportedRatherThanTruncated() {
        assertEquals(FitOutcome.TooLarge, fitter.fit(request(ByteArray(5_000), 1_000)))
    }

    @Test
    fun anEmptyAttachmentIsUnreadableNotTooLarge() {
        assertEquals(FitOutcome.Unreadable, fitter.fit(request(ByteArray(0), 1_000)))
    }

    @Test
    fun noBudgetIsTooLargeEvenForTinyInput() {
        assertEquals(FitOutcome.TooLarge, fitter.fit(request(ByteArray(1), 0)))
    }

    @Test
    fun anAttachmentExactlyAtTheBudgetIsAccepted() {
        assertTrue(fitter.fit(request(ByteArray(1_000), 1_000)) is FitOutcome.Fitted)
    }
}

class DefaultAttachmentFitterRoutingTest {

    @Test
    fun imagesRouteToTheImageFitter() {
        val images = RecordingFitter()
        val raw = RecordingFitter()
        val fitter = DefaultAttachmentFitter(images, raw)
        fitter.fit(FitRequest("image/jpeg", ByteArray(4), budgetBytes = 10, maxImageWidth = 1, maxImageHeight = 1))
        assertEquals(1, images.calls)
        assertEquals(0, raw.calls)
    }

    @Test
    fun everythingElseRoutesToTheRawFitter() {
        val images = RecordingFitter()
        val raw = RecordingFitter()
        val fitter = DefaultAttachmentFitter(images, raw)
        fitter.fit(FitRequest("audio/amr", ByteArray(4), budgetBytes = 10, maxImageWidth = 1, maxImageHeight = 1))
        fitter.fit(FitRequest("text/vcard", ByteArray(4), budgetBytes = 10, maxImageWidth = 1, maxImageHeight = 1))
        assertEquals(0, images.calls)
        assertEquals(2, raw.calls)
    }

    @Test
    fun routingUsesTheSameNormalisationAsTheContentTypeTable() {
        assertTrue(ContentTypes.isImage("IMAGE/JPEG"))
        assertTrue(ContentTypes.isImage("image/jpeg; name=x.jpg"))
    }

    private class RecordingFitter : com.anindra.messages.mms.spi.AttachmentFitter {
        var calls = 0
        override fun fit(request: com.anindra.messages.mms.spi.FitRequest): FitOutcome {
            calls++
            return FitOutcome.TooLarge
        }
    }
}
