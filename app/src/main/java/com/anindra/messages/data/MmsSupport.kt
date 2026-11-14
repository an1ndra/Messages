package com.anindra.messages.data

import java.nio.charset.Charset
import java.util.Locale

object MmsSupport {
    const val TRANSPORT_SMS = "sms"
    const val TRANSPORT_MMS = "mms"
    const val PROVIDER_SELECTION = "(msg_box=1 AND m_type=132) OR (msg_box=2 AND m_type=128)"
    const val MAX_TEXT_BYTES = 1_048_576

    /** The URI a provider part's bytes are read from, and the value an imported
     *  message row stores in `media_uri`. */
    fun partUri(partId: Long): String = "content://mms/part/$partId"

    /**
     * The provider MMS rows whose stored pictures and voice notes already cover
     * everything the provider holds, so the import can skip them without reading
     * their addresses or opening a transaction.
     *
     * A row is complete only when every media part the provider lists has its
     * own message row. An MMS imported before every attachment got a row of its
     * own therefore re-enters the import and is backfilled, without needing a
     * one-shot version gate that every later patch would have to bump (#364).
     */
    fun completeMmsIds(
        stored: Map<Long, Set<String>>,
        mediaPartIds: Map<Long, List<Long>>
    ): Set<Long> = stored.filter { (sysId, uris) ->
        mediaPartIds[sysId].orEmpty().all { partUri(it) in uris }
    }.keys

    /**
     * The provider MMS the import can skip this pass.
     *
     * Every part being stored is not enough: the first row's body can still
     * carry the notice an older import wrote over pictures that now have rows of
     * their own. Whether its count is still true is only visible in the
     * provider's parts, so a row that still makes the claim always goes through
     * the import again — a genuinely unsupported attachment (a video)
     * reproduces the same notice and the row is left alone.
     */
    fun skippableMmsIds(
        stored: Map<Long, Set<String>>,
        mediaPartIds: Map<Long, List<Long>>,
        withNotice: Set<Long>
    ): Set<Long> = completeMmsIds(stored, mediaPartIds) - withNotice

    /**
     * Whether an imported MMS should raise its conversation's unread badge.
     *
     * Only a message the store did not have yet counts. Backfilling a missing
     * picture into an MMS already on screen is not new mail, and this app never
     * writes the provider's read flag — so counting the backfill would make
     * every old unread conversation bump again on upgrade.
     */
    fun countsAsNewUnread(isMe: Boolean, read: Boolean, alreadyStored: Boolean): Boolean =
        !isMe && !read && !alreadyStored

    /** Inbox/outbox PDU types. 130 is an incoming MMS the carrier has only
     *  announced; it has no parts until the default SMS app downloads it. */
    const val PDU_SEND_REQ = 128
    const val PDU_NOTIFICATION_IND = 130
    const val PDU_RETRIEVE_CONF = 132
    const val PENDING_DOWNLOAD_SELECTION = "msg_box=1 AND m_type=$PDU_NOTIFICATION_IND"

    /** The line a message belongs to. A single-SIM provider has no such column,
     *  so a query naming it fails and the caller has to be able to ask again
     *  without it. */
    const val PROVIDER_SUBSCRIPTION_ID = "sub_id"

    fun acceptsTextChunk(currentBytes: Int, nextBytes: Int): Boolean =
        currentBytes in 0..MAX_TEXT_BYTES && nextBytes in 0..(MAX_TEXT_BYTES - currentBytes)

