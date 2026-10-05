package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The conflict tallies are only worth reporting if the code actually counts
 * them. Each site that decides not to write a message has to increment its own
 * bucket — a `continue` that skips without counting is exactly the silent loss
 * this log exists to expose, and it is invisible in review.
 */
class TransferConflictCountingTest {

    private val repo = sourceFile("data/Repository.kt")

    private fun sourceFile(relative: String): String =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/anindra/messages/$relative") }
            .firstOrNull { it.isFile }
            ?.readText()
            ?: error("$relative not found")

    /** Body of [functionName] in Repository.kt. */
    private fun bodyOf(functionName: String): String {
        val start = repo.indexOf("fun $functionName(")
        assertTrue("$functionName not found", start > 0)
        var depth = 0
        var seen = false
        for (i in start until repo.length) {
            when (repo[i]) {
                '{' -> { depth++; seen = true }
                '}' -> { depth--; if (seen && depth == 0) return repo.substring(start, i + 1) }
            }
        }
        error("unterminated body for $functionName")
    }

    /**
     * Every `skipped++` in [functionName] must sit next to a `conflicts.count(`.
     *
     * Scoping it to `skipped++` rather than every `continue` is deliberate: a
     * `continue` inside a cursor loop is just paging, while `skipped++` is the
     * app counting a message it decided not to write — and that is exactly the
     * decision the log claims to explain.
     */
    private fun assertSkipsSayWhy(functionName: String) {
        val lines = bodyOf(functionName).lines()
        val uncounted = lines.mapIndexedNotNull { index, line ->
            if (!line.contains("skipped++")) return@mapIndexedNotNull null
            // Look both ways: the tally is written either before the bail-out
            // or on the line after it.
            val window = lines.drop((index - 4).coerceAtLeast(0)).take(9).joinToString("\n")
            if (window.contains("conflicts.count(")) null else "${functionName}:${index + 1}\n$window"
        }
        assertEquals(
            "$functionName counted skipped messages without saying why:\n${uncounted.joinToString("\n")}",
            emptyList<String>(),
            uncounted
        )
    }

    @Test
    fun streamingImportCountsEverySkip(): Unit = assertSkipsSayWhy("importStaged")

    @Test
    fun ownBackupMergeCountsEverySkip(): Unit = assertSkipsSayWhy("mergeDatabase")

    @Test
    fun theDuplicateCheckIsWhatFillsTheAlreadyPresentBucket() {
        val body = bodyOf("importStaged")
        assertTrue(
            "the streaming import has no duplicate check, so a repeated merge " +
                "cannot report 'already present'",
            body.contains("Conflict.ALREADY_PRESENT")
        )
        assertTrue(
            "duplicates must be recorded against the address-keyed identity",
            body.contains("messageKey(")
        )
    }

    @Test
    fun everyConflictReasonIsReportedBySomePath() {
        val names = listOf(
            "ALREADY_PRESENT",
            "PROVIDER_ID_TAKEN",
            "PROVIDER_ID_DROPPED",
            "CONVERSATION_MERGED",
            "CONVERSATION_UNRESOLVED",
            "RECORD_UNREADABLE",
            "PART_TOO_LARGE"
        )
        val unreported = names.filterNot { repo.contains("Conflict.$it") }
        assertEquals(
            "these reasons are declared but never recorded, so the log would " +
                "never show them",
            emptyList<String>(),
            unreported
        )
    }

    @Test
    fun aFailedInsertIsCountedRatherThanCountedAsAdded() {
        val body = bodyOf("mergeDatabase")
        assertTrue(
            "mergeDatabase counted `added++` unconditionally; a refused insert " +
                "read as success",
            body.contains("if (insertId <= 0) continue")
        )
    }

    @Test
    fun exportsRecordTheirFailureReasonInsteadOfABareFalse() {
        val body = bodyOf("backupDatabase")
        assertTrue(
            "backupDatabase still returns a bare false somewhere, which loses the reason",
            !body.contains("return false")
        )
        assertTrue(
            "every export failure must go through exportFailed so it is logged",
            body.contains("exportFailed(")
        )
    }
}