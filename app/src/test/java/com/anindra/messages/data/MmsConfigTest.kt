package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeCarrierValues(private val values: Map<String, Any>) : CarrierValues {
    override fun boolean(key: String, fallback: Boolean): Boolean =
        values[key] as? Boolean ?: fallback

    override fun integer(key: String, fallback: Int): Int =
        values[key] as? Int ?: fallback
}

class MmsConfigTest {
    @Test
    fun fallsBackToAospDefaultsWhenCarrierOmitsValues() {
        val config = MmsConfig.from(FakeCarrierValues(emptyMap()))
        assertEquals(307_200, config.maxMessageSize)
        assertEquals(640, config.maxImageWidth)
        assertEquals(480, config.maxImageHeight)
        assertFalse(config.notifyWapMmsc)
        assertFalse(config.deliveryReport)
        assertFalse(config.readReport)
    }

    @Test
    fun readsCarrierOverrides() {
        val config = MmsConfig.from(
            FakeCarrierValues(
                mapOf(
                    MmsConfig.KEY_MAX_MESSAGE_SIZE to 51_200,
                    MmsConfig.KEY_MAX_IMAGE_WIDTH to 1_080,
                    MmsConfig.KEY_MAX_IMAGE_HEIGHT to 1_080,
                    MmsConfig.KEY_NOTIFY_WAP_MMSC to true,
                    MmsConfig.KEY_DELIVERY_REPORT to true,
                    MmsConfig.KEY_READ_REPORT to true
                )
            )
        )
        assertEquals(51_200, config.maxMessageSize)
        assertEquals(1_080, config.maxImageWidth)
        assertEquals(1_080, config.maxImageHeight)
        assertTrue(config.notifyWapMmsc)
        assertTrue(config.deliveryReport)
        assertTrue(config.readReport)
    }

    @Test
    fun carrierKeysMatchThePlatformNames() {
        // These are CarrierConfigManager.KEY_MMS_* values; a rename upstream would
        // silently revert every limit to the AOSP default.
        assertEquals("maxMessageSize", MmsConfig.KEY_MAX_MESSAGE_SIZE)
        assertEquals("maxImageWidth", MmsConfig.KEY_MAX_IMAGE_WIDTH)
        assertEquals("maxImageHeight", MmsConfig.KEY_MAX_IMAGE_HEIGHT)
        assertEquals("enabledNotifyWapMMSC", MmsConfig.KEY_NOTIFY_WAP_MMSC)
        assertEquals("enableMMSDeliveryReports", MmsConfig.KEY_DELIVERY_REPORT)
        assertEquals("enableMMSReadReports", MmsConfig.KEY_READ_REPORT)
    }

    @Test
    fun clampsNegativeMessageSizeInsteadOfAcceptingIt() {
        // A carrier that reports -1 must not make every payload "fit".
        val config = MmsConfig.from(
            FakeCarrierValues(mapOf(MmsConfig.KEY_MAX_MESSAGE_SIZE to -1))
        )
        assertEquals(0, config.maxMessageSize)
        assertFalse(config.acceptsPayload(1))
    }

    @Test
    fun enforcesPayloadCap() {
        val config = MmsConfig(maxMessageSize = 1_000)
        assertTrue(config.acceptsPayload(0))
        assertTrue(config.acceptsPayload(1_000))
        assertFalse(config.acceptsPayload(1_001))
        assertFalse(config.acceptsPayload(5_000_000))
    }

    @Test
    fun reportHeadersDefaultToNo() {
        val config = MmsConfig()
        assertEquals(MmsConfig.NO, config.deliveryReportHeader())
        assertEquals(MmsConfig.NO, config.readReportHeader())
    }

    @Test
    fun reportHeadersFollowTheCarrier() {
        val config = MmsConfig(deliveryReport = true, readReport = true)
        assertEquals(MmsConfig.YES, config.deliveryReportHeader())
        assertEquals(MmsConfig.YES, config.readReportHeader())
    }
}
