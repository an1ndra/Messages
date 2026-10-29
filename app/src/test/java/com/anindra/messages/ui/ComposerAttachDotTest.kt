package com.anindra.messages.ui

import androidx.compose.ui.graphics.toArgb
import com.anindra.messages.ui.theme.ComposerAttachDot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Google Messages badges the composer's attach button: a #76D1FF dot overlaps
 * the top-right of the "+" ring, and the ring reads as broken because the dot
 * carries a ring of the field's own colour around it.
 *
 * The colour was measured off the running app, so it is pinned here; it is the
 * one composer colour that is not a Material scheme role.
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

    @Test
    fun theDotIsGooglesMeasuredColour() {
        val argb = ComposerAttachDot.toArgb()
        assertEquals("#76D1FF", "#%06X".format(argb and 0xFFFFFF))
    }

    @Test
    fun theDotIsGooglesMeasuredSize() {
        // GM's dot measures 14px (~5dp) across at 440dpi; the 8dp box is that
        // dot plus the 1.5dp cut-out ring on each side.
        assertTrue(
            "the dot box must be 8dp, or the badge reads heavier than GM's",
            Regex("""\.size\(8\.dp\)\s*\.background\(MaterialTheme\.colorScheme\.inputPill""")
                .containsMatchIn(chat)
        )
    }

    @Test
    fun theAttachButtonCarriesIt() {
        assertTrue(
            "the dot must sit on the attach button's ring",
            chat.contains("ComposerAttachDot")
        )
        assertTrue(
            "it is drawn at the ring's top-right, not centred",
            chat.contains("Alignment.TopEnd")
        )
    }

    @Test
    fun theRingReadsAsBroken() {
        assertTrue(
            "the dot needs a ring of field colour, or it floats on the icon instead of cutting it",
            Regex("""background\(MaterialTheme\.colorScheme\.inputPill, CircleShape\)\s*\.padding""")
                .containsMatchIn(chat)
        )
    }

    @Test
    fun theBadgeIsDecorative() {
        assertTrue(
            "the attach button's label already explains the control; the dot must not add one",
            chat.contains("stringResource(R.string.icon_attach)")
        )
    }
}
