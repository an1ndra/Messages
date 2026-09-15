package com.anindra.messages.diagnostics

import com.anindra.messages.crash.CrashAppInfo
import com.anindra.messages.crash.CrashDeviceInfo
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {

    private val device = CrashDeviceInfo(
        sdkInt = 35,
        model = "Pixel 7",
        manufacturer = "Google",
        brand = "google",
        fingerprint = "google/panther/panther:15/AP3A/1:user/release-keys"
    )
    private val app = CrashAppInfo(versionName = "1.0.24", versionCode = 27L)
    private val appDetails = AppDetails(
        defaultSms = true,
        permissions = listOf(
            PermissionState("SEND_SMS", true),
            PermissionState("READ_PHONE_STATE", false)
        ),
        locale = "en_US",
        timeZone = "America/New_York",
        themeMode = "system",
        notificationsEnabled = true
    )
    private val sims = listOf(
        SimInfo(7, 0, "Vodafone", "Vodafone UK", "23415", "gb", false),
        SimInfo(3, 1, null, null, null, null, true)
    )
    private val display = DisplayInfo(
        modeId = 2,
        width = 1080,
        height = 2400,
        refreshRate = 120f,
        densityDpi = 420,
        configDensityDpi = 420,
        preferredModeId = 2,
        supportedModes = listOf(
            DisplayModeInfo(1, 1080, 2400, 60f),
            DisplayModeInfo(2, 1440, 3120, 120f)
        )
    )

    @Test
    fun reportIncludesAppAndPermissionDetails() {
        val text = DiagnosticsReport.format(
            device, app, appDetails, 7, true, true, 2, sims, display, 0L
        )
        assertTrue(text.contains("Default SMS handler: true"))
        assertTrue(text.contains("Locale: en_US"))
        assertTrue(text.contains("Time zone: America/New_York"))
        assertTrue(text.contains("Theme mode: system"))
        assertTrue(text.contains("SEND_SMS: granted"))
        assertTrue(text.contains("READ_PHONE_STATE: denied"))
    }

    @Test
    fun reportIncludesSimDetails() {
        val text = DiagnosticsReport.format(
            device, app, appDetails, 7, true, true, 2, sims, display, 0L
        )
        assertTrue(text.contains("Selected subscriptionId: 7"))
        assertTrue(text.contains("Multi-SIM: true, phoneCount: 2"))
        assertTrue(text.contains("Subscription 7:"))
        assertTrue(text.contains("slotIndex: 0"))
        assertTrue(text.contains("carrierName: Vodafone"))
        assertTrue(text.contains("mccMnc: 23415"))
        assertTrue(text.contains("Subscription 3:"))
        assertTrue(text.contains("embedded: true"))
    }

    @Test
    fun reportIncludesDisplayModes() {
        val text = DiagnosticsReport.format(
            device, app, appDetails, -1, false, false, 1, emptyList(), display, 0L
        )
        assertTrue(text.contains("Current mode: id=2 1080x2400 @ 120.0Hz"))
        assertTrue(text.contains("Preferred display mode id: 2"))
        assertTrue(text.contains("id=1 1080x2400 @ 60.0Hz"))
        assertTrue(text.contains("id=2 1440x3120 @ 120.0Hz"))
    }

    @Test
    fun reportHandlesMissingSimPermission() {
        val text = DiagnosticsReport.format(
            device, app, appDetails, -1, false, false, 1, emptyList(), display, 0L
        )
        assertTrue(text.contains("READ_PHONE_STATE granted: false"))
        assertTrue(text.contains("Active subscriptions: none"))
        assertTrue(text.contains("Messages diagnostics report"))
    }
}
