package com.anindra.messages.mms.smil

import com.anindra.messages.mms.pdu.ContentTypes
import com.anindra.messages.mms.pdu.PduBody
import com.anindra.messages.mms.pdu.PduPart

/**
 * Builds the SMIL part carriers require. Sending without one is common and renders
 * as nothing at all on many handsets, so the layout is generated from the body
 * rather than left to the sender.
 */
object SmilBuilder {

    /**
     * A slideshow for [body], or null when there is nothing to lay out — the SMIL
     * part itself is never a layout item, so a body holding only one would
     * otherwise serialize into an empty `<body>`.
     */
    fun build(body: PduBody): SmilDocument? {
        val pars = mutableListOf<SmilPar>()
        var current = mutableListOf<SmilItem>()
        var hasText = false
        var hasMedia = false

        fun flush() {
            if (current.isNotEmpty()) {
                pars.add(SmilPar(current))
                current = mutableListOf()
                hasText = false
                hasMedia = false
            }
        }

        for (part in body.parts()) {
            if (part.isSmil) continue
            val item = itemFor(part) ?: continue
            // Media and text share a par until it holds both, then the next item
            // starts a new one. That is what makes a caption appear with its
            // image rather than as a following slide, and it is the grouping the
            // AOSP helper this replaces produces.
            if (hasText && hasMedia) flush()
            current.add(item)
            if (item is SmilText) hasText = true else hasMedia = true
        }
        flush()

        if (pars.isEmpty()) return null
        return SmilDocument(pars)
    }

    private fun itemFor(part: PduPart): SmilItem? {
        val contentType = part.contentType ?: return null
        return when {
            ContentTypes.isText(contentType) -> SmilText(srcFor(part))
            ContentTypes.isImage(contentType) -> SmilImage(srcFor(part))
            ContentTypes.isVideo(contentType) -> SmilVideo(srcFor(part))
            ContentTypes.isAudio(contentType) -> SmilAudio(srcFor(part))
            else -> null
        }
    }

    /**
     * The `src` has to match what the part is written under, or the handset
     * cannot resolve it. Name wins, then content-location, then a `cid:` URI
     * built from the content-id — the same order the AOSP helper uses, and the
     * only one that resolves for every carrier.
     */
    private fun srcFor(part: PduPart): String {
        part.name?.takeIf { it.isNotBlank() }?.let { return it }
        part.contentLocation?.takeIf { it.isNotBlank() }?.let { return it.substringAfterLast('/') }
        part.contentId?.takeIf { it.isNotBlank() }?.let { return CID_PREFIX + it.trim('<', '>') }
        return FALLBACK_SRC
    }

    private const val CID_PREFIX = "cid:"
    private const val FALLBACK_SRC = "attachment"
}
