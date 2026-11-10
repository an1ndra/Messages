package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The composer is a pill: its corner radius is half its height, and its height
 * matches the 56dp send/record circle beside it. Google Messages' own composer
 * row measures 56dp tall in a uiautomator dump (field `[..,2032][..,2186]` on a
 * 440dpi screen), circle included.
 *
 * The rule is asserted rather than the numbers, so restyling the bar only has to
 * keep the corner and the height in proportion.
 */
class ComposerShapeTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private val chat get() = File(
        app, "src/main/java/com/anindra/messages/ui/ChatScreen.kt"
    ).readText()

    private val cornerRadius: Double?
        get() = Regex("""Surface\(\s*shape = RoundedCornerShape\((\d+)\.dp\),\s*color = MaterialTheme\.colorScheme\.inputPill""")
            .find(chat)?.groupValues?.get(1)?.toDouble()

    private val pillHeight: Double?
        get() = Regex("""Modifier\.fillMaxWidth\(\)\.heightIn\(min = (\d+)\.dp\)""")
            .find(chat)?.groupValues?.get(1)?.toDouble()

    @Test
    fun theComposerIsAPill() {
        assertNotNull(
            "the input surface must still be the themed pill Surface",
            cornerRadius
        )
        assertNotNull("the pill must declare its minimum height", pillHeight)
        assertEquals(
            "the corner radius is half the pill's height, or it is a rounded rectangle",
            pillHeight!! / 2.0,
            cornerRadius!!,
            0.001
        )
    }

    @Test
    fun thePillMatchesTheSendCircle() {
        assertTrue(
            "the composer pill must be as tall as the circle beside it",
            chat.contains("Modifier.fillMaxWidth().heightIn(min = 56.dp)")
        )
        // Record, Send and the locked send share one circle now, so there is a
        // single 56dp box behind every state rather than one per branch.
        assertTrue(
            "the one composer circle must be that same height",
            Regex("""\.size\(56\.dp\)[\s\S]{0,900}?\.background\(discColor, CircleShape\)""")
                .containsMatchIn(chat)
        )
    }
}
