package com.anindra.messages.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * QUIK import is its own entry in General settings, with its own picker, rather
 * than a third choice inside the "Import messages" dialog: the file is plain
 * JSON and none of the sms-ie flow (encryption, parts, format sniffing) applies
 * to it.
 */
class QuikImportEntryTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private val screen get() = File(
        app, "src/main/java/com/anindra/messages/ui/SettingsScreen.kt"
    ).readText()

    @Test
    fun itIsAChoiceInsideImportMessages() {
        assertTrue(
            "the import dialog must offer QUIK alongside the other formats",
            screen.contains("stringResource(R.string.settings_import_quik_title) to \"\"")
        )
        assertTrue(
            "and it must be reachable as the third choice",
            screen.contains("ImportSource.QUIK -> 2")
        )
    }

    @Test
    fun itOpensTheJsonPicker() {
        assertTrue(
            "picking QUIK must launch a JSON picker",
            screen.contains("quikLauncher.launch(arrayOf(\"application/json\", \"*/*\"))")
        )
        assertTrue(
            "and record itself as the QUIK source",
            screen.contains("pendingImportSource = ImportSource.QUIK")
        )
    }

    @Test
    fun thereIsNoSecondImportRow() {
        assertTrue(
            "one Import messages entry, not two",
            !screen.contains("settings_import_quik_subtitle")
        )
    }

    @Test
    fun theImportStillRunsThroughTheQuikPath() {
        assertTrue(
            "the mode dialog must route QUIK to its own importer",
            screen.contains("vm.importQuik(target, mode, showImportResult)")
        )
    }
}
