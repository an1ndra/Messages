package com.anindra.messages.ui

import com.anindra.messages.ui.BidiText.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #362: every line is laid out in the direction of its own first strong
 *  character — the rule Google Messages and Fossify both apply, checked against
 *  them on `emulator-5558` — so Persian prose and an LTR "11:24 PM" line can
 *  live in the same bubble. */
class BidiTextDirectionTest {

    @Test
    fun plainPersianAndArabicAreRtl() {
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("سلام خوبی؟"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("الرصيد"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("שלום"))
    }

    @Test
    fun plainLatinIsLtr() {
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("See you at 8"))
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("OK"))
    }

    @Test
    fun punctuationDoesNotVote() {
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("سلام!"))
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("hello!"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("سلام..."))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("خوبی؟"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("سلام،"))
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("hello,"))
    }

    @Test
    fun numbersDoNotVote() {
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("Call me at +98 999 862 0453"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("شماره من ۱۲۳۴۵۶ است ۰۹۱۲"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("کد 482 913: "))
        // A leading amount is weak, so the Persian after it still decides — the
        // shape of both samples in the issue's second follow-up.
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("60374356بنام امید"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("واريز400,000"))
    }

    @Test
    fun theFirstStrongCharacterWinsNotTheLast() {
        // #362 follow-up: last-strong-wins laid the receipt out LTR because
        // "11:24 PM" sat last, and the bank SMS because of "bki. ir".
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("Order 48291 تکمیل شد"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("سلام Hi"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("بریم OK"))
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("Hi سلام"))
        // Neither end matters on its own; only the head does.
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("a سلام b"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("سلام hi خوبی"))
    }

    @Test
    fun noStrongCharacterFallsBackToLtr() {
        assertEquals(Direction.LTR, BidiText.firstStrongDirection(""))
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("   "))
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("۱۲۳۴۵۶"))
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("+98 999 862 0453"))
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("👍🎉"))
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("@Lock"))
    }

    @Test
    fun emojiNeverVote() {
        // #362 follow-up: Character.getDirectionality(char) reports a lone
        // surrogate as LTR, so 📣 at the end of a Persian message was the last
        // "strong" character and flipped the whole bubble. Flags are two
        // regional indicators, which Unicode really does class as L.
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("ارسال رایگان شد 📣"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("📣 ارسال رایگان شد"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("🎁 1 گیگ اینترنت هدیه با پرداخت هر قبض خدماتی! 🎁👇"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("سلام ❤️"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("سلام 🇮🇷🇺🇸"))
        // ...and the inverse: emoji do not make an English message Persian.
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("hello 📣"))
        // Nothing strong at all is still LTR.
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("📣🎁🇮🇷"))
        // The composer has the same trap, since a draft can start on an emoji.
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("📣 سلام"))
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("📣 hello"))
    }

    @Test
    fun linksDoNotVote() {
        // #362 follow-up: a Persian broadcast whose last line is a bare link.
        assertEquals(
            Direction.RTL,
            BidiText.firstStrongDirection("📣ارسال تپسی‌شاپ رایگان شد\nبه مدت محدود\nl.tpshp.co/3lDXoA\nلغو 11r.eu/t")
        )
        assertEquals(
            Direction.RTL,
            BidiText.firstStrongDirection("🎁 1 گیگ اینترنت هدیه\nhttps://zbl.io/RogeAbne")
        )
        // ...as does a message that is nothing but a link.
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("https://zbl.io/RogeAbne"))
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("l.tpshp.co/3lDXoA"))
        // A dotted abbreviation is not a link, so the Latin "e" votes first.
        assertEquals(emptyList<IntRange>(), BidiText.linkRanges("e.g سلام"))
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("e.g سلام"))
    }

    @Test
    fun linksAreFoundInBothShapesAnSmsCarries() {
        fun found(s: String) = BidiText.linkRanges(s).map { s.substring(it) }
        assertEquals(listOf("https://zbl.io/RogeAbne"), found("https://zbl.io/RogeAbne"))
        assertEquals(listOf("11r.eu/t"), found("لغو 11r.eu/t"))
        assertEquals(listOf("@tabdeal.org"), found("@tabdeal.org #85904"))
        assertEquals(emptyList<String>(), found("e.g. fine"))
    }

    @Test
    fun composerFollowsTheFirstStrongCharacterOnly() {
        assertEquals(Direction.LTR, BidiText.firstStrongDirection(""))
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("123"))
        // Digits are weak, so the first Persian letter is still what flips it.
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("  123 سلام"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("سلام hello"))
        assertEquals(Direction.RTL, BidiText.firstStrongDirection("  سلام"))
        assertEquals(Direction.LTR, BidiText.firstStrongDirection("hello سلام"))
    }

    /** The two messages the issue's second follow-up left broken. Every line is
     *  RTL except the bare digits/timestamps/domain ones, which Google Messages
     *  and Fossify both keep LTR. */
    @Test
    fun theReportsDeliveryReceiptIsRtlLineByLine() {
        val body = "60374356بنام امید\nفرستنده:\n+989015000000\nاستفاده شده SIM:\n" +
            "سیم ۱\nارسال شده:\n18.06\n11:24 PM"
        assertEquals(
            listOf(
                Direction.RTL, Direction.RTL, Direction.LTR, Direction.RTL,
                Direction.RTL, Direction.RTL, Direction.LTR, Direction.LTR
            ),
            BidiText.lines(body).map { it.direction }
        )
    }

    @Test
    fun theReportsBankSmsIsRtlLineByLine() {
        val body = "واريز400,000\nمانده1,165,755\n050705-19:01\nکارت8217*\nbki. ir"
        assertEquals(
            listOf(Direction.RTL, Direction.RTL, Direction.LTR, Direction.RTL, Direction.LTR),
            BidiText.lines(body).map { it.direction }
        )
    }

    @Test
    fun aLineIsJudgedOnItsOwnHeadNotTheMessage() {
        // "سلام abc." opens Persian: RTL, and the "." moves to the visual start.
        assertEquals(listOf(Direction.RTL), BidiText.lines("سلام abc.").map { it.direction })
        // "11:24 PM." opens Latin: LTR, "." stays at the visual end.
        assertEquals(listOf(Direction.LTR), BidiText.lines("11:24 PM.").map { it.direction })
        // A bare domain is a link and leaves no strong character outside it.
        assertEquals(listOf(Direction.LTR), BidiText.lines("bki. ir").map { it.direction })
    }

    @Test
    fun numberIsolationFollowsTheLineItSitsOn() {
        // A message whose only line is Latin is painted LTR as a whole, so the
        // transform leaves it completely alone.
        assertEquals("Hi سلام ۱۲۳٬۴۵۶", BidiText.isolateNumberRuns("Hi سلام ۱۲۳٬۴۵۶").text)
        assertEquals("balance: ۱۲۳ 456 rials", BidiText.isolateNumberRuns("balance: ۱۲۳ 456 rials").text)
        // On a Persian line the number is isolated so it keeps its order.
        val iso = BidiText.isolateNumberRuns("سلام ۱۲۳٬۴۵۶")
        assertTrue(iso.text.contains("${BidiText.LRI}۱۲۳٬۴۵۶${BidiText.PDI}"))
    }

    @Test
    fun onlyTheRtlLinesOfAMixedMessageAreIsolated() {
        val body = "مانده 78,556,010\n11:24 PM"
        val iso = BidiText.isolateNumberRuns(body)
        assertEquals(listOf(Direction.RTL, Direction.LTR), iso.lines)
        assertTrue(iso.text.contains("${BidiText.LRI}78,556,010${BidiText.PDI}"))
        // The LTR line is pinned whole — never split into per-number isolates,
        // which would pull "11:24" and "PM" apart inside it.
        assertTrue(iso.text.endsWith("${BidiText.LRI}11:24 PM${BidiText.PDI}"))
    }
}
