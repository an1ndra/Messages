package com.anindra.messages.mms.transport

import android.telephony.SmsManager
import com.anindra.messages.mms.Wire
import com.anindra.messages.mms.net.CarrierProfile
import com.anindra.messages.mms.profileStoreOf
import com.anindra.messages.mms.pdu.EncodedStringValue
import com.anindra.messages.mms.pdu.HeaderField
import com.anindra.messages.mms.pdu.MessageType
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.pdu.PduBody
import com.anindra.messages.mms.pdu.PduPart
import com.anindra.messages.mms.spi.MmsDownloadTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The platform path's two testable claims are that every PendingIntent it builds
 * carries a mutability flag -- unreadable off a real PendingIntent under plain
 * JUnit, so it is asserted from the described callback -- and that the composed
 * PDU really is written to the cache dir and served from the FileProvider the app
 * declared.
 */
class SystemMmsTransportTest {

    @get:Rule
    val cache = TemporaryFolder()

    private val platform = RecordingMmsPlatform()
    private val authority = "com.anindra.messages.fileprovider"
    private val targetRow = "content://mms/42"
    private val profiles = profileStoreOf(
        CarrierProfile.KEY_MAX_MESSAGE_SIZE to 12_345,
        CarrierProfile.KEY_HTTP_SOCKET_TIMEOUT to 9_000,
        CarrierProfile.KEY_USER_AGENT to "Messages/1.0",
        CarrierProfile.KEY_UA_PROFILE_URL to "http://mmsc.test/uaprof.xml",
    )

    // Built in @Before rather than in a field: the rule has not created the
    // folder when a field initialiser runs.
    private lateinit var transport: SystemMmsTransport

    @Before
    fun setUp() {
        transport = transportOver(platform, MmsDownloadTarget { _, _ -> targetRow })
    }

    private fun transportOver(
        platform: MmsPlatform,
        targets: MmsDownloadTarget,
    ) = SystemMmsTransport(
        fileProviderAuthority = authority,
        cacheDir = cache.root,
        targets = targets,
        carrierProfiles = profiles,
        platform = platform,
    )

    private fun sendReq(transactionId: String = "T-send"): Pdu = Pdu(MessageType.SEND_REQ).apply {
        headers.setContentType("application/vnd.wap.multipart.related")
        headers.setText(HeaderField.TRANSACTION_ID, transactionId)
        headers.addEncoded(HeaderField.TO, EncodedStringValue.utf8("+15559998888"))
        from = EncodedStringValue.insertAddressToken()
        body = PduBody().also { body ->
            body.add(
                PduPart().apply {
                    contentType = "image/jpeg"
                    name = "image"
                    data = ByteArray(8)
                },
            )
        }
    }

    @Test
    fun theTransportReportsTheChoiceItIsRegisteredUnder() {
        assertEquals(TransportChoice.SYSTEM.id, transport.id)
        assertTrue(transport.reportsThroughPendingIntent)
        assertTrue(transport.ownsNotificationRow)
    }

    @Test
    fun everyCallbackItBuildsCarriesAMutabilityFlag() {
        assertTrue(transport.send(sendReq(), Wire.SUBSCRIPTION_ID, SilentTransportListener))
        assertTrue(transport.retrieve(Wire.notificationInd(), Wire.SUBSCRIPTION_ID, SilentTransportListener))

        assertEquals(2, platform.callbacks.size)
        platform.callbacks.forEach { callback ->
            assertTrue("$callback carries no mutability flag", callback.hasMutabilityFlag)
            assertEquals(MmsCallback.MUTABLE, callback.mutabilityFlags)
        }
    }

    @Test
    fun theFlagsAlsoSayThePlatformMayFillTheResultIn() {
        transport.send(sendReq(), Wire.SUBSCRIPTION_ID, SilentTransportListener)
        val flags = platform.callbacks.single().flags
        assertEquals(MmsCallback.RESULT_FLAGS, flags)
        assertTrue(flags and MmsCallback.MUTABLE != 0)
    }

    @Test
    fun theComposedPduIsServedFromTheFileProvider() {
        transport.send(sendReq(), Wire.SUBSCRIPTION_ID, SilentTransportListener)
        val location = platform.sends.single().locationUri
        assertTrue(location.startsWith("content://$authority/mms/"))
        val file = cache.root.resolve(location.removePrefix("content://$authority/mms/"))
        assertTrue("no file written at $file", file.isFile)
        assertTrue(file.length() > 0)
    }

