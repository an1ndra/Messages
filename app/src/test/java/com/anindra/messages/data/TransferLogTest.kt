package com.anindra.messages.data

import com.anindra.messages.diagnostics.DiagnosticsData
import com.anindra.messages.diagnostics.DiagnosticsReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferLogTest {

    private fun entry(
        operation: TransferOperation = TransferOperation.IMPORT,
        succeeded: Boolean = true,
        detail: String = "ok",
        added: Int = 0,
        seen: Int = 0,
        skipped: Int = 0,
        conflicts: Map<String, Int> = emptyMap(),
        timestamp: Long = 1_000L
    ) = TransferEntry(
        timestamp = timestamp,
        operation = operation,
        format = "PIN",
        mode = "merge",
        succeeded = succeeded,
        detail = detail,
        added = added,
        seen = seen,
        skipped = skipped,
        conflicts = conflicts
    )

    @Test
    fun appendKeepsEntriesInOrder() {
        val log = TransferLog.append(emptyList(), entry(detail = "first"))
        val withSecond = TransferLog.append(log, entry(detail = "second"))
        assertEquals(listOf("first", "second"), withSecond.map { it.detail })
    }

    @Test
    fun logIsCappedAtTheNewestEntries() {
        var log = emptyList<TransferEntry>()
        repeat(TransferLog.MAX_ENTRIES + 8) { log = TransferLog.append(log, entry(detail = "run $it")) }

        assertEquals(TransferLog.MAX_ENTRIES, log.size)
        assertEquals("run 8", log.first().detail)
        assertEquals("run ${TransferLog.MAX_ENTRIES + 7}", log.last().detail)
    }

    @Test
    fun encodeDecodeRoundTripsEveryField() {
        val original = listOf(
            entry(
                operation = TransferOperation.EXPORT,
                succeeded = false,
                detail = "Cannot write to Documents/Messages",
                timestamp = 1_700_000_000_000L
            ),
            entry(
                added = 40,
                seen = 42,
                skipped = 2,
                conflicts = mapOf(
                    Repository.Conflict.ALREADY_PRESENT to 2,
                    Repository.Conflict.PART_TOO_LARGE to 1
                )
            )
        )

        assertEquals(original, TransferLog.decode(TransferLog.encode(original)))
    }

    @Test
    fun decodeOfGarbageIsEmptyRatherThanThrowing() {
        assertEquals(emptyList<TransferEntry>(), TransferLog.decode("not json at all"))
        assertEquals(emptyList<TransferEntry>(), TransferLog.decode(""))
    }

    @Test
    fun aFailedRunIsKeptDistinctFromASuccessfulOne() {
        val log = TransferLog.append(
            emptyList(),
            entry(succeeded = false, detail = "Wrong PIN or corrupted file")
        )
        val restored = TransferLog.decode(TransferLog.encode(log)).single()
        assertFalse(restored.succeeded)
        assertEquals("Wrong PIN or corrupted file", restored.detail)
    }

    @Test
    fun conflictTalliesSurviveTheRoundTrip() {
        val conflicts = mapOf(
            Repository.Conflict.ALREADY_PRESENT to 7,
            Repository.Conflict.PROVIDER_ID_DROPPED to 3,
            Repository.Conflict.CONVERSATION_MERGED to 2
        )
        val restored = TransferLog.decode(TransferLog.encode(listOf(entry(conflicts = conflicts))))
            .single()
        assertEquals(conflicts, restored.conflicts)
    }

    @Test
    fun retryAndRecoveryFieldsSurviveTheRoundTrip() {
        val original = listOf(entry().copy(attempts = 3, recovered = true))
        val restored = TransferLog.decode(TransferLog.encode(original)).single()
        assertEquals(3, restored.attempts)
        assertTrue(restored.recovered)
    }

    @Test
    fun anEntryWrittenBeforeTheseFieldsExistedReadsAsASingleAttempt() {
        val legacy = """[{"t":1,"op":"EXPORT","fmt":"RAW","mode":"none","ok":true,
            "detail":"Saved x","added":0,"seen":0,"skipped":0,"conflicts":{}}]"""
        val restored = TransferLog.decode(legacy).single()
        assertEquals(1, restored.attempts)
        assertFalse(restored.recovered)
    }

    @Test
    fun diagnosticsReportShowsBackupHealth() {
        val text = DiagnosticsReport.format(
            diagnosticsData().copy(
                backupHealth = com.anindra.messages.diagnostics.BackupHealthInfo(
                    enabled = true,
                    lastAt = 1_700_000_000_000L,
                    lastOk = false,
                    detail = "Cannot write to Documents/Messages",
                    retryPending = true,
                    interval = "weekly"
                )
            )
        )
        assertTrue(text.contains("--- Backup ---"))
        assertTrue(text.contains("Periodic backup: weekly"))
        assertTrue(text.contains("FAILED"))
        assertTrue(text.contains("Retry pending: true"))
    }

    /** The report reads the log rather than re-deriving it. */
    @Test
    fun diagnosticsReportShowsTheLoggedOutcomeAndConflicts() {
        val text = DiagnosticsReport.format(
            diagnosticsData(
                transfers = listOf(
                    entry(
                        succeeded = false,
                        detail = "Wrong PIN or corrupted file",
                        conflicts = mapOf(Repository.Conflict.ALREADY_PRESENT to 12)
                    )
                )
            )
        )
        assertTrue(text.contains("--- Transfers ---"))
        assertTrue(text.contains("Wrong PIN or corrupted file"))
        assertTrue(text.contains("FAILED"))
        assertTrue(text.contains("conflict: already present = 12"))
    }

    @Test
    fun diagnosticsReportSaysSoWhenNothingHasRun() {
        val text = DiagnosticsReport.format(diagnosticsData())
        assertTrue(text.contains("--- Transfers ---"))
        assertTrue(text.contains("No imports or exports recorded"))
    }

    /** Minimal device/app state: the transfer section is what these assert on. */
    private fun diagnosticsData(transfers: List<TransferEntry> = emptyList()) = DiagnosticsData(
        device = com.anindra.messages.crash.CrashDeviceInfo(
            sdkInt = 35,
            model = "Pixel 7",
            manufacturer = "Google",
            brand = "google",
            fingerprint = "google/panther/panther:15/AP3A/1:user/release-keys"
        ),
        app = com.anindra.messages.crash.CrashAppInfo(versionName = "1.0.27", versionCode = 30L),
        appDetails = com.anindra.messages.diagnostics.AppDetails(
            defaultSms = true,
            permissions = emptyList(),
            locale = "en_US",
            timeZone = "UTC",
            themeMode = "system",
            notificationsEnabled = true
        ),
        selectedSubId = -1,
        phoneStateGranted = false,
        multiSim = false,
        phoneCount = 0,
        sims = emptyList(),
        display = com.anindra.messages.diagnostics.DisplayInfo(
            modeId = 1,
            width = 1080,
            height = 2400,
            refreshRate = 60f,
            densityDpi = 420,
            configDensityDpi = 420,
            preferredModeId = 0,
            supportedModes = emptyList()
        ),
        timestamp = 0L,
        transfers = transfers
    )
}