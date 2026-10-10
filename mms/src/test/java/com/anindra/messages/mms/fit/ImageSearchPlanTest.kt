package com.anindra.messages.mms.fit

import com.anindra.messages.mms.spi.FitOutcome
import com.anindra.messages.mms.spi.FitRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The order a photo is encoded in, which is what decides whether it arrives
 * sharp.
 *
 * A carrier that declares no image bounds still gets a 640x480 default, and
 * treating that guess as a limit cut every picture down to it. The search now
 * treats bytes as the only limit in that case, and spends quality before
 * resolution so a generous budget costs nothing.
 */
class ImageSearchPlanTest {

    private fun plan(w: Int, h: Int) = ImageAttachmentFitter.searchPlan(w, h)

    @Test
    fun qualityIsSpentBeforeResolution() {
        val steps = plan(1000, 800)

        // Every candidate at the full size comes before any smaller one.
        val firstSmallerIndex = steps.indexOfFirst { it.first < 1000 }
        val lastFullSizeIndex = steps.indexOfLast { it.first == 1000 }
        assertTrue(
            "quality must be tried at full size before shrinking",
            firstSmallerIndex > lastFullSizeIndex
        )
    }

    @Test
    fun theSearchWalksDownThroughEverySizeStep() {
        val widths = plan(1000, 800).map { it.first }.distinct()

        assertEquals(listOf(1000, 850, 700, 550, 400, 300), widths)
    }

    @Test
    fun theFirstCandidateKeepsTheWholeImage() {
        val steps = plan(4000, 3000)

        assertEquals(Triple(4000, 3000, 85), steps.first())
    }

    @Test
    fun theLastStepGetsTheFullQualityLadder() {
        val steps = plan(100, 100)
        val smallest = steps.minOf { it.first }

        // At the smallest size every quality is worth trying, because at this
        // point quality is all that is left to give.
        assertTrue(steps.count { it.first == smallest } >= 4)
        assertEquals(45, steps.last().third)
    }

    @Test
    fun aTinyImageNeverCollapsesToNothing() {
        val steps = plan(1, 1)

        assertTrue(steps.all { it.first >= 1 && it.second >= 1 })
    }

    @Test
    fun thePlanIsTheSameShapeWhateverTheBudget() {
        // The budget is not an input: which candidate wins is decided by the
        // bytes each one produces, so the order has to be fixed.
        assertEquals(plan(2000, 1500), plan(2000, 1500))
    }
}

/**
 * What the fitter does with the carrier's answer about image bounds.
 */
class ImageDimensionCapTest {

    private fun request(
        width: Int = 640,
        height: Int = 480,
        reported: Boolean = false,
    ) = FitRequest(
        mimeType = "image/jpeg",
        bytes = ByteArray(16),
        sourceWidth = 4000,
        sourceHeight = 3000,
        budgetBytes = 300_000,
        maxImageWidth = width,
        maxImageHeight = height,
        dimensionLimitsReported = reported,
    )

    @Test
    fun anUndeclaredCapIsCarriedAsSuchSoItCanBeIgnored() {
        // The defaults still populate the fields, because other layers read
        // them; only this flag says they are not the carrier's opinion.
        assertTrue(!request(reported = false).dimensionLimitsReported)
        assertTrue(request(reported = true).dimensionLimitsReported)
    }

    @Test
    fun aFitRecordsWhatWasActuallySent() {
        val fitted = FitOutcome.Fitted(ByteArray(8), "image/jpeg", 1600, 1200)
        assertEquals(1600, fitted.width)
        assertEquals(1200, fitted.height)
    }
}