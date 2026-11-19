package com.anindra.messages.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The participant row is written on every startup migration and on every new
 * conversation, and the statement it used needed SQLite 3.24. Android 10 (the
 * app's minimum) ships 3.22, where `ON CONFLICT … DO UPDATE` is a syntax error
 * thrown on a background thread — the process died before any screen appeared.
 */
class ParticipantUpsertTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val repository = File(main, "java/com/anindra/messages/data/Repository.kt").readText()

    private val upsert = repository.substringAfter("private fun upsertParticipant(")
        .substringBefore("\n    }\n")

    @Test
    fun theParticipantWriteNeverNeedsANewerSqlite() {
        assertTrue(
            "upsert syntax is unavailable on Android 10, the minimum this app supports",
            !repository.contains("ON CONFLICT(")
        )
        assertTrue(
            "the row is refreshed with an UPDATE first",
            Regex("""database\.update\("participants",\s*refreshed,\s*"normalized_destination=\?",\s*arrayOf\(e164\)\)""")
                .containsMatchIn(upsert)
        )
        assertTrue(
            "and inserted only when no row matched",
            upsert.contains("insertOrThrow(\"participants\", null, inserted)")
        )
    }

    @Test
    fun everyColumnTheUpsertKeptIsStillWritten() {
        listOf(
            "normalized_destination", "send_destination", "display_destination",
            "comparable_destination", "country_code", "sub_id"
        ).forEach { column ->
            assertTrue("$column is no longer written", upsert.contains("\"$column\""))
        }
        assertTrue(
            "a concurrent writer's row is refreshed rather than left stale",
            Regex("""catch \(_: android\.database\.sqlite\.SQLiteConstraintException\)[\s\S]{0,200}?database\.update\("participants"""" )
                .containsMatchIn(upsert)
        )
        assertTrue(
            "non-phone senders still never get a participant row",
            upsert.contains("PhoneNumberUtils.isLikelyPhoneNumber(address)")
        )
    }

    @Test
    fun theMigrationStillRunsOnceAndIsMarkedDone() {
        val migration = repository.substringAfter("private fun runParticipantMigration()")
            .substringBefore("\n    }\n")
        assertTrue(
            "the flag is what keeps the pass off the next start",
            migration.contains("settings.participantsMigrated = true")
        )
        assertTrue(
            "and the pass still walks the conversations",
            migration.contains("SELECT id,address,sub_id FROM conversations") ||
                migration.contains("FROM conversations")
        )
    }
}
