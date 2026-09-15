package com.anindra.messages.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class CrashReportFormatterTest {

    private val device = CrashDeviceInfo(
        sdkInt = 31,
        model = "Pixel 5",
        manufacturer = "Google",
        brand = "google",
        fingerprint = "google/redfin/redfin:12/SP1A/1234:user/release-keys"
    )
    private val app = CrashAppInfo(versionName = "1.0.24", versionCode = 27L)

    @Test
    fun reportIncludesAppAndDeviceInfo() {
        val text = CrashReportFormatter.format(RuntimeException("boom"), device, app, 0L)
        assertTrue(text.contains("1.0.24"))
        assertTrue(text.contains("27"))
        assertTrue(text.contains("Android SDK: 31"))
        assertTrue(text.contains("Google Pixel 5"))
        assertTrue(text.contains("google/redfin/redfin"))
    }

    @Test
    fun reportIncludesExceptionTypeMessageAndStack() {
        val text = CrashReportFormatter.format(
            IllegalStateException("kaboom"), device, app, 0L
        )
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("kaboom"))
        assertTrue(text.contains("CrashReportFormatterTest"))
    }

    @Test
    fun reportFileNameIsStable() {
        assertTrue(CrashReportFormatter.reportFileName(0L).startsWith("crash-"))
        assertTrue(CrashReportFormatter.reportFileName(0L).endsWith(".txt"))
    }

    @Test
    fun buildZipRoundTripsEntries() {
        val out = ByteArrayOutputStream()
        CrashReportStore.buildZip(
            out,
            listOf("crash-1.txt" to "first", "crash-2.txt" to "second")
        )
        val entries = mutableMapOf<String, String>()
        ZipInputStream(out.toByteArray().inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        assertEquals(2, entries.size)
        assertEquals("first", entries["crash-1.txt"])
        assertEquals("second", entries["crash-2.txt"])
    }
}
