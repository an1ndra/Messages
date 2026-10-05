package com.anindra.messages.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A settings footer is a note about the group above it. These guard the two ways
 * that goes wrong: the string goes missing from a translation, or it restates a
 * row instead of adding anything.
 */
class SettingsFooterStringsTest {

    private val resDir: File by lazy {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "src/main/res").isDirectory) dir = dir.parentFile
        dir?.let { File(it, "src/main/res") } ?: error("src/main/res not found")
    }

    private val base: Map<String, String> by lazy { readStrings(File(resDir, "values")) }

    /** Each footer's key, and the rows it sits underneath. */
    private val footers = mapOf(
        "settings_link_behaviour_footer" to listOf(
            "settings_advanced_hide_links", "settings_advanced_highlight_links",
            "settings_advanced_link_warning", "settings_link_tap_info",
            "settings_advanced_hide_links_desc", "link_warning_confirm",
        ),
        "settings_notif_footer" to listOf(
            "settings_notif_title", "settings_notif_subtitle",
            "settings_pin_notification_sound", "settings_sound_default",
        ),
        "settings_auto_delete_footer" to listOf(
            "settings_retention_title", "settings_retention_blocked",
            "settings_retention_keep_spam",
        ),
        "settings_accessibility_footer" to listOf(
            "settings_accessibility_title", "accessibility_options_title",
            "a11y_bold_title", "a11y_reduce_motion_title",
        ),
        "settings_inbox_footer" to listOf(
            "settings_inbox_title", "settings_archiving_title", "settings_pinned_title",
            "swipe_left_title", "swipe_right_title", "settings_unread_top_title",
            "settings_forwarding_title", "settings_blocking_title",
            "settings_scheduled_title", "settings_delayed_title",
        ),
    )

    @Test
    fun everyFooterIsDefinedAndNotBlank() {
        for (key in footers.keys) {
            val text = base[key]
            assertTrue("$key is missing from values/strings_settings.xml", text != null)
            assertTrue("$key is blank", !text!!.isBlank())
        }
    }

    @Test
    fun everyFooterIsShippedToEveryLocale() {
        // TranslationParityTest covers key parity; this catches a footer that was
        // added to values/ only, which parity alone would not explain.
        val locales = resDir.listFiles { f: File ->
            f.isDirectory && f.name.matches(Regex("values-[a-z]{2}(-r[A-Z]{2})?"))
        }.orEmpty()
        assertTrue("no locales found", locales.isNotEmpty())
        for (dir in locales) {
            val strings = readStrings(dir)
            for (key in footers.keys) {
                assertTrue("${dir.name} is missing $key", strings.containsKey(key))
                assertTrue("${dir.name} has a blank $key", !strings[key]!!.isBlank())
            }
        }
    }

    @Test
    fun noFooterJustRestatesTheRowsAboveIt() {
        // A footer that repeats a row title or subtitle is dead weight: the row
        // already says it. Compare on normalised words so punctuation and case
        // differences do not hide a genuine copy.
        for ((key, rows) in footers) {
            val footerWords = words(base[key]!!)
            for (row in rows) {
                val rowText = base[row] ?: continue
                assertTrue(
                    "$key is a copy of $row",
                    footerWords != words(rowText)
                )
            }
        }
    }

    @Test
    fun footersAreDistinctFromEachOther() {
        // Each page gets its own note; a copy-paste would leave one page's
        // explanation describing another's options.
        val seen = mutableMapOf<Set<String>, String>()
        for (key in footers.keys) {
            val normalised = words(base[key]!!)
            val previous = seen[normalised]
            assertTrue("$key duplicates $previous", previous == null)
            seen[normalised] = key
        }
    }

    @Test
    fun everyRowACoveredFooterSitsUnderStillExists() {
        // If a row is renamed, its key stops resolving and the footer silently
        // stops covering it. Fail here rather than shipping a page whose note no
        // longer matches the options above it.
        val missing = footers.flatMap { (footer, rows) ->
            rows.filter { !base.containsKey(it) }.map { "$footer -> $it" }
        }
        assertTrue("unknown row keys: $missing", missing.isEmpty())
    }

    private fun words(s: String): Set<String> =
        s.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .split(' ')
            .filter { it.length > 3 }
            .toSet()

    private fun readStrings(dir: File): Map<String, String> {
        val result = mutableMapOf<String, String>()
        dir.listFiles { f: File -> f.name.startsWith("strings") && f.name.endsWith(".xml") }
            .orEmpty()
            .forEach { file ->
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                val nodes = doc.getElementsByTagName("string")
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as org.w3c.dom.Element
                    result[el.getAttribute("name")] = el.textContent
                }
            }
        return result
    }
}
