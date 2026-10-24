package com.anindra.messages.ui

import com.anindra.messages.ui.BidiText.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #362: a message's layout direction comes from its first and last strong
 *  characters, trailing punctuation and digits never voting; the composer's
 *  direction comes from the first strong character alone. */
class BidiTextDirectionTest {

    @Test
    fun plainPersianAndArabicAreRtl() {
        assertEquals(Direction.RTL, BidiText.direction("سلام خوبی؟"))
        assertEquals(Direction.RTL, BidiText.direction("الرصيد"))
        assertEquals(Direction.RTL, BidiText.direction("שלום"))
    }

    @Test
    fun plainLatinIsLtr() {
        assertEquals(Direction.LTR, BidiText.direction("See you at 8"))
        assertEquals(Direction.LTR, BidiText.direction("OK"))
    }

    @Test
    fun trailingPunctuationDoesNotVote() {
        assertEquals(Direction.RTL, BidiText.direction("سلام!"))
        assertEquals(Direction.LTR, BidiText.direction("hello!"))
        assertEquals(Direction.RTL, BidiText.direction("سلام..."))
        assertEquals(Direction.RTL, BidiText.direction("خوبی؟"))
        assertEquals(Direction.RTL, BidiText.direction("سلام،"))
        assertEquals(Direction.LTR, BidiText.direction("hello,"))
    }

    @Test
    fun trailingNumberDoesNotVote() {
        assertEquals(Direction.LTR, BidiText.direction("Call me at +98 999 862 0453"))
        assertEquals(Direction.RTL, BidiText.direction("شماره من ۱۲۳۴۵۶ است ۰۹۱۲"))
        assertEquals(Direction.RTL, BidiText.direction("کد 482 913: "))
    }

    @Test
    fun endsDecideWhenTheTwoEndsDisagree() {
        // Last strong character wins, so a message that ends in Persian is laid
        // out as Persian even though it opens in English (Google Messages).
        assertEquals(Direction.RTL, BidiText.direction("Hi سلام"))
        assertEquals(Direction.RTL, BidiText.direction("Order 48291 تکمیل شد"))
        // ...and the inverse, the mirror of the same rule.
        assertEquals(Direction.LTR, BidiText.direction("سلام Hi"))
        assertEquals(Direction.LTR, BidiText.direction("بریم OK"))
    }

    @Test
    fun agreeingEndsDecideEvenWithTheOtherScriptInBetween() {
        assertEquals(Direction.LTR, BidiText.direction("a سلام b"))
        assertEquals(Direction.RTL, BidiText.direction("سلام hi خوبی"))
    }

    @Test
    fun noStrongCharacterFallsBackToLtr() {
        assertEquals(Direction.LTR, BidiText.direction(""))
        assertEquals(Direction.LTR, BidiText.direction("   "))
        assertEquals(Direction.LTR, BidiText.direction("۱۲۳۴۵۶"))
        assertEquals(Direction.LTR, BidiText.direction("+98 999 862 0453"))
        assertEquals(Direction.LTR, BidiText.direction("👍🎉"))
        assertEquals(Direction.LTR, BidiText.direction("@Lock"))
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

    @Test
    fun numberIsolationFollowsTheSameDirectionAsTheBubble() {
        // First strong is Latin, last strong is Persian, so the bubble renders
        // RTL — and the number has to be isolated the same way it would be in
        // an unambiguously RTL message, or it reorders once painted.
        val body = "Hi سلام ۱۲۳٬۴۵۶"
        assertEquals(Direction.RTL, BidiText.direction(body))
        assertTrue(
            BidiText.isolateNumberRuns(body).text
                .contains("${BidiText.LRI}۱۲۳٬۴۵۶${BidiText.PDI}")
        )
        // ...while an LTR message is still left alone.
        assertEquals("balance: ۱۲۳ 456 rials", BidiText.isolateNumberRuns("balance: ۱۲۳ 456 rials").text)
    }
}
