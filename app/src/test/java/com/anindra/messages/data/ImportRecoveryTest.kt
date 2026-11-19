package com.anindra.messages.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * An inbox that was emptied while the app could not read the system's messages
 * has to refill itself once access exists again. Two rules make that safe: a
 * failed read never counts as "import done", and an empty provider list never
 * counts as "the user deleted everything".
 */
class ImportRecoveryTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val repository = File(main, "java/com/anindra/messages/data/Repository.kt").readText()
    private val application = File(main, "java/com/anindra/messages/MessagesApplication.kt").readText()

    @Test
    fun aFailedSyncDoesNotClaimTheImportIsDone() {
        assertTrue("the failure is still logged", repository.contains("syncFromSystem failed"))
        assertTrue(
            "a sync that read nothing must not mark the first import done",
            !Regex("""syncFromSystem failed[\s\S]{0,240}?firstImportDone = true""")
                .containsMatchIn(repository)
        )
    }

    @Test
    fun anEmptyProviderReadNeverPrunesTheLocalCopy() {
        val prune = repository.substringAfter("fun pruneMessagesMissingFromProvider()")
            .substringBefore("\n    }\n")
        assertTrue(
            "an empty provider list is a permission symptom, not a deletion",
            Regex("""if \(live\.isEmpty\(\)\) \{[\s\S]{0,200}?return false""").containsMatchIn(prune)
        )
        assertTrue(
            "and the refusal is logged rather than silent",
            prune.contains("provider returned no ids")
        )
    }

    @Test
    fun anEmptyCopyWithAccessIsReimported() {
        val recovery = repository.substringAfter("fun recoverEmptyImportIfNeeded()")
            .substringBefore("\n    }\n")
        assertTrue("only when the app thinks it imported already",
            recovery.contains("if (!settings.firstImportDone) return"))
        assertTrue("only when SMS access exists",
            Regex("""READ_SMS[\s\S]{0,120}?PERMISSION_GRANTED""").containsMatchIn(recovery))
        assertTrue("only when the copy really is empty",
            recovery.contains("SELECT COUNT(*) FROM conversations"))
        assertTrue("and it re-arms the import",
            recovery.contains("settings.firstImportDone = false"))
        assertTrue(
            "the app start has to run it before syncing",
            Regex("""recoverEmptyImportIfNeeded\(\)\s*\n\s*repository\.syncFromSystem\(\)""")
                .containsMatchIn(application)
        )
    }
}
