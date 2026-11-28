package com.anindra.messages.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A thread's tab is decided from the message that opened it. The classifier
 *  itself cannot know which message that is — the view model has to pass it —
 *  so the wiring is checked where a JVM test can see it. */
class InboxCategoryImportTest {

    @Test
    fun theInboxClassifiesFromTheOpeningMessageNotThePreview() {
        val main = sourceOf("com/anindra/messages/MainActivity.kt")
        val block = main.substringAfter("fun classifyInboxCategories(")
            .substringBefore("    /**")
        assertTrue(block.contains("InboxCategoryRules.openingBody("))
        assertFalse(block.contains("body = convo.snippet"))
        assertTrue(block.contains("findRanges(body)"))
    }

    @Test
    fun theRepositoryReadsTheOldestMessageOfAThread() {
        val repo = sourceOf("com/anindra/messages/data/Repository.kt")
        assertTrue(repo.contains("ORDER BY timestamp ASC, id ASC LIMIT 1"))
    }

    private fun sourceOf(relativePath: String): String {
        val workingDir = System.getProperty("user.dir") ?: "."
        var dir: File? = File(workingDir).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/$relativePath")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        throw AssertionError("could not locate $relativePath from $workingDir")
    }
}
