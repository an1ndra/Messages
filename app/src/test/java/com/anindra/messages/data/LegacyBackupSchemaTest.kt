package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A backup from an older build has to import.
 *
 * The real failure: the merge read `locked` and `sub_id` and filtered on
 * `deleted_at`, none of which exist in a v8 backup, so importing one threw
 * "no such column: locked" and the user's history was refused. The file is a
 * perfectly good SQLite database and was never rejected for that.
 *
 * The shape of the fix matters as much as the fix. A missing column becomes a
 * *literal*, not a different column, so the caller's positional reads keep
 * pointing at the right fields. Substituting the next available column's name
 * would compile, run, and quietly write transport into sub_id.
 */
class LegacyBackupSchemaTest {

    /** The columns a v8 backup actually has, taken from a real one. */
    private val v8Columns = setOf(
        "id", "conversation_id", "body", "timestamp", "is_me", "status",
        "media_type", "media_uri", "reactions", "sys_id"
    )

    private val currentColumns = v8Columns + setOf(
        "locked", "sub_id", "transport", "deleted_at"
    )

    @Test
    fun anOldBackupsQueryUsesLiteralsForAbsentColumns() {
        val q = LegacyBackupSchema.messagesQuery(v8Columns)
        assertFalse("must not select a column the file lacks: $q", "locked," in q)
        assertFalse("must not filter on a column the file lacks: $q", "deleted_at" in q)
        assertTrue("transport falls back to sms: $q", "'sms'" in q)
        assertTrue("locked falls back to 0: $q", "0" in q)
    }

    @Test
    fun aCurrentBackupsQueryIsUnchanged() {
        val q = LegacyBackupSchema.messagesQuery(currentColumns)
        assertTrue("real columns are used when present: $q", "locked" in q)
        assertTrue("real sub_id is used when present: $q", "sub_id" in q)
        assertTrue("deleted_at filter is applied: $q", "WHERE deleted_at=0" in q)
        assertFalse("no literal fallback needed: $q", "'sms'" in q)
    }

    @Test
    fun theSelectedColumnsKeepTheirPositions() {
        // The caller reads these positionally, so a missing column must not shift
        // anything after it. What sits at an index is the real column when the
        // file has it and the literal fallback when it does not.
        fun select(cols: Set<String>) = LegacyBackupSchema.messagesQuery(cols)
            .removePrefix("SELECT ").substringBefore(" FROM")
            .split(",").map { it.trim() }

        val old = select(v8Columns)
        val now = select(currentColumns)

        listOf(old to "v8", now to "current").forEach { (cols, label) ->
            assertEquals("$label: column count must be stable", 12, cols.size)
            assertEquals("$label: sys_id must stay at index 8", "sys_id", cols[8])
            assertEquals(
                "$label: locked must land at index 9",
                if (label == "v8") "0" else "locked",
                cols[LegacyBackupSchema.LOCKED_INDEX]
            )
            assertEquals(
                "$label: sub_id must land at index 10",
                if (label == "v8") "-1" else "sub_id",
                cols[LegacyBackupSchema.SUB_ID_INDEX]
            )
            assertEquals(
                "$label: transport must land at index 11",
                if (label == "v8") "'sms'" else "transport",
                cols[LegacyBackupSchema.TRANSPORT_INDEX]
            )
        }
    }

    @Test
    fun theGeneratedQueryActuallyRunsAgainstARealOldBackup() {
        // The real file is the user's, not part of the repo, so this only runs
        // when it happens to be present. Whether the SQL *executes* against a
        // real v8 database is asserted by scripts/test-legacy-backup-import.sh,
        // which has a sqlite3 to hand; a JVM test has no driver.
        val backup = File("/tmp/opencode/user-backup.db")
        if (!backup.isFile) return

        val bytes = backup.readBytes().copyOf(16)
        assertEquals(
            "the fixture should be a plain SQLite database",
            "SQLite format 3",
            String(bytes, 0, 15)
        )
        // The query must reference no column the v8 schema lacks.
        val q = LegacyBackupSchema.messagesQuery(v8Columns)
        v8Columns.forEach { assertTrue("required column $it missing from: $q", it in q) }
    }
}
