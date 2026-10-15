package com.anindra.messages.mms.transport

import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.spi.MmsTransport
import com.anindra.messages.mms.spi.NotificationAcknowledger
import com.anindra.messages.mms.spi.PlatformTransaction

/**
 * A transport's stable name.
 *
 * The id is deliberately the same string a transport reports, so the choice, the
 * transport and the Diagnostics line cannot name three different things for one
 * path. Only the platform path ships: the app opens no socket of its own, which
 * is what keeps it free of the INTERNET permission.
 */
enum class TransportChoice(val id: String) {
    /** `SmsManager.sendMultimediaMessage`: the platform owns the whole transaction. */
    SYSTEM("system"),
    ;

    companion object {
        /**
         * The platform path, and the only one.
         *
         * The MMSC, the APN and the notification response all stay where the
         * modem expects them, and the app itself opens no socket — which is what
         * keeps it free of the INTERNET permission. A direct MMSC client used to
         * exist here and was removed for exactly that reason.
         */
        val DEFAULT = SYSTEM

        fun of(id: String): TransportChoice? = entries.firstOrNull { it.id == id.trim().lowercase() }
    }
}

/**
 * The transport a subscription will use.
 *
 * A per-subscription pin wins over the global choice. One path ships today, so
 * every subscription resolves to the platform transport.
 */
data class TransportSelection(
    val default: TransportChoice = TransportChoice.DEFAULT,
    val overrides: Map<Int, TransportChoice> = emptyMap(),
) {
    fun choiceFor(subscriptionId: Int): TransportChoice = overrides[subscriptionId] ?: default
}

/** A transport together with the parts of the transaction it owns. */
data class TransportBinding(
    val choice: TransportChoice,
    val transport: MmsTransport,
    /** Null when the transaction does not run through the platform. */
    val platform: PlatformTransaction?,
    /** Null when the platform already answered the notification itself. */
    val acknowledger: NotificationAcknowledger?,
) {
    val reportsThroughPendingIntent: Boolean get() = platform?.reportsThroughPendingIntent == true

    val ownsNotificationRow: Boolean get() = platform?.ownsNotificationRow == true

    fun acknowledge(notification: Pdu, status: Int, subscriptionId: Int): Boolean =
        acknowledger?.acknowledge(notification, status, subscriptionId) == true
}

/**
 * Chooses a transport per subscription, at call time.
 *
 * The map is built once from the transports handed in, and a transport whose id
 * is not a [TransportChoice] is refused here rather than at the first send:
 * registering one that can never be selected would look like a working fallback
 * and quietly never be reached.
 */
class TransportRegistry(
    transports: List<MmsTransport>,
    private val selection: TransportSelection = TransportSelection(),
) {
    private val bindings: Map<TransportChoice, TransportBinding> = transports.associate { transport ->
        val choice = TransportChoice.of(transport.id)
            ?: throw IllegalArgumentException("transport '${transport.id}' is not a TransportChoice id")
        choice to TransportBinding(
            choice = choice,
            transport = transport,
            platform = transport as? PlatformTransaction,
            acknowledger = transport as? NotificationAcknowledger,
        )
    }

    /** The binding for [subscriptionId], or null when nothing is registered for its choice. */
    fun assign(subscriptionId: Int): TransportBinding? =
        bindings[selection.choiceFor(subscriptionId)]
}