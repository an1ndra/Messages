package com.anindra.messages.mms.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The MMSC request rules, pinned.
 *
 * A macro left unexpanded in `x-wap-profile` gets the whole transaction rejected
 * by the carrier with no local diagnosis, and the two 2xx rules in the reference
 * stack disagree about whether a delivered message was delivered, so both are
 * nailed down here rather than left to the transport.
 */
class MmscHttpClientTest {
    private val mmsc = ApnProfile(
        apnName = "mms",
        mmscUrl = "http://mms.example.net:8080/servlets/mms",
        mmsProxy = null,
        mmsPort = null,
        type = "mms",
    )

    private fun client(
        engine: RecordingHttpEngine,
        ua: String? = "Android/16 Build/UP1A",
        profile: String? = null,
    ) = MmscHttpClient(
        engine = engine,
        userAgent = { ua },
        uaProfileUrl = { profile },
        language = { "en-GB" },
    )

    @Test
    fun postCarriesTheRequiredHeaders() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())
        val response = client(engine).post(byteArrayOf(7, 8), mmsc, line1 = null)

        assertTrue(response is MmscResponse.Success)
        val headers = engine.lastRequest.headers
        assertEquals("application/vnd.wap.mms-message", headers["Accept"])
        assertEquals("en-GB", headers["Accept-Language"])
        assertEquals("Android/16 Build/UP1A", headers["User-Agent"])
        assertNull("no profile configured means no header", headers["x-wap-profile"])
    }

    @Test
    fun postSendsTheComposedPduBytesUnchanged() {
        val pdu = byteArrayOf(0x0B, 0x80.toByte(), 0x00, 0xFF.toByte())
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())

        client(engine).post(pdu, mmsc, line1 = null)

        assertEquals("POST", engine.lastRequest.method)
        assertArrayEquals(pdu, engine.lastRequest.body)
        assertEquals("http://mms.example.net:8080/servlets/mms", engine.lastRequest.url)
    }

    @Test
    fun aBlankUserAgentIsOmittedRatherThanSentEmpty() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())

        client(engine, ua = "   ").post(byteArrayOf(1), mmsc, line1 = null)

        assertFalse(engine.lastRequest.headers.containsKey("User-Agent"))
    }

    @Test
    fun profileMacrosAreExpandedFromTheSuppliedLine1() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())

        client(engine, profile = "http://carrier.example/##LINE1##.xml")
            .post(byteArrayOf(1), mmsc, line1 = "+447700900123")

        assertEquals("http://carrier.example/+447700900123.xml", engine.lastRequest.headers["x-wap-profile"])
    }

    @Test
    fun anUnexpandableNaiMacroIsRemovedAndNeverSentLiterally() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())

        client(engine, profile = "http://carrier.example/u/##NAI##/##LINE1##")
            .post(byteArrayOf(1), mmsc, line1 = "+447700900123")

        val sent = engine.lastRequest.headers.getValue("x-wap-profile")
        assertEquals("http://carrier.example/u//+447700900123", sent)
        assertFalse("no literal macro may leave the device", sent.contains("##"))
    }

    @Test
    fun everyUnknownMacroIsStrippedToo() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())

        client(engine, profile = "http://carrier.example/##DEVICEID##/##UNKNOWN##")
            .post(byteArrayOf(1), mmsc, line1 = null)

        val sent = engine.lastRequest.headers.getValue("x-wap-profile")
        assertEquals("http://carrier.example//", sent)
    }

    @Test
    fun aProfileThatIsNothingButMacrosIsOmittedEntirely() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())

        client(engine, profile = "##LINE1##").post(byteArrayOf(1), mmsc, line1 = null)

        assertFalse(engine.lastRequest.headers.containsKey("x-wap-profile"))
    }

    @Test
    fun anyTwoHundredRangeStatusIsSuccess() {
        listOf(200, 201, 202, 204, 206).forEach { status ->
            val engine = RecordingHttpEngine(RecordingHttpEngine.ok(statusCode = status))

            val response = client(engine).post(byteArrayOf(1), mmsc, line1 = null)

            assertTrue("status $status was $response", response is MmscResponse.Success)
            assertEquals("status $status", status, (response as MmscResponse.Success).statusCode)
            assertArrayEquals(byteArrayOf(1, 2, 3), response.bytes)
        }
    }

    @Test
    fun everyOtherStatusIsAnHttpFailureCarryingTheStatus() {
        listOf(301, 400, 403, 404, 410, 500, 502, 503).forEach { status ->
            val engine = RecordingHttpEngine(RecordingHttpEngine.status(status))

            val response = client(engine).post(byteArrayOf(1), mmsc, line1 = null)

            assertEquals("status $status", MmscResponse.Failure(MmscFailure.HTTP_FAILURE, status), response)
        }
    }

    @Test
    fun aNotFoundIsDistinguishableSoTheNotificationCanBeDropped() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.status(404))

        val response = client(engine).retrieve("http://mms.example.net/inbox/1", mmsc)

        assertTrue(response is MmscResponse.Failure)
        response as MmscResponse.Failure
        assertEquals(MmscFailure.HTTP_FAILURE, response.kind)
        assertEquals(404, response.statusCode)
        assertEquals(404, MmscHttpClient.HTTP_NOT_FOUND)
    }

    @Test
    fun aConnectTimeoutIsATimeoutAndNotAnIoFailure() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.timeout())

        val response = client(engine).post(byteArrayOf(1), mmsc, line1 = null)

        assertEquals(MmscResponse.Failure(MmscFailure.TIMEOUT, null), response)
    }

    @Test
    fun aTlsFailureIsReportedAsTls() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.tls())

        val response = client(engine).post(byteArrayOf(1), mmsc, line1 = null)

        assertEquals(MmscResponse.Failure(MmscFailure.TLS, null), response)
    }

    @Test
    fun aReadTimeoutIsAlsoATimeout() {
        val engine = RecordingHttpEngine(java.net.SocketTimeoutException("read timed out"))

        assertEquals(
            MmscResponse.Failure(MmscFailure.TIMEOUT, null),
            client(engine).retrieve("http://mms.example.net/inbox/1", mmsc),
        )
    }

    @Test
    fun aPlainIoFailureIsIo() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.io())

        assertEquals(
            MmscResponse.Failure(MmscFailure.IO, null),
            client(engine).post(byteArrayOf(1), mmsc, line1 = null),
        )
    }

    @Test
    fun aProxylessHttpsRetrieveGoesOutWithNoProxy() {
        val https = mmsc.copy(mmscUrl = "https://mms.example.net/mms", mmsProxy = "", mmsPort = 0)
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())

        val response = client(engine).retrieve("https://mms.example.net/mms/inbox/9", https)

        assertTrue(response is MmscResponse.Success)
        assertEquals("GET", engine.lastRequest.method)
        assertNull("no proxy configured means none is built", engine.lastRequest.proxy)
        assertEquals("https://mms.example.net/mms/inbox/9", engine.lastRequest.url)
        assertNull(engine.lastRequest.body)
    }

    @Test
    fun aConfiguredProxyIsCarriedOnTheRequest() {
        val proxied = mmsc.copy(mmsProxy = "proxy.example.net", mmsPort = 8080)
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())

        client(engine).post(byteArrayOf(1), proxied, line1 = null)

        assertEquals(ProxySpec("proxy.example.net", 8080), engine.lastRequest.proxy)
    }

    @Test
    fun anApnWithNoMmscIsInvalidRatherThanAMalformedUrl() {
        val engine = RecordingHttpEngine()
        val blank = mmsc.copy(mmscUrl = "")

        val response = client(engine).post(byteArrayOf(1), blank, line1 = null)

        assertEquals(MmscResponse.Failure(MmscFailure.INVALID_APN, null), response)
        assertTrue("nothing may be sent", engine.requests.isEmpty())
    }

    @Test
    fun aNonHttpMmscIsInvalidRatherThanAMalformedUrl() {
        val engine = RecordingHttpEngine()

        val response = client(engine).post(byteArrayOf(1), mmsc.copy(mmscUrl = "mmsc.example.net"), line1 = null)

        assertEquals(MmscResponse.Failure(MmscFailure.INVALID_APN, null), response)
        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun aRelativeContentLocationResolvesAgainstTheMmsc() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())

        client(engine).retrieve("../mmsc/inbox/42", mmsc)

        assertEquals("http://mms.example.net:8080/mmsc/inbox/42", engine.lastRequest.url)
    }

    @Test
    fun anAbsoluteContentLocationIsUsedAsGiven() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())

        client(engine).retrieve("http://other.example.net/inbox/7", mmsc)

        assertEquals("http://other.example.net/inbox/7", engine.lastRequest.url)
    }

    @Test
    fun anEmptyContentLocationIsInvalid() {
        val engine = RecordingHttpEngine()

        assertEquals(
            MmscResponse.Failure(MmscFailure.INVALID_APN, null),
            client(engine).retrieve("  ", mmsc),
        )
        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun ipv4MmscHostsLoseTheirLeadingZeros() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())
        val zeroPadded = mmsc.copy(mmscUrl = "http://010.004.000.001/servlets/mms")

        client(engine).post(byteArrayOf(1), zeroPadded, line1 = null)

        assertEquals("http://10.4.0.1/servlets/mms", engine.lastRequest.url)
    }

    @Test
    fun anAllZeroOctetSurvivesTrimming() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())

        client(engine).post(byteArrayOf(1), mmsc.copy(mmscUrl = "http://000.0.0.0/mms"), line1 = null)

        assertEquals("http://0.0.0.0/mms", engine.lastRequest.url)
    }

    @Test
    fun aNonNumericHostIsLeftAlone() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())

        client(engine).post(byteArrayOf(1), mmsc.copy(mmscUrl = "https://mms.corp.example/mms"), line1 = null)

        assertEquals("https://mms.corp.example/mms", engine.lastRequest.url)
    }

    @Test
    fun theSuccessRuleIsOneRuleAndItIsTwoHundredRange() {
        // The reference stack has a class that accepts any 2xx and a class that
        // demands exactly 200. This is the single rule both now go through.
        assertTrue(MmscHttpClient.isSuccess(200))
        assertTrue(MmscHttpClient.isSuccess(202))
        assertFalse(MmscHttpClient.isSuccess(301))
        assertFalse(MmscHttpClient.isSuccess(404))
        assertFalse(MmscHttpClient.isSuccess(500))
    }

    @Test
    fun theAcceptLanguageDefaultsToTheDeviceLocale() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())
        val client = MmscHttpClient(engine, { null }, { null })

        client.post(byteArrayOf(1), mmsc, line1 = null)

        assertEquals(Locale.getDefault().toLanguageTag(), engine.lastRequest.headers["Accept-Language"])
    }
}