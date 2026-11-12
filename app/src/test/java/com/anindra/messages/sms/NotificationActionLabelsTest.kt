package com.anindra.messages.sms

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * #452: the notification's Reply and Mark as read buttons were built from
 * hardcoded English literals, so they stayed English while Delete followed the
 * language picked in the app. Every action label is a resource now.
 */
class NotificationActionLabelsTest {

    private val res: File by lazy { LocaleCatalog.resDir() }

    private val sms: String by lazy {
        File(res.parentFile, "java/com/anindra/messages/sms/SmsSupport.kt").readText()
    }

    private fun strings(dir: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(res, "$dir/strings_main.xml")).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val el = nodes.item(it) as Element
            el.getAttribute("name") to el.textContent
        }
    }

    private fun value(key: String, dir: String): String? = strings(dir)[key]

    private val labels = listOf("notif_action_reply", "notif_action_mark_read", "notif_action_delete")

    @Test
    fun everyActionLabelComesFromAResource() {
        for (key in labels) {
            assertTrue(
                "$key must be wired through R.string, not an English literal",
                sms.contains("R.string.$key")
            )
        }
    }

    @Test
    fun noActionLabelIsAHardcodedLiteral() {
        for (literal in listOf("setLabel(\"Reply\")", "ic_reply, \"Reply\"", "0, \"Mark as read\"")) {
            assertFalse("hardcoded notification action label: $literal", sms.contains(literal))
        }
    }

    @Test
    fun everyShippedLocaleTranslatesEveryActionLabel() {
        val missing = mutableListOf<String>()
        for (dir in listOf(File(res, "values")) + LocaleCatalog.localeDirs(res)) {
            for (key in labels) {
                val v = value(key, dir.name)
                if (v == null) missing += "${dir.name}: $key absent"
                else if (v.isBlank()) missing += "${dir.name}: $key blank"
            }
        }
        assertTrue("untranslated notification action labels: $missing", missing.isEmpty())
    }

    @Test
    fun noLocaleFallsBackToTheEnglishLabel() {
        val english = strings("values")
        val same = mutableListOf<String>()
        for (dir in LocaleCatalog.localeDirs(res)) {
            for (key in labels) {
                val translated = value(key, dir.name)
                if (translated != null && translated == english[key]) same += "${dir.name}: $key"
            }
        }
        assertEquals("labels identical to English: $same", emptyList<String>(), same)
    }

    @Test
    fun thePersianLabelsAreTranslated() {
        // The reporter's language is the one that proved the bug.
        assertNotNull(value("notif_action_reply", "values-fa"))
        assertTrue(value("notif_action_reply", "values-fa") != "Reply")
        assertTrue(value("notif_action_mark_read", "values-fa") != "Mark as read")
    }
}
