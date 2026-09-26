package com.anindra.messages.diagnostics

import com.anindra.messages.crash.CrashAppInfo
import com.anindra.messages.data.SimCard
import com.anindra.messages.crash.CrashDeviceInfo
import org.junit.Assert.assertEquals
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
        notificationsEnabled = true,
        fontFamily = "dm_sans",
        blockedKeywords = 2
    )
    private val deviceExtra = DeviceExtra(
        release = "15",
        codename = "REL",
        incremental = "123",
        securityPatch = "2025-08-01",
        device = "panther",
        product = "panther",
        hardware = "panther",
        board = "panther",
        id = "AP3A",
        display = "AP3A",
        type = "user",
        tags = "release-keys",
        abis = "arm64-v8a",
        abis64 = "arm64-v8a",
        processors = 8,
        isEmulator = false,
        kernel = "5.15.0",
        vmVersion = "2.1.0",
        fontScale = 1.0f
    )
    private val appExtra = AppExtra(packageName = "com.anindra.messages", targetSdk = 35)
    private val system = SystemInfo(
        memoryTotalBytes = 4L * 1024 * 1024 * 1024,
        memoryAvailBytes = 2L * 1024 * 1024 * 1024,
        heapMaxBytes = 256L * 1024 * 1024,
        heapUsedBytes = 32L * 1024 * 1024,
        storageTotalBytes = 64L * 1024 * 1024 * 1024,
        storageFreeBytes = 16L * 1024 * 1024 * 1024,
        batteryLevel = 87,
        batteryCharging = true,
        dbSizeBytes = 100L * 1024,
        conversationCount = 12,
        messageCount = 345,
        pendingCrashReports = 1
    )
    private val sims = listOf(
        SimCard(7, 0, "Vodafone", "Vodafone UK", "23415", "gb", false),
        SimCard(3, 1, null, null, null, null, true)
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

    private fun report() = DiagnosticsReport.format(
        DiagnosticsData(
            device = device,
            app = app,
            appDetails = appDetails,
            deviceExtra = deviceExtra,
            appExtra = appExtra,
            system = system,
            selectedSubId = 7,
            phoneStateGranted = true,
            multiSim = true,
            phoneCount = 2,
            sims = sims,
            display = display,
            timestamp = 0L
        )
    )

    @Test
    fun reportIncludesAppAndPermissionDetails() {
        val text = report()
        assertTrue(text.contains("Default SMS handler: true"))
        assertTrue(text.contains("Locale: en_US"))
        assertTrue(text.contains("Theme mode: system"))
        assertTrue(text.contains("Font: dm_sans"))
        assertTrue(text.contains("Blocked keywords: 2"))
        assertTrue(text.contains("SEND_SMS: granted"))
        assertTrue(text.contains("READ_PHONE_STATE: denied"))
    }

    @Test
    fun reportIncludesDeviceSystemAndDataDetails() {
        val text = report()
        assertTrue(text.contains("Android: 15 (SDK 35, codename REL)"))
        assertTrue(text.contains("Security patch: 2025-08-01"))
        assertTrue(text.contains("Build ID: AP3A"))
        assertTrue(text.contains("Build type: user"))
        assertTrue(text.contains("Device: panther"))
        assertTrue(text.contains("Product: panther"))
        assertTrue(text.contains("Emulator: false"))
        assertTrue(text.contains("Kernel: 5.15.0"))
        assertTrue(text.contains("CPU cores: 8"))
        assertTrue(text.contains("Memory: 2048 / 4096 MB free"))
        assertTrue(text.contains("Battery: 87% (charging)"))
        assertTrue(text.contains("Conversations: 12"))
        assertTrue(text.contains("Messages: 345"))
        assertTrue(text.contains("Database size: 100 KB"))
        assertTrue(text.contains("Pending crash reports: 1"))
    }

    @Test
    fun reportIncludesSimAndDisplayDetails() {
        val text = report()
        assertTrue(text.contains("Selected subscriptionId: 7"))
        assertTrue(text.contains("Multi-SIM: true, phoneCount: 2"))
        assertTrue(text.contains("carrierName: Vodafone"))
        assertTrue(text.contains("Current mode: id=2 1080x2400 @ 120.0Hz"))
        assertTrue(text.contains("id=2 1440x3120 @ 120.0Hz"))
    }

    @Test
    fun reportIncludesAccessibilityModeWhenEnabled() {
        val text = DiagnosticsReport.format(
            DiagnosticsData(
                device = device,
                app = app,
                appDetails = appDetails.copy(
                    accessibilityMode = true,
                    a11yFontScale = 130,
                    a11yBold = true,
                    a11yHighContrast = true,
                    a11yReduceMotion = true,
                    a11yLargeTouch = true
                ),
                deviceExtra = deviceExtra,
                appExtra = appExtra,
                system = system,
                selectedSubId = 7,
                phoneStateGranted = true,
                multiSim = true,
                phoneCount = 2,
                sims = sims,
                display = display,
                timestamp = 0L
            )
        )
        assertTrue(text.contains("Accessibility mode: true"))
        assertTrue(text.contains("Font scale: 130%"))
        assertTrue(text.contains("Bold text: true"))
        assertTrue(text.contains("High contrast: true"))
        assertTrue(text.contains("Reduce motion: true"))
        assertTrue(text.contains("Larger touch targets: true"))
    }

    @Test
    fun reportOmitsAccessibilityDetailsWhenDisabled() {
        val text = report()
        assertTrue(text.contains("Accessibility mode: false"))
        assertTrue(!text.contains("Font scale: 130%"))
    }

    @Test
    fun reportHandlesMissingSimPermission() {
        val text = DiagnosticsReport.format(
            DiagnosticsData(
                device = device,
                app = app,
                appDetails = appDetails,
                selectedSubId = -1,
                phoneStateGranted = false,
                multiSim = false,
                phoneCount = 1,
                sims = emptyList(),
                display = display,
                timestamp = 0L
            )
        )
        assertTrue(text.contains("READ_PHONE_STATE granted: false"))
        assertTrue(text.contains("Active subscriptions: none"))
        assertTrue(text.contains("Messages diagnostics report"))
    }

    @Test
    fun phoneCountUsesActiveModemsOnSdk30Plus() {
        assertEquals(2, phoneCountForSdk(35, 2, null))
        assertEquals(0, phoneCountForSdk(30, null, 4))
    }

    @Test
    fun phoneCountFallsBackToMaxSubscriptionsBelowSdk30() {
        assertEquals(2, phoneCountForSdk(29, null, 2))
        assertEquals(0, phoneCountForSdk(29, null, null))
    }
}
