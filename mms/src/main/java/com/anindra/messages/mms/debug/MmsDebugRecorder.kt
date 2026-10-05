package com.anindra.messages.mms.debug

import com.anindra.messages.mms.spi.MmsDiagnostics

/**
 * An in-memory recorder that keeps the last [capacity] MMS events.
 *
 * On a phone this is the fastest way to answer "what just happened?" without
 * taking a screenshot: Diagnostics reads the snapshot and prints it as text.
 */
class MmsDebugRecorder(private val capacity: Int = DEFAULT_CAPACITY) : MmsDiagnostics {

    private val events = ArrayDeque<MmsEvent>(capacity)
    private val lock = Any()

    fun snapshot(): List<MmsEvent> = synchronized(lock) { events.toList() }

    fun last(): MmsEvent? = synchronized(lock) { events.lastOrNull() }

    override fun sendStarted(transportId: String, subscriptionId: Int, messageUri: String?) {
        record(
            MmsEvent(
                category = "send",
                name = "started",
                subscriptionId = subscriptionId,
                details = mapOf(
                    "transport" to transportId,
                    "uri" to (messageUri ?: "-"),
                ),
            ),
        )
    }

    override fun sendBuilt(pduSize: Int, recipientCount: Int) {
        record(
            MmsEvent(
                category = "send",
                name = "built",
                details = mapOf(
                    "pduSize" to pduSize.toString(),
                    "recipients" to recipientCount.toString(),
                ),
            ),
        )
    }

    override fun sendCompleted(
        transportId: String,
        outcome: String,
        responseStatus: Int?,
        httpStatus: Int,
    ) {
        record(
            MmsEvent(
                category = "send",
                name = "completed",
                details = mapOf(
                    "transport" to transportId,
                    "outcome" to outcome,
                    "responseStatus" to (responseStatus?.toString() ?: "-"),
                    "httpStatus" to httpStatus.toString(),
                ),
            ),
        )
    }

    override fun receiveStarted(messageType: String, subscriptionId: Int) {
        record(
            MmsEvent(
                category = "receive",
                name = "started",
                subscriptionId = subscriptionId,
                details = mapOf("messageType" to messageType),
            ),
        )
    }

    override fun receiveCompleted(stage: String, messageUri: String?) {
        record(
            MmsEvent(
                category = "receive",
                name = "completed",
                details = mapOf(
                    "stage" to stage,
                    "uri" to (messageUri ?: "-"),
                ),
            ),
        )
    }

    override fun transportSelected(transportId: String, subscriptionId: Int, available: Boolean) {
        record(
            MmsEvent(
                category = "transport",
                name = "selected",
                subscriptionId = subscriptionId,
                details = mapOf(
                    "transport" to transportId,
                    "available" to available.toString(),
                ),
            ),
        )
    }

    override fun apnResolved(subscriptionId: Int, mmsc: String?, proxy: String?) {
        record(
            MmsEvent(
                category = "apn",
                name = "resolved",
                subscriptionId = subscriptionId,
                details = mapOf(
                    "mmsc" to (mmsc ?: "-"),
                    "proxy" to (proxy ?: "-"),
                ),
            ),
        )
    }

    override fun networkResolved(available: Boolean) {
        record(
            MmsEvent(
                category = "network",
                name = "resolved",
                details = mapOf("available" to available.toString()),
            ),
        )
    }

    private fun record(event: MmsEvent) = synchronized(lock) {
        if (events.size >= capacity) events.removeFirst()
        events.addLast(event)
    }

    data class MmsEvent(
        val category: String,
        val name: String,
        val subscriptionId: Int = -1,
        val details: Map<String, String> = emptyMap(),
    ) {
        override fun toString(): String = buildString {
            append("[").append(category).append("]").append(name)
            if (subscriptionId != -1) append(" sub=").append(subscriptionId)
            if (details.isNotEmpty()) {
                append(" ")
                details.entries.joinTo(this, " ") { "${it.key}=${it.value}" }
            }
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 32
    }
}
