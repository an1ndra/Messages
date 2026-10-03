package com.anindra.messages.mms.spi

import com.anindra.messages.mms.pdu.Pdu

/**
 * Turns a PDU into the octets the radio expects, and back.
 *
 * Separate from [MmsTransport] on purpose: the codec is pure and total, while the
 * transport owns the network. Keeping them apart is what lets a PDU built on one
 * path be verified byte-for-byte without a network, and lets the transport be
 * swapped without touching the codec.
 */
interface MmsPduCodec {
    /** Null when the PDU cannot be represented on the wire. */
    fun compose(pdu: Pdu): ByteArray?

    /** Null for malformed or unsupported input; never throws. */
    fun parse(bytes: ByteArray): Pdu?
}
