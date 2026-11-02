package com.anindra.messages.i18n

import com.anindra.messages.ui.BidiText
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Persian (`fa`) is the second right-to-left locale the app ships, and the one
 * the #345 reports were written in. [TranslationParityTest] only proves the
 * files line up key-for-key; a locale can line up and still be English, which
 * is what "no values-fa yet" looked like for every RTL window.
 *
 * So this checks the content, not the shape: each English string that has
 * something to say must come back in Arabic script, and the strings the app
 * actually lays out must resolve RTL through the same [BidiText] the bubbles
 * use — not through a rule re-implemented here.
 */
class PersianLocaleTest {

    private val resDir: File by lazy { LocaleCatalog.resDir() }

    private val baseStrings: Map<String, String> by lazy { readStrings(File(resDir, "values")) }

    private val persianStrings: Map<String, String> by lazy { readStrings(File(resDir, "values-fa")) }

    /** Strings that stay in Latin script on purpose: typeface and product
     *  names, the SMS label, the two date patterns (patterns, not words, and
     *  the renderer resolves their field order from CLDR), and the licence
     *  list of project names. */
    private val latinByDesign = setOf(
        "font_dm_sans", "font_inter", "font_figtree", "font_poppins",
        "time_format_date", "time_format_divider",
        "status_sms", "settings_theme_amoled", "settings_sound_dragon",
        "about_licences_subtitle", "settings_import_source_sms_ie_title",
    )

    @Test
    fun persianIsADeclaredShippedLocale() {
        val declared = LocaleCatalog.declaredLocales(resDir)
        assertTrue("fa must be declared in locales_config.xml", "fa" in declared)
        assertEquals("fa must resolve to values-fa/", "values-fa", LocaleCatalog.dirNameFor("fa"))
        assertTrue("values-fa/ must exist", File(resDir, "values-fa").isDirectory)
    }

    @Test
    fun everyEnglishStringHasAPersianOne() {
        assertTrue("values-fa/ is empty", persianStrings.isNotEmpty())
        assertEquals("values-fa/ must define exactly the base keys", baseStrings.keys, persianStrings.keys)
    }

    /** The real "is it translated" check: an English sentence left in the fa
     *  file is Arabic-script-free even though its key is present. */
    @Test
    fun noEnglishSentenceSurvivesInPersian() {
        val arabic = Regex("[\\u0600-\\u06FF]")
        val word = Regex("[A-Za-z]{3,}")
        val problems = baseStrings
            .filter { (key, base) -> key !in latinByDesign && word.containsMatchIn(base) }
            .filter { (key, _) -> !arabic.containsMatchIn(persianStrings.getValue(key)) }
            .keys
        assertEquals(emptyList<String>(), problems.sorted())
    }

    /** Every shipped Persian sentence must be laid out right-to-left by the
     *  same function the message bubbles use. Values carrying Latin letters
     *  are skipped: a brand name legitimately decides its own direction. */
    @Test
    fun persianSentencesResolveRightToLeft() {
        val problems = persianStrings
            .filterKeys { it !in latinByDesign }
            .filterValues { value -> value.none { it in 'a'..'z' || it in 'A'..'Z' } }
            .filterValues { value -> value.any { it in '\u0600'..'\u06FF' } }
            .filterValues { value -> BidiText.direction(value) != BidiText.Direction.RTL }
            .keys
        assertEquals(emptyList<String>(), problems.sorted())
    }

    /** Crowdin region-qualifies a bare code by default (fa -> fa-rIR), which
     *  would export values-fa-rIR and leave values-fa/ untouched. */
    @Test
    fun crowdinPinsPersianToTheShippedDirectory() {
        val config = File(LocaleCatalog.repoRoot(), "crowdin.yml").readText()
        assertTrue(
            "crowdin.yml must map fa -> fa, or Crowdin exports values-fa-rIR",
            Regex("""(?m)^\s+fa:\s*fa\s*$""").containsMatchIn(config)
        )
    }

    private fun readStrings(dir: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val out = linkedMapOf<String, String>()
        dir.listFiles { f: File -> f.name.startsWith("strings") && f.extension == "xml" }
            .orEmpty()
            .sortedBy { it.name }
            .forEach { file ->
                factory.newDocumentBuilder().parse(file).getElementsByTagName("string").let { nodes ->
                    for (i in 0 until nodes.length) {
                        val el = nodes.item(i) as Element
                        out[el.getAttribute("name")] = el.textContent
                    }
                }
            }
        return out
    }
}
