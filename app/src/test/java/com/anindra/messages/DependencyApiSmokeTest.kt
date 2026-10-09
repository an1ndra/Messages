package com.anindra.messages

import androidx.core.app.NotificationCompat
import androidx.fragment.app.FragmentActivity
import coil3.request.ImageRequest
import com.anindra.messages.data.PhoneNumberUtils
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DependencyApiSmokeTest {

    @Test
    fun libphonenumberParsesAndFormatsNumbers() {
        val util = PhoneNumberUtil.getInstance()
        val fr = util.parse("06 12 34 56 78", "FR")
        assertTrue(util.isPossibleNumber(fr))
        assertEquals("+33612345678", util.format(fr, PhoneNumberUtil.PhoneNumberFormat.E164))

        val us = util.parse("650 555 1212", "US")
        assertTrue(util.isPossibleNumber(us))
        assertEquals("+16505551212", util.format(us, PhoneNumberUtil.PhoneNumberFormat.E164))
    }

    @Test
    fun phoneNumberUtilsDisplayAndRegionStayStable() {
        assertEquals("06 12 34 56 78", PhoneNumberUtils.displayFor("+33612345678", "FR"))
        assertEquals("(650) 555-1212", PhoneNumberUtils.displayFor("+16505551212", "US"))
        assertNull(PhoneNumberUtils.toE164("198", "IN"))
        assertTrue(PhoneNumberUtils.isDialableAddress("198"))
    }

    @Test
    fun orgJsonRoundTripsObjectsAndArrays() {
        val obj = JSONObject()
            .put("id", 42L)
            .put("body", "hello")
            .put("hidden", false)
        obj.put("parts", JSONArray().put(JSONObject().put("ct", "text/plain")))

        val parsed = JSONObject(obj.toString())
        assertEquals(42L, parsed.getLong("id"))
        assertEquals("hello", parsed.getString("body"))
        assertFalse(parsed.getBoolean("hidden"))
        assertEquals(1, parsed.getJSONArray("parts").length())
        assertEquals("text/plain", parsed.getJSONArray("parts").getJSONObject(0).getString("ct"))
    }

    @Test
    fun coil3ImageRequestBuilderIsAvailable() {
        assertNotNull(Class.forName("coil3.request.ImageRequest"))
        assertEquals("ImageRequest", ImageRequest::class.simpleName)
    }

    @Test
    fun androidxCoreNotificationCompatBuilderIsAvailable() {
        assertNotNull(Class.forName("androidx.core.app.NotificationCompat"))
        assertNotNull(NotificationCompat.Builder::class.java)
    }

    @Test
    fun androidxFragmentActivityIsAvailable() {
        assertNotNull(Class.forName("androidx.fragment.app.FragmentActivity"))
        assertNotNull(FragmentActivity::class.java)
    }
}
