package com.anindra.messages.mms.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierProfileTest {
    private val appDefaults = MapCarrierValues(
        mapOf(
            CarrierProfile.KEY_MAX_MESSAGE_SIZE to 300_000,
            CarrierProfile.KEY_MAX_IMAGE_WIDTH to 1_024,
            CarrierProfile.KEY_MAX_IMAGE_HEIGHT to 768,
            CarrierProfile.KEY_NOTIFY_WAP_MMSC to false,
            CarrierProfile.KEY_TRANS_ID_ENABLED to false,
            CarrierProfile.KEY_GROUP_MMS_ENABLED to true,
            CarrierProfile.KEY_HTTP_SOCKET_TIMEOUT to 45_000,
            CarrierProfile.KEY_UA_PROFILE_URL to "http://app.example/u.xml",
            CarrierProfile.KEY_USER_AGENT to "AppDefault/1.0",
        )
    )

    @Test
    fun appDefaultsAreUsedWhereThePlatformIsSilent() {
        val profile = CarrierProfile(appDefaults, platform = null)

        assertEquals(300_000, profile.maxMessageSize())
        assertEquals(1_024, profile.maxImageWidth())
        assertEquals(768, profile.maxImageHeight())
        assertEquals(45_000, profile.httpSocketTimeout())
        assertFalse(profile.notifyWapMmsc())
        assertFalse(profile.transIdEnabled())
        assertTrue(profile.groupMmsEnabled())
        assertEquals("http://app.example/u.xml", profile.uaProfUrl())
        assertEquals("AppDefault/1.0", profile.userAgent())
    }

    @Test
    fun thePlatformWinsWhereTheTwoLayersDisagree() {
        val platform = MapCarrierValues(
            mapOf(
                CarrierProfile.KEY_MAX_MESSAGE_SIZE to 512_000,
                CarrierProfile.KEY_MAX_IMAGE_WIDTH to 2_048,
                CarrierProfile.KEY_MAX_IMAGE_HEIGHT to 1_536,
                CarrierProfile.KEY_NOTIFY_WAP_MMSC to true,
                CarrierProfile.KEY_TRANS_ID_ENABLED to true,
                CarrierProfile.KEY_GROUP_MMS_ENABLED to false,
                CarrierProfile.KEY_HTTP_SOCKET_TIMEOUT to 90_000,
                CarrierProfile.KEY_UA_PROFILE_URL to "http://carrier.example/u.xml",
                CarrierProfile.KEY_USER_AGENT to "CarrierAgent/2.0",
            )
        )
        val profile = CarrierProfile(appDefaults, platform)

        assertEquals(512_000, profile.maxMessageSize())
        assertEquals(2_048, profile.maxImageWidth())
        assertEquals(1_536, profile.maxImageHeight())
        assertTrue(profile.notifyWapMmsc())
        assertTrue(profile.transIdEnabled())
        assertFalse(profile.groupMmsEnabled())
        assertEquals(90_000, profile.httpSocketTimeout())
        assertEquals("http://carrier.example/u.xml", profile.uaProfUrl())
        assertEquals("CarrierAgent/2.0", profile.userAgent())
    }

    @Test
    fun theLayersAreAppliedPerKeyNotWholesale() {
        val platform = MapCarrierValues(
            mapOf(CarrierProfile.KEY_MAX_MESSAGE_SIZE to 1_000_000)
        )
        val profile = CarrierProfile(appDefaults, platform)

        assertEquals("platform wins here", 1_000_000, profile.maxMessageSize())
        assertEquals("app default still fills the gap", 1_024, profile.maxImageWidth())
    }

    @Test
    fun aCompletelyAbsentProfileFallsBackToTheHardDefaults() {
        val profile = CarrierProfile(MapCarrierValues(emptyMap()), platform = null)

        assertEquals(CarrierProfile.DEFAULT_MAX_MESSAGE_SIZE, profile.maxMessageSize())
        assertEquals(CarrierProfile.DEFAULT_MAX_IMAGE_WIDTH, profile.maxImageWidth())
        assertEquals(CarrierProfile.DEFAULT_MAX_IMAGE_HEIGHT, profile.maxImageHeight())
        assertEquals(CarrierProfile.DEFAULT_SOCKET_TIMEOUT_MS, profile.httpSocketTimeout())
        assertFalse(profile.notifyWapMmsc())
        assertFalse(profile.transIdEnabled())
        assertTrue(profile.groupMmsEnabled())
        assertNull(profile.uaProfUrl())
        assertNull(profile.userAgent())
    }

    @Test
    fun anEmptyPlatformBundleStillDegradesToTheAppLayer() {
        val profile = CarrierProfile(appDefaults, MapCarrierValues(emptyMap()))

        assertEquals(300_000, profile.maxMessageSize())
        assertEquals("AppDefault/1.0", profile.userAgent())
    }

    @Test
    fun everyGetterSurvivesAnAbsentConfigWithoutThrowing() {
        val profile = CarrierProfile(MapCarrierValues(emptyMap()), null)

        // Every getter is called; a regression that introduces a non-nullable
        // platform layer fails here rather than on a carrier at runtime.
        listOf<() -> Any?>(
            profile::maxMessageSize,
            profile::maxImageWidth,
            profile::maxImageHeight,
            profile::httpSocketTimeout,
            profile::notifyWapMmsc,
            profile::transIdEnabled,
            profile::groupMmsEnabled,
            profile::uaProfUrl,
            profile::userAgent,
        ).forEach { it() }
    }

    @Test
    fun aNonsensicalSocketTimeoutIsIgnored() {
        val profile = CarrierProfile(MapCarrierValues(emptyMap()), MapCarrierValues(
            mapOf(CarrierProfile.KEY_HTTP_SOCKET_TIMEOUT to 0)
        ))

        assertEquals(CarrierProfile.DEFAULT_SOCKET_TIMEOUT_MS, profile.httpSocketTimeout())
    }

    @Test
    fun aNegativeMessageSizeIsClampedToZero() {
        val profile = CarrierProfile(MapCarrierValues(emptyMap()), MapCarrierValues(
            mapOf(CarrierProfile.KEY_MAX_MESSAGE_SIZE to -5)
        ))

        assertEquals(0, profile.maxMessageSize())
    }

    @Test
    fun aBlankProfileUrlOrUserAgentReadsAsAbsent() {
        val profile = CarrierProfile(MapCarrierValues(emptyMap()), MapCarrierValues(
            mapOf(
                CarrierProfile.KEY_UA_PROFILE_URL to "   ",
                CarrierProfile.KEY_USER_AGENT to "",
            )
        ))

        assertNull(profile.uaProfUrl())
        assertNull(profile.userAgent())
    }

    @Test
    fun theProfileIsCachedPerSubscriptionUntilInvalidated() {
        var loads = 0
        val source = CarrierConfigSource { subscriptionId ->
            loads++
            MapCarrierValues(mapOf(CarrierProfile.KEY_MAX_MESSAGE_SIZE to 1_000 * subscriptionId))
        }
        val store = CarrierProfileStore(MapCarrierValues(emptyMap()), source, { -1 })

        assertEquals(1_000, store.of(1).maxMessageSize())
        assertEquals(1_000, store.of(1).maxMessageSize())
        assertEquals(2_000, store.of(2).maxMessageSize())
        assertEquals(2, loads)

        store.invalidate()

        assertEquals(1_000, store.of(1).maxMessageSize())
        assertEquals(3, loads)
    }

    @Test
    fun aSubscriptionWithoutConfigStillYieldsAUsableProfile() {
        val store = CarrierProfileStore(MapCarrierValues(emptyMap()), CarrierConfigSource { null }, { -1 })

        assertEquals(CarrierProfile.DEFAULT_MAX_MESSAGE_SIZE, store.of(4).maxMessageSize())
    }

    @Test
    fun aNonPositiveSubscriptionIdUsesTheDefaultSmsSubscription() {
        val store = CarrierProfileStore(
            MapCarrierValues(emptyMap()),
            CarrierConfigSource { id -> MapCarrierValues(mapOf(CarrierProfile.KEY_MAX_MESSAGE_SIZE to id)) },
            { 8 },
        )

        assertEquals(8, store.of(-1).maxMessageSize())
        assertEquals(8, store.of(0).maxMessageSize())
    }

    @Test
    fun theCarrierConfigKeyNamesAreTheOnesTheModemReads() {
        assertEquals("maxMessageSize", CarrierProfile.KEY_MAX_MESSAGE_SIZE)
        assertEquals("maxImageWidth", CarrierProfile.KEY_MAX_IMAGE_WIDTH)
        assertEquals("maxImageHeight", CarrierProfile.KEY_MAX_IMAGE_HEIGHT)
        assertEquals("enabledNotifyWapMMSC", CarrierProfile.KEY_NOTIFY_WAP_MMSC)
        assertEquals("enableMmsTransId", CarrierProfile.KEY_TRANS_ID_ENABLED)
        assertEquals("enableGroupMms", CarrierProfile.KEY_GROUP_MMS_ENABLED)
        assertEquals("httpSocketTimeout", CarrierProfile.KEY_HTTP_SOCKET_TIMEOUT)
        assertEquals("mmsUaProfileUrl", CarrierProfile.KEY_UA_PROFILE_URL)
        assertEquals("mmsUserAgent", CarrierProfile.KEY_USER_AGENT)
    }

    @Test
    fun anImageCapIsOnlyRealWhenTheCarrierDeclaredIt() {
        // The app-defaults layer always carries 640x480, so it cannot answer
        // this: only the platform layer can say a cap was actually declared.
        val silent = CarrierProfile(
            MapCarrierValues(
                mapOf(
                    CarrierProfile.KEY_MAX_IMAGE_WIDTH to 640,
                    CarrierProfile.KEY_MAX_IMAGE_HEIGHT to 480,
                )
            ),
            platform = null,
        )
        assertFalse(silent.imageLimitsReported())

        val empty = CarrierProfile(appDefaults, MapCarrierValues(emptyMap()))
        assertFalse(empty.imageLimitsReported())
    }

    @Test
    fun aDeclaredImageCapIsReported() {
        val declared = CarrierProfile(
            appDefaults,
            MapCarrierValues(
                mapOf(
                    CarrierProfile.KEY_MAX_IMAGE_WIDTH to 1_080,
                    CarrierProfile.KEY_MAX_IMAGE_HEIGHT to 1_080,
                )
            ),
        )

        assertTrue(declared.imageLimitsReported())
        assertEquals(1_080, declared.maxImageWidth())
    }

    @Test
    fun halfAnImageCapIsNotACap() {
        val half = CarrierProfile(
            appDefaults,
            MapCarrierValues(mapOf(CarrierProfile.KEY_MAX_IMAGE_WIDTH to 1_080)),
        )

        assertFalse(half.imageLimitsReported())
    }
}
