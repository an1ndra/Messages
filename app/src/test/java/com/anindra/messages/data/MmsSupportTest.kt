package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsSupportTest {
    @Test
    fun importsRetrievedInboxAndSentMessagesOnly() {
        assertTrue(MmsSupport.isImportable(1, 132))
        assertTrue(MmsSupport.isImportable(2, 128))
        assertFalse(MmsSupport.isImportable(1, 130))
        assertFalse(MmsSupport.isImportable(3, 128))
        assertFalse(MmsSupport.isImportable(4, 128))
    }

    @Test
    fun detectsAnnouncedButUndownloadedInboxMms() {
        assertTrue(MmsSupport.isPendingDownload(1, MmsSupport.PDU_NOTIFICATION_IND))
        assertFalse(MmsSupport.isPendingDownload(1, MmsSupport.PDU_RETRIEVE_CONF))
        assertFalse(MmsSupport.isPendingDownload(1, MmsSupport.PDU_SEND_REQ))
        assertFalse(MmsSupport.isPendingDownload(2, MmsSupport.PDU_NOTIFICATION_IND))
        assertFalse(MmsSupport.isPendingDownload(3, MmsSupport.PDU_NOTIFICATION_IND))
        assertEquals("msg_box=1 AND m_type=130", MmsSupport.PENDING_DOWNLOAD_SELECTION)
        assertEquals("content://mms/42", MmsSupport.messageContentUri(42))
    }

    @Test
    fun resolvesAttachmentMimeWhenTheProviderIsSilent() {
        assertEquals("image/png", MmsSupport.defaultAttachmentMime("image/png", "content://x/a"))
        assertEquals("image/jpeg", MmsSupport.defaultAttachmentMime("image/jpeg; charset=binary", "content://x/a"))
        assertEquals("video/mp4", MmsSupport.defaultAttachmentMime(null, "content://x/clip.MP4"))
        assertEquals("audio/mp4", MmsSupport.defaultAttachmentMime("  ", "content://x/clip.m4a"))
        assertEquals("image/jpeg", MmsSupport.defaultAttachmentMime(null, "content://x/photo"))
    }

    @Test
    fun buildsAttachmentPlusOptionalTextPart() {
        val withCaption = MmsSupport.outgoingParts("image/png", "hello")
        assertEquals(2, withCaption.size)
        assertEquals("image", withCaption[0].name)
        assertEquals("image/png", withCaption[0].mimeType)
        assertFalse(withCaption[0].isText)
        assertEquals("text/plain", withCaption[1].mimeType)
        assertTrue(withCaption[1].isText)
        assertEquals(1, MmsSupport.outgoingParts("image/png", "  ").size)
    }

    @Test
    fun namesSavedAttachmentsSafely() {
        assertEquals(
            "Marek Nowak_1700000000000.jpg",
            MmsSupport.savedAttachmentName("Marek Nowak", 1_700_000_000_000L, "image/jpeg")
        )
        assertEquals(
            "message_1700000000000.png",
            MmsSupport.savedAttachmentName("   ", 1_700_000_000_000L, "image/png")
        )
        assertEquals(
            "a_b_1700000000000.jpg",
            MmsSupport.savedAttachmentName("a/b", 1_700_000_000_000L, "image/jpeg")
        )
        assertEquals(
            "Sara_1700000000000.jpg",
            MmsSupport.savedAttachmentName("Sara", 1_700_000_000_000L, "application/octet-stream")
        )
    }

    @Test
    fun resolvesSavedAttachmentMimeByExtension() {
        assertEquals("image/png", MmsSupport.mimeForSavedAttachment("content://mms/part/9", "image/png"))
        assertEquals("image/png", MmsSupport.mimeForSavedAttachment("content://x/a.PNG", null))
        assertEquals("video/mp4", MmsSupport.mimeForSavedAttachment("content://x/clip.mp4", null))
        assertEquals("image/jpeg", MmsSupport.mimeForSavedAttachment("content://mms/part/9", null))
    }

    @Test
    fun aVoiceNoteKeepsItsOwnNameAndType() {
        // #364: a saved voice note was named ".jpg" because the extension table
        // only knew pictures.
        assertEquals(
            "Sara_1700000000000.m4a",
            MmsSupport.savedAttachmentName(
                "Sara", 1_700_000_000_000L, "audio/mp4", MmsSupport.SaveTarget.AUDIO
            )
        )
        assertEquals(
            "audio/mp4",
            MmsSupport.mimeForSavedAttachment(
                "content://x/voice-1700.m4a", null, MmsSupport.SaveTarget.AUDIO
            )
        )
        assertEquals(
            "an audio save with no name still must not claim to be a picture",
            "audio/mp4",
            MmsSupport.mimeForSavedAttachment(
                "content://x/voice-1700", null, MmsSupport.SaveTarget.AUDIO
            )
        )
    }

    @Test
    fun theSaveTargetFollowsTheMessageNotTheMenu() {
        assertEquals(MmsSupport.SaveTarget.AUDIO, MmsSupport.saveTargetFor("audio"))
        assertEquals(MmsSupport.SaveTarget.IMAGE, MmsSupport.saveTargetFor("image"))
        assertEquals(MmsSupport.SaveTarget.FILE, MmsSupport.saveTargetFor("text"))
    }

    @Test
    fun convertsSecondsWithoutOverflow() {
        assertEquals(1_700_000_000_000L, MmsSupport.milliseconds(1_700_000_000L))
        assertNull(MmsSupport.milliseconds(-1))
        assertNull(MmsSupport.milliseconds(Long.MAX_VALUE))
    }

    @Test
    fun leavesAProviderDateThatIsAlreadyMilliseconds() {
        // Some carriers store the MMS `date` in milliseconds. Scaling it again
        // put the conversation in the year 58000, where it sorted above every
        // real message in the list forever (#317).
        val alreadyMs = 1_791_706_107_000L
        assertEquals(alreadyMs, MmsSupport.milliseconds(alreadyMs))
        assertEquals(
            "a millisecond date must not move a day",
            "2026-10-11",
            java.time.Instant.ofEpochMilli(MmsSupport.milliseconds(alreadyMs)!!)
                .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
        )
    }

    @Test
    fun secondsAndMillisecondsMapToTheSameInstant() {
        // The point of the unit-aware reader: one instant, two provider
        // encodings, one stored value.
        assertEquals(
            MmsSupport.milliseconds(1_700_000_000L),
            MmsSupport.milliseconds(1_700_000_000_000L)
        )
    }

    @Test
    fun theMigrationFloorSeparatesMillisecondsFromSeconds() {
        val floor = MmsSupport.timestampMillisecondsFloor()
        assertTrue("a real millisecond timestamp must be above the floor", 1_700_000_000_000L > floor)
        assertTrue("seconds-valued timestamps must stay below it", 1_700_000_000L < floor)
    }

    @Test
    fun resolvesIncomingSenderAndOutgoingRecipient() {
        val incoming = listOf(MmsSupport.Address(137, "+15551234567/TYPE=PLMN"))
        val outgoing = listOf(MmsSupport.Address(137, "insert-address-token"),
            MmsSupport.Address(151, "+15551234567"))
        assertEquals("+15551234567", MmsSupport.peer(1, incoming, 1))
        assertEquals("+15551234567", MmsSupport.peer(2, outgoing, 1))
        // A thread with several recipients is a group, but the *sender* of an
        // incoming MMS is still unambiguous, so it is attributed rather than
        // discarded. This used to return null, which silently threw away every
        // group MMS unread; MmsPeerTest covers the group cases in detail.
        assertEquals("+15551234567", MmsSupport.peer(1, incoming, 2))
        assertEquals("+15551234567", MmsSupport.peer(1, incoming, null))
        assertNull(MmsSupport.phoneAddress("sender@example.test"))
    }

    @Test
    fun combinesTextAndKeepsEveryAttachmentItCanShow() {
        val content = MmsSupport.content(listOf(
            MmsSupport.Part(1, "application/smil"),
            MmsSupport.Part(2, "text/plain", "Caption"),
            MmsSupport.Part(3, "IMAGE/JPEG; name=photo.jpg"),
            MmsSupport.Part(4, "image/png"),
            MmsSupport.Part(5, "video/mp4")
        ))
        assertEquals(3L, content.imageId)
        // Each picture and the clip get a row of their own (#398, #461), so
        // nothing here is left to apologise for.
        assertEquals(listOf(4L), content.extraImageIds)
        assertEquals(listOf(5L), content.videoIds)
        assertEquals(0, content.omittedParts)
        assertEquals("Caption", content.body)
        assertEquals(
            listOf("image" to 3L, "image" to 4L, "video" to 5L),
            content.mediaRows()
        )
    }

    @Test
    fun aVideoMmsIsAVideoRatherThanANotice() {
        // #461: a clip-only MMS used to arrive as one grey line saying the
        // attachment could not be displayed.
        val content = MmsSupport.content(listOf(
            MmsSupport.Part(1, "application/smil"),
            MmsSupport.Part(7, "video/mp4")
        ))
        assertEquals("video", content.mediaType)
        assertEquals(7L, content.mediaId)
        assertEquals(listOf(7L), content.videoIds)
        assertEquals("", content.body)
        assertEquals(listOf("video" to 7L), content.mediaRows())
    }

    @Test
    fun aClipIsNotFoldedIntoThePictureAlbum() {
        val content = MmsSupport.content(listOf(
            MmsSupport.Part(2, "image/jpeg"),
            MmsSupport.Part(3, "video/3gpp")
        ))
        assertEquals("image", content.mediaType)
        assertEquals(listOf(3L), content.videoIds)
        // The one picture is the message's own, so the album list stays empty;
        // the clip is still a row of its own.
        assertEquals(emptyList<Long>(), content.extraImageIds)
        assertEquals(listOf("image" to 2L, "video" to 3L), content.mediaRows())
    }

    @Test
    fun onlyWhatTheAppCannotShowIsCountedAsUnsupported() {
        val content = MmsSupport.content(listOf(
            MmsSupport.Part(1, "image/png"),
            MmsSupport.Part(2, "application/pdf"),
            MmsSupport.Part(3, "video/3gpp")
        ))
        assertEquals(1, content.omittedParts)
        assertTrue(content.body.contains("1 additional/unsupported"))
    }

    @Test
    fun aMimeDecidesWhichBubbleTheAttachmentGets() {
        assertEquals("image", MmsSupport.mediaKind("image/jpeg"))
        assertEquals("video", MmsSupport.mediaKind("video/mp4"))
        assertEquals("video", MmsSupport.mediaKind("VIDEO/3GPP"))
        assertEquals("file", MmsSupport.mediaKind("application/pdf"))
        assertTrue(MmsSupport.isVideo("video/quicktime"))
        assertFalse(MmsSupport.isVideo("image/jpeg"))
    }

    @Test
    fun aSavedClipKeepsAVideoExtension() {
        assertEquals("Alex_1700000000000.mp4",
            MmsSupport.savedAttachmentName("Alex", 1700000000000, "video/mp4"))
        assertEquals("Alex_1700000000000.3gp",
            MmsSupport.savedAttachmentName("Alex", 1700000000000, "video/3gpp"))
    }

    @Test
    fun everyPictureOfAMultiPictureMmsIsKept() {
        val content = MmsSupport.content(listOf(
            MmsSupport.Part(1, "application/smil"),
            MmsSupport.Part(2, "image/jpeg"),
            MmsSupport.Part(3, "image/png"),
            MmsSupport.Part(4, "image/webp")
        ))
        assertEquals(2L, content.imageId)
        assertEquals(listOf(3L, 4L), content.extraImageIds)
        // Nothing is missing from the bubble any more, so there is no notice to
        // print over the pictures.
        assertEquals(0, content.omittedParts)
        assertEquals("", content.body)
        assertEquals(
            listOf("image" to 2L, "image" to 3L, "image" to 4L),
            content.mediaRows()
        )
    }

    @Test
    fun aRescanReplacesABodyThatStillSaysPicturesAreNotDisplayed() {
        // What the import stored before #398: the first picture plus the notice
        // over the two it left out. A picture-only MMS has no caption at all, so
        // the fresh body is empty and that empty body is what clears the notice.
        val stored = "[MMS: 2 additional/unsupported attachment(s) not displayed]"
        assertEquals("", MmsSupport.refreshedBody(stored, "", providerEmpty = false))
        assertEquals("Caption", MmsSupport.refreshedBody("Caption\n$stored", "Caption", providerEmpty = false))
        // A notice an MMS still deserves (a video part this app cannot show) is
        // the freshly computed one, count included.
        val withVideo = "[MMS: 1 additional/unsupported attachment(s) not displayed]"
        assertEquals(withVideo, MmsSupport.refreshedBody(stored, withVideo, providerEmpty = false))
    }

    @Test
    fun aRescanKeepsTheStoredBodyWhenTheProviderHasNothingLeft() {
        // Clearing the provider must not wipe the only copy of the message.
        assertEquals("hello", MmsSupport.refreshedBody("hello", "", providerEmpty = true))
        assertEquals("", MmsSupport.refreshedBody("", "", providerEmpty = true))
    }

    @Test
    fun aTextOnlyMmsHasNoMediaRows() {
        val content = MmsSupport.content(listOf(MmsSupport.Part(7, "text/plain", "hi")))
        assertTrue(content.mediaRows().isEmpty())
        assertEquals("hi", content.body)
    }

    @Test
    fun readsAVoiceNotePartInsteadOfCallingItUnsupported() {
        val content = MmsSupport.content(listOf(
            MmsSupport.Part(1, "application/smil"),
            MmsSupport.Part(2, "text/plain", "Listen"),
            MmsSupport.Part(3, "audio/mp4")
        ))
        assertEquals(3L, content.audioId)
        assertEquals("audio/mp4", content.audioMime)
        // Nothing is left over: a voice note is supported, so it must not also
        // be reported as an attachment this app cannot show.
        assertEquals(0, content.omittedParts)
        assertEquals("Listen", content.body)
        assertEquals("audio", content.mediaType)
        assertEquals(3L, content.mediaId)
        assertEquals(null, content.imageId)
    }

    @Test
    fun anAmrVoiceNoteCountsAsAudioToo() {
        val content = MmsSupport.content(listOf(MmsSupport.Part(9, "audio/amr")))
        assertEquals("audio", content.mediaType)
        assertEquals(9L, content.audioId)
        assertTrue(MmsSupport.isAudio("AUDIO/3GPP; name=note.3gp"))
        assertFalse(MmsSupport.isAudio("image/jpeg"))
    }

    @Test
    fun aPictureStillWinsOverAVoiceNote() {
        val content = MmsSupport.content(listOf(
            MmsSupport.Part(3, "audio/mp4"),
            MmsSupport.Part(4, "image/jpeg")
        ))
        assertEquals("image", content.mediaType)
        assertEquals(4L, content.mediaId)
        // The audio part is still present, but only one attachment is shown, so
        // it is reported rather than silently dropped.
        assertEquals(1, content.omittedParts)
    }

    @Test
    fun attachmentKindFollowsTheMimeType() {
        assertEquals("image", MmsSupport.mediaKind("image/png"))
        assertEquals("audio", MmsSupport.mediaKind("audio/mp4"))
        assertEquals("video", MmsSupport.mediaKind("video/mp4"))
        assertEquals("file", MmsSupport.mediaKind("application/pdf"))
    }

    @Test
    fun platformAliasesForTheRecordedContainerAreNormalised() {
        // Android reports an .m4a as audio/mp4a-latm on some builds and
        // audio/m4a on others; the carrier is told the RFC name either way.
        assertEquals("audio/mp4", MmsSupport.canonicalMime("audio/mp4a-latm"))
        assertEquals("audio/mp4", MmsSupport.canonicalMime("audio/m4a"))
        assertEquals("audio/mp4", MmsSupport.canonicalMime("audio/x-m4a; charset=binary"))
        assertEquals("image/jpeg", MmsSupport.canonicalMime("image/jpeg"))
        assertEquals(
            "audio/mp4",
            MmsSupport.defaultAttachmentMime("audio/mp4a-latm", "content://voice/1.m4a")
        )
    }

    @Test
    fun aVoiceAttachmentIsNamedAsAudioInTheSmilLayout() {
        val parts = MmsSupport.outgoingParts("audio/mp4", "")
        assertEquals("audio", parts[0].name)
        assertEquals("audio/mp4", parts[0].mimeType)
        assertEquals("image", MmsSupport.outgoingParts("image/png", "")[0].name)
    }

    @Test
    fun anInboundVoiceNoteIsMarkedAsSuch() {
        val note = MmsSupport.InboundMms("+15551234567", "", 1L, audioUri = "content://mms/part/3")
        assertTrue(note.isAudio)
        assertFalse(
            MmsSupport.InboundMms("+15551234567", "hi", 1L).isAudio
        )
    }

    @Test
    fun boundsStreamedTextAndDecodesCharset() {
        assertTrue(MmsSupport.acceptsTextChunk(0, MmsSupport.MAX_TEXT_BYTES))
        assertFalse(MmsSupport.acceptsTextChunk(MmsSupport.MAX_TEXT_BYTES, 1))
        assertFalse(MmsSupport.acceptsTextChunk(-1, 1))
        assertEquals("café", MmsSupport.decodeText(byteArrayOf(99, 97, 102, -23), 4))
    }

    @Test
    fun distinguishesProviderNamespaces() {
        assertEquals("content://sms", MmsSupport.providerUri(MmsSupport.TRANSPORT_SMS))
        assertEquals("content://mms", MmsSupport.providerUri(MmsSupport.TRANSPORT_MMS))
        assertNull(MmsSupport.providerUri("unknown"))
    }

    @Test
    fun recognizesWapPushesByActionAndTypeAlone() {
        val action = android.provider.Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION
        assertTrue(MmsSupport.isMmsWapPush(action, MmsSupport.WAP_PUSH_MIME))
        assertFalse(MmsSupport.isMmsWapPush(null, MmsSupport.WAP_PUSH_MIME))
        assertFalse(MmsSupport.isMmsWapPush(action, null))
        assertFalse(MmsSupport.isMmsWapPush(action, "text/plain"))
        assertFalse(
            MmsSupport.isMmsWapPush(
                android.provider.Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION,
                MmsSupport.WAP_PUSH_MIME
            )
        )
    }

    @Test
    fun resolvesTheLocationAnAnnouncedMmsCanBeFetchedFrom() {
        assertEquals(
            "http://mmsc.example/msg/42",
            MmsSupport.downloadLocation("http://mmsc.example/msg/42")
        )
        assertNull(MmsSupport.downloadLocation(null))
        assertNull(MmsSupport.downloadLocation(""))
        assertNull(MmsSupport.downloadLocation("   "))
    }

    @Test
    fun anOwnMessageIsLabelledByWhatItCarries() {
        // A picture leaves as an MMS even while its row is still stored as sms,
        // so the label cannot come from the transport column alone.
        assertEquals("MMS", MmsSupport.outgoingKind("image", MmsSupport.TRANSPORT_SMS))
        assertEquals("MMS", MmsSupport.outgoingKind("text", MmsSupport.TRANSPORT_MMS))
        assertEquals("SMS", MmsSupport.outgoingKind("text", MmsSupport.TRANSPORT_SMS))
    }

    @Test
    fun aProviderRowIsCompleteOnlyWhenEveryPartHasARow() {
        val first = MmsSupport.partUri(11)
        val second = MmsSupport.partUri(12)
        val third = MmsSupport.partUri(13)
        val stored = mapOf(
            // Imported before every attachment got a row of its own.
            7L to setOf(first),
            // Fully imported.
            8L to setOf(first, second, third),
            // A text-only MMS carries no part URI.
            9L to setOf(""),
        )
        val parts = mapOf(7L to listOf(11L, 12L, 13L), 8L to listOf(11L, 12L, 13L))
        assertEquals(setOf(8L, 9L), MmsSupport.completeMmsIds(stored, parts))
    }

    @Test
    fun aMessageWithNoProviderPartNeedsNoBackfill() {
        // The provider's parts were deleted after import; there is nothing to
        // add, and re-importing would duplicate an empty row.
        val stored = mapOf(5L to setOf("content://mms/part/50"))
        assertEquals(setOf(5L), MmsSupport.completeMmsIds(stored, emptyMap()))
    }

    @Test
    fun aCompleteMmsThatStillCarriesTheNoticeIsReadAgain() {
        val first = MmsSupport.partUri(11)
        val second = MmsSupport.partUri(12)
        val stored = mapOf(7L to setOf(first, second), 8L to setOf(first))
        val parts = mapOf(7L to listOf(11L, 12L), 8L to listOf(11L, 12L))
        assertEquals(setOf(7L), MmsSupport.completeMmsIds(stored, parts))
        // 7 has every part, but its body still says pictures are not displayed,
        // which only the provider can disprove.
        assertEquals(emptySet<Long>(), MmsSupport.skippableMmsIds(stored, parts, setOf(7L)))
        // A notice on an incomplete row does not drag a complete sibling down.
        assertEquals(setOf(7L), MmsSupport.skippableMmsIds(stored, parts, setOf(8L)))
    }

    @Test
    fun theBodyNoticeIsRecognisable() {
        // A document is the case the notice still exists for; a clip is shown
        // in its own bubble since #461.
        val notice = MmsSupport.content(listOf(
            MmsSupport.Part(1, "image/png"),
            MmsSupport.Part(2, "application/pdf")
        )).body
        assertEquals("[MMS: 1 additional/unsupported attachment(s) not displayed]", notice)
        assertTrue(MmsSupport.carriesNotice(notice))
        assertTrue(MmsSupport.carriesNotice("Caption\n$notice"))
        assertFalse(MmsSupport.carriesNotice("Caption"))
    }

    @Test
    fun onlyANewUnreadInboundMmsBumpsTheBadge() {
        assertTrue(MmsSupport.countsAsNewUnread(isMe = false, read = false, alreadyStored = false))
        assertFalse(MmsSupport.countsAsNewUnread(isMe = true, read = false, alreadyStored = false))
        assertFalse(MmsSupport.countsAsNewUnread(isMe = false, read = true, alreadyStored = false))
        // The picture the upgrade backfills belongs to a message already shown.
        assertFalse(MmsSupport.countsAsNewUnread(isMe = false, read = false, alreadyStored = true))
    }
}
