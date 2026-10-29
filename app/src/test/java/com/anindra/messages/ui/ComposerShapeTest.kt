package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The composer is a pill, like Google Messages': a 56dp field whose corners are
 * 28dp — half its height. At 24dp it still read as a rounded rectangle when the
 * two were put side by side.
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

    @Test
    fun theComposerIsAPill() {
        assertTrue(
            "the input surface must use the 28dp corner that matches its height",
            Regex("""Surface\(\s*shape = RoundedCornerShape\(28\.dp\),\s*color = MaterialTheme\.colorScheme\.inputPill""")
                .containsMatchIn(chat)
        )
    }

    @Test
    fun theOldSquarerCornerIsGone() {
        assertTrue(
            "24dp on a 56dp field is a rounded rectangle, not a pill",
            !Regex("""Surface\(\s*shape = RoundedCornerShape\(24\.dp\),\s*color = MaterialTheme\.colorScheme\.inputPill""")
                .containsMatchIn(chat)
        )
    }
}
