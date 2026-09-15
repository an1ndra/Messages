package com.anindra.messages.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.Display
import com.anindra.messages.crash.CrashAppInfo
import com.anindra.messages.crash.CrashDeviceInfo
import com.anindra.messages.crash.CrashReporter
import com.anindra.messages.data.DownloadsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrier: String?,
    val displayName: String?,
    val mccMnc: String?,
    val countryIso: String?,
    val embedded: Boolean
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

object DiagnosticsReport {
    const val FILE_NAME = "messages-diagnostics.txt"

    fun format(
        device: CrashDeviceInfo,
        app: CrashAppInfo,
        selectedSubId: Int,
        phoneStateGranted: Boolean,
        multiSim: Boolean,
        phoneCount: Int,
        sims: List<SimInfo>,
        display: DisplayInfo,
        timestamp: Long
    ): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
        return buildString {
            appendLine("Messages diagnostics report")
            appendLine("Time: $time")
            appendLine("App: ${app.versionName} (${app.versionCode})")
            appendLine("Android SDK: ${device.sdkInt}")
            appendLine("Device: ${device.manufacturer} ${device.model} (${device.brand})")
            appendLine("Fingerprint: ${device.fingerprint}")
            appendLine()
            appendLine("--- SIM ---")
            appendLine("READ_PHONE_STATE granted: $phoneStateGranted")
            appendLine("Multi-SIM: $multiSim, phoneCount: $phoneCount")
            appendLine("Selected subscriptionId: $selectedSubId")
            if (sims.isEmpty()) {
                appendLine("Active subscriptions: none")
            } else {
                sims.forEach { s ->
                    appendLine("Subscription ${s.subscriptionId}:")
                    appendLine("  slotIndex: ${s.slotIndex}")
                    appendLine("  carrierName: ${s.carrier ?: "null"}")
                    appendLine("  displayName: ${s.displayName ?: "null"}")
                    appendLine("  mccMnc: ${s.mccMnc ?: "null"}")
                    appendLine("  countryIso: ${s.countryIso ?: "null"}")
                    appendLine("  embedded: ${s.embedded}")
                }
            }
            appendLine()
            appendLine("--- Display ---")
            appendLine("Current mode: id=${display.modeId} ${display.width}x${display.height} @ ${display.refreshRate}Hz")
            appendLine("Density: ${display.densityDpi}dpi (config ${display.configDensityDpi}dpi)")
            appendLine("Preferred display mode id: ${display.preferredModeId}")
            appendLine("Supported modes:")
            display.supportedModes.forEach { m ->
                appendLine("  id=${m.modeId} ${m.width}x${m.height} @ ${m.refreshRate}Hz")
            }
        }
    }

    fun collect(context: Context, selectedSubId: Int): String {
        val phoneStateGranted = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val tm = context.getSystemService(TelephonyManager::class.java)
        val sims = if (phoneStateGranted) {
            try {
                context.getSystemService(SubscriptionManager::class.java)
                    ?.activeSubscriptionInfoList
                    ?.map {
                        SimInfo(
                            subscriptionId = it.subscriptionId,
                            slotIndex = it.simSlotIndex,
                            carrier = it.carrierName?.toString()?.ifBlank { null },
                            displayName = it.displayName?.toString()?.ifBlank { null },
                            mccMnc = "${it.mccString ?: ""}${it.mncString ?: ""}".ifBlank { null },
                            countryIso = it.countryIso?.ifBlank { null },
                            embedded = it.isEmbedded
                        )
                    } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

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
            tm?.activeModemCount ?: 0
        } else {
            @Suppress("DEPRECATION")
            tm?.phoneCount ?: 0
        }
        return format(
            CrashReporter.deviceInfo(),
            CrashReporter.appInfo(context),
            selectedSubId,
            phoneStateGranted,
            phoneCount > 1,
            phoneCount,
            sims,
            displayInfo,
            System.currentTimeMillis()
        )
    }

    fun saveToDownloads(context: Context, selectedSubId: Int): Boolean =
        DownloadsStore.write(context, FILE_NAME, "text/plain", collect(context, selectedSubId).toByteArray())
}
