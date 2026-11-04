package com.anindra.messages.data

/**
 * What a forward should hand to the SMS framework for a given source message.
 * Text stays an SMS; a message carrying an attachment — a picture or a voice
 * note — is re-sent as MMS carrying that attachment, not as an SMS with an
 * (often empty) caption.
 */
sealed interface ForwardPlan {

    data class Sms(val body: String) : ForwardPlan

    data class Mms(
        val mediaUri: String,
        val caption: String,
        /** The source message's `media_type`, so a voice note stays a voice note
         *  and is not re-sent as a picture. */
        val mediaType: String = MEDIA_IMAGE
    ) : ForwardPlan

    companion object {
        private const val MEDIA_IMAGE = "image"
        private const val MEDIA_AUDIO = "audio"

        /** The kinds this app both stores and can send back out. */
        private val FORWARDABLE = setOf(MEDIA_IMAGE, MEDIA_AUDIO)

        /** [body] has already had links hidden if the "hide links" setting is on. */
        fun of(msg: Message, body: String): ForwardPlan =
            if (msg.mediaType in FORWARDABLE && msg.mediaUri.isNotBlank()) {
                Mms(msg.mediaUri, body, msg.mediaType)
            } else {
                Sms(body)
            }
    }
}
