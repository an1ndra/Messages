package com.anindra.messages.data

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore

/** Resolves and plays the configured incoming/outgoing message sounds. */
object SoundStore {

    data class Entry(val key: String, val title: String, val uri: Uri?)

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
        key.startsWith("system:") -> runCatching {
            val uri = Uri.parse(key.removePrefix("system:"))
            RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: ""
        }.getOrNull().orEmpty().ifEmpty { "System sound" }
        else -> fallback
    }

    fun play(context: Context, key: String): Boolean {
        if (key == SettingsStore.SOUND_SILENT || key == SettingsStore.SOUND_DEFAULT) return false
        val uri = when {
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
