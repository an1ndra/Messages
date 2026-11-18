package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #479: a shared location is a `geo:` link, not a picture of a map. */
class LocationShareTest {

    @Test
    fun theLinkCarriesSixDecimals() {
        assertEquals("geo:12.971599,77.594566", LocationShare.geoLink(12.9715987, 77.5945663))
    }

    @Test
    fun theLabelIsAppendedAsAQuery() {
        assertEquals("geo:1.000000,2.000000?q=MG%20Road", LocationShare.geoLink(1.0, 2.0, "MG Road"))
    }

    @Test
    fun aLabelWithSeparatatorsCannotBreakTheQuery() {
        // '?' and '&' would end the q parameter or start a new one.
        assertEquals("geo:1.000000,2.000000?q=Coffee%3FTea%26Cake",
            LocationShare.geoLink(1.0, 2.0, "Coffee?Tea&Cake"))
    }

    @Test
    fun anEmptyLabelLeavesABareLink() {
        assertEquals("geo:1.000000,2.000000", LocationShare.geoLink(1.0, 2.0, "   "))
    }

    @Test
    fun negativeCoordinatesKeepTheirSign() {
        assertTrue(LocationShare.geoLink(-33.868820, 151.209290).startsWith("geo:-33.868820,151.209290"))
        assertFalse(LocationShare.geoLink(0.0, 0.0).contains("--"))
    }
}
