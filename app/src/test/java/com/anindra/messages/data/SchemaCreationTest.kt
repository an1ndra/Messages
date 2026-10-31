package com.anindra.messages.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A fresh install builds the schema in `Db.onCreate`, then keeps patching that
 * same schema with `ALTER TABLE ... ADD COLUMN` for columns older versions
 * lacked. When a column is added to the `CREATE TABLE` itself but its `ALTER`
 * is left behind, SQLite aborts the whole of `onCreate` with "duplicate column
 * name" — and since the app cannot open its database, it dies on first launch.
 *
 * That is exactly what `starred` did: added to the `messages` table and to an
 * `onCreate` `ALTER` in the same commit. Nothing caught it because every
 * development device already had a database, so `onCreate` never ran again.
 *
 * These tests read the source rather than a database: SQLite is not available
 * off-device, and the failure mode is a literal duplicate in the DDL.
 */
class SchemaCreationTest {

    private val repository: String by lazy {
        val app = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
        File(app, "src/main/java/com/anindra/messages/data/Repository.kt").readText()
    }

    private val onCreate: String by lazy {
        val start = repository.indexOf("override fun onCreate(db: SQLiteDatabase)")
        val end = repository.indexOf("override fun onUpgrade(")
        assertTrue("Db.onCreate not found", start >= 0 && end > start)
        repository.substring(start, end)
    }

    @Test
    fun onCreateNeverAltersAColumnItJustCreated() {
        val created = columnsOfCreatedTables()
        val problems = mutableListOf<String>()
        Regex("""ALTER TABLE (\w+) ADD COLUMN (\w+)""").findAll(onCreate).forEach { alter ->
            val (table, column) = alter.destructured
            if (column in created[table].orEmpty()) {
                problems += "$table.$column is in CREATE TABLE and in an onCreate ALTER"
            }
        }
        assertEquals(emptyList<String>(), problems)
    }

    /** Every `CREATE TABLE <name>(...)` in onCreate, with its column names. */
    private fun columnsOfCreatedTables(): Map<String, Set<String>> {
        val tables = mutableMapOf<String, Set<String>>()
        val marker = "CREATE TABLE "
        var at = onCreate.indexOf(marker)
        while (at >= 0) {
            var i = at + marker.length
            if (onCreate.startsWith("IF NOT EXISTS ", i)) i += "IF NOT EXISTS ".length
            val nameEnd = onCreate.indexOf('(', i)
            if (nameEnd < 0) break
            val name = onCreate.substring(i, nameEnd).trim()
            // Walk to the matching bracket: a body holds nested ones, as in
            // REFERENCES conversations(id), so the first `)` is not the end.
            var depth = 0
            var bodyEnd = -1
            for (j in nameEnd until onCreate.length) {
                when (onCreate[j]) {
                    '(' -> depth++
                    ')' -> { depth--; if (depth == 0) { bodyEnd = j; break } }
                }
            }
            if (bodyEnd < 0) break
            val body = onCreate.substring(nameEnd + 1, bodyEnd)
            val columns = body.lines()
                .map { it.trim().trimEnd(',') }
                .filter { it.isNotEmpty() && !it.startsWith("PRIMARY KEY") && !it.startsWith("UNIQUE") }
                .mapNotNull { it.split(Regex("""\s+""")).firstOrNull() }
                .toSet()
            tables[name] = columns
            at = onCreate.indexOf(marker, bodyEnd)
        }
        return tables
    }

    @Test
    fun theSchemaUnderTestIsActuallyParsed() {
        // Guards the regex above: a silently empty parse would make the check
        // vacuous, which is how the original duplicate survived review.
        val created = columnsOfCreatedTables()
        assertTrue("no CREATE TABLE parsed from onCreate", created.isNotEmpty())
        assertTrue(
            "messages should be one of the created tables",
            "starred" in created["messages"].orEmpty()
        )
    }
}
