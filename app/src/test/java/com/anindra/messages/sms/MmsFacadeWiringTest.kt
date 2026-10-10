package com.anindra.messages.sms

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
    fun theAnnouncedRowIsFoundByItsTransactionId() {
        assertTrue(
            "the download target must resolve the announced row by tr_id",
            source.contains("Telephony.Mms.TRANSACTION_ID")
        )
    }
}
