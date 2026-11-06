package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** #405 follow-up: a phone number in the forward picker is a weakly-typed run
 *  of digits and separators, so an RTL window laid it out right-to-left and
 *  "+98 912 762 0912" painted as "0912 762 98+". Numbers must be painted as
 *  LTR isolates whatever the window direction, and a typed search must take
 *  the direction of its own first strong character. */
class ForwardPickerDirectionTest {

    private val chatScreen: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/anindra/messages/ui/ChatScreen.kt") }
            .firstOrNull { it.isFile }
            ?: error("ChatScreen.kt not found")
    }

    private val picker: String by lazy {
        val src = chatScreen.readText()
        val marker = Regex("""(?:private|internal|public)?\s*fun ForwardPicker\(""").find(src)
            ?: error("ForwardPicker not found")
        balancedBlock(src, src.indexOf('{', marker.range.last))
    }

    @Test
    fun everyNumberRowIsPaintedAsAnLtrIsolate() {
        // One for the primary line when the target has no name, one for the
        // secondary line under a name.
        assertEquals(
            "both number slots must go through BidiText.ltr, like every other " +
                "number row in an RTL window",
            2,
            Regex("""BidiText\.ltr\(target\.number\)""").findAll(picker).count()
        )
    }

    @Test
    fun aNameKeepsItsOwnDirection() {
        assertTrue(
            "a contact name must be laid out by its own first strong character, not the window's",
            picker.contains("BidiText.firstStrongDirection(name).toTextDirection()")
        )
    }

    @Test
    fun theSearchFieldPaintsInItsOwnDirection() {
        assertTrue(
            "a typed number must not inherit the RTL window direction",
            picker.contains("BidiText.firstStrongDirection(query)") &&
                picker.contains("textStyle = queryStyle")
        )
    }

    @Test
    fun theDirectionIsAssertableFromADumpRun() {
        assertTrue(
            "the paint order is invisible to uiautomator; log it as ForwardBidi",
            picker.contains("\"ForwardBidi\"")
        )
    }

    private fun balancedBlock(src: String, openBrace: Int): String {
        var depth = 0
        for (i in openBrace until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(openBrace, i + 1)
                }
            }
        }
        throw AssertionError("unterminated block at $openBrace")
    }
}
