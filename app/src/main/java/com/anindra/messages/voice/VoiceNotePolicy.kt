package com.anindra.messages.voice

/**
 * How long a voice note may be, and how big it will be when it is not.
 *
 * A voice note leaves as an MMS attachment, so the carrier's message-size cap —
 * not the clock — is what decides where recording stops. Both the recorder and
 * the Diagnostics sheet read this one object, so the cap the app reports is the
 * cap it enforces.
 */
object VoiceNotePolicy {

    /** What the recorder writes and what the PDU declares. */
    const val MIME = "audio/mp4"

    /** AAC-LC mono at 32 kbps: speech is intelligible and a minute stays small. */
    const val BITRATE = 32_000
    const val SAMPLE_RATE = 44_100

    /** Container and per-track headers the stream size does not account for. */
    private const val CONTAINER_BYTES_PER_SECOND = 1_024

    const val MIN_SECONDS = 1
    const val MAX_SECONDS = 120

    fun bytesPerSecond(): Int = BITRATE / 8 + CONTAINER_BYTES_PER_SECOND

    /**
     * The longest recording that fits [carrierBudgetBytes].
     *
     * An unknown or nonsensical budget falls back to [MAX_SECONDS]: refusing to
     * record because a carrier profile was unreadable would be worse than
     * letting the send fail with a notice.
     */
    fun maxSeconds(carrierBudgetBytes: Int): Int {
        if (carrierBudgetBytes <= 0) return MAX_SECONDS
        return (carrierBudgetBytes / bytesPerSecond()).coerceIn(MIN_SECONDS, MAX_SECONDS)
    }

    fun estimatedBytes(seconds: Int): Int = seconds.coerceAtLeast(0) * bytesPerSecond()

    /** `m:ss`, the way a player labels a short clip. */
    fun label(millis: Long): String {
        val seconds = (millis / 1000).coerceAtLeast(0)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    /**
     * `mm:ss`, the way the recording bar counts up.
     *
     * Google Messages' own bar reads "00:03", not "0:03": a counter that holds
     * its width does not make the row jump every ten seconds.
     */
    fun elapsedLabel(millis: Long): String {
        val seconds = (millis / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}
