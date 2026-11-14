package com.anindra.messages.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays one voice note at a time, app-wide.
 *
 * A chat can hold dozens of voice bubbles, and two of them playing over each
 * other is never what a tap meant. One player and one state stream also means a
 * bubble only needs to know its own URI to render correctly: everything else —
 * position, whether the audio is running — belongs to whichever note is loaded.
 *
 * The player is released when the chat closes, so a paused note does not keep an
 * audio track (and its focus) alive behind the inbox.
 */
object VoicePlayback {

    data class State(
        val uri: String = "",
        val playing: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val durations = ConcurrentHashMap<String, Int>()

    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private var focus: AudioFocusRequest? = null

    /** Play, pause or switch to [uri]. */
    fun toggle(context: Context, uri: String) {
        val app = context.applicationContext
        val current = player
        if (current != null && _state.value.uri == uri) {
            if (current.isPlaying) {
                current.pause()
                _state.value = _state.value.copy(playing = false, positionMs = current.currentPosition)
                stopTicker()
            } else if (requestFocus(app)) {
                current.start()
                _state.value = _state.value.copy(playing = true)
                startTicker(current)
            }
            return
        }
        release()
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        _state.value = State(uri = uri)
        try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mediaPlayer.setDataSource(app, Uri.parse(uri))
            mediaPlayer.setOnPreparedListener { prepared ->
                durations[uri] = prepared.duration
                _state.value = State(
                    uri = uri,
                    playing = false,
                    positionMs = 0,
                    durationMs = prepared.duration.coerceAtLeast(0)
                )
                if (requestFocus(app)) {
                    prepared.start()
                    _state.value = _state.value.copy(playing = true)
                    startTicker(prepared)
                }
            }
            mediaPlayer.setOnCompletionListener { finished ->
                _state.value = _state.value.copy(
                    playing = false,
                    positionMs = finished.duration.coerceAtLeast(0)
                )
                stopTicker()
            }
            mediaPlayer.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "playback failed what=$what extra=$extra")
                release()
                true
            }
            mediaPlayer.prepareAsync()
        } catch (t: Throwable) {
            Log.w(TAG, "could not open $uri: ${t.message}")
            release()
        }
    }

    /** Stops and releases the player without changing what the UI believes. */
    fun release() {
        stopTicker()
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        abandonFocus()
        _state.value = _state.value.copy(playing = false, positionMs = 0)
    }

    /** A duration already measured for [uri], or 0. */
    fun cachedDuration(uri: String): Int = durations[uri] ?: 0

    /**
     * Reads a note's length without playing it, so its bubble can show `0:07`
     * before it is ever tapped. Kept off the main thread; the result is cached.
     */
    suspend fun measureDuration(context: Context, uri: String): Int {
        durations[uri]?.let { return it }
        val measured = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context.applicationContext, Uri.parse(uri))
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.toInt()
            } catch (t: Throwable) {
                Log.w(TAG, "could not read the length of $uri: ${t.message}")
                null
            } finally {
                runCatching { retriever.release() }
            }
        } ?: return 0
        durations[uri] = measured
        return measured
    }

    private fun startTicker(mediaPlayer: MediaPlayer) {
        stopTicker()
        ticker = scope.launch {
            while (true) {
                delay(TICK_MS)
                if (!_state.value.playing) break
                val position = runCatching { mediaPlayer.currentPosition }.getOrDefault(0)
                _state.value = _state.value.copy(positionMs = position)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun requestFocus(context: Context): Boolean {
        appContext = context.applicationContext
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        val request = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    player?.let { if (it.isPlaying) it.pause() }
                    _state.value = _state.value.copy(playing = false)
                    stopTicker()
                }
            }
            .build()
            .also { focus = it }
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val request = focus ?: return
        val manager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        manager?.abandonAudioFocusRequest(request)
        focus = null
    }

    /** Only used to hand audio focus back; an application context cannot leak. */
    private var appContext: Context? = null

    private const val TAG = "VoicePlayback"
    private const val TICK_MS = 100L
}
