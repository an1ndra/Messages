package com.anindra.messages.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Guards the hand-converted vector drawables. [ic_padlock] was traced from a
 *  512px SVG by hand: malformed path data only fails when the drawable is
 *  inflated on screen, and a missing group transform silently changes the icon's
 *  optical size next to the Material glyphs it sits beside. */
class VectorDrawableTest {

    private val drawableDir: File by lazy {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "src/main/res").isDirectory) dir = dir.parentFile
        dir?.let { File(it, "src/main/res/drawable") } ?: error("src/main/res not found")
    }

    private fun parse(name: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder()
            .parse(File(drawableDir, "$name.xml"))
            .documentElement
    }

    private fun paths(root: Element): List<Element> {
        val out = mutableListOf<Element>()
        fun walk(node: Element) {
            if (node.tagName.endsWith("path")) out.add(node)
            val kids = node.childNodes
            for (i in 0 until kids.length) (kids.item(i) as? Element)?.let { walk(it) }
        }
        walk(root)
        return out
    }

    @Test
    fun padlockIsAValidDrawable() {
        val root = parse("ic_padlock")
        assertEquals("root element", "vector", root.tagName.substringAfterLast(':'))
        assertEquals("path count", 2, paths(root).size)
    }

    @Test
    fun padlockPathsCarryRealGeometry() {
        val data = paths(parse("ic_padlock")).map { it.getAttribute("android:pathData") }
        assertTrue("every path needs path data", data.all { it.isNotBlank() })
        // A path that never moves or draws is the shape a transcription slip leaves
        // behind, and it inflates happily while showing nothing.
        data.forEach {
            assertTrue("path must start with a moveto: $it", it.trimStart().startsWith("M"))
            assertTrue("path should have real extent, got ${it.length} chars", it.length > 40)
        }
        assertTrue("padlock body should be an even-odd ring (the keyhole)", data.any { it.contains("a52 52") })
    }

    @Test
    fun padlockIsRebasedOntoItsViewportRatherThanScaledInPlace() {
        val root = parse("ic_padlock")
        // A <group> transform leaves the art against the viewport edge, where
        // VectorDrawable's clip shaves it, and any scale under 1 leaves a
        // transparent margin that reads as padding around the icon.
        assertEquals("no transform group", 0, root.getElementsByTagName("group").length)
        // Measured ink bounds of the source are x 80..438, y 10..506, i.e.
        // 358x496, plus a 1-unit margin on each side.
        assertEquals(
            "viewport must match the traced artwork bounds",
            "360", root.getAttribute("android:viewportWidth")
        )
        assertEquals("498", root.getAttribute("android:viewportHeight"))
    }

    @Test
    fun padlockDeclaresAnOpticalSizeComparableToAMaterialGlyph() {
        val root = parse("ic_padlock")
        fun dp(name: String) =
            root.getAttribute(name).removeSuffix("dp").toDouble()
        // Sized by eye against the bin on a device screenshot, then deliberately
        // nudged above the Material glyph's own ink height for visual weight, so
        // this no longer tracks Icons.Rounded.Delete exactly. The band is tight
        // around the chosen value so an edit cannot quietly undo that.
        val h = dp("android:height")
        assertTrue("height ${h}dp is out of the chosen range", h in 18.0..19.0)
        val w = dp("android:width")
        assertTrue("width ${w}dp is out of the chosen range", w in 13.0..14.0)
    }
}
