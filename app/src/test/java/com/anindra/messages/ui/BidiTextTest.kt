package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BidiTextTest {

    private val lri = BidiText.LRI
    private val pdi = BidiText.PDI

    @Test
    fun ltrWrapsAndIsIdempotent() {
        assertEquals("$lri+98 999 862 0453$pdi", BidiText.ltr("+98 999 862 0453"))
        assertEquals("$lri+98 999 862 0453$pdi", BidiText.ltr("$lri+98 999 862 0453$pdi"))
        assertEquals("", BidiText.ltr(""))
    }

    @Test
    fun isolatesSpacedNumberAfterRtlText() {
        val out = BidiText.isolateNumberRuns("کد پیگیری 0999 862 0453").text
        assertEquals("کد پیگیری ${lri}0999 862 0453$pdi", out)
    }

    @Test
    fun isolatesLeadingPlusAndParentheses() {
        assertEquals(
            "کد ${lri}+155589935615$pdi",
            BidiText.isolateNumberRuns("کد +155589935615").text
        )
        assertEquals(
            "تلفن ${lri}(555) 000-4111$pdi",
            BidiText.isolateNumberRuns("تلفن (555) 000-4111").text
        )
    }

    @Test
    fun leavesPlainDigitRunsAlone() {
        assertEquals("90007872", BidiText.isolateNumberRuns("90007872").text)
        assertEquals("SIM 1", BidiText.isolateNumberRuns("SIM 1").text)
        assertEquals("Order 48291", BidiText.isolateNumberRuns("Order 48291").text)
    }

    @Test
    fun leavesLtrTextAlone() {
        assertEquals(
            "test 0999 862 0453",
            BidiText.isolateNumberRuns("test 0999 862 0453").text
        )
        assertEquals(
            "+155589935615",
            BidiText.isolateNumberRuns("+155589935615").text
        )
    }

    @Test
    fun remapShiftsStyledRangeOverIsolates() {
        val body = "کد 482 913 end"
        val otp = body.indexOf("482")..body.indexOf("913") + 2
        val iso = BidiText.isolateNumberRuns(body)
        val mapped = iso.remap(otp)
        assertEquals("$lri" + "482 913" + "$pdi", iso.text.substring(mapped.first, mapped.last + 1))
    }

    @Test
    fun excludedRunInsideLinkIsUntouched() {
        val body = "see http://x.io/12 34 now"
        val link = 4..17
        val iso = BidiText.isolateNumberRuns(body, listOf(link))
        assertEquals(body, iso.text)
    }
}
