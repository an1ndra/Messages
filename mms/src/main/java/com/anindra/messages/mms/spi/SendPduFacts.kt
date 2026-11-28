package com.anindra.messages.mms.spi

import com.anindra.messages.mms.pdu.HeaderField
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.pdu.PduComposer

/** One part of a submission, as the carrier will see it. */
data class SendPduPart(
    val contentType: String,
    val name: String?,
    val contentId: String?,
    val contentLocation: String?,
    val bytes: Int,
)

/**
 * The submission as it goes on the wire.
 *
 * Reported beside the size because a carrier refusal cannot be diagnosed from
 * the result code alone: "the MMSC said 500" is the same line whether the sender
 * was the wrong number, a part lost its identifier, or an extra header changed
 * the submission. Every field here is one the composer decides, read back
 * through the composer's own helpers rather than re-derived, so the report
 * cannot describe a PDU that was never sent.
 */
data class SendPduFacts(
    val pduSize: Int,
    val from: String,
    val recipients: List<String>,
    val parts: List<SendPduPart>,
    /** Field codes in wire order, X-Mms-Content-Type last. */
    val headerCodes: List<Int>,
    val declaredMessageSize: Long?,
) {

    /**
     * The same facts as the key/value pairs Diagnostics prints.
     *
     * Both readers of this (the recorder behind the report and the logcat
     * mirror) format from here, so the line in logcat and the line in a bug
     * report cannot say different things.
     */
    fun fields(): Map<String, String> = linkedMapOf(
        "size" to pduSize.toString(),
        "from" to from,
        "to" to (recipients.joinToString(",").ifEmpty { "-" }),
        "msgSize" to (declaredMessageSize?.toString() ?: "-"),
        "parts" to (parts.joinToString(";") { part ->
            "${part.name ?: "-"}/${part.contentType}" +
                "/cid=${part.contentId ?: "-"}/cl=${part.contentLocation ?: "-"}/${part.bytes}B"
        }.ifEmpty { "-" }),
        "headers" to headerCodes.joinToString(".") { "%02x".format(it) },
    )

    companion object {
        /** The marker the wire carries instead of a sender, as text. */
        const val INSERT_ADDRESS_TOKEN = "insert-address-token"

        fun of(pdu: Pdu, pduSize: Int): SendPduFacts = SendPduFacts(
            pduSize = pduSize,
            from = PduComposer.fromAddress(pdu)?.text ?: INSERT_ADDRESS_TOKEN,
            recipients = pdu.to.map { PduComposer.recipientValue(pdu, it).text },
            parts = pdu.body?.parts().orEmpty().map { part ->
                SendPduPart(
                    contentType = part.contentType.orEmpty(),
                    name = part.name,
                    contentId = part.contentIdOrNull(),
                    contentLocation = part.contentLocation,
                    bytes = part.data?.size ?: 0,
                )
            },
            headerCodes = PduComposer.headerOrder(pdu) +
                listOfNotNull(
                    HeaderField.CONTENT_TYPE.takeIf { pdu.headers.contentTypeOrNull() != null },
                ),
            declaredMessageSize = pdu.messageSize,
        )
    }
}
