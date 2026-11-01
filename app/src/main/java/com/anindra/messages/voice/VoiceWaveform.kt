package com.anindra.messages.voice

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The bar heights a voice note's bubble draws.
 *
 * The bars are the recording's own loudness, not decoration: each one is the
 * peak amplitude of a slice of the decoded audio, which is what makes a quiet
 * note look quiet and a shout look like a shout. Decoding is done once per
 * message and remembered, because a chat holds many bubbles and re-decoding a
 * note every time it scrolls into view would stall the list.
 */
object VoiceWaveform {

    /** Bars across the bubble, the same order of magnitude Google Messages uses. */
    const val BARS = 32

    private val cache = ConcurrentHashMap<String, FloatArray>()

    /** A cached envelope, or null when it has not been read yet. */
    fun cached(uri: String): FloatArray? = cache[uri]

    /** Reads (or returns the cached) envelope for [uri]. */
    suspend fun peaks(context: Context, uri: String, bars: Int = BARS): FloatArray {
        cache[uri]?.let { return it }
        val decoded = withContext(Dispatchers.IO) { decode(context, uri, bars) }
        val result = decoded ?: placeholder(uri, bars)
        cache[uri] = result
        return result
    }

    /**
     * The fallback envelope for audio this device cannot decode.
     *
     * It is deterministic from the URI so a bubble does not change shape every
     * time it is drawn, and it only appears when the real one is unavailable:
     * an unreadable note still needs a control that looks like a voice note.
     */
    fun placeholder(uri: String, bars: Int = BARS): FloatArray {
        val seed = uri.hashCode()
        return FloatArray(bars) { index ->
            val wave = sin((index + (seed and 0xFF)) * 0.7f).toFloat()
            (0.25f + 0.5f * abs(wave)).coerceIn(MIN_BAR, 1f)
        }
    }

    /**
     * Resamples [raw] into [bars] buckets and scales the result into 0..1.
     *
     * The floor keeps a silent stretch visible: a bar of zero height would read
     * as a gap in the control rather than a quiet moment.
     */
    fun normalize(raw: FloatArray, bars: Int = BARS): FloatArray {
        if (raw.isEmpty()) return placeholder("", bars)
        val out = FloatArray(bars)
        for (i in 0 until bars) {
            val from = i * raw.size / bars
            val to = (((i + 1) * raw.size) / bars).coerceAtLeast(from + 1).coerceAtMost(raw.size)
            var peak = 0f
            for (j in from until to) if (raw[j] > peak) peak = raw[j]
            out[i] = peak
        }
        val loudest = out.maxOrNull() ?: 0f
        if (loudest <= 0f) return placeholder("", bars)
        return FloatArray(bars) { (out[it] / loudest).coerceAtLeast(MIN_BAR).coerceAtMost(1f) }
    }

    private const val MIN_BAR = 0.12f

    private fun decode(context: Context, uriString: String, bars: Int): FloatArray? {
        val uri = Uri.parse(uriString)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context.applicationContext, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    format = candidate
                    break
                }
            }
            if (track < 0 || format == null) return null
            extractor.selectTrack(track)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            if (durationUs <= 0L) return null
            val totalSamples = (durationUs * sampleRate / 1_000_000L).coerceAtLeast(1L)
            val perBar = (totalSamples / bars).coerceAtLeast(1L)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val peaks = FloatArray(bars)
            val info = MediaCodec.BufferInfo()
            var sampleIndex = 0L
            var inputDone = false
            var outputDone = false
            val deadline = System.currentTimeMillis() + DECODE_BUDGET_MS

            while (!outputDone && System.currentTimeMillis() < deadline) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(INPUT_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inputIndex) ?: return null
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex, 0, size, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = decoder.dequeueOutputBuffer(info, INPUT_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_FORMAT_CHANGED,
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val buffer = decoder.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val shorts = buffer.asShortBuffer()
                            while (shorts.hasRemaining()) {
                                val value = abs(shorts.get().toInt())
                                val bar = (sampleIndex / perBar).toInt().coerceIn(0, bars - 1)
                                if (value > peaks[bar]) peaks[bar] = value.toFloat()
                                sampleIndex++
                            }
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            if (peaks.all { it <= 0f }) return null
            normalize(peaks, bars)
        } catch (t: Throwable) {
            Log.w(TAG, "could not read a waveform for $uriString: ${t.message}")
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private const val TAG = "VoiceWaveform"
    private const val INPUT_TIMEOUT_US = 5_000L
    private const val DECODE_BUDGET_MS = 4_000L
}
