package com.anindra.messages.sms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The composed PDU is handed to the platform as a content URI the telephony
 * process has to open. The URI and the FileProvider's path mapping are two
 * halves of one contract: a URI without the `mms/` segment, or an authority
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
        File(
            root,
            "mms/src/main/java/com/anindra/messages/mms/transport/SystemMmsTransport.kt"
        ).readText()
    }

    private val facade: String by lazy {
        File(main, "java/com/anindra/messages/sms/MmsFacade.kt").readText()
    }

    private val manifest: String by lazy { File(main, "AndroidManifest.xml").readText() }

    private val paths: String by lazy { File(main, "res/xml/file_paths.xml").readText() }

    @Test
    fun theTransportAddressesThePduThroughTheMmsSegmentOfTheProvider() {
        assertTrue(
            "the PDU URI must carry the `mms/` segment file_paths.xml names",
            transport.contains("content://\$fileProviderAuthority/mms/")
        )
        assertTrue(
            "file_paths.xml must expose the cache root as `mms` (path=\".\")",
            Regex("""<cache-path[^>]*name="mms"[^>]*path="\."[^>]*/>""").containsMatchIn(paths)
        )
    }

    @Test
    fun theAuthorityTheFacadePassesIsTheOneTheManifestDeclares() {
        assertTrue(
            "AndroidManifest.xml must declare the \${applicationId}.fileprovider authority",
            Regex("""android:authorities="\$\{applicationId\}\.fileprovider"""")
                .containsMatchIn(manifest)
        )
        assertTrue(
            "MmsFacade must pass packageName + \".fileprovider\" as the authority",
            facade.contains("app.packageName + \".fileprovider\"")
        )
    }

    @Test
    fun thePduIsWrittenToTheCacheRootTheProviderExposes() {
        assertTrue(
            "MmsFacade must hand the transport the app cache root",
            facade.contains("cacheDir = app.cacheDir")
        )
        assertTrue(
            "the transport must write the PDU straight into that cache root",
            transport.contains("File(cacheDir, ")
        )
    }

    @Test
    fun theSendResultActionIsDeclaredForTheStatusReceiver() {
        // The platform answers the send with the transport's own action; a
        // receiver that does not declare it never hears the result and the
        // message stays in "sending" forever.
        assertTrue(
            "SmsStatusReceiver must declare the transport's SEND_SENT action",
            manifest.contains("com.anindra.messages.mms.action.SEND_SENT")
        )
        assertTrue(
            "the transport action string moved; update the manifest with it",
            transport.contains("\"com.anindra.messages.mms.action.SEND_SENT\"")
        )
    }
}
