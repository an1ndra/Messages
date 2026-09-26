package com.anindra.messages.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Loads an sms-ie backup from a user-picked document. v2 backups are a ZIP
 * (`messages.ndjson` + a `data/` directory of MMS part files); v1 backups are a
 * bare JSON file, in which case MMS binaries are unavailable.
 */
object SmsIeReader {

    private const val TAG = "SmsIeImport"
    private const val MAX_PART_BYTES = 8 * 1024 * 1024

    data class Loaded(val parsed: SmsIeBackup.Parsed, val zip: Boolean)

    fun load(context: Context, uri: Uri): Loaded? {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: run {
            Log.w(TAG, "cannot open $uri")
            return null
        }

        if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return readZip(bytes)
        }
        val json = bytes.toString(Charsets.UTF_8)
        return runCatching { Loaded(SmsIeBackup.parse(json), zip = false) }
            .onFailure { Log.w(TAG, "unparsable backup: ${it.message}") }
            .getOrNull()
    }

    private fun readZip(bytes: ByteArray): Loaded? {
        var json: String? = null
        val parts = HashMap<String, ByteArray>()
        runCatching {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || name.isEmpty()) continue
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        if (out.size() + read > MAX_PART_BYTES) throw IllegalStateException("part too large")
                        out.write(buffer, 0, read)
                    }
                    val content = out.toByteArray()
                    if (name == "messages.ndjson" || (name.endsWith(".json") && json == null)) {
                        json = content.toString(Charsets.UTF_8)
                    } else {
                        parts[name] = content
                    }
                }
            }
        }.onFailure { Log.w(TAG, "zip read failed: ${it.message}") }

        val text = json ?: return null
        return runCatching {
            Loaded(SmsIeBackup.parse(text) { file -> parts[file.substringAfterLast('/')] }, zip = true)
        }.onFailure { Log.w(TAG, "unparsable zip: ${it.message}") }.getOrNull()
    }
}