    data class Address(val type: Int, val value: String)
    data class Part(val id: Long, val mime: String, val text: String? = null)
    /**
     * What one MMS carries, as far as this app can show it.
     *
     * [imageId], [extraImageIds] and [videoIds] are the attachment part ids the
     * provider will hand the bytes out under; each one is imported as a message
     * row of its own, so a four-picture MMS shows four pictures instead of one
     * and a note about the rest (#398), and a clip arrives as a clip rather than
     * as that same note (#461). [audioId] is a voice note, shown only when the
     * MMS carries no picture and no clip. [mediaType] is the label the first row
     * is stored under, so the bubble and the notification agree with what was
     * read.
     */
    data class Content(
        val body: String,
        val imageId: Long?,
        val omittedParts: Int,
        /** The second and later pictures of a multi-picture MMS. */
        val extraImageIds: List<Long> = emptyList(),
        /** The clips of this MMS; a clip is never folded into a picture album. */
        val videoIds: List<Long> = emptyList(),
        val audioId: Long? = null,
        val audioMime: String = ""
    ) {
        val mediaType: String
            get() = when {
                imageId != null -> "image"
                videoIds.isNotEmpty() -> "video"
                audioId != null -> "audio"
                else -> "text"
            }

        val mediaId: Long?
            get() = imageId ?: videoIds.firstOrNull() ?: audioId

        /** Every attachment this MMS carries, in the order it should be shown, as
         *  (mediaType, partId). Empty for a text-only MMS. */
        fun mediaRows(): List<Pair<String, Long>> = buildList {
            imageId?.let { add("image" to it) }
            extraImageIds.forEach { add("image" to it) }
            videoIds.forEach { add("video" to it) }
        }
    }
    data class InboundMms(
        val address: String,
        val body: String,
        val timestamp: Long,
        /** The picture of a picture MMS, or null. A picture-only MMS has a blank [body]. */
        val imageUri: String? = null,
        /** The voice note of a voice MMS, or null. */
        val audioUri: String? = null,
        /**
         * The conversation the import filed this message under (#448).
         *
         * A group no longer has one address, so a notifier that looks the chat up
         * by sender would name — and route its channel and open-thread check to —
         * the sender's private chat instead of the group.
         */
        val conversationId: Long = 0
    ) {
        val isAudio: Boolean get() = audioUri != null
    }
    /** [subscriptionId] is the line the announcement was filed under, or -1 when the
     *  provider does not record one. The downloaded message keeps it. */
    data class PendingDownload(val id: Long, val contentLocation: String?, val subscriptionId: Int = -1)

    /**
     * The label for a message this account sent.
     *
     * Anything carrying media goes out as an MMS, whatever it is stored as, so
     * the label follows the message rather than the table it lives in.
     */
    fun outgoingKind(mediaType: String, transport: String): String =
        if (mediaType != "text" || transport == TRANSPORT_MMS) "MMS" else "SMS"

    fun isImportable(box: Int, pduType: Int): Boolean =
        (box == 1 && pduType == PDU_RETRIEVE_CONF) || (box == 2 && pduType == PDU_SEND_REQ)

    /** An announced-but-undownloaded incoming MMS: the platform hands the row
     *  to the default SMS app, which must download it before it can be read. */
    fun isPendingDownload(box: Int, pduType: Int): Boolean =
        box == 1 && pduType == PDU_NOTIFICATION_IND

    fun messageContentUri(id: Long): String = "content://mms/$id"

    const val WAP_PUSH_MIME = "application/vnd.wap.mms-message"

    /** A WAP push is recognized by action and MIME type alone: the platform
     *  has already filed the announced provider row, so nothing else from the
     *  broadcast is needed to fetch it. The PDU rides in the "data" extra and
     *  there is no data URI at all. */
    fun isMmsWapPush(action: String?, type: String?): Boolean =
        action == android.provider.Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION &&
            type == WAP_PUSH_MIME

    /** The Content-Location an announced MMS can be fetched from, or null when
     *  it does not have one — there is nowhere to point the platform's
     *  downloader at, so it cannot be requested at all. */
    fun downloadLocation(contentLocation: String?): String? =
        contentLocation?.takeIf { it.isNotBlank() }

    /** ContentResolver often reports no type for FileProvider URIs; MMS still
     *  needs a concrete content type on the wire. */
    fun defaultAttachmentMime(contentType: String?, uri: String): String {
        val declared = contentType?.substringBefore(';')?.trim()
        if (!declared.isNullOrBlank()) return canonicalMime(declared)
        val lower = uri.lowercase()
        return when {
            lower.contains("video") || VIDEO_EXTENSIONS.any { lower.endsWith(it) } -> "video/mp4"
            lower.contains("audio") || AUDIO_EXTENSIONS.any { lower.endsWith(it) } -> "audio/mp4"
            else -> "image/jpeg"
        }
    }

