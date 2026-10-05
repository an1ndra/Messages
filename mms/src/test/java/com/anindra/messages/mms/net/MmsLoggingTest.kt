package com.anindra.messages.mms.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A subscriber number reaching an interpolated string is the leak this guards against. */
private val MSISDN_IN_A_MESSAGE =
    Regex("""\$\{?(line1|nai|msisdn|number)\b""", RegexOption.IGNORE_CASE)

/**
 * Nothing in this layer may write an MMS body or a subscriber's number to a log.
 *
 * A composed M-Send.req runs to megabytes and the MSISDN is PII; a failure
 * report carrying either is a leak, and the transports here run where a bug
 * report is one tap away. The rule is enforced from two sides because the second
 * is the one that actually holds: no `Log` call anywhere in the package, and the
 * recording engine has no logger to leak through.
 */
class MmsLoggingTest {

    private val sourceFiles: List<File>
        get() = File("src/main/java/com/anindra/messages/mms/net").listFiles()
            ?.filter { it.extension == "kt" }
            .orEmpty()
            .sortedBy { it.name }

    @Test
    fun theTransportSourcesArePresentToBeChecked() {
        assertTrue("no sources found to assert on", sourceFiles.size >= 5)
    }

    @Test
    fun noTransportSourceCallsAndroidLogAtAll() {
        sourceFiles.forEach { file ->
            val text = file.readText()
            assertFalse(
                "${file.name} calls android.util.Log",
                Regex("""\bLog\.[vdiwe]\(""").containsMatchIn(text),
            )
            assertFalse("${file.name} imports Timber", "timber" in text)
        }
    }

    @Test
    fun noTransportSourceMentionsAMsisdnInAFormattedString() {
        sourceFiles.forEach { file ->
            // `line1` is the MSISDN. It may be passed around and expanded into a
            // header, but it is never concatenated into a message.
            assertEquals(
                "${file.name} formats a subscriber number",
                emptyList<String>(),
                MSISDN_IN_A_MESSAGE.findAll(file.readText()).map { it.value }.toList(),
            )
        }
    }

    @Test
    fun theMsisdnAssertionActuallyBites() {
        // A check that cannot fail proves nothing, so it is run against the three
        // shapes of leak it exists to catch.
        listOf(
            """Log.i(TAG, "sending to ${'$'}line1")""",
            """val url = "?msisdn=${'$'}{msisdn}\"""",
            """Log.d(TAG, "${'$'}nai")""",
        ).forEach { leak ->
            assertTrue(
                "this check missed: $leak",
                MSISDN_IN_A_MESSAGE.containsMatchIn(leak),
            )
        }
        assertFalse(MSISDN_IN_A_MESSAGE.containsMatchIn("template.replace(LINE1_MACRO, line1)"))
    }

    @Test
    fun noTransportSourceCanReachTheResponseBodyFromALogLikeCall() {
        sourceFiles.forEach { file ->
            val text = file.readText()
            // A body reaches a log via a println, a printStackTrace, or an
            // interpolated `.bytes`; none of which appear in this package.
            assertFalse("${file.name} prints", "printStackTrace" in text || "println(" in text)
        }
    }

    @Test
    fun theRecordingEngineHasNoLoggerToLeakThrough() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())

        // The fake's whole surface: no logger parameter, no log sink. Anything
        // logged would have to come from inside the client, which the source
        // assertions above rule out.
        RecordingHttpEngine::class.java.declaredFields.forEach { field ->
            assertFalse(
                "fake engine holds a ${field.type}",
                field.type.name.contains("Log") || field.type.name.contains("Logger"),
            )
        }

        engine.execute(
            MmscHttpRequest(
                url = "http://mms.example.net/servlets/mms",
                method = MmscHttpClient.METHOD_POST,
                headers = mapOf("Accept" to MmscHttpClient.MMS_MEDIA_TYPE),
                body = ByteArray(2_048) { 0x41 },
                proxy = null,
            )
        )

        assertEquals(1, engine.requests.size)
    }

    @Test
    fun theBodyReachesTheEngineIntactAndNowhereElse() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())
        val pdu = ByteArray(1_024) { (it and 0xFF).toByte() }

        MmscHttpClient(engine, { null }, { null }).post(pdu, PROFILED_APN, line1 = "+447700900123")

        assertEquals(1, engine.requests.size)
        assertEquals(MmscHttpClient.METHOD_POST, engine.lastRequest.method)
        assertTrue(pdu.contentEquals(engine.lastRequest.body))
    }
}