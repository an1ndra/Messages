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
        val body = "کد 482 913 تمام"
        val otp = body.indexOf("482")..body.indexOf("913") + 2
        val iso = BidiText.isolateNumberRuns(body)
        val mapped = iso.remap(otp)
        assertEquals("$lri" + "482 913" + "$pdi", iso.text.substring(mapped.first, mapped.last + 1))
    }

    @Test
    fun isolatesAPersianLineBalanceWrittenInAsciiDigits() {
        // #345 screenshot: a Saman withdrawal renders the balance as
        // "78,556 مانده ,010" on 1.0.30 - the label lands inside the amount.
        // ASCII digits with ASCII commas are the same single run as the
        // Persian-digit form above, and the label must stay outside it on
        // either side.
        assertEquals(
            "مانده ${lri}78,556,010$pdi",
            BidiText.isolateNumberRuns("مانده 78,556,010").text
        )
        assertEquals(
            "${lri}78,556,010$pdi مانده",
            BidiText.isolateNumberRuns("78,556,010 مانده").text
        )
        // The full body the issue photographed: one line of RTL prose, one
        // balance line, then the stamp - each number contiguous.
        assertEquals(
            "بانک سامان\nبرداشت مبلغ\nخرید کالا\nمانده ${lri}78,556,010$pdi\n" +
                "بانک سامان\n${lri}1405/7/28$pdi",
            BidiText.isolateNumberRuns(
                "بانک سامان\nبرداشت مبلغ\nخرید کالا\nمانده 78,556,010\n" +
                    "بانک سامان\n1405/7/28"
            ).text
        )
    }

    @Test
    fun isolatesPersianDigitsWithArabicSeparators() {        // #345: a Saman Bank balance is written in Extended Arabic-Indic digits
        // with U+066C as the thousands separator. It has to be pinned LTR, or
        // the word "مانده" of the following clause is reordered into the
        // middle of the amount.
        assertEquals(
            "موجودی: ${lri}۱۲۳٬۴۵۶٬۷۸۹$pdi ریال",
            BidiText.isolateNumberRuns("موجودی: ۱۲۳٬۴۵۶٬۷۸۹ ریال").text
        )
        assertEquals(
            "مانده ${lri}۱٬۲۳۴٫۵۶$pdi",
            BidiText.isolateNumberRuns("مانده ۱٬۲۳۴٫۵۶").text
        )
    }

    @Test
    fun isolatesArabicIndicDigitsToo() {
        assertEquals(
            "الرصيد ${lri}١٢٣٬٤٥٦$pdi",
            BidiText.isolateNumberRuns("الرصيد ١٢٣٬٤٥٦").text
        )
    }

    @Test
    fun aPlainPersianDigitRunNeedsNoIsolate() {
        assertEquals("كد ۱۲۳۴۵۶", BidiText.isolateNumberRuns("كد ۱۲۳۴۵۶").text)
    }

    @Test
    fun aBankTimestampStaysInOnePiece() {
        // #345 follow-up: a Mellat stamp is "1405/07/30 - 16:59". ':59' used to
        // fall outside the isolate, so the minutes reordered away from the hour
        // and the colon came adrift.
        assertEquals(
            "بانک ملت\n${lri}1405/07/30 - 16:59$pdi",
            BidiText.isolateNumberRuns("بانک ملت\n1405/07/30 - 16:59").text
        )
        // The same stamp in Persian digits, and one written without spaces.
        assertEquals(
            "بانک ملت\n${lri}۱۴۰۵/۰۷/۳۰ - ۱۶:۵۹$pdi",
            BidiText.isolateNumberRuns("بانک ملت\n۱۴۰۵/۰۷/۳۰ - ۱۶:۵۹").text
        )
        assertEquals(
            "بانک ملت\n${lri}05/07/30-04:05$pdi",
            BidiText.isolateNumberRuns("بانک ملت\n05/07/30-04:05").text
        )
    }

    @Test
    fun aPasargadTimestampAndNegativeAmountStayWhole() {
        // Underscore-separated stamp, and a leading '-' that belongs to the
        // amount rather than to the Persian text around it.
        assertEquals(
            "شماره حساب ${lri}07/30_04:00$pdi",
            BidiText.isolateNumberRuns("شماره حساب 07/30_04:00").text
        )
        assertEquals(
            "واریز ${lri}-500,000$pdi",
            BidiText.isolateNumberRuns("واریز -500,000").text
        )
    }

    @Test
    fun excludedRunInsideLinkIsUntouched() {
        val body = "see http://x.io/12 34 now"
        val link = 4..17
        val iso = BidiText.isolateNumberRuns(body, listOf(link))
        assertEquals(body, iso.text)
    }
}