    /**
     * One declared type for the audio the app can record.
     *
     * The recorder writes an MPEG-4 container with AAC in it, and Android's own
     * MIME table reports that as `audio/mp4a-latm` from some builds and
     * `audio/m4a` from others. A carrier is handed a content type it asks a
     * handset to decode, so the app sends the RFC name rather than whichever
     * alias the local platform happens to prefer.
     */
    fun canonicalMime(raw: String): String {
        val type = mime(raw)
        return if (type in AUDIO_MP4_ALIASES) "audio/mp4" else type
    }

    private val AUDIO_MP4_ALIASES = listOf(
        "audio/mp4a-latm", "audio/m4a", "audio/x-m4a", "audio/aac", "audio/mp4a"
    )

    private val VIDEO_EXTENSIONS = listOf(".mp4", ".3gp", ".mkv", ".webm")
    private val AUDIO_EXTENSIONS = listOf(".m4a", ".mp3", ".amr", ".ogg", ".aac")

    data class OutgoingPart(val name: String, val mimeType: String, val isText: Boolean)
    /** Attachment first, then an optional text part. A SMIL part is prepended
     *  separately because several carriers reject an MMS without one. The
     *  attachment's name is what the SMIL `src` points at, so it follows the
     *  kind of media rather than always reading "image". */
    fun outgoingParts(attachmentMime: String, caption: String): List<OutgoingPart> = buildList {
        add(OutgoingPart(if (isAudio(attachmentMime)) "audio" else "image", attachmentMime, false))
        if (caption.isNotBlank()) add(OutgoingPart("text", "text/plain", true))
    }

    /**
     * Normalises an MMS provider `date` to milliseconds.
     *
     * The column is seconds on most carriers but already milliseconds on
     * others. Multiplying an already-millisecond value by 1000 produced a date
     * far in the future, which sorts that conversation to the top of the list
     * permanently and buries every message sent afterwards (#317).
     */
    fun milliseconds(raw: Long): Long? = when {
        raw < 0 -> null
        // A date already in milliseconds is kept, but only within the range a
        // real date occupies: an absurd value is a unit this cannot vouch for,
        // not a timestamp to store.
        raw >= MILLISECOND_FLOOR -> raw.takeIf { it <= PLAUSIBLE_DATE_CEILING }
        raw <= Long.MAX_VALUE / 1000 -> raw * 1000
        else -> null
    }

    /** Below this a provider `date` can only already be milliseconds. */
    private const val MILLISECOND_FLOOR = 1_000_000_000_000L

    /**
     * Above this (year ~2286 in milliseconds) nothing real is stored, so the
     * value is rejected rather than kept. Guards against an overflowing or
     * already-scaled date reaching the list.
     */
    private const val PLAUSIBLE_DATE_CEILING = 9_999_999_999_999L

    /** The same threshold, for migrating rows that were already written wrong. */
    fun timestampMillisecondsFloor(): Long = MILLISECOND_FLOOR

    fun phoneAddress(raw: String): String? {
        val value = raw.trim().replace(Regex("/TYPE=PLMN$", RegexOption.IGNORE_CASE), "")
        if (!value.matches(Regex("\\+?[0-9 ()\\-.]+"))) return null
        val digits = value.filter { it in '0'..'9' }
        return digits.takeIf { it.length in 3..15 }?.let { if (value.startsWith('+')) "+$it" else it }
    }

    /**
     * Who an MMS is attributed to, or null when it cannot be trusted.
     *
     * A group MMS has several To/From entries, so the old single-participant
     * rule rejected every one of them and group MMS was silently discarded
     * unread. What is actually needed is the *sender*: the one From on an
     * incoming message, or the recipient on one we sent, so the message can be
     * filed against the right conversation and labelled.
     *
     * Types 137 and 151 are the PDU originator and destination;
     * "insert-address-token" is the PDU's own placeholder, not a participant.
     */
    fun peer(box: Int, addresses: List<Address>, threadRecipientCount: Int?): String? {
        if (box !in 1..2) return null
        if (addresses.any { it.type !in setOf(137, 151) }) return null
        val real = addresses.filter { it.value != "insert-address-token" }
        if (real.any { phoneAddress(it.value) == null }) return null
        return when (box) {
            // Incoming: the single sender, even inside a group thread.
            1 -> addresses.filter { it.type == 137 && it.value != "insert-address-token" }
                .singleOrNull()?.value?.let(::phoneAddress)
            // Outgoing: a 1:1 has one recipient; a group send has several and is
            // already filed under its conversation, so the first is enough.
            else -> addresses.filter { it.type == 151 }
                .let { if (threadRecipientCount == 1) it.singleOrNull() else it.firstOrNull() }
                ?.value?.let(::phoneAddress)
        }
    }

