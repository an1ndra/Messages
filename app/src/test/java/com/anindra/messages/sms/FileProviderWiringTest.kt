package com.anindra.messages.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The composed PDU is handed to the platform as a content URI the telephony
 * process has to open. The URI and the FileProvider's path mapping are two
 * halves of one contract: a URI without the right segment, or an authority
 * that is not the manifest's, means the platform can never read the PDU and no
 * send can succeed.
 *
 * Wiring-level: the mapping lives in XML and a Context, so a plain unit test
 * cannot compute the URI. What it can pin is that the `:mms` transport, the
 * app's facade, the manifest and `file_paths.xml` all name the same things.
 */
class FileProviderWiringTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("repository root not found")
    }

    private val main: File get() = File(root, "app/src/main")

    private val transport: String by lazy {
        File(root, "mms/src/main/java/com/anindra/messages/mms/transport/SystemMmsTransport.kt")
            .readText()
    }

    private val facade: String by lazy {
        File(main, "java/com/anindra/messages/sms/MmsFacade.kt").readText()
    }

    private val manifest: String by lazy { File(main, "AndroidManifest.xml").readText() }

    private val paths: String by lazy { File(main, "res/xml/file_paths.xml").readText() }

    @Test
    fun theTransportAddressesThePduThroughTheProvidersMmsSegment() {
        assertTrue(
            "the transport must address the PDU through the mms/ segment",
            transport.contains("/mms/") && transport.contains("content://")
        )
    }

    @Test
    fun theCacheRootThePduIsWrittenToIsExposedByTheProvider() {
        assertTrue(
            "the transport must write the PDU into the cache root",
            transport.contains("cacheDir")
        )
        assertTrue(
            "file_paths.xml must expose the cache root (a cache-path with path=\".\")",
            Regex("""<cache-path[^>]*path="\."[^>]*/>""").containsMatchIn(paths)
        )
    }

    @Test
    fun theAuthorityTheTransportUsesIsTheOneTheManifestDeclares() {
        val declared = manifest.contains("androidx.core.content.FileProvider") &&
            manifest.contains(".fileprovider")
        assertTrue("manifest must declare a FileProvider authority", declared)

        assertTrue(
            "the facade must hand the transport the app's own authority",
            facade.contains("app.packageName + \".fileprovider\"")
        )
        assertTrue(
            "the transport is given the manifest's authority",
            facade.contains("app.packageName + \".fileprovider\"")
        )
    }

    @Test
    fun theComposerIsGone() {
        // It was the only user of the vendored AOSP MMS stack, which is removed
        // with it. Anything still naming it would not compile.
        assertFalse(
            File(main, "java/com/anindra/messages/sms/MmsComposer.kt").exists()
        )
        assertFalse(File(root, "android-smsmms").exists())
    }

    @Test
    fun thePlatformTransportIsTheOneWired() {
        assertTrue(facade.contains("SystemMmsTransport("))
        assertTrue(facade.contains("SmsManagerMmsPlatform("))
        assertTrue(facade.contains("CarrierProfileStore.create(app)"))
    }

    @Test
    fun everyDownloadDestinationIsAStagingFileThePlatformCanWrite() {
        // Both paths that hand the platform a destination have to hand it a file.
        // The provider row the download used to be aimed at is not one, and the
        // failure it produced (MMS_ERROR_IO_ERROR on every attempt) is quiet
        // enough that only pinning this stops it coming back.
        val downloader = File(main, "java/com/anindra/messages/sms/MmsDownloader.kt").readText()
        val destination = Regex("""val destination = (.+)""").find(downloader)?.groupValues?.get(1)
            ?: error("no download destination found in MmsDownloader.kt")
        assertFalse(
            "the download destination must not be the announcement row: $destination",
            destination.contains("Uri.parse(key)")
        )
        assertTrue(
            "the download destination must come from the staging file: $destination",
            destination.contains("stagingUri(") || destination.contains("MmsStaging.uriFor(")
        )
        assertFalse(
            "the facade must not build a provider row URI as a destination",
            facade.contains("Uri.withAppendedPath")
        )
    }
}