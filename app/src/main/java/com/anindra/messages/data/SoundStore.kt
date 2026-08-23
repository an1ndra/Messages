package com.anindra.messages.data

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

/** Resolves and plays the configured incoming/outgoing message sounds. */
object SoundStore {

    data class Entry(val key: String, val title: String, val uri: Uri?)

    fun customSoundsDir(context: Context): File =
        File(context.filesDir, "custom_sounds").apply { mkdirs() }

    fun listSystemSounds(context: Context): List<Entry> {
        val out = mutableListOf<Entry>()
        val manager = RingtoneManager(context).apply {
            setType(RingtoneManager.TYPE_NOTIFICATION)
        }
        val cursor = manager.cursor
        cursor.use {
            val titleIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            while (it.moveToNext()) {
                out.add(Entry("system:${manager.getRingtoneUri(it.position)}", it.getString(titleIdx) ?: "Unknown", manager.getRingtoneUri(it.position)))
            }
        }
        return out
    }

    fun entryTitle(context: Context, key: String, fallback: String): String = when {
        key == SettingsStore.SOUND_DEFAULT -> fallback
        key == SettingsStore.SOUND_SILENT -> "Silent"
        key.startsWith("custom:") ->
            File(key.removePrefix("custom:")).nameWithoutExtension
        key.startsWith("system:") -> runCatching {
            val uri = Uri.parse(key.removePrefix("system:"))
            RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: ""
        }.getOrNull().orEmpty().ifEmpty { "System sound" }
        else -> fallback
    }

    fun importCustom(context: Context, uri: Uri): String? {
        return try {
            val resolver = context.contentResolver
            var name = "sound"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
            val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val dest = File(customSoundsDir(context), "${System.currentTimeMillis()}_$safeName")
            val stream = resolver.openInputStream(uri) ?: return null
            stream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            "custom:${dest.absolutePath}"
        } catch (_: Exception) {
            null
        }
    }

    fun play(context: Context, key: String): Boolean {
        if (key == SettingsStore.SOUND_SILENT || key == SettingsStore.SOUND_DEFAULT) return false
        val uri = when {
            key.startsWith("custom:") -> Uri.fromFile(File(key.removePrefix("custom:")))
            key.startsWith("system:") -> Uri.parse(key.removePrefix("system:"))
            else -> return false
        }
        return try {
            MediaPlayer.create(context, uri)?.let { player ->
                player.setOnCompletionListener { it.release() }
                player.start()
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
    }
}
