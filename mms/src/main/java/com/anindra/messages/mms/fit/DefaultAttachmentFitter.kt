package com.anindra.messages.mms.fit

import android.net.Uri
import com.anindra.messages.mms.pdu.ContentTypes
import com.anindra.messages.mms.spi.AttachmentFitter
import com.anindra.messages.mms.spi.FitOutcome
import com.anindra.messages.mms.spi.FitRequest
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Routes an attachment to the fitter that can handle its type.
 *
 * An image goes through decode-and-re-encode because that is the only way to get
 * both dimensions and byte count under the carrier's limits. Everything else is
 * passed through, bounded by the budget.
 */
class DefaultAttachmentFitter(
    private val images: AttachmentFitter = ImageAttachmentFitter(),
    private val raw: AttachmentFitter = RawAttachmentFitter(),
) : AttachmentFitter {

    override fun fit(request: FitRequest): FitOutcome =
        if (ContentTypes.isImage(request.mimeType)) images.fit(request) else raw.fit(request)
}

/**
 * Reads the whole attachment into memory under a hard cap.
 *
 * The cap is the caller's budget plus a small slack, so an attachment that cannot
 * possibly fit is rejected while reading rather than after a full buffer has been
 * allocated.
 */
object AttachmentReader {
    fun readBounded(input: InputStream, limitBytes: Long): ByteArray? {
        if (limitBytes <= 0) return null
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limitBytes) return null
            out.write(buffer, 0, read)
        }
        return if (out.size() == 0) null else out.toByteArray()
    }

    fun mimeTypeOf(resolver: android.content.ContentResolver, uri: Uri, fallback: String): String =
        resolver.getType(uri)?.takeIf { it.isNotBlank() } ?: fallback
}
