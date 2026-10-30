package com.anindra.messages.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.anindra.messages.ui.theme.composerAttachDot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

/**
 * Reported: "in light mode in + icon one dot color not looks good?"
 *
 * The composer's attach button carries GM's badge dot. It was pinned to the
 * #76D1FF sampled off GM's *dark* composer, which on a light pill is a pale cyan
 * almost the colour of the field it sits on. It follows the accent now, so each
 * theme supplies a dot that reads.
 */
class ComposerAttachDotTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private val chat get() = File(
        app, "src/main/java/com/anindra/messages/ui/ChatScreen.kt"
    ).readText()

    private val theme get() = File(
        app, "src/main/java/com/anindra/messages/ui/theme/Theme.kt"
    ).readText()

    private fun scheme(primary: Color) = lightColorScheme(primary = primary)

    @Test
    fun theDotFollowsTheAccentInBothThemes() {
        assertEquals(Color(0xFF0B57D0), scheme(Color(0xFF0B57D0)).composerAttachDot)
        assertEquals(Color(0xFFA8C7FA), scheme(Color(0xFFA8C7FA)).composerAttachDot)
    }

    @Test
    fun theDotIsNoLongerTheDarkComposerCyan() {
        val light = scheme(Color(0xFF0B57D0)).composerAttachDot.toArgb() and 0xFFFFFF
        assertNotEquals("the pale cyan is the light-mode defect", 0x76D1FF, light)
    }

    @Test
    fun theHexIsGoneFromTheColourSource() {
        assertTrue(
            "a fixed hex cannot follow the theme",
            !Regex("""Color\(0xFF76D1FF\)""").containsMatchIn(theme)
        )
        assertTrue("the dot must be a scheme role", theme.contains("get() = composerAttachDot") ||
            theme.contains("val ColorScheme.composerAttachDot"))
    }

    @Test
    fun theAttachButtonCarriesIt() {
        assertTrue(
            "the dot must be painted with the role, not a literal",
            Regex("""\.background\(dot, CircleShape\)""").containsMatchIn(chat)
        )
        assertTrue(
            "the dot must name its colour for the regression script",
            chat.contains("\"ComposerDot\"")
        )
        assertTrue(
            "GM's dot measures 14px (~5dp) across at 440dpi; the box stays 8dp",
            Regex("""\.size\(8\.dp\)""").containsMatchIn(chat)
        )
    }

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
