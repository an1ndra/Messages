package com.anindra.messages.voice

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Records one voice note into the app's cache and hands back a URI the send path
 * can read.
 *
 * The output is an MPEG-4 container with AAC-LC mono in it. That is what Google
 * Messages-class handsets decode without asking, and it is small enough at
 * [VoiceNotePolicy.BITRATE] that a minute of speech sits well inside a carrier's
 * default 300 KB MMS cap.
 */
class VoiceRecorder(private val context: Context) {

    data class Note(val uri: Uri, val bytes: Long, val durationMs: Long)

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    /** @return the URI to play/send from, or null when the microphone could not
     *  be opened (no device, SELinux denial, or the emulator's audio disabled). */
    fun start(): Uri? {
        cancel()
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val output = File(dir, "voice-${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioChannels(1)
            mediaRecorder.setAudioSamplingRate(VoiceNotePolicy.SAMPLE_RATE)
            mediaRecorder.setAudioEncodingBitRate(VoiceNotePolicy.BITRATE)
            mediaRecorder.setOutputFile(output.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            file = output
            startedAt = SystemClock.elapsedRealtime()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
        } catch (t: Throwable) {
            Log.w(TAG, "could not start recording: ${t.message}")
            runCatching { mediaRecorder.release() }
            output.delete()
            recorder = null
            file = null
            null
        }
    }

    /** Stops and returns the recording, or null when it produced no bytes. */
    fun finish(): Note? {
        val mediaRecorder = recorder ?: return null
        val output = file
        val durationMs = SystemClock.elapsedRealtime() - startedAt
        recorder = null
        file = null
        val stopped = try {
            mediaRecorder.stop()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "recording produced no usable file: ${t.message}")
            false
        } finally {
            runCatching { mediaRecorder.release() }
        }
        if (!stopped || output == null || !output.exists() || output.length() <= 0L) {
            output?.delete()
            return null
        }
        return Note(
            uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output),
            bytes = output.length(),
            durationMs = durationMs
        )
    }

    /** Discards an in-flight recording (a slide-to-cancel, or leaving the chat). */
    fun cancel() {
        recorder?.let { mediaRecorder ->
            runCatching { mediaRecorder.stop() }
            runCatching { mediaRecorder.release() }
        }
        recorder = null
        file?.delete()
        file = null
    }

    private companion object {
        const val TAG = "VoiceRecorder"
        const val DIR = "voice"
    }
}
