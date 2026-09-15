package com.anindra.messages.crash

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class CrashDeviceInfo(
    val sdkInt: Int,
    val model: String,
    val manufacturer: String,
    val brand: String,
    val fingerprint: String
)

data class CrashAppInfo(
    val versionName: String,
    val versionCode: Long
)

object CrashReportFormatter {
    fun format(
        throwable: Throwable,
        device: CrashDeviceInfo,
        app: CrashAppInfo,
        timestamp: Long
    ): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
        return buildString {
            appendLine("Messages crash report")
            appendLine("Time: $time")
            appendLine("App: ${app.versionName} (${app.versionCode})")
            appendLine("Android SDK: ${device.sdkInt}")
            appendLine("Device: ${device.manufacturer} ${device.model} (${device.brand})")
            appendLine("Fingerprint: ${device.fingerprint}")
            appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
            appendLine()
            appendLine(throwable.stackTraceToString())
        }
    }

    fun reportFileName(timestamp: Long): String =
        "crash-${stamp(timestamp)}.txt"

    fun zipFileName(timestamp: Long): String =
        "messages-crash-${stamp(timestamp)}.zip"

    private fun stamp(timestamp: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestamp))
}

object CrashReportStore {
    const val DIR_NAME = "crash_reports"
    private const val MAX_REPORTS = 5

    fun dir(context: Context): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun save(context: Context, text: String, timestamp: Long): File? = try {
        val file = File(dir(context), CrashReportFormatter.reportFileName(timestamp))
        file.writeText(text)
        prune(context)
        file
    } catch (_: Exception) {
        null
    }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun readAll(context: Context): String =
        list(context).joinToString("\n\n---\n\n") { it.readText() }

    fun prune(context: Context) {
        val files = dir(context).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedBy { it.name } ?: return
        if (files.size > MAX_REPORTS) files.dropLast(MAX_REPORTS).forEach { it.delete() }
    }

    fun buildZip(out: OutputStream, entries: List<Pair<String, String>>) {
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    fun exportZip(context: Context, timestamp: Long): File? = try {
        val d = dir(context)
        d.listFiles { f -> f.isFile && f.name.endsWith(".zip") }?.forEach { it.delete() }
        val entries = list(context).map { it.name to it.readText() }
        if (entries.isEmpty()) null else {
            val zip = File(d, CrashReportFormatter.zipFileName(timestamp))
            zip.outputStream().use { buildZip(it, entries) }
            zip
        }
    } catch (_: Exception) {
        null
    }

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}

object CrashReporter {
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val now = System.currentTimeMillis()
                val text = CrashReportFormatter.format(throwable, deviceInfo(), appInfo(context), now)
                CrashReportStore.save(context.applicationContext, text, now)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun deviceInfo(): CrashDeviceInfo = CrashDeviceInfo(
        sdkInt = Build.VERSION.SDK_INT,
        model = Build.MODEL ?: "unknown",
        manufacturer = Build.MANUFACTURER ?: "unknown",
        brand = Build.BRAND ?: "unknown",
        fingerprint = Build.FINGERPRINT ?: "unknown"
    )

    fun appInfo(context: Context): CrashAppInfo = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        CrashAppInfo(info.versionName ?: "unknown", info.longVersionCode)
    } catch (_: Exception) {
        CrashAppInfo("unknown", 0L)
    }

    fun pending(context: Context): List<File> = CrashReportStore.list(context)

    fun reportText(context: Context): String = CrashReportStore.readAll(context)

    fun exportZip(context: Context): File? = CrashReportStore.exportZip(context, System.currentTimeMillis())

    fun clear(context: Context) = CrashReportStore.clear(context)
}
