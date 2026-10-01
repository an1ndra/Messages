package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The streaming reader, and the properties that let a 50k backup import.
 *
 * The previous reader pulled the whole file into a ByteArray and kept every
 * decompressed part in a map, so a backup with images needed the entire archive
 * resident before a single row was written. These check the staging behaviour
 * that replaced it, plus the per-part cap that used to abort whole backups.
 */
class SmsIeReaderStagingTest {

    private val tmp: File by lazy {
        File(System.getProperty("java.io.tmpdir"), "smsie-stage-test-${System.nanoTime()}")
            .apply { mkdirs() }
    }

    private fun zip(name: String, build: (ZipOutputStream) -> Unit): File {
        val f = File(tmp, name)
        ZipOutputStream(f.outputStream()).use(build)
        return f
    }

    private fun ndjson(vararg records: String) = records.joinToString("\n")

    private fun record(address: String, body: String, type: Int = 1) =
        """{"_id":"1","address":"$address","date":"1600000000000","read":"1",""" +
            """"type":"$type","body":"$body","sub_id":"1"}"""

    private fun png(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val z = java.util.zip.DeflaterOutputStream(out)
        z.write(ByteArray(64) { 0x42 })
        z.close()
        return out.toByteArray()
    }

    // --- staging ----------------------------------------------------------

    @Test
    fun stagesAV2ArchiveAndKeepsPartsOnDisk() {
        val f = zip("ok.zip") { z ->
            z.putNextEntry(ZipEntry("messages.ndjson"))
            z.write(ndjson(record("+15551230001", "one"), record("+15551230001", "two")).toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("data/PART_1.png"))
            z.write(png())
            z.closeEntry()
        }
        val staged = SmsIeStager.stage(f, tmp)
        assertNotNull(staged)
        staged!!.use {
            assertTrue("v2 backup should be detected", it.zip)
            assertEquals(2, it.messageFile.readLines().size)
            assertNotNull("the part should be staged", it.partBytes("PART_1.png"))
        }
    }

    @Test
    fun stagesAV1JsonArray() {
        val f = File(tmp, "v1.json")
        f.writeText("[${record("+15551230001", "one")},${record("+15551230001", "two")}]")
        SmsIeStager.stage(f, tmp)!!.use {
            assertTrue("a bare JSON array is v1, not a zip", !it.zip)
            val seen = mutableListOf<String>()
            SmsIeReader.forEachRecord(it) { seen += it }
            assertEquals(2, seen.size)
        }
    }

    @Test
    fun recordsAreYieldedOneAtATime() {
        val records = (1..2500).map { record("+1555123%04d".format(it % 7), "body $it") }
        val f = zip("many.zip") { z ->
            z.putNextEntry(ZipEntry("messages.ndjson"))
            z.write(ndjson(*records.toTypedArray()).toByteArray())
            z.closeEntry()
        }
        SmsIeStager.stage(f, tmp)!!.use { staged ->
            var n = 0
            SmsIeReader.forEachRecord(staged) { n++ }
            assertEquals("every record must be yielded", 2500, n)
        }
    }

    // --- the cap that used to abort a whole backup -------------------------

    @Test
    fun anOversizedPartIsSkippedWithoutLosingTheMessages() {
        // A part over the cap used to throw out of the whole zip loop. Whether
        // that lost everything depended on the order entries were written in,
        // which a real export does not guarantee.
        val f = zip("big-first.zip") { z ->
            z.putNextEntry(ZipEntry("data/PART_HUGE.jpg"))
            z.write(ByteArray(SmsIeReader.MAX_PART_BYTES + 1024))
            z.closeEntry()
            z.putNextEntry(ZipEntry("messages.ndjson"))
            z.write(ndjson(record("+15551230001", "kept"), record("+15551230001", "also kept")).toByteArray())
            z.closeEntry()
        }
        SmsIeStager.stage(f, tmp)!!.use { staged ->
            assertEquals("the oversized part is reported", 1, staged.skippedParts.size)
            assertTrue(staged.wasSkipped("PART_HUGE.jpg"))
            val seen = mutableListOf<String>()
            SmsIeReader.forEachRecord(staged) { seen += it }
            assertEquals(
                "the message list must survive an oversized part regardless of entry order",
                2, seen.size
            )
        }
    }

