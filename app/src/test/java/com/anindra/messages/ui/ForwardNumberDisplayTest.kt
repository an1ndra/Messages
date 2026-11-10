package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The forward picker paints a contact's number the way the rest of the app
 * does, not the way the Contacts provider happens to spell it (#405
 * follow-up).
 *
 * The reporter's Samsung phone book showed "BENZ Manocheher" saved without
 * dashes, while the picker rendered "091-210-4…", "300-033-03" and
 * "671-5014". The provider had stored separators and the picker painted
 * `Contact.number` verbatim, so the list disagreed with the phone book it had
 * just read. Formatting is display-only: the raw number is still what the
 * picker hands back when the row is tapped.
 */
class ForwardNumberDisplayTest {

    private fun painted(raw: String): String =
        formatPhoneNumber(raw, "IR").filter { it != BidiText.LRI && it != BidiText.PDI }

    @Test
    fun providerSeparatorsAreNotPainted() {
        assertEquals("0912104456", painted("091-210-4456"))
        assertEquals("30003303", painted("300-033-03"))
        assertEquals("6715014", painted("671-5014"))
        assertFalse(painted("091-210-4456").contains('-'))
    }

    @Test
    fun aLandlineGetsTheCanonicalNationalSpacing() {
        assertEquals("021 6600 0000", painted("021-660-00000"))
    }

    @Test
    fun aDialStringIsLeftExactlyAsStored() {
        // libphonenumber reads "*21*2258#" as the digits 212258 and would paint
        // "021 2258" — a USSD code is not a phone number to it.
        assertEquals("*21*2258#", painted("*21*2258#"))
        assertEquals("*140*11#", painted("*140*11#"))
        assertEquals("#21#", painted("#21#"))
    }

    @Test
    fun thePickerPaintsTheFormattedNumberButSendsTheStoredOne() {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var source: String? = null
        while (dir != null && source == null) {
            val candidate = File(dir, "app/src/main/java/com/anindra/messages/ui/ChatScreen.kt")
            if (candidate.isFile) source = candidate.readText()
            dir = dir.parentFile
        }
        val picker = source!!.substringAfter("internal fun ForwardPicker(")
            .substringBefore("\n@Composable")
        assertTrue(
            "the picker must paint the formatted number",
            picker.contains("formatPhoneNumber(target.number)"),
        )
        assertTrue(
            "the picker must still send the stored number, not the painted one",
            picker.contains("onPick(target.number"),
        )
    }
}
