package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OtpDetectorTest {

    private fun picked(body: String) = OtpDetector.findRanges(body).map { body.substring(it) }

    @Test
    fun keywordBeforeDigits() {
        assertEquals(listOf("482913"), picked("Your OTP is 482913"))
        assertEquals(listOf("443322"), picked("Your code: 443322."))
        assertEquals(listOf("4829"), picked("PIN: 4829"))
    }

    @Test
    fun keywordAfterDigits() {
        assertEquals(listOf("482913"), picked("482913 is your verification code"))
        assertEquals(listOf("752913"), picked("G-752913 is your Google verification code"))
        assertEquals(listOf("1122"), picked("1122 is your PIN"))
    }

    @Test
    fun groupedDigitsWithKeyword() {
        assertEquals(listOf("482 913"), picked("OTP: 482 913"))
        assertEquals(listOf("4433-2211"), picked("Your passcode 4433-2211 expires soon"))
    }

    @Test
    fun bareSixDigitsNeedsStrongKeyword() {
        assertEquals(listOf("482913"), picked("HDFC Bank: OTP for tx of Rs.2500 is 482913."))
        assertEquals(listOf("112233"), picked("Use 112233 to verify your login"))
    }

    @Test
    fun nonOtpNumbersIgnored() {
        assertEquals(emptyList<String>(), picked("Paid ₹12500 to merchant"))
        assertEquals(emptyList<String>(), picked("Order 48291 shipped"))
        assertEquals(emptyList<String>(), picked("Meeting at 1430 hrs"))
        assertEquals(emptyList<String>(), picked("Balance 2500 due Friday"))
        assertEquals(emptyList<String>(), picked("Copyright 2024 code review"))
    }

    @Test
    fun currencyGuardSuppressesMoneyNearKeyword() {
        assertEquals(emptyList<String>(), picked("Refund code ₹4500 processed"))
    }

    @Test
    fun multipleOtpsAllFound() {
        assertEquals(listOf("111222", "333444"), picked("OTP 111222 backup code 333444"))
    }

    @Test
    fun yearKeptWhenDirectlyAfterKeyword() {
        assertEquals(listOf("2024"), picked("Enter PIN 2024 to confirm"))
    }
}
