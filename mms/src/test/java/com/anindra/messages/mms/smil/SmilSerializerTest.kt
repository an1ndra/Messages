package com.anindra.messages.mms.smil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmilSerializerTest {

    private fun serialize(vararg pars: SmilPar) =
        SmilSerializer.serialize(SmilDocument(pars.toList())).toString(Charsets.UTF_8)

    private fun imagePar(vararg src: String) = SmilPar(src.map { SmilImage(it) })
    private fun textPar(vararg src: String) = SmilPar(src.map { SmilText(it) })

    @Test
    fun theOutputIsPinnedToTheShapeCarriersAlreadyAccept() {
        // Byte-for-byte what the AOSP SmilHelper produced for this app: an empty
        // <layout>, no <meta>, and no region attributes. A change to this literal
        // is a change to the send path and needs a carrier to confirm it.
        assertEquals(
            "<smil xmlns=\"http://www.w3.org/2001/SMIL20/Language\">" +
                "<head><layout/></head>" +
                "<body>" +
                "<par dur=\"8000ms\"><img src=\"image000001.jpg\"/></par>" +
                "<par dur=\"8000ms\"><text src=\"text000002.txt\"/></par>" +
                "</body>" +
                "</smil>",
            serialize(imagePar("image000001.jpg"), textPar("text000002.txt")),
        )
    }

    @Test
    fun severalItemsInOneParallelShareItsDuration() {
        assertEquals(
            "<smil xmlns=\"http://www.w3.org/2001/SMIL20/Language\">" +
                "<head><layout/></head>" +
                "<body>" +
                "<par dur=\"8000ms\"><img src=\"a.jpg\"/><img src=\"b.jpg\"/></par>" +
                "</body>" +
                "</smil>",
            serialize(SmilPar(listOf(SmilImage("a.jpg"), SmilImage("b.jpg")))),
        )
    }

    @Test
    fun aDurationIsWrittenAsWholeMillisecondsNotAFraction() {
        // MMS 1.3 requires integer milliseconds even though SMIL 3.0 would allow
        // a fractional timecount.
        val xml = SmilSerializer.serialize(
            SmilDocument(listOf(imagePar("a.jpg")), parDurationMillis = 2_500L),
        ).toString(Charsets.UTF_8)
        assertTrue(xml.contains("dur=\"2500ms\""))
        assertTrue(!xml.contains("2.5"))
    }

    @Test
    fun anAmpersandIsEscapedBeforeAnythingElse() {
        assertEquals("a&amp;b", SmilSerializer.escape("a&b"))
    }

    @Test
    fun everyCharacterThatCouldEndAnAttributeIsEscaped() {
        assertEquals("&lt;a&gt;", SmilSerializer.escape("<a>"))
        assertEquals("&quot;q&quot;", SmilSerializer.escape("\"q\""))
    }

    @Test
    fun anApostropheIsLeftAloneBecauseAmpersandAposIsNotXml10() {
        assertEquals("it's", SmilSerializer.escape("it's"))
    }

    @Test
    fun escapingIsNotAppliedTwiceToAnAlreadyEscapedValue() {
        // '&' first is what guarantees this: escaping in the other order would
        // turn the ampersand of a produced "&amp;" into "&amp;amp;".
        assertEquals("&amp;lt;", SmilSerializer.escape("&lt;"))
    }

    @Test
    fun aHostileSrcCannotBreakOutOfTheAttribute() {
        val xml = serialize(imagePar("\" onload=\"alert(1)"))
        assertTrue(xml.contains("&quot;"))
        assertTrue(!xml.contains("onload=\"alert"))
    }

    @Test
    fun aSrcContainingMarkupIsEscapedRatherThanEmitted() {
        val xml = serialize(imagePar("</par><script>"))
        assertTrue(!xml.contains("<script>"))
        assertTrue(xml.contains("&lt;/par&gt;"))
    }

    @Test
    fun serializingTheSameDocumentTwiceIsByteIdentical() {
        val document = SmilDocument(listOf(imagePar("a.jpg"), textPar("b.txt")))
        assertTrue(SmilSerializer.serialize(document).contentEquals(SmilSerializer.serialize(document)))
    }

    @Test
    fun anEmptyDocumentStillProducesWellFormedXml() {
        assertEquals(
            "<smil xmlns=\"http://www.w3.org/2001/SMIL20/Language\">" +
                "<head><layout/></head><body></body></smil>",
            serialize(),
        )
    }

    @Test
    fun aVcardIsWrittenAsATextElement() {
        // SMIL has no vcard element; the AOSP helper emitted a text element for it.
        assertEquals("text", SmilItem.forElementName("vcard", "c.vcf")!!.elementName)
    }
}
