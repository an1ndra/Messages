package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saving an attachment has to say what happened.
 *
 * The outcome was worked out into a `message` and then thrown away — no toast
 * was ever shown, so a save that wrote the file and one that failed looked
 * identical to the user (and, as a warning, to CodeQL).
 */
class AttachmentSaveFeedbackTest {

    private val main: String by lazy {
        val app = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
        File(app, "src/main/java/com/anindra/messages/MainActivity.kt").readText()
    }

    @Test
    fun theSaveSaysWhatHappened() {
        val save = main.substringAfter("fun saveMessageImages(")
            .substringBefore("fun retryMessage(")
        assertTrue("the outcome must be shown", save.contains("Toast.makeText(app, message"))
        assertTrue("and the outcome must be computed", save.contains("val message = when {"))
    }
}
