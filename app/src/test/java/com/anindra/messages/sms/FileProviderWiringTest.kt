package com.anindra.messages.sms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The composed PDU is handed to the platform as a content URI the telephony
 * process has to open. The URI and the FileProvider's path mapping are two
 * halves of one contract: the composer once built the URI by hand and drifted
 * from `file_paths.xml` (no `mms/` segment), so no send could ever be read.
 *
 * Wiring-level: the mapping lives in XML and a Context, so a plain unit test
 * cannot compute the URI. What it can pin is that the composer derives the
 * URI through the same mechanism the XML feeds, so the two cannot drift apart
 * again.
 */
class FileProviderWiringTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val composer: String by lazy {
        File(main, "java/com/anindra/messages/sms/MmsComposer.kt").readText()
    }

    private val manifest: String by lazy {
        File(main, "AndroidManifest.xml").readText()
    }

    private val paths: String by lazy {
        File(main, "res/xml/file_paths.xml").readText()
    }

    @Test
    fun theComposerAsksTheFileProviderForThePduUri() {
        assertTrue(
            "MmsComposer must derive the PDU URI through FileProvider.getUriForFile, " +
                "not by hand — a hand-built URI drifts from file_paths.xml",
            composer.contains("FileProvider.getUriForFile(")
        )
        assertTrue(
            "a hand-built content:// PDU URI is how the send broke originally",
            !Regex("""Uri\.Builder\(\)[\s\S]{0,200}scheme\("content"\)""")
                .containsMatchIn(composer)
        )
    }

    @Test
    fun theAuthorityTheComposerUsesIsTheOneTheManifestDeclares() {
        val authority =
            Regex("""android:authorities="\$\{applicationId\}\.fileprovider"""")
        assertTrue(
            "AndroidManifest.xml must declare the \${applicationId}.fileprovider authority",
            authority.containsMatchIn(manifest)
        )
        assertTrue(
            "MmsComposer must use the manifest's authority",
            composer.contains(".fileprovider")
        )
    }

    @Test
    fun theCacheRootThePduIsWrittenToIsExposedByTheProvider() {
        // The PDU file is written directly into the cache root
        // (File(context.cacheDir, "mms-send-...dat")), so file_paths.xml must
        // expose the cache root itself — that entry's name segment is what
        // getUriForFile puts into the URI.
        assertTrue(
            "MmsComposer must write the PDU into the cache root the provider exposes",
            composer.contains("File(context.cacheDir, \"mms-send-")
        )
        assertTrue(
            "file_paths.xml must expose the cache root (a cache-path with path=\".\")",
            Regex("""<cache-path[^>]*path="\."[^>]*/>""").containsMatchIn(paths)
        )
    }
}
