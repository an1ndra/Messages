package com.anindra.messages.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * An imported backup has to reach the system SMS store, not just this app.
 *
 * The local database is this app's own; the phone's messaging app reads
 * `content://sms`. Messages imported from an SMS Import / Export backup used to
 * stop here, so the user's history was invisible in the app they use day to day
 * even though it had imported "successfully".
 */
class ProviderMirrorWiringTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val repo: String by lazy {
        File(main, "java/com/anindra/messages/data/Repository.kt").readText()
    }

    @Test
    fun theSmsIeImportMirrorsIntoTheSystemProvider() {
        val start = repo.indexOf("fun importSmsIeFrom(")
        assertTrue("importSmsIeFrom not found", start > 0)
        val body = repo.substring(start, repo.indexOf("\n    fun ", start + 1))
        assertTrue(
            "an sms-ie import must mirror into the system SMS store, or the phone's " +
                "own messaging app never shows the imported history",
            body.contains("pushLocalMessagesToProvider()")
        )
    }

    @Test
    fun theMirrorSkipsMessagesThatAreAlreadyThere() {
        // One existence query per row is a binder round trip each, which dominates
        // a 50k import, so the live ids are read once.
        assertTrue(
            "the live provider ids should be read once up front, not queried per row",
            repo.contains("val live = HashSet<Long>()")
        )
        assertTrue(
            "already-mirrored rows should be skipped by an in-memory lookup",
            repo.contains("live.contains(existingSysId)")
        )
    }

    @Test
    fun theMirrorIsScopedToPlainSms() {
        // MMS has to be written as a multipart, which is a different and much
        // riskier operation; it is excluded on purpose rather than forgotten, so
        // the query must not claim to cover it.
        val start = repo.indexOf("fun pushLocalMessagesToProvider()")
        assertTrue("pushLocalMessagesToProvider not found", start > 0)
        val body = repo.substring(start, repo.indexOf("\n    private fun ", start + 1))
        assertTrue(
            "only plain text SMS is mirrored; MMS is deliberately out of scope",
            body.contains("m.transport='sms'") && body.contains("m.media_type='text'")
        )
    }

    @Test
    fun theMirrorFlushesInBatches() {
        assertTrue(
            "provider ids should be written back in batches, not one transaction per row",
            repo.contains("MIRROR_FLUSH_EVERY")
        )
        assertTrue(repo.contains("private fun writeBackSysIds("))
    }
}