    @Test
    fun twoSendsDoNotShareAFile() {
        transport.send(sendReq("T-one"), Wire.SUBSCRIPTION_ID, SilentTransportListener)
        transport.send(sendReq("T-two"), Wire.SUBSCRIPTION_ID, SilentTransportListener)
        val names = platform.sends.map { it.locationUri.substringAfterLast('/') }
        assertEquals(2, names.toSet().size)
        assertTrue(names.all { it.startsWith(SystemMmsTransport.FILE_PREFIX) })
    }

    @Test
    fun theConfigOverridesAreTheCarrierProfileValues() {
        transport.send(sendReq(), Wire.SUBSCRIPTION_ID, SilentTransportListener)
        val overrides = platform.sends.single().configOverrides
        assertEquals(12_345, overrides[SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE])
        assertEquals(9_000, overrides[SmsManager.MMS_CONFIG_HTTP_SOCKET_TIMEOUT])
        assertEquals("Messages/1.0", overrides[SmsManager.MMS_CONFIG_USER_AGENT])
        assertEquals("http://mmsc.test/uaprof.xml", overrides[SmsManager.MMS_CONFIG_UA_PROF_URL])
    }

    @Test
    fun onlyTheProfileOwnedKeysAreOverridden() {
        transport.send(sendReq(), Wire.SUBSCRIPTION_ID, SilentTransportListener)
        assertEquals(
            setOf(
                SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE,
                SmsManager.MMS_CONFIG_HTTP_SOCKET_TIMEOUT,
                SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH,
                SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT,
                SmsManager.MMS_CONFIG_USER_AGENT,
                SmsManager.MMS_CONFIG_UA_PROF_URL,
            ),
            platform.sends.single().configOverrides.keys,
        )
    }

    @Test
    fun theCallbackCarriesWhatTheAppNeedsToCorrelateAndCleanUp() {
        transport.send(sendReq("T-xyz"), 7, SilentTransportListener)
        val sent = platform.callbacks.single()
        assertEquals(SystemMmsTransport.ACTION_SEND_SENT, sent.action)
        assertEquals("T-xyz", sent.extras[SystemMmsTransport.EXTRA_TRANSACTION_ID])
        assertEquals("7", sent.extras[SystemMmsTransport.EXTRA_SUBSCRIPTION_ID])
        assertEquals(
            platform.sends.single().locationUri.substringAfterLast('/'),
            sent.extras[SystemMmsTransport.EXTRA_LOCATION],
        )
    }

    @Test
    fun theRetrieveIsIssuedAgainstTheNotificationRow() {
        transport.retrieve(Wire.notificationInd("T-dl"), Wire.SUBSCRIPTION_ID, SilentTransportListener)
        val download = platform.downloads.single()
        assertEquals("http://mmsc.test/mms/inbox/7", download.locationUrl)
        assertEquals(targetRow, download.contentUri)
        assertEquals(SystemMmsTransport.ACTION_DOWNLOAD_COMPLETE, download.completion.action)
        assertEquals("T-dl", download.completion.extras[SystemMmsTransport.EXTRA_TRANSACTION_ID])
    }

    @Test
    fun aSendThatCannotBeComposedNeverStarts() {
        assertFalse(transport.send(Pdu(MessageType.SEND_CONF), Wire.SUBSCRIPTION_ID, SilentTransportListener))
        assertTrue(platform.sends.isEmpty())
        assertEquals(0, cache.root.listFiles()?.size ?: 0)
    }

    @Test
    fun aRetrieveWithNoDestinationRowNeverStarts() {
        val targetless = transportOver(platform, MmsDownloadTarget { _, _ -> null })
        assertFalse(targetless.retrieve(Wire.notificationInd(), Wire.SUBSCRIPTION_ID, SilentTransportListener))
        assertTrue(platform.downloads.isEmpty())
    }

    @Test
    fun availabilityComesFromWhetherThePlatformRefuses() {
        val refused = transportOver(RecordingMmsPlatform(available = false), MmsDownloadTarget { _, _ -> targetRow })
        assertFalse(refused.isAvailable(Wire.SUBSCRIPTION_ID))
        assertTrue(transport.isAvailable(Wire.SUBSCRIPTION_ID))
    }
}