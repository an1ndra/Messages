package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Import & export log restyle carries the outcome in a status pill and the
 * retry count in its own pill, and lists the newest run first. None of that is
 * enforced by the compiler, so a refactor could silently drop it; these pin the
 * wiring, while the previews cover the rendering.
 */
class TransferLogScreenWiringTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val screen: String by lazy {
        File(main, "java/com/anindra/messages/ui/TransferLogScreen.kt").readText()
    }

    private val baseStrings: String by lazy {
        File(main, "res/values/strings_advanced.xml").readText()
    }

    @Test
    fun baseDefinesTheOutcomeAndRetryStrings() {
        listOf(
            "transfer_log_status_ok",
            "transfer_log_status_failed",
            "transfer_log_retried"
        ).forEach { key ->
            assertTrue("base string $key is missing", baseStrings.contains("name=\"$key\""))
        }
    }

    @Test
    fun theOutcomeIsShownAsTextAndNotOnlyColour() {
        assertTrue(
            "success must read as text, not just a green icon",
            screen.contains("R.string.transfer_log_status_ok")
        )
        assertTrue(
            "failure must read as text, not just a red icon",
            screen.contains("R.string.transfer_log_status_failed")
        )
    }

    @Test
    fun theRetryCountIsShownWhenThereWasMoreThanOneAttempt() {
        assertTrue(
            "a retried run must say how many attempts it took",
            screen.contains("R.string.transfer_log_retried")
        )
        assertTrue(
            "the retry pill is only meaningful above one attempt",
            screen.contains("entry.attempts > 1")
        )
    }

    @Test
    fun theNewestRunIsListedFirst() {
        assertTrue(
            "the run a user just did must be at the top, not below twenty old ones",
            screen.contains("entries.asReversed()")
        )
    }
}
