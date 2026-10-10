package com.anindra.messages.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The :mms wiring is platform glue — a Context, a provider and the FileProvider
 * authority — so it cannot be constructed in a plain JVM test. What can be
 * pinned is the one contract that has already broken once (the composed PDU
 * served from outside the provider's cache root), plus the transport choice:
 * the platform path is the default, and the direct MMSC client is not wired in
 * behind it.
 */
class MmsFacadeWiringTest {

    private val source: String by lazy {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map {
                File(it, "app/src/main/java/com/anindra/messages/sms/MmsFacade.kt")
            }
            .firstOrNull { it.isFile }
            ?: error("MmsFacade.kt not found from ${File("").absolutePath}")
        file.readText()
    }

    @Test
    fun theComposedPduIsServedFromTheProvidersCacheRoot() {
        assertTrue(
            "the transport must be handed the app's FileProvider authority",
            source.contains("app.packageName + \".fileprovider\"")
        )
        assertTrue(
            "the PDU must be written into the cache root file_paths.xml exposes",
            source.contains("cacheDir = app.cacheDir")
        )
    }

    @Test
    fun thePlatformTransportIsTheOneWired() {
        assertTrue(source.contains("SystemMmsTransport("))
        assertTrue(source.contains("SmsManagerMmsPlatform("))
        assertTrue(source.contains("CarrierProfileStore.create(app)"))
    }

    @Test
    fun theFacadeHandsThePlatformNoDownloadDestinationItCannotWrite() {
        // A `content://mms/<id>` row is not a destination the MMS service can
        // open: every download against one fails with MMS_ERROR_IO_ERROR. The
        // facade used to build exactly that by resolving the announcement row.
        assertFalse(
            "the download target must not resolve an announcement row as a destination",
            source.contains("Telephony.Mms.TRANSACTION_ID")
        )
        assertFalse(
            "the download target must not build a provider row URI",
            source.contains("Uri.withAppendedPath")
        )
    }
}