    /** Every distinct phone recipient of an outgoing group MMS. */
    fun participants(addresses: List<Address>): List<String> =
        addresses.filter { it.type == 151 }
            .mapNotNull { phoneAddress(it.value) }
            .filter { it.isNotBlank() }
            .distinct()

    fun mime(raw: String): String = raw.substringBefore(';').trim().lowercase(Locale.ROOT)

    fun isImage(raw: String): Boolean = mime(raw) in setOf(
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "image/bmp", "image/heic", "image/heif"
    )

    /** Anything a handset will play: the app records `audio/mp4`, but a
     *  received voice note can arrive as AMR, MP3 or OGG. */
    fun isAudio(raw: String): Boolean = mime(raw).startsWith("audio/")

    /** The MIME the app labels a clip with when the provider does not say. */
    const val DEFAULT_VIDEO_MIME = "video/mp4"

    fun isVideo(raw: String): Boolean = mime(raw) in setOf(
        "video/mp4", "video/3gpp", "video/3gpp2", "video/webm", "video/x-matroska", "video/quicktime"
    )

    /** The message `media_type` for an attachment of this type: pictures get the
     *  album layout, a clip its own bubble, a voice note its waveform, and
     *  everything else is a plain file. */
    fun mediaKind(rawMime: String): String = when {
        isImage(rawMime) -> "image"
        isVideo(rawMime) -> "video"
        isAudio(rawMime) -> "audio"
        else -> "file"
    }

    /** The sentence [content] writes when it could not show every attachment.
     *  Only the provider's parts can say whether it is still true. */
    private const val NOTICE_MARKER = "additional/unsupported attachment(s) not displayed"

    fun carriesNotice(body: String): Boolean = body.contains(NOTICE_MARKER)

    fun content(parts: List<Part>): Content {
        // Every attachment the provider can hand out gets a message row of its
        // own, so a four-picture MMS arrives as four pictures rather than one
        // picture and a note about the others (#398), and a clip arrives as a
        // clip (#461). A voice note is shown when there is neither; anything the
        // app cannot show at all — a document — is reported rather than dropped
        // silently.
        val images = parts.filter { it.id > 0 && isImage(it.mime) }
        val audio = parts.firstOrNull { it.id > 0 && isAudio(it.mime) }
            ?.takeIf { images.isEmpty() }
        val videos = parts.filter { it.id > 0 && isVideo(it.mime) }
        val text = parts.filter { mime(it.mime) == "text/plain" }.mapNotNull { it.text }
            .filter { it.isNotBlank() }.joinToString("\n")
        val shownIds = (images + videos).map { it.id }.toSet()
        val omitted = parts.count {
            mime(it.mime) !in setOf("text/plain", "application/smil") &&
                it.id !in shownIds && it !== audio
        }
        val notice = if (omitted > 0) "[MMS: $omitted $NOTICE_MARKER]" else ""
        return Content(
            body = listOf(text, notice).filter { it.isNotEmpty() }.joinToString("\n"),
            imageId = images.firstOrNull()?.id,
            omittedParts = omitted,
            extraImageIds = images.drop(1).map { it.id },
            videoIds = videos.map { it.id },
            audioId = audio?.id,
            audioMime = audio?.let { mime(it.mime) } ?: ""
        )
    }

    /**
     * The body an already-imported MMS row should carry after a re-scan.
     *
     * Rows written before #398 stored "[MMS: N additional/unsupported
     * attachment(s) not displayed]" beside the first picture, and every picture
     * has a row of its own now, so the sentence contradicts the screen. The
     * freshly computed body is authoritative — including an empty one, which is
     * what a picture-only MMS has — except when the provider row yields nothing
     * at all (its parts were deleted after import): then the stored text is the
     * only copy left.
     */
    fun refreshedBody(stored: String, fresh: String, providerEmpty: Boolean): String =
        if (providerEmpty) stored else fresh

