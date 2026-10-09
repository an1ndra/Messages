package com.anindra.messages

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Other apps (bank payment receipts, share-to-SMS) must be able to hand a
 * message to us. The platform only shows an activity in the share sheet when
 * its manifest filter matches the intent exactly.
 */
class ManifestShareIntentFilterTest {
    private val manifest: Element by lazy {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.newDocumentBuilder().parse(locateManifest()).documentElement
    }

    private fun elements(tag: String): List<Element> {
        val nodes = manifest.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun mainActivityFilters(): List<Element> {
        val activities = elements("activity")
            .filter { it.getAttributeNS(ANDROID_NS, "name") == ".MainActivity" }
        assertTrue("MainActivity not found", activities.isNotEmpty())
        return activities.flatMap { activity ->
            (0 until activity.childNodes.length)
                .map { activity.childNodes.item(it) }
                .filterIsInstance<Element>()
                .filter { it.tagName == "intent-filter" }
        }
    }

    private fun filterMatchesAction(filters: List<Element>, action: String): List<Element> {
        return filters.filter { filter ->
            (0 until filter.childNodes.length)
                .map { filter.childNodes.item(it) }
                .filterIsInstance<Element>()
                .any { it.tagName == "action" && it.getAttributeNS(ANDROID_NS, "name") == action }
        }
    }

    private fun Element.schemeValues(): Set<String> {
        return (0 until childNodes.length)
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName == "data" && it.hasAttributeNS(ANDROID_NS, "scheme") }
            .map { it.getAttributeNS(ANDROID_NS, "scheme") }
            .toSet()
    }

    private fun Element.mimeTypeValues(): Set<String> {
        return (0 until childNodes.length)
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName == "data" && it.hasAttributeNS(ANDROID_NS, "mimeType") }
            .map { it.getAttributeNS(ANDROID_NS, "mimeType") }
            .toSet()
    }

    @Test
    fun mainActivityHandlesSendToForAllSmsSchemes() {
        val sendto = filterMatchesAction(mainActivityFilters(), "android.intent.action.SENDTO")
        assertTrue("MainActivity has no SENDTO filter", sendto.isNotEmpty())
        val schemes = sendto.flatMap { it.schemeValues() }.toSet()
        assertTrue(
            "SENDTO filter must cover sms/smsto/mms/mmsto, found: $schemes",
            schemes.containsAll(setOf("sms", "smsto", "mms", "mmsto")),
        )
    }

    @Test
    fun mainActivityHandlesSendForPlainText() {
        val send = filterMatchesAction(mainActivityFilters(), "android.intent.action.SEND")
        assertTrue("MainActivity has no ACTION_SEND filter", send.isNotEmpty())
        val mimeTypes = send.flatMap { it.mimeTypeValues() }.toSet()
        assertTrue(
            "ACTION_SEND filter must accept text/plain, found: $mimeTypes",
            "text/plain" in mimeTypes,
        )
    }

    private fun locateManifest(): File {
        val rel = "app/src/main/AndroidManifest.xml"
        val start = System.getProperty("user.dir") ?: "."
        var dir: File? = File(start).absoluteFile
        while (dir != null) {
            val candidate = File(dir, rel)
            if (candidate.isFile) return candidate
            val nested = File(dir, "src/main/AndroidManifest.xml")
            if (nested.isFile) return nested
            dir = dir.parentFile
        }
        throw AssertionError("could not locate AndroidManifest.xml from $start")
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
