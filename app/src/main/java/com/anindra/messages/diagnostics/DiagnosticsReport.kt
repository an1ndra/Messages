package com.anindra.messages.diagnostics

import android.Manifest
import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.view.Display
import com.anindra.messages.crash.CrashAppInfo
import com.anindra.messages.crash.CrashDeviceInfo
import com.anindra.messages.crash.CrashReporter
import com.anindra.messages.data.DownloadsStore
import com.anindra.messages.data.SettingsStore
import com.anindra.messages.data.SimCard
import com.anindra.messages.data.SimCards
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PermissionState(
    val name: String,
    val granted: Boolean
)

data class AppDetails(
    val defaultSms: Boolean,
    val permissions: List<PermissionState>,
    val locale: String,
    val timeZone: String,
    val themeMode: String,
    val notificationsEnabled: Boolean,
    val fontFamily: String = "",
    val sendSound: Boolean = false,
    val receiveSound: Boolean = false,
    val privacyMode: Boolean = false,
    val appLock: Boolean = false,
    val drafts: Boolean = false,
    val blockedKeywords: Int = 0
)

data class DeviceExtra(
    val release: String = "",
    val codename: String = "",
    val incremental: String = "",
    val securityPatch: String = "",
    val baseOs: String = "",
    val device: String = "",
    val product: String = "",
    val hardware: String = "",
    val board: String = "",
    val bootloader: String = "",
    val id: String = "",
    val display: String = "",
    val type: String = "",
    val tags: String = "",
    val host: String = "",
    val user: String = "",
    val abis: String = "",
    val abis32: String = "",
    val abis64: String = "",
    val buildTime: Long = 0L,
    val isEmulator: Boolean = false,
    val kernel: String = "",
    val vmVersion: String = "",
    val processors: Int = 0,
    val fontScale: Float = 1f
)

data class AppExtra(
    val packageName: String = "",
    val targetSdk: Int = 0,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L
)

data class SystemInfo(
    val memoryTotalBytes: Long = 0L,
    val memoryAvailBytes: Long = 0L,
    val lowMemory: Boolean = false,
    val heapMaxBytes: Long = 0L,
    val heapUsedBytes: Long = 0L,
    val storageTotalBytes: Long = 0L,
    val storageFreeBytes: Long = 0L,
    val batteryLevel: Int = -1,
    val batteryCharging: Boolean = false,
    val dbSizeBytes: Long = 0L,
    val conversationCount: Int = 0,
    val messageCount: Int = 0,
    val pendingCrashReports: Int = 0
)

data class DisplayModeInfo(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float
)

data class DisplayInfo(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val densityDpi: Int,
    val configDensityDpi: Int,
    val preferredModeId: Int,
    val supportedModes: List<DisplayModeInfo>
)

data class DiagnosticsData(
    val device: CrashDeviceInfo,
    val app: CrashAppInfo,
    val appDetails: AppDetails,
    val deviceExtra: DeviceExtra = DeviceExtra(),
    val appExtra: AppExtra = AppExtra(),
    val system: SystemInfo = SystemInfo(),
    val selectedSubId: Int,
    val phoneStateGranted: Boolean,
    val multiSim: Boolean,
    val phoneCount: Int,
    val sims: List<SimCard>,
    val display: DisplayInfo,
    val timestamp: Long
)

object DiagnosticsReport {
    const val FILE_NAME = "messages-diagnostics.txt"

