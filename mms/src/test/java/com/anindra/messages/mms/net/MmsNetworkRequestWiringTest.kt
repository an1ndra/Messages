package com.anindra.messages.mms.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The one thing here a JVM test cannot build is the platform request itself:
 * under the unit-test stubs `NetworkRequest.Builder()` answers every call with
 * a default, so the object it returns carries nothing to assert. What can be
 * pinned is the source of the only function that builds it — the two
 * conventions a request the carrier provisioned for MMS must NOT get wrong:
 * it requires MMS (not INTERNET, which an MMS-only APN does not have) and
 * pins the subscription only when one was named.
 */
class MmsNetworkRequestWiringTest {

    private val source: String by lazy {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map {
                File(
                    it,
                    "src/main/java/com/anindra/messages/mms/net/MmsNetworkBinding.kt"
                )
            }
            .firstOrNull { it.isFile }
            ?: error("MmsNetworkBinding.kt not found from ${File("").absolutePath}")
        file.readText()
    }

    @Test
    fun theRequestAsksForMmsAndNotInternet() {
        assertTrue(
            "the request must carry NET_CAPABILITY_MMS",
            source.contains("NET_CAPABILITY_MMS")
        )
        assertFalse(
            "requiring NET_CAPABILITY_INTERNET excludes the MMS-only APNs the " +
                "carrier provisions for exactly this transaction",
            source.contains("NET_CAPABILITY_INTERNET")
        )
    }

    @Test
    fun aNamedSubscriptionIsTheCaseThatPinsOne() {
        assertTrue(
            "a named subscription must be pinned with a TelephonyNetworkSpecifier",
            Regex("""if \(spec\.pinsSubscription\)[\s\S]{0,200}TelephonyNetworkSpecifier""")
                .containsMatchIn(source)
        )
    }
}
