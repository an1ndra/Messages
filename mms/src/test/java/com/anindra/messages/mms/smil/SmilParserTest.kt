package com.anindra.messages.mms.smil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmilParserTest {

    private val twoPar = """
        <smil xmlns="http://www.w3.org/2001/SMIL20/Language">
        <head><layout/></head>
        <body>
        <par dur="8s"><img src="a.jpg"/></par>
        <par dur="5s"><text src="b.txt"/></par>
        </body>
        </smil>
    """.trimIndent()

    @Test
    fun ourOwnOutputParsesBackToTheSameDocument() {
        val document = SmilDocument(
            listOf(SmilPar(listOf(SmilImage("a.jpg"))), SmilPar(listOf(SmilText("b.txt")))),
        )
        val round = SmilParser.parse(SmilSerializer.serialize(document))!!
        assertEquals(2, round.pars.size)
        assertEquals(listOf("a.jpg", "b.txt"), round.items.map { it.src })
        assertTrue(round.items[0] is SmilImage)
        assertTrue(round.items[1] is SmilText)
    }

    @Test
    fun parGroupingSurvivesARoundTrip() {
        val document = SmilDocument(
            listOf(SmilPar(listOf(SmilImage("a.jpg"), SmilImage("b.jpg"), SmilText("c.txt")))),
        )
        val round = SmilParser.parse(SmilSerializer.serialize(document))!!
        assertEquals(1, round.pars.size)
        assertEquals(3, round.pars[0].items.size)
    }

    @Test
    fun theFirstParsDurationWinsForTheDocument() {
        val parsed = SmilParser.parse(twoPar.toByteArray())!!
        assertEquals(8_000L, parsed.parDurationMillis)
    }

    @Test
    fun aDocumentCarryingRegionsAndMetaIsStillRead() {
        // We never emit these, but inbound slideshows do, and refusing a message
        // over its presentation metadata would be a bad trade.
        val rich = """
            <smil xmlns="http://www.w3.org/2001/SMIL20/Language">
            <head>
            <meta name="mms-compatibility" content="96"/>
            <meta name="present" content="16s"/>
            <layout>
            <root-layout width="320px" height="480px"/>
            <region-layout id="Image" top="0px" left="0px" width="320px" height="360px"/>
            </layout>
            </head>
            <body>
            <par dur="8s"><img src="a.jpg" region="Image"/></par>
            </body>
            </smil>
        """.trimIndent()
        val parsed = SmilParser.parse(rich.toByteArray())!!
        assertEquals("a.jpg", parsed.items.single().src)
        assertEquals("96", parsed.compatibility)
    }

    @Test
    fun aFileEntityIsNotRead() {
        val xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE smil [ <!ENTITY xxe SYSTEM "file:///etc/hostname"> ]>
            <smil xmlns="http://www.w3.org/2001/SMIL20/Language">
            <head><layout/></head>
            <body><par dur="8s"><img src="&xxe;"/></par></body>
            </smil>
        """.trimIndent()
        val parsed = SmilParser.parse(xxe.toByteArray())
        if (parsed != null) {
            assertTrue("entity text leaked into src", !parsed.items.any { it.src.contains("root") || it.src.isBlank() })
        }
    }

    @Test
    fun aRemoteEntityIsNotFetched() {
        val xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE smil [ <!ENTITY xxe SYSTEM "http://127.0.0.1:1/nothing"> ]>
            <smil xmlns="http://www.w3.org/2001/SMIL20/Language">
            <head><layout/></head>
            <body><par dur="8s"><img src="&xxe;"/></par></body>
            </smil>
        """.trimIndent()
        SmilParser.parse(xxe.toByteArray())
    }

    @Test
    fun nonXmlIsRejectedRatherThanThrowing() {
        assertNull(SmilParser.parse("not xml at all".toByteArray()))
        assertNull(SmilParser.parse(ByteArray(0)))
    }

    @Test
    fun unclosedTagsAreRejected() {
        assertNull(SmilParser.parse("<smil><head><layout/></head><body><par>".toByteArray()))
    }

    @Test
    fun aDocumentThatIsNotSmilIsRejected() {
        assertNull(SmilParser.parse("<html><body/></html>".toByteArray()))
    }

    @Test
    fun aSmilWithNoItemsIsRejected() {
        assertNull(SmilParser.parse("<smil><head><layout/></head><body></body></smil>".toByteArray()))
    }

    @Test
    fun anItemWithoutASrcIsSkipped() {
        val partial = """
            <smil xmlns="http://www.w3.org/2001/SMIL20/Language">
            <head><layout/></head>
            <body><par dur="8s"><img/><img src="a.jpg"/></par></body>
            </smil>
        """.trimIndent()
        assertEquals(listOf("a.jpg"), SmilParser.parse(partial.toByteArray())!!.items.map { it.src })
    }

    @Test
    fun aRelativeSrcIsLeftRelative() {
        // The SMIL part carries no base of its own, so resolving would mean
        // guessing whether the message location is a URL, a cid: or a path.
        val relative = """
            <smil xmlns="http://www.w3.org/2001/SMIL20/Language">
            <head><layout/></head>
            <body><par dur="8s"><img src="photo.jpg"/></par></body>
            </smil>
        """.trimIndent()
        assertEquals("photo.jpg", SmilParser.parse(relative.toByteArray())!!.items.single().src)
    }

    @Test
    fun aDocumentWithoutANamespaceStillParses() {
        val plain = "<smil><head><layout/></head><body><par dur=\"8s\"><img src=\"a.jpg\"/></par></body></smil>"
        assertEquals("a.jpg", SmilParser.parse(plain.toByteArray())!!.items.single().src)
    }
}
