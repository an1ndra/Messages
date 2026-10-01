package com.anindra.messages

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Google Play filters out apps whose permissions imply a hardware feature the
 * device lacks, unless the matching <uses-feature> opts out with
 * required="false" (lint: PermissionImpliesUnsupportedChromeOsHardware).
 * Every SMS/MMS/phone-state permission implies android.hardware.telephony, so
 * declaring that feature as optional is what keeps the app installable on
 * ChromeOS and telephony-less tablets, where it falls back to the simulated SIM.
 */
class ManifestTelephonyFeatureTest {
    private val manifest: Element by lazy {
        val factory = DocumentBuilderFactory.newInstance()
        // Required: without this getAttributeNS() on android:* attributes returns "".
        factory.isNamespaceAware = true
        factory.newDocumentBuilder().parse(locateManifest()).documentElement
    }

    private fun elements(tag: String): List<Element> {
        val nodes = manifest.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    /** Permissions the platform treats as implying android.hardware.telephony. */
    private val telephonyImplyingPermissions = setOf(
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
        "android.permission.WRITE_SMS",
        "android.permission.RECEIVE_MMS",
        "android.permission.RECEIVE_WAP_PUSH",
        "android.permission.READ_PHONE_STATE",
    )

    private fun declaredPermissions(): Set<String> =
        elements("uses-permission").mapTo(mutableSetOf()) { it.getAttributeNS(ANDROID_NS, "name") }

    private fun telephonyFeatures(): List<Element> =
        elements("uses-feature").filter { it.getAttributeNS(ANDROID_NS, "name") == "android.hardware.telephony" }

    @Test
    fun telephonyIsDeclaredExactlyOnce() {
        assertEquals(
            "expected exactly one <uses-feature android:name=\"android.hardware.telephony\"/>",
            1,
            telephonyFeatures().size,
        )
    }

    @Test
    fun telephonyFeatureIsNotRequired() {
        val feature = telephonyFeatures().single()
        assertEquals("false", feature.getAttributeNS(ANDROID_NS, "required"))
    }

    @Test
    fun usesFeatureSitsOutsideApplicationBeforeIt() {
        val order = (0 until manifest.childNodes.length)
            .map { manifest.childNodes.item(it) }
            .filterIsInstance<Element>()
        val featureIndex = order.indexOfFirst { it.tagName == "uses-feature" }
        val appIndex = order.indexOfFirst { it.tagName == "application" }
        assertTrue("manifest declares no direct <uses-feature> child", featureIndex >= 0)
        assertTrue("<uses-feature> must be declared before <application>", featureIndex < appIndex)
    }

    @Test
    fun telephonyImplyingPermissionsAreOptedOut() {
        val implying = declaredPermissions().intersect(telephonyImplyingPermissions)
        assertTrue(
            "no telephony-implying permission found; the test is not exercising anything",
            implying.isNotEmpty(),
        )
        val optedOut = telephonyFeatures().any { it.getAttributeNS(ANDROID_NS, "required") == "false" }
        assertTrue(
            "these permissions imply android.hardware.telephony and need a " +
                "<uses-feature required=\"false\"> opt-out: $implying",
            optedOut,
        )
    }

    @Test
    fun requiredAttributeIsSpelledOnTheFeature() {
        // A bare <uses-feature android:name="..."/> defaults to required="true",
        // which is exactly the bug: Play would hide the app from no-telephony devices.
        val feature = telephonyFeatures().single()
        assertTrue(
            "android:required must be present and explicitly \"false\"",
            feature.hasAttributeNS(ANDROID_NS, "required"),
        )
    }

    private fun locateManifest(): File {
        val rel = "app/src/main/AndroidManifest.xml"
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, rel)
            if (candidate.isFile) return candidate
            val nested = File(dir, "src/main/AndroidManifest.xml")
            if (nested.isFile) return nested
            dir = dir.parentFile
        }
        throw AssertionError("could not locate AndroidManifest.xml from ${System.getProperty("user.dir")}")
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