    fun decodeText(bytes: ByteArray, charset: Int): String {        val name = when (charset) {
            0, 106 -> "UTF-8"
            3 -> "US-ASCII"
            4 -> "ISO-8859-1"
            17 -> "Shift_JIS"
            1000 -> "UTF-16BE"
            1013 -> "UTF-16BE"
            1014 -> "UTF-16LE"
            1015 -> "UTF-16"
            2026 -> "Big5"
            else -> throw IllegalArgumentException("Unsupported MMS text charset")
        }
        return String(bytes, Charset.forName(name))
    }

    fun providerUri(transport: String): String? = when (transport) {
        TRANSPORT_SMS -> "content://sms"
        TRANSPORT_MMS -> "content://mms"
        else -> null
    }

    /** Where a saved attachment belongs. One decision, read by the menu that
     *  offers the save and by the store that performs it, so a voice note can
     *  never be labelled "Save image" and written into the gallery. */
    enum class SaveTarget { IMAGE, VIDEO, AUDIO, FILE }

    fun saveTargetFor(mediaType: String): SaveTarget = when (mediaType) {
        "image" -> SaveTarget.IMAGE
        "video" -> SaveTarget.VIDEO
        "audio" -> SaveTarget.AUDIO
        else -> SaveTarget.FILE
    }

    private val EXTENSIONS = mapOf(
        "image/jpeg" to "jpg", "image/jpg" to "jpg", "image/png" to "png",
        "image/gif" to "gif", "image/webp" to "webp", "image/bmp" to "bmp",
        "image/heic" to "heic", "image/heif" to "heif", "video/mp4" to "mp4",
        "video/3gpp" to "3gp", "video/3gpp2" to "3g2", "video/webm" to "webm",
        "audio/mp4" to "m4a", "audio/m4a" to "m4a", "audio/x-m4a" to "m4a",
        "audio/mp4a-latm" to "m4a", "audio/aac" to "aac", "audio/3gpp" to "3gp",
        "audio/amr" to "amr", "audio/mpeg" to "mp3", "audio/ogg" to "ogg",
        "audio/wav" to "wav", "audio/x-wav" to "wav"
    )

    /** The fallback a saved attachment gets when neither the resolver nor the
     *  URI names a usable type: pictures when saving a picture, audio when
     *  saving a voice note. */
    private fun fallbackMime(target: SaveTarget): String = when (target) {
        SaveTarget.IMAGE -> "image/jpeg"
        SaveTarget.VIDEO -> DEFAULT_VIDEO_MIME
        SaveTarget.AUDIO -> "audio/mp4"
        SaveTarget.FILE -> "application/octet-stream"
    }

    /** Fallback mime for a saved attachment when the resolver reports none. */
    fun mimeForSavedAttachment(
        uri: String,
        resolved: String?,
        target: SaveTarget = SaveTarget.IMAGE
    ): String {
        if (!resolved.isNullOrBlank()) return resolved.substringBefore(';').trim()
        val lower = uri.lowercase()
        return EXTENSIONS.entries.firstOrNull { lower.endsWith(".${it.value}") }?.key
            ?: fallbackMime(target)
    }

    /** Filename for a saved attachment: "<contact>_<timestamp>.<ext>". Spaces are
     *  kept (the gallery shows them); only path-unsafe characters are dropped. */
    fun savedAttachmentName(
        contactName: String,
        timestamp: Long,
        mime: String,
        target: SaveTarget = SaveTarget.IMAGE
    ): String {
        val safe = contactName.trim()
            .replace(Regex("[/\\\\:*?\"<>|\\x00-\\x1F]"), "_")
            .trim('_', ' ')
            .take(32)
            .ifBlank { "message" }
        val ext = EXTENSIONS[mime.substringBefore(';').trim()]
            ?: EXTENSIONS[fallbackMime(target)] ?: "bin"
        return "${safe}_$timestamp.$ext"
    }
}
