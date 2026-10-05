package com.anindra.messages.data

/**
 * What a forward should hand to the SMS framework for a given source message.
 * Text stays an SMS; an image message is re-sent as MMS carrying its own
 * attachment, not as an SMS with an (often empty) caption.
 */
sealed interface ForwardPlan {

    data class Sms(val body: String) : ForwardPlan

    data class Mms(val mediaUri: String, val caption: String) : ForwardPlan

    companion object {
        private const val MEDIA_IMAGE = "image"

        /** [body] has already had links hidden if the "hide links" setting is on. */
        fun of(msg: Message, body: String): ForwardPlan =
            if (msg.mediaType == MEDIA_IMAGE && msg.mediaUri.isNotBlank()) {
                Mms(msg.mediaUri, body)
            } else {
                Sms(body)
            }
    }
}