    @Test
    fun anOversizedPartIsNotEvenWrittenToDisk() {
        val f = zip("big-only.zip") { z ->
            z.putNextEntry(ZipEntry("data/PART_HUGE.jpg"))
            z.write(ByteArray(SmsIeReader.MAX_PART_BYTES + 1024))
            z.closeEntry()
            z.putNextEntry(ZipEntry("messages.ndjson"))
            z.write(ndjson(record("+15551230001", "kept")).toByteArray())
            z.closeEntry()
        }
        SmsIeStager.stage(f, tmp)!!.use { staged ->
            assertNull(
                "a skipped part must not be readable",
                staged.partBytes("PART_HUGE.jpg")
            )
        }
    }

    @Test
    fun aPartJustUnderTheCapIsKept() {
        val payload = ByteArray(SmsIeReader.MAX_PART_BYTES - 1024)
        val f = zip("edge.zip") { z ->
            z.putNextEntry(ZipEntry("messages.ndjson"))
            z.write(ndjson(record("+15551230001", "kept")).toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("data/PART_EDGE.bin"))
            z.write(payload)
            z.closeEntry()
        }
        SmsIeStager.stage(f, tmp)!!.use { staged ->
            assertEquals("nothing should be skipped", 0, staged.skippedParts.size)
            assertEquals(payload.size, staged.partBytes("PART_EDGE.bin")?.size)
        }
    }

    // --- hostile input ----------------------------------------------------

    @Test
    fun aPartNameCannotEscapeTheStagingDirectory() {
        // A crafted backup must not be able to make the importer read or write
        // outside its own scratch dir.
        val f = zip("escape.zip") { z ->
            z.putNextEntry(ZipEntry("messages.ndjson"))
            z.write(
                ndjson(
                    """{"_id":"1","address":"+15551230001","date":"1600000000000",""" +
                        """"msg_box":"1","m_type":"132","__parts":""" +
                        """[{"ct":"text/plain","text":"hi"},""" +
                        """{"ct":"image/jpeg","_data":"../../../../etc/passwd"}]}"""
                ).toByteArray()
            )
            z.closeEntry()
            z.putNextEntry(ZipEntry("data/PART_1.png"))
            z.write(png())
            z.closeEntry()
        }
        SmsIeStager.stage(f, tmp)!!.use { staged ->
            assertNull(
                "a traversing _data path must resolve to nothing",
                staged.partBytes("../../../../etc/passwd")
            )
            assertNotNull("the real part is still found by file name", staged.partBytes("PART_1.png"))
        }
    }

    @Test
    fun anArchiveWithNoMessageListIsRejected() {
        val f = zip("nolist.zip") { z ->
            z.putNextEntry(ZipEntry("data/PART_1.png"))
            z.write(png())
            z.closeEntry()
        }
        assertNull("an archive with no message list cannot be imported", SmsIeStager.stage(f, tmp))
    }

    @Test
    fun anEmptyFileIsRejected() {
        val f = File(tmp, "empty.json")
        f.writeText("")
        assertNull(SmsIeStager.stage(f, tmp))
    }

    @Test
    fun aDamagedRecordCostsOnlyThatRecord() {
        val f = zip("damaged.zip") { z ->
            z.putNextEntry(ZipEntry("messages.ndjson"))
            z.write(
                (listOf(
                    record("+15551230001", "good one"),
                    "{ this is not json",
                    record("+15551230002", "good two")
                )).joinToString("\n").toByteArray()
            )
            z.closeEntry()
        }
        SmsIeStager.stage(f, tmp)!!.use { staged ->
            val parsed = ArrayList<SmsIeBackup.Message?>()
            SmsIeReader.forEachRecord(staged) { parsed.add(SmsIeBackup.record(it)) }
            assertEquals("all three lines are seen", 3, parsed.size)
            assertNotNull("the first good record survives", parsed[0])
            assertNull("the damaged record is dropped, not fatal", parsed[1])
            assertNotNull("the record after the damage still imports", parsed[2])
        }
    }

    @Test
    fun stagingIsCleanedUpOnClose() {
        val f = zip("cleanup.zip") { z ->
            z.putNextEntry(ZipEntry("messages.ndjson"))
            z.write(ndjson(record("+15551230001", "one")).toByteArray())
            z.closeEntry()
        }
        var root: File? = null
        SmsIeStager.stage(f, tmp)!!.use { root = it.debugRoot }
        assertTrue("staging must not be left behind", root!!.exists().not())
    }
}

/** [SmsIeReader.stage] takes a ContentResolver URI; this drives it from a File
 *  so the streaming behaviour can be tested without an Android runtime. */
internal object SmsIeStager {
    fun stage(file: File, workDir: File): SmsIeReader.Staged? =
        SmsIeReader.stageFile(file, workDir)
}