    fun format(data: DiagnosticsData): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(data.timestamp))
        return buildString {
            appendLine("Messages diagnostics report")
            appendLine("Time: $time")
            appendLine()
            appendLine("--- App ---")
            appendLine("Version: ${data.app.versionName} (${data.app.versionCode})")
            appendLine("Package: ${data.appExtra.packageName}")
            appendLine("Target SDK: ${data.appExtra.targetSdk}")
            appendLine("First install: ${date(data.appExtra.firstInstallTime)}")
            appendLine("Last update: ${date(data.appExtra.lastUpdateTime)}")
            appendLine("Default SMS handler: ${data.appDetails.defaultSms}")
            appendLine("Locale: ${data.appDetails.locale}")
            appendLine("Time zone: ${data.appDetails.timeZone}")
            appendLine("Theme mode: ${data.appDetails.themeMode}")
            appendLine("Font: ${data.appDetails.fontFamily}")
            appendLine("Notifications enabled: ${data.appDetails.notificationsEnabled}")
            appendLine("Send sound: ${data.appDetails.sendSound}")
            appendLine("Receive sound: ${data.appDetails.receiveSound}")
            appendLine("Privacy mode: ${data.appDetails.privacyMode}")
            appendLine("App lock: ${data.appDetails.appLock}")
            appendLine("Drafts: ${data.appDetails.drafts}")
            appendLine("Blocked keywords: ${data.appDetails.blockedKeywords}")
            appendLine("Permissions:")
            data.appDetails.permissions.forEach { p ->
                appendLine("  ${p.name}: ${if (p.granted) "granted" else "denied"}")
            }
            appendLine()
            appendLine("--- Device ---")
            appendLine("Android: ${data.deviceExtra.release} (SDK ${data.device.sdkInt}, codename ${data.deviceExtra.codename})")
            appendLine("Incremental: ${data.deviceExtra.incremental}")
            appendLine("Security patch: ${data.deviceExtra.securityPatch}")
            appendLine("Base OS: ${data.deviceExtra.baseOs}")
            appendLine("Model: ${data.device.manufacturer} ${data.device.model} (${data.device.brand})")
            appendLine("Device: ${data.deviceExtra.device}")
            appendLine("Product: ${data.deviceExtra.product}")
            appendLine("Hardware: ${data.deviceExtra.hardware}")
            appendLine("Board: ${data.deviceExtra.board}")
            appendLine("Bootloader: ${data.deviceExtra.bootloader}")
            appendLine("Build ID: ${data.deviceExtra.id}")
            appendLine("Build display: ${data.deviceExtra.display}")
            appendLine("Build type: ${data.deviceExtra.type}")
            appendLine("Build tags: ${data.deviceExtra.tags}")
            appendLine("Build host: ${data.deviceExtra.host}")
            appendLine("Build user: ${data.deviceExtra.user}")
            appendLine("ABIs: ${data.deviceExtra.abis}")
            appendLine("ABIs 32: ${data.deviceExtra.abis32}")
            appendLine("ABIs 64: ${data.deviceExtra.abis64}")
            appendLine("Build time: ${date(data.deviceExtra.buildTime)}")
            appendLine("Emulator: ${data.deviceExtra.isEmulator}")
            appendLine("Kernel: ${data.deviceExtra.kernel}")
            appendLine("Java VM: ${data.deviceExtra.vmVersion}")
            appendLine("CPU cores: ${data.deviceExtra.processors}")
            appendLine("Font scale: ${data.deviceExtra.fontScale}")
            appendLine("Fingerprint: ${data.device.fingerprint}")
            appendLine()
            appendLine("--- System ---")
            appendLine("Memory: ${mb(data.system.memoryAvailBytes)} / ${mb(data.system.memoryTotalBytes)} MB free")
            appendLine("Low memory: ${data.system.lowMemory}")
            appendLine("App heap: ${mb(data.system.heapUsedBytes)} / ${mb(data.system.heapMaxBytes)} MB used")
            appendLine("Storage: ${mb(data.system.storageFreeBytes)} / ${mb(data.system.storageTotalBytes)} MB free")
            appendLine("Battery: ${data.system.batteryLevel}% (${if (data.system.batteryCharging) "charging" else "discharging"})")
            appendLine()
            appendLine("--- Data ---")
            appendLine("Conversations: ${data.system.conversationCount}")
            appendLine("Messages: ${data.system.messageCount}")
            appendLine("Database size: ${kb(data.system.dbSizeBytes)} KB")
            appendLine("Pending crash reports: ${data.system.pendingCrashReports}")
            appendLine()
            appendLine("--- SIM ---")
            appendLine("READ_PHONE_STATE granted: ${data.phoneStateGranted}")
            appendLine("Multi-SIM: ${data.multiSim}, phoneCount: ${data.phoneCount}")
            appendLine("Selected subscriptionId: ${data.selectedSubId}")
            if (data.sims.isEmpty()) {
                appendLine("Active subscriptions: none")
            } else {
                data.sims.forEach { s ->
                    appendLine("Subscription ${s.subscriptionId}:")
                    appendLine("  slotIndex: ${s.slotIndex}")
                    appendLine("  carrierName: ${s.carrierName ?: "null"}")
                    appendLine("  displayName: ${s.displayName ?: "null"}")
                    appendLine("  mccMnc: ${s.mccMnc ?: "null"}")
                    appendLine("  countryIso: ${s.countryIso ?: "null"}")
                    appendLine("  embedded: ${s.embedded}")
                }
            }
            appendLine()
            appendLine("--- Display ---")
            appendLine("Current mode: id=${data.display.modeId} ${data.display.width}x${data.display.height} @ ${data.display.refreshRate}Hz")
            appendLine("Density: ${data.display.densityDpi}dpi (config ${data.display.configDensityDpi}dpi)")
            appendLine("Preferred display mode id: ${data.display.preferredModeId}")
            appendLine("Supported modes:")
            data.display.supportedModes.forEach { m ->
                appendLine("  id=${m.modeId} ${m.width}x${m.height} @ ${m.refreshRate}Hz")
            }
        }
    }

    private fun date(millis: Long): String =
        if (millis <= 0) "unknown"
        else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))

    private fun mb(bytes: Long): Long = bytes / (1024 * 1024)

    private fun kb(bytes: Long): Long = bytes / 1024

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.lowercase().contains("emulator") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.PRODUCT == "google_sdk" ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")

    fun collect(
        context: Context,
        selectedSubId: Int,
        settings: SettingsStore,
        conversationCount: Int,
        messageCount: Int
    ): String {
        val phoneStateGranted = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val tm = context.getSystemService(TelephonyManager::class.java)
        val sims = SimCards.load(context)

        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display?.mode
        val displayInfo = DisplayInfo(
            modeId = mode?.modeId ?: -1,
            width = mode?.physicalWidth ?: 0,
            height = mode?.physicalHeight ?: 0,
            refreshRate = mode?.refreshRate ?: 0f,
            densityDpi = context.resources.displayMetrics.densityDpi,
            configDensityDpi = context.resources.configuration.densityDpi,
            preferredModeId = (context as? android.app.Activity)?.window?.attributes?.preferredDisplayModeId ?: 0,
            supportedModes = display?.supportedModes?.map {
                DisplayModeInfo(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate)
            } ?: emptyList()
        )

        val phoneCount = if (Build.VERSION.SDK_INT >= 30) {
            phoneCountForSdk(30, tm?.activeModemCount, null)
        } else {
            val subscriptionManager =
                context.getSystemService(android.telephony.SubscriptionManager::class.java)
            phoneCountForSdk(
                29, null,
                subscriptionManager?.activeSubscriptionInfoCountMax
            )
        }

        val dbFile = context.getDatabasePath("messages.db")
        val stat = try { StatFs(context.filesDir.path) } catch (_: Exception) { null }
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
        val runtime = Runtime.getRuntime()
        val battery = context.getSystemService(BatteryManager::class.java)
        val packageInfo = try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }

        return format(
            DiagnosticsData(
                device = CrashReporter.deviceInfo(),
                app = CrashReporter.appInfo(context),
                appDetails = appDetails(context, settings),
                deviceExtra = DeviceExtra(
                    release = Build.VERSION.RELEASE ?: "",
                    codename = Build.VERSION.CODENAME ?: "",
                    incremental = Build.VERSION.INCREMENTAL ?: "",
                    securityPatch = Build.VERSION.SECURITY_PATCH ?: "",
                    baseOs = Build.VERSION.BASE_OS ?: "",
                    device = Build.DEVICE ?: "",
                    product = Build.PRODUCT ?: "",
                    hardware = Build.HARDWARE ?: "",
                    board = Build.BOARD ?: "",
                    bootloader = Build.BOOTLOADER ?: "",
                    id = Build.ID ?: "",
                    display = Build.DISPLAY ?: "",
                    type = Build.TYPE ?: "",
                    tags = Build.TAGS ?: "",
                    host = Build.HOST ?: "",
                    user = Build.USER ?: "",
                    abis = Build.SUPPORTED_ABIS?.joinToString(", ") ?: "",
                    abis32 = Build.SUPPORTED_32_BIT_ABIS?.joinToString(", ") ?: "",
                    abis64 = Build.SUPPORTED_64_BIT_ABIS?.joinToString(", ") ?: "",
                    buildTime = Build.TIME,
                    isEmulator = isEmulator(),
                    kernel = System.getProperty("os.version") ?: "",
                    vmVersion = System.getProperty("java.vm.version") ?: "",
                    processors = Runtime.getRuntime().availableProcessors(),
                    fontScale = context.resources.configuration.fontScale
                ),
                appExtra = AppExtra(
                    packageName = context.packageName,
                    targetSdk = context.applicationInfo.targetSdkVersion,
                    firstInstallTime = packageInfo?.firstInstallTime ?: 0L,
                    lastUpdateTime = packageInfo?.lastUpdateTime ?: 0L
                ),
                system = SystemInfo(
                    memoryTotalBytes = memory.totalMem,
                    memoryAvailBytes = memory.availMem,
                    lowMemory = memory.lowMemory,
                    heapMaxBytes = runtime.maxMemory(),
                    heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
                    storageTotalBytes = stat?.totalBytes ?: 0L,
                    storageFreeBytes = stat?.availableBytes ?: 0L,
                    batteryLevel = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1,
                    batteryCharging = battery?.isCharging ?: false,
                    dbSizeBytes = if (dbFile != null && dbFile.exists()) dbFile.length() else 0L,
                    conversationCount = conversationCount,
                    messageCount = messageCount,
                    pendingCrashReports = CrashReporter.pending(context).size
                ),
                selectedSubId = selectedSubId,
                phoneStateGranted = phoneStateGranted,
                multiSim = phoneCount > 1,
                phoneCount = phoneCount,
                sims = sims,
                display = displayInfo,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun appDetails(context: Context, settings: SettingsStore): AppDetails {
        val defaultSms = try {
            val rm = context.getSystemService(RoleManager::class.java)
            (rm?.isRoleHeld(RoleManager.ROLE_SMS) ?: false) ||
                Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        } catch (_: Exception) {
            false
        }
        val permissions = listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.POST_NOTIFICATIONS
        ).map {
            PermissionState(
                it.substringAfterLast('.'),
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            )
        }
        return AppDetails(
            defaultSms = defaultSms,
            permissions = permissions,
            locale = Locale.getDefault().toString(),
            timeZone = TimeZone.getDefault().id,
            themeMode = settings.themeMode,
            notificationsEnabled = settings.notificationsEnabled,
            fontFamily = settings.fontFamily,
            sendSound = settings.sendSoundEnabled,
            receiveSound = settings.receiveSoundEnabled,
            privacyMode = settings.privacyModeEnabled,
            appLock = settings.appLockEnabled,
            drafts = settings.draftsEnabled,
            blockedKeywords = settings.blockedKeywords.size
        )
    }
}

internal fun phoneCountForSdk(sdkInt: Int, activeModemCount: Int?, maxSubscriptions: Int?): Int =
    if (sdkInt >= 30) activeModemCount ?: 0 else maxSubscriptions ?: 0
