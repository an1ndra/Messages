package com.anindra.messages.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class TranslationParityTest {

    private val resDir: File by lazy {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "src/main/res").isDirectory) dir = dir.parentFile
        dir?.let { File(it, "src/main/res") } ?: error("src/main/res not found")
    }

    private val baseStrings: Map<String, String> by lazy { readStrings(File(resDir, "values")) }

    private val localeDirs: List<File> by lazy {
        resDir.listFiles { f: File ->
            f.isDirectory && f.name.matches(Regex("values-[a-z]{2}(-r[A-Z]{2})?"))
        }.orEmpty().sortedBy { it.name }
    }

    @Test
    fun everyShippedLocaleIsDiscovered() {
        assertEquals(
            listOf(
                "values-ar", "values-de", "values-es", "values-fr", "values-hi-rIN", "values-ja",
                "values-ko", "values-pl", "values-pt-rBR", "values-ru", "values-zh-rCN", "values-zh-rTW",
            ),
            localeDirs.map { it.name },
        )
    }

    @Test
    fun localesDefineEveryBaseKeyAndNothingElse() {
        val problems = mutableListOf<String>()
        for (dir in localeDirs) {
            val locale = readStrings(dir)
            val missing = baseStrings.keys - locale.keys
            val extra = locale.keys - baseStrings.keys
            if (missing.isNotEmpty()) problems += "${dir.name} missing ${missing.sorted()}"
            if (extra.isNotEmpty()) problems += "${dir.name} unknown ${extra.sorted()}"
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun localesDoNotDefineTheSameKeyTwice() {
        val duplicates = localeDirs.mapNotNull { dir ->
            val seen = mutableSetOf<String>()
            val dupes = mutableSetOf<String>()
            dir.listFiles { f: File -> f.name.startsWith("strings") && f.extension == "xml" }
                .orEmpty()
                .forEach { file ->
                    file.readText().lineSequence()
                        .mapNotNull { Regex("<string name=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
                        .forEach { key -> if (!seen.add(key)) dupes += "$key (${file.name})" }
                }
            dupes.takeIf { it.isNotEmpty() }?.let { "${dir.name} defines ${it.sorted()}" }
        }
        assertEquals(emptyList<String>(), duplicates)
    }

    @Test
    fun localePlaceholdersMatchTheBaseValue() {
        val pattern = Regex("""%\d+\$[sd]""")
        val problems = mutableListOf<String>()
        for (dir in localeDirs) {
            val locale = readStrings(dir)
            for ((key, base) in baseStrings) {
                val translated = locale[key] ?: continue
                val expected = pattern.findAll(base).map { it.value }.toSet()
                val actual = pattern.findAll(translated).map { it.value }.toSet()
                if (expected != actual) problems += "${dir.name}/$key expected $expected got $actual"
            }
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun newlyTranslatedKeysAreNotLeftInEnglish() {
        val mustBeLocalized = setOf(
            "access_open_conversation", "settings_accessibility_title", "a11y_font_130",
            "settings_advanced_emoji_button", "keywords_title", "keywords_subtitle_count",
            "diagnostics_clip_label", "settings_sim_unknown", "chat_moved_to_spam",
            "chat_select_all", "chat_trash", "message_details_title", "message_status_failed",
            "chat_alphanumeric_notice", "common_continue", "access_blocked",
            "conversations_spam_blocked", "spam_blocked_messages_empty", "spam_message_deleted",
            "notif_you", "crash_report_title", "crash_report_message", "crash_report_clip_label",
            "settings_blocked_numbers_title", "blocked_numbers_empty_message", "blocked_numbers_unblock",
            "settings_retention_title", "settings_retention_action_active", "settings_retention_keep_spam",
            "settings_backup_save_to",
            "settings_backup_saved_location", "settings_import_source_title", "settings_import_sms_ie_failed",
            "trash_reason_blocked", "translation_help",
            "spam_empty", "spam_empty_confirm", "spam_conversation_deleted",
            "spam_conversations_cleared", "spam_messages_cleared",
        )
        assertTrue("base must define the tracked keys", baseStrings.keys.containsAll(mustBeLocalized))
        val problems = mutableListOf<String>()
        for (dir in localeDirs) {
            val locale = readStrings(dir)
            for (key in mustBeLocalized) {
                if (locale[key] == baseStrings[key]) problems += "${dir.name}/$key still English"
            }
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun localeFilesFollowTheBaseKeyOrder() {
        val problems = mutableListOf<String>()
        for (dir in localeDirs) {
            dir.listFiles { f: File -> f.name.startsWith("strings") && f.extension == "xml" }
                .orEmpty()
                .forEach { file ->
                    val baseFile = File(resDir, "values/${file.name}")
                    if (!baseFile.exists()) return@forEach
                    val baseOrder = readOrder(baseFile)
                    val actual = readOrder(file)
                    val positions = baseOrder.withIndex().associate { (i, k) -> k to i }
                    val sequence = actual.map { positions[it] ?: -1 }
                    if (sequence != sequence.sorted()) problems += "${dir.name}/${file.name} key order"
                }
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun noLocaleStringIsEmptyWhileTheBaseHasText() {
        val problems = mutableListOf<String>()
        for (dir in localeDirs) {
            val locale = readStrings(dir)
            for ((key, base) in baseStrings) {
                if (base.isNotBlank() && locale[key]?.isBlank() == true) problems += "${dir.name}/$key empty"
            }
        }
        assertEquals(emptyList<String>(), problems)
    }

    private fun readOrder(file: File): List<String> =
        Regex("<string name=\"([^\"]+)\"").findAll(file.readText()).map { it.groupValues[1] }.toList()

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
