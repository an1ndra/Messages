package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Bidi

class BidiTextTest {

    private val lri = BidiText.LRI
    private val pdi = BidiText.PDI

    /** What the platform actually paints: the string in visual order, as the
     *  Unicode bidi algorithm reorders it for the given paragraph direction. */
    private fun painted(text: String, rtl: Boolean): String {
        val bidi = Bidi(
            text,
            if (rtl) Bidi.DIRECTION_RIGHT_TO_LEFT else Bidi.DIRECTION_LEFT_TO_RIGHT
        )
        val levels = ByteArray(text.length) { bidi.getLevelAt(it).toByte() }
        val chars = Array<Any>(text.length) { text[it].toString() }
        Bidi.reorderVisually(levels, 0, chars, 0, text.length)
        return chars.joinToString("")
    }

    /** The painted string without the invisible bidi controls, i.e. what the
     *  reader actually sees. */
    private fun String.visible(): String =
        filterNot { it == lri || it == pdi || it in '\u200E'..'\u200F' }

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
    fun aMaskedCardNumberStaysOneRun() {
        // #345 follow-up: a Pasargad card transfer carries masked card numbers -
        // digits around Latin 'x' masks. The letters are strong LTR, so while
        // they stayed outside the number run the digits reordered around them
        // and only read correctly once selected.
        assertEquals(
            "کارت مبدا:\n${lri}5022-29xx-xxxx-9227$pdi\nکارت مقصد:",
            BidiText.isolateNumberRuns("کارت مبدا:\n5022-29xx-xxxx-9227\nکارت مقصد:").text
        )
        // Star masks are the same shape.
        assertEquals(
            "کارت مقصد:\n${lri}6104-33**-****-8284$pdi\nمانده:",
            BidiText.isolateNumberRuns("کارت مقصد:\n6104-33**-****-8284\nمانده:").text
        )
    }

    @Test
    fun thePasargadCardTransferBodyStaysContiguous() {
        // The reporter's photographed body, verbatim: both card masks and the
        // timestamp stay one contiguous block each. The bare account/amount
        // digits ("مبلغ:00000000") have no separator inside, so a run isolate
        // would change nothing - they stay plain, like every pure digit run.
        assertEquals(
            "کارت مبدا:\n${lri}5022-29xx-xxxx-9227$pdi\n" +
                "کارت مقصد:\n${lri}6104-33xx-xxxx-8284$pdi\n" +
                "نام دریافت کننده: \n" +
                "مبلغ:00000000 ریال\n" +
                "تاریخ انتقال:${lri}22:20:51 1405/08/03$pdi",
            BidiText.isolateNumberRuns(
                "کارت مبدا:\n5022-29xx-xxxx-9227\n" +
                    "کارت مقصد:\n6104-33xx-xxxx-8284\n" +
                    "نام دریافت کننده: \n" +
                    "مبلغ:00000000 ریال\n" +
                    "تاریخ انتقال:22:20:51 1405/08/03"
            ).text
        )
    }

    @Test
    fun aLineOfOnlyEmojiIsPinnedLtrAndEmojiDoNotVote() {
        // #362 follow-up: an emoji line in a Persian message is a line with no
        // strong character, so it gets the same LTR pin as a bare amount.
        val body = "کد تخفیف را ثبت کن\n🎁🎁"
        val iso = BidiText.isolateNumberRuns(body)
        assertEquals(BidiText.Direction.RTL, iso.direction)
        assertEquals("کد تخفیف را ثبت کن\n${lri}🎁🎁$pdi", iso.text)
        assertEquals(1, iso.ltrLines)
    }

    @Test
    fun excludedRunInsideLinkIsUntouched() {
        val body = "see http://x.io/12 34 now"
        val link = 4..17
        val iso = BidiText.isolateNumberRuns(body, listOf(link))
        assertEquals(body, iso.text)
    }

    @Test
    fun aLineOfOnlyNumbersOrLinksIsPinnedLtr() {
        // #362 follow-up: inside a Persian message, a line that is nothing but a
        // link (or a bare amount, or "@handle #123") must still read left to
        // right — base RTL would otherwise put it on the wrong edge.
        val body = "کد ورود به صرافی تبدیل:\n85904\n\n@tabdeal.org #85904\nلغو11"
        val out = BidiText.isolateNumberRuns(body).text
        assertEquals(
            "کد ورود به صرافی تبدیل:\n${lri}85904$pdi\n\n${lri}@tabdeal.org #85904$pdi\nلغو11",
            out
        )
        assertEquals(2, BidiText.isolateNumberRuns(body).ltrLines)
    }

    @Test
    fun aBareLinkLineInAPersianBroadcastIsPinnedLtr() {
        val body = "📣ارسال تپسی‌شاپ رایگان شد\nبه مدت محدود\nl.tpshp.co/3lDXoA\nلغو 11r.eu/t"
        val iso = BidiText.isolateNumberRuns(body)
        assertEquals(BidiText.Direction.RTL, iso.direction)
        assertEquals("📣ارسال تپسی‌شاپ رایگان شد\nبه مدت محدود\n${lri}l.tpshp.co/3lDXoA$pdi\nلغو 11r.eu/t", iso.text)
        assertEquals(1, iso.ltrLines)
        // The link's digits stay where they are: the line isolate already pins
        // the whole line, and a second isolate inside it would nest for nothing.
        assertEquals("لغو 11r.eu/t", iso.text.substringAfterLast('\n'))
    }

    @Test
    fun aGroupedAmountWithDecimalsIsIsolatedWhole() {
        // #395: on 1.0.30 the run regex had no ',' in its middle class, so
        // "20,980.01" matched as two runs ("20" and "980.01") and each was
        // isolated on its own. Two adjacent isolates in an RTL paragraph paint
        // right-to-left, so the amount came out as "980.01,20".
        assertEquals(
            "مبلغ ${lri}20,980.01$pdi ریال",
            BidiText.isolateNumberRuns("مبلغ 20,980.01 ریال").text
        )
        assertEquals(
            "הסכום ${lri}20,980.01$pdi שח",
            BidiText.isolateNumberRuns("הסכום 20,980.01 שח").text
        )
    }

    @Test
    fun theGroupedAmountPaintsInReadingOrderInAnRtlBubble() {
        val iso = BidiText.isolateNumberRuns("مبلغ 20,980.01 ریال")
        assertEquals(BidiText.Direction.RTL, iso.direction)
        assertTrue(painted(iso.text, rtl = true).contains("20,980.01"))
        assertFalse(painted(iso.text, rtl = true).contains("980.01,20"))
        // The split form is exactly what the report photographed: two isolates
        // cannot hold a grouped number together, and the assertion above is only
        // worth anything if this really is how it breaks. The isolates
        // themselves sit between the runs, so compare what is left on screen
        // once the invisible controls are dropped.
        val split = painted("مبلغ ${lri}20$pdi,${lri}980.01$pdi ریال", rtl = true).visible()
        assertTrue(split, split.contains("980.01,20"))
    }

    @Test
    fun aSpaceGroupedAmountIsIsolatedToo() {
        // A plain space between the groups is a neutral, not a number separator,
        // so Unicode splits the amount there even without any isolate.
        assertEquals(
            "مبلغ ${lri}20 980.01$pdi ریال",
            BidiText.isolateNumberRuns("مبلغ 20 980.01 ریال").text
        )
        assertFalse(painted("مبلغ 20 980.01 ریال", rtl = true).contains("20 980.01"))
    }
}
