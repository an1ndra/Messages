package com.anindra.messages.mms.smil

import com.anindra.messages.mms.pdu.PduBody
import com.anindra.messages.mms.pdu.PduPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmilBuilderTest {

    private fun part(
        contentType: String,
        name: String? = null,
        contentId: String? = null,
        contentLocation: String? = null,
        smil: Boolean = false,
    ) = PduPart().apply {
        this.contentType = contentType
        this.name = name
        this.contentId = contentId
        this.contentLocation = contentLocation
        if (smil) this.contentType = PduPart.APP_SMIL
    }

    private fun bodyOf(vararg parts: PduPart) = PduBody().apply { parts.forEach { add(it) } }

    @Test
    fun anImageAndItsCaptionBecomeTwoParallels() {
        val document = SmilBuilder.build(
            bodyOf(part("image/jpeg", "image000001.jpg"), part("text/plain", "text000002.txt"))
        )!!
        assertEquals(2, document.pars.size)
        assertTrue(document.pars[0].items.single() is SmilImage)
        assertTrue(document.pars[1].items.single() is SmilText)
    }

    @Test
    fun severalMediaBeforeACaptionShareOneParallel() {
        // The grouping rule: a par holds media and text together, and only splits
        // once it has both. Two images then a caption is a single par.
        val document = SmilBuilder.build(
            bodyOf(
                part("image/jpeg", "a.jpg"),
                part("image/jpeg", "b.jpg"),
                part("text/plain", "c.txt"),
            )
        )!!
        assertEquals(1, document.pars.size)
        assertEquals(3, document.pars[0].items.size)
    }

    @Test
    fun aSecondCaptionStartsANewParallel() {
        val document = SmilBuilder.build(
            bodyOf(
                part("image/jpeg", "a.jpg"),
                part("text/plain", "one.txt"),
                part("text/plain", "two.txt"),
            )
        )!!
        assertEquals(2, document.pars.size)
        assertEquals(1, document.pars[0].items.size)
        assertEquals(1, document.pars[1].items.size)
    }

    @Test
    fun theSmilPartItselfIsNeverALayoutItem() {
        val document = SmilBuilder.build(
            bodyOf(
                part("application/smil", "smil.xml", smil = true),
                part("image/jpeg", "a.jpg"),
            )
        )!!
        assertEquals(1, document.items.size)
        assertEquals(1, document.pars.size)
    }

    @Test
    fun aBodyOfOnlySmilProducesNothing() {
        assertNull(SmilBuilder.build(bodyOf(part("application/smil", "smil.xml", smil = true))))
    }

    @Test
    fun anEmptyBodyProducesNothing() {
        assertNull(SmilBuilder.build(bodyOf()))
    }

    @Test
    fun anUntypedPartIsSkippedRatherThanGuessed() {
        val document = SmilBuilder.build(
            bodyOf(part("application/octet-stream", "x.bin"), part("image/jpeg", "a.jpg"))
        )!!
        assertEquals(1, document.items.size)
    }

    @Test
    fun aBodyOfOnlyUnknownTypesProducesNothing() {
        assertNull(SmilBuilder.build(bodyOf(part("application/octet-stream", "x.bin"))))
    }

    @Test
    fun videoAndAudioGetTheirOwnElements() {
        val document = SmilBuilder.build(
            bodyOf(part("video/mp4", "v.mp4"), part("audio/amr", "a.amr"))
        )!!
        val items = document.items
        assertTrue(items[0] is SmilVideo)
        assertEquals("video", items[0].elementName)
        assertTrue(items[1] is SmilAudio)
        assertEquals("audio", items[1].elementName)
    }

    @Test
    fun srcPrefersNameThenLocationThenACidUri() {
        val named = SmilBuilder.build(bodyOf(part("image/jpeg", name = "photo.jpg", contentLocation = "loc.jpg", contentId = "<cid1>")))!!
        assertEquals("photo.jpg", named.items.single().src)

        val located = SmilBuilder.build(bodyOf(part("image/jpeg", name = null, contentLocation = "http://mmsc/loc.jpg", contentId = "<cid1>")))!!
        assertEquals("loc.jpg", located.items.single().src)

        val cidOnly = SmilBuilder.build(bodyOf(part("image/jpeg", name = null, contentLocation = null, contentId = "<cid1>")))!!
        assertEquals("cid:cid1", cidOnly.items.single().src)
    }

    @Test
    fun aContentIdWithAngleBracketsIsUnwrappedForTheCidUri() {
        val document = SmilBuilder.build(bodyOf(part("image/jpeg", name = null, contentId = "<smil>")))!!
        assertEquals("cid:smil", document.items.single().src)
    }

    @Test
    fun aPartWithNoIdentifiersAtAllStillProducesAResolvableSrc() {
        val document = SmilBuilder.build(bodyOf(part("image/jpeg")))!!
        assertNotNull(document.items.single().src)
        assertTrue(document.items.single().src.isNotBlank())
    }

    @Test
    fun partOrderIsPreserved() {
        val document = SmilBuilder.build(
            bodyOf(
                part("image/jpeg", "a.jpg"),
                part("image/jpeg", "b.jpg"),
                part("text/plain", "c.txt"),
                part("image/jpeg", "d.jpg"),
            )
        )!!
        assertEquals(
            listOf("a.jpg", "b.jpg", "c.txt", "d.jpg"),
            document.items.map { it.src },
        )
    }
}
